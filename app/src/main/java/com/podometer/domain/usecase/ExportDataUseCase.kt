// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.export.ExportDailySummary
import com.podometer.data.export.ExportData
import com.podometer.data.export.ExportHourlyAggregate
import com.podometer.data.export.ExportMetadata
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
 * @param stepRepository Source for daily summaries and hourly aggregates.
 * @param deviceModel Device model string included in export metadata (injectable for testing).
 */
class ExportDataUseCase(
    private val stepRepository: StepRepository,
    private val deviceModel: String,
) {

    // ─── Constants ───────────────────────────────────────────────────────────

    private companion object {
        /** App version included in every export. */
        const val APP_VERSION = "2.0.0"
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
