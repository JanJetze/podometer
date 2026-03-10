// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import com.podometer.ui.dashboard.formatActivityTime
import com.podometer.ui.dashboard.formatStepCount
import com.podometer.ui.theme.PodometerTheme

// ─── Data model ───────────────────────────────────────────────────────────────

/**
 * A single data point in the step graph, representing one time bucket.
 *
 * @property bucketStartMillis Epoch milliseconds of the bucket start.
 * @property cumulativeSteps   Running total of steps up to and including this bucket.
 * @property bucketSteps       Number of steps in this bucket alone.
 */
data class StepGraphPoint(
    val bucketStartMillis: Long,
    val cumulativeSteps: Int,
    val bucketSteps: Int,
)

/**
 * Complete data for rendering the step graph.
 *
 * @property points          Time-ordered list of graph data points.
 * @property maxCumulative   Maximum cumulative step count (for left Y-axis scaling).
 * @property maxBucket       Maximum per-bucket step count (for right Y-axis scaling).
 */
data class StepGraphData(
    val points: List<StepGraphPoint>,
    val maxCumulative: Int,
    val maxBucket: Int,
)

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Builds [StepGraphData] from raw sensor windows.
 *
 * Groups windows into time buckets of [bucketSizeMs] duration, computes cumulative
 * and per-bucket step counts.
 *
 * @param windows      Chronologically ordered sensor windows for a single day.
 * @param bucketSizeMs Size of each time bucket in milliseconds.
 * @param dayStartMillis Start of the day in epoch millis.
 * @param dayEndMillis   End of the day in epoch millis.
 * @return [StepGraphData] ready for rendering.
 */
fun buildStepGraphData(
    windows: List<StepWindowPoint>,
    bucketSizeMs: Long,
    dayStartMillis: Long,
    dayEndMillis: Long,
): StepGraphData {
    if (windows.isEmpty()) {
        return StepGraphData(
            points = emptyList(),
            maxCumulative = 0,
            maxBucket = 0,
        )
    }

    // Spread gap-spanning windows so steps are distributed across time.
    val spread = windows.spreadWindows()

    // Group windows into buckets
    val buckets = mutableMapOf<Long, MutableList<StepWindowPoint>>()
    for (window in spread) {
        val bucketStart = ((window.timestamp - dayStartMillis) / bucketSizeMs) * bucketSizeMs + dayStartMillis
        buckets.getOrPut(bucketStart) { mutableListOf() }.add(window)
    }

    val sortedBucketStarts = buckets.keys.sorted()
    var cumulativeSteps = 0
    val points = sortedBucketStarts.map { bucketStart ->
        val bucketWindows = buckets[bucketStart]!!
        val bucketSteps = bucketWindows.sumOf { it.stepCount }
        cumulativeSteps += bucketSteps
        StepGraphPoint(
            bucketStartMillis = bucketStart,
            cumulativeSteps = cumulativeSteps,
            bucketSteps = bucketSteps,
        )
    }

    val maxCumulative = points.maxOfOrNull { it.cumulativeSteps } ?: 0
    val maxBucket = points.maxOfOrNull { it.bucketSteps } ?: 0

    return StepGraphData(
        points = points,
        maxCumulative = maxCumulative,
        maxBucket = maxBucket,
    )
}

// ─── Composable ───────────────────────────────────────────────────────────────

/** Width reserved for left Y-axis labels. */
private val Y_AXIS_LEFT_WIDTH = 40.dp

/** Width reserved for right Y-axis labels. */
private val Y_AXIS_RIGHT_WIDTH = 36.dp

/** Bottom padding for X-axis labels in dp. */
private val X_AXIS_HEIGHT = 24.dp

/** Height of the chart canvas area. */
private val CHART_HEIGHT = 200.dp

/** Alpha for the crosshair line. */
private const val CROSSHAIR_ALPHA = 0.6f

/** Number of Y-axis grid divisions. */
private const val Y_GRID_DIVISIONS = 4

/**
 * Rounds a value up to a "nice" number for axis labeling (1, 2, 5 multiples).
 */
internal fun niceAxisMax(value: Int): Int {
    if (value <= 0) return 100
    val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(value.toDouble()))).toInt()
    val normalized = value.toDouble() / magnitude
    val niceNorm = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return (niceNorm * magnitude).toInt().coerceAtLeast(1)
}

/**
 * Formats a step count for Y-axis labels: "1.2k" for thousands, plain number otherwise.
 */
