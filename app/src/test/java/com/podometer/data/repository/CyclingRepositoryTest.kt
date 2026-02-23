// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.CyclingSession
import com.podometer.data.db.CyclingSessionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CyclingRepository].
 *
 * Uses a fake DAO to verify delegation without Android dependencies.
 */
class CyclingRepositoryTest {

    // ─── Fake ───────────────────────────────────────────────────────────────

    private class FakeCyclingSessionDao(
        private val sessionsFlow: Flow<List<CyclingSession>> = flowOf(emptyList()),
        private val insertReturnId: Long = 1L,
    ) : CyclingSessionDao {
        var insertedSession: CyclingSession? = null
        var updatedSession: CyclingSession? = null
        var deletedSession: CyclingSession? = null

        override fun getTodaySessions(todayStart: Long): Flow<List<CyclingSession>> = sessionsFlow

        override suspend fun insertSession(session: CyclingSession): Long {
            insertedSession = session
            return insertReturnId
        }

        override suspend fun updateSession(session: CyclingSession) {
            updatedSession = session
        }

        override suspend fun deleteSession(session: CyclingSession) {
            deletedSession = session
        }

        override suspend fun getAllSessions(): List<CyclingSession> = emptyList()
    }

    // ─── getTodaySessions ────────────────────────────────────────────────────

    @Test
    fun `getTodaySessions delegates to CyclingSessionDao`() = runTest {
        val sessions = listOf(
            CyclingSession(id = 1, startTime = 1000L, endTime = 2000L, durationMinutes = 16),
        )
        val dao = FakeCyclingSessionDao(sessionsFlow = flowOf(sessions))
        val repo = CyclingRepository(dao)

        val result = repo.getTodaySessions().first()

        assertEquals(sessions, result)
    }

    @Test
    fun `getTodaySessions returns empty list when no rows`() = runTest {
        val dao = FakeCyclingSessionDao(sessionsFlow = flowOf(emptyList()))
        val repo = CyclingRepository(dao)

        val result = repo.getTodaySessions().first()

        assertTrue(result.isEmpty())
    }

    // ─── insertSession ───────────────────────────────────────────────────────

    @Test
    fun `insertSession delegates to CyclingSessionDao and returns generated id`() = runTest {
        val dao = FakeCyclingSessionDao(insertReturnId = 42L)
        val repo = CyclingRepository(dao)
        val session = CyclingSession(startTime = 1_700_000_000_000L, durationMinutes = 0)

        val returnedId = repo.insertSession(session)

        assertEquals(42L, returnedId)
        assertEquals(session, dao.insertedSession)
    }

    // ─── updateSession ───────────────────────────────────────────────────────

    @Test
    fun `updateSession delegates to CyclingSessionDao`() = runTest {
        val dao = FakeCyclingSessionDao()
        val repo = CyclingRepository(dao)
        val session = CyclingSession(id = 5, startTime = 1_700_000_000_000L, durationMinutes = 30)

        repo.updateSession(session)

        assertEquals(session, dao.updatedSession)
    }

    // ─── deleteSession ───────────────────────────────────────────────────────

    @Test
    fun `deleteSession delegates to CyclingSessionDao`() = runTest {
        val dao = FakeCyclingSessionDao()
        val repo = CyclingRepository(dao)
        val session = CyclingSession(id = 3, startTime = 1_700_000_000_000L, durationMinutes = 60)

        repo.deleteSession(session)

        assertEquals(session, dao.deletedSession)
    }

    // ─── Class existence ────────────────────────────────────────────────────

    @Test
    fun `CyclingRepository class exists in repository package`() {
        val clazz = CyclingRepository::class.java
        assertNotNull(clazz)
        assertTrue(
            "CyclingRepository must be in com.podometer.data.repository",
            clazz.name == "com.podometer.data.repository.CyclingRepository",
        )
    }
}
