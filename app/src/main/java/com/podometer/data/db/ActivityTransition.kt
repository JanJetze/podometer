// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an activity transition event.
 *
 * Both [fromActivity] and [toActivity] are plain strings
 * ("WALKING", "CYCLING", "STILL"). Domain-layer enums are converted
 * before persistence and after retrieval.
 */
@Entity(tableName = "activity_transitions")
data class ActivityTransition(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** Epoch-millisecond timestamp when the transition occurred. */
    val timestamp: Long,
    /** Activity the user was performing before this transition. */
    val fromActivity: String,
    /** Activity the user transitioned into. */
    val toActivity: String,
    /** True when a user manually corrected/overrode the detected transition. */
    val isManualOverride: Boolean = false,
)
