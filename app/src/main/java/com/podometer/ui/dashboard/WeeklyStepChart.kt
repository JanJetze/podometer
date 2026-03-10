// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.domain.model.DaySummary
import com.podometer.ui.theme.PodometerTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ─── Chart colour constants ────────────────────────────────────────────────────

/** Colour for bars that have not reached the daily goal. */
private val BarBelowGoalColor = Color(0xFF78909C)

/** Colour for placeholder bars (days with no data). */
private val BarPlaceholderColor = Color(0xFFCFD8DC)

/** Colour for the dashed goal line. */
private val GoalLineColor = Color(0xFF546E7A)

// ─── Data model ───────────────────────────────────────────────────────────────

/**
 * Represents a single bar in the 7-day step chart.
 *
 * @property date            Calendar date in "yyyy-MM-dd" format, or empty string for placeholders.
 * @property dayLabel        Single-character day label: M, T, W, T, F, S, or S.
 * @property steps           Total steps for this day; 0 for placeholders.
 * @property heightFraction  Normalised bar height in [0f, 1f] relative to the chart Y-axis ceiling.
 * @property aboveGoal       True when [steps] strictly exceeds the daily goal.
 * @property isToday         True when this bar represents the current calendar day.
 * @property isPlaceholder   True when no [DaySummary] data exists for this day.
 * @property distanceKm      Total distance in kilometres for this day; 0f for placeholders.
 */
data class ChartBar(
    val date: String,
    val dayLabel: String,
    val steps: Int,
    val heightFraction: Float,
    val aboveGoal: Boolean,
    val isToday: Boolean,
    val isPlaceholder: Boolean,
    val distanceKm: Float = 0f,
)

// ─── Day-label mapping ────────────────────────────────────────────────────────

private val DAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "S",
)

private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

/** Short date formatter for the bar detail card, e.g. "Mon, Mar 2". */
private val CHART_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, MMM d")

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Computes the Y-axis goal fraction for the dashed goal line.
 *
 * The scale ceiling is `max(goal, maxSteps)` so that the chart never clips bars above goal.
 * If both values are zero, returns 1f as a safe default.
 *
 * @param goal      The user's daily step goal.
 * @param maxSteps  The highest step count across all bars with data.
 * @return Fractional position of the goal line in [0f, 1f].
 */
