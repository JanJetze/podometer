// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single cycling session.
 *
 * [endTime] is nullable — a null value indicates the session is still ongoing.
 * [isManualOverride] is set to true when the user explicitly started or
 * corrected a session rather than having it detected automatically.
 */
@Entity(tableName = "cycling_sessions")
data class CyclingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** Epoch-millisecond timestamp when the session started. */
    val startTime: Long,
    /** Epoch-millisecond timestamp when the session ended; null if ongoing. */
    val endTime: Long? = null,
    /** Duration of the session in minutes. */
    val durationMinutes: Int,
    /** True when the user manually created or overrode this session. */
    val isManualOverride: Boolean = false,
)
