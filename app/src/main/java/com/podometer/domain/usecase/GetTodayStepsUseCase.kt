// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.repository.PreferencesManager
import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.StepData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Returns a [Flow] of [StepData] representing today's step-count progress.
 *
 * Combines the live step count from [StepRepository] with the user-configured
 * stride length from [PreferencesManager] to compute [StepData.progressPercent]
 * and [StepData.distanceKm]. The daily goal is hardcoded at 10,000 steps.
 */
class GetTodayStepsUseCase @Inject constructor(
    private val stepRepository: StepRepository,
    private val preferencesManager: PreferencesManager,
) {

    // ─── Constants ───────────────────────────────────────────────────────────

    private companion object {
        /** Default daily step goal. */
        const val DEFAULT_GOAL = 10_000
    }

    operator fun invoke(): Flow<StepData> =
        stepRepository.getTodaySteps().combine(preferencesManager.strideLengthKm()) { steps, strideKm ->
            val progressPercent = (steps.toFloat() / DEFAULT_GOAL * 100f).coerceAtMost(100f)
            val distanceKm = steps * strideKm
            StepData(
                steps = steps,
                goal = DEFAULT_GOAL,
                progressPercent = progressPercent,
                distanceKm = distanceKm,
            )
        }
}
