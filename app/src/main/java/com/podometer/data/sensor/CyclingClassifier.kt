// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import com.podometer.domain.model.ActivityState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heuristic classifier that detects cycling vs walking vs still activity by
 * combining accelerometer variance with step-cadence frequency.
 *
 * ## V2 — Density-based walking detection
 *
 * Window classification:
 * - **Still:** `magnitudeVariance ≤ 0.5` AND `stepFrequency < 0.3 Hz`
 * - **Cycling window:** `magnitudeVariance > 2.0` AND `stepFrequency < 0.3 Hz`
 * - **Walking window:** `stepFrequency ≥ 1.5 Hz` (regardless of variance)
 *
 * Walking detection uses a density-based approach: the classifier maintains a
 * rolling time window (default 5 minutes) and counts how many evaluations within
 * that window have `stepFrequency ≥ 1.5 Hz`. This tolerates sensor throttling
 * gaps that broke the previous consecutive-window requirement.
 *
 * The `stepFrequencyHz` value from [StepFrequencyTracker] already encodes a
 * 30-second sliding window of step events, so each reading represents 30 seconds
 * of continuous step history — even if the accelerometer was throttled during
 * part of that interval.
 *
 * ## State machine
 *
 * ```
 * STILL    ──≥5 walking windows in 5 min──────────────────► WALKING
 * STILL    ──6+ consecutive cycling windows & ≥60 s────────► CYCLING
 * WALKING  ──<2 walking windows in 5 min──────────────────► STILL
 * WALKING  ──still for ≥ 2 min────────────────────────────► STILL
 * WALKING  ──6+ consecutive cycling windows & ≥60 s────────► CYCLING
 * CYCLING  ──8+ consecutive windows with hz ≥ 2.0─────────► WALKING
 * CYCLING  ──still for ≥ 3 min────────────────────────────► STILL
 * ```
 *
 * Cycling exit requires `hz ≥ 2.0` (not 1.5) because road/bike vibration
 * generates false step events at 0.4–1.8 Hz. Requiring 2.0 Hz for 8 consecutive
 * windows filters these out while still detecting genuine walking.
 *
 * ## Thread safety
 *
 * All public methods are `@Synchronized` on this instance.
 *
 * ## Pure Kotlin
 *
 * This class has no Android framework dependencies and is fully unit-testable
 * on the JVM.
 */
