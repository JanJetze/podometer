// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure accumulator for step events; holds no Android-framework references so it
 * can be tested with plain JUnit.
 *
 * Responsibilities:
 * - Track steps within the current hour bucket ([currentHourSteps]).
 * - Track total steps today ([totalStepsToday]).
 * - Detect hour-boundary crossings on each [addSteps] call.
 * - Detect midnight crossings and reset the daily total for the new day.
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
 */
class StepAccumulator(
    initialHourTimestamp: Long,
    private val strideLengthKm: Float = DEFAULT_STRIDE_LENGTH_KM,
) {

    /** Steps accumulated in the current (open) hour bucket. */
    var currentHourSteps: Int = 0
        private set

    /** Running total of steps counted today (resets at midnight). */
    var totalStepsToday: Int = 0
        private set

    /** Epoch-millis timestamp for the start of the current open bucket. */
    private var currentHourTimestamp: Long = truncateToHour(initialHourTimestamp)

    /** The calendar date of the current open bucket. Resets totalStepsToday when it changes. */
    private var currentDate: LocalDate = toLocalDate(currentHourTimestamp)

    /**
     * The detected activity for the current open bucket.
     * Defaults to [DEFAULT_ACTIVITY] and resets after each hour rollover.
     * Update via [setActivity].
     */
    private var currentActivity: String = DEFAULT_ACTIVITY

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
     * Adds [delta] steps, checks for an hour-boundary crossing (including a
     * midnight date rollover), and returns a [FlushResult] if the previous hour
     * bucket was completed, or `null` if no flush occurred.
     *
     * When the event time crosses midnight the daily step total is reset so
     * [totalStepsToday] only counts steps for the current calendar day.
     *
     * @param delta Number of new steps (may be 0, never negative).
     * @param now   Current epoch-millis (defaults to [System.currentTimeMillis]).
     */
    fun addSteps(delta: Int, now: Long = System.currentTimeMillis()): FlushResult? {
        val nowHour = truncateToHour(now)

        return if (nowHour > currentHourTimestamp) {
            // Hour boundary crossed: flush previous bucket, start new one.
            // The flush total captures all steps accumulated so far today (including
            // the steps in the bucket being flushed, which are already in totalStepsToday).
            val flushResult = buildFlushResult(
                bucketTimestamp = currentHourTimestamp,
                bucketSteps = currentHourSteps,
                totalAfterFlush = totalStepsToday,
            )

            // Detect midnight: if the new timestamp is on a different day, reset the
            // daily total before accumulating the new delta.
            val nowDate = toLocalDate(nowHour)
            if (nowDate != currentDate) {
                totalStepsToday = delta
                currentDate = nowDate
            } else {
                totalStepsToday += delta
            }

            // Reset activity to default for the new bucket.
            currentActivity = DEFAULT_ACTIVITY
            currentHourTimestamp = nowHour
            currentHourSteps = delta
            flushResult
        } else {
            currentHourSteps += delta
            totalStepsToday += delta
            null
        }
    }

    /**
     * Explicitly flushes the current open hour bucket (e.g., when the service
     * is stopping mid-hour).
     *
     * Returns `null` when [currentHourSteps] is zero (nothing to persist).
     *
     * @param now Current epoch-millis (defaults to [System.currentTimeMillis]).
     */
    fun flush(now: Long = System.currentTimeMillis()): FlushResult? {
        if (currentHourSteps == 0) return null

        val result = buildFlushResult(
            bucketTimestamp = currentHourTimestamp,
            bucketSteps = currentHourSteps,
            totalAfterFlush = totalStepsToday,
        )
        currentHourSteps = 0
        return result
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun buildFlushResult(
        bucketTimestamp: Long,
        bucketSteps: Int,
        totalAfterFlush: Int,
    ): FlushResult {
        val aggregate = HourlyStepAggregate(
            timestamp = bucketTimestamp,
            stepCountDelta = bucketSteps,
            detectedActivity = currentActivity,
        )
        val date = toLocalDate(bucketTimestamp).toString()
        val dailySummary = DailySummary(
            date = date,
            totalSteps = totalAfterFlush,
            totalDistance = totalAfterFlush * strideLengthKm,
            walkingMinutes = 0,
            cyclingMinutes = 0,
        )
        return FlushResult(aggregate = aggregate, dailySummary = dailySummary)
    }

    // ─── Companion ───────────────────────────────────────────────────────────

    companion object {
        /** Default activity state used for new buckets. */
        const val DEFAULT_ACTIVITY = "WALKING"

        /** Default stride length: 75 cm = 0.00075 km. */
        const val DEFAULT_STRIDE_LENGTH_KM = 0.00075f

        /**
         * Truncates [epochMillis] to the start of its local hour.
         *
         * Example: 08:37:22.456 → 08:00:00.000
         */
        fun truncateToHour(epochMillis: Long): Long {
            val zone = ZoneId.systemDefault()
            return Instant.ofEpochMilli(epochMillis)
                .atZone(zone)
                .let { zdt ->
                    zdt.withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
                }
        }

        /**
         * Converts [epochMillis] to the local [LocalDate].
         */
        private fun toLocalDate(epochMillis: Long): LocalDate =
            Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
    }
}
