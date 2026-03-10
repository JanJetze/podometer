// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import com.podometer.util.DateTimeUtils
import java.time.LocalDate

/**
 * Pure accumulator for step events; holds no Android-framework references so it
 * can be tested with plain JUnit.
 *
 * Responsibilities:
 * - Track steps within the current hour bucket ([currentHourSteps]).
 * - Track total steps today ([totalStepsToday]).
 * - Detect hour-boundary crossings on each [addSteps] call.
 * - Detect midnight crossings and reset the daily total for the new day.
 * - Handle multi-hour clock jumps (DST, timezone changes) by emitting a
 *   [FlushResult] for each skipped intermediate hour with zero steps.
 * - Track the detected activity state per bucket via [setActivity].
 * - Produce [FlushResult] (hourly aggregate + daily summary) whenever the bucket
 *   is flushed — either automatically at an hour boundary or explicitly via
 *   [flush].
 *
 * @param initialHourTimestamp Epoch-millis for the start of the first hour
 *   bucket. Typically `System.currentTimeMillis()` rounded down to the hour when
 *   the service starts.
 * @param strideLengthKm Stride length in kilometres used to compute
 *   [com.podometer.data.db.DailySummary.totalDistance]. Defaults to 0.00075 km
 *   (75 cm = 0.75 m). This value is fixed at construction time and does not
 *   change for the lifetime of this accumulator instance — by design, the
 *   service reads the preference once at session start and passes it here.
 * @param initialCurrentHourSteps Seed value for [currentHourSteps]. Used on
 *   service restart to restore the current hour's previously-persisted step
 *   count from the database. Defaults to 0.
 * @param initialTotalStepsToday Seed value for [totalStepsToday]. Used on
 *   service restart to restore today's previously-persisted total from the
 *   database. Defaults to 0.
 */
