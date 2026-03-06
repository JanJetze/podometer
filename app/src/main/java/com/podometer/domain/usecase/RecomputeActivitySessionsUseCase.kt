// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import com.podometer.data.db.SensorWindow
import com.podometer.data.repository.SensorWindowRepository
import com.podometer.data.sensor.AccelerometerSampleBuffer
import com.podometer.data.sensor.CyclingClassifier
import com.podometer.data.sensor.WindowFeatures
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.TransitionEvent
import com.podometer.domain.model.buildActivitySessions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replays stored sensor windows through a fresh [CyclingClassifier] to produce
 * activity sessions for a given date.
 *
 * This enables retroactive recomputation when classifier parameters change —
 * the raw 30-second windows are retained for 7 days and can be replayed at any
 * time to generate updated session data.
 */
interface RecomputeActivitySessionsUseCase {

    /**
     * Returns a [Flow] of [ActivitySession]s recomputed from stored sensor
     * windows for the given [date].
     *
     * @param date    The calendar date to recompute sessions for.
     * @param nowMillis Current wall-clock time for ongoing session calculation.
     */
    operator fun invoke(date: LocalDate, nowMillis: Long): Flow<List<ActivitySession>>
}

/**
 * Default implementation that queries [SensorWindowRepository] and replays
 * windows through a fresh [CyclingClassifier] instance.
 */
@Singleton
class RecomputeActivitySessionsUseCaseImpl @Inject constructor(
    private val sensorWindowRepository: SensorWindowRepository,
) : RecomputeActivitySessionsUseCase {

    override fun invoke(date: LocalDate, nowMillis: Long): Flow<List<ActivitySession>> =
        sensorWindowRepository.getWindowsForDay(date).map { windows ->
            replayWindows(windows, nowMillis)
        }

    companion object {
        /**
         * Replays a list of [SensorWindow]s through a fresh classifier, collecting
         * transitions and building activity sessions.
         *
         * This is a pure function (no side effects) and can be unit-tested directly.
         *
         * @param windows   Chronologically ordered sensor windows for a single day.
         * @param nowMillis Current wall-clock time for ongoing session calculation.
         * @return Consolidated activity sessions derived from the replayed transitions.
         */
        fun replayWindows(
            windows: List<SensorWindow>,
            nowMillis: Long,
        ): List<ActivitySession> {
            if (windows.isEmpty()) return emptyList()

            val classifier = CyclingClassifier()
            val transitions = mutableListOf<TransitionEvent>()
            var transitionId = 1

            for (window in windows) {
                val features = WindowFeatures(
                    magnitudeMean = 0.0, // not stored; unused by classifier
                    magnitudeStd = 0.0,  // not stored; unused by classifier
                    magnitudeVariance = window.magnitudeVariance,
                    sampleCount = 0,
                    windowDurationMs = 0L,
                    zeroCrossingRate = 0.0,
                )
                val result = classifier.evaluate(features, window.stepFrequencyHz, window.timestamp)
                if (result != null) {
                    transitions.add(
                        TransitionEvent(
                            id = transitionId++,
                            timestamp = result.effectiveTimestamp,
                            fromActivity = result.fromState,
                            toActivity = result.toState,
                            isManualOverride = false,
                        ),
                    )
                }
            }

            val sessions = buildActivitySessions(transitions, nowMillis)
            return attachStepCounts(sessions, windows)
        }

        /**
         * Sums [SensorWindow.stepCount] for each session's time range and returns
         * sessions with [ActivitySession.stepCount] populated.
         */
        private fun attachStepCounts(
            sessions: List<ActivitySession>,
            windows: List<SensorWindow>,
        ): List<ActivitySession> {
            if (sessions.isEmpty() || windows.isEmpty()) return sessions
            return sessions.map { session ->
                val endTime = session.endTime ?: Long.MAX_VALUE
                val steps = windows
                    .filter { it.timestamp in session.startTime until endTime }
                    .sumOf { it.stepCount }
                session.copy(stepCount = steps)
            }
        }
    }
}
