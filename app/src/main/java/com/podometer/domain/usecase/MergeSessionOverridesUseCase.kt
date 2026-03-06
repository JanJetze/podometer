// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.ManualSessionOverride
import com.podometer.data.db.SensorWindow
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState

/**
 * Merges recomputed activity sessions with manual session overrides.
 *
 * Manual overrides take precedence: any recomputed session whose time range
 * overlaps with a manual override is replaced. Override sessions get their
 * step counts computed from the sensor [windows] that fall within their
 * time range. The result is a flat list of sessions sorted by start time.
 *
 * This is a pure function with no side effects, suitable for unit testing.
 *
 * @param recomputed Recomputed sessions from sensor window replay.
 * @param overrides  User-created manual session overrides.
 * @param windows    Sensor windows used to compute step counts for overrides.
 * @return Merged sessions sorted by start time.
 */
fun mergeSessionOverrides(
    recomputed: List<ActivitySession>,
    overrides: List<ManualSessionOverride>,
    windows: List<SensorWindow> = emptyList(),
): List<ActivitySession> {
    if (overrides.isEmpty()) return recomputed

    // Convert overrides to ActivitySessions with step counts from sensor windows
    val overrideSessions = overrides.map { override ->
        val steps = windows
            .filter { it.timestamp in override.startTime until override.endTime }
            .sumOf { it.stepCount }
        ActivitySession(
            activity = ActivityState.fromString(override.activity),
            startTime = override.startTime,
            endTime = override.endTime,
            startTransitionId = -override.id.toInt(), // negative to distinguish
            isManualOverride = true,
            stepCount = steps,
        )
    }

    // Remove recomputed sessions that overlap with any override
    val filteredRecomputed = recomputed.filter { session ->
        val sessionEnd = session.endTime ?: Long.MAX_VALUE
        overrides.none { override ->
            // Overlap: session and override share any time
            session.startTime < override.endTime && sessionEnd > override.startTime
        }
    }

    // Merge, exclude STILL overrides (they only suppress detected sessions),
    // and sort by start time
    val activeOverrides = overrideSessions.filter { it.activity != ActivityState.STILL }
    return (filteredRecomputed + activeOverrides).sortedBy { it.startTime }
}
