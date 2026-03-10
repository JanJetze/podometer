// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepBucket
import com.podometer.util.DateTimeUtils
import java.time.LocalDate

/**
 * Pure accumulator for step events; holds no Android-framework references so it
 * can be tested with plain JUnit.
 *
 * Responsibilities:
 * - Track steps within the current 5-minute bucket ([currentBucketSteps]).
 * - Track total steps today ([totalStepsToday]).
 * - Detect 5-minute-boundary crossings on each [addSteps] call.
 * - Detect midnight crossings and reset the daily total for the new day.
 * - Handle multi-bucket clock jumps (DST, timezone changes) by emitting a
 *   [FlushResult] for each skipped intermediate bucket with zero steps.
 * - Produce [FlushResult] (5-minute bucket + daily summary) whenever the bucket
 *   is flushed — either automatically at a bucket boundary or explicitly via
 *   [flush].
 *
 * @param initialBucketTimestamp Epoch-millis for the start of the first 5-minute
 *   bucket. Typically `System.currentTimeMillis()` rounded down to the nearest
 *   5-minute boundary when the service starts.
 * @param strideLengthKm Stride length in kilometres used to compute
 *   [com.podometer.data.db.DailySummary.totalDistance]. Defaults to 0.00075 km
 *   (75 cm = 0.75 m). This value is fixed at construction time and does not
 *   change for the lifetime of this accumulator instance — by design, the
 *   service reads the preference once at session start and passes it here.
 * @param initialCurrentBucketSteps Seed value for [currentBucketSteps]. Used on
 *   service restart to restore the current bucket's previously-persisted step
 *   count from the database. Defaults to 0.
 * @param initialTotalStepsToday Seed value for [totalStepsToday]. Used on
 *   service restart to restore today's previously-persisted total from the
 *   database. Defaults to 0.
 */
