// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.repository.StepRepository
import com.podometer.domain.model.DaySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Functional interface for retrieving this week's daily step summaries as a [Flow]. */
fun interface GetWeeklyStepsUseCase {
    operator fun invoke(): Flow<List<DaySummary>>
}

/**
 * Returns a [Flow] of [List<DaySummary>] covering the full current ISO calendar week
 * (Monday through Sunday).
 *
 * Future days within the week will have no data in the repository, which is fine —
 * the chart renders them as empty placeholder bars.
 *
 * Maps DB [com.podometer.data.db.DailySummary] entities to domain
 * [DaySummary] models, converting [totalDistance] to [DaySummary.totalDistanceKm].
 */
class GetWeeklyStepsUseCaseImpl @Inject constructor(
    private val stepRepository: StepRepository,
) : GetWeeklyStepsUseCase {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override operator fun invoke(): Flow<List<DaySummary>> {
        val today = LocalDate.now()
        // ISO week: Monday through Sunday of the week containing today
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val sunday = monday.plusDays(6)
        val startDate = monday.format(formatter)
        val endDate = sunday.format(formatter)

        return stepRepository.getWeeklyDailySummaries(startDate, endDate).map { dbList ->
            dbList.map { db ->
                DaySummary(
                    date = db.date,
                    totalSteps = db.totalSteps,
                    totalDistanceKm = db.totalDistance,
                )
            }
        }
    }
}
