// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a user-created or user-edited activity session override.
 *
 * Manual overrides take precedence over recomputed sessions from sensor windows.
 * They allow the user to correct activity boundaries and reclassify activity types.
 *
 * @property id        Auto-generated primary key.
 * @property startTime Session start in epoch milliseconds.
 * @property endTime   Session end in epoch milliseconds.
 * @property activity  Activity type as string ("WALKING", "CYCLING", "STILL").
 * @property date      Date string in "yyyy-MM-dd" format for day-based queries.
 */
@Entity(tableName = "manual_session_overrides")
data class ManualSessionOverride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val activity: String,
    val date: String,
)
