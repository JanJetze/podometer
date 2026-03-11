// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.trends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podometer.domain.model.DaySummary
import com.podometer.ui.theme.PodometerTheme

// ─── Screen entry point ───────────────────────────────────────────────────────

/**
 * Trends screen — entry point that wires to [TrendsViewModel].
 *
 * Observes [TrendsUiState] from the ViewModel and delegates all rendering to
 * [TrendsScreenContent], keeping this function free of direct data references
 * so that [TrendsScreenContent] can be previewed in isolation.
 */
@Composable
fun TrendsScreen(
    modifier: Modifier = Modifier,
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TrendsScreenContent(
        uiState = uiState,
        onSetPeriod = viewModel::setPeriod,
        onPreviousPeriod = viewModel::previousPeriod,
        onNextPeriod = viewModel::nextPeriod,
        modifier = modifier,
    )
}

// ─── Pure presentational composable ──────────────────────────────────────────

/**
 * Pure presentational Trends screen content.
 *
 * Renders:
 * 1. A period toggle (Weekly / Monthly) segmented button.
 * 2. A [WeeklyTrendsChart] or [MonthlyTrendsChart] based on the selected period,
 *    with previous/next navigation callbacks.
 * 3. [StatsCards] showing aggregated statistics for the current period.
 *
 * This composable holds no ViewModel references — all state and events flow
 * through its parameters.
 *
 * @param uiState          Current [TrendsUiState] to render.
 * @param onSetPeriod      Called when the user toggles between Weekly and Monthly.
 * @param onPreviousPeriod Called when the user taps the previous-period arrow.
 * @param onNextPeriod     Called when the user taps the next-period arrow.
 * @param modifier         Optional [Modifier] applied to the root [Column].
 */
