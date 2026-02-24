// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.service

import com.podometer.data.db.CyclingSession

/**
 * Manages the in-memory lifecycle of a cycling session.
 *
 * This class is pure Kotlin with no Android framework dependencies, making it
 * fully unit-testable on the JVM.
 *
 * Responsibilities:
 * - Track the ongoing cycling session's in-memory state (start time, DB id).
 * - Produce [CyclingSession] entities ready for DB insertion/update.
 * - Expose [isStepCountingPaused] so [StepTrackingService] can skip step
 *   accumulation while cycling is active (step-frequency tracking continues
 *   regardless so the classifier can detect when the user resumes walking).
 *
 * ## Thread safety
 *
 * All public methods are `@Synchronized` on this instance. The lock is
 * acquired during classifier evaluation (~5 s period) and step-event
 * processing, so contention overhead is negligible.
 *
 * ## Race-window note
 *
 * [isStepCountingPaused] activates immediately on [startSession] (not only
 * after [setOngoingSessionId]). This closes the race between the DB insert
 * and the second synchronized call that previously allowed steps to be
 * counted during the window.
 */
class CyclingSessionManager {

    /** Epoch-millisecond timestamp when the current session started. */
    private var sessionStartTimeMs: Long = 0L

    /** DB-generated primary key of the ongoing session, or `null` when inactive. */
    private var ongoingSessionId: Int? = null

    /**
     * Set to `true` as soon as [startSession] is called; cleared by [endSession]
     * and [reset]. Drives [isStepCountingPaused] so step accumulation is blocked
     * from the very start of a session, before the DB round-trip completes and
     * [setOngoingSessionId] is called.
     */
    private var sessionActive: Boolean = false

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns `true` while a cycling session is active (i.e. after [startSession]
     * and before [endSession] or [reset]).
     *
     * This becomes `true` immediately on [startSession], before the DB insert
     * completes and [setOngoingSessionId] is called, which prevents any step
     * accumulation during that window.
     *
     * Step accumulation in [StepTrackingService] should be skipped while this
     * is `true`. Step-frequency tracking must **not** be skipped — the
     * classifier needs it to detect the return to walking.
     */
    val isStepCountingPaused: Boolean
        @Synchronized get() = sessionActive

    /**
     * Creates a new [CyclingSession] entity representing the start of a session
     * and activates step-count pausing immediately.
     *
     * The returned entity has `id = 0` (Room will auto-generate the real id on
     * insert). Call [setOngoingSessionId] with the id returned by the DAO after
     * insertion so that [hasOngoingSession] returns `true` and [endSession] can
     * produce a correctly-keyed entity for the DB update.
     *
     * @param startTimeMs Epoch-millisecond timestamp when cycling was detected.
     * @return A [CyclingSession] ready for DB insertion.
     */
    @Synchronized
    fun startSession(startTimeMs: Long): CyclingSession {
        sessionStartTimeMs = startTimeMs
        sessionActive = true
        return CyclingSession(
            id = 0,
            startTime = startTimeMs,
            endTime = null,
            durationMinutes = 0,
            isManualOverride = false,
        )
    }

    /**
     * Closes the ongoing session by computing its duration and setting its
     * end time. Also clears [isStepCountingPaused] regardless of whether a
     * fully-registered session exists.
     *
     * Returns `null` if there is no ongoing session (i.e. [setOngoingSessionId]
     * was never called after the last [startSession], or the session was already
     * ended).
     *
     * Duration is rounded to the nearest whole minute.
     *
     * @param endTimeMs Epoch-millisecond timestamp when cycling ended.
     * @return A [CyclingSession] ready for DB update, or `null` if inactive.
     */
    @Synchronized
    fun endSession(endTimeMs: Long): CyclingSession? {
        sessionActive = false
        val id = ongoingSessionId ?: return null

        val durationMs = endTimeMs - sessionStartTimeMs
        val durationMinutes = ((durationMs + 30_000L) / 60_000L).toInt()

        val session = CyclingSession(
            id = id,
            startTime = sessionStartTimeMs,
            endTime = endTimeMs,
            durationMinutes = durationMinutes,
            isManualOverride = false,
        )

        ongoingSessionId = null
        sessionStartTimeMs = 0L
        return session
    }

    /**
     * Records the DB-generated primary key for the ongoing session.
     *
     * Must be called after [startSession] and after the DAO insert returns the
     * generated id. Once called, [hasOngoingSession] returns `true`.
     *
     * Note: [isStepCountingPaused] is already `true` from the [startSession]
     * call — this method does not change it.
     *
     * @param id The auto-generated primary key returned by
     *   [com.podometer.data.db.CyclingSessionDao.insertSession].
     */
    @Synchronized
    fun setOngoingSessionId(id: Int) {
        ongoingSessionId = id
    }

    /**
     * Returns the DB-generated primary key of the ongoing session, or `null`
     * if there is no active session.
     */
    @Synchronized
    fun getOngoingSessionId(): Int? = ongoingSessionId

    /**
     * Returns `true` if there is a fully-registered ongoing session (i.e.
     * [setOngoingSessionId] has been called and the session has not yet ended).
     *
     * Note: this returns `false` between [startSession] and [setOngoingSessionId],
     * even though [isStepCountingPaused] is already `true` during that window.
     */
    @Synchronized
    fun hasOngoingSession(): Boolean = ongoingSessionId != null

    /**
     * Resets all state to the initial (inactive) condition.
     *
     * Call this from [StepTrackingService.onDestroy] together with
     * [com.podometer.data.sensor.CyclingClassifier.reset] to ensure stale state
     * does not carry over to the next service session.
     */
    @Synchronized
    fun reset() {
        ongoingSessionId = null
        sessionStartTimeMs = 0L
        sessionActive = false
    }
}
