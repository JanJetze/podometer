// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.TransitionEvent
import com.podometer.ui.theme.PodometerTheme

// ─── Activity colour constants ────────────────────────────────────────────────
// Same values as in ActivityBadge.kt — redefined here to keep components independent.
// A future refactor can extract these to a shared theme location.

/** Colour for walking segments in the timeline. */
private val WalkingColor = Color(0xFF4CAF50)

/** Colour for cycling segments in the timeline. */
private val CyclingColor = Color(0xFF2196F3)

/** Colour for still/no-data segments in the timeline. */
private val StillColor = Color(0xFF9E9E9E)

/** Semi-transparent dark colour for transition marker lines. */
private val MarkerColor = Color(0x80000000)

// ─── Data model ───────────────────────────────────────────────────────────────

/**
 * Represents a single coloured segment of the activity timeline bar.
 *
 * @property startFraction Position of the segment's left edge within the bar, in [0.0, 1.0].
 * @property endFraction   Position of the segment's right edge within the bar, in [0.0, 1.0].
 * @property activity      The [ActivityState] that determines the segment colour.
 */
data class TimelineSegment(
    val startFraction: Float,
    val activity: ActivityState,
    val endFraction: Float,
)

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Converts an epoch-millisecond timestamp to a fractional position [0.0, 1.0] within a day.
 *
 * The result is clamped to [0f, 1f] so that timestamps outside the day window do not
 * produce out-of-bounds fractions.
 *
 * @param millis         The timestamp to convert.
 * @param dayStartMillis Start of the day in epoch millis (inclusive).
 * @param dayEndMillis   End of the day in epoch millis (exclusive).
 * @return Fractional position in [0.0, 1.0].
 */
internal fun timestampToFraction(
    millis: Long,
    dayStartMillis: Long,
    dayEndMillis: Long,
): Float {
    val dayDuration = (dayEndMillis - dayStartMillis).toFloat()
    if (dayDuration <= 0f) return 0f
    return ((millis - dayStartMillis).toFloat() / dayDuration).coerceIn(0f, 1f)
}

/**
 * Builds a list of [TimelineSegment]s from a list of [TransitionEvent]s.
 *
 * ### Segment building rules
 * - **No transitions**: returns a single [ActivityState.STILL] segment spanning the entire bar.
 * - **First segment**: starts at [dayStartMillis], ends at the first transition timestamp;
 *   activity is the first transition's [TransitionEvent.fromActivity].
 * - **Middle segments**: span from one transition to the next; activity is each transition's
 *   [TransitionEvent.toActivity].
 * - **Last segment**: starts at the last transition, ends at `min(nowMillis, dayEndMillis)`;
 *   activity is the last transition's [TransitionEvent.toActivity].
 *
 * Segments with zero duration (startFraction == endFraction) are omitted.
 *
 * @param transitions    Ordered list of activity transitions for the day.
 * @param dayStartMillis Start of the day in epoch millis.
 * @param dayEndMillis   End of the day in epoch millis.
 * @param nowMillis      Current time in epoch millis; determines where the last segment ends.
 * @return List of [TimelineSegment]s suitable for rendering.
 */
fun buildTimelineSegments(
    transitions: List<TransitionEvent>,
    dayStartMillis: Long,
    dayEndMillis: Long,
    nowMillis: Long,
): List<TimelineSegment> {
    val effectiveEnd = nowMillis.coerceAtMost(dayEndMillis)
    val endFraction = timestampToFraction(effectiveEnd, dayStartMillis, dayEndMillis)

    if (transitions.isEmpty()) {
        return listOf(
            TimelineSegment(
                startFraction = 0f,
                endFraction = 1f,
                activity = ActivityState.STILL,
            ),
        )
    }

    val segments = mutableListOf<TimelineSegment>()

    // First segment: day start to first transition, activity = fromActivity of first transition
    val firstTransition = transitions.first()
    val firstTransitionFraction = timestampToFraction(
        firstTransition.timestamp,
        dayStartMillis,
        dayEndMillis,
    )
    if (firstTransitionFraction > 0f) {
        segments += TimelineSegment(
            startFraction = 0f,
            endFraction = firstTransitionFraction,
            activity = firstTransition.fromActivity,
        )
    }

    // Middle segments: between consecutive transitions
    for (i in 0 until transitions.size - 1) {
        val current = transitions[i]
        val next = transitions[i + 1]
        val segStart = timestampToFraction(current.timestamp, dayStartMillis, dayEndMillis)
        val segEnd = timestampToFraction(next.timestamp, dayStartMillis, dayEndMillis)
        if (segEnd > segStart) {
            segments += TimelineSegment(
                startFraction = segStart,
                endFraction = segEnd,
                activity = current.toActivity,
            )
        }
    }

    // Last segment: from last transition to nowMillis (capped at dayEnd)
    val lastTransition = transitions.last()
    val lastTransitionFraction = timestampToFraction(
        lastTransition.timestamp,
        dayStartMillis,
        dayEndMillis,
    )
    if (endFraction > lastTransitionFraction) {
        segments += TimelineSegment(
            startFraction = lastTransitionFraction,
            endFraction = endFraction,
            activity = lastTransition.toActivity,
        )
    }

    return segments
}

