// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.StepData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Functional interface for retrieving today's step data as a [Flow]. */
fun interface GetTodayStepsUseCase {
    operator fun invoke(): Flow<StepData>
}

/**
 * Returns a [Flow] of [StepData] representing today's step-count progress.
 *
 * Combines the live step count from [StepRepository] with the user-configured
 * stride length and daily step goal from [PreferencesManager] to compute
 * [StepData.progressPercent] and [StepData.distanceKm]. When the user changes
 * their step goal or stride length in Settings, the emitted [StepData] updates
 * immediately without waiting for new steps to arrive.
 */
class GetTodayStepsUseCaseImpl @Inject constructor(
    private val stepRepository: StepRepository,
    private val preferencesManager: PreferencesManager,
) : GetTodayStepsUseCase {

    private companion object {
        /**
         * Fallback goal used when the value from [PreferencesManager] is zero or negative.
         *
         * [PreferencesManager.setDailyStepGoal] already validates that goal > 0, but a
         * defensive guard here ensures [StepData.progressPercent] is never computed via
         * division-by-zero regardless of how this use case is wired up in the future.
         */
        const val DEFAULT_DAILY_STEP_GOAL = 10_000
    }

    override operator fun invoke(): Flow<StepData> =
        combine(
            stepRepository.getTodaySteps(),
            preferencesManager.strideLengthKm(),
            preferencesManager.dailyStepGoal(),
        ) { steps, strideKm, goal ->
            val safeGoal = if (goal > 0) goal else DEFAULT_DAILY_STEP_GOAL
            val progressPercent = steps.toFloat() / safeGoal * 100f
            val distanceKm = steps * strideKm
            StepData(
                steps = steps,
                goal = safeGoal,
                progressPercent = progressPercent,
                distanceKm = distanceKm,
            )
        }
}
