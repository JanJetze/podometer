// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepBucket
import com.podometer.data.db.StepBucketDao
import com.podometer.data.db.StepDao
import com.podometer.data.export.ExportData
import com.podometer.data.repository.StepBucketRepository
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
    ) : StepDao {
        override fun getDailySummary(date: String): Flow<DailySummary?> = flowOf(null)

        override fun getWeeklyDailySummaries(
            startDate: String,
            endDate: String,
        ): Flow<List<DailySummary>> = flowOf(emptyList())

        override suspend fun getTodayTotalStepsSnapshot(date: String): Int? = null
        override suspend fun upsertDailySummary(summary: DailySummary) = Unit
        override suspend fun upsertStepsAndDistance(date: String, totalSteps: Int, totalDistance: Float) = Unit
        override suspend fun getAllDailySummaries(): List<DailySummary> = dailySummaries
        override suspend fun insertAllDailySummaries(summaries: List<DailySummary>) { }
    }

    private class FakeStepBucketDao(
        private val allBuckets: List<StepBucket> = emptyList(),
    ) : StepBucketDao {
        override suspend fun upsert(bucket: StepBucket) = Unit
        override fun getBucketsForDay(startOfDay: Long, endOfDay: Long): Flow<List<StepBucket>> = flowOf(emptyList())
        override fun getBucketsInRange(start: Long, end: Long): Flow<List<StepBucket>> = flowOf(emptyList())
        override suspend fun getStepsForBucket(bucketTimestamp: Long): Int? = null
        override suspend fun getAllBuckets(): List<StepBucket> = allBuckets
        override suspend fun deleteAll() = Unit
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun makeUseCase(
        dailySummaries: List<DailySummary> = emptyList(),
        stepBuckets: List<StepBucket> = emptyList(),
        deviceModel: String = "Test Device",
    ): ExportDataUseCase {
        val stepRepo = StepRepository(FakeStepDao(dailySummaries))
        val bucketRepo = StepBucketRepository(FakeStepBucketDao(stepBuckets))
        return ExportDataUseCase(stepRepo, bucketRepo, deviceModel)
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
            DailySummary("2026-02-22", 9000, 6.75f),
            DailySummary("2026-02-23", 5000, 3.75f),
        )
        val useCase = makeUseCase(dailySummaries = summaries)

        val exportData = useCase.buildExportData()

        assertEquals(2, exportData.dailySummaries.size)
        assertEquals("2026-02-22", exportData.dailySummaries[0].date)
        assertEquals(9000, exportData.dailySummaries[0].totalSteps)
        assertEquals(6.75f, exportData.dailySummaries[0].totalDistance, 0.0001f)
        assertEquals("2026-02-23", exportData.dailySummaries[1].date)
    }

    @Test
    fun `buildExportData maps step buckets to export models`() = runTest {
        val buckets = listOf(
            StepBucket(timestamp = 1_740_218_400_000L, stepCount = 350),
            StepBucket(timestamp = 1_740_218_700_000L, stepCount = 0),
        )
        val useCase = makeUseCase(stepBuckets = buckets)

        val exportData = useCase.buildExportData()

        assertEquals(2, exportData.stepBuckets.size)
        assertEquals(1_740_218_400_000L, exportData.stepBuckets[0].timestamp)
        assertEquals(350, exportData.stepBuckets[0].stepCount)
        assertEquals(0, exportData.stepBuckets[1].stepCount)
    }

    @Test
    fun `buildExportData returns empty lists when repositories have no data`() = runTest {
        val useCase = makeUseCase()

        val exportData = useCase.buildExportData()

        assertEquals(0, exportData.dailySummaries.size)
        assertEquals(0, exportData.stepBuckets.size)
    }

    @Test
    fun `serializeToJson produces valid JSON round-trip`() = runTest {
        val summaries = listOf(DailySummary("2026-02-23", 8000, 6.0f))
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
