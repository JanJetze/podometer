// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.trends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.domain.model.DaySummary
import com.podometer.ui.theme.LocalGoalRingColors
import com.podometer.ui.theme.PodometerTheme
import java.time.LocalDate

// ─── Chart colour constants ───────────────────────────────────────────────────

/** Colour for bars below the minimum goal. */
private val WeeklyBarBelowMinimumColor = Color(0xFF90A4AE)

/** Colour for the dashed goal line. */
private val WeeklyGoalLineColor = Color(0xFF546E7A)

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Weekly trends bar chart showing daily step counts for a 7-day week with a goal line overlay.
 *
 * Features:
 * - 7 vertical bars (Mon-Sun) showing daily step counts.
 * - Horizontal dashed goal line at the target goal level.
 * - Bars coloured by tier: below minimum = muted, minimum-to-target = sage, above target = primary.
 * - Tapping a bar shows a tooltip with the exact step count and date.
 * - Left/right navigation arrows to move between weeks.
 * - Week date range label (e.g., "Mar 3 – Mar 9").
 *
 * This is a pure presentational composable — it holds no ViewModel references.
 *
 * @param days          Ordered list of [DaySummary] for the 7 days of the week (Mon to Sun).
 * @param targetGoal    User's daily target step goal; determines the goal line position.
 * @param minimumGoal   User's minimum daily activity level; determines tier colour boundary.
 * @param weekLabel     Human-readable label for the week, e.g. "Mar 3 – Mar 9".
 * @param onPreviousWeek Callback invoked when the user taps the left (previous) arrow.
 * @param onNextWeek    Callback invoked when the user taps the right (next) arrow.
 * @param canGoNext     Whether the right arrow should be enabled (false when showing current week).
 * @param modifier      Optional [Modifier] applied to the root [Column].
 */
@Composable
fun WeeklyTrendsChart(
    days: List<DaySummary>,
    targetGoal: Int,
    minimumGoal: Int,
    weekLabel: String,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val bars = buildTrendsChartBars(days, targetGoal, minimumGoal)
    val maxSteps = bars.maxOfOrNull { it.steps } ?: 0
    val goalFraction = computeTrendsGoalFraction(targetGoal, maxSteps)

    var selectedBarIndex by remember { mutableIntStateOf(-1) }

    // Resolve theme colours outside Canvas
    val ringColors = LocalGoalRingColors.current
    val aboveTargetColor = ringColors.target
    val minimumToTargetColor = ringColors.minimum
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier) {
        // ─── Navigation header ───────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onPreviousWeek) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous week",
                )
            }
            Text(
                text = weekLabel,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNextWeek, enabled = canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next week",
                )
            }
        }

        // ─── Bar chart canvas ─────────────────────────────────────────────────
        WeeklyTrendsChartCanvas(
            bars = bars,
            goalFraction = goalFraction,
            aboveTargetColor = aboveTargetColor,
            minimumToTargetColor = minimumToTargetColor,
            belowMinimumColor = WeeklyBarBelowMinimumColor,
            goalLineColor = WeeklyGoalLineColor,
            onBarTap = { index ->
                selectedBarIndex = if (selectedBarIndex == index) -1 else index
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        )

        // ─── Day labels ───────────────────────────────────────────────────────
        WeeklyTrendsChartLabels(
            bars = bars,
            selectedBarIndex = selectedBarIndex,
            accentColor = primaryColor,
            labelColor = labelColor,
        )

        // ─── Tooltip card ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = selectedBarIndex in bars.indices,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            if (selectedBarIndex in bars.indices) {
                WeeklyTrendsTooltip(bar = bars[selectedBarIndex])
            }
        }
    }
}

/**
 * Canvas that draws the 7 bars and the dashed goal line.
 *
 * @param bars                List of [TrendsChartBar]s to render (7 items for weekly chart).
 * @param goalFraction        Normalised height of the goal line in [0f, 1f].
 * @param aboveTargetColor    Bar fill colour for [TrendsBarTier.ABOVE_TARGET].
 * @param minimumToTargetColor Bar fill colour for [TrendsBarTier.MINIMUM_TO_TARGET].
 * @param belowMinimumColor   Bar fill colour for [TrendsBarTier.BELOW_MINIMUM].
 * @param goalLineColor       Colour of the dashed goal line.
 * @param onBarTap            Callback with the index of the tapped bar.
 * @param modifier            Applied to the [Canvas].
 */
@Composable
private fun WeeklyTrendsChartCanvas(
    bars: List<TrendsChartBar>,
    goalFraction: Float,
    aboveTargetColor: Color,
    minimumToTargetColor: Color,
    belowMinimumColor: Color,
    goalLineColor: Color,
    onBarTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) {
        Canvas(modifier = modifier) { /* nothing to draw */ }
        return
    }

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 3.dp.toPx() }
    val barGapPx = with(density) { 6.dp.toPx() }
    val minBarHeightPx = with(density) { 3.dp.toPx() }
    val goalDashOnPx = with(density) { 8.dp.toPx() }
    val goalDashOffPx = with(density) { 4.dp.toPx() }
    val goalLineStrokePx = with(density) { 1.5.dp.toPx() }

    Canvas(
        modifier = modifier.pointerInput(bars.size) {
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
            val rawBarHeight = bar.heightFraction * totalHeight
            val barHeight = when {
                bar.steps > 0 && rawBarHeight < minBarHeightPx -> minBarHeightPx
                else -> rawBarHeight
            }
            val barColor = when (bar.tier) {
                TrendsBarTier.ABOVE_TARGET -> aboveTargetColor
                TrendsBarTier.MINIMUM_TO_TARGET -> minimumToTargetColor
                TrendsBarTier.BELOW_MINIMUM -> belowMinimumColor
            }
            val top = totalHeight - barHeight
            drawRoundRect(
                color = barColor,
                topLeft = Offset(barLeft, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(cornerRadiusPx),
            )
        }

        // Dashed goal line
        val goalY = totalHeight * (1f - goalFraction)
        drawLine(
            color = goalLineColor,
            start = Offset(0f, goalY),
            end = Offset(totalWidth, goalY),
            strokeWidth = goalLineStrokePx,
            pathEffect = PathEffect.dashPathEffect(
                intervals = floatArrayOf(goalDashOnPx, goalDashOffPx),
                phase = 0f,
            ),
        )
    }
}

