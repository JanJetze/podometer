// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.StepData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Returns a [Flow] of [StepData] representing today's step-count progress.
 *
 * Combines the live step count from [StepRepository] with a hardcoded
 * default goal (10,000 steps) and default stride length (0.75 m) to
 * compute [StepData.progressPercent] and [StepData.distanceKm].
 */
class GetTodayStepsUseCase @Inject constructor(
    private val stepRepository: StepRepository,
) {

    /** Default daily step goal. */
    private val defaultGoal = 10_000

    /** Default stride length in kilometres (0.75 m = 0.00075 km). */
    private val strideKm = 0.00075f

    operator fun invoke(): Flow<StepData> =
        stepRepository.getTodaySteps().map { steps ->
            val progressPercent = (steps.toFloat() / defaultGoal * 100f).coerceAtMost(100f)
            val distanceKm = steps * strideKm
            StepData(
                steps = steps,
                goal = defaultGoal,
                progressPercent = progressPercent,
                distanceKm = distanceKm,
            )
        }
}
