// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

/**
 * Result of computing a step delta from a cumulative TYPE_STEP_COUNTER value.
 *
 * @property newDelta The number of new steps detected since the last event
 *   (0 on the very first event or after a counter reset).
 * @property newBaseline The updated baseline to use for the next delta
 *   computation, equal to [currentValue] passed to [computeStepDelta].
 */
data class StepDeltaResult(
    val newDelta: Int,
    val newBaseline: Float,
)

/**
 * Computes the step delta for a TYPE_STEP_COUNTER event.
 *
 * TYPE_STEP_COUNTER reports cumulative steps since the last device reboot.
 * On the first event after registration [baseline] is `null`; in that case
 * this function stores [currentValue] as the new baseline and returns a delta
 * of 0 (we do not count steps that happened before we started listening).
 *
 * If the counter has rolled back (e.g., after a reboot mid-session) the delta
 * is clamped to 0 and [currentValue] becomes the new baseline.
 *
 * @param currentValue The raw sensor value from [android.hardware.SensorEvent.values][0].
 * @param baseline     The cumulative value recorded at the previous event, or
 *                     `null` if this is the first event after [StepSensorManager.startListening].
 */
fun computeStepDelta(currentValue: Float, baseline: Float?): StepDeltaResult {
    if (baseline == null) {
        return StepDeltaResult(newDelta = 0, newBaseline = currentValue)
    }
    val rawDelta = (currentValue - baseline).toInt()
    val delta = if (rawDelta > 0) rawDelta else 0
    return StepDeltaResult(newDelta = delta, newBaseline = currentValue)
}

/**
 * Selects the best available [SensorType] given hardware availability flags.
 *
 * Priority: TYPE_STEP_COUNTER > TYPE_STEP_DETECTOR > TYPE_ACCELEROMETER > NONE.
 *
 * TYPE_STEP_COUNTER is preferred because it supports batching (saves battery).
 * TYPE_STEP_DETECTOR is used as first fallback.
 * TYPE_ACCELEROMETER with software step detection is used as last resort.
 *
 * @param hasStepCounter   Whether TYPE_STEP_COUNTER is present on the device.
 * @param hasStepDetector  Whether TYPE_STEP_DETECTOR is present on the device.
 * @param hasAccelerometer Whether TYPE_ACCELEROMETER is present on the device.
 */
fun selectSensorType(
    hasStepCounter: Boolean,
    hasStepDetector: Boolean,
    hasAccelerometer: Boolean,
): SensorType =
    when {
        hasStepCounter -> SensorType.STEP_COUNTER
        hasStepDetector -> SensorType.STEP_DETECTOR
        hasAccelerometer -> SensorType.ACCELEROMETER
        else -> SensorType.NONE
    }
