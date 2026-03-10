// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.podometer.data.db.DailySummary
import com.podometer.data.db.StepDao
import com.podometer.data.repository.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Unit tests for [GetStreakUseCaseImpl].
 *
 * Uses fake DAOs and a fake DataStore to test streak calculation logic
 * including rest days, today's progress, and edge cases.
 */
class GetStreakUseCaseTest {

    // ─── Fake DataStore ──────────────────────────────────────────────────────

    private class FakeDataStore(
        private val initial: Preferences = preferencesOf(),
    ) : DataStore<Preferences> {
        private var current: Preferences = initial
        override val data: Flow<Preferences> get() = flowOf(current)
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(current)
            current = updated
            return updated
        }
    }

    // ─── Fake StepDao ────────────────────────────────────────────────────────

    private class FakeStepDao(
        private val summaries: List<DailySummary> = emptyList(),
    ) : StepDao {
        override fun getDailySummary(date: String): Flow<DailySummary?> =
            flowOf(summaries.find { it.date == date })

        override fun getWeeklyDailySummaries(
            startDate: String,
            endDate: String,
        ): Flow<List<DailySummary>> = flowOf(emptyList())

        override suspend fun getTodayTotalStepsSnapshot(date: String): Int? = null

        override suspend fun upsertDailySummary(summary: DailySummary) = Unit
        override suspend fun upsertStepsAndDistance(
            date: String, totalSteps: Int, totalDistance: Float,
        ) = Unit

        override suspend fun getAllDailySummaries(): List<DailySummary> = summaries

        override suspend fun insertAllDailySummaries(summaries: List<DailySummary>) = Unit

        override suspend fun getDailySummariesUpTo(
            endDate: String,
            limit: Int,
        ): List<DailySummary> =
            summaries
                .filter { it.date <= endDate }
                .sortedByDescending { it.date }
                .take(limit)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun preferencesManager(): PreferencesManager =
        PreferencesManager(FakeDataStore())

    /** Formats a [LocalDate] as "yyyy-MM-dd". */
    private fun LocalDate.fmt(): String = toString()

    // ─── Tests: no data ──────────────────────────────────────────────────────

    @Test
    fun `streak is zero when there is no data`() = runTest {
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(emptyList()),
            preferencesManager = preferencesManager(),
            today = LocalDate.of(2026, 3, 10),
        )

        val result = useCase()

        assertEquals(0, result.currentStreak)
        assertEquals(TodayProgress.NOT_MET, result.todayProgress)
    }

    // ─── Tests: today progress ───────────────────────────────────────────────

    @Test
    fun `todayProgress is MET when today meets minimum goal`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val pm = preferencesManager().also { it.setMinimumStepGoal(5_000) }
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(
                listOf(DailySummary(today.fmt(), totalSteps = 5_000, totalDistance = 0f)),
            ),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        assertEquals(TodayProgress.MET, result.todayProgress)
        assertEquals(1, result.currentStreak)
    }

    @Test
    fun `todayProgress is NOT_MET when today is below minimum goal`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val pm = preferencesManager().also { it.setMinimumStepGoal(5_000) }
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(
                listOf(DailySummary(today.fmt(), totalSteps = 4_999, totalDistance = 0f)),
            ),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        assertEquals(TodayProgress.NOT_MET, result.todayProgress)
    }

    // ─── Tests: streak counts backwards ──────────────────────────────────────

    @Test
    fun `streak is 3 for three consecutive days all meeting minimum goal`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val pm = preferencesManager().also { it.setMinimumStepGoal(5_000) }
        val summaries = listOf(
            DailySummary(today.fmt(), 6_000, 0f),
            DailySummary(today.minusDays(1).fmt(), 7_000, 0f),
            DailySummary(today.minusDays(2).fmt(), 5_000, 0f),
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        assertEquals(3, result.currentStreak)
        assertEquals(TodayProgress.MET, result.todayProgress)
    }

    @Test
    fun `streak breaks when a day is below minimum goal`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val pm = preferencesManager().also { it.setMinimumStepGoal(5_000) }
        val summaries = listOf(
            DailySummary(today.fmt(), 6_000, 0f),
            DailySummary(today.minusDays(1).fmt(), 4_000, 0f), // breaks streak
            DailySummary(today.minusDays(2).fmt(), 8_000, 0f),
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        assertEquals(1, result.currentStreak)
    }

    @Test
    fun `streak breaks when a day has no data and is not a rest day`() = runTest {
        val today = LocalDate.of(2026, 3, 10) // Tuesday
        val pm = preferencesManager().also { it.setMinimumStepGoal(5_000) }
        // today and 2 days ago have data, but yesterday (Monday) has no data
        val summaries = listOf(
            DailySummary(today.fmt(), 6_000, 0f),
            DailySummary(today.minusDays(2).fmt(), 8_000, 0f),
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        assertEquals(1, result.currentStreak)
    }

    // ─── Tests: today not yet met — check yesterday ───────────────────────────

    @Test
    fun `streak counts from yesterday when today not yet met`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val pm = preferencesManager().also { it.setMinimumStepGoal(5_000) }
        val summaries = listOf(
            DailySummary(today.fmt(), 1_000, 0f), // today not yet met
            DailySummary(today.minusDays(1).fmt(), 6_000, 0f),
            DailySummary(today.minusDays(2).fmt(), 5_500, 0f),
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        // Today not met, so streak is yesterday's streak = 2
        assertEquals(2, result.currentStreak)
        assertEquals(TodayProgress.NOT_MET, result.todayProgress)
    }

    @Test
    fun `streak is zero when today not yet met and yesterday not met either`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val pm = preferencesManager().also { it.setMinimumStepGoal(5_000) }
        val summaries = listOf(
            DailySummary(today.fmt(), 1_000, 0f),
            DailySummary(today.minusDays(1).fmt(), 2_000, 0f),
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        assertEquals(0, result.currentStreak)
        assertEquals(TodayProgress.NOT_MET, result.todayProgress)
    }

    // ─── Tests: rest days ─────────────────────────────────────────────────────

    @Test
    fun `rest day is skipped and does not break the streak`() = runTest {
        // today = Wednesday 2026-03-11, yesterday = Tuesday 2026-03-10
        // Sunday (2026-03-08) is a rest day, 2 days before yesterday
        val today = LocalDate.of(2026, 3, 11) // Wednesday
        val pm = preferencesManager().also {
            it.setMinimumStepGoal(5_000)
            it.setRestDays(setOf(DayOfWeek.SUNDAY))
        }
        val summaries = listOf(
            DailySummary(today.fmt(), 6_000, 0f),           // Wed
            DailySummary(today.minusDays(1).fmt(), 7_000, 0f), // Tue
            DailySummary(today.minusDays(2).fmt(), 5_000, 0f), // Mon
            // Sunday (today.minusDays(3)) is a rest day — no entry needed
            DailySummary(today.minusDays(4).fmt(), 8_000, 0f), // Sat
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        // Wed(1) + Tue(2) + Mon(3) + Sun(rest,skip) + Sat(4) = 4
        assertEquals(4, result.currentStreak)
    }

    @Test
    fun `multiple consecutive rest days are all skipped`() = runTest {
        // today = Wednesday 2026-03-11
        // Saturday and Sunday are rest days
        val today = LocalDate.of(2026, 3, 11) // Wednesday
        val pm = preferencesManager().also {
            it.setMinimumStepGoal(5_000)
            it.setRestDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        }
        val summaries = listOf(
            DailySummary(today.fmt(), 6_000, 0f),           // Wed
            DailySummary(today.minusDays(1).fmt(), 7_000, 0f), // Tue
            DailySummary(today.minusDays(2).fmt(), 5_000, 0f), // Mon
            // Sun 2026-03-08 is rest day — skipped
            // Sat 2026-03-07 is rest day — skipped
            DailySummary(today.minusDays(5).fmt(), 9_000, 0f), // Fri
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        // Wed(1) + Tue(2) + Mon(3) + Sun(skip) + Sat(skip) + Fri(4) = 4
        assertEquals(4, result.currentStreak)
    }

    @Test
    fun `rest day at beginning of streak does not count toward streak`() = runTest {
        // today is Sunday which is a rest day — streak starts from yesterday
        val today = LocalDate.of(2026, 3, 8) // Sunday
        val pm = preferencesManager().also {
            it.setMinimumStepGoal(5_000)
            it.setRestDays(setOf(DayOfWeek.SUNDAY))
        }
        val summaries = listOf(
            DailySummary(today.minusDays(1).fmt(), 6_000, 0f), // Sat
            DailySummary(today.minusDays(2).fmt(), 7_000, 0f), // Fri
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        // Today (Sunday) is rest day — skipped. Sat(1) + Fri(2) = 2
        assertEquals(2, result.currentStreak)
    }

    // ─── Tests: streak with no rest days set ─────────────────────────────────

    @Test
    fun `long streak of 10 consecutive days all at minimum goal`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val pm = preferencesManager().also { it.setMinimumStepGoal(5_000) }
        val summaries = (0L..9L).map { offset ->
            DailySummary(today.minusDays(offset).fmt(), 5_000, 0f)
        }
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        assertEquals(10, result.currentStreak)
        assertEquals(TodayProgress.MET, result.todayProgress)
    }

    @Test
    fun `streak uses minimum goal not target goal`() = runTest {
        val today = LocalDate.of(2026, 3, 10)
        val pm = preferencesManager().also {
            it.setMinimumStepGoal(3_000)
            it.setTargetStepGoal(8_000)
        }
        // Steps are above minimum (3000) but below target (8000)
        val summaries = listOf(
            DailySummary(today.fmt(), 4_000, 0f),
            DailySummary(today.minusDays(1).fmt(), 3_500, 0f),
        )
        val useCase = GetStreakUseCaseImpl(
            stepDao = FakeStepDao(summaries),
            preferencesManager = pm,
            today = today,
        )

        val result = useCase()

        // Both days meet the minimum goal
        assertEquals(2, result.currentStreak)
        assertEquals(TodayProgress.MET, result.todayProgress)
    }
}