internal fun formatAxisLabel(value: Int): String = when {
    value >= 10_000 -> "${value / 1_000}k"
    value >= 1_000 -> {
        val thousands = value / 1_000
        val hundreds = (value % 1_000) / 100
        if (hundreds > 0) "${thousands}.${hundreds}k" else "${thousands}k"
    }
    else -> value.toString()
}

/** Time labels for the X-axis at various zoom levels. */
private val TIME_LABELS_COARSE = listOf(
    "6a" to 6f / 24f,
    "9a" to 9f / 24f,
    "12p" to 12f / 24f,
    "3p" to 15f / 24f,
    "6p" to 18f / 24f,
    "9p" to 21f / 24f,
)

private val TIME_LABELS_FINE = listOf(
    "12a" to 0f / 24f,
    "3a" to 3f / 24f,
    "6a" to 6f / 24f,
    "9a" to 9f / 24f,
    "12p" to 12f / 24f,
    "3p" to 15f / 24f,
    "6p" to 18f / 24f,
    "9p" to 21f / 24f,
)

/**
 * Dual-axis step line graph showing cumulative and per-bucket step counts.
 *
 * Features:
 * - Left Y-axis: cumulative step count with labeled grid lines
 * - Right Y-axis: per-bucket step count with labeled grid lines
 * - Tap to show crosshair with tooltip
 * - Horizontal pan and pinch-to-zoom on the time axis
 * - Double-tap to reset zoom
 *
 * @param graphData               Pre-computed [StepGraphData] to render.
 * @param dayStartMillis          Start of the day in epoch millis for time calculations.
 * @param dayEndMillis            End of the day in epoch millis.
 * @param modifier                Optional [Modifier] applied to the root [Column].
 */
@Composable
fun StepGraph(
    graphData: StepGraphData,
    dayStartMillis: Long,
    dayEndMillis: Long,
    modifier: Modifier = Modifier,
) {
    val cumulativeLineColor = MaterialTheme.colorScheme.primary
    val bucketLineColor = MaterialTheme.colorScheme.tertiary
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurface

    // Zoom/pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetFraction by remember { mutableFloatStateOf(0f) }

    // Crosshair state
    var crosshairFraction by remember { mutableStateOf<Float?>(null) }

    val dayDuration = (dayEndMillis - dayStartMillis).toFloat()

    // Compute nice axis maximums
    val niceMaxCumulative = niceAxisMax(graphData.maxCumulative)
    val niceMaxBucket = niceAxisMax(graphData.maxBucket)

    Column(modifier = modifier) {
        // Y-axis labels + chart in a Row
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Y-axis labels (cumulative)
            YAxisLabels(
                maxValue = niceMaxCumulative,
                color = cumulativeLineColor,
                alignment = Alignment.End,
                modifier = Modifier
                    .width(Y_AXIS_LEFT_WIDTH)
                    .height(CHART_HEIGHT),
            )

            // Chart area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(CHART_HEIGHT),
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(1f, 10f)
                                // Anchor zoom to the pinch centroid
                                val centroidFraction = centroid.x / size.width
                                val centroidDay = offsetFraction + centroidFraction / oldScale
                                val newOffset = (centroidDay - centroidFraction / newScale)
                                    .coerceIn(0f, (1f - 1f / newScale).coerceAtLeast(0f))
                                // Apply pan
                                val panFraction = pan.x / size.width / newScale
                                val finalOffset = (newOffset - panFraction)
                                    .coerceIn(0f, (1f - 1f / newScale).coerceAtLeast(0f))
                                scale = newScale
                                offsetFraction = finalOffset
                                crosshairFraction = null
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    val chartWidth = size.width.toFloat()
                                    val tapFraction = offset.x / chartWidth
                                    val dayFraction = offsetFraction + tapFraction / scale
                                    crosshairFraction = dayFraction.coerceIn(0f, 1f)
                                },
                                onDoubleTap = {
                                    scale = 1f
                                    offsetFraction = 0f
                                    crosshairFraction = null
                                },
                            )
                        },
                ) {
                    val chartWidth = size.width
                    val chartHeight = size.height
                    val visibleStart = offsetFraction
                    val visibleEnd = (offsetFraction + 1f / scale).coerceAtMost(1f)
                    val visibleRange = visibleEnd - visibleStart
                    if (visibleRange <= 0f) return@Canvas

                    fun fractionToX(f: Float): Float =
                        ((f - visibleStart) / visibleRange) * chartWidth

                    // Grid lines with Y-axis labels
                    val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                    for (i in 1 until Y_GRID_DIVISIONS) {
                        val y = chartHeight * (1f - i.toFloat() / Y_GRID_DIVISIONS)
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(chartWidth, y),
                            strokeWidth = 1f,
                            pathEffect = gridDash,
                        )
                    }

                    if (graphData.points.isEmpty()) return@Canvas

                    // Cumulative line (left axis)
                    if (niceMaxCumulative > 0) {
                        val path = Path()
                        var started = false
                        for (point in graphData.points) {
                            val fraction = (point.bucketStartMillis - dayStartMillis) / dayDuration
                            val x = fractionToX(fraction)
                            val y = chartHeight * (1f - point.cumulativeSteps.toFloat() / niceMaxCumulative)
                            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                        }
                        drawPath(path, cumulativeLineColor, style = Stroke(width = 2.5f))
                    }

                    // Bucket line (right axis)
                    if (niceMaxBucket > 0) {
                        val path = Path()
                        var started = false
                        for (point in graphData.points) {
                            val fraction = (point.bucketStartMillis - dayStartMillis) / dayDuration
                            val x = fractionToX(fraction)
                            val y = chartHeight * (1f - point.bucketSteps.toFloat() / niceMaxBucket)
                            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                        }
                        drawPath(path, bucketLineColor, style = Stroke(width = 1.5f), alpha = 0.8f)
                    }

                    // Crosshair
                    val cf = crosshairFraction
                    if (cf != null) {
                        val cx = fractionToX(cf)
                        if (cx in 0f..chartWidth) {
                            drawLine(crosshairColor, Offset(cx, 0f), Offset(cx, chartHeight), 1.5f, alpha = CROSSHAIR_ALPHA)
                        }
                    }
                }

                // Tooltip overlay
                val cf = crosshairFraction
                if (cf != null && graphData.points.isNotEmpty()) {
                    val targetMillis = dayStartMillis + (cf * dayDuration).toLong()
                    val nearestPoint = graphData.points.minByOrNull {
                        kotlin.math.abs(it.bucketStartMillis - targetMillis)
                    }
                    if (nearestPoint != null) {
                        CrosshairTooltip(point = nearestPoint, modifier = Modifier.align(Alignment.TopCenter))
                    }
                }
            }

            // Right Y-axis labels (per bucket)
            YAxisLabels(
                maxValue = niceMaxBucket,
                color = bucketLineColor,
                alignment = Alignment.Start,
                modifier = Modifier
                    .width(Y_AXIS_RIGHT_WIDTH)
                    .height(CHART_HEIGHT),
            )
        }

        // X-axis time labels (indented to match chart area)
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(Y_AXIS_LEFT_WIDTH))
            StepGraphTimeLabels(
                scale = scale,
                offsetFraction = offsetFraction,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Y_AXIS_RIGHT_WIDTH))
        }

        // Legend
        StepGraphLegend(
            maxCumulative = graphData.maxCumulative,
            maxBucket = graphData.maxBucket,
            cumulativeColor = cumulativeLineColor,
            bucketColor = bucketLineColor,
        )
    }
}