class StepAccumulator(
    initialBucketTimestamp: Long,
    private val strideLengthKm: Float = DEFAULT_STRIDE_LENGTH_KM,
    initialCurrentBucketSteps: Int = 0,
    initialTotalStepsToday: Int = 0,
) {

    /** Steps accumulated in the current (open) 5-minute bucket. */
    var currentBucketSteps: Int = initialCurrentBucketSteps
        private set

    /** Running total of steps counted today (resets at midnight). */
    var totalStepsToday: Int = initialTotalStepsToday
        private set

    /** Epoch-millis timestamp for the start of the current open bucket. */
    private var currentBucketTimestamp: Long = truncateToBucket(initialBucketTimestamp)

    /** The calendar date of the current open bucket. Resets totalStepsToday when it changes. */
    private var currentDate: LocalDate = toLocalDate(currentBucketTimestamp)

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Adds [delta] steps, checks for a 5-minute-boundary crossing (including
     * midnight date rollovers and multi-bucket clock jumps), and returns a
     * [List] of [FlushResult] for each bucket that was completed.
     *
     * An empty list means no flush occurred (same bucket as the current one).
     * A single-element list is the common case of one bucket boundary crossed.
     * Multiple elements are produced when the clock jumps forward by more than
     * one bucket (DST, timezone change, etc.). Each skipped intermediate bucket
     * yields a [FlushResult] with zero [FlushResult.bucket] step count.
     *
     * When the event time crosses midnight the daily step total is reset so
     * [totalStepsToday] only counts steps for the current calendar day.
     *
     * @param delta Number of new steps (may be 0, never negative).
     * @param now   Current epoch-millis (defaults to [System.currentTimeMillis]).
     * @return A list of [FlushResult] for all completed buckets, or an
     *   empty list if no bucket boundary was crossed.
     */
    fun addSteps(delta: Int, now: Long = System.currentTimeMillis()): List<FlushResult> {
        val nowBucket = truncateToBucket(now)

        if (nowBucket <= currentBucketTimestamp) {
            // Same bucket — just accumulate
            currentBucketSteps += delta
            totalStepsToday += delta
            return emptyList()
        }

        // Bucket boundary crossed: flush current bucket + any intermediate empty buckets.
        val results = mutableListOf<FlushResult>()

        // 1. Flush the current (potentially non-empty) bucket.
        results.add(
            buildFlushResult(
                bucketTimestamp = currentBucketTimestamp,
                bucketSteps = currentBucketSteps,
                totalAfterFlush = totalStepsToday,
                summaryDate = currentDate,
            )
        )

        // 2. Create 0-step buckets for each intermediate skipped bucket.
        var intermediateBucket = currentBucketTimestamp + FIVE_MINUTES_MS
        while (intermediateBucket < nowBucket) {
            val intermediateDate = toLocalDate(intermediateBucket)
            if (intermediateDate != currentDate) {
                // Midnight was crossed in the gap — reset the daily running total.
                totalStepsToday = 0
                currentDate = intermediateDate
            }
            results.add(
                buildFlushResult(
                    bucketTimestamp = intermediateBucket,
                    bucketSteps = 0,
                    totalAfterFlush = totalStepsToday,
                    summaryDate = currentDate,
                )
            )
            intermediateBucket += FIVE_MINUTES_MS
        }

        // 3. Start the new current bucket for nowBucket.
        val nowDate = toLocalDate(nowBucket)
        if (nowDate != currentDate) {
            // Midnight crossed right at the boundary between last intermediate and nowBucket.
            totalStepsToday = delta
            currentDate = nowDate
        } else {
            totalStepsToday += delta
        }
        currentBucketTimestamp = nowBucket
        currentBucketSteps = delta

        return results
    }

    /**
     * Explicitly flushes the current open bucket (e.g., when the service
     * is stopping mid-bucket).
     *
     * Returns an empty list when [currentBucketSteps] is zero (nothing to persist).
     * Returns a single-element list otherwise.
     *
     * @param now Current epoch-millis (defaults to [System.currentTimeMillis]).
     * @return A list with one [FlushResult], or an empty list if nothing to flush.
     */
    fun flush(now: Long = System.currentTimeMillis()): List<FlushResult> {
        if (currentBucketSteps == 0) return emptyList()

        val result = buildFlushResult(
            bucketTimestamp = currentBucketTimestamp,
            bucketSteps = currentBucketSteps,
            totalAfterFlush = totalStepsToday,
            summaryDate = currentDate,
        )
        currentBucketSteps = 0
        return listOf(result)
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Builds a [FlushResult] for the bucket identified by [bucketTimestamp].
     *
     * @param bucketTimestamp Epoch-millis for the start of the 5-minute bucket
     *   being flushed.
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
        val bucket = StepBucket(
            timestamp = bucketTimestamp,
            stepCount = bucketSteps,
        )
        val dailySummary = DailySummary(
            date = summaryDate.toString(),
            totalSteps = totalAfterFlush,
            totalDistance = totalAfterFlush * strideLengthKm,
        )
        return FlushResult(
            bucket = bucket,
            dailySummary = dailySummary,
        )
    }

    // ─── Companion ───────────────────────────────────────────────────────────

    companion object {
        /** Default stride length: 75 cm = 0.00075 km. */
        const val DEFAULT_STRIDE_LENGTH_KM = 0.00075f

        /** Milliseconds in one 5-minute bucket. */
        const val FIVE_MINUTES_MS = 5 * 60_000L

        /**
         * Truncates [epochMillis] to the start of its local 5-minute bucket.
         *
         * Example: 08:37:22.456 → 08:35:00.000
         *
         * Delegates to [DateTimeUtils.truncateToBucket].
         */
        fun truncateToBucket(epochMillis: Long): Long =
            DateTimeUtils.truncateToBucket(epochMillis)

        /**
         * Converts [epochMillis] to the local [LocalDate].
         *
         * Delegates to [DateTimeUtils.toLocalDate].
         */
        private fun toLocalDate(epochMillis: Long): LocalDate =
            DateTimeUtils.toLocalDate(epochMillis)
    }
}
