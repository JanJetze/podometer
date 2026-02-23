// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.CyclingSession
import com.podometer.data.db.CyclingSessionDao
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for cycling session data.
 *
 * Delegates all persistence to [CyclingSessionDao].
 */
@Singleton
class CyclingRepository @Inject constructor(
    private val cyclingSessionDao: CyclingSessionDao,
) {

    // ─── Read ────────────────────────────────────────────────────────────────

    /** Returns all cycling sessions for today as a [Flow]. */
    fun getTodaySessions(): Flow<List<CyclingSession>> =
        cyclingSessionDao.getTodaySessions(getTodayStartMillis())

    // ─── Write ───────────────────────────────────────────────────────────────

    /**
     * Inserts a new cycling session and returns the generated row ID.
     */
    suspend fun insertSession(session: CyclingSession): Long =
        cyclingSessionDao.insertSession(session)

    /** Updates an existing cycling session row (matched by primary key). */
    suspend fun updateSession(session: CyclingSession) {
        cyclingSessionDao.updateSession(session)
    }

    /** Deletes a cycling session row (matched by primary key). */
    suspend fun deleteSession(session: CyclingSession) {
        cyclingSessionDao.deleteSession(session)
    }

    /**
     * Returns all cycling sessions ordered by start time ascending.
     * One-shot suspend function intended for data export.
     */
    suspend fun getAllSessions(): List<CyclingSession> =
        cyclingSessionDao.getAllSessions()

    /**
     * Returns the most recent ongoing [CyclingSession] (one with a null
     * [CyclingSession.endTime]), or `null` if there is no such session.
     *
     * Used by [com.podometer.service.StepTrackingService] on startup to
     * recover from an app restart that occurred during an active cycling session.
     */
    suspend fun getOngoingSession(): CyclingSession? =
        cyclingSessionDao.getOngoingSession()

    // ─── Helper ──────────────────────────────────────────────────────────────

    private fun getTodayStartMillis(): Long =
        LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
