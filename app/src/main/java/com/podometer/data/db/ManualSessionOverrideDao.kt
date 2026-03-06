// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [ManualSessionOverride] entities.
 */
@Dao
interface ManualSessionOverrideDao {

    /** Inserts a new manual session override, returning the generated ID. */
    @Insert
    suspend fun insert(override: ManualSessionOverride): Long

    /** Updates an existing manual session override. */
    @Update
    suspend fun update(override: ManualSessionOverride)

    /** Deletes a manual session override by ID. */
    @Query("DELETE FROM manual_session_overrides WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Returns all overrides for a given date string, ordered by start time. */
    @Query("SELECT * FROM manual_session_overrides WHERE date = :date ORDER BY startTime")
    fun getOverridesForDate(date: String): Flow<List<ManualSessionOverride>>
}
