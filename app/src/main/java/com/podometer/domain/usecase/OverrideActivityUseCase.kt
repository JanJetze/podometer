// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.ActivityTransition
import com.podometer.data.db.CyclingSession
import com.podometer.data.repository.CyclingRepository
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
 * In addition to updating the transition, this implementation manages [CyclingSession]
 * records:
 * - Overriding **to** [ActivityState.CYCLING]: creates a new [CyclingSession] starting at
 *   [ActivityTransition.timestamp] with [CyclingSession.isManualOverride] = `true`.
 * - Overriding **away from** [ActivityState.CYCLING]: finds and deletes the
 *   [CyclingSession] that covers [ActivityTransition.timestamp].
 *
 * Step count recalculation is intentionally deferred to a future task. Replaying the
 * raw step-event stream to recompute affected hourly aggregates is out of scope for v1;
 * the trade-off is that step counts remain as originally recorded even after a cycling
 * override. This is acceptable for v1 because cycling steps are already excluded from the
 * pedometer counter by the service layer.
 *
 * @param stepRepository     Repository used to persist the updated transition.
 * @param cyclingRepository  Repository used to create or delete [CyclingSession] records.
 */
class OverrideActivityUseCaseImpl @Inject constructor(
    private val stepRepository: StepRepository,
    private val cyclingRepository: CyclingRepository,
) : OverrideActivityUseCase {

    override suspend operator fun invoke(transition: ActivityTransition, newActivity: ActivityState) {
        val updated = transition.copy(
            toActivity = newActivity.name,
            isManualOverride = true,
        )
        stepRepository.updateTransition(updated)

        val wasAlreadyCycling = transition.toActivity == ActivityState.CYCLING.name
        val isNowCycling = newActivity == ActivityState.CYCLING

        when {
            isNowCycling && !wasAlreadyCycling -> createCyclingSession(transition)
            !isNowCycling && wasAlreadyCycling -> deleteCyclingSession(transition.timestamp)
        }
    }

    /**
     * Creates a new [CyclingSession] for the time range beginning at [transition]'s timestamp.
     *
     * The session's [CyclingSession.endTime] and [CyclingSession.durationMinutes] are left
     * at defaults (null / 0) for v1 since the next-transition lookup is not yet implemented.
     */
    private suspend fun createCyclingSession(transition: ActivityTransition) {
        val session = CyclingSession(
            startTime = transition.timestamp,
            endTime = null,
            durationMinutes = 0,
            isManualOverride = true,
        )
        cyclingRepository.insertSession(session)
    }

    /**
     * Finds the [CyclingSession] covering [timestamp] and deletes it, if one exists.
     */
    private suspend fun deleteCyclingSession(timestamp: Long) {
        val session = cyclingRepository.getSessionCoveringTimestamp(timestamp) ?: return
        cyclingRepository.deleteSession(session)
    }
}
