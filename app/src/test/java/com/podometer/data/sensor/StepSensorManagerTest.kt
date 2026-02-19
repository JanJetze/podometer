// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [StepSensorManager] computation logic.
 *
 * The SensorEventListener callbacks cannot be unit-tested without the Android
 * framework, so these tests focus on the pure delta-computation function and
 * the sensor-type selection logic — both of which are extracted into testable
 * standalone functions.
 */
class StepSensorManagerTest {

    // ─── computeStepDelta ────────────────────────────────────────────────────

    @Test
    fun `computeStepDelta returns zero when baseline is null (first event)`() {
        val delta = computeStepDelta(currentValue = 1500f, baseline = null)
        assertEquals(0, delta.newDelta)
        assertEquals(1500f, delta.newBaseline, 0f)
    }

    @Test
    fun `computeStepDelta returns correct delta when baseline is set`() {
        val delta = computeStepDelta(currentValue = 1510f, baseline = 1500f)
        assertEquals(10, delta.newDelta)
        assertEquals(1510f, delta.newBaseline, 0f)
    }

    @Test
    fun `computeStepDelta returns zero delta when value equals baseline`() {
        val delta = computeStepDelta(currentValue = 2000f, baseline = 2000f)
        assertEquals(0, delta.newDelta)
        assertEquals(2000f, delta.newBaseline, 0f)
    }

    @Test
    fun `computeStepDelta handles large cumulative counter values`() {
        val delta = computeStepDelta(currentValue = 1_000_000f, baseline = 999_900f)
        assertEquals(100, delta.newDelta)
        assertEquals(1_000_000f, delta.newBaseline, 0f)
    }

    @Test
    fun `computeStepDelta handles counter reset (current less than baseline)`() {
        // After reboot, the counter resets to 0. We treat a negative delta as 0.
        val delta = computeStepDelta(currentValue = 50f, baseline = 1500f)
        assertEquals(0, delta.newDelta)
        assertEquals(50f, delta.newBaseline, 0f)
    }

    @Test
    fun `computeStepDelta updates baseline even when delta is zero (first event)`() {
        val first = computeStepDelta(currentValue = 300f, baseline = null)
        assertEquals(300f, first.newBaseline, 0f)

        val second = computeStepDelta(currentValue = 305f, baseline = first.newBaseline)
        assertEquals(5, second.newDelta)
        assertEquals(305f, second.newBaseline, 0f)
    }

    // ─── SensorType ──────────────────────────────────────────────────────────

    @Test
    fun `SensorType enum has STEP_COUNTER entry`() {
        assertEquals(SensorType.STEP_COUNTER, SensorType.valueOf("STEP_COUNTER"))
    }

    @Test
    fun `SensorType enum has STEP_DETECTOR entry`() {
        assertEquals(SensorType.STEP_DETECTOR, SensorType.valueOf("STEP_DETECTOR"))
    }

    @Test
    fun `SensorType enum has NONE entry`() {
        assertEquals(SensorType.NONE, SensorType.valueOf("NONE"))
    }

    @Test
    fun `SensorType enum contains exactly four values`() {
        assertEquals(4, SensorType.entries.size)
    }

    // ─── selectSensorType ────────────────────────────────────────────────────

    @Test
    fun `selectSensorType returns STEP_COUNTER when both are available`() {
        val result = selectSensorType(hasStepCounter = true, hasStepDetector = true, hasAccelerometer = false)
        assertEquals(SensorType.STEP_COUNTER, result)
    }

    @Test
    fun `selectSensorType returns STEP_COUNTER when only step counter available`() {
        val result = selectSensorType(hasStepCounter = true, hasStepDetector = false, hasAccelerometer = false)
        assertEquals(SensorType.STEP_COUNTER, result)
    }

    @Test
    fun `selectSensorType falls back to STEP_DETECTOR when counter unavailable`() {
        val result = selectSensorType(hasStepCounter = false, hasStepDetector = true, hasAccelerometer = false)
        assertEquals(SensorType.STEP_DETECTOR, result)
    }

    @Test
    fun `selectSensorType returns NONE when neither sensor is available`() {
        val result = selectSensorType(hasStepCounter = false, hasStepDetector = false, hasAccelerometer = false)
        assertEquals(SensorType.NONE, result)
    }

    // ─── Class and package existence ─────────────────────────────────────────

    @Test
    fun `SensorType is in com_podometer_data_sensor package`() {
        assertEquals("com.podometer.data.sensor", SensorType::class.java.packageName)
    }
}
