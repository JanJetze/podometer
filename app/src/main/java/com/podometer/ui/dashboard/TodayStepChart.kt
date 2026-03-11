// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val TOOLTIP_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mma").withZone(ZoneId.systemDefault())

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

/**
 * Returns the X-axis label interval in hours for the given [resolution].
 *
 * - [ChartResolution.HOURLY]: every 1 hour (every bar)
 * - [ChartResolution.THIRTY_MIN]: every 2 hours (every 4 bars)
 * - [ChartResolution.FIFTEEN_MIN]: every 2 hours (every 8 bars)
 * - [ChartResolution.FIVE_MIN]: every 3 hours (every 36 bars)
 *
 * @param resolution The current chart resolution.
 * @return Number of hours between consecutive X-axis labels.
 */
fun labelIntervalHoursFor(resolution: ChartResolution): Int = when (resolution) {
    ChartResolution.HOURLY -> 1
    ChartResolution.THIRTY_MIN -> 2
    ChartResolution.FIFTEEN_MIN -> 2
    ChartResolution.FIVE_MIN -> 3
}

/**
 * Formats a tooltip string for a single [StepBar], showing the time range and step count.
 *
 * Example output: "8:00am–8:30am: 245 steps"
 *
 * @param bar        The bar to format.
 * @param resolution The chart resolution, used to compute the end time of the bucket.
 * @return A human-readable string like "8:00am–8:30am: 245 steps" (or "1 step" when count = 1).
 */
