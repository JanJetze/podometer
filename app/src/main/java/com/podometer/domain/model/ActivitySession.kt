// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.model

/**
 * A consolidated activity session derived from a pair of transitions.
 *
 * Unlike [TransitionEvent] which captures individual state changes, an
 * [ActivitySession] represents a continuous period of a single activity
 * (WALKING or CYCLING) with a start time, optional end time, and duration.
 *
 * @property activity           The activity performed during this session.
 * @property startTime          Epoch-millisecond timestamp when the activity began.
 * @property endTime            Epoch-millisecond timestamp when the activity ended,
 *                              or `null` if the session is still ongoing.
 * @property startTransitionId  Database ID of the [TransitionEvent] that opened
 *                              this session. Used for override/reclassification.
 * @property isManualOverride   True when the opening transition was manually
 *                              overridden by the user.
 * @property stepCount          Total steps detected during this session.
 */
data class ActivitySession(
    val activity: ActivityState,
    val startTime: Long,
    val endTime: Long?,
    val startTransitionId: Int,
    val isManualOverride: Boolean,
    val stepCount: Int = 0,
) {
    companion object {
        /** Default session duration used for new/ongoing sessions (30 minutes). */
        const val DEFAULT_DURATION_MS = 30 * 60_000L
    }

    /** True when this session was created manually and has no backing transition. */
    val isNew: Boolean get() = startTransitionId == 0

    /** Returns [endTime] or a default end time (start + 30 min) for ongoing sessions. */
    fun effectiveEndTime(): Long = endTime ?: (startTime + DEFAULT_DURATION_MS)
}

/**
 * Builds a list of [ActivitySession]s from chronologically ordered [transitions].
 *
 * Each transition whose [TransitionEvent.toActivity] is [ActivityState.WALKING] or
 * [ActivityState.CYCLING] opens a new session. The session ends at the next
 * transition's timestamp, or stays `null` (ongoing) if it is the last transition
 * and the activity is not [ActivityState.STILL].
 *
 * STILL transitions only close previous sessions — they are not emitted as
 * standalone sessions.
 *
 * @param transitions Chronologically ordered list of today's transitions.
 * @param nowMillis   Current wall-clock time; unused but reserved for future
 *                    ongoing-duration display.
 * @return Consolidated sessions in chronological order.
 */
fun buildActivitySessions(
    transitions: List<TransitionEvent>,
    nowMillis: Long,
): List<ActivitySession> {
    if (transitions.isEmpty()) return emptyList()

    val sessions = mutableListOf<ActivitySession>()
    var openSession: ActivitySession? = null

    for (transition in transitions) {
        // Close any open session at this transition's timestamp
        if (openSession != null) {
            sessions.add(openSession.copy(endTime = transition.timestamp))
            openSession = null
        }

        // Open a new session for WALKING or CYCLING
        if (transition.toActivity == ActivityState.WALKING ||
            transition.toActivity == ActivityState.CYCLING
        ) {
            openSession = ActivitySession(
                activity = transition.toActivity,
                startTime = transition.timestamp,
                endTime = null,
                startTransitionId = transition.id,
                isManualOverride = transition.isManualOverride,
            )
        }
    }

    // If there's still an open session, add it as ongoing
    if (openSession != null) {
        sessions.add(openSession)
    }

    return sessions
}
