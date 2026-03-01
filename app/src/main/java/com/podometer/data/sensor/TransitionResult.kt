// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.sensor

import com.podometer.domain.model.ActivityState

/**
 * Represents a completed activity state transition detected by [CyclingClassifier].
 *
 * @param fromState          The activity state before the transition.
 * @param toState            The activity state after the transition.
 * @param effectiveTimestamp  Wall-clock timestamp (ms) of when the transition
 *   effectively occurred.  For grace-period STILL transitions this is the
 *   timestamp of the first still window (when the user actually stopped); for
 *   all other transitions it equals the `currentTimeMs` passed to
 *   [CyclingClassifier.evaluate].
 */
data class TransitionResult(
    val fromState: ActivityState,
    val toState: ActivityState,
    val effectiveTimestamp: Long,
)