fun computeGoalFraction(goal: Int, maxSteps: Int): Float {
    val ceiling = maxOf(goal, maxSteps)
    if (ceiling == 0) return 1f
    return (goal.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
}

/**
 * Builds 7 [ChartBar]s for the rolling 7-day window ending on [todayDate] (inclusive).
 *
 * The window spans the 6 days prior to [todayDate] plus today itself, ordered oldest-first.
 * Days without a matching [DaySummary] entry are represented as placeholder bars with
 * zero steps and `isPlaceholder = true`.
 *
 * The Y-axis ceiling is `max(goal, maxSteps)` so bars above the goal are always
 * fully visible within the chart area.
 *
 * @param daySummaries List of per-day summaries (may be empty or contain fewer than 7 entries).
 * @param goal         User's daily step goal.
 * @param todayDate    Today's date in "yyyy-MM-dd" format; the window ends on this date.
 * @return A list of exactly 7 [ChartBar]s ordered oldest-day-first to today-last.
 */
fun buildChartBars(
    daySummaries: List<DaySummary>,
    goal: Int,
    todayDate: String,
): List<ChartBar> {
    val today = LocalDate.parse(todayDate, DATE_FORMATTER)
    // Rolling 7-day window: 6 days ago through today
    val windowDates = (6L downTo 0L).map { today.minusDays(it) }

    // Index summaries by date string for O(1) lookup
    val summaryByDate: Map<String, DaySummary> = daySummaries.associateBy { it.date }

    // Determine scale ceiling: max(goal, maxSteps in the window)
    val maxSteps = windowDates.mapNotNull { date ->
        summaryByDate[date.format(DATE_FORMATTER)]?.totalSteps
    }.maxOrNull() ?: 0
    val ceiling = maxOf(goal, maxSteps)

    return windowDates.map { date ->
        val dateStr = date.format(DATE_FORMATTER)
        val summary = summaryByDate[dateStr]
        val dayLabel = DAY_LABELS[date.dayOfWeek] ?: "?"
        val isToday = dateStr == todayDate

        if (summary == null) {
            ChartBar(
                date = dateStr,
                dayLabel = dayLabel,
                steps = 0,
                heightFraction = 0f,
                aboveGoal = false,
                isToday = isToday,
                isPlaceholder = true,
            )
        } else {
            val heightFraction = if (ceiling > 0) {
                (summary.totalSteps.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            ChartBar(
                date = dateStr,
                dayLabel = dayLabel,
                steps = summary.totalSteps,
                heightFraction = heightFraction,
                aboveGoal = summary.totalSteps > goal,
                isToday = isToday,
                isPlaceholder = false,
                distanceKm = summary.totalDistanceKm,
            )
        }
    }
}

/**
 * Builds an accessible plain-English description of the weekly step chart.
 *
 * This is a pure-Kotlin function kept separate from the Composable so it can be
 * unit-tested on the JVM without Android resources or Compose infrastructure.
 * The Composable uses `stringResource()` for proper localisation at runtime.
 *
 * @param bars The 7 [ChartBar]s to describe.
 * @return A human-readable English string suitable for use as a `contentDescription` in tests.
 */
fun weeklyChartContentDescription(bars: List<ChartBar>): String {
    val parts = bars.map { bar ->
        when {
            bar.isPlaceholder -> "${bar.dayLabel}: no data"
            bar.isToday -> "${bar.dayLabel} (today): ${bar.steps} steps"
            else -> "${bar.dayLabel}: ${bar.steps} steps"
        }
    }
    return "Weekly step chart: ${parts.joinToString(", ")}."
}

/**
 * Formats a chart date string ("yyyy-MM-dd") into a short human-readable form.
 *
 * Example: "2026-03-02" becomes "Mon, Mar 2".
 *
 * @param dateStr Date in ISO local-date format.
 * @return Human-readable short date string.
 */
fun formatChartDate(dateStr: String): String {
    val date = LocalDate.parse(dateStr, DATE_FORMATTER)
    return date.format(CHART_DATE_FORMATTER)
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * 7-day vertical bar chart displaying daily step counts with tap-to-reveal details.
 *
 * Features:
 * - 7 vertical bars, one per day (rolling 7-day window ending today)
 * - Tap any bar to reveal a detail card showing steps, distance, and activity time
 * - Today's bar has a highlighted outline in [MaterialTheme.colorScheme.primary]
 * - Y-axis auto-scaled to `max(goal, maxSteps)` so bars above goal are never clipped
 * - Day labels (M T W T F S S) displayed below the bars
 * - Dashed horizontal goal line at the computed [computeGoalFraction] height
 * - Bars above goal use [MaterialTheme.colorScheme.tertiary]; others use a neutral colour
 * - Days with no [DaySummary] data show gray placeholder bars
 *
 * Accessibility: the component carries a `contentDescription` describing each day's step
 * count so TalkBack can read the chart without visuals.
 *
 * @param daySummaries List of per-day step summaries for the current week (0–7 entries).
 * @param goal         User's daily step goal; determines the goal line and accent colouring.
 * @param todayDate    Today's date in "yyyy-MM-dd" format.
 * @param modifier     Optional [Modifier] applied to the root [Column].
 */
@Composable
fun WeeklyStepChart(
    daySummaries: List<DaySummary>,
    goal: Int,
    todayDate: String,
    modifier: Modifier = Modifier,
) {
    val bars = buildChartBars(daySummaries, goal, todayDate)
    var selectedBarIndex by remember { mutableIntStateOf(-1) }

    // Build localised content description for TalkBack
    val accessibilityText = run {
        val parts = bars.map { bar ->
            when {
                bar.isPlaceholder -> stringResource(R.string.cd_weekly_chart_day_no_data, bar.dayLabel)
                bar.isToday -> stringResource(R.string.cd_weekly_chart_day_today, bar.dayLabel, bar.steps)
                else -> stringResource(R.string.cd_weekly_chart_day, bar.dayLabel, bar.steps)
            }
        }
        stringResource(R.string.cd_weekly_chart_summary, parts.joinToString(", "))
    }

    val maxSteps = bars.filter { !it.isPlaceholder }.maxOfOrNull { it.steps } ?: 0
    val goalFraction = computeGoalFraction(goal, maxSteps)

    // Theme colours resolved here, passed down to Canvas (no MaterialTheme inside Canvas)
    val accentColor = MaterialTheme.colorScheme.tertiary
    val todayOutlineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier.semantics { contentDescription = accessibilityText },
    ) {
        WeeklyStepChartCanvas(
            bars = bars,
            goalFraction = goalFraction,
            accentColor = accentColor,
            todayOutlineColor = todayOutlineColor,
            onBarTap = { index ->
                selectedBarIndex = if (selectedBarIndex == index) -1 else index
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
        WeeklyStepChartLabels(
            bars = bars,
            todayOutlineColor = todayOutlineColor,
            labelColor = labelColor,
            selectedBarIndex = selectedBarIndex,
        )
        AnimatedVisibility(
            visible = selectedBarIndex in bars.indices,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (selectedBarIndex in bars.indices) {
                BarDetailCard(bar = bars[selectedBarIndex])
            }
        }
    }
}

/**
 * Canvas that draws the 7 vertical bars, the dashed goal line, and today's highlight outline.
 *
 * Supports tap gestures to select individual bars. Each bar occupies an equal-width
 * tap zone across the full chart width for forgiving touch targets.
 *
 * @param bars               The 7 [ChartBar]s to render.
 * @param goalFraction       Normalised height of the goal line in [0f, 1f].
 * @param accentColor        Colour used for bars that exceed the goal.
 * @param todayOutlineColor  Colour used for the highlight outline on today's bar.
 * @param onBarTap           Callback invoked with the tapped bar index (0–6).
 * @param modifier           Applied to the [Canvas].
 */
@Composable
private fun WeeklyStepChartCanvas(
    bars: List<ChartBar>,
    goalFraction: Float,
    accentColor: Color,
    todayOutlineColor: Color,
    onBarTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cornerRadiusDp = 3.dp
    val barGapDp = 6.dp
    val outlineStrokeWidthDp = 2.dp
    val goalDashOnDp = 8.dp
    val goalDashOffDp = 4.dp
    val minBarHeightDp = 3.dp // Minimum visible height for non-zero bars

    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
    val barGapPx = with(density) { barGapDp.toPx() }
    val outlineStrokePx = with(density) { outlineStrokeWidthDp.toPx() }
    val goalDashOnPx = with(density) { goalDashOnDp.toPx() }
    val goalDashOffPx = with(density) { goalDashOffDp.toPx() }
    val minBarHeightPx = with(density) { minBarHeightDp.toPx() }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                val slotWidth = size.width.toFloat() / bars.size
                val index = (offset.x / slotWidth).toInt().coerceIn(0, bars.size - 1)
                onBarTap(index)
            }
        },
    ) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barCount = bars.size
        val totalGapWidth = barGapPx * (barCount - 1)
        val barWidth = (totalWidth - totalGapWidth) / barCount

        bars.forEachIndexed { index, bar ->
            val barLeft = index * (barWidth + barGapPx)

            if (bar.isPlaceholder) {
                // Draw a short placeholder stub at the bottom
                val stubHeight = minBarHeightPx * 2f
                drawRoundedBar(
                    left = barLeft,
                    barWidth = barWidth,
                    totalHeight = totalHeight,
                    barHeight = stubHeight,
                    color = BarPlaceholderColor,
                    cornerRadius = cornerRadiusPx,
                )
            } else {
                // Draw the actual bar
                val rawBarHeight = bar.heightFraction * totalHeight
                val barHeight = if (bar.steps > 0 && rawBarHeight < minBarHeightPx) {
                    minBarHeightPx
                } else {
                    rawBarHeight
                }
                val barColor = when {
                    bar.aboveGoal -> accentColor
                    else -> BarBelowGoalColor
                }
                drawRoundedBar(
                    left = barLeft,
                    barWidth = barWidth,
                    totalHeight = totalHeight,
                    barHeight = barHeight,
                    color = barColor,
                    cornerRadius = cornerRadiusPx,
                )

                // Draw today highlight outline
                if (bar.isToday) {
                    val outlineTop = totalHeight - barHeight - outlineStrokePx / 2f
                    val outlineHeight = barHeight + outlineStrokePx
                    drawRoundRect(
                        color = todayOutlineColor,
                        topLeft = Offset(barLeft - outlineStrokePx / 2f, outlineTop),
                        size = Size(barWidth + outlineStrokePx, outlineHeight),
                        cornerRadius = CornerRadius(cornerRadiusPx + outlineStrokePx / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = outlineStrokePx,
                        ),
                    )
                }
            }
        }

        // Draw the dashed goal line
        val goalY = totalHeight * (1f - goalFraction)
        val dashPathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(goalDashOnPx, goalDashOffPx),
            phase = 0f,
        )
        drawLine(
            color = GoalLineColor,
            start = Offset(0f, goalY),
            end = Offset(totalWidth, goalY),
            strokeWidth = with(density) { 1.5.dp.toPx() },
            pathEffect = dashPathEffect,
        )
    }
}