fun formatTooltipTimeRange(bar: StepBar, resolution: ChartResolution): String {
    val bucketMs = resolution.minutes.toLong() * 60_000L
    val start = Instant.ofEpochMilli(bar.startTime)
    val end = Instant.ofEpochMilli(bar.startTime + bucketMs)
    val startLabel = TOOLTIP_TIME_FORMATTER.format(start).lowercase()
    val endLabel = TOOLTIP_TIME_FORMATTER.format(end).lowercase()
    val stepWord = if (bar.stepCount == 1) "step" else "steps"
    return "$startLabel\u2013$endLabel: ${bar.stepCount} $stepWord"
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Vertical bar chart showing today's step counts broken down by time buckets.
 *
 * Features:
 * - Adjustable resolution via chip group: 5m, 15m, 30m, 1h
 * - Bars coloured with [MaterialTheme.colorScheme.primary]
 * - The current time bucket's bar is highlighted with a distinct colour
 * - Flush bars (no gaps) forming a continuous histogram
 * - Canvas-drawn X-axis time labels with resolution-aware spacing
 * - Tap a bar to show a tooltip with the time range and step count
 * - Empty-state message when no data is available
 *
 * This is a purely presentational composable. The selected-bar state is held internally.
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
            val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

            val density = LocalDensity.current

            var selectedBarIndex by remember(resolution) { mutableStateOf<Int?>(null) }

            val intervalHours = labelIntervalHoursFor(resolution)
            val timeLabels = buildTimeLabels(aggregated, resolution, intervalHours = intervalHours)

            TodayStepChartCanvas(
                bars = aggregated,
                ceiling = ceiling,
                currentBucketStart = currentBucketStart,
                primaryColor = primaryColor,
                highlightColor = highlightColor,
                trackColor = trackColor,
                selectedBarIndex = selectedBarIndex,
                onBarTap = { index ->
                    selectedBarIndex = if (selectedBarIndex == index) null else index
                },
                density = density,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )

            TodayStepChartXAxisCanvas(
                bars = aggregated,
                timeLabels = timeLabels,
                labelColor = labelColor,
                density = density,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
            )

            AnimatedVisibility(
                visible = selectedBarIndex != null && selectedBarIndex!! in aggregated.indices,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                val idx = selectedBarIndex
                if (idx != null && idx in aggregated.indices) {
                    StepBarTooltip(
                        bar = aggregated[idx],
                        resolution = resolution,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Canvas that draws the vertical step bars for [TodayStepChart].
 *
 * Bars are flush (no gaps), forming a continuous histogram. The selected bar is drawn with
 * a slightly lower opacity overlay to indicate selection. Tapping a bar invokes [onBarTap].
 *
 * @param bars               Aggregated [StepBar]s to render.
 * @param ceiling            Y-axis maximum (equals max step count).
 * @param currentBucketStart Epoch ms of the current time bucket; used to highlight that bar.
 * @param primaryColor       Default bar colour.
 * @param highlightColor     Colour used for the current time bucket bar.
 * @param trackColor         Background fill for empty/zero bars.
 * @param selectedBarIndex   Index of the currently selected bar, or null if none.
 * @param onBarTap           Called with the tapped bar index when the user taps the canvas.
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
    selectedBarIndex: Int?,
    onBarTap: (Int) -> Unit,
    density: androidx.compose.ui.unit.Density,
    modifier: Modifier = Modifier,
) {
    val cornerRadiusDp = 1.dp
    val minBarHeightDp = 3.dp

    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
    val minBarHeightPx = with(density) { minBarHeightDp.toPx() }

    Canvas(
        modifier = modifier.pointerInput(bars) {
            detectTapGestures { offset ->
                if (bars.isEmpty()) return@detectTapGestures
                val barWidth = size.width.toFloat() / bars.size
                val index = (offset.x / barWidth).toInt().coerceIn(0, bars.size - 1)
                onBarTap(index)
            }
        },
    ) {
        val totalWidth = size.width
        val totalHeight = size.height
        val barCount = bars.size
        if (barCount == 0) return@Canvas

        val barWidth = totalWidth / barCount

        bars.forEachIndexed { index, bar ->
            val barLeft = index * barWidth
            val isCurrentBucket = bar.startTime == currentBucketStart
            val isSelected = index == selectedBarIndex
            val baseColor = if (isCurrentBucket) highlightColor else primaryColor
            val barColor = if (isSelected) baseColor.copy(alpha = baseColor.alpha * 0.6f) else baseColor

            if (ceiling == 0 || bar.stepCount == 0) {
                // Draw a minimal placeholder at the bottom
                drawRoundRect(
                    color = if (isSelected) trackColor.copy(alpha = 0.6f) else trackColor,
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
 * Canvas-based X-axis label row for [TodayStepChart].
 *
 * Draws text labels at the left edge of the corresponding bars, using absolute pixel positions
 * that match the bar canvas layout. This avoids the inaccuracy of weight-based Row spacers.
 *
 * @param bars       Aggregated bars (defines the number of columns and bar width).
 * @param timeLabels List of (barIndex, label) pairs indicating where to display labels.
 * @param labelColor Colour for the label text.
 * @param density    Used for sp-to-px text size conversion.
 * @param modifier   Applied to the [Canvas].
 */
@Composable
private fun TodayStepChartXAxisCanvas(
    bars: List<StepBar>,
    timeLabels: List<Pair<Int, String>>,
    labelColor: Color,
    density: androidx.compose.ui.unit.Density,
    modifier: Modifier = Modifier,
) {
    val labelSizePx = with(density) { 10.sp.toPx() }
    val labelColorArgb = android.graphics.Color.argb(
        (labelColor.alpha * 255).toInt(),
        (labelColor.red * 255).toInt(),
        (labelColor.green * 255).toInt(),
        (labelColor.blue * 255).toInt(),
    )

    Canvas(modifier = modifier) {
        if (bars.isEmpty()) return@Canvas
        val barWidth = size.width / bars.size
        val paint = android.graphics.Paint().apply {
            color = labelColorArgb
            textSize = labelSizePx
            isAntiAlias = true
        }
        timeLabels.forEach { (index, label) ->
            val x = index * barWidth
            drawContext.canvas.nativeCanvas.drawText(label, x, size.height, paint)
        }
    }
}

/**
 * Tooltip card shown above the chart when a bar is selected.
 *
 * Displays the time range and step count for the selected [bar].
 *
 * @param bar        The selected [StepBar].
 * @param resolution The current resolution, used to compute the bucket end time.
 * @param modifier   Applied to the [Surface].
 */
@Composable
private fun StepBarTooltip(
    bar: StepBar,
    resolution: ChartResolution,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        modifier = modifier,
    ) {
        Text(
            text = formatTooltipTimeRange(bar, resolution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
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

/** Preview: tooltip visible on hourly bar. */
@Preview(showBackground = true, name = "TodayStepChart — Tooltip visible")
@Composable
private fun PreviewTodayStepChartTooltip() {
    PodometerTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Show the tooltip card directly for preview purposes
            StepBarTooltip(
                bar = StepBar(startTime = 1_741_860_000_000L, stepCount = 245),
                resolution = ChartResolution.THIRTY_MIN,
            )
        }
    }
}
