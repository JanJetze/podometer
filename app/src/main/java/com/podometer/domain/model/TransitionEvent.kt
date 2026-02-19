// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.model

/**
 * Domain model representing a detected or manually overridden activity
 * transition.
 *
 * @property id               Database primary key.
 * @property timestamp        Epoch-millisecond timestamp when the transition occurred.
 * @property fromActivity     Activity the user was performing before the transition.
 * @property toActivity       Activity the user transitioned into.
 * @property isManualOverride True when a user manually corrected the detected transition.
 */
data class TransitionEvent(
    val id: Int,
    val timestamp: Long,
    val fromActivity: ActivityState,
    val toActivity: ActivityState,
    val isManualOverride: Boolean,
)
