// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import com.podometer.domain.model.ActivityState

/**
 * Represents a completed activity state transition detected by [CyclingClassifier].
 *
 * @param fromState The activity state before the transition.
 * @param toState   The activity state after the transition.
 */
data class TransitionResult(
    val fromState: ActivityState,
    val toState: ActivityState,
)
