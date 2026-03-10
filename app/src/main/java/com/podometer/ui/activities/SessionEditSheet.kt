// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import com.podometer.ui.dashboard.activityLabel
import com.podometer.ui.dashboard.formatActivityTime
import com.podometer.ui.theme.LocalActivityColors
import com.podometer.ui.theme.PodometerTheme

/** Padding in minutes before and after the session for the zoomed view. */
private const val SESSION_PADDING_MINUTES = 5

/** Minimum touch target width for drag handles in dp. */
private val HANDLE_WIDTH = 48.dp

/** Height of the zoomed step graph in the edit sheet. */
private val EDIT_GRAPH_HEIGHT = 150.dp

/** Alpha for the selected region background. */
private const val SELECTED_REGION_ALPHA = 0.2f

/**
 * Bottom sheet content for editing or creating an activity session.
 *
 * Shows a zoomed step graph for the session's time range with draggable start/end
 * markers. Activity type chips allow reclassifying the session.
 *
 * @param session        The session being edited (or a template for a new session).
 * @param windows        Sensor windows for the day (used to draw the zoomed graph).
 * @param dayStartMillis Start of the day in epoch millis.
 * @param dayEndMillis   End of the day in epoch millis.
 * @param onSave         Callback with the edited start time, end time, and activity.
 * @param onCancel       Callback when the user cancels editing.
 * @param onDelete       Callback to delete the session (null hides the button).
 */
