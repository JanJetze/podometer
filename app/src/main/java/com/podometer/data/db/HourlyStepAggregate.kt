// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity recording a single hourly step-count bucket.
 *
 * [detectedActivity] is stored as a plain string ("WALKING", "CYCLING", "STILL").
 * Conversion to/from domain enums happens in the domain layer.
 */
@Entity(tableName = "hourly_step_aggregates")
data class HourlyStepAggregate(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** Epoch-millisecond timestamp for the start of this hourly bucket. */
    val timestamp: Long,
    /** Number of steps detected during this hour. */
    val stepCountDelta: Int,
    /** Activity detected during this hour: "WALKING", "CYCLING", or "STILL". */
    val detectedActivity: String,
)
