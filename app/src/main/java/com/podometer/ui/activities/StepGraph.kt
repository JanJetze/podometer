// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.data.db.SensorWindow
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import com.podometer.ui.dashboard.activityLabel
import com.podometer.ui.dashboard.formatActivityDuration
import com.podometer.ui.dashboard.formatActivityRange
import com.podometer.ui.dashboard.formatActivityTime
import com.podometer.ui.dashboard.formatStepCount
import com.podometer.ui.theme.ActivityColors
import com.podometer.ui.theme.LocalActivityColors
import com.podometer.ui.theme.PodometerTheme

// ─── Data model ───────────────────────────────────────────────────────────────

/**
 * A single data point in the step graph, representing one time bucket.
 *
 * @property bucketStartMillis Epoch milliseconds of the bucket start.
 * @property cumulativeSteps   Running total of steps up to and including this bucket.
 * @property bucketSteps       Number of steps in this bucket alone.
 * @property dominantActivity  The most common activity state during this bucket.
 */
data class StepGraphPoint(
    val bucketStartMillis: Long,
    val cumulativeSteps: Int,
    val bucketSteps: Int,
    val dominantActivity: ActivityState,
)

/**
 * Describes a colored region behind the graph corresponding to an activity session.
 *
 * @property startFraction Fraction [0, 1] of the day where the region starts.
 * @property endFraction   Fraction [0, 1] of the day where the region ends.
 * @property activity      The activity type for coloring.
 */
data class ActivityRegion(
    val startFraction: Float,
    val endFraction: Float,
    val activity: ActivityState,
)

/**
 * A vertical marker at an activity session boundary on the graph.
 *
 * @property fraction    Position as a day fraction [0, 1].
 * @property activity    The activity that starts (for start markers) or ends (for end markers).
 * @property isStart     True for session start, false for session end.
 * @property sessionIndex Index into the sessions list for highlight linking.
 */
data class ActivityMarker(
    val fraction: Float,
    val activity: ActivityState,
    val isStart: Boolean,
    val sessionIndex: Int,
)

/**
 * Complete data for rendering the step graph.
 *
 * @property points          Time-ordered list of graph data points.
 * @property maxCumulative   Maximum cumulative step count (for left Y-axis scaling).
 * @property maxBucket       Maximum per-bucket step count (for right Y-axis scaling).
 * @property activityRegions Colored background regions for activity sessions.
 * @property markers         Vertical marker lines at activity session boundaries.
 */
data class StepGraphData(
    val points: List<StepGraphPoint>,
    val maxCumulative: Int,
    val maxBucket: Int,
    val activityRegions: List<ActivityRegion>,
    val markers: List<ActivityMarker> = emptyList(),
)

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Builds [StepGraphData] from raw sensor windows and activity sessions.
 *
 * Groups windows into time buckets of [bucketSizeMs] duration, computes cumulative
 * and per-bucket step counts, determines the dominant activity per bucket, and
 * builds activity regions from sessions.
 *
 * @param windows      Chronologically ordered sensor windows for a single day.
 * @param sessions     Activity sessions for the same day.
 * @param bucketSizeMs Size of each time bucket in milliseconds.
 * @param dayStartMillis Start of the day in epoch millis.
 * @param dayEndMillis   End of the day in epoch millis.
 * @return [StepGraphData] ready for rendering.
 */
fun buildStepGraphData(
    windows: List<SensorWindow>,
    sessions: List<ActivitySession>,
    bucketSizeMs: Long,
    dayStartMillis: Long,
    dayEndMillis: Long,
): StepGraphData {
    if (windows.isEmpty()) {
        val regions = buildActivityRegions(sessions, dayStartMillis, dayEndMillis)
        val markers = buildActivityMarkers(sessions, dayStartMillis, dayEndMillis)
        return StepGraphData(
            points = emptyList(),
            maxCumulative = 0,
            maxBucket = 0,
            activityRegions = regions,
            markers = markers,
        )
    }

    // Group windows into buckets
    val buckets = mutableMapOf<Long, MutableList<SensorWindow>>()
    for (window in windows) {
        val bucketStart = ((window.timestamp - dayStartMillis) / bucketSizeMs) * bucketSizeMs + dayStartMillis
        buckets.getOrPut(bucketStart) { mutableListOf() }.add(window)
    }

    val sortedBucketStarts = buckets.keys.sorted()
    var cumulativeSteps = 0
    val points = sortedBucketStarts.map { bucketStart ->
        val bucketWindows = buckets[bucketStart]!!
        val bucketSteps = bucketWindows.sumOf { it.stepCount }
        cumulativeSteps += bucketSteps
        val dominantActivity = determineDominantActivity(
            bucketStart, bucketStart + bucketSizeMs, sessions,
        )
        StepGraphPoint(
            bucketStartMillis = bucketStart,
            cumulativeSteps = cumulativeSteps,
            bucketSteps = bucketSteps,
            dominantActivity = dominantActivity,
        )
    }

    val maxCumulative = points.maxOfOrNull { it.cumulativeSteps } ?: 0
    val maxBucket = points.maxOfOrNull { it.bucketSteps } ?: 0
    val regions = buildActivityRegions(sessions, dayStartMillis, dayEndMillis)

    val markers = buildActivityMarkers(sessions, dayStartMillis, dayEndMillis)

    return StepGraphData(
        points = points,
        maxCumulative = maxCumulative,
        maxBucket = maxBucket,
        activityRegions = regions,
        markers = markers,
    )
}

