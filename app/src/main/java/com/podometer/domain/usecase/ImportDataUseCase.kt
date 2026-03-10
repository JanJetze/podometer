// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import com.podometer.data.db.StepDao
import com.podometer.data.export.ExportData
import kotlinx.serialization.json.Json

/**
 * Use case that parses an exported JSON file and inserts all data into the local database.
 *
 * Existing rows with matching primary keys are replaced (daily summaries) or
 * inserted with new auto-generated IDs (hourly aggregates).
 *
 * @param stepDao DAO for daily summaries and hourly aggregates.
 */
class ImportDataUseCase(
    private val stepDao: StepDao,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses [jsonString] as an [ExportData] and inserts all records into the database.
     *
     * Auto-generated IDs (id = 0) are used so Room assigns fresh primary keys,
     * avoiding conflicts with any existing data.
     */
    suspend fun importFromJson(jsonString: String) {
        val data = json.decodeFromString(ExportData.serializer(), jsonString)

        stepDao.insertAllDailySummaries(
            data.dailySummaries.map { s ->
                DailySummary(
                    date = s.date,
                    totalSteps = s.totalSteps,
                    totalDistance = s.totalDistance,
                    walkingMinutes = s.walkingMinutes,
                    cyclingMinutes = s.cyclingMinutes,
                )
            },
        )

        stepDao.insertAllHourlyAggregates(
            data.hourlyAggregates.map { a ->
                HourlyStepAggregate(
                    id = 0,
                    timestamp = a.timestamp,
                    stepCountDelta = a.stepCountDelta,
                    detectedActivity = a.detectedActivity,
                )
            },
        )
    }
}
