// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.DailySummary
import com.podometer.data.db.HourlyStepAggregate
import com.podometer.data.db.StepDao
import com.podometer.data.export.ExportData
import com.podometer.data.repository.StepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ExportDataUseCase].
 *
 * Uses fake DAOs to verify that the use case correctly gathers all data from
 * repositories, maps to export models, and produces valid serialized JSON.
 * All tests run on the JVM without Android dependencies.
 */
class ExportDataUseCaseTest {

    // ─── Fake DAOs ───────────────────────────────────────────────────────────

    private class FakeStepDao(
        private val dailySummaries: List<DailySummary> = emptyList(),
        private val hourlyAggregates: List<HourlyStepAggregate> = emptyList(),
    ) : StepDao {
        override fun getTodayHourlyAggregates(todayStart: Long): Flow<List<HourlyStepAggregate>> =
            flowOf(emptyList())

        override fun getTodayTotalSteps(todayStart: Long): Flow<Int?> = flowOf(null)

        override fun getDailySummary(date: String): Flow<DailySummary?> = flowOf(null)

        override fun getWeeklyDailySummaries(
            startDate: String,
            endDate: String,
        ): Flow<List<DailySummary>> = flowOf(emptyList())

        override suspend fun getStepsForHour(hourTimestamp: Long): Int? = null

        override suspend fun getTodayTotalStepsSnapshot(todayStart: Long): Int? = null

        override suspend fun deleteHourlyAggregateByTimestamp(hourTimestamp: Long) = Unit

        override suspend fun insertHourlyAggregate(aggregate: HourlyStepAggregate) = Unit

        override suspend fun upsertHourlyAggregate(aggregate: HourlyStepAggregate) = Unit

        override suspend fun upsertDailySummary(summary: DailySummary) = Unit

        override suspend fun upsertStepsAndDistance(date: String, totalSteps: Int, totalDistance: Float) = Unit

        override suspend fun addWalkingMinutes(date: String, minutes: Int) = Unit

        override suspend fun addCyclingMinutes(date: String, minutes: Int) = Unit

        override suspend fun getAllDailySummaries(): List<DailySummary> = dailySummaries

        override suspend fun getAllHourlyAggregates(): List<HourlyStepAggregate> = hourlyAggregates

        override suspend fun insertAllDailySummaries(summaries: List<DailySummary>) { }

        override suspend fun insertAllHourlyAggregates(aggregates: List<HourlyStepAggregate>) { }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun makeUseCase(
        dailySummaries: List<DailySummary> = emptyList(),
        hourlyAggregates: List<HourlyStepAggregate> = emptyList(),
        deviceModel: String = "Test Device",
    ): ExportDataUseCase {
        val stepRepo = StepRepository(
            FakeStepDao(dailySummaries, hourlyAggregates),
        )
        return ExportDataUseCase(stepRepo, deviceModel)
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    @Test
    fun `buildExportData returns ExportData with metadata`() = runTest {
        val useCase = makeUseCase(deviceModel = "Pixel 7")

        val exportData = useCase.buildExportData()

        assertNotNull(exportData.metadata)
        assertEquals("2.0.0", exportData.metadata.appVersion)
        assertEquals("Pixel 7", exportData.metadata.deviceModel)
        assertNotNull(exportData.metadata.exportDate)
        assertTrue(exportData.metadata.exportDate.isNotEmpty())
    }

    @Test
    fun `buildExportData maps daily summaries to export models`() = runTest {
        val summaries = listOf(
            DailySummary("2026-02-22", 9000, 6.75f, 80, 10),
            DailySummary("2026-02-23", 5000, 3.75f, 45, 0),
        )
        val useCase = makeUseCase(dailySummaries = summaries)

        val exportData = useCase.buildExportData()

        assertEquals(2, exportData.dailySummaries.size)
        assertEquals("2026-02-22", exportData.dailySummaries[0].date)
        assertEquals(9000, exportData.dailySummaries[0].totalSteps)
        assertEquals(6.75f, exportData.dailySummaries[0].totalDistance, 0.0001f)
        assertEquals(80, exportData.dailySummaries[0].walkingMinutes)
        assertEquals(10, exportData.dailySummaries[0].cyclingMinutes)
        assertEquals("2026-02-23", exportData.dailySummaries[1].date)
    }

    @Test
    fun `buildExportData maps hourly aggregates to export models`() = runTest {
        val aggregates = listOf(
            HourlyStepAggregate(id = 1, timestamp = 1_740_218_400_000L, stepCountDelta = 350, detectedActivity = "WALKING"),
            HourlyStepAggregate(id = 2, timestamp = 1_740_222_000_000L, stepCountDelta = 0, detectedActivity = "STILL"),
        )
        val useCase = makeUseCase(hourlyAggregates = aggregates)

        val exportData = useCase.buildExportData()

        assertEquals(2, exportData.hourlyAggregates.size)
        assertEquals(1, exportData.hourlyAggregates[0].id)
        assertEquals(1_740_218_400_000L, exportData.hourlyAggregates[0].timestamp)
        assertEquals(350, exportData.hourlyAggregates[0].stepCountDelta)
        assertEquals("WALKING", exportData.hourlyAggregates[0].detectedActivity)
        assertEquals("STILL", exportData.hourlyAggregates[1].detectedActivity)
    }

    @Test
    fun `buildExportData returns empty lists when repositories have no data`() = runTest {
        val useCase = makeUseCase()

        val exportData = useCase.buildExportData()

        assertEquals(0, exportData.dailySummaries.size)
        assertEquals(0, exportData.hourlyAggregates.size)
    }

    @Test
    fun `serializeToJson produces valid JSON round-trip`() = runTest {
        val summaries = listOf(DailySummary("2026-02-23", 8000, 6.0f, 70, 0))
        val useCase = makeUseCase(dailySummaries = summaries)

        val exportData = useCase.buildExportData()
        val jsonString = useCase.serializeToJson(exportData)

        assertNotNull(jsonString)
        assertTrue(jsonString.isNotBlank())

        // Verify it parses back to ExportData
        val json = Json { prettyPrint = true }
        val decoded = json.decodeFromString<ExportData>(jsonString)
        assertEquals(1, decoded.dailySummaries.size)
        assertEquals("2026-02-23", decoded.dailySummaries[0].date)
        assertEquals(8000, decoded.dailySummaries[0].totalSteps)
    }

    @Test
    fun `serializeToJson output is human-readable with prettyPrint`() = runTest {
        val useCase = makeUseCase()

        val exportData = useCase.buildExportData()
        val jsonString = useCase.serializeToJson(exportData)

        // Pretty-printed JSON contains newlines
        assertTrue(
            "JSON output should be pretty-printed with newlines",
            jsonString.contains("\n"),
        )
    }

    @Test
    fun `exportDate in metadata is in ISO 8601 format`() = runTest {
        val useCase = makeUseCase()

        val exportData = useCase.buildExportData()

        // ISO 8601 basic check: contains 'T' separator and 'Z' or offset
        val exportDate = exportData.metadata.exportDate
        assertTrue(
            "Export date '$exportDate' should contain 'T' as ISO 8601 separator",
            exportDate.contains("T"),
        )
    }
}
