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
 *   3 — Consolidate 5-second sensor windows into 30-second windows.
 *       Keeps the window with the highest magnitudeVariance per 30 s slot.
 */
@Database(
    entities = [
        HourlyStepAggregate::class,
        ActivityTransition::class,
        DailySummary::class,
        CyclingSession::class,
        SensorWindow::class,
        ManualSessionOverride::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class PodometerDatabase : RoomDatabase() {

    abstract fun stepDao(): StepDao

    abstract fun activityTransitionDao(): ActivityTransitionDao

    abstract fun cyclingSessionDao(): CyclingSessionDao

    abstract fun sensorWindowDao(): SensorWindowDao

    abstract fun manualSessionOverrideDao(): ManualSessionOverrideDao

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

        /**
         * Migration from version 2 to 3: consolidate 5-second sensor windows
         * into 30-second windows.
         *
         * For each 30-second time slot (timestamp / 30000), keeps only the
         * window with the highest magnitudeVariance. This preserves the
         * cycling signal (which depends on variance) while reducing storage
         * by ~6x. The stepFrequencyHz already encodes a 30-second sliding
         * window, so no information is lost from the step frequency perspective.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM sensor_windows WHERE id NOT IN (
                        SELECT id FROM (
                            SELECT id, ROW_NUMBER() OVER (
                                PARTITION BY timestamp / 30000
                                ORDER BY magnitudeVariance DESC
                            ) AS rn FROM sensor_windows
                        ) WHERE rn = 1
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Migration from version 3 to 4: creates the `manual_session_overrides`
         * table for user-edited activity session boundaries.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS manual_session_overrides (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        activity TEXT NOT NULL,
                        date TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
