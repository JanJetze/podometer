// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.export.ExportActivityTransition
import com.podometer.data.export.ExportCyclingSession
import com.podometer.data.export.ExportDailySummary
import com.podometer.data.export.ExportData
import com.podometer.data.export.ExportHourlyAggregate
import com.podometer.data.export.ExportMetadata
import com.podometer.data.export.ExportSensorWindow
import com.podometer.data.db.SensorWindowDao
import com.podometer.data.repository.CyclingRepository
import com.podometer.data.repository.StepRepository
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Use case that gathers all app data from repositories and serializes it to JSON.
 *
 * The [buildExportData] function is a pure suspend function that can be unit
 * tested without Android dependencies. The [serializeToJson] function is a
 * pure function operating only on the export model.
 *
 * @param stepRepository Source for daily summaries, hourly aggregates, and transitions.
 * @param cyclingRepository Source for cycling sessions.
 * @param deviceModel Device model string included in export metadata (injectable for testing).
 */
class ExportDataUseCase(
    private val stepRepository: StepRepository,
    private val cyclingRepository: CyclingRepository,
    private val sensorWindowDao: SensorWindowDao,
    private val deviceModel: String,
) {

    // ─── Constants ───────────────────────────────────────────────────────────

    private companion object {
        /** App version included in every export. */
        const val APP_VERSION = "1.0.0"
    }

    private val json = Json { prettyPrint = true }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Gathers all data from repositories and maps them to export models.
     *
     * This is a one-shot suspend function — it does not observe ongoing changes.
     * It must be called from a coroutine context backed by an IO dispatcher for
     * optimal performance when fetching large datasets.
     *
     * @return A fully populated [ExportData] ready for serialization.
     */
    suspend fun buildExportData(): ExportData {
        val dailySummaries = stepRepository.getAllDailySummaries()
        val hourlyAggregates = stepRepository.getAllHourlyAggregates()
        val transitions = stepRepository.getAllTransitions()
        val sessions = cyclingRepository.getAllSessions()
        val sensorWindows = sensorWindowDao.getAllWindows()

        return ExportData(
            metadata = ExportMetadata(
                exportDate = Instant.now().toString(),
                appVersion = APP_VERSION,
                deviceModel = deviceModel,
            ),
            dailySummaries = dailySummaries.map { summary ->
                ExportDailySummary(
                    date = summary.date,
                    totalSteps = summary.totalSteps,
                    totalDistance = summary.totalDistance,
                    walkingMinutes = summary.walkingMinutes,
                    cyclingMinutes = summary.cyclingMinutes,
                )
            },
            hourlyAggregates = hourlyAggregates.map { aggregate ->
                ExportHourlyAggregate(
                    id = aggregate.id,
                    timestamp = aggregate.timestamp,
                    stepCountDelta = aggregate.stepCountDelta,
                    detectedActivity = aggregate.detectedActivity,
                )
            },
            activityTransitions = transitions.map { transition ->
                ExportActivityTransition(
                    id = transition.id,
                    timestamp = transition.timestamp,
                    fromActivity = transition.fromActivity,
                    toActivity = transition.toActivity,
                    isManualOverride = transition.isManualOverride,
                )
            },
            cyclingSessions = sessions.map { session ->
                ExportCyclingSession(
                    id = session.id,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    durationMinutes = session.durationMinutes,
                    isManualOverride = session.isManualOverride,
                )
            },
            sensorWindows = sensorWindows.map { window ->
                ExportSensorWindow(
                    id = window.id,
                    timestamp = window.timestamp,
                    magnitudeVariance = window.magnitudeVariance,
                    stepFrequencyHz = window.stepFrequencyHz,
                    stepCount = window.stepCount,
                )
            },
        )
    }

    /**
     * Serializes an [ExportData] object to a pretty-printed JSON string.
     *
     * This is a pure function with no side effects — suitable for unit testing
     * without Android dependencies.
     *
     * @param exportData The data to serialize.
     * @return A human-readable JSON string.
     */
    fun serializeToJson(exportData: ExportData): String =
        json.encodeToString(ExportData.serializer(), exportData)
}
