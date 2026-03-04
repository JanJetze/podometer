// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [SensorWindow] entities.
 *
 * Provides insert, range query, and retention cleanup operations for the
 * raw sensor window storage.
 */
@Dao
interface SensorWindowDao {

    /** Inserts a single sensor window row. */
    @Insert
    suspend fun insert(window: SensorWindow)

    /**
     * Returns all sensor windows whose [SensorWindow.timestamp] falls within
     * [[startMs], [endMs]] (inclusive), ordered by timestamp ascending.
     *
     * Returns a [Flow] so callers can observe updates reactively.
     */
    @Query("SELECT * FROM sensor_windows WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp")
    fun getWindowsBetween(startMs: Long, endMs: Long): Flow<List<SensorWindow>>

    /**
     * Deletes all sensor windows older than [cutoffMs].
     *
     * Called on service start to enforce 7-day retention.
     */
    @Query("DELETE FROM sensor_windows WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)
}
