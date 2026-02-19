// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

/**
 * Represents the step-counting hardware sensor type selected by
 * [StepSensorManager].
 *
 * Priority order: [STEP_COUNTER] > [STEP_DETECTOR] > [NONE].
 */
enum class SensorType {
    /** Android TYPE_STEP_COUNTER — reports cumulative steps since last reboot. */
    STEP_COUNTER,

    /** Android TYPE_STEP_DETECTOR — fires once per detected step. */
    STEP_DETECTOR,

    /** No step-counting sensor is available on this device. */
    NONE,
}