/**
 * Determines the dominant activity for a time range by finding which session
 * covers the most time within that range.
 *
 * @param rangeStartMs Start of the range in epoch millis.
 * @param rangeEndMs   End of the range in epoch millis.
 * @param sessions     Activity sessions to check against.
 * @return The activity state with the most overlap, or [ActivityState.STILL] if none.
 */
internal fun determineDominantActivity(
    rangeStartMs: Long,
    rangeEndMs: Long,
    sessions: List<ActivitySession>,
): ActivityState {
    var maxOverlap = 0L
    var dominant = ActivityState.STILL
    for (session in sessions) {
        val sessionEnd = session.endTime ?: Long.MAX_VALUE
        val overlapStart = maxOf(rangeStartMs, session.startTime)
        val overlapEnd = minOf(rangeEndMs, sessionEnd)
        val overlap = overlapEnd - overlapStart
        if (overlap > maxOverlap) {
            maxOverlap = overlap
            dominant = session.activity
        }
    }
    return dominant
}

/**
 * Builds colored activity regions from sessions for the graph background.
 *
 * STILL sessions are excluded — only WALKING and CYCLING produce visible regions.
 *
 * @param sessions       Activity sessions for the day.
 * @param dayStartMillis Start of the day in epoch millis.
 * @param dayEndMillis   End of the day in epoch millis.
 * @return List of [ActivityRegion]s for non-STILL sessions.
 */
internal fun buildActivityRegions(
    sessions: List<ActivitySession>,
    dayStartMillis: Long,
    dayEndMillis: Long,
): List<ActivityRegion> {
    val dayDuration = (dayEndMillis - dayStartMillis).toFloat()
    if (dayDuration <= 0f) return emptyList()

    return sessions
        .filter { it.activity != ActivityState.STILL }
        .map { session ->
            val sessionEnd = session.endTime ?: dayEndMillis
            ActivityRegion(
                startFraction = ((session.startTime - dayStartMillis).toFloat() / dayDuration).coerceIn(0f, 1f),
                endFraction = ((sessionEnd - dayStartMillis).toFloat() / dayDuration).coerceIn(0f, 1f),
                activity = session.activity,
            )
        }
        .filter { it.endFraction > it.startFraction }
}

/**
 * Builds vertical marker lines at every activity session boundary.
 *
 * Each non-STILL session produces a start marker and (if closed) an end marker.
 * Markers are used to draw thin vertical lines and to detect tap-near-marker
 * interactions.
 *
 * @param sessions       Activity sessions for the day.
 * @param dayStartMillis Start of the day in epoch millis.
 * @param dayEndMillis   End of the day in epoch millis.
 * @return List of [ActivityMarker]s sorted by fraction.
 */
internal fun buildActivityMarkers(
    sessions: List<ActivitySession>,
    dayStartMillis: Long,
    dayEndMillis: Long,
): List<ActivityMarker> {
    val dayDuration = (dayEndMillis - dayStartMillis).toFloat()
    if (dayDuration <= 0f) return emptyList()

    val markers = mutableListOf<ActivityMarker>()
    sessions.forEachIndexed { index, session ->
        if (session.activity == ActivityState.STILL) return@forEachIndexed

        markers.add(
            ActivityMarker(
                fraction = ((session.startTime - dayStartMillis) / dayDuration).coerceIn(0f, 1f),
                activity = session.activity,
                isStart = true,
                sessionIndex = index,
            ),
        )
        if (session.endTime != null) {
            markers.add(
                ActivityMarker(
                    fraction = ((session.endTime - dayStartMillis) / dayDuration).coerceIn(0f, 1f),
                    activity = session.activity,
                    isStart = false,
                    sessionIndex = index,
                ),
            )
        }
    }
    return markers.sortedBy { it.fraction }
}