/**
 * Draws a filled rounded-top bar growing upward from the bottom of the canvas.
 */
private fun DrawScope.drawRoundedBar(
    left: Float,
    barWidth: Float,
    totalHeight: Float,
    barHeight: Float,
    color: Color,
    cornerRadius: Float,
) {
    val top = totalHeight - barHeight
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(cornerRadius),
    )
}

/**
 * Row of day labels displayed below the chart canvas.
 *
 * Today's label is rendered in [todayOutlineColor] to match the highlighted bar.
 * The selected bar's label (if any) is rendered in bold with [todayOutlineColor].
 *
 * @param bars              The 7 [ChartBar]s whose [ChartBar.dayLabel]s are displayed.
 * @param todayOutlineColor Colour for the label of today's day and the selected day.
 * @param labelColor        Default colour for all other labels.
 * @param selectedBarIndex  Index of the currently selected bar, or -1 if none.
 */
@Composable
private fun WeeklyStepChartLabels(
    bars: List<ChartBar>,
    todayOutlineColor: Color,
    labelColor: Color,
    selectedBarIndex: Int = -1,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        bars.forEachIndexed { index, bar ->
            val isSelected = index == selectedBarIndex
            Text(
                text = bar.dayLabel,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontWeight = if (isSelected) FontWeight.Bold else null,
                color = if (bar.isToday || isSelected) todayOutlineColor else labelColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
            )
        }
    }
}

