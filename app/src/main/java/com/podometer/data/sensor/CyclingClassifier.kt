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
 * STILL            ──4+ walking windows (~20s)─────────────►  WALKING
 * STILL / WALKING  ──2+ cycling windows & >=60 s───────────►  CYCLING
 * CYCLING          ──non-still, non-cycling window─────────►  WALKING
 * WALKING          ──still for >= 2 min────────────────────►  STILL
 * CYCLING          ──still for >= 3 min────────────────────►  STILL
 * ```
 *
 * Grace periods prevent noisy fragmentation: a crosswalk pause no longer splits
 * a walk into two segments, and kitchen steps require sustained walking (~20 s)
 * before entering WALKING.
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
 * @param walkingGracePeriodMs       Duration of sustained stillness (ms) required
 *   before WALKING transitions to STILL. Brief pauses (crosswalk, traffic light)
 *   are absorbed. Default: [DEFAULT_WALKING_GRACE_PERIOD_MS] (2 minutes).
 * @param cyclingGracePeriodMs       Duration of sustained stillness (ms) required
 *   before CYCLING transitions to STILL. Brief stops (red light, intersection)
 *   are absorbed. Default: [DEFAULT_CYCLING_GRACE_PERIOD_MS] (3 minutes).
 * @param consecutiveWalkingWindowsRequired Number of consecutive walking windows
 *   required before STILL transitions to WALKING. Filters spurious walking from
 *   kitchen steps or brief hand movements. Default:
 *   [DEFAULT_CONSECUTIVE_WALKING_WINDOWS] (~20 seconds at 5-second evaluation).
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
    private val consecutiveWalkingWindowsRequired: Int = DEFAULT_CONSECUTIVE_WALKING_WINDOWS,
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
        walkingGracePeriodMs = DEFAULT_WALKING_GRACE_PERIOD_MS,
        cyclingGracePeriodMs = DEFAULT_CYCLING_GRACE_PERIOD_MS,
        consecutiveWalkingWindowsRequired = DEFAULT_CONSECUTIVE_WALKING_WINDOWS,
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

    /** Number of consecutive still windows observed. Used for grace-period tracking. */
    private var consecutiveStillWindows: Int = 0

    /** Timestamp of the first still window in the current consecutive still streak. */
    private var stillWindowStartTimeMs: Long = 0L

    /** Number of consecutive walking windows observed from STILL (for entry threshold). */
    private var consecutiveWalkingWindows: Int = 0

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
        val isWalkingWindow = !isCyclingWindow && !isStillWindow && stepFrequency >= stepFrequencyThreshold

        // Update consecutive cycling window count and track when the streak started
        if (isCyclingWindow) {
            if (consecutiveCyclingWindows == 0) {
                cyclingWindowStartTimeMs = currentTimeMs
            }
            consecutiveCyclingWindows++
        } else {
            consecutiveCyclingWindows = 0
            cyclingWindowStartTimeMs = 0L
        }

        // Update consecutive still window count
        if (isStillWindow) {
            if (consecutiveStillWindows == 0) {
                stillWindowStartTimeMs = currentTimeMs
            }
            consecutiveStillWindows++
        } else {
            consecutiveStillWindows = 0
            stillWindowStartTimeMs = 0L
        }

        // Update consecutive walking windows (for STILL → WALKING threshold)
        if (isWalkingWindow) {
            consecutiveWalkingWindows++
        } else {
            consecutiveWalkingWindows = 0
        }

        val previousState = currentState

        // Cycling transition: enough consecutive cycling windows, duration gate
        // satisfied, and not already cycling — checked first in all states
        val durationSatisfied = (currentTimeMs - cyclingWindowStartTimeMs) >= minCyclingDurationMs
        if (consecutiveCyclingWindows >= consecutiveWindowsRequired
            && durationSatisfied
            && currentState != ActivityState.CYCLING
        ) {
            currentState = ActivityState.CYCLING
            return TransitionResult(
                fromState = previousState,
                toState = ActivityState.CYCLING,
                effectiveTimestamp = currentTimeMs,
            )
        }

        return when (currentState) {
            ActivityState.STILL -> {
                // Walking entry requires sustained walking to filter kitchen steps
                if (isWalkingWindow && consecutiveWalkingWindows >= consecutiveWalkingWindowsRequired) {
                    currentState = ActivityState.WALKING
                    TransitionResult(
                        fromState = previousState,
                        toState = ActivityState.WALKING,
                        effectiveTimestamp = currentTimeMs,
                    )
                } else {
                    null
                }
            }

            ActivityState.WALKING -> {
                if (isStillWindow) {
                    val stillDuration = currentTimeMs - stillWindowStartTimeMs
                    if (stillDuration >= walkingGracePeriodMs) {
                        currentState = ActivityState.STILL
                        TransitionResult(
                            fromState = previousState,
                            toState = ActivityState.STILL,
                            effectiveTimestamp = stillWindowStartTimeMs,
                        )
                    } else {
                        null // within grace period — stay WALKING
                    }
                } else {
                    null // non-still window — stay WALKING
                }
            }

            ActivityState.CYCLING -> {
                // Non-still, non-cycling window → WALKING (immediate, no grace period)
                if (!isCyclingWindow && !isStillWindow) {
                    currentState = ActivityState.WALKING
                    TransitionResult(
                        fromState = previousState,
                        toState = ActivityState.WALKING,
                        effectiveTimestamp = currentTimeMs,
                    )
                } else if (isStillWindow) {
                    val stillDuration = currentTimeMs - stillWindowStartTimeMs
                    if (stillDuration >= cyclingGracePeriodMs) {
                        currentState = ActivityState.STILL
                        TransitionResult(
                            fromState = previousState,
                            toState = ActivityState.STILL,
                            effectiveTimestamp = stillWindowStartTimeMs,
                        )
                    } else {
                        null // within grace period — stay CYCLING
                    }
                } else {
                    null // cycling window — stay CYCLING
                }
            }
        }
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
        consecutiveStillWindows = 0
        stillWindowStartTimeMs = 0L
        consecutiveWalkingWindows = 0
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

        /**
         * Default grace period before WALKING transitions to STILL, in ms.
         *
         * 120 000 ms = 2 minutes. Brief pauses (crosswalks, traffic lights)
         * do not fragment a walking session.
         */
        const val DEFAULT_WALKING_GRACE_PERIOD_MS = 120_000L

        /**
         * Default grace period before CYCLING transitions to STILL, in ms.
         *
         * 180 000 ms = 3 minutes. Brief stops (red lights, intersections)
         * do not fragment a cycling session.
         */
        const val DEFAULT_CYCLING_GRACE_PERIOD_MS = 180_000L

        /**
         * Default number of consecutive walking windows required before
         * STILL transitions to WALKING.
         *
         * Four windows (~20 seconds at a 5-second evaluation period) filters
         * out spurious walking from kitchen steps or brief hand movements.
         */
        const val DEFAULT_CONSECUTIVE_WALKING_WINDOWS = 4
    }
}