@Composable
fun TrendsScreenContent(
    uiState: TrendsUiState,
    onSetPeriod: (TrendsPeriod) -> Unit,
    onPreviousPeriod: () -> Unit,
    onNextPeriod: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ─── Period toggle ────────────────────────────────────────────────────
        TrendsPeriodToggle(
            selectedPeriod = uiState.period,
            onSelectPeriod = onSetPeriod,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )

        // ─── Chart ────────────────────────────────────────────────────────────
        when (uiState.period) {
            TrendsPeriod.WEEKLY -> WeeklyTrendsChart(
                days = uiState.days,
                targetGoal = uiState.targetGoal,
                minimumGoal = uiState.minimumGoal,
                weekLabel = uiState.periodLabel,
                onPreviousWeek = onPreviousPeriod,
                onNextWeek = onNextPeriod,
                canGoNext = uiState.canGoNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
            TrendsPeriod.MONTHLY -> MonthlyTrendsChart(
                days = uiState.days,
                targetGoal = uiState.targetGoal,
                minimumGoal = uiState.minimumGoal,
                monthLabel = uiState.periodLabel,
                onPreviousMonth = onPreviousPeriod,
                onNextMonth = onNextPeriod,
                canGoNext = uiState.canGoNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }

        // ─── Stats cards ──────────────────────────────────────────────────────
        Text(
            text = "Statistics",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        StatsCards(
            stats = uiState.stats,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─── Period toggle ────────────────────────────────────────────────────────────

/**
 * Segmented button toggle for selecting between [TrendsPeriod.WEEKLY] and [TrendsPeriod.MONTHLY].
 *
 * @param selectedPeriod The currently active [TrendsPeriod].
 * @param onSelectPeriod Callback invoked with the newly selected period.
 * @param modifier       Optional [Modifier] applied to the [MultiChoiceSegmentedButtonRow].
 */
@Composable
fun TrendsPeriodToggle(
    selectedPeriod: TrendsPeriod,
    onSelectPeriod: (TrendsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(TrendsPeriod.WEEKLY, TrendsPeriod.MONTHLY)
    MultiChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, period ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                ),
                onCheckedChange = { onSelectPeriod(period) },
                checked = selectedPeriod == period,
                label = {
                    Text(
                        text = when (period) {
                            TrendsPeriod.WEEKLY -> "Weekly"
                            TrendsPeriod.MONTHLY -> "Monthly"
                        },
                    )
                },
            )
        }
    }
}

// ─── Preview functions ────────────────────────────────────────────────────────

private fun sampleWeeklyDays(): List<DaySummary> = listOf(
    DaySummary("2026-03-02", 7_200, 5.4f),
    DaySummary("2026-03-03", 11_500, 8.6f),
    DaySummary("2026-03-04", 4_800, 3.6f),
    DaySummary("2026-03-05", 13_000, 9.7f),
    DaySummary("2026-03-06", 8_400, 6.3f),
    DaySummary("2026-03-07", 10_200, 7.6f),
    DaySummary("2026-03-08", 6_500, 4.9f),
)

private fun sampleMonthlyDays(): List<DaySummary> {
    val steps = listOf(
        8_000, 11_500, 4_800, 13_000, 8_400, 10_200, 6_500,
        9_100, 7_300, 12_400, 5_200, 10_800, 8_700, 11_200,
        6_000, 9_500, 13_800, 7_600, 10_100, 8_300, 11_700,
        6_800, 9_200, 12_600, 7_100, 10_500, 8_900, 11_300,
        6_300, 9_700, 13_100,
    )
    return steps.mapIndexed { i, s ->
        val day = i + 1
        DaySummary(
            date = "2026-03-${day.toString().padStart(2, '0')}",
            totalSteps = s,
            totalDistanceKm = s / 1_333f,
        )
    }
}

/** Preview: Trends screen — weekly view. */
@Preview(showBackground = true, name = "TrendsScreen — Weekly")
@Composable
private fun PreviewTrendsScreenWeekly() {
    PodometerTheme(dynamicColor = false) {
        TrendsScreenContent(
            uiState = TrendsUiState(
                period = TrendsPeriod.WEEKLY,
                days = sampleWeeklyDays(),
                stats = computeTrendsStats(sampleWeeklyDays(), targetGoal = 10_000),
                targetGoal = 10_000,
                minimumGoal = 6_000,
                periodLabel = "Mar 2 – Mar 8",
                canGoNext = false,
            ),
            onSetPeriod = {},
            onPreviousPeriod = {},
            onNextPeriod = {},
        )
    }
}

/** Preview: Trends screen — monthly view. */
@Preview(showBackground = true, name = "TrendsScreen — Monthly")
@Composable
private fun PreviewTrendsScreenMonthly() {
    PodometerTheme(dynamicColor = false) {
        TrendsScreenContent(
            uiState = TrendsUiState(
                period = TrendsPeriod.MONTHLY,
                days = sampleMonthlyDays(),
                stats = computeTrendsStats(sampleMonthlyDays(), targetGoal = 10_000),
                targetGoal = 10_000,
                minimumGoal = 6_000,
                periodLabel = "March 2026",
                canGoNext = false,
            ),
            onSetPeriod = {},
            onPreviousPeriod = {},
            onNextPeriod = {},
        )
    }
}

/** Preview: Trends screen — empty state. */
@Preview(showBackground = true, name = "TrendsScreen — Empty")
@Composable
private fun PreviewTrendsScreenEmpty() {
    PodometerTheme(dynamicColor = false) {
        TrendsScreenContent(
            uiState = TrendsUiState(
                period = TrendsPeriod.WEEKLY,
                days = emptyList(),
                stats = TrendsStats(0, 0, "", 0.0, 0f),
                targetGoal = 10_000,
                minimumGoal = 6_000,
                periodLabel = "Mar 2 – Mar 8",
                canGoNext = false,
            ),
            onSetPeriod = {},
            onPreviousPeriod = {},
            onNextPeriod = {},
        )
    }
}

/** Preview: Trends screen — previous period (canGoNext enabled). */
@Preview(showBackground = true, name = "TrendsScreen — Previous period")
@Composable
private fun PreviewTrendsScreenPreviousPeriod() {
    PodometerTheme(dynamicColor = false) {
        TrendsScreenContent(
            uiState = TrendsUiState(
                period = TrendsPeriod.WEEKLY,
                days = sampleWeeklyDays(),
                stats = computeTrendsStats(sampleWeeklyDays(), targetGoal = 10_000),
                targetGoal = 10_000,
                minimumGoal = 6_000,
                periodLabel = "Feb 23 – Mar 1",
                canGoNext = true,
            ),
            onSetPeriod = {},
            onPreviousPeriod = {},
            onNextPeriod = {},
        )
    }
}

/** Preview: TrendsPeriodToggle — weekly selected. */
@Preview(showBackground = true, name = "TrendsPeriodToggle — Weekly")
@Composable
private fun PreviewTrendsPeriodToggleWeekly() {
    PodometerTheme(dynamicColor = false) {
        TrendsPeriodToggle(
            selectedPeriod = TrendsPeriod.WEEKLY,
            onSelectPeriod = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

/** Preview: TrendsPeriodToggle — monthly selected. */
@Preview(showBackground = true, name = "TrendsPeriodToggle — Monthly")
@Composable
private fun PreviewTrendsPeriodToggleMonthly() {
    PodometerTheme(dynamicColor = false) {
        TrendsPeriodToggle(
            selectedPeriod = TrendsPeriod.MONTHLY,
            onSelectPeriod = {},
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}
