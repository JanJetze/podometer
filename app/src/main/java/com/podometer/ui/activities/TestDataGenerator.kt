// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import com.podometer.data.db.CyclingSession
import com.podometer.data.db.SensorWindow
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.DaySummary
import com.podometer.domain.model.StepData
import com.podometer.domain.model.TransitionEvent
import com.podometer.util.DateTimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * Generates realistic fake sensor windows and activity sessions for a given date.
 *
 * Used in debug builds to visualize the step graph without needing real sensor data.
 * Each date produces a unique but deterministic pattern — the same date always
 * generates the same data, while different dates look visibly different.
 */
object TestDataGenerator {

    private const val WINDOW_DURATION_MS = 30_000L

    /**
     * Returns a deterministic [Random] seeded from the given [date].
     *
     * Ensures repeatable output per date while varying across days.
     */
    private fun seededRandom(date: LocalDate): Random =
        Random(date.toEpochDay())

    /**
     * A template for one activity block within a day.
     *
     * @property startHour  Hour-of-day (fractional) when the block starts.
     * @property durationHours Duration in hours.
     * @property activity   The activity type for this block.
     * @property stepsPerWindow Approximate steps per 30-second window (0 for cycling).
     */
    private data class ActivityBlock(
        val startHour: Double,
        val durationHours: Double,
        val activity: ActivityState,
        val stepsPerWindow: Int,
    )

