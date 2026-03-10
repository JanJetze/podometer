// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.StepBucket
import com.podometer.data.db.StepBucketDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [StepBucketRepository].
 *
 * Uses a fake DAO implementation to avoid Android dependencies while
 * verifying delegation and basic repository behaviour.
 */
class StepBucketRepositoryTest {

    // ─── Fake DAO ───────────────────────────────────────────────────────────

    private class FakeStepBucketDao(
        private val bucketsForDayFlow: Flow<List<StepBucket>> = flowOf(emptyList()),
        private val bucketsInRangeFlow: Flow<List<StepBucket>> = flowOf(emptyList()),
    ) : StepBucketDao {
        var upsertedBucket: StepBucket? = null
        var deleteAllCalled: Boolean = false

        override suspend fun upsert(bucket: StepBucket) {
            upsertedBucket = bucket
        }

        override fun getBucketsForDay(startOfDay: Long, endOfDay: Long): Flow<List<StepBucket>> =
            bucketsForDayFlow

        override fun getBucketsInRange(start: Long, end: Long): Flow<List<StepBucket>> =
            bucketsInRangeFlow

        override suspend fun getStepsForBucket(bucketTimestamp: Long): Int? = null

        override suspend fun getAllBuckets(): List<StepBucket> = emptyList()

        override suspend fun deleteAll() {
            deleteAllCalled = true
        }
    }

    // ─── upsert ──────────────────────────────────────────────────────────────

    @Test
    fun `upsert delegates to StepBucketDao`() = runTest {
        val dao = FakeStepBucketDao()
        val repo = StepBucketRepository(dao)
        val bucket = StepBucket(timestamp = 1_000_000L, stepCount = 42)

        repo.upsert(bucket)

        assertEquals(bucket, dao.upsertedBucket)
    }

    @Test
    fun `upsert stores the provided step count`() = runTest {
        val dao = FakeStepBucketDao()
        val repo = StepBucketRepository(dao)
        val bucket = StepBucket(timestamp = 5 * 60_000L, stepCount = 250)

        repo.upsert(bucket)

        assertEquals(250, dao.upsertedBucket?.stepCount)
    }

    // ─── getBucketsForDay ────────────────────────────────────────────────────

    @Test
    fun `getBucketsForDay returns empty list when no rows exist`() = runTest {
        val dao = FakeStepBucketDao(bucketsForDayFlow = flowOf(emptyList()))
        val repo = StepBucketRepository(dao)

        val result = repo.getBucketsForDay(0L, 86_399_999L).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getBucketsForDay delegates to StepBucketDao and returns buckets`() = runTest {
        val buckets = listOf(
            StepBucket(timestamp = 0L, stepCount = 10),
            StepBucket(timestamp = 5 * 60_000L, stepCount = 20),
        )
        val dao = FakeStepBucketDao(bucketsForDayFlow = flowOf(buckets))
        val repo = StepBucketRepository(dao)

        val result = repo.getBucketsForDay(0L, 86_399_999L).first()

        assertEquals(2, result.size)
        assertEquals(10, result[0].stepCount)
        assertEquals(20, result[1].stepCount)
    }

    // ─── getBucketsInRange ───────────────────────────────────────────────────

    @Test
    fun `getBucketsInRange returns empty list when no rows exist`() = runTest {
        val dao = FakeStepBucketDao(bucketsInRangeFlow = flowOf(emptyList()))
        val repo = StepBucketRepository(dao)

        val result = repo.getBucketsInRange(1_000_000L, 2_000_000L).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getBucketsInRange delegates to StepBucketDao and returns buckets`() = runTest {
        val buckets = listOf(
            StepBucket(timestamp = 1_200_000L, stepCount = 30),
            StepBucket(timestamp = 1_500_000L, stepCount = 55),
        )
        val dao = FakeStepBucketDao(bucketsInRangeFlow = flowOf(buckets))
        val repo = StepBucketRepository(dao)

        val result = repo.getBucketsInRange(1_000_000L, 2_000_000L).first()

        assertEquals(2, result.size)
        assertEquals(30, result[0].stepCount)
        assertEquals(55, result[1].stepCount)
    }

    // ─── deleteAll ───────────────────────────────────────────────────────────

    @Test
    fun `deleteAll delegates to StepBucketDao`() = runTest {
        val dao = FakeStepBucketDao()
        val repo = StepBucketRepository(dao)

        repo.deleteAll()

        assertTrue(dao.deleteAllCalled)
    }

    // ─── Class existence ────────────────────────────────────────────────────

    @Test
    fun `StepBucketRepository class exists in repository package`() {
        val clazz = StepBucketRepository::class.java
        assertNotNull(clazz)
        assertTrue(
            "StepBucketRepository must be in com.podometer.data.repository",
            clazz.name == "com.podometer.data.repository.StepBucketRepository",
        )
    }
}