/**
 * Finds the nearest [ActivityMarker] within [thresholdFraction] of [tapFraction].
 *
 * @param markers           List of markers to search.
 * @param tapFraction       Day fraction where the user tapped.
 * @param thresholdFraction Maximum distance for a marker to be considered "near".
 * @return The nearest marker, or null if none is close enough.
 */
internal fun findNearestMarker(
    markers: List<ActivityMarker>,
    tapFraction: Float,
    thresholdFraction: Float,
): ActivityMarker? {
    return markers
        .filter { kotlin.math.abs(it.fraction - tapFraction) <= thresholdFraction }
        .minByOrNull { kotlin.math.abs(it.fraction - tapFraction) }
}

// ─── Composable ───────────────────────────────────────────────────────────────

/** Left/right padding for Y-axis labels in dp. */
private val Y_AXIS_WIDTH = 48.dp

/** Bottom padding for X-axis labels in dp. */
private val X_AXIS_HEIGHT = 20.dp

/** Height of the chart canvas area. */
private val CHART_HEIGHT = 200.dp

/** Semi-transparent alpha for activity region backgrounds. */
private const val REGION_ALPHA = 0.15f

/** Alpha for the crosshair line. */
private const val CROSSHAIR_ALPHA = 0.6f

/** Alpha for highlighted activity region. */
private const val HIGHLIGHT_ALPHA = 0.35f

/** Alpha for session boundary marker lines. */
private const val MARKER_ALPHA = 0.5f

/** Fraction of the visible range within which a tap snaps to a marker. */
private const val MARKER_TAP_THRESHOLD = 0.02f

/** Time labels for the X-axis. */
private val TIME_LABELS = listOf(
    "6am" to 6f / 24f,
    "9am" to 9f / 24f,
    "12pm" to 12f / 24f,
    "3pm" to 15f / 24f,
    "6pm" to 18f / 24f,
    "9pm" to 21f / 24f,
)

/**
 * Dual-axis step line graph showing cumulative and per-bucket step counts.
 *
 * Features:
 * - Left Y-axis: cumulative step count (monotonically increasing line)
 * - Right Y-axis: per-bucket step count (spiky intensity line)
 * - Activity session colored background regions
 * - Tap to show crosshair with tooltip
 * - Horizontal pan and pinch-to-zoom on the time axis
 * - Double-tap to reset zoom
 *
 * @param graphData               Pre-computed [StepGraphData] to render.
 * @param sessions               Activity sessions for marker tooltip detail.
 * @param dayStartMillis          Start of the day in epoch millis for time calculations.
 * @param dayEndMillis            End of the day in epoch millis.
 * @param highlightedSessionIndex Index of the session to highlight, or -1 for none.
 * @param onSessionHighlight      Callback when a session is highlighted via marker tap.
 * @param modifier                Optional [Modifier] applied to the root [Column].
 */
