// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.ui.theme.LocalGoalRingColors
import com.podometer.ui.theme.PodometerTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Data models ──────────────────────────────────────────────────────────────

/**
 * Represents a single bar in the today's step bar chart.
 *
 * @property startTime  5-min-aligned epoch milliseconds marking the start of the bucket.
 * @property stepCount  Total steps accumulated in this time bucket.
 */
data class StepBar(val startTime: Long, val stepCount: Int)

/**
 * Supported resolutions for the [TodayStepChart] time axis.
 *
 * @property minutes  The bucket width in minutes.
 * @property label    Short display label for the segmented control.
 */
enum class ChartResolution(val minutes: Int, val label: String) {
    FIVE_MIN(5, "5m"),
    FIFTEEN_MIN(15, "15m"),
    THIRTY_MIN(30, "30m"),
    HOURLY(60, "1h"),
}

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

private val TIME_LABEL_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("ha").withZone(ZoneId.systemDefault())

/**
 * Aggregates a list of 5-minute [StepBar]s into wider buckets defined by [resolution].
 *
 * Bars are grouped by flooring their [StepBar.startTime] to the nearest [resolution] boundary.
 * Within each bucket, [StepBar.stepCount]s are summed. The [StepBar.startTime] of the output
 * bar is the earliest input bar's timestamp in that bucket.
 *
 * If [resolution] is [ChartResolution.FIVE_MIN], the input is returned as-is (no aggregation).
 *
 * @param bars       List of 5-min-aligned [StepBar]s to aggregate.
 * @param resolution Target bucket width.
 * @return List of [StepBar]s aggregated to [resolution], sorted by startTime.
 */
fun aggregateBars(bars: List<StepBar>, resolution: ChartResolution): List<StepBar> {
    if (bars.isEmpty()) return emptyList()
    if (resolution == ChartResolution.FIVE_MIN) return bars

    val bucketMs = resolution.minutes.toLong() * 60_000L
    return bars
        .groupBy { bar -> (bar.startTime / bucketMs) * bucketMs }
        .entries
        .sortedBy { it.key }
        .map { (bucketStart, barsInBucket) ->
            StepBar(
                startTime = bucketStart,
                stepCount = barsInBucket.sumOf { it.stepCount },
            )
        }
}

/**
 * Returns the Y-axis ceiling for the chart, which equals the maximum step count across all bars.
 *
 * Returns 0 if [bars] is empty or all bars have zero steps.
 *
 * @param bars List of [StepBar]s to inspect.
 * @return Maximum [StepBar.stepCount] in [bars], or 0 if empty.
 */
fun chartYAxisCeiling(bars: List<StepBar>): Int {
    return bars.maxOfOrNull { it.stepCount } ?: 0
}

/**
 * Builds a list of (barIndex, timeLabel) pairs for the X-axis labels.
 *
 * Labels are emitted every [intervalHours] bars. The label text is the bar's time
 * formatted as "ha" lowercase (e.g. "9am", "2pm") in the system default timezone.
 *
 * @param bars          The aggregated bars to label.
 * @param resolution    Current chart resolution (used for context, not computation here).
 * @param intervalHours How many bars apart each label should be.
 * @return A list of (barIndex, labelText) pairs.
 */