/**
 * Row of day-of-week labels (M T W T F S S) displayed below the chart canvas.
 *
 * The selected bar's label is bolded and rendered in [accentColor].
 *
 * @param bars             The bars whose [TrendsChartBar.dayLabel]s are displayed.
 * @param selectedBarIndex Index of the currently selected bar, or -1 if none.
 * @param accentColor      Colour for the selected bar's label.
 * @param labelColor       Default colour for all other labels.
 */
@Composable
private fun WeeklyTrendsChartLabels(
    bars: List<TrendsChartBar>,
    selectedBarIndex: Int,
    accentColor: Color,
    labelColor: Color,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        bars.forEachIndexed { index, bar ->
            val isSelected = index == selectedBarIndex
            Text(
                text = bar.dayLabel,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontWeight = if (isSelected) FontWeight.Bold else null,
                color = if (isSelected) accentColor else labelColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
            )
        }
    }
}

/**
 * Tooltip card shown when a bar is tapped — displays the date and exact step count.
 *
 * @param bar The selected [TrendsChartBar] to display.
 */
@Composable
private fun WeeklyTrendsTooltip(bar: TrendsChartBar) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val date = LocalDate.parse(bar.date, DATE_FORMATTER_ISO)
            Text(
                text = date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${formatStepsWithCommas(bar.steps)} steps",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun formatStepsWithCommas(steps: Int): String {
    return java.text.NumberFormat.getIntegerInstance().format(steps)
}

// ─── Preview functions ────────────────────────────────────────────────────────

/** Preview: full week with mixed step counts above and below goals. */
@Preview(showBackground = true, name = "WeeklyTrendsChart — Mixed data")
@Composable
private fun PreviewWeeklyTrendsChartMixed() {
    PodometerTheme(dynamicColor = false) {
        WeeklyTrendsChart(
            days = listOf(
                DaySummary("2026-03-02", 7_200, 5.4f),
                DaySummary("2026-03-03", 11_500, 8.6f),
                DaySummary("2026-03-04", 4_800, 3.6f),
                DaySummary("2026-03-05", 13_000, 9.7f),
                DaySummary("2026-03-06", 8_400, 6.3f),
                DaySummary("2026-03-07", 10_200, 7.6f),
                DaySummary("2026-03-08", 6_500, 4.9f),
            ),
            targetGoal = 10_000,
            minimumGoal = 6_000,
            weekLabel = "Mar 2 – Mar 8",
            onPreviousWeek = {},
            onNextWeek = {},
            canGoNext = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: all days above target goal. */
@Preview(showBackground = true, name = "WeeklyTrendsChart — All above target")
@Composable
private fun PreviewWeeklyTrendsChartAllAbove() {
    PodometerTheme(dynamicColor = false) {
        WeeklyTrendsChart(
            days = listOf(
                DaySummary("2026-03-02", 11_000, 8.2f),
                DaySummary("2026-03-03", 12_500, 9.4f),
                DaySummary("2026-03-04", 10_500, 7.9f),
                DaySummary("2026-03-05", 14_000, 10.5f),
                DaySummary("2026-03-06", 11_800, 8.8f),
                DaySummary("2026-03-07", 13_200, 9.9f),
                DaySummary("2026-03-08", 10_100, 7.6f),
            ),
            targetGoal = 10_000,
            minimumGoal = 6_000,
            weekLabel = "Mar 2 – Mar 8",
            onPreviousWeek = {},
            onNextWeek = {},
            canGoNext = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: empty — no data for the week. */
@Preview(showBackground = true, name = "WeeklyTrendsChart — Empty")
@Composable
private fun PreviewWeeklyTrendsChartEmpty() {
    PodometerTheme(dynamicColor = false) {
        WeeklyTrendsChart(
            days = emptyList(),
            targetGoal = 10_000,
            minimumGoal = 6_000,
            weekLabel = "Mar 2 – Mar 8",
            onPreviousWeek = {},
            onNextWeek = {},
            canGoNext = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: previous week (next arrow enabled). */
@Preview(showBackground = true, name = "WeeklyTrendsChart — Previous week (canGoNext)")
@Composable
private fun PreviewWeeklyTrendsChartPreviousWeek() {
    PodometerTheme(dynamicColor = false) {
        WeeklyTrendsChart(
            days = listOf(
                DaySummary("2026-02-23", 8_000, 6.0f),
                DaySummary("2026-02-24", 6_500, 4.9f),
                DaySummary("2026-02-25", 9_200, 6.9f),
                DaySummary("2026-02-26", 11_000, 8.2f),
                DaySummary("2026-02-27", 7_800, 5.8f),
                DaySummary("2026-02-28", 5_000, 3.7f),
                DaySummary("2026-03-01", 12_000, 9.0f),
            ),
            targetGoal = 10_000,
            minimumGoal = 6_000,
            weekLabel = "Feb 23 – Mar 1",
            onPreviousWeek = {},
            onNextWeek = {},
            canGoNext = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
