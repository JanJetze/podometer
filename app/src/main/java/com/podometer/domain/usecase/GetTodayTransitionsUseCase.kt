// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.TransitionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Returns a [Flow] of [List<TransitionEvent>] for all activity transitions
 * detected today.
 *
 * Maps DB [com.podometer.data.db.ActivityTransition] entities to domain
 * [TransitionEvent] models, converting the string activity fields to
 * [ActivityState] enum values via [ActivityState.fromString].
 */
class GetTodayTransitionsUseCase @Inject constructor(
    private val stepRepository: StepRepository,
) {

    operator fun invoke(): Flow<List<TransitionEvent>> =
        stepRepository.getTodayTransitions().map { dbList ->
            dbList.map { db ->
                TransitionEvent(
                    id = db.id,
                    timestamp = db.timestamp,
                    fromActivity = ActivityState.fromString(db.fromActivity),
                    toActivity = ActivityState.fromString(db.toActivity),
                    isManualOverride = db.isManualOverride,
                )
            }
        }
}
