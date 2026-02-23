// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.data.export

import kotlinx.serialization.Serializable

/**
 * Root container for a full data export.
 *
 * All lists are ordered by date/timestamp ascending to make the file
 * human-readable and easy to import into analysis tools.
 */
@Serializable
data class ExportData(
    /** Metadata about when and where the export was created. */
    val metadata: ExportMetadata,
    /** All daily activity summaries, ordered by date ascending. */
    val dailySummaries: List<ExportDailySummary>,
    /** All hourly step-count aggregates, ordered by timestamp ascending. */
    val hourlyAggregates: List<ExportHourlyAggregate>,
    /** All activity transition events, ordered by timestamp ascending. */
    val activityTransitions: List<ExportActivityTransition>,
    /** All cycling sessions, ordered by start time ascending. */
    val cyclingSessions: List<ExportCyclingSession>,
)

/**
 * Metadata included at the top of every export file.
 */
@Serializable
data class ExportMetadata(
    /** ISO 8601 timestamp when the export was created, e.g. "2026-02-23T10:00:00Z". */
    val exportDate: String,
    /** Application version string, e.g. "1.0.0". */
    val appVersion: String,
    /** Device model name, e.g. "Pixel 7". */
    val deviceModel: String,
)

/**
 * Export model mirroring the Room [com.podometer.data.db.DailySummary] entity.
 *
 * Kept separate from the Room entity so that database schema changes do not
 * affect the serialized export format.
 */
@Serializable
data class ExportDailySummary(
    /** Calendar date in "yyyy-MM-dd" format. */
    val date: String,
    /** Total steps accumulated over the day. */
    val totalSteps: Int,
    /** Total distance in kilometres. */
    val totalDistance: Float,
    /** Total minutes spent walking. */
    val walkingMinutes: Int,
    /** Total minutes spent cycling. */
    val cyclingMinutes: Int,
)

/**
 * Export model mirroring the Room [com.podometer.data.db.HourlyStepAggregate] entity.
 */
@Serializable
data class ExportHourlyAggregate(
    /** Database row ID. */
    val id: Int,
    /** Epoch-millisecond timestamp for the start of this hourly bucket. */
    val timestamp: Long,
    /** Number of steps detected during this hour. */
    val stepCountDelta: Int,
    /** Activity detected during this hour: "WALKING", "CYCLING", or "STILL". */
    val detectedActivity: String,
)

/**
 * Export model mirroring the Room [com.podometer.data.db.ActivityTransition] entity.
 */
@Serializable
data class ExportActivityTransition(
    /** Database row ID. */
    val id: Int,
    /** Epoch-millisecond timestamp when the transition occurred. */
    val timestamp: Long,
    /** Activity before this transition. */
    val fromActivity: String,
    /** Activity after this transition. */
    val toActivity: String,
    /** True when the user manually corrected the detected transition. */
    val isManualOverride: Boolean,
)

/**
 * Export model mirroring the Room [com.podometer.data.db.CyclingSession] entity.
 */
@Serializable
data class ExportCyclingSession(
    /** Database row ID. */
    val id: Int,
    /** Epoch-millisecond timestamp when the session started. */
    val startTime: Long,
    /** Epoch-millisecond timestamp when the session ended; null if ongoing. */
    val endTime: Long?,
    /** Duration of the session in minutes. */
    val durationMinutes: Int,
    /** True when the user manually created or overrode this session. */
    val isManualOverride: Boolean,
)
