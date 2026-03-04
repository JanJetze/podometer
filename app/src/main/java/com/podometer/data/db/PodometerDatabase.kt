// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for the Podometer app.
 *
 * Version history:
 *   1 — Initial schema: hourly step aggregates, activity transitions,
 *       daily summaries, and cycling sessions.
 *   2 — Added sensor_windows table for raw classifier window storage
 *       (7-day retention, ~6 MB).
 */
@Database(
    entities = [
        HourlyStepAggregate::class,
        ActivityTransition::class,
        DailySummary::class,
        CyclingSession::class,
        SensorWindow::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class PodometerDatabase : RoomDatabase() {

    abstract fun stepDao(): StepDao

    abstract fun activityTransitionDao(): ActivityTransitionDao

    abstract fun cyclingSessionDao(): CyclingSessionDao

    abstract fun sensorWindowDao(): SensorWindowDao

    companion object {
        /**
         * Migration from version 1 to 2: creates the `sensor_windows` table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sensor_windows (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        magnitudeVariance REAL NOT NULL,
                        stepFrequencyHz REAL NOT NULL,
                        stepCount INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
