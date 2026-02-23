// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers the accelerometer at [SensorManager.SENSOR_DELAY_NORMAL] (~5 Hz) for
 * activity-classification feature extraction and feeds raw samples into an
 * [AccelerometerSampleBuffer].
 *
 * This registration is independent of [StepSensorManager]'s accelerometer use
 * (which targets step detection at SENSOR_DELAY_GAME ~50 Hz) for three reasons:
 *  1. Cycling detection always needs accelerometer data regardless of which step
 *     sensor type is active on the device.
 *  2. SENSOR_DELAY_NORMAL (~5 Hz) is sufficient for activity classification;
 *     higher rates would waste CPU and battery.
 *  3. Decoupling keeps the step-detection and activity-classification pipelines
 *     independent and separately testable.
 *
 * Usage:
 * ```kotlin
 * accelerometerSampler.startSampling()
 * // … sensor data accumulates in sampleBuffer …
 * val features = accelerometerSampler.sampleBuffer.computeWindowFeatures()
 * // …
 * accelerometerSampler.stopSampling()
 * ```
 *
 * @param sensorManager The Android system [SensorManager] obtained via
 *   `context.getSystemService(Context.SENSOR_SERVICE)`.
 */
@Singleton
class AccelerometerSampler @Inject constructor(
    private val sensorManager: SensorManager,
) : SensorEventListener {

    /**
     * The circular buffer holding the most recent accelerometer magnitude samples.
     *
     * Consumers (e.g., a cycling-detection coroutine) should call
     * [AccelerometerSampleBuffer.computeWindowFeatures] periodically to obtain
     * the current sliding-window feature vector.
     */
    val sampleBuffer = AccelerometerSampleBuffer()

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Registers the TYPE_ACCELEROMETER sensor listener at
     * [SensorManager.SENSOR_DELAY_NORMAL].
     *
     * If no accelerometer is available on the device this method logs a warning
     * and returns without registering. Calling [startSampling] while already
     * registered is a no-op (Android deduplicates listeners for the same sensor).
     */
    fun startSampling() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.w(TAG, "No TYPE_ACCELEROMETER available — AccelerometerSampler inactive")
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "Registered TYPE_ACCELEROMETER at SENSOR_DELAY_NORMAL for activity classification")
    }

    /**
     * Unregisters the sensor listener and resets the sample buffer.
     *
     * Safe to call multiple times or before [startSampling].
     */
    fun stopSampling() {
        sensorManager.unregisterListener(this)
        sampleBuffer.reset()
        Log.d(TAG, "Unregistered accelerometer sampler, buffer reset")
    }

    // ─── SensorEventListener ─────────────────────────────────────────────────

    /**
     * Receives raw accelerometer events, computes the vector magnitude, and
     * stores it in [sampleBuffer].
     *
     * The magnitude computation (`sqrt(x² + y² + z²)`) is the only arithmetic
     * on the hot path; all other work (buffer indexing) is O(1) with no heap
     * allocations.
     */
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()
        val magnitude = sqrt(x * x + y * y + z * z)
        sampleBuffer.addSample(magnitude, event.timestamp)
    }

    /**
     * Called when the sensor accuracy changes.
     *
     * Accuracy degradation (e.g., [SensorManager.SENSOR_STATUS_UNRELIABLE]) is
     * logged but does not cause [sampleBuffer] to be reset, because short
     * accuracy blips are common (e.g., near magnetic interference) and the
     * sliding-window feature computation is naturally robust to occasional
     * outliers.
     */
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        Log.d(TAG, "Accelerometer accuracy changed: $accuracy")
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    private companion object {
        private const val TAG = "AccelerometerSampler"
    }
}
