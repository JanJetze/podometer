// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.trends

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

// ─── Constants ────────────────────────────────────────────────────────────────

/** Day-of-month numbers to display on the X-axis label row. */
private val MONTHLY_AXIS_LABELS = setOf(1, 5, 10, 15, 20, 25, 30)

/** Colour for bars below the minimum goal. */
private val MonthlyBarBelowMinimumColor = Color(0xFF90A4AE)

/** Colour for the dashed goal line. */
private val MonthlyGoalLineColor = Color(0xFF546E7A)

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Monthly trends bar chart showing daily step counts for a full month with a goal line overlay.
 *
 * Features:
 * - Compact bar chart with one bar per day of the month (~28–31 bars).
 * - Horizontal dashed goal line at the target goal level.
 * - X-axis labels at day numbers 1, 5, 10, 15, 20, 25, 30.
 * - Bars coloured by tier: below minimum = muted, minimum-to-target = sage, above target = primary.
 * - Tapping a bar shows a tooltip with the exact step count and date.
 * - Left/right navigation arrows to move between months.
 * - Month label (e.g., "March 2026").
 *
 * This is a pure presentational composable — it holds no ViewModel references.
 *
 * @param days           Ordered list of [DaySummary] for each day of the month.
 * @param targetGoal     User's daily target step goal; determines the goal line position.
 * @param minimumGoal    User's minimum daily activity level; determines tier colour boundary.
 * @param monthLabel     Human-readable month label, e.g. "March 2026".
 * @param onPreviousMonth Callback invoked when the user taps the left (previous) arrow.
 * @param onNextMonth    Callback invoked when the user taps the right (next) arrow.
 * @param canGoNext      Whether the right arrow should be enabled (false when showing current month).
 * @param modifier       Optional [Modifier] applied to the root [Column].
 */
@Composable
fun MonthlyTrendsChart(
    days: List<DaySummary>,
    targetGoal: Int,
    minimumGoal: Int,
    monthLabel: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
) {
    val bars = buildMonthlyChartBars(days, targetGoal, minimumGoal)
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
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous month",
                )
            }
            Text(
                text = monthLabel,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onNextMonth, enabled = canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next month",
                )
            }
        }

        // ─── Bar chart canvas ─────────────────────────────────────────────────
        MonthlyTrendsChartCanvas(
            bars = bars,
            goalFraction = goalFraction,
            aboveTargetColor = aboveTargetColor,
            minimumToTargetColor = minimumToTargetColor,
            belowMinimumColor = MonthlyBarBelowMinimumColor,
            goalLineColor = MonthlyGoalLineColor,
            onBarTap = { index ->
                selectedBarIndex = if (selectedBarIndex == index) -1 else index
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )

        // ─── X-axis day labels ────────────────────────────────────────────────
        if (bars.isNotEmpty()) {
            MonthlyTrendsAxisLabels(
                bars = bars,
                selectedBarIndex = selectedBarIndex,
                accentColor = primaryColor,
                labelColor = labelColor,
            )
        }

        // ─── Tooltip card ─────────────────────────────────────────────────────
        if (selectedBarIndex in bars.indices) {
            MonthlyTrendsTooltip(bar = bars[selectedBarIndex])
        }
    }
}

/**
 * Canvas that draws compact monthly bars and the dashed goal line.
 *
 * @param bars                 List of [TrendsChartBar]s to render (one per day of the month).
 * @param goalFraction         Normalised height of the goal line in [0f, 1f].
 * @param aboveTargetColor     Bar fill colour for [TrendsBarTier.ABOVE_TARGET].
 * @param minimumToTargetColor Bar fill colour for [TrendsBarTier.MINIMUM_TO_TARGET].
 * @param belowMinimumColor    Bar fill colour for [TrendsBarTier.BELOW_MINIMUM].
 * @param goalLineColor        Colour of the dashed goal line.
 * @param onBarTap             Callback with the index of the tapped bar.
 * @param modifier             Applied to the [Canvas].
 */
