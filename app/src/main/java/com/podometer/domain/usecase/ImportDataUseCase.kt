// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.ActivityTransition
import com.podometer.data.db.ActivityTransitionDao
import com.podometer.data.db.CyclingSession
import com.podometer.data.db.CyclingSessionDao
import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import com.podometer.data.db.SensorWindow
import com.podometer.data.db.SensorWindowDao
import com.podometer.data.db.StepDao
import com.podometer.data.export.ExportData
import kotlinx.serialization.json.Json

/**
 * Use case that parses an exported JSON file and inserts all data into the local database.
 *
 * Existing rows with matching primary keys are replaced (daily summaries) or
 * inserted with new auto-generated IDs (hourly aggregates, transitions, sessions).
 *
 * @param stepDao DAO for daily summaries and hourly aggregates.
 * @param activityTransitionDao DAO for activity transitions.
 * @param cyclingSessionDao DAO for cycling sessions.
 * @param sensorWindowDao DAO for raw sensor classifier windows.
 */
class ImportDataUseCase(
    private val stepDao: StepDao,
    private val activityTransitionDao: ActivityTransitionDao,
    private val cyclingSessionDao: CyclingSessionDao,
    private val sensorWindowDao: SensorWindowDao,
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

        activityTransitionDao.insertAllTransitions(
            data.activityTransitions.map { t ->
                ActivityTransition(
                    id = 0,
                    timestamp = t.timestamp,
                    fromActivity = t.fromActivity,
                    toActivity = t.toActivity,
                    isManualOverride = t.isManualOverride,
                )
            },
        )

        cyclingSessionDao.insertAllSessions(
            data.cyclingSessions.map { s ->
                CyclingSession(
                    id = 0,
                    startTime = s.startTime,
                    endTime = s.endTime,
                    durationMinutes = s.durationMinutes,
                    isManualOverride = s.isManualOverride,
                )
            },
        )

        sensorWindowDao.insertAll(
            data.sensorWindows.map { w ->
                SensorWindow(
                    id = 0,
                    timestamp = w.timestamp,
                    magnitudeVariance = w.magnitudeVariance,
                    stepFrequencyHz = w.stepFrequencyHz,
                    stepCount = w.stepCount,
                )
            },
        )
    }
}
