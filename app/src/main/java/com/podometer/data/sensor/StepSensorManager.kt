// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages registration of the device's step-counting hardware sensor and
 * converts raw sensor events into step-delta emissions.
 *
 * Priority chain:
 *  1. TYPE_STEP_COUNTER — cumulative counter since last reboot; uses
 *     [maxReportLatencyUs] for hardware batching to save battery.
 *  2. TYPE_STEP_DETECTOR — fires once per detected step (first fallback).
 *  3. TYPE_ACCELEROMETER — raw accelerometer with software step detection
 *     via [AccelerometerStepDetector] (last resort).
 *  4. NONE — no sensor available; [stepEvents] never emits.
 *
 * Usage:
 * ```
 * manager.startListening()
 * lifecycleScope.launch {
 *     manager.stepEvents.collect { delta -> /* delta new steps */ }
 * }
 * // …
 * manager.stopListening()
 * ```
 *
 * @param sensorManager The Android system [SensorManager] obtained via
 *   `context.getSystemService(Context.SENSOR_SERVICE)`.
 */
@Singleton
class StepSensorManager @Inject constructor(
    private val sensorManager: SensorManager,
) : SensorEventListener {

    private val _stepEvents = MutableSharedFlow<Int>(extraBufferCapacity = 64)

    /**
     * Emits the number of **new** steps detected since the previous event.
     *
     * - For TYPE_STEP_COUNTER this is `current − previous` (delta from the
     *   cumulative counter).
     * - For TYPE_STEP_DETECTOR each emission is always `1`.
     * - For TYPE_ACCELEROMETER each emission is `1` when a step is detected
     *   by [AccelerometerStepDetector].
     * - No emission is produced on the first TYPE_STEP_COUNTER event (baseline
     *   is established).
     */
    val stepEvents: SharedFlow<Int> = _stepEvents.asSharedFlow()

    /** Baseline for the cumulative TYPE_STEP_COUNTER; null before the first event. */
    @Volatile
    private var stepCounterBaseline: Float? = null

    /** Software step detector for the TYPE_ACCELEROMETER fallback path. */
    private val accelerometerDetector = AccelerometerStepDetector()

    // ─── Sensor availability ─────────────────────────────────────────────────

    /**
     * Returns the best [SensorType] available on this device.
     *
     * Checks hardware availability at call time; does not depend on whether
     * [startListening] has been called.
     */
    fun getAvailableSensorType(): SensorType = selectSensorType(
        hasStepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
        hasStepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null,
        hasAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
    )

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    /** True while the sensor listener is registered; guards against duplicate registrations. */
    internal var isListening: Boolean = false

    /**
     * Registers the best available sensor listener.
     *
     * - TYPE_STEP_COUNTER is registered with a 5-second batch latency so the
     *   hardware can accumulate events and flush them in bulk (saves battery).
     * - TYPE_STEP_DETECTOR is registered with [SensorManager.SENSOR_DELAY_NORMAL].
     * - TYPE_ACCELEROMETER is registered with [SensorManager.SENSOR_DELAY_GAME]
     *   (~50 Hz) — above the Nyquist frequency for walking cadence (~2 Hz) so
     *   the EMA filter in [AccelerometerStepDetector] has enough resolution.
     *
     * Calling this when already listening is a no-op, guarded by an internal
     * flag, so the listener is never registered twice even if [startListening]
     * is called multiple times (e.g. on repeated [android.app.Service.onStartCommand]
     * invocations with START_STICKY). The flag is reset by [stopListening].
     * When no step-counting sensor is available ([SensorType.NONE]) the flag
     * is left as `false` because no registration took place.
     */
    fun startListening() {
        if (isListening) {
            Log.d(TAG, "Already listening — ignoring duplicate startListening() call")
            return
        }
        val sensorType = getAvailableSensorType()
        when (sensorType) {
            SensorType.STEP_COUNTER -> {
                val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!!
                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    BATCH_LATENCY_US,
                )
                Log.d(TAG, "Registered TYPE_STEP_COUNTER with ${BATCH_LATENCY_US}µs batch latency")
            }
            SensorType.STEP_DETECTOR -> {
                val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)!!
                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                )
                Log.d(TAG, "Registered TYPE_STEP_DETECTOR")
            }
            SensorType.ACCELEROMETER -> {
                val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
                accelerometerDetector.reset()
                sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
                Log.d(TAG, "Registered TYPE_ACCELEROMETER (software step detection)")
            }
            SensorType.NONE -> {
                Log.w(TAG, "No step-counting sensor available on this device")
            }
        }
        if (sensorType != SensorType.NONE) {
            isListening = true
        }
    }

    /**
     * Unregisters the sensor listener and resets all internal state.
     *
     * Safe to call multiple times or before [startListening].
     */
    fun stopListening() {
        sensorManager.unregisterListener(this)
        stepCounterBaseline = null
        accelerometerDetector.reset()
        isListening = false
        Log.d(TAG, "Unregistered step sensor listener")
    }

    // ─── SensorEventListener ─────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> handleStepCounterEvent(event.values[0])
            Sensor.TYPE_STEP_DETECTOR -> handleStepDetectorEvent()
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometerEvent(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Log accuracy changes but do not stop listening — sensors may briefly
        // report unreliable accuracy without losing step data.
        Log.d(TAG, "Accuracy changed for ${sensor.name}: $accuracy")
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun handleStepCounterEvent(rawValue: Float) {
        val result = computeStepDelta(
            currentValue = rawValue,
            baseline = stepCounterBaseline,
        )
        stepCounterBaseline = result.newBaseline
        if (result.newDelta > 0) {
            _stepEvents.tryEmit(result.newDelta)
        }
    }

    private fun handleStepDetectorEvent() {
        _stepEvents.tryEmit(1)
    }

    private fun handleAccelerometerEvent(event: SensorEvent) {
        // Threading: this callback runs on the sensor-delivery thread.
        // [AccelerometerStepDetector] fields are @Volatile so changes written
        // here (processSample) are immediately visible to any lifecycle thread
        // that calls reset() via stopListening() / startListening().
        if (accelerometerDetector.process(event)) {
            _stepEvents.tryEmit(1)
        }
    }

    // ─── Constants ───────────────────────────────────────────────────────────

    private companion object {
        private const val TAG = "StepSensorManager"

        /** 5 seconds in microseconds — hardware batch latency for TYPE_STEP_COUNTER. */
        private const val BATCH_LATENCY_US = 5_000_000
    }
}
