// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import com.podometer.domain.model.ActivityState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heuristic classifier that detects cycling vs walking vs still activity by
 * combining accelerometer variance with step-cadence frequency.
 *
 * ## V1 Heuristic
 *
 * A window is classified as a "cycling window" when:
 * 1. `magnitudeVariance > varianceThreshold` — there is enough motion to rule
 *    out a stationary state; AND
 * 2. `stepFrequency < stepFrequencyThreshold` — the step cadence is too low to
 *    be walking (cycling does not produce step-counter events).
 *
 * A window is classified as a "still window" when:
 * - `magnitudeVariance <= stillVarianceThreshold` AND `stepFrequency ≈ 0`
 *
 * To reduce false positives the classifier requires:
 * 1. At least [consecutiveWindowsRequired] consecutive cycling windows (minimum
 *    stability check); AND
 * 2. The elapsed time since the first consecutive cycling window is at least
 *    [minCyclingDurationMs] (default 60 seconds).  This directly satisfies the
 *    product spec: "step counter reports zero/very few steps for >60 seconds →
 *    classify as cycling".
 *
 * ## State machine
 *
 * ```
 * STILL / WALKING  ──2+ cycling windows & >=60 s──────────────────►  CYCLING
 * STILL            ──non-still, non-cycling window────────────────►  WALKING
 * CYCLING          ──non-still, non-cycling window────────────────►  WALKING
 * WALKING / CYCLING ──still window──────────────────────────────►  STILL
 * ```
 *
 * ## Thread safety
 *
 * All public methods are `@Synchronized` on this instance.  The lock is
 * acquired at most once per classifier evaluation period (~5 seconds), so
 * contention overhead is negligible.
 *
 * ## Pure Kotlin
 *
 * This class has no Android framework dependencies and is fully unit-testable
 * on the JVM.
 *
 * @param varianceThreshold          Minimum accelerometer magnitude variance
 *   (in (m/s²)²) to consider a window as containing motion indicative of
 *   cycling. Default: [DEFAULT_VARIANCE_THRESHOLD].
 * @param stepFrequencyThreshold     Maximum step frequency (Hz) allowed while
 *   still classifying a window as cycling. Cycling generates very few or no
 *   step events. Default: [DEFAULT_STEP_FREQ_THRESHOLD].
 * @param consecutiveWindowsRequired Number of back-to-back cycling windows
 *   required before a CYCLING transition is emitted. This is the minimum
 *   stability check; the primary gate is [minCyclingDurationMs]. Default:
 *   [DEFAULT_CONSECUTIVE_WINDOWS].
 * @param stillVarianceThreshold     Maximum variance below which the device is
 *   considered stationary (gravity-only noise). Default:
 *   [DEFAULT_STILL_VARIANCE_THRESHOLD].
 * @param minCyclingDurationMs       Minimum elapsed time in milliseconds from
 *   the first consecutive cycling window to when the CYCLING transition may be
 *   emitted.  Satisfies the spec requirement: zero/very few steps for at least
 *   60 seconds before classifying as cycling. Default:
 *   [DEFAULT_MIN_CYCLING_DURATION_MS].
 */
