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
        )

        val encoded = json.encodeToString(summary)
        val decoded = json.decodeFromString<ExportDailySummary>(encoded)

        assertEquals(summary.date, decoded.date)
        assertEquals(summary.totalSteps, decoded.totalSteps)
        assertEquals(summary.totalDistance, decoded.totalDistance, 0.0001f)
    }

    // ─── ExportStepBucket ────────────────────────────────────────────────────

    @Test
    fun `ExportStepBucket round-trip serialization preserves all fields`() {
        val bucket = ExportStepBucket(
            timestamp = 1_740_304_800_000L,
            stepCount = 350,
        )

        val encoded = json.encodeToString(bucket)
        val decoded = json.decodeFromString<ExportStepBucket>(encoded)

        assertEquals(bucket.timestamp, decoded.timestamp)
        assertEquals(bucket.stepCount, decoded.stepCount)
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
                ExportDailySummary("2026-02-22", 9000, 6.75f),
                ExportDailySummary("2026-02-23", 5000, 3.75f),
            ),
            stepBuckets = listOf(
                ExportStepBucket(1_740_218_400_000L, 42),
            ),
        )

        val encoded = json.encodeToString(exportData)
        val decoded = json.decodeFromString<ExportData>(encoded)

        assertEquals(exportData.metadata.exportDate, decoded.metadata.exportDate)
        assertEquals(exportData.metadata.appVersion, decoded.metadata.appVersion)
        assertEquals(2, decoded.dailySummaries.size)
        assertEquals("2026-02-22", decoded.dailySummaries[0].date)
        assertEquals(9000, decoded.dailySummaries[0].totalSteps)
        assertEquals(1, decoded.stepBuckets.size)
        assertEquals(42, decoded.stepBuckets[0].stepCount)
    }

    @Test
    fun `ExportData round-trip with empty lists`() {
        val exportData = ExportData(
            metadata = ExportMetadata("2026-02-23T10:00:00Z", "2.0.0", "Generic"),
            dailySummaries = emptyList(),
            stepBuckets = emptyList(),
        )

        val encoded = json.encodeToString(exportData)
        val decoded = json.decodeFromString<ExportData>(encoded)

        assertEquals(0, decoded.dailySummaries.size)
        assertEquals(0, decoded.stepBuckets.size)
    }

    @Test
    fun `ExportData JSON contains metadata export date field`() {
        val exportData = ExportData(
            metadata = ExportMetadata("2026-02-23T10:00:00Z", "2.0.0", "Pixel 7"),
            dailySummaries = emptyList(),
            stepBuckets = emptyList(),
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
