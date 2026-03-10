// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity recording a single 5-minute clock-aligned step-count bucket.
 *
 * Timestamps are aligned to 5-minute boundaries on the clock
 * (:00, :05, :10, :15, :20, :25, :30, :35, :40, :45, :50, :55).
 * Each row represents steps counted during that 5-minute window.
 *
 * [timestamp] serves as the primary key, so there is exactly one row per
 * 5-minute slot. Upsert via [StepBucketDao.upsert] ensures that partial
 * writes during the same bucket are idempotent.
 */
@Entity(tableName = "step_buckets")
data class StepBucket(
    @PrimaryKey
    /** Epoch-millisecond timestamp for the start of this 5-minute bucket. */
    val timestamp: Long,
    /** Number of steps detected during this 5-minute window. */
    val stepCount: Int,
)
