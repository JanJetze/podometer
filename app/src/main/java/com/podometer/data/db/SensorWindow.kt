// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing a single 30-second classifier window of raw sensor data.
 *
 * These windows are recorded continuously by [com.podometer.service.StepTrackingService]
 * and retained for 7 days. They enable retroactive recomputation of activity sessions
 * when classifier parameters change.
 *
 * @property id                Auto-generated primary key.
 * @property timestamp         Window start epoch milliseconds.
 * @property magnitudeVariance Accelerometer magnitude variance (m/s²)² for this window.
 * @property stepFrequencyHz   Step cadence in Hz from [com.podometer.data.sensor.StepFrequencyTracker].
 * @property stepCount         Number of steps detected during this window.
 */
@Entity(tableName = "sensor_windows")
data class SensorWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val magnitudeVariance: Double,
    val stepFrequencyHz: Double,
    val stepCount: Int,
)

/**
 * Sums [SensorWindow.stepCount] for windows whose [SensorWindow.timestamp]
 * falls within the half-open range `[startTime, endTime)`.
 *
 * @param startTime Inclusive start of the time range in epoch milliseconds.
 * @param endTime   Exclusive end of the time range in epoch milliseconds.
 * @return Total step count for the range.
 */
fun List<SensorWindow>.sumStepsInRange(startTime: Long, endTime: Long): Int =
    filter { it.timestamp in startTime until endTime }.sumOf { it.stepCount }
