// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import android.hardware.SensorEvent
import kotlin.math.sqrt

/**
 * Last-resort step detector driven by the raw accelerometer (TYPE_ACCELEROMETER).
 *
 * Algorithm:
 *  1. Compute the acceleration vector magnitude: `sqrt(x² + y² + z²)`.
 *  2. Apply an EMA (exponential moving average) low-pass filter to suppress
 *     high-frequency vibration noise.
 *  3. Detect a *rising edge* past [THRESHOLD_RISE] with hysteresis: once the
 *     filtered magnitude climbs above [THRESHOLD_RISE] the detector is "armed"
 *     and will not re-fire until the signal drops below [THRESHOLD_FALL].
 *  4. A minimum-time gate of [MIN_STEP_MS] milliseconds prevents rapid-fire
 *     false positives from vibration bursts.
 *
 * This class is `internal` — it is not part of the public API of this module.
 *
 * @see StepSensorManager
 */
internal class AccelerometerStepDetector {

    // ─── Mutable state ────────────────────────────────────────────────────────
    //
    // Threading model: [processSample] (and therefore [process]) is called
    // exclusively from the sensor-callback thread delivered by Android's
    // SensorManager.  [reset] is called from lifecycle threads (e.g. the
    // thread that calls [StepSensorManager.startListening] /
    // [StepSensorManager.stopListening]).  These two groups of writes are
    // independent — reset() writes a clean initial state and processSample()
    // reads/writes live state — so @Volatile visibility guarantees are
    // sufficient without an additional lock.

    /** EMA-smoothed acceleration magnitude (m/s²). Initialised to resting gravity. */
    @Volatile
    private var filteredMagnitude: Double = GRAVITY

    /**
     * True once the filtered magnitude has crossed [THRESHOLD_RISE].
     * Prevents re-triggering while the signal remains elevated.
     */
    @Volatile
    private var aboveThreshold: Boolean = false

    /** Timestamp (monotonic nanoseconds) of the most recently counted step. */
    @Volatile
    private var lastStepNs: Long = 0L

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Processes a raw [SensorEvent] from TYPE_ACCELEROMETER.
     *
     * @return `true` if a new step was detected, `false` otherwise.
     */
    fun process(event: SensorEvent): Boolean {
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val magnitude = sqrt(x * x + y * y + z * z)
        return processSample(magnitude, event.timestamp)
    }

    /**
     * Core detection logic, exposed for unit testing without a real [SensorEvent].
     *
     * @param magnitude   Acceleration vector magnitude in m/s².
     * @param timestampNs Monotonic event timestamp in nanoseconds
     *                    (use [SensorEvent.timestamp] in production; use a
     *                    synthetic counter in tests).
     * @return `true` if a new step was detected, `false` otherwise.
     */
    fun processSample(magnitude: Double, timestampNs: Long): Boolean {
        // Guard: reject non-finite magnitudes (NaN, ±Infinity) produced by
        // faulty or uncalibrated sensors.  Propagating NaN into the EMA would
        // corrupt filteredMagnitude permanently, disabling step detection for
        // the entire session.
        if (!magnitude.isFinite()) return false

        // 1. EMA low-pass filter.
        filteredMagnitude = (1.0 - ALPHA) * filteredMagnitude + ALPHA * magnitude

        // 2. Rising-edge detection with hysteresis.
        if (!aboveThreshold && filteredMagnitude >= THRESHOLD_RISE) {
            aboveThreshold = true
            // 3. Timing gate.
            // Note: a backwards timestamp (timestampNs < lastStepNs) produces a
            // negative elapsedMs, which is always < MIN_STEP_MS and is therefore
            // correctly rejected by the gate below — no special handling needed.
            val elapsedMs = (timestampNs - lastStepNs) / 1_000_000L
            return if (elapsedMs >= MIN_STEP_MS) {
                lastStepNs = timestampNs
                true  // step detected
            } else {
                false // vibration burst or backwards timestamp — ignore
            }
        } else if (filteredMagnitude < THRESHOLD_FALL) {
            aboveThreshold = false
        }
        return false
    }

    /**
     * Resets all internal state to the initial values.
     *
     * Call this when the sensor is unregistered so that stale state from a
     * previous session does not affect a future registration.
     */
    fun reset() {
        filteredMagnitude = GRAVITY
        aboveThreshold = false
        lastStepNs = 0L
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        /**
         * Standard gravitational acceleration (m/s²).
         * A stationary device reads approximately this value.
         */
        const val GRAVITY: Double = 9.81

        /**
         * EMA smoothing factor α.
         * At 50 Hz this corresponds to a low-pass cutoff of roughly 0.8 Hz,
         * which passes the ~1–2 Hz walking frequency while rejecting
         * higher-frequency vibration.
         *
         * Formula: α = 1 − exp(−2π·f_c / f_s) ≈ 0.1 for f_c=0.8 Hz, f_s=50 Hz.
         */
        const val ALPHA: Double = 0.1

        /**
         * Rising-edge threshold (m/s²).
         * The filtered magnitude must reach this value to trigger a step candidate.
         * Set ~0.7 m/s² above resting gravity to respond to normal walking.
         */
        const val THRESHOLD_RISE: Double = 10.5

        /**
         * Hysteresis fall threshold (m/s²).
         * After a step is detected the signal must drop below this value before
         * the detector can trigger again.  The 0.5 m/s² gap between
         * [THRESHOLD_RISE] and [THRESHOLD_FALL] prevents chatter.
         */
        const val THRESHOLD_FALL: Double = 10.0

        /**
         * Minimum interval between consecutive steps (milliseconds).
         * Corresponds to a maximum cadence of 240 steps/min — well above
         * normal running (~180 steps/min) to gate out vibration bursts.
         */
        const val MIN_STEP_MS: Long = 250L
    }
}