@Composable
fun StepGraph(
    graphData: StepGraphData,
    sessions: List<ActivitySession>,
    dayStartMillis: Long,
    dayEndMillis: Long,
    highlightedSessionIndex: Int = -1,
    onSessionHighlight: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activityColors = LocalActivityColors.current
    val cumulativeLineColor = MaterialTheme.colorScheme.primary
    val bucketLineColor = MaterialTheme.colorScheme.tertiary
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurface
    val markerColor = MaterialTheme.colorScheme.onSurface

    // Marker/session state
    var nearMarker by remember { mutableStateOf<ActivityMarker?>(null) }

    // Zoom/pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetFraction by remember { mutableFloatStateOf(0f) }

    // Crosshair state
    var crosshairFraction by remember { mutableStateOf<Float?>(null) }

    val dayDuration = (dayEndMillis - dayStartMillis).toFloat()

    Column(modifier = modifier) {
        Box {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Update scale (horizontal zoom only)
                            val newScale = (scale * zoom).coerceIn(1f, 10f)
                            // Adjust offset to keep center stable during zoom
                            val panFraction = pan.x / size.width / scale
                            val newOffset = (offsetFraction - panFraction)
                                .coerceIn(0f, 1f - 1f / newScale)
                            scale = newScale
                            offsetFraction = newOffset
                            // Dismiss crosshair on pan/zoom
                            crosshairFraction = null
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                // Convert tap position to day fraction
                                val chartWidth = size.width.toFloat()
                                val tapFraction = offset.x / chartWidth
                                val dayFraction = offsetFraction + tapFraction / scale
                                val clampedFraction = dayFraction.coerceIn(0f, 1f)

                                // Check if tap is near a marker
                                val visRange = 1f / scale
                                val threshold = MARKER_TAP_THRESHOLD * visRange
                                val marker = findNearestMarker(
                                    graphData.markers, clampedFraction, threshold,
                                )
                                nearMarker = marker
                                crosshairFraction = if (marker != null) {
                                    onSessionHighlight(marker.sessionIndex)
                                    marker.fraction
                                } else {
                                    clampedFraction
                                }
                            },
                            onDoubleTap = {
                                // Reset zoom
                                scale = 1f
                                offsetFraction = 0f
                                crosshairFraction = null
                                nearMarker = null
                            },
                        )
                    },
            ) {
                val chartWidth = size.width
                val chartHeight = size.height

                // Visible range in day fractions
                val visibleStart = offsetFraction
                val visibleEnd = (offsetFraction + 1f / scale).coerceAtMost(1f)
                val visibleRange = visibleEnd - visibleStart

                if (visibleRange <= 0f) return@Canvas

                // Helper: day fraction to x pixel
                fun fractionToX(f: Float): Float =
                    ((f - visibleStart) / visibleRange) * chartWidth

                // Draw activity regions
                for (region in graphData.activityRegions) {
                    val x1 = fractionToX(region.startFraction).coerceIn(0f, chartWidth)
                    val x2 = fractionToX(region.endFraction).coerceIn(0f, chartWidth)
                    if (x2 > x1) {
                        drawRect(
                            color = region.activity.regionColor(activityColors),
                            topLeft = Offset(x1, 0f),
                            size = Size(x2 - x1, chartHeight),
                            alpha = REGION_ALPHA,
                        )
                    }
                }

                // Draw highlighted session region
                if (highlightedSessionIndex in graphData.activityRegions.indices) {
                    val region = graphData.activityRegions[highlightedSessionIndex]
                    val x1 = fractionToX(region.startFraction).coerceIn(0f, chartWidth)
                    val x2 = fractionToX(region.endFraction).coerceIn(0f, chartWidth)
                    if (x2 > x1) {
                        drawRect(
                            color = region.activity.regionColor(activityColors),
                            topLeft = Offset(x1, 0f),
                            size = Size(x2 - x1, chartHeight),
                            alpha = HIGHLIGHT_ALPHA,
                        )
                    }
                }

                // Draw session boundary markers
                for (marker in graphData.markers) {
                    val mx = fractionToX(marker.fraction)
                    if (mx in 0f..chartWidth) {
                        drawLine(
                            color = markerColor,
                            start = Offset(mx, 0f),
                            end = Offset(mx, chartHeight),
                            strokeWidth = 1.5f,
                            alpha = MARKER_ALPHA,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                        )
                    }
                }

                // Draw horizontal grid lines (3 lines for each axis)
                val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                for (i in 1..3) {
                    val y = chartHeight * (1f - i / 4f)
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f,
                        pathEffect = gridDash,
                    )
                }

                if (graphData.points.isEmpty()) return@Canvas

                // Draw cumulative line (left axis)
                if (graphData.maxCumulative > 0) {
                    val cumulativePath = Path()
                    var started = false
                    for (point in graphData.points) {
                        val fraction = (point.bucketStartMillis - dayStartMillis) / dayDuration
                        val x = fractionToX(fraction)
                        val y = chartHeight * (1f - point.cumulativeSteps.toFloat() / graphData.maxCumulative)
                        if (!started) {
                            cumulativePath.moveTo(x, y)
                            started = true
                        } else {
                            cumulativePath.lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = cumulativePath,
                        color = cumulativeLineColor,
                        style = Stroke(width = 3f),
                    )
                }

                // Draw bucket line (right axis)
                if (graphData.maxBucket > 0) {
                    val bucketPath = Path()
                    var started = false
                    for (point in graphData.points) {
                        val fraction = (point.bucketStartMillis - dayStartMillis) / dayDuration
                        val x = fractionToX(fraction)
                        val y = chartHeight * (1f - point.bucketSteps.toFloat() / graphData.maxBucket)
                        if (!started) {
                            bucketPath.moveTo(x, y)
                            started = true
                        } else {
                            bucketPath.lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = bucketPath,
                        color = bucketLineColor,
                        style = Stroke(width = 2f),
                    )
                }

                // Draw crosshair
                val cf = crosshairFraction
                if (cf != null) {
                    val cx = fractionToX(cf)
                    if (cx in 0f..chartWidth) {
                        drawLine(
                            color = crosshairColor,
                            start = Offset(cx, 0f),
                            end = Offset(cx, chartHeight),
                            strokeWidth = 2f,
                            alpha = CROSSHAIR_ALPHA,
                        )
                    }
                }
            }

            // Tooltip overlay
            val cf = crosshairFraction
            if (cf != null) {
                val markerSession = nearMarker?.let { m ->
                    sessions.getOrNull(m.sessionIndex)
                }
                if (markerSession != null) {
                    // Show session detail tooltip when tapping near a marker
                    MarkerTooltip(
                        session = markerSession,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                } else if (graphData.points.isNotEmpty()) {
                    // Show regular crosshair tooltip
                    val targetMillis = dayStartMillis + (cf * dayDuration).toLong()
                    val nearestPoint = graphData.points.minByOrNull {
                        kotlin.math.abs(it.bucketStartMillis - targetMillis)
                    }
                    if (nearestPoint != null) {
                        CrosshairTooltip(
                            point = nearestPoint,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }

        // X-axis time labels
        StepGraphTimeLabels(
            scale = scale,
            offsetFraction = offsetFraction,
        )

        // Y-axis legend
        StepGraphLegend(
            maxCumulative = graphData.maxCumulative,
            maxBucket = graphData.maxBucket,
            cumulativeColor = cumulativeLineColor,
            bucketColor = bucketLineColor,
        )
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
 * Tooltip card shown when tapping near an activity session boundary marker.
 *
 * Shows the activity type, time range, duration, and step count.
 */
@Composable
private fun MarkerTooltip(
    session: ActivitySession,
    modifier: Modifier = Modifier,
) {
    val durationMs = if (session.endTime != null) session.endTime - session.startTime else null
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        modifier = modifier.padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = activityLabel(session.activity),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = formatActivityRange(session.startTime, session.endTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatActivityDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.stepCount > 0) {
                Text(
                    text = "${formatStepCount(session.stepCount)} steps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Row of time labels below the graph, adjusting for zoom/pan state.
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(X_AXIS_HEIGHT),
    ) {
        for ((label, fraction) in TIME_LABELS) {
            if (fraction in visibleStart..visibleEnd) {
                val xFraction = (fraction - visibleStart) / visibleRange
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = (xFraction * 300).dp.coerceAtMost(280.dp)),
                    // Note: using fillMaxWidth with a fraction offset would be
                    // more precise, but this simple approach works well enough
                    // for the fixed set of labels.
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
            Canvas(modifier = Modifier.padding(end = 4.dp).height(2.dp).padding(horizontal = 0.dp)) {
                drawLine(
                    color = cumulativeColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(12.dp.toPx(), size.height / 2),
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
            Canvas(modifier = Modifier.padding(end = 4.dp).height(2.dp)) {
                drawLine(
                    color = bucketColor,
                    start = Offset(0f, size.height / 2),
                    end = Offset(12.dp.toPx(), size.height / 2),
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

/**
 * Returns the background region color for an [ActivityState].
 */
private fun ActivityState.regionColor(colors: ActivityColors): Color = when (this) {
    ActivityState.WALKING -> colors.walking
    ActivityState.CYCLING -> colors.cycling
    ActivityState.STILL -> colors.still
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

/** Preview: step graph with sample data showing walking and cycling sessions. */
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
        SensorWindow(
            id = i.toLong(),
            timestamp = ts,
            magnitudeVariance = 0.0,
            stepFrequencyHz = 0.0,
            stepCount = steps,
        )
    }

    val sessions = listOf(
        ActivitySession(
            activity = ActivityState.WALKING,
            startTime = 9 * hour,
            endTime = 10 * hour,
            startTransitionId = 1,
            isManualOverride = false,
            stepCount = 360,
        ),
        ActivitySession(
            activity = ActivityState.CYCLING,
            startTime = 10 * hour,
            endTime = 11 * hour,
            startTransitionId = 2,
            isManualOverride = false,
        ),
        ActivitySession(
            activity = ActivityState.WALKING,
            startTime = 14 * hour,
            endTime = 15 * hour,
            startTransitionId = 3,
            isManualOverride = false,
            stepCount = 480,
        ),
    )

    val data = buildStepGraphData(windows, sessions, bucketMs, dayStart, dayEnd)

    PodometerTheme(dynamicColor = false) {
        StepGraph(
            graphData = data,
            sessions = sessions,
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
        activityRegions = emptyList(),
    )

    PodometerTheme(dynamicColor = false) {
        StepGraph(
            graphData = data,
            sessions = emptyList(),
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