fun buildTimeLabels(
    bars: List<StepBar>,
    resolution: ChartResolution,
    intervalHours: Int = 2,
): List<Pair<Int, String>> {
    if (bars.isEmpty()) return emptyList()
    val intervalBars = maxOf(1, (intervalHours * 60) / resolution.minutes)
    return bars.mapIndexedNotNull { index, bar ->
        if (index % intervalBars == 0) {
            val label = TIME_LABEL_FORMATTER.format(
                Instant.ofEpochMilli(bar.startTime),
            ).lowercase()
            index to label
        } else {
            null
        }
    }
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Vertical bar chart showing today's step counts broken down by time buckets.
 *
 * Features:
 * - Adjustable resolution via chip group: 5m, 15m, 30m, 1h
 * - Bars coloured with [MaterialTheme.colorScheme.primary]
 * - The current time bucket's bar is highlighted with a distinct colour
 * - X-axis time labels every few hours
 * - Empty-state message when no data is available
 *
 * This is a purely presentational composable — it holds no internal state.
 *
 * @param bars               5-minute-aligned step buckets for today (may be empty).
 * @param resolution         Currently selected chart resolution.
 * @param onResolutionChange Callback invoked when the user selects a different resolution.
 * @param modifier           Optional [Modifier] applied to the root [Column].
 */
@Composable
fun TodayStepChart(
    bars: List<StepBar>,
    resolution: ChartResolution,
    onResolutionChange: (ChartResolution) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // Resolution selector chips
        ResolutionChips(
            resolution = resolution,
            onResolutionChange = onResolutionChange,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        val aggregated = aggregateBars(bars, resolution)

        if (aggregated.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No step data for today yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val ceiling = chartYAxisCeiling(aggregated)
            val nowMs = System.currentTimeMillis()
            val bucketMs = resolution.minutes.toLong() * 60_000L
            val currentBucketStart = (nowMs / bucketMs) * bucketMs

            val primaryColor = MaterialTheme.colorScheme.primary
            val highlightColor = LocalGoalRingColors.current.target
            val trackColor = MaterialTheme.colorScheme.surfaceVariant

            val density = LocalDensity.current

            TodayStepChartCanvas(
                bars = aggregated,
                ceiling = ceiling,
                currentBucketStart = currentBucketStart,
                primaryColor = primaryColor,
                highlightColor = highlightColor,
                trackColor = trackColor,
                density = density,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )

            // X-axis labels: use 3-hour intervals for 5m resolution to reduce crowding
            val labelIntervalHours = if (resolution == ChartResolution.FIVE_MIN) 3 else 2
            val timeLabels = buildTimeLabels(aggregated, resolution, intervalHours = labelIntervalHours)
            if (timeLabels.isNotEmpty()) {
                TodayStepChartXAxis(
                    bars = aggregated,
                    timeLabels = timeLabels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * Canvas that draws the vertical step bars for [TodayStepChart].
 *
 * @param bars               Aggregated [StepBar]s to render.
 * @param ceiling            Y-axis maximum (equals max step count).
 * @param currentBucketStart Epoch ms of the current time bucket; used to highlight that bar.
 * @param primaryColor       Default bar colour.
 * @param highlightColor     Colour used for the current time bucket bar.
 * @param trackColor         Background fill for empty/zero bars.
 * @param density            Density for dp-to-px conversions.
 * @param modifier           Applied to the [Canvas].
 */
@Composable
private fun TodayStepChartCanvas(
    bars: List<StepBar>,
    ceiling: Int,
    currentBucketStart: Long,
    primaryColor: Color,
    highlightColor: Color,
    trackColor: Color,
    density: androidx.compose.ui.unit.Density,
    modifier: Modifier = Modifier,
) {
    val cornerRadiusDp = 3.dp
    val barGapDp = 2.dp
    val minBarHeightDp = 3.dp

    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
    val barGapPx = with(density) { barGapDp.toPx() }
    val minBarHeightPx = with(density) { minBarHeightDp.toPx() }

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barCount = bars.size
        if (barCount == 0) return@Canvas

        val totalGapWidth = barGapPx * (barCount - 1)
        val barWidth = (totalWidth - totalGapWidth) / barCount

        bars.forEachIndexed { index, bar ->
            val barLeft = index * (barWidth + barGapPx)
            val isCurrentBucket = bar.startTime == currentBucketStart
            val barColor = if (isCurrentBucket) highlightColor else primaryColor

            if (ceiling == 0 || bar.stepCount == 0) {
                // Draw a minimal placeholder at the bottom
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(barLeft, totalHeight - minBarHeightPx),
                    size = Size(barWidth, minBarHeightPx),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                )
            } else {
                val fraction = (bar.stepCount.toFloat() / ceiling.toFloat()).coerceIn(0f, 1f)
                val rawBarHeight = fraction * totalHeight
                val barHeight = maxOf(rawBarHeight, minBarHeightPx)
                val top = totalHeight - barHeight
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(barLeft, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                )
            }
        }
    }
}

/**
 * X-axis label row for [TodayStepChart], placing time strings under their corresponding bars.
 *
 * @param bars       Aggregated bars (defines the number of columns).
 * @param timeLabels List of (barIndex, label) pairs indicating where to display labels.
 * @param modifier   Applied to the [Row].
 */
@Composable
private fun TodayStepChartXAxis(
    bars: List<StepBar>,
    timeLabels: List<Pair<Int, String>>,
    modifier: Modifier = Modifier,
) {
    val labelMap = timeLabels.toMap()
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        bars.forEachIndexed { index, _ ->
            val label = labelMap[index]
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Segmented chip group for selecting the chart [ChartResolution].
 *
 * @param resolution         Currently selected resolution.
 * @param onResolutionChange Callback when user selects a different resolution.
 * @param modifier           Applied to the [Row].
 */
@Composable
private fun ResolutionChips(
    resolution: ChartResolution,
    onResolutionChange: (ChartResolution) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartResolution.entries.forEach { res ->
            FilterChip(
                selected = res == resolution,
                onClick = { onResolutionChange(res) },
                label = {
                    Text(
                        text = res.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

// ─── Preview functions ────────────────────────────────────────────────────────

private fun previewBars(): List<StepBar> {
    // Simulate a day's worth of 5-min buckets starting at 6 AM
    val sixAm = 1_741_860_000_000L // approximate 6 AM on 2026-03-10
    return (0 until 144).map { i ->
        StepBar(
            startTime = sixAm + i.toLong() * 5 * 60_000L,
            stepCount = if (i < 20) 0 else (50 + (i % 30) * 10).coerceAtMost(300),
        )
    }
}

/** Preview: full day with data, 5-minute resolution. */
@Preview(showBackground = true, name = "TodayStepChart — 5m resolution")
@Composable
private fun PreviewTodayStepChart5m() {
    PodometerTheme(dynamicColor = false) {
        TodayStepChart(
            bars = previewBars(),
            resolution = ChartResolution.FIVE_MIN,
            onResolutionChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: 15-minute resolution. */
@Preview(showBackground = true, name = "TodayStepChart — 15m resolution")
@Composable
private fun PreviewTodayStepChart15m() {
    PodometerTheme(dynamicColor = false) {
        TodayStepChart(
            bars = previewBars(),
            resolution = ChartResolution.FIFTEEN_MIN,
            onResolutionChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: 30-minute resolution. */
@Preview(showBackground = true, name = "TodayStepChart — 30m resolution")
@Composable
private fun PreviewTodayStepChart30m() {
    PodometerTheme(dynamicColor = false) {
        TodayStepChart(
            bars = previewBars(),
            resolution = ChartResolution.THIRTY_MIN,
            onResolutionChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: hourly resolution. */
@Preview(showBackground = true, name = "TodayStepChart — Hourly resolution")
@Composable
private fun PreviewTodayStepChartHourly() {
    PodometerTheme(dynamicColor = false) {
        TodayStepChart(
            bars = previewBars(),
            resolution = ChartResolution.HOURLY,
            onResolutionChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: empty state — no step data. */
@Preview(showBackground = true, name = "TodayStepChart — Empty state")
@Composable
private fun PreviewTodayStepChartEmpty() {
    PodometerTheme(dynamicColor = false) {
        TodayStepChart(
            bars = emptyList(),
            resolution = ChartResolution.FIFTEEN_MIN,
            onResolutionChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: dark theme with hourly resolution. */
@Preview(showBackground = true, backgroundColor = 0xFF0E1514, name = "TodayStepChart — Dark theme")
@Composable
private fun PreviewTodayStepChartDark() {
    PodometerTheme(darkTheme = true, dynamicColor = false) {
        TodayStepChart(
            bars = previewBars(),
            resolution = ChartResolution.HOURLY,
            onResolutionChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
