// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.ActivityTransition
import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.ActivityState
import javax.inject.Inject

/**
 * Updates an existing activity transition to reflect a manual override.
 *
 * Copies the original [ActivityTransition] record with [ActivityTransition.toActivity]
 * replaced by the string representation of [newActivity] and
 * [ActivityTransition.isManualOverride] set to `true`.
 *
 * @param transition  The existing DB transition to override (used to retain all other fields).
 * @param newActivity The corrected [ActivityState] to set as [ActivityTransition.toActivity].
 */
class OverrideActivityUseCase @Inject constructor(
    private val stepRepository: StepRepository,
) {

    suspend operator fun invoke(transition: ActivityTransition, newActivity: ActivityState) {
        val updated = transition.copy(
            toActivity = newActivity.name,
            isManualOverride = true,
        )
        stepRepository.updateTransition(updated)
    }
}