/**
 * Detail card showing step count, distance, and activity time for a selected bar.
 *
 * Appears below the chart labels when the user taps a bar. For placeholder bars
 * (days with no data), shows a "No data" message.
 *
 * @param bar The selected [ChartBar] to display details for.
 */
@Composable
private fun BarDetailCard(bar: ChartBar) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = formatChartDate(bar.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (bar.isPlaceholder) {
                Text(
                    text = stringResource(R.string.chart_tooltip_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.chart_tooltip_steps_distance,
                        formatStepCount(bar.steps),
                        bar.distanceKm,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ─── Preview functions ─────────────────────────────────────────────────────────

/** Preview: full 7-day data with mixed steps above and below goal. */
@Preview(showBackground = true, name = "WeeklyStepChart — Full 7-day data")
@Composable
private fun PreviewWeeklyStepChartFull() {
    PodometerTheme {
        WeeklyStepChart(
            daySummaries = listOf(
                DaySummary("2026-02-16", 7_200, 5.4f),
                DaySummary("2026-02-17", 11_500, 8.6f),
                DaySummary("2026-02-18", 9_800, 7.3f),
                DaySummary("2026-02-19", 13_000, 9.7f),
                DaySummary("2026-02-20", 8_400, 6.3f),
                DaySummary("2026-02-21", 10_200, 7.6f),
                DaySummary("2026-02-22", 6_500, 4.9f),
            ),
            goal = 10_000,
            todayDate = "2026-02-22",
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: partial data — only 3 days filled, 4 placeholder bars. */
@Preview(showBackground = true, name = "WeeklyStepChart — Partial data (3 days)")
@Composable
private fun PreviewWeeklyStepChartPartial() {
    PodometerTheme {
        WeeklyStepChart(
            daySummaries = listOf(
                DaySummary("2026-02-21", 7_000, 5.2f),
                DaySummary("2026-02-22", 9_500, 7.1f),
                DaySummary("2026-02-23", 4_200, 3.1f),
            ),
            goal = 10_000,
            todayDate = "2026-02-23",
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: all 7 days above goal — all bars in accent colour. */
@Preview(showBackground = true, name = "WeeklyStepChart — All above goal")
@Composable
private fun PreviewWeeklyStepChartAllAboveGoal() {
    PodometerTheme {
        WeeklyStepChart(
            daySummaries = listOf(
                DaySummary("2026-02-16", 11_000, 8.2f),
                DaySummary("2026-02-17", 12_500, 9.4f),
                DaySummary("2026-02-18", 10_500, 7.9f),
                DaySummary("2026-02-19", 14_000, 10.5f),
                DaySummary("2026-02-20", 11_800, 8.8f),
                DaySummary("2026-02-21", 13_200, 9.9f),
                DaySummary("2026-02-22", 10_100, 7.6f),
            ),
            goal = 10_000,
            todayDate = "2026-02-22",
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: all 7 days below goal — all bars in neutral colour. */
@Preview(showBackground = true, name = "WeeklyStepChart — All below goal")
@Composable
private fun PreviewWeeklyStepChartAllBelowGoal() {
    PodometerTheme {
        WeeklyStepChart(
            daySummaries = listOf(
                DaySummary("2026-02-16", 3_000, 2.2f),
                DaySummary("2026-02-17", 4_500, 3.4f),
                DaySummary("2026-02-18", 2_000, 1.5f),
                DaySummary("2026-02-19", 6_000, 4.5f),
                DaySummary("2026-02-20", 5_000, 3.8f),
                DaySummary("2026-02-21", 7_000, 5.2f),
                DaySummary("2026-02-22", 1_500, 1.1f),
            ),
            goal = 10_000,
            todayDate = "2026-02-22",
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: empty — all 7 bars are gray placeholders. */
@Preview(showBackground = true, name = "WeeklyStepChart — Empty (no data)")
@Composable
private fun PreviewWeeklyStepChartEmpty() {
    PodometerTheme {
        WeeklyStepChart(
            daySummaries = emptyList(),
            goal = 10_000,
            todayDate = "2026-02-23",
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: bar detail card with activity data. */
@Preview(showBackground = true, name = "BarDetailCard — With data")
@Composable
private fun PreviewBarDetailCard() {
    PodometerTheme {
        BarDetailCard(
            bar = ChartBar(
                date = "2026-02-19",
                dayLabel = "W",
                steps = 13_000,
                heightFraction = 1f,
                aboveGoal = true,
                isToday = false,
                isPlaceholder = false,
                distanceKm = 9.7f,
            ),
        )
    }
}

/** Preview: bar detail card for a placeholder day. */
@Preview(showBackground = true, name = "BarDetailCard — No data")
@Composable
private fun PreviewBarDetailCardNoData() {
    PodometerTheme {
        BarDetailCard(
            bar = ChartBar(
                date = "2026-02-17",
                dayLabel = "M",
                steps = 0,
                heightFraction = 0f,
                aboveGoal = false,
                isToday = false,
                isPlaceholder = true,
            ),
        )
    }
}

/** Preview: bar detail card with distance data. */
@Preview(showBackground = true, name = "BarDetailCard — With distance")
@Composable
private fun PreviewBarDetailCardWithDistance() {
    PodometerTheme {
        BarDetailCard(
            bar = ChartBar(
                date = "2026-02-20",
                dayLabel = "F",
                steps = 8_400,
                heightFraction = 0.7f,
                aboveGoal = false,
                isToday = false,
                isPlaceholder = false,
                distanceKm = 6.3f,
            ),
        )
    }
}