    /**
     * Generates a date-specific schedule of activity blocks.
     *
     * The number and timing of activities vary by day-of-week and a date seed.
     * Possible patterns: commute days (cycling + walks), active days (many walks),
     * rest days (few short walks), long-run days (one extended walk).
     */
    private fun generateSchedule(date: LocalDate, rng: Random): List<ActivityBlock> {
        val dayType = (date.toEpochDay() % 7).toInt().let { if (it < 0) it + 7 else it }

        // Pool of possible activity blocks with jittered start times
        fun jitter(base: Double): Double = base + rng.nextDouble(-0.25, 0.25)
        fun walkDuration(): Double = 0.25 + rng.nextDouble() * 0.75 // 15–60 min
        fun cycleDuration(): Double = 0.5 + rng.nextDouble() * 0.5  // 30–60 min
        fun walkIntensity(): Int = 25 + rng.nextInt(30) // 25–54 steps per window

        return when (dayType) {
            // Commute day: cycling morning + evening, short walks
            0 -> listOf(
                ActivityBlock(jitter(7.5), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(9.0), cycleDuration(), ActivityState.CYCLING, 0),
                ActivityBlock(jitter(12.5), walkDuration() * 0.6, ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(17.5), cycleDuration(), ActivityState.CYCLING, 0),
                ActivityBlock(jitter(19.5), walkDuration() * 0.5, ActivityState.WALKING, walkIntensity()),
            )
            // Active day: many walks throughout the day
            1 -> listOf(
                ActivityBlock(jitter(6.5), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(9.0), walkDuration() * 0.4, ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(11.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(14.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(16.5), walkDuration() * 0.5, ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(19.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
            )
            // Rest day: just a couple short walks
            2 -> listOf(
                ActivityBlock(jitter(10.0), walkDuration() * 0.5, ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(16.0), walkDuration() * 0.5, ActivityState.WALKING, walkIntensity()),
            )
            // Long run day: one extended walk + short evening walk
            3 -> listOf(
                ActivityBlock(jitter(8.0), 1.0 + rng.nextDouble() * 0.5, ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(18.0), walkDuration() * 0.4, ActivityState.WALKING, walkIntensity()),
            )
            // Mixed day: walk + cycle + walk
            4 -> listOf(
                ActivityBlock(jitter(7.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(10.5), cycleDuration(), ActivityState.CYCLING, 0),
                ActivityBlock(jitter(13.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(17.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
            )
            // Afternoon-heavy day: quiet morning, active afternoon
            5 -> listOf(
                ActivityBlock(jitter(12.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(14.5), cycleDuration(), ActivityState.CYCLING, 0),
                ActivityBlock(jitter(16.5), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(19.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
            )
            // Morning-heavy day: early start, quiet evening
            else -> listOf(
                ActivityBlock(jitter(6.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(8.0), cycleDuration(), ActivityState.CYCLING, 0),
                ActivityBlock(jitter(10.0), walkDuration(), ActivityState.WALKING, walkIntensity()),
                ActivityBlock(jitter(12.5), walkDuration() * 0.5, ActivityState.WALKING, walkIntensity()),
            )
        }.map { block ->
            // Clamp start hours to valid range
            block.copy(startHour = block.startHour.coerceIn(6.0, 21.0))
        }.sortedBy { it.startHour }
    }

    /**
     * Generates fake [SensorWindow]s for the given [date].
     *
     * Step counts vary based on the date-specific activity schedule.
     *
     * @param date The date to generate windows for.
     * @return Chronologically ordered list of sensor windows.
     */
    fun generateWindows(date: LocalDate): List<SensorWindow> {
        val rng = seededRandom(date)
        val schedule = generateSchedule(date, seededRandom(date))
        val dayStart = DateTimeUtils.startOfDayMillis(date)
        val windows = mutableListOf<SensorWindow>()
        var id = 1L

        val startMs = dayStart + 7 * 3_600_000L
        val endMs = dayStart + 22 * 3_600_000L

        var ts = startMs
        while (ts < endMs) {
            val hourFraction = (ts - dayStart).toDouble() / 3_600_000.0
            val activeBlock = schedule.firstOrNull { block ->
                hourFraction >= block.startHour &&
                    hourFraction < block.startHour + block.durationHours
            }

            val steps = when {
                activeBlock != null && activeBlock.activity == ActivityState.WALKING -> {
                    val base = activeBlock.stepsPerWindow
                    (base + rng.nextInt(-8, 9)).coerceAtLeast(5)
                }
                activeBlock != null && activeBlock.activity == ActivityState.CYCLING -> {
                    rng.nextInt(0, 3)
                }
                // Random idle steps
                else -> if (rng.nextInt(10) < 2) rng.nextInt(1, 6) else 0
            }

            val variance = if (steps > 0) 2.0 + steps * 0.5 else 0.1
            val frequency = if (steps > 0) 1.5 + steps * 0.1 else 0.0

            windows.add(
                SensorWindow(
                    id = id++,
                    timestamp = ts,
                    magnitudeVariance = variance,
                    stepFrequencyHz = frequency,
                    stepCount = steps,
                ),
            )
            ts += WINDOW_DURATION_MS
        }

        return windows
    }

    /**
     * Generates fake [ActivitySession]s for the given [date].
     *
     * The number, timing, and type of sessions vary per date.
     *
     * @param date The date to generate sessions for.
     * @return Chronologically ordered list of activity sessions.
     */
    fun generateSessions(date: LocalDate): List<ActivitySession> {
        val rng = seededRandom(date)
        val schedule = generateSchedule(date, seededRandom(date))
        val dayStart = DateTimeUtils.startOfDayMillis(date)
        fun h(hour: Double): Long = dayStart + (hour * 3_600_000).toLong()

        return schedule.mapIndexed { index, block ->
            val steps = if (block.activity == ActivityState.WALKING) {
                (block.stepsPerWindow * block.durationHours * 120).toInt()
            } else {
                0
            }
            ActivitySession(
                activity = block.activity,
                startTime = h(block.startHour),
                endTime = h(block.startHour + block.durationHours),
                startTransitionId = index + 1,
                isManualOverride = false,
                stepCount = steps,
            )
        }
    }

    /**
     * Generates fake [StepData] for the dashboard today card.
     *
     * Uses today's date to vary the step count.
     *
     * @param goal The user's daily step goal.
     * @param strideKm The user's stride length in km.
     * @return A [StepData] with date-varying step progress.
     */
    fun generateTodaySteps(goal: Int = 10_000, strideKm: Float = 0.00075f): StepData {
        val rng = seededRandom(LocalDate.now())
        val steps = 2_000 + rng.nextInt(8_000) // 2,000–10,000
        return StepData(
            steps = steps,
            goal = goal,
            progressPercent = steps.toFloat() / goal * 100f,
            distanceKm = steps * strideKm,
        )
    }

    /**
     * Generates fake [TransitionEvent]s for the dashboard transition log.
     *
     * Derived from the same date-specific schedule as [generateSessions].
     *
     * @param date The date to generate transitions for.
     * @return Chronologically ordered list of transition events.
     */
    fun generateTransitions(date: LocalDate): List<TransitionEvent> {
        val schedule = generateSchedule(date, seededRandom(date))
        val dayStart = DateTimeUtils.startOfDayMillis(date)
        fun h(hour: Double): Long = dayStart + (hour * 3_600_000).toLong()

        val transitions = mutableListOf<TransitionEvent>()
        var id = 1
        for (block in schedule) {
            transitions.add(
                TransitionEvent(id++, h(block.startHour), ActivityState.STILL, block.activity, false),
            )
            transitions.add(
                TransitionEvent(id++, h(block.startHour + block.durationHours), block.activity, ActivityState.STILL, false),
            )
        }
        return transitions
    }

    /**
     * Generates fake [DaySummary] entries for the weekly step chart.
     *
     * Each day of the week gets a different step count derived from its date seed.
     */
    fun generateWeeklySummaries(): List<DaySummary> {
        val today = LocalDate.now()
        val startOfWeek = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        return (0..today.dayOfWeek.value - 1).map { i ->
            val day = startOfWeek.plusDays(i.toLong())
            val rng = seededRandom(day)
            val steps = 3_000 + rng.nextInt(9_000) // 3,000–12,000
            val hasCycling = generateSchedule(day, seededRandom(day)).any {
                it.activity == ActivityState.CYCLING
            }
            DaySummary(
                date = day.format(formatter),
                totalSteps = steps,
                totalDistanceKm = steps * 0.00075f,
                walkingMinutes = steps / 100,
                cyclingMinutes = if (hasCycling) 30 + rng.nextInt(60) else 0,
            )
        }
    }

    /**
     * Generates fake [CyclingSession]s for the dashboard cycling section.
     *
     * Only produces sessions for dates whose schedule includes cycling blocks.
     *
     * @param date The date to generate sessions for.
     * @return Cycling sessions derived from the date-specific schedule.
     */
    fun generateCyclingSessions(date: LocalDate): List<CyclingSession> {
        val schedule = generateSchedule(date, seededRandom(date))
        val dayStart = DateTimeUtils.startOfDayMillis(date)
        fun h(hour: Double): Long = dayStart + (hour * 3_600_000).toLong()

        return schedule
            .filter { it.activity == ActivityState.CYCLING }
            .mapIndexed { index, block ->
                CyclingSession(
                    id = index + 1,
                    startTime = h(block.startHour),
                    endTime = h(block.startHour + block.durationHours),
                    durationMinutes = (block.durationHours * 60).toInt(),
                )
            }
    }
}
