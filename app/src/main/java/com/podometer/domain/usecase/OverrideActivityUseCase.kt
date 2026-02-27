// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.ActivityTransition
import com.podometer.data.db.CyclingSession
import com.podometer.data.repository.CyclingRepository
import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.ActivityState
import com.podometer.service.CyclingSessionManager
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
 *   If a subsequent transition exists, [CyclingSession.endTime] and
 *   [CyclingSession.durationMinutes] are computed immediately. Sessions shorter than
 *   [CyclingSessionManager.MIN_SESSION_DURATION_MS] are not inserted at all.
 *   If no next transition exists, the session is left open (endTime = null) to represent
 *   ongoing activity.
 * - Overriding **away from** [ActivityState.CYCLING]: finds the [CyclingSession] covering
 *   [ActivityTransition.timestamp] and closes it by setting [CyclingSession.endTime] and
 *   computing [CyclingSession.durationMinutes]. Sessions shorter than
 *   [CyclingSessionManager.MIN_SESSION_DURATION_MS] are deleted instead.
 *
 * Step count recalculation is intentionally deferred to a future task. Replaying the
 * raw step-event stream to recompute affected hourly aggregates is out of scope for v1;
 * the trade-off is that step counts remain as originally recorded even after a cycling
 * override. This is acceptable for v1 because cycling steps are already excluded from the
 * pedometer counter by the service layer.
 *
 * @param stepRepository     Repository used to persist the updated transition.
 * @param cyclingRepository  Repository used to create or close [CyclingSession] records.
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
            !isNowCycling && wasAlreadyCycling -> closeCyclingSession(transition.timestamp)
        }
    }

    /**
     * Creates a new [CyclingSession] for the time range beginning at [transition]'s timestamp.
     *
     * If a subsequent transition exists in the DB, [CyclingSession.endTime] and
     * [CyclingSession.durationMinutes] are computed immediately so the session is stored
     * with complete data. Sessions shorter than [CyclingSessionManager.MIN_SESSION_DURATION_MS]
     * are not inserted at all (consistent with the service-layer threshold).
     *
     * If no next transition exists, the session is left open ([CyclingSession.endTime] = null,
     * [CyclingSession.durationMinutes] = 0) to represent activity that is still ongoing.
     */
    private suspend fun createCyclingSession(transition: ActivityTransition) {
        val nextTransition = stepRepository.getNextTransitionAfter(transition.timestamp)

        if (nextTransition != null) {
            val durationMs = nextTransition.timestamp - transition.timestamp
            if (durationMs < CyclingSessionManager.MIN_SESSION_DURATION_MS) {
                // Too short — do not create a session.
                return
            }
            val durationMinutes = ((durationMs + 30_000L) / 60_000L).toInt()
            val session = CyclingSession(
                startTime = transition.timestamp,
                endTime = nextTransition.timestamp,
                durationMinutes = durationMinutes,
                isManualOverride = true,
            )
            cyclingRepository.insertSession(session)
        } else {
            // No next transition: session is ongoing.
            val session = CyclingSession(
                startTime = transition.timestamp,
                endTime = null,
                durationMinutes = 0,
                isManualOverride = true,
            )
            cyclingRepository.insertSession(session)
        }
    }

    /**
     * Finds the [CyclingSession] covering [timestamp] and closes it by setting its
     * [CyclingSession.endTime] to [timestamp] and computing [CyclingSession.durationMinutes].
     *
     * If the computed duration is less than [CyclingSessionManager.MIN_SESSION_DURATION_MS]
     * the session is deleted instead (consistent with the service-layer threshold for
     * accidental cycling detection).
     *
     * Does nothing if no session covers [timestamp].
     */
    private suspend fun closeCyclingSession(timestamp: Long) {
        val session = cyclingRepository.getSessionCoveringTimestamp(timestamp) ?: return
        val durationMs = timestamp - session.startTime
        if (durationMs < CyclingSessionManager.MIN_SESSION_DURATION_MS) {
            cyclingRepository.deleteSession(session)
            return
        }
        val durationMinutes = ((durationMs + 30_000L) / 60_000L).toInt()
        cyclingRepository.updateSession(
            session.copy(
                endTime = timestamp,
                durationMinutes = durationMinutes,
            ),
        )
    }
}
