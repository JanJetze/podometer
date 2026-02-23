// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.export

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            appVersion = "1.0.0",
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

    // ─── ExportActivityTransition ────────────────────────────────────────────

    @Test
    fun `ExportActivityTransition round-trip serialization preserves all fields`() {
        val transition = ExportActivityTransition(
            id = 7,
            timestamp = 1_740_304_800_000L,
            fromActivity = "STILL",
            toActivity = "WALKING",
            isManualOverride = false,
        )

        val encoded = json.encodeToString(transition)
        val decoded = json.decodeFromString<ExportActivityTransition>(encoded)

        assertEquals(transition.id, decoded.id)
        assertEquals(transition.timestamp, decoded.timestamp)
        assertEquals(transition.fromActivity, decoded.fromActivity)
        assertEquals(transition.toActivity, decoded.toActivity)
        assertEquals(transition.isManualOverride, decoded.isManualOverride)
    }

    @Test
    fun `ExportActivityTransition serializes isManualOverride true`() {
        val transition = ExportActivityTransition(
            id = 3,
            timestamp = 9_000L,
            fromActivity = "WALKING",
            toActivity = "CYCLING",
            isManualOverride = true,
        )

        val encoded = json.encodeToString(transition)
        val decoded = json.decodeFromString<ExportActivityTransition>(encoded)

        assertEquals(true, decoded.isManualOverride)
    }

    // ─── ExportCyclingSession ────────────────────────────────────────────────

    @Test
    fun `ExportCyclingSession round-trip serialization preserves all fields when endTime is non-null`() {
        val session = ExportCyclingSession(
            id = 1,
            startTime = 1_740_300_000_000L,
            endTime = 1_740_301_000_000L,
            durationMinutes = 16,
            isManualOverride = false,
        )

        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<ExportCyclingSession>(encoded)

        assertEquals(session.id, decoded.id)
        assertEquals(session.startTime, decoded.startTime)
        assertEquals(session.endTime, decoded.endTime)
        assertEquals(session.durationMinutes, decoded.durationMinutes)
        assertEquals(session.isManualOverride, decoded.isManualOverride)
    }

    @Test
    fun `ExportCyclingSession round-trip serialization preserves null endTime for ongoing session`() {
        val session = ExportCyclingSession(
            id = 2,
            startTime = 1_740_304_800_000L,
            endTime = null,
            durationMinutes = 0,
            isManualOverride = false,
        )

        val encoded = json.encodeToString(session)
        val decoded = json.decodeFromString<ExportCyclingSession>(encoded)

        assertNull(decoded.endTime)
    }

    // ─── ExportData (full container) ─────────────────────────────────────────

    @Test
    fun `ExportData round-trip serialization preserves all nested data`() {
        val exportData = ExportData(
            metadata = ExportMetadata(
                exportDate = "2026-02-23T10:00:00Z",
                appVersion = "1.0.0",
                deviceModel = "Pixel 7",
            ),
            dailySummaries = listOf(
                ExportDailySummary("2026-02-22", 9000, 6.75f, 80, 10),
                ExportDailySummary("2026-02-23", 5000, 3.75f, 45, 0),
            ),
            hourlyAggregates = listOf(
                ExportHourlyAggregate(1, 1_740_218_400_000L, 400, "WALKING"),
            ),
            activityTransitions = listOf(
                ExportActivityTransition(1, 1_740_218_400_000L, "STILL", "WALKING", false),
            ),
            cyclingSessions = listOf(
                ExportCyclingSession(1, 1_740_218_400_000L, 1_740_219_000_000L, 10, false),
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
        assertEquals(1, decoded.activityTransitions.size)
        assertEquals("WALKING", decoded.activityTransitions[0].toActivity)
        assertEquals(1, decoded.cyclingSessions.size)
        assertEquals(10, decoded.cyclingSessions[0].durationMinutes)
    }

    @Test
    fun `ExportData round-trip with empty lists`() {
        val exportData = ExportData(
            metadata = ExportMetadata("2026-02-23T10:00:00Z", "1.0.0", "Generic"),
            dailySummaries = emptyList(),
            hourlyAggregates = emptyList(),
            activityTransitions = emptyList(),
            cyclingSessions = emptyList(),
        )

        val encoded = json.encodeToString(exportData)
        val decoded = json.decodeFromString<ExportData>(encoded)

        assertEquals(0, decoded.dailySummaries.size)
        assertEquals(0, decoded.hourlyAggregates.size)
        assertEquals(0, decoded.activityTransitions.size)
        assertEquals(0, decoded.cyclingSessions.size)
    }

    @Test
    fun `ExportData JSON contains metadata export date field`() {
        val exportData = ExportData(
            metadata = ExportMetadata("2026-02-23T10:00:00Z", "1.0.0", "Pixel 7"),
            dailySummaries = emptyList(),
            hourlyAggregates = emptyList(),
            activityTransitions = emptyList(),
            cyclingSessions = emptyList(),
        )

        val encoded = json.encodeToString(exportData)

        assert(encoded.contains("2026-02-23T10:00:00Z")) {
            "JSON should contain the export date but was: $encoded"
        }
        assert(encoded.contains("1.0.0")) {
            "JSON should contain the app version but was: $encoded"
        }
    }
}
