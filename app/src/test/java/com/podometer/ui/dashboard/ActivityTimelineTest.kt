// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.TransitionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ActivityTimeline] pure-logic helpers.
 *
 * These tests exercise [buildTimelineSegments] and [timelineContentDescription],
 * which are pure-Kotlin functions that can be run on the JVM without Compose or
 * an Android device.
 */
class ActivityTimelineTest {

    // ─── Helper ──────────────────────────────────────────────────────────────

    /** Returns epoch millis for a given hour (0–23) within an arbitrary fixed day. */
    private fun hoursToMillis(dayStartMillis: Long, hours: Float): Long =
        dayStartMillis + (hours * 60 * 60 * 1000).toLong()

    private val dayStart = 0L
    private val dayEnd = 24L * 60 * 60 * 1000L // 24 hours in millis
    private val now = 12L * 60 * 60 * 1000L    // noon

    // ─── buildTimelineSegments — empty transitions ────────────────────────────

    @Test
    fun `buildTimelineSegments with empty transitions returns single gray segment`() {
        val segments = buildTimelineSegments(
            transitions = emptyList(),
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = now,
        )

        assertEquals(1, segments.size)
        assertEquals(ActivityState.STILL, segments[0].activity)
        assertEquals(0f, segments[0].startFraction, 0.001f)
        assertEquals(1f, segments[0].endFraction, 0.001f)
    }

    // ─── buildTimelineSegments — single transition ────────────────────────────

    @Test
    fun `buildTimelineSegments with single transition at noon returns two segments`() {
        val transitionAt6h = TransitionEvent(
            id = 1,
            timestamp = hoursToMillis(dayStart, 6f),
            fromActivity = ActivityState.STILL,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        )

        val segments = buildTimelineSegments(
            transitions = listOf(transitionAt6h),
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = now,
        )

        assertEquals(2, segments.size)

        // First segment: midnight to 6am, activity is fromActivity (STILL)
        assertEquals(ActivityState.STILL, segments[0].activity)
        assertEquals(0f, segments[0].startFraction, 0.001f)
        assertEquals(0.25f, segments[0].endFraction, 0.001f)  // 6h / 24h = 0.25

        // Second segment: 6am to noon (now), activity is toActivity (WALKING)
        assertEquals(ActivityState.WALKING, segments[1].activity)
        assertEquals(0.25f, segments[1].startFraction, 0.001f)
        assertEquals(0.5f, segments[1].endFraction, 0.001f)   // 12h / 24h = 0.5
    }

    // ─── buildTimelineSegments — multiple transitions ─────────────────────────