/**
 * Y-axis labels drawn vertically alongside the chart.
 *
 * Shows [Y_GRID_DIVISIONS] evenly spaced labels from 0 to [maxValue].
 *
 * @param maxValue  The maximum value on this axis.
 * @param color     Color for the label text.
 * @param alignment Horizontal alignment of labels within the column.
 * @param modifier  Modifier (must include width and height constraints).
 */
@Composable
private fun YAxisLabels(
    maxValue: Int,
    color: Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val textMeasurer = rememberTextMeasurer()

    // Measure label height so we can center each label on its grid line
    val sampleResult = textMeasurer.measure("0", labelStyle)
    val labelHeightPx = sampleResult.size.height

    BoxWithConstraints(modifier = modifier) {
        val heightPx = with(density) { maxHeight.toPx() }
        for (i in 0..Y_GRID_DIVISIONS) {
            val value = maxValue * i / Y_GRID_DIVISIONS
            val yFraction = 1f - i.toFloat() / Y_GRID_DIVISIONS
            val yOffsetPx = (yFraction * heightPx - labelHeightPx / 2f).toInt()
                .coerceIn(0, (heightPx - labelHeightPx).toInt())

            Text(
                text = formatAxisLabel(value),
                style = labelStyle,
                color = color,
                textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, yOffsetPx) },
            )
        }
    }
}

/**
 * Tooltip card shown when the crosshair is active.
 *
 * Displays the time, cumulative steps, and bucket steps for the nearest data point.
 */
