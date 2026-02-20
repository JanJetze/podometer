// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.ActivityTransition
import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.ActivityState
import javax.inject.Inject

/**
 * Functional interface for overriding an existing activity transition.
 *
 * Implementations must set [ActivityTransition.toActivity] to [newActivity] and mark
 * [ActivityTransition.isManualOverride] as `true`.
 *
 * Using a `fun interface` follows the same pattern as [GetTodayStepsUseCase] and other
 * domain use cases in this project, enabling easy faking in unit tests without Mockito.
 */
fun interface OverrideActivityUseCase {
    /**
     * Applies the override.
     *
     * @param transition  The existing DB transition to override (retains all other fields).
     * @param newActivity The corrected [ActivityState] to set as [ActivityTransition.toActivity].
     */
    suspend operator fun invoke(transition: ActivityTransition, newActivity: ActivityState)
}

/**
 * Default implementation of [OverrideActivityUseCase].
 *
 * Copies the original [ActivityTransition] record with [ActivityTransition.toActivity]
 * replaced by the string representation of [newActivity] and
 * [ActivityTransition.isManualOverride] set to `true`.
 *
 * @param stepRepository Repository used to persist the updated transition.
 */
class OverrideActivityUseCaseImpl @Inject constructor(
    private val stepRepository: StepRepository,
) : OverrideActivityUseCase {

    override suspend operator fun invoke(transition: ActivityTransition, newActivity: ActivityState) {
        val updated = transition.copy(
            toActivity = newActivity.name,
            isManualOverride = true,
        )
        stepRepository.updateTransition(updated)
    }
}