@Composable
fun SessionEditSheet(
    session: ActivitySession,
    windows: List<StepWindowPoint>,
    dayStartMillis: Long,
    dayEndMillis: Long,
    onSave: (startMs: Long, endMs: Long, activity: ActivityState) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val paddingMs = SESSION_PADDING_MINUTES * 60_000L
    val sessionEnd = session.effectiveEndTime()
    val viewStart = (session.startTime - paddingMs).coerceAtLeast(dayStartMillis)
    val viewEnd = (sessionEnd + paddingMs).coerceAtMost(dayEndMillis)
    val viewDuration = (viewEnd - viewStart).toFloat()

    var startFraction by remember {
        mutableFloatStateOf(((session.startTime - viewStart) / viewDuration).coerceIn(0f, 1f))
    }
    var endFraction by remember {
        mutableFloatStateOf(((sessionEnd - viewStart) / viewDuration).coerceIn(0f, 1f))
    }
    var selectedActivity by remember { mutableStateOf(session.activity) }

    val activityColors = LocalActivityColors.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val handleColor = MaterialTheme.colorScheme.primary
    val bucketLineColor = MaterialTheme.colorScheme.tertiary

    // Filter windows to the view range
    val viewWindows = remember(windows, viewStart, viewEnd) {
        windows.filter { it.timestamp in viewStart until viewEnd }
    }

    // Compute bucket steps for the zoomed view (30s buckets = raw resolution)
    val bucketMs = 30_000L
    val maxSteps = remember(viewWindows) {
        if (viewWindows.isEmpty()) 1
        else viewWindows.maxOf { it.stepCount }.coerceAtLeast(1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = if (session.isNew) "New Activity" else "Edit Activity",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Time range display
        val editStartMs = viewStart + (startFraction * viewDuration).toLong()
        val editEndMs = viewStart + (endFraction * viewDuration).toLong()
        Text(
            text = "${formatActivityTime(editStartMs)} – ${formatActivityTime(editEndMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Zoomed step graph with draggable markers
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EDIT_GRAPH_HEIGHT),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(EDIT_GRAPH_HEIGHT)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val chartWidth = size.width.toFloat()
                            val dragFraction = dragAmount / chartWidth
                            val tapX = change.position.x
                            val tapFraction = tapX / chartWidth

                            // Determine which handle to drag (closest)
                            val distToStart = kotlin.math.abs(tapFraction - startFraction)
                            val distToEnd = kotlin.math.abs(tapFraction - endFraction)

                            if (distToStart < distToEnd) {
                                startFraction = (startFraction + dragFraction)
                                    .coerceIn(0f, endFraction - 0.01f)
                            } else {
                                endFraction = (endFraction + dragFraction)
                                    .coerceIn(startFraction + 0.01f, 1f)
                            }
                        }
                    },
            ) {
                val chartWidth = size.width
                val chartHeight = size.height

                // Draw selected region background
                val regionColor = activityColors.colorFor(selectedActivity)
                val x1 = startFraction * chartWidth
                val x2 = endFraction * chartWidth
                drawRect(
                    color = regionColor,
                    topLeft = Offset(x1, 0f),
                    size = Size(x2 - x1, chartHeight),
                    alpha = SELECTED_REGION_ALPHA,
                )

                // Draw step data as a line
                if (viewWindows.isNotEmpty()) {
                    val stepPath = Path()
                    var started = false
                    for (window in viewWindows) {
                        val wFraction = (window.timestamp - viewStart) / viewDuration
                        val wx = wFraction * chartWidth
                        val wy = chartHeight * (1f - window.stepCount.toFloat() / maxSteps)
                        if (!started) {
                            stepPath.moveTo(wx, wy)
                            started = true
                        } else {
                            stepPath.lineTo(wx, wy)
                        }
                    }
                    drawPath(
                        path = stepPath,
                        color = bucketLineColor,
                        style = Stroke(width = 2f),
                    )
                }

                // Draw start handle
                drawLine(
                    color = handleColor,
                    start = Offset(x1, 0f),
                    end = Offset(x1, chartHeight),
                    strokeWidth = 4f,
                )
                // Start handle thumb
                drawCircle(
                    color = handleColor,
                    radius = 8f,
                    center = Offset(x1, chartHeight / 2),
                )

                // Draw end handle
                drawLine(
                    color = handleColor,
                    start = Offset(x2, 0f),
                    end = Offset(x2, chartHeight),
                    strokeWidth = 4f,
                )
                // End handle thumb
                drawCircle(
                    color = handleColor,
                    radius = 8f,
                    center = Offset(x2, chartHeight / 2),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Activity type selector
        Text(
            text = "Activity Type",
            style = MaterialTheme.typography.labelMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityState.entries.filter { it != ActivityState.STILL }.forEach { activity ->
                FilterChip(
                    selected = selectedActivity == activity,
                    onClick = { selectedActivity = activity },
                    label = { Text(activityLabel(activity)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (onDelete != null) {
                OutlinedButton(
                    onClick = onDelete,
                ) {
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    val newStartMs = viewStart + (startFraction * viewDuration).toLong()
                    val newEndMs = viewStart + (endFraction * viewDuration).toLong()
                    onSave(newStartMs, newEndMs, selectedActivity)
                },
            ) {
                Text("Save")
            }
        }
    }
}


// ─── Preview ────────────────────────────────────────────────────────────────

/** Preview: session edit sheet with sample walking session. */
@Preview(showBackground = true, name = "SessionEditSheet — Walking")
@Composable
private fun PreviewSessionEditSheet() {
    val hour = 3_600_000L
    val session = ActivitySession(
        activity = ActivityState.WALKING,
        startTime = 9 * hour,
        endTime = 10 * hour,
        startTransitionId = 1,
        isManualOverride = false,
        stepCount = 360,
    )

    val windows = (0 until 120).map { i ->
        StepWindowPoint(
            id = i.toLong(),
            timestamp = 9 * hour - 5 * 60_000L + i * 30_000L,
            stepCount = if (i in 10..110) (i % 5) + 1 else 0,
        )
    }

    PodometerTheme(dynamicColor = false) {
        SessionEditSheet(
            session = session,
            windows = windows,
            dayStartMillis = 0L,
            dayEndMillis = 86_400_000L,
            onSave = { _, _, _ -> },
            onCancel = {},
            onDelete = {},
        )
    }
}
