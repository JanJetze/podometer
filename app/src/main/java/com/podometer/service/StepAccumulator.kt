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
 * - Produce [FlushResult] (hourly aggregate + daily summary) whenever the bucket
 *   is flushed — either automatically at an hour boundary or explicitly via
 *   [flush].
 *
 * @param initialHourTimestamp Epoch-millis for the start of the first hour
 *   bucket. Typically `System.currentTimeMillis()` rounded down to the hour when
 *   the service starts.
 */
class StepAccumulator(initialHourTimestamp: Long) {

    /** Steps accumulated in the current (open) hour bucket. */
    var currentHourSteps: Int = 0
        private set

    /** Running total of steps counted today (survives hour rollovers). */
    var totalStepsToday: Int = 0
        private set

    /** Epoch-millis timestamp for the start of the current open bucket. */
    private var currentHourTimestamp: Long = truncateToHour(initialHourTimestamp)

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Adds [delta] steps, checks for an hour-boundary crossing, and returns a
     * [FlushResult] if the previous hour bucket was completed, or `null` if no
     * flush occurred.
     *
     * @param delta Number of new steps (may be 0, never negative).
     * @param now   Current epoch-millis (defaults to [System.currentTimeMillis]).
     */
    fun addSteps(delta: Int, now: Long = System.currentTimeMillis()): FlushResult? {
        val nowHour = truncateToHour(now)

        return if (nowHour > currentHourTimestamp) {
            // Hour boundary crossed: flush previous bucket, start new one.
            val flushResult = buildFlushResult(
                bucketTimestamp = currentHourTimestamp,
                bucketSteps = currentHourSteps,
                totalAfterFlush = totalStepsToday + delta,
            )
            // Update total *before* resetting the hour bucket.
            totalStepsToday += delta
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
            detectedActivity = "WALKING",
        )
        val date = Instant.ofEpochMilli(bucketTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()          // produces "yyyy-MM-dd" via LocalDate.toString()
        val dailySummary = DailySummary(
            date = date,
            totalSteps = totalAfterFlush,
            totalDistance = 0f,
            walkingMinutes = 0,
            cyclingMinutes = 0,
        )
        return FlushResult(aggregate = aggregate, dailySummary = dailySummary)
    }

    // ─── Companion ───────────────────────────────────────────────────────────

    companion object {
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
    }
}
