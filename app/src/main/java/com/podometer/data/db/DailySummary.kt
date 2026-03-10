// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity storing the aggregated daily activity summary.
 *
 * [date] uses the format "yyyy-MM-dd" and serves as the primary key so that
 * each calendar day has exactly one row.
 */
@Entity(tableName = "daily_summaries")
data class DailySummary(
    @PrimaryKey
    /** Calendar date in "yyyy-MM-dd" format, e.g. "2026-02-17". */
    val date: String,
    /** Total steps accumulated over the day. */
    val totalSteps: Int,
    /** Total distance walked in kilometres. */
    val totalDistance: Float,
)
