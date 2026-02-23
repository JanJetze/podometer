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
 * To reduce false positives the classifier requires [consecutiveWindowsRequired]
 * consecutive cycling windows before emitting a CYCLING transition.
 *
 * ## State machine
 *
 * ```
 * STILL / WALKING  ──2+ cycling windows──►  CYCLING
 * CYCLING          ──non-cycling window──►  WALKING
 * WALKING / CYCLING ──still window──────►  STILL
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
 *   required before a CYCLING transition is emitted. Prevents false positives
 *   from brief high-variance, low-step bursts. Default:
 *   [DEFAULT_CONSECUTIVE_WINDOWS].
 * @param stillVarianceThreshold     Maximum variance below which the device is
 *   considered stationary (gravity-only noise). Default:
 *   [DEFAULT_STILL_VARIANCE_THRESHOLD].
 */
@Singleton
class CyclingClassifier(
    private val varianceThreshold: Double = DEFAULT_VARIANCE_THRESHOLD,
    private val stepFrequencyThreshold: Double = DEFAULT_STEP_FREQ_THRESHOLD,
    private val consecutiveWindowsRequired: Int = DEFAULT_CONSECUTIVE_WINDOWS,
    private val stillVarianceThreshold: Double = DEFAULT_STILL_VARIANCE_THRESHOLD,
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
    )

    // Internal state — protected by @Synchronized on each public method.
    private var currentState: ActivityState = ActivityState.STILL
    private var consecutiveCyclingWindows: Int = 0

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
     * @return [TransitionResult] describing the state change, or `null` if
     *   no transition occurred.
     */
    @Synchronized
    fun evaluate(features: WindowFeatures, stepFrequency: Double): TransitionResult? {
        val variance = features.magnitudeVariance

        // Determine what this window looks like
        val isStillWindow = variance <= stillVarianceThreshold && stepFrequency < stepFrequencyThreshold
        val isCyclingWindow = variance > varianceThreshold && stepFrequency < stepFrequencyThreshold

        // Update consecutive cycling window count
        if (isCyclingWindow) {
            consecutiveCyclingWindows++
        } else {
            consecutiveCyclingWindows = 0
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

        // Cycling transition: enough consecutive cycling windows and not already cycling
        if (consecutiveCyclingWindows >= consecutiveWindowsRequired && currentState != ActivityState.CYCLING) {
            currentState = ActivityState.CYCLING
            return TransitionResult(fromState = previousState, toState = ActivityState.CYCLING)
        }

        // Walking transition: non-cycling window while currently cycling
        if (!isCyclingWindow && currentState == ActivityState.CYCLING) {
            currentState = ActivityState.WALKING
            return TransitionResult(fromState = ActivityState.CYCLING, toState = ActivityState.WALKING)
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
         * Two windows (~10 seconds at a 5-second evaluation period) prevents
         * false positives from brief high-variance, low-step episodes.
         */
        const val DEFAULT_CONSECUTIVE_WINDOWS = 2

        /**
         * Maximum variance considered "still" (gravity-only noise) in (m/s²)².
         *
         * Below 0.5 (m/s²)² the device is essentially stationary; gravity
         * alone accounts for the signal.
         */
        const val DEFAULT_STILL_VARIANCE_THRESHOLD = 0.5
    }
}