@Singleton
class CyclingClassifier(
    private val varianceThreshold: Double = DEFAULT_VARIANCE_THRESHOLD,
    private val stepFrequencyThreshold: Double = DEFAULT_STEP_FREQ_THRESHOLD,
    private val consecutiveWindowsRequired: Int = DEFAULT_CONSECUTIVE_WINDOWS,
    private val stillVarianceThreshold: Double = DEFAULT_STILL_VARIANCE_THRESHOLD,
    private val minCyclingDurationMs: Long = DEFAULT_MIN_CYCLING_DURATION_MS,
    private val walkingGracePeriodMs: Long = DEFAULT_WALKING_GRACE_PERIOD_MS,
    private val cyclingGracePeriodMs: Long = DEFAULT_CYCLING_GRACE_PERIOD_MS,
    private val walkingHzThreshold: Double = DEFAULT_WALKING_HZ_THRESHOLD,
    private val walkingEntryCount: Int = DEFAULT_WALKING_ENTRY_COUNT,
    private val walkingExitCount: Int = DEFAULT_WALKING_EXIT_COUNT,
    private val densityWindowMs: Long = DEFAULT_DENSITY_WINDOW_MS,
    private val cyclingWalkExitHz: Double = DEFAULT_CYCLING_WALK_EXIT_HZ,
    private val cyclingWalkExitCount: Int = DEFAULT_CYCLING_WALK_EXIT_COUNT,
) {

    /**
     * No-argument constructor used by Hilt for dependency injection.
     */
    @Inject
    constructor() : this(
        varianceThreshold = DEFAULT_VARIANCE_THRESHOLD,
        stepFrequencyThreshold = DEFAULT_STEP_FREQ_THRESHOLD,
        consecutiveWindowsRequired = DEFAULT_CONSECUTIVE_WINDOWS,
        stillVarianceThreshold = DEFAULT_STILL_VARIANCE_THRESHOLD,
        minCyclingDurationMs = DEFAULT_MIN_CYCLING_DURATION_MS,
        walkingGracePeriodMs = DEFAULT_WALKING_GRACE_PERIOD_MS,
        cyclingGracePeriodMs = DEFAULT_CYCLING_GRACE_PERIOD_MS,
        walkingHzThreshold = DEFAULT_WALKING_HZ_THRESHOLD,
        walkingEntryCount = DEFAULT_WALKING_ENTRY_COUNT,
        walkingExitCount = DEFAULT_WALKING_EXIT_COUNT,
        densityWindowMs = DEFAULT_DENSITY_WINDOW_MS,
        cyclingWalkExitHz = DEFAULT_CYCLING_WALK_EXIT_HZ,
        cyclingWalkExitCount = DEFAULT_CYCLING_WALK_EXIT_COUNT,
    )

    // Internal state — protected by @Synchronized on each public method.
    private var currentState: ActivityState = ActivityState.STILL
    private var consecutiveCyclingWindows: Int = 0
    private var cyclingWindowStartTimeMs: Long = -1L
    private var consecutiveStillWindows: Int = 0
    private var stillWindowStartTimeMs: Long = -1L

    // Density-based walking: rolling buffer of (timestamp, stepFrequency) pairs.
    private val densityTimestamps = LongArray(DENSITY_BUFFER_CAPACITY)
    private val densityFrequencies = DoubleArray(DENSITY_BUFFER_CAPACITY)
    private var densityHead: Int = 0
    private var densityCount: Int = 0

    // Cycling → Walking exit: consecutive windows with high hz.
    private var consecutiveHighWalkInCycling: Int = 0
    private var highWalkInCyclingStartMs: Long = 0L

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Evaluates the supplied sensor features and returns a [TransitionResult]
     * if the activity state changed, or `null` if the state is unchanged.
     *
     * Call this periodically (e.g., every 30 seconds) from a background
     * coroutine, feeding the latest [WindowFeatures] from
     * [AccelerometerSampleBuffer.computeWindowFeatures] and the current
     * [stepFrequency] from [StepFrequencyTracker.computeStepFrequency].
     */
    @Synchronized
    fun evaluate(features: WindowFeatures, stepFrequency: Double, currentTimeMs: Long): TransitionResult? {
        val variance = features.magnitudeVariance

        val isStillWindow = variance <= stillVarianceThreshold && stepFrequency < stepFrequencyThreshold
        val isCyclingWindow = variance > varianceThreshold && stepFrequency < stepFrequencyThreshold

        // Update density buffer
        addToDensityBuffer(currentTimeMs, stepFrequency)

        // Update consecutive cycling window count
        if (isCyclingWindow) {
            if (consecutiveCyclingWindows == 0) {
                cyclingWindowStartTimeMs = currentTimeMs
            }
            consecutiveCyclingWindows++
        } else {
            consecutiveCyclingWindows = 0
            cyclingWindowStartTimeMs = -1L
        }

        // Update consecutive still window count
        if (isStillWindow) {
            if (consecutiveStillWindows == 0) {
                stillWindowStartTimeMs = currentTimeMs
            }
            consecutiveStillWindows++
        } else {
            consecutiveStillWindows = 0
            stillWindowStartTimeMs = -1L
        }

        val previousState = currentState

        // Cycling transition: checked first in all states
        val durationSatisfied = cyclingWindowStartTimeMs >= 0L &&
            (currentTimeMs - cyclingWindowStartTimeMs) >= minCyclingDurationMs
        if (consecutiveCyclingWindows >= consecutiveWindowsRequired
            && durationSatisfied
            && currentState != ActivityState.CYCLING
        ) {
            currentState = ActivityState.CYCLING
            consecutiveHighWalkInCycling = 0
            highWalkInCyclingStartMs = 0L
            return TransitionResult(
                fromState = previousState,
                toState = ActivityState.CYCLING,
                effectiveTimestamp = currentTimeMs,
            )
        }

        val walkingCount = countWalkingInDensityWindow(currentTimeMs)

        return when (currentState) {
            ActivityState.STILL -> {
                if (walkingCount >= walkingEntryCount) {
                    val firstWalkTs = firstWalkingTimestampInWindow(currentTimeMs)
                    currentState = ActivityState.WALKING
                    TransitionResult(
                        fromState = previousState,
                        toState = ActivityState.WALKING,
                        effectiveTimestamp = firstWalkTs,
                    )
                } else {
                    null
                }
            }

            ActivityState.WALKING -> {
                // Grace period: sustained stillness exits to STILL
                if (isStillWindow && stillWindowStartTimeMs >= 0L
                    && (currentTimeMs - stillWindowStartTimeMs) >= walkingGracePeriodMs
                ) {
                    currentState = ActivityState.STILL
                    TransitionResult(
                        fromState = previousState,
                        toState = ActivityState.STILL,
                        effectiveTimestamp = stillWindowStartTimeMs,
                    )
                } else if (walkingCount < walkingExitCount) {
                    // Density-based exit: not enough walking windows in the rolling window
                    currentState = ActivityState.STILL
                    TransitionResult(
                        fromState = previousState,
                        toState = ActivityState.STILL,
                        effectiveTimestamp = currentTimeMs,
                    )
                } else {
                    null
                }
            }

            ActivityState.CYCLING -> {
                // Track consecutive high-hz walking windows for cycling exit
                if (stepFrequency >= cyclingWalkExitHz) {
                    if (consecutiveHighWalkInCycling == 0) {
                        highWalkInCyclingStartMs = currentTimeMs
                    }
                    consecutiveHighWalkInCycling++
                } else {
                    consecutiveHighWalkInCycling = 0
                    highWalkInCyclingStartMs = 0L
                }

                if (consecutiveHighWalkInCycling >= cyclingWalkExitCount) {
                    val effectiveTs = highWalkInCyclingStartMs
                    consecutiveHighWalkInCycling = 0
                    highWalkInCyclingStartMs = 0L
                    currentState = ActivityState.WALKING
                    TransitionResult(
                        fromState = previousState,
                        toState = ActivityState.WALKING,
                        effectiveTimestamp = effectiveTs,
                    )
                } else if (isStillWindow && stillWindowStartTimeMs >= 0L
                    && (currentTimeMs - stillWindowStartTimeMs) >= cyclingGracePeriodMs
                ) {
                    currentState = ActivityState.STILL
                    TransitionResult(
                        fromState = previousState,
                        toState = ActivityState.STILL,
                        effectiveTimestamp = stillWindowStartTimeMs,
                    )
                } else {
                    null
                }
            }
        }
    }

    /**
     * Returns the current activity state as determined by the most recent
     * [evaluate] call.
     */
    @Synchronized
    fun getCurrentState(): ActivityState = currentState

    /**
     * Resets the classifier to its initial state.
     */
    @Synchronized
    fun reset() {
        currentState = ActivityState.STILL
        consecutiveCyclingWindows = 0
        cyclingWindowStartTimeMs = -1L
        consecutiveStillWindows = 0
        stillWindowStartTimeMs = -1L
        densityHead = 0
        densityCount = 0
        consecutiveHighWalkInCycling = 0
        highWalkInCyclingStartMs = 0L
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun addToDensityBuffer(timestampMs: Long, stepFrequency: Double) {
        densityTimestamps[densityHead] = timestampMs
        densityFrequencies[densityHead] = stepFrequency
        densityHead = (densityHead + 1) % DENSITY_BUFFER_CAPACITY
        if (densityCount < DENSITY_BUFFER_CAPACITY) densityCount++
    }

    private fun countWalkingInDensityWindow(currentTimeMs: Long): Int {
        val cutoff = currentTimeMs - densityWindowMs
        var count = 0
        val startIdx = (densityHead - densityCount + DENSITY_BUFFER_CAPACITY) % DENSITY_BUFFER_CAPACITY
        for (i in 0 until densityCount) {
            val idx = (startIdx + i) % DENSITY_BUFFER_CAPACITY
            if (densityTimestamps[idx] >= cutoff && densityFrequencies[idx] >= walkingHzThreshold) {
                count++
            }
        }
        return count
    }

    private fun firstWalkingTimestampInWindow(currentTimeMs: Long): Long {
        val cutoff = currentTimeMs - densityWindowMs
        val startIdx = (densityHead - densityCount + DENSITY_BUFFER_CAPACITY) % DENSITY_BUFFER_CAPACITY
        for (i in 0 until densityCount) {
            val idx = (startIdx + i) % DENSITY_BUFFER_CAPACITY
            if (densityTimestamps[idx] >= cutoff && densityFrequencies[idx] >= walkingHzThreshold) {
                return densityTimestamps[idx]
            }
        }
        return currentTimeMs
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        /** Accelerometer magnitude variance threshold for cycling detection (m/s²)². */
        const val DEFAULT_VARIANCE_THRESHOLD = 2.0

        /** Maximum step frequency (Hz) for a window to be classified as cycling. */
        const val DEFAULT_STEP_FREQ_THRESHOLD = 0.3

        /** Consecutive cycling windows required before entering CYCLING. */
        const val DEFAULT_CONSECUTIVE_WINDOWS = 6

        /** Maximum variance considered still (gravity-only noise) in (m/s²)². */
        const val DEFAULT_STILL_VARIANCE_THRESHOLD = 0.5

        /** Minimum elapsed duration of consecutive cycling windows before CYCLING (ms). */
        const val DEFAULT_MIN_CYCLING_DURATION_MS = 60_000L

        /** Grace period before WALKING transitions to STILL (ms). */
        const val DEFAULT_WALKING_GRACE_PERIOD_MS = 120_000L

        /** Grace period before CYCLING transitions to STILL (ms). */
        const val DEFAULT_CYCLING_GRACE_PERIOD_MS = 180_000L

        /** Minimum step frequency (Hz) for a window to count as walking in the density check. */
        const val DEFAULT_WALKING_HZ_THRESHOLD = 1.5

        /** Number of walking windows in the density window required to enter WALKING. */
        const val DEFAULT_WALKING_ENTRY_COUNT = 5

        /** Walking window count below which WALKING exits to STILL. */
        const val DEFAULT_WALKING_EXIT_COUNT = 2

        /** Rolling time window (ms) for walking density calculation. */
        const val DEFAULT_DENSITY_WINDOW_MS = 300_000L

        /** Step frequency (Hz) required for cycling→walking exit (higher than entry to
         *  filter road vibration false positives). */
        const val DEFAULT_CYCLING_WALK_EXIT_HZ = 2.0

        /** Consecutive windows with hz ≥ [DEFAULT_CYCLING_WALK_EXIT_HZ] to exit cycling. */
        const val DEFAULT_CYCLING_WALK_EXIT_COUNT = 8

        /** Pre-allocated density buffer capacity. 600 entries covers 5 hours at 30s intervals. */
        internal const val DENSITY_BUFFER_CAPACITY = 600
    }
}