/**
 * Builds an accessible text summary of the timeline segments for TalkBack.
 *
 * Returns a sentence describing each segment's activity and its position within the day
 * as a percentage range.
 *
 * This is a plain Kotlin function (no `@Composable`, no `stringResource`) intentionally
 * kept for JVM unit testability — the same pattern used in ActivityBadge.kt for
 * [ActivityState.displayText] and [ActivityState.contentDescriptionText]. The
 * [ActivityTimeline] composable builds its content description inline via `stringResource()`
 * for proper localisation at runtime.
 *
 * @param segments The list of [TimelineSegment]s to summarise.
 * @return A human-readable English string suitable for use as a `contentDescription` in tests.
 */
fun timelineContentDescription(segments: List<TimelineSegment>): String {
    if (segments.isEmpty()) {
        return "Activity timeline: no activity data available."
    }

    val parts = segments.map { seg ->
        val activityLabel = when (seg.activity) {
            ActivityState.WALKING -> "walking"
            ActivityState.CYCLING -> "cycling"
            ActivityState.STILL -> "still"
        }
        val percentStart = (seg.startFraction * 100).toInt()
        val percentEnd = (seg.endFraction * 100).toInt()
        "$activityLabel from $percentStart% to $percentEnd% of the day"
    }

    return "Activity timeline: ${parts.joinToString("; ")}."
}

// ─── Internal colour helpers ──────────────────────────────────────────────────

/** Returns the timeline bar colour for this [ActivityState]. */
private fun ActivityState.timelineColor(): Color = when (this) {
    ActivityState.WALKING -> WalkingColor
    ActivityState.CYCLING -> CyclingColor
    ActivityState.STILL -> StillColor
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Horizontal activity timeline bar that spans a full day.
 *
 * Renders colour-coded segments for each detected activity period:
 * - Green for [ActivityState.WALKING]
 * - Blue for [ActivityState.CYCLING]
 * - Gray for [ActivityState.STILL]
 *
 * Thin vertical marker lines are drawn at every activity transition boundary.
 * Six time labels (6am, 9am, 12pm, 3pm, 6pm, 9pm) are displayed below the bar.
 *
 * This is a pure presentational composable — it holds no internal state and receives
 * all data via parameters. Use [buildTimelineSegments] to convert a [List] of
 * [TransitionEvent]s (e.g. from [DashboardUiState.transitions]) into segments.
 *
 * Accessibility: the component carries a `contentDescription` built from string resources
 * (via `stringResource()`) so TalkBack can announce a localised text summary of the timeline.
 *
 * @param segments  Pre-built list of [TimelineSegment]s to render.
 * @param modifier  Optional [Modifier] applied to the root [Column].
 */
@Composable
fun ActivityTimeline(
    segments: List<TimelineSegment>,
    modifier: Modifier = Modifier,
) {
    // Build a localised content description using string resources so TalkBack announces
    // the correct text in the user's language. The pure-Kotlin timelineContentDescription()
    // function is kept separately for JVM unit tests.
    val accessibilityText = if (segments.isEmpty()) {
        stringResource(R.string.cd_timeline_no_data)
    } else {
        val parts = segments.map { seg ->
            val label = when (seg.activity) {
                ActivityState.WALKING -> stringResource(R.string.activity_walking)
                ActivityState.CYCLING -> stringResource(R.string.activity_cycling)
                ActivityState.STILL -> stringResource(R.string.activity_still)
            }
            stringResource(
                R.string.cd_timeline_segment,
                label,
                (seg.startFraction * 100).toInt(),
                (seg.endFraction * 100).toInt(),
            )
        }
        stringResource(R.string.cd_timeline_summary, parts.joinToString("; "))
    }

    Column(
        modifier = modifier
            .semantics { contentDescription = accessibilityText },
    ) {
        ActivityTimelineBar(segments = segments)
        ActivityTimelineLabels()
    }
}

/**
 * The horizontal Canvas bar that renders segments and transition markers with rounded corners.
 *
 * The entire bar is clipped to a rounded rectangle before drawing segments, so all segment
 * colours are naturally contained within rounded bounds.
 *
 * @param segments List of [TimelineSegment]s to draw.
 */
@Composable
private fun ActivityTimelineBar(segments: List<TimelineSegment>) {
    val density = LocalDensity.current
    val cornerRadiusDp = 4.dp
    val markerWidthDp = 2.dp
    val cornerRadiusPx = with(density) { cornerRadiusDp.toPx() }
    val markerWidthPx = with(density) { markerWidthDp.toPx() }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
    ) {
        val barWidth = size.width
        val barHeight = size.height
        val barSize = Size(barWidth, barHeight)
        val cornerRadius = CornerRadius(cornerRadiusPx)

        // Clip to the rounded rectangle so all drawing stays inside rounded bounds
        val roundedPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = barWidth,
                    bottom = barHeight,
                    cornerRadius = cornerRadius,
                ),
            )
        }
        clipPath(roundedPath, clipOp = ClipOp.Intersect) {

            if (segments.isEmpty()) {
                // Draw a single gray bar if no segments
                drawRect(color = StillColor, size = barSize)
                return@clipPath
            }

            // Draw all segments
            segments.forEach { seg ->
                val segLeft = seg.startFraction * barWidth
                val segRight = seg.endFraction * barWidth
                val segWidth = (segRight - segLeft).coerceAtLeast(0f)
                if (segWidth > 0f) {
                    drawRect(
                        color = seg.activity.timelineColor(),
                        topLeft = Offset(segLeft, 0f),
                        size = Size(segWidth, barHeight),
                    )
                }
            }

            // Draw thin vertical transition markers at each segment boundary
            segments.zipWithNext().forEach { (_, next) ->
                val markerX = next.startFraction * barWidth
                if (markerX >= markerWidthPx / 2f && markerX <= barWidth - markerWidthPx / 2f) {
                    drawRect(
                        color = MarkerColor,
                        topLeft = Offset(markerX - markerWidthPx / 2f, 0f),
                        size = Size(markerWidthPx, barHeight),
                    )
                }
            }
        }
    }
}

