// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for the Podometer app.
 *
 * Version history:
 *   1 — Initial schema: hourly step aggregates, activity transitions,
 *       daily summaries, and cycling sessions.
 *
 * No migration strategy is needed for version 1. A migration path will be
 * added when the schema is updated in a future version.
 */
@Database(
    entities = [
        HourlyStepAggregate::class,
        ActivityTransition::class,
        DailySummary::class,
        CyclingSession::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PodometerDatabase : RoomDatabase() {

    abstract fun stepDao(): StepDao

    abstract fun activityTransitionDao(): ActivityTransitionDao

    abstract fun cyclingSessionDao(): CyclingSessionDao
}
