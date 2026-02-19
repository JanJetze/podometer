// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.DaySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Returns a [Flow] of [List<DaySummary>] covering the current calendar week
 * (Monday through today).
 *
 * Maps DB [com.podometer.data.db.DailySummary] entities to domain
 * [DaySummary] models, converting [totalDistance] to [DaySummary.totalDistanceKm].
 */
class GetWeeklyStepsUseCase @Inject constructor(
    private val stepRepository: StepRepository,
) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    operator fun invoke(): Flow<List<DaySummary>> {
        val today = LocalDate.now()
        // ISO week starts on Monday (day-of-week 1)
        val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val startDate = startOfWeek.format(formatter)
        val endDate = today.format(formatter)

        return stepRepository.getWeeklyDailySummaries(startDate, endDate).map { dbList ->
            dbList.map { db ->
                DaySummary(
                    date = db.date,
                    totalSteps = db.totalSteps,
                    totalDistanceKm = db.totalDistance,
                    walkingMinutes = db.walkingMinutes,
                    cyclingMinutes = db.cyclingMinutes,
                )
            }
        }
    }
}
