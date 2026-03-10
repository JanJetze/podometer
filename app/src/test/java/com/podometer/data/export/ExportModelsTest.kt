// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for export data models.
 *
 * Verifies round-trip serialization (serialize then deserialize) and
 * that all fields are preserved correctly. These tests run on the JVM
 * without Android dependencies.
 */
class ExportModelsTest {

    private val json = Json { prettyPrint = true }

    // ─── ExportMetadata ──────────────────────────────────────────────────────

    @Test
    fun `ExportMetadata round-trip serialization preserves all fields`() {
        val metadata = ExportMetadata(
            exportDate = "2026-02-23T10:00:00Z",
            appVersion = "2.0.0",
            deviceModel = "Pixel 7",
        )

        val encoded = json.encodeToString(metadata)
        val decoded = json.decodeFromString<ExportMetadata>(encoded)

        assertEquals(metadata.exportDate, decoded.exportDate)
        assertEquals(metadata.appVersion, decoded.appVersion)
        assertEquals(metadata.deviceModel, decoded.deviceModel)
    }

    // ─── ExportDailySummary ──────────────────────────────────────────────────

    @Test
    fun `ExportDailySummary round-trip serialization preserves all fields`() {
        val summary = ExportDailySummary(
            date = "2026-02-23",
            totalSteps = 8500,
            totalDistance = 6.375f,
            walkingMinutes = 70,
            cyclingMinutes = 20,
        )

        val encoded = json.encodeToString(summary)
        val decoded = json.decodeFromString<ExportDailySummary>(encoded)

        assertEquals(summary.date, decoded.date)
        assertEquals(summary.totalSteps, decoded.totalSteps)
        assertEquals(summary.totalDistance, decoded.totalDistance, 0.0001f)
        assertEquals(summary.walkingMinutes, decoded.walkingMinutes)
        assertEquals(summary.cyclingMinutes, decoded.cyclingMinutes)
    }

    // ─── ExportHourlyAggregate ───────────────────────────────────────────────

    @Test
    fun `ExportHourlyAggregate round-trip serialization preserves all fields`() {
        val aggregate = ExportHourlyAggregate(
            id = 42,
            timestamp = 1_740_304_800_000L,
            stepCountDelta = 350,
            detectedActivity = "WALKING",
        )

        val encoded = json.encodeToString(aggregate)
        val decoded = json.decodeFromString<ExportHourlyAggregate>(encoded)

        assertEquals(aggregate.id, decoded.id)
        assertEquals(aggregate.timestamp, decoded.timestamp)
        assertEquals(aggregate.stepCountDelta, decoded.stepCountDelta)
        assertEquals(aggregate.detectedActivity, decoded.detectedActivity)
    }

    // ─── ExportData (full container) ─────────────────────────────────────────

    @Test
    fun `ExportData round-trip serialization preserves all nested data`() {
        val exportData = ExportData(
            metadata = ExportMetadata(
                exportDate = "2026-02-23T10:00:00Z",
                appVersion = "2.0.0",
                deviceModel = "Pixel 7",
            ),
            dailySummaries = listOf(
                ExportDailySummary("2026-02-22", 9000, 6.75f, 80, 10),
                ExportDailySummary("2026-02-23", 5000, 3.75f, 45, 0),
            ),
            hourlyAggregates = listOf(
                ExportHourlyAggregate(1, 1_740_218_400_000L, 400, "WALKING"),
            ),
        )

        val encoded = json.encodeToString(exportData)
        val decoded = json.decodeFromString<ExportData>(encoded)

        assertEquals(exportData.metadata.exportDate, decoded.metadata.exportDate)
        assertEquals(exportData.metadata.appVersion, decoded.metadata.appVersion)
        assertEquals(2, decoded.dailySummaries.size)
        assertEquals("2026-02-22", decoded.dailySummaries[0].date)
        assertEquals(9000, decoded.dailySummaries[0].totalSteps)
        assertEquals(1, decoded.hourlyAggregates.size)
        assertEquals(400, decoded.hourlyAggregates[0].stepCountDelta)
    }

    @Test
    fun `ExportData round-trip with empty lists`() {
        val exportData = ExportData(
            metadata = ExportMetadata("2026-02-23T10:00:00Z", "2.0.0", "Generic"),
            dailySummaries = emptyList(),
            hourlyAggregates = emptyList(),
        )

        val encoded = json.encodeToString(exportData)
        val decoded = json.decodeFromString<ExportData>(encoded)

        assertEquals(0, decoded.dailySummaries.size)
        assertEquals(0, decoded.hourlyAggregates.size)
    }

    @Test
    fun `ExportData JSON contains metadata export date field`() {
        val exportData = ExportData(
            metadata = ExportMetadata("2026-02-23T10:00:00Z", "2.0.0", "Pixel 7"),
            dailySummaries = emptyList(),
            hourlyAggregates = emptyList(),
        )

        val encoded = json.encodeToString(exportData)

        assert(encoded.contains("2026-02-23T10:00:00Z")) {
            "JSON should contain the export date but was: $encoded"
        }
        assert(encoded.contains("2.0.0")) {
            "JSON should contain the app version but was: $encoded"
        }
    }
}