/**
 * Row of time labels displayed below the timeline bar.
 *
 * Renders six labels: 6am, 9am, 12pm, 3pm, 6pm, 9pm — evenly distributed
 * across the width using equal [Modifier.weight].
 */
@Composable
private fun ActivityTimelineLabels() {
    val timeLabels = listOf("6am", "9am", "12pm", "3pm", "6pm", "9pm")

    Row(modifier = Modifier.fillMaxWidth()) {
        timeLabels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
            )
        }
    }
}

// ─── Preview functions ─────────────────────────────────────────────────────────

/** Preview: all walking — single green bar spanning the full day. */
@Preview(showBackground = true, name = "ActivityTimeline — All Walking")
@Composable
private fun PreviewActivityTimelineAllWalking() {
    val dayStart = 0L
    val dayEnd = 24L * 60 * 60 * 1000L
    val transitions = listOf(
        TransitionEvent(
            id = 1,
            timestamp = dayStart,
            fromActivity = ActivityState.WALKING,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        ),
    )
    val segments = buildTimelineSegments(
        transitions = transitions,
        dayStartMillis = dayStart,
        dayEndMillis = dayEnd,
        nowMillis = dayEnd,
    )
    PodometerTheme {
        ActivityTimeline(
            segments = segments,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: mixed activities — still before 6am, walking 6–9am, cycling 9am–12pm, still 12–3pm, walking 3–6pm. */
@Preview(showBackground = true, name = "ActivityTimeline — Mixed Activities")
@Composable
private fun PreviewActivityTimelineMixed() {
    val dayStart = 0L
    val dayEnd = 24L * 60 * 60 * 1000L
    val hour = 60L * 60 * 1000L

    val transitions = listOf(
        TransitionEvent(
            id = 1,
            timestamp = 6L * hour,
            fromActivity = ActivityState.STILL,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        ),
        TransitionEvent(
            id = 2,
            timestamp = 9L * hour,
            fromActivity = ActivityState.WALKING,
            toActivity = ActivityState.CYCLING,
            isManualOverride = false,
        ),
        TransitionEvent(
            id = 3,
            timestamp = 12L * hour,
            fromActivity = ActivityState.CYCLING,
            toActivity = ActivityState.STILL,
            isManualOverride = false,
        ),
        TransitionEvent(
            id = 4,
            timestamp = 15L * hour,
            fromActivity = ActivityState.STILL,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        ),
    )
    val segments = buildTimelineSegments(
        transitions = transitions,
        dayStartMillis = dayStart,
        dayEndMillis = dayEnd,
        nowMillis = 18L * hour,
    )
    PodometerTheme {
        ActivityTimeline(
            segments = segments,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: no transitions — single gray bar indicating no recorded activity. */
@Preview(showBackground = true, name = "ActivityTimeline — Empty (No Data)")
@Composable
private fun PreviewActivityTimelineEmpty() {
    val dayStart = 0L
    val dayEnd = 24L * 60 * 60 * 1000L
    val segments = buildTimelineSegments(
        transitions = emptyList(),
        dayStartMillis = dayStart,
        dayEndMillis = dayEnd,
        nowMillis = 12L * 60 * 60 * 1000L,
    )
    PodometerTheme {
        ActivityTimeline(
            segments = segments,
            modifier = Modifier.padding(16.dp),
        )
    }
}
