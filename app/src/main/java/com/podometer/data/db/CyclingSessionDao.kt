// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [CyclingSession] data.
 *
 * Flow-returning queries emit a new value whenever the underlying table changes.
 * Write operations are [suspend] so callers must use a coroutine context.
 */
@Dao
interface CyclingSessionDao {

    /**
     * Returns all [CyclingSession] rows with a [CyclingSession.startTime] >=
     * [todayStart], ordered by start time ascending.
     */
    @Query("SELECT * FROM cycling_sessions WHERE startTime >= :todayStart ORDER BY startTime ASC")
    fun getTodaySessions(todayStart: Long): Flow<List<CyclingSession>>

    /**
     * Inserts a new [CyclingSession] and returns the generated row ID.
     */
    @Insert
    suspend fun insertSession(session: CyclingSession): Long

    /** Inserts multiple [CyclingSession] rows. */
    @Insert
    suspend fun insertAllSessions(sessions: List<CyclingSession>)

    /** Updates an existing [CyclingSession] row (matched by primary key). */
    @Update
    suspend fun updateSession(session: CyclingSession)

    /** Deletes a [CyclingSession] row (matched by primary key). */
    @Delete
    suspend fun deleteSession(session: CyclingSession)

    /**
     * Returns all [CyclingSession] rows ordered by start time ascending.
     * One-shot suspend query intended for data export — not a [Flow].
     */
    @Query("SELECT * FROM cycling_sessions ORDER BY startTime ASC")
    suspend fun getAllSessions(): List<CyclingSession>

    /**
     * Returns the most recent [CyclingSession] where [CyclingSession.endTime]
     * is `null` (i.e. the session is still ongoing), or `null` if there is
     * no such session.
     *
     * Used on service start to recover from an app restart that occurred
     * during an active cycling session.
     */
    @Query("SELECT * FROM cycling_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getOngoingSession(): CyclingSession?

    /**
     * Returns the most recent [CyclingSession] whose time range covers [timestamp],
     * or `null` if no such session exists.
     *
     * A session covers a timestamp when [CyclingSession.startTime] <= [timestamp] AND
     * ([CyclingSession.endTime] IS NULL OR [CyclingSession.endTime] >= [timestamp]).
     * When multiple sessions overlap (possible from data corruption or concurrent writes),
     * the one with the latest [CyclingSession.startTime] is returned.
     *
     * Used by the manual override use case to find which session to delete when the user
     * reclassifies a cycling transition back to a non-cycling activity.
     */
    @Query(
        "SELECT * FROM cycling_sessions WHERE startTime <= :timestamp " +
            "AND (endTime IS NULL OR endTime >= :timestamp) ORDER BY startTime DESC LIMIT 1",
    )
    suspend fun getSessionCoveringTimestamp(timestamp: Long): CyclingSession?
}