@Singleton
class CyclingClassifier(
    private val varianceThreshold: Double = DEFAULT_VARIANCE_THRESHOLD,
    private val stepFrequencyThreshold: Double = DEFAULT_STEP_FREQ_THRESHOLD,
    private val consecutiveWindowsRequired: Int = DEFAULT_CONSECUTIVE_WINDOWS,
    private val stillVarianceThreshold: Double = DEFAULT_STILL_VARIANCE_THRESHOLD,
    private val minCyclingDurationMs: Long = DEFAULT_MIN_CYCLING_DURATION_MS,
) {

    /**
     * No-argument constructor used by Hilt for dependency injection.
     *
     * Uses default threshold constants. The parameterised constructor is
     * provided for unit tests that need custom thresholds without requiring
     * the DI graph.
     */
    @Inject
    constructor() : this(
        varianceThreshold = DEFAULT_VARIANCE_THRESHOLD,
        stepFrequencyThreshold = DEFAULT_STEP_FREQ_THRESHOLD,
        consecutiveWindowsRequired = DEFAULT_CONSECUTIVE_WINDOWS,
        stillVarianceThreshold = DEFAULT_STILL_VARIANCE_THRESHOLD,
        minCyclingDurationMs = DEFAULT_MIN_CYCLING_DURATION_MS,
    )

    // Internal state — protected by @Synchronized on each public method.
    private var currentState: ActivityState = ActivityState.STILL
    private var consecutiveCyclingWindows: Int = 0

    /**
     * Wall-clock timestamp (from [System.currentTimeMillis]) of the first
     * cycling window in the current consecutive streak.  Reset to `0L` when
     * the streak is broken or [reset] is called.
     */
    private var cyclingWindowStartTimeMs: Long = 0L

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Evaluates the supplied sensor features and returns a [TransitionResult]
     * if the activity state changed, or `null` if the state is unchanged.
     *
     * Call this periodically (e.g., every 5 seconds) from a background
     * coroutine, feeding the latest [WindowFeatures] from
     * [AccelerometerSampleBuffer.computeWindowFeatures] and the current
     * [stepFrequency] from [StepFrequencyTracker.computeStepFrequency].
     *
     * @param features       Feature vector computed from the accelerometer
     *   sliding window.
     * @param stepFrequency  Step cadence in Hz from [StepFrequencyTracker].
     * @param currentTimeMs  Current wall-clock time in milliseconds, typically
     *   `System.currentTimeMillis()`.  Used to enforce [minCyclingDurationMs].
     * @return [TransitionResult] describing the state change, or `null` if
     *   no transition occurred.
     */
    @Synchronized
    fun evaluate(features: WindowFeatures, stepFrequency: Double, currentTimeMs: Long): TransitionResult? {
        val variance = features.magnitudeVariance

        // Determine what this window looks like
        val isStillWindow = variance <= stillVarianceThreshold && stepFrequency < stepFrequencyThreshold
        val isCyclingWindow = variance > varianceThreshold && stepFrequency < stepFrequencyThreshold

        // Update consecutive cycling window count and track when the streak started
        if (isCyclingWindow) {
            if (consecutiveCyclingWindows == 0) {
                // First window of a new streak — record when it started
                cyclingWindowStartTimeMs = currentTimeMs
            }
            consecutiveCyclingWindows++
        } else {
            consecutiveCyclingWindows = 0
            cyclingWindowStartTimeMs = 0L
        }

        val previousState = currentState

        // Still detection takes priority: stationary regardless of current state
        if (isStillWindow) {
            currentState = ActivityState.STILL
            return if (previousState != ActivityState.STILL) {
                TransitionResult(fromState = previousState, toState = ActivityState.STILL)
            } else {
                null
            }
        }

        // Cycling transition: enough consecutive cycling windows, duration gate
        // satisfied, and not already cycling
        val durationSatisfied = (currentTimeMs - cyclingWindowStartTimeMs) >= minCyclingDurationMs
        if (consecutiveCyclingWindows >= consecutiveWindowsRequired
            && durationSatisfied
            && currentState != ActivityState.CYCLING
        ) {
            currentState = ActivityState.CYCLING
            return TransitionResult(fromState = previousState, toState = ActivityState.CYCLING)
        }

        // Walking transition:
        //   - From CYCLING: any non-cycling, non-still window (variance drop or steps resuming)
        //   - From STILL: requires step activity (stepFrequency >= stepFrequencyThreshold) to
        //     distinguish purposeful walking from ambiguous low-motion gap-zone windows
        val isWalkingTransition = !isCyclingWindow && !isStillWindow && when (currentState) {
            ActivityState.CYCLING -> true
            ActivityState.STILL -> stepFrequency >= stepFrequencyThreshold
            ActivityState.WALKING -> false
        }
        if (isWalkingTransition) {
            currentState = ActivityState.WALKING
            return TransitionResult(fromState = previousState, toState = ActivityState.WALKING)
        }

        // No state change
        return null
    }

    /**
     * Returns the current activity state as determined by the most recent
     * [evaluate] call.
     *
     * Starts as [ActivityState.STILL] and is reset to [ActivityState.STILL]
     * by [reset].
     */
    @Synchronized
    fun getCurrentState(): ActivityState = currentState

    /**
     * Resets the classifier to its initial state.
     *
     * Call this when the sensor session ends (e.g., [StepTrackingService]
     * `onDestroy`) so that stale state from a previous session does not
     * contaminate the next one.
     */
    @Synchronized
    fun reset() {
        currentState = ActivityState.STILL
        consecutiveCyclingWindows = 0
        cyclingWindowStartTimeMs = 0L
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        /**
         * Default accelerometer magnitude variance threshold in (m/s²)².
         *
         * A value of 2.0 is above gravity-only sensor noise (~0.2–0.5) but
         * indicates purposeful motion. Cycling on typical roads produces
         * variances well above this level.
         */
        const val DEFAULT_VARIANCE_THRESHOLD = 2.0

        /**
         * Default step frequency threshold in Hz.
         *
         * Walking cadence is typically 1.5–2.5 Hz. A threshold of 0.3 Hz
         * means "effectively no step events" — consistent with cycling, which
         * does not generate step-counter events.
         */
        const val DEFAULT_STEP_FREQ_THRESHOLD = 0.3

        /**
         * Default number of consecutive cycling windows required before the
         * CYCLING transition is emitted.
         *
         * Two windows (~10 seconds at a 5-second evaluation period) provides
         * minimum stability against brief high-variance, low-step episodes.
         * The primary gate is [DEFAULT_MIN_CYCLING_DURATION_MS].
         */
        const val DEFAULT_CONSECUTIVE_WINDOWS = 2

        /**
         * Maximum variance considered "still" (gravity-only noise) in (m/s²)².
         *
         * Below 0.5 (m/s²)² the device is essentially stationary; gravity
         * alone accounts for the signal.
         */
        const val DEFAULT_STILL_VARIANCE_THRESHOLD = 0.5

        /**
         * Default minimum elapsed duration of consecutive cycling windows
         * before a CYCLING transition is emitted, in milliseconds.
         *
         * 60 000 ms = 60 seconds satisfies the product spec:
         * "step counter reports zero/very few steps for >60 seconds →
         * classify as cycling".
         */
        const val DEFAULT_MIN_CYCLING_DURATION_MS = 60_000L
    }
}
