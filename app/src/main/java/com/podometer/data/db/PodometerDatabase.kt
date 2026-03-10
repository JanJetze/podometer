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
 *   2 — Added sensor_windows table for raw classifier window storage
 *       (7-day retention, ~6 MB).
 *   3 — Consolidate 5-second sensor windows into 30-second windows.
 *       Keeps the window with the highest magnitudeVariance per 30 s slot.
 *   4 — Added manual_session_overrides table.
 *   5 — v2.0.0 migration: dropped activity_transitions, cycling_sessions,
 *       sensor_windows, and manual_session_overrides tables.
 *       Only hourly_step_aggregates and daily_summaries remain.
 *   6 — v2.1.0 migration: replaced hourly_step_aggregates with step_buckets
 *       (5-minute clock-aligned buckets). Destructive migration from v5.
 */
@Database(
    entities = [
        StepBucket::class,
        DailySummary::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class PodometerDatabase : RoomDatabase() {

    abstract fun stepDao(): StepDao

    abstract fun stepBucketDao(): StepBucketDao
}