@Composable
private fun MonthlyTrendsChartCanvas(
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
    val cornerRadiusPx = with(density) { 2.dp.toPx() }
    val barGapPx = with(density) { 2.dp.toPx() }
    val minBarHeightPx = with(density) { 2.dp.toPx() }
    val goalDashOnPx = with(density) { 6.dp.toPx() }
    val goalDashOffPx = with(density) { 3.dp.toPx() }
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
 * Sparse X-axis labels for the monthly chart, shown only for days in [MONTHLY_AXIS_LABELS].
 *
 * Bars at other positions have an empty placeholder to maintain alignment.
 *
 * @param bars             All bars in the monthly chart.
 * @param selectedBarIndex Index of the currently selected bar, or -1.
 * @param accentColor      Colour for the selected bar's label.
 * @param labelColor       Default colour for all other labels.
 */
@Composable
private fun MonthlyTrendsAxisLabels(
    bars: List<TrendsChartBar>,
    selectedBarIndex: Int,
    accentColor: Color,
    labelColor: Color,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        bars.forEachIndexed { index, bar ->
            val dayNum = bar.dayLabel.toIntOrNull() ?: 0
            val showLabel = dayNum in MONTHLY_AXIS_LABELS || index == selectedBarIndex
            val isSelected = index == selectedBarIndex
            Text(
                text = if (showLabel) bar.dayLabel else "",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                fontWeight = if (isSelected) FontWeight.Bold else null,
                color = if (isSelected) accentColor else labelColor,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
            )
        }
    }
}

/**
 * Tooltip card shown when a monthly bar is tapped.
 *
 * @param bar The selected [TrendsChartBar] to display.
 */
@Composable
private fun MonthlyTrendsTooltip(bar: TrendsChartBar) {
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
                text = "${java.text.NumberFormat.getIntegerInstance().format(bar.steps)} steps",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ─── Preview functions ────────────────────────────────────────────────────────

private fun marchSummaries(): List<DaySummary> {
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

/** Preview: full March 2026 with mixed step counts. */
@Preview(showBackground = true, name = "MonthlyTrendsChart — Full March")
@Composable
private fun PreviewMonthlyTrendsChartFull() {
    PodometerTheme(dynamicColor = false) {
        MonthlyTrendsChart(
            days = marchSummaries(),
            targetGoal = 10_000,
            minimumGoal = 6_000,
            monthLabel = "March 2026",
            onPreviousMonth = {},
            onNextMonth = {},
            canGoNext = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: partial data — only first 15 days. */
@Preview(showBackground = true, name = "MonthlyTrendsChart — Partial (15 days)")
@Composable
private fun PreviewMonthlyTrendsChartPartial() {
    PodometerTheme(dynamicColor = false) {
        MonthlyTrendsChart(
            days = marchSummaries().take(15),
            targetGoal = 10_000,
            minimumGoal = 6_000,
            monthLabel = "March 2026",
            onPreviousMonth = {},
            onNextMonth = {},
            canGoNext = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: empty month — no data. */
@Preview(showBackground = true, name = "MonthlyTrendsChart — Empty")
@Composable
private fun PreviewMonthlyTrendsChartEmpty() {
    PodometerTheme(dynamicColor = false) {
        MonthlyTrendsChart(
            days = emptyList(),
            targetGoal = 10_000,
            minimumGoal = 6_000,
            monthLabel = "March 2026",
            onPreviousMonth = {},
            onNextMonth = {},
            canGoNext = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: previous month (next arrow enabled). */
@Preview(showBackground = true, name = "MonthlyTrendsChart — Previous month (canGoNext)")
@Composable
private fun PreviewMonthlyTrendsChartPrevious() {
    PodometerTheme(dynamicColor = false) {
        MonthlyTrendsChart(
            days = marchSummaries(),
            targetGoal = 10_000,
            minimumGoal = 6_000,
            monthLabel = "February 2026",
            onPreviousMonth = {},
            onNextMonth = {},
            canGoNext = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