    @Test
    fun `buildTimelineSegments with multiple transitions returns correct segment count`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1,
                timestamp = hoursToMillis(dayStart, 6f),
                fromActivity = ActivityState.STILL,
                toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 2,
                timestamp = hoursToMillis(dayStart, 9f),
                fromActivity = ActivityState.WALKING,
                toActivity = ActivityState.CYCLING,
                isManualOverride = false,
            ),
        )

        val segments = buildTimelineSegments(
            transitions = transitions,
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = now,
        )

        // Expect 3 segments: STILL(0–6h), WALKING(6–9h), CYCLING(9–12h/now)
        assertEquals(3, segments.size)
        assertEquals(ActivityState.STILL, segments[0].activity)
        assertEquals(ActivityState.WALKING, segments[1].activity)
        assertEquals(ActivityState.CYCLING, segments[2].activity)
    }

    @Test
    fun `buildTimelineSegments middle segment spans between two transitions`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1,
                timestamp = hoursToMillis(dayStart, 6f),
                fromActivity = ActivityState.STILL,
                toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 2,
                timestamp = hoursToMillis(dayStart, 9f),
                fromActivity = ActivityState.WALKING,
                toActivity = ActivityState.CYCLING,
                isManualOverride = false,
            ),
        )

        val segments = buildTimelineSegments(
            transitions = transitions,
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = now,
        )

        // Middle segment: WALKING from 6h to 9h → fractions 0.25 to 0.375
        assertEquals(0.25f, segments[1].startFraction, 0.001f)
        assertEquals(0.375f, segments[1].endFraction, 0.001f)  // 9h / 24h = 0.375
    }

    // ─── buildTimelineSegments — last segment ends at nowMillis or dayEnd ─────

    @Test
    fun `buildTimelineSegments last segment ends at nowMillis when now is before dayEnd`() {
        val transitionAt6h = TransitionEvent(
            id = 1,
            timestamp = hoursToMillis(dayStart, 6f),
            fromActivity = ActivityState.STILL,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        )

        val customNow = hoursToMillis(dayStart, 18f) // 6pm

        val segments = buildTimelineSegments(
            transitions = listOf(transitionAt6h),
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = customNow,
        )

        // Last segment should end at 18h/24h = 0.75
        assertEquals(0.75f, segments.last().endFraction, 0.001f)
    }

    @Test
    fun `buildTimelineSegments last segment ends at dayEnd when now is at or past dayEnd`() {
        val transitionAt6h = TransitionEvent(
            id = 1,
            timestamp = hoursToMillis(dayStart, 6f),
            fromActivity = ActivityState.STILL,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        )

        val segments = buildTimelineSegments(
            transitions = listOf(transitionAt6h),
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = dayEnd + 1000L, // past end of day
        )

        // Last segment should end at 1.0
        assertEquals(1f, segments.last().endFraction, 0.001f)
    }

    // ─── buildTimelineSegments — segment fractions are in bounds ─────────────

    @Test
    fun `buildTimelineSegments all segment fractions are within 0 to 1`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1,
                timestamp = hoursToMillis(dayStart, 7f),
                fromActivity = ActivityState.STILL,
                toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 2,
                timestamp = hoursToMillis(dayStart, 14f),
                fromActivity = ActivityState.WALKING,
                toActivity = ActivityState.STILL,
                isManualOverride = false,
            ),
        )

        val segments = buildTimelineSegments(
            transitions = transitions,
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = now,
        )

        segments.forEach { seg ->
            assertTrue("startFraction ${seg.startFraction} should be >= 0", seg.startFraction >= 0f)
            assertTrue("endFraction ${seg.endFraction} should be <= 1", seg.endFraction <= 1f)
            assertTrue(
                "startFraction should be <= endFraction",
                seg.startFraction <= seg.endFraction,
            )
        }
    }

    // ─── buildTimelineSegments — consecutive segments are contiguous ──────────

    @Test
    fun `buildTimelineSegments segments are contiguous with no gaps`() {
        val transitions = listOf(
            TransitionEvent(
                id = 1,
                timestamp = hoursToMillis(dayStart, 8f),
                fromActivity = ActivityState.STILL,
                toActivity = ActivityState.WALKING,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 2,
                timestamp = hoursToMillis(dayStart, 11f),
                fromActivity = ActivityState.WALKING,
                toActivity = ActivityState.CYCLING,
                isManualOverride = false,
            ),
        )

        val segments = buildTimelineSegments(
            transitions = transitions,
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = now,
        )

        // Each segment's endFraction should equal the next segment's startFraction
        for (i in 0 until segments.size - 1) {
            assertEquals(
                "Segment $i endFraction should equal segment ${i + 1} startFraction",
                segments[i].endFraction,
                segments[i + 1].startFraction,
                0.001f,
            )
        }
    }

    // ─── buildTimelineSegments — all-walking scenario ─────────────────────────

    @Test
    fun `buildTimelineSegments single walking transition from midnight`() {
        val transition = TransitionEvent(
            id = 1,
            timestamp = dayStart, // transition at start of day
            fromActivity = ActivityState.WALKING,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        )

        val segments = buildTimelineSegments(
            transitions = listOf(transition),
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = dayEnd,
        )

        // First segment from dayStart to transition (zero duration), second from transition to dayEnd
        // The first segment may be empty (0 duration) but list should have the last segment correct
        assertTrue("Should have at least one segment", segments.isNotEmpty())
    }

    // ─── timelineContentDescription ───────────────────────────────────────────

    @Test
    fun `timelineContentDescription with empty segments returns no data message`() {
        val description = timelineContentDescription(emptyList())
        assertTrue(
            "Empty segments description should mention 'no activity'",
            description.contains("no activity", ignoreCase = true),
        )
    }

    @Test
    fun `timelineContentDescription with single still segment returns still description`() {
        val segments = listOf(
            TimelineSegment(
                startFraction = 0f,
                endFraction = 1f,
                activity = ActivityState.STILL,
            ),
        )
        val description = timelineContentDescription(segments)
        assertTrue(
            "Description should mention 'still': $description",
            description.contains("still", ignoreCase = true),
        )
    }

    @Test
    fun `timelineContentDescription with walking segment includes walking`() {
        val segments = listOf(
            TimelineSegment(
                startFraction = 0f,
                endFraction = 0.5f,
                activity = ActivityState.WALKING,
            ),
            TimelineSegment(
                startFraction = 0.5f,
                endFraction = 1f,
                activity = ActivityState.STILL,
            ),
        )
        val description = timelineContentDescription(segments)
        assertTrue(
            "Description should mention 'walking': $description",
            description.contains("walking", ignoreCase = true),
        )
    }

    @Test
    fun `timelineContentDescription with all three activities mentions all three`() {
        val segments = listOf(
            TimelineSegment(startFraction = 0f, endFraction = 0.33f, activity = ActivityState.STILL),
            TimelineSegment(startFraction = 0.33f, endFraction = 0.66f, activity = ActivityState.WALKING),
            TimelineSegment(startFraction = 0.66f, endFraction = 1f, activity = ActivityState.CYCLING),
        )
        val description = timelineContentDescription(segments)
        assertTrue("Description should mention 'still': $description", description.contains("still", ignoreCase = true))
        assertTrue("Description should mention 'walking': $description", description.contains("walking", ignoreCase = true))
        assertTrue("Description should mention 'cycling': $description", description.contains("cycling", ignoreCase = true))
    }

    // ─── ActivityTimelineKt class existence ───────────────────────────────────

    @Test
    fun `ActivityTimelineKt exists in com_podometer_ui_dashboard package`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.ActivityTimelineKt")
        assertTrue(
            "ActivityTimelineKt should exist in com.podometer.ui.dashboard",
            clazz.name.startsWith("com.podometer.ui.dashboard"),
        )
    }

    @Test
    fun `buildTimelineSegments function is accessible as top-level function via reflection`() {
        val clazz = Class.forName("com.podometer.ui.dashboard.ActivityTimelineKt")
        val method = clazz.getDeclaredMethod(
            "buildTimelineSegments",
            List::class.java,
            Long::class.java,
            Long::class.java,
            Long::class.java,
        )
        @Suppress("UNCHECKED_CAST")
        val result = method.invoke(null, emptyList<TransitionEvent>(), dayStart, dayEnd, now) as List<*>
        assertEquals(1, result.size)
    }
}