@Composable
private fun CrosshairTooltip(
    point: StepGraphPoint,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        modifier = modifier.padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = formatActivityTime(point.bucketStartMillis),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${formatStepCount(point.cumulativeSteps)} total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${formatStepCount(point.bucketSteps)} steps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

/**
 * Row of time labels below the graph, adjusting for zoom/pan state.
 *
 * Uses [TIME_LABELS_COARSE] at default zoom and [TIME_LABELS_FINE] when zoomed in.
 */
@Composable
private fun StepGraphTimeLabels(
    scale: Float,
    offsetFraction: Float,
    modifier: Modifier = Modifier,
) {
    val visibleStart = offsetFraction
    val visibleEnd = (offsetFraction + 1f / scale).coerceAtMost(1f)
    val visibleRange = visibleEnd - visibleStart
    val labels = if (scale > 2f) TIME_LABELS_FINE else TIME_LABELS_COARSE
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(X_AXIS_HEIGHT),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }

        for ((label, fraction) in labels) {
            if (fraction in visibleStart..visibleEnd) {
                val xFraction = (fraction - visibleStart) / visibleRange
                val xOffsetPx = (xFraction * widthPx).toInt()

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.offset { IntOffset(xOffsetPx - 12, 0) },
                )
            }
        }
    }
}

/**
 * Compact legend row showing what each line color represents.
 */
@Composable
private fun StepGraphLegend(
    maxCumulative: Int,
    maxBucket: Int,
    cumulativeColor: Color,
    bucketColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .width(16.dp)
                    .height(3.dp),
            ) {
                drawLine(
                    color = cumulativeColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 3f,
                )
            }
            Text(
                text = "Cumulative (${formatStepCount(maxCumulative)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .width(16.dp)
                    .height(3.dp),
            ) {
                drawLine(
                    color = bucketColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = 2f,
                )
            }
            Text(
                text = "Per bucket (${formatStepCount(maxBucket)})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Bucket Size Selector ─────────────────────────────────────────────────────

/**
 * Available bucket size options for the step graph.
 */
enum class BucketSize(val label: String, val ms: Long) {
    ONE_MIN("1m", 60_000L),
    FIVE_MIN("5m", 300_000L),
    FIFTEEN_MIN("15m", 900_000L),
    THIRTY_MIN("30m", 1_800_000L),
    ONE_HOUR("1h", 3_600_000L),
}

/**
 * Row of filter chips for selecting the graph bucket size.
 *
 * @param selectedMs     Currently selected bucket size in milliseconds.
 * @param onBucketSelected Callback when a bucket size is selected.
 * @param modifier       Optional modifier.
 */
@Composable
fun BucketSizeSelector(
    selectedMs: Long,
    onBucketSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        BucketSize.entries.forEach { bucket ->
            FilterChip(
                selected = selectedMs == bucket.ms,
                onClick = { onBucketSelected(bucket.ms) },
                label = { Text(bucket.label) },
            )
        }
    }
}

// ─── Preview functions ─────────────────────────────────────────────────────────

/** Preview: step graph with sample data. */
@Preview(showBackground = true, name = "StepGraph — Sample data")
@Composable
private fun PreviewStepGraphSampleData() {
    val dayStart = 0L
    val dayEnd = 86_400_000L
    val hour = 3_600_000L
    val bucketMs = 300_000L // 5 min

    // Generate sample windows
    val windows = (0 until (18 * 3600 / 30)).map { i ->
        val ts = dayStart + 6 * hour + i * 30_000L
        val steps = when {
            ts in (9 * hour)..(10 * hour) -> 3
            ts in (10 * hour)..(11 * hour) -> 1
            ts in (14 * hour)..(15 * hour) -> 4
            else -> 0
        }
        StepWindowPoint(
            id = i.toLong(),
            timestamp = ts,
            stepCount = steps,
        )
    }

    val data = buildStepGraphData(windows, bucketMs, dayStart, dayEnd)

    PodometerTheme(dynamicColor = false) {
        StepGraph(
            graphData = data,
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: empty step graph with no data. */
@Preview(showBackground = true, name = "StepGraph — Empty")
@Composable
private fun PreviewStepGraphEmpty() {
    val data = StepGraphData(
        points = emptyList(),
        maxCumulative = 0,
        maxBucket = 0,
    )

    PodometerTheme(dynamicColor = false) {
        StepGraph(
            graphData = data,
            dayStartMillis = 0L,
            dayEndMillis = 86_400_000L,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: bucket size selector with 5-minute selected. */
@Preview(showBackground = true, name = "BucketSizeSelector")
@Composable
private fun PreviewBucketSizeSelector() {
    PodometerTheme(dynamicColor = false) {
        BucketSizeSelector(
            selectedMs = 300_000L,
            onBucketSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