class StepAccumulator(
    initialHourTimestamp: Long,
    private val strideLengthKm: Float = DEFAULT_STRIDE_LENGTH_KM,
    initialCurrentHourSteps: Int = 0,
    initialTotalStepsToday: Int = 0,
) {

    /** Steps accumulated in the current (open) hour bucket. */
    var currentHourSteps: Int = initialCurrentHourSteps
        private set

    /** Running total of steps counted today (resets at midnight). */
    var totalStepsToday: Int = initialTotalStepsToday
        private set

    /** Epoch-millis timestamp for the start of the current open bucket. */
    private var currentHourTimestamp: Long = truncateToHour(initialHourTimestamp)

    /** The calendar date of the current open bucket. Resets totalStepsToday when it changes. */
    private var currentDate: LocalDate = toLocalDate(currentHourTimestamp)

    /**
     * The detected activity for the current open bucket.
     * Defaults to [DEFAULT_ACTIVITY] and resets after each hour rollover.
     * Update via [setActivity].
     *
     * Exposed as a public getter so that [com.podometer.service.StepTrackingService]
     * can read the current activity when writing partial-hour aggregates to the
     * database on each step event (for live dashboard updates).
     */
    var currentActivity: String = DEFAULT_ACTIVITY
        private set

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Updates the detected activity state for the current open hour bucket.
     *
     * The activity is recorded in the [HourlyStepAggregate] when the bucket is
     * flushed. After each hour-boundary rollover the activity resets to
     * [DEFAULT_ACTIVITY] ("WALKING").
     *
     * @param activity A plain string activity name: "WALKING", "CYCLING", or "STILL".
     */
    fun setActivity(activity: String) {
        currentActivity = activity
    }

    /**
     * Adds [delta] steps, checks for an hour-boundary crossing (including
     * midnight date rollovers and multi-hour clock jumps), and returns a
     * [List] of [FlushResult] for each hour bucket that was completed.
     *
     * An empty list means no flush occurred (same hour as the current bucket).
     * A single-element list is the common case of one hour boundary crossed.
     * Multiple elements are produced when the clock jumps forward by more than
     * one hour (DST, timezone change, etc.). Each skipped intermediate hour
     * yields a [FlushResult] with zero [FlushResult.aggregate] step count.
     *
     * When the event time crosses midnight the daily step total is reset so
     * [totalStepsToday] only counts steps for the current calendar day.
     *
     * @param delta Number of new steps (may be 0, never negative).
     * @param now   Current epoch-millis (defaults to [System.currentTimeMillis]).
     * @return A list of [FlushResult] for all completed hour buckets, or an
     *   empty list if no hour boundary was crossed.
     */
    fun addSteps(delta: Int, now: Long = System.currentTimeMillis()): List<FlushResult> {
        val nowHour = truncateToHour(now)

        if (nowHour <= currentHourTimestamp) {
            // Same hour — just accumulate
            currentHourSteps += delta
            totalStepsToday += delta
            return emptyList()
        }

        // Hour boundary crossed: flush current bucket + any intermediate empty hours.
        val results = mutableListOf<FlushResult>()

        // 1. Flush the current (potentially non-empty) bucket.
        results.add(
            buildFlushResult(
                bucketTimestamp = currentHourTimestamp,
                bucketSteps = currentHourSteps,
                totalAfterFlush = totalStepsToday,
                summaryDate = currentDate,
            )
        )

        // 2. Create 0-step aggregates for each intermediate skipped hour.
        var intermediateHour = currentHourTimestamp + ONE_HOUR_MS
        while (intermediateHour < nowHour) {
            val intermediateDate = toLocalDate(intermediateHour)
            if (intermediateDate != currentDate) {
                // Midnight was crossed in the gap — reset the daily running total.
                totalStepsToday = 0
                currentDate = intermediateDate
            }
            results.add(
                buildFlushResult(
                    bucketTimestamp = intermediateHour,
                    bucketSteps = 0,
                    totalAfterFlush = totalStepsToday,
                    summaryDate = currentDate,
                )
            )
            intermediateHour += ONE_HOUR_MS
        }

        // 3. Start the new current bucket for nowHour.
        val nowDate = toLocalDate(nowHour)
        if (nowDate != currentDate) {
            // Midnight crossed right at the boundary between last intermediate and nowHour.
            totalStepsToday = delta
            currentDate = nowDate
        } else {
            totalStepsToday += delta
        }
        currentActivity = DEFAULT_ACTIVITY
        currentHourTimestamp = nowHour
        currentHourSteps = delta

        return results
    }

    /**
     * Explicitly flushes the current open hour bucket (e.g., when the service
     * is stopping mid-hour).
     *
     * Returns an empty list when [currentHourSteps] is zero (nothing to persist).
     * Returns a single-element list otherwise.
     *
     * @param now Current epoch-millis (defaults to [System.currentTimeMillis]).
     * @return A list with one [FlushResult], or an empty list if nothing to flush.
     */
    fun flush(now: Long = System.currentTimeMillis()): List<FlushResult> {
        if (currentHourSteps == 0) return emptyList()

        val result = buildFlushResult(
            bucketTimestamp = currentHourTimestamp,
            bucketSteps = currentHourSteps,
            totalAfterFlush = totalStepsToday,
            summaryDate = currentDate,
        )
        currentHourSteps = 0
        return listOf(result)
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Builds a [FlushResult] for the bucket identified by [bucketTimestamp].
     *
     * @param bucketTimestamp Epoch-millis for the start of the hour bucket being
     *   flushed. Used exclusively for the [HourlyStepAggregate] timestamp.
     * @param bucketSteps Total steps recorded during the bucket.
     * @param totalAfterFlush Running total of steps today at the moment of flush.
     * @param summaryDate The calendar date that [totalAfterFlush] belongs to.
     */
    private fun buildFlushResult(
        bucketTimestamp: Long,
        bucketSteps: Int,
        totalAfterFlush: Int,
        summaryDate: LocalDate,
    ): FlushResult {
        val aggregate = HourlyStepAggregate(
            timestamp = bucketTimestamp,
            stepCountDelta = bucketSteps,
            detectedActivity = currentActivity,
        )
        val dailySummary = DailySummary(
            date = summaryDate.toString(),
            totalSteps = totalAfterFlush,
            totalDistance = totalAfterFlush * strideLengthKm,
        )
        return FlushResult(
            aggregate = aggregate,
            dailySummary = dailySummary,
        )
    }

    // ─── Companion ───────────────────────────────────────────────────────────

    companion object {
        /** Default activity state used for new buckets. */
        const val DEFAULT_ACTIVITY = "WALKING"

        /** Default stride length: 75 cm = 0.00075 km. */
        const val DEFAULT_STRIDE_LENGTH_KM = 0.00075f

        /** Milliseconds in one hour. */
        private const val ONE_HOUR_MS = 3_600_000L

        /**
         * Truncates [epochMillis] to the start of its local hour.
         *
         * Example: 08:37:22.456 → 08:00:00.000
         *
         * Delegates to [DateTimeUtils.truncateToHour].
         */
        fun truncateToHour(epochMillis: Long): Long =
            DateTimeUtils.truncateToHour(epochMillis)

        /**
         * Converts [epochMillis] to the local [LocalDate].
         *
         * Delegates to [DateTimeUtils.toLocalDate].
         */
        private fun toLocalDate(epochMillis: Long): LocalDate =
            DateTimeUtils.toLocalDate(epochMillis)
    }
}
