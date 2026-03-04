// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.repository

import com.podometer.data.db.SensorWindow
import com.podometer.data.db.SensorWindowDao
import com.podometer.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for raw sensor window data.
 *
 * Wraps [SensorWindowDao] and provides convenience methods for day-based queries
 * and retention cleanup.
 */
@Singleton
class SensorWindowRepository @Inject constructor(
    private val sensorWindowDao: SensorWindowDao,
) {

    /** Inserts a single sensor window row. */
    suspend fun insertWindow(window: SensorWindow) {
        sensorWindowDao.insert(window)
    }

    /**
     * Returns all sensor windows for the day containing [date], ordered by timestamp.
     *
     * Queries midnight-to-midnight (exclusive end) in the system default time zone.
     */
    fun getWindowsForDay(date: LocalDate): Flow<List<SensorWindow>> {
        val startMs = DateTimeUtils.startOfDayMillis(date)
        val endMs = startMs + 86_400_000L - 1 // inclusive end of day
        return sensorWindowDao.getWindowsBetween(startMs, endMs)
    }

    /**
     * Deletes all sensor windows older than [cutoffMs].
     *
     * Call on service start to enforce 7-day retention.
     */
    suspend fun deleteOlderThan(cutoffMs: Long) {
        sensorWindowDao.deleteOlderThan(cutoffMs)
    }
}
