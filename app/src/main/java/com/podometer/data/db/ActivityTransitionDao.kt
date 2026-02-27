// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ActivityTransition] data.
 *
 * Flow-returning queries emit a new value whenever the underlying table changes.
 * Write operations are [suspend] so callers must use a coroutine context.
 */
@Dao
interface ActivityTransitionDao {

    /**
     * Returns all [ActivityTransition] rows with a timestamp >= [todayStart],
     * ordered by timestamp ascending.
     */
    @Query("SELECT * FROM activity_transitions WHERE timestamp >= :todayStart ORDER BY timestamp ASC")
    fun getTodayTransitions(todayStart: Long): Flow<List<ActivityTransition>>

    /** Inserts a new [ActivityTransition] row. */
    @Insert
    suspend fun insertTransition(transition: ActivityTransition)

    /** Updates an existing [ActivityTransition] row (matched by primary key). */
    @Update
    suspend fun updateTransition(transition: ActivityTransition)

    /**
     * Returns all [ActivityTransition] rows ordered by timestamp ascending.
     * One-shot suspend query intended for data export — not a [Flow].
     */
    @Query("SELECT * FROM activity_transitions ORDER BY timestamp ASC")
    suspend fun getAllTransitions(): List<ActivityTransition>

    /**
     * Returns the first [ActivityTransition] whose timestamp is strictly greater than
     * [afterTimestamp], ordered by timestamp ascending, or `null` if none exists.
     *
     * Used by [com.podometer.domain.usecase.OverrideActivityUseCaseImpl] to find the
     * next transition after a manual cycling override so that the session's end time
     * and duration can be computed immediately.
     */
    @Query("SELECT * FROM activity_transitions WHERE timestamp > :afterTimestamp ORDER BY timestamp ASC LIMIT 1")
    suspend fun getNextTransitionAfter(afterTimestamp: Long): ActivityTransition?
}
