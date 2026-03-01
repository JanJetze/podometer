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
    fun `buildTimelineSegments with empty transitions returns empty list`() {
        val segments = buildTimelineSegments(
            transitions = emptyList(),
            dayStartMillis = dayStart,
            dayEndMillis = dayEnd,
            nowMillis = now,
        )

        assertTrue("Empty transitions should return empty segments", segments.isEmpty())
    }

    // ─── buildTimelineSegments — single transition ────────────────────────────

    @Test
    fun `buildTimelineSegments with single transition returns only non-STILL segments`() {
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

        // STILL segment (midnight to 6am) is filtered out; only WALKING remains
        assertEquals(1, segments.size)
        assertEquals(ActivityState.WALKING, segments[0].activity)
        assertEquals(0.25f, segments[0].startFraction, 0.001f)  // 6h / 24h = 0.25
        assertEquals(0.5f, segments[0].endFraction, 0.001f)     // 12h / 24h = 0.5
    }

    // ─── buildTimelineSegments — multiple transitions ─────────────────────────

    @Test
    fun `buildTimelineSegments with multiple transitions filters STILL segments`() {
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

        // STILL(0–6h) is filtered out; WALKING(6–9h) and CYCLING(9–12h) remain
        assertEquals(2, segments.size)
        assertEquals(ActivityState.WALKING, segments[0].activity)
        assertEquals(ActivityState.CYCLING, segments[1].activity)
    }

    @Test
    fun `buildTimelineSegments WALKING segment spans between two transitions`() {
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

        // WALKING segment: 6h to 9h → fractions 0.25 to 0.375
        val walkingSegment = segments.first { it.activity == ActivityState.WALKING }
        assertEquals(0.25f, walkingSegment.startFraction, 0.001f)
        assertEquals(0.375f, walkingSegment.endFraction, 0.001f)  // 9h / 24h = 0.375
    }

    // ─── buildTimelineSegments — STILL segments in middle are filtered ────────

    @Test
    fun `buildTimelineSegments filters STILL segments from mixed activity output`() {
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
                timestamp = hoursToMillis(dayStart, 8f),
                fromActivity = ActivityState.WALKING,
                toActivity = ActivityState.STILL,
                isManualOverride = false,
            ),
            TransitionEvent(
                id = 3,
                timestamp = hoursToMillis(dayStart, 10f),
                fromActivity = ActivityState.STILL,
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

        // STILL segments (0-6h, 8-10h) filtered; WALKING(6-8h) and CYCLING(10-12h) remain
        assertEquals(2, segments.size)
        assertTrue("All segments should be non-STILL",
            segments.all { it.activity != ActivityState.STILL })
        assertEquals(ActivityState.WALKING, segments[0].activity)
        assertEquals(ActivityState.CYCLING, segments[1].activity)
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

    // ─── buildTimelineSegments — consecutive non-STILL segments are contiguous

    @Test
    fun `buildTimelineSegments adjacent non-STILL segments are contiguous`() {
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

        // WALKING(8-11h) and CYCLING(11-12h) — adjacent and contiguous
        assertEquals(2, segments.size)
        assertEquals(
            "WALKING endFraction should equal CYCLING startFraction",
            segments[0].endFraction,
            segments[1].startFraction,
            0.001f,
        )
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

        // The transition is at dayStart, so the first segment (dayStart→dayStart) has zero duration
        // and is omitted. Only the last segment (dayStart→dayEnd, WALKING) should be present.
        assertEquals("Should have exactly 1 segment (zero-duration first segment is omitted)", 1, segments.size)
        assertEquals(ActivityState.WALKING, segments[0].activity)
        assertEquals(0f, segments[0].startFraction, 0.001f)
        assertEquals(1f, segments[0].endFraction, 0.001f)
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
    fun `timelineContentDescription with single still segment returns no data message`() {
        val segments = listOf(
            TimelineSegment(
                startFraction = 0f,
                endFraction = 1f,
                activity = ActivityState.STILL,
            ),
        )
        val description = timelineContentDescription(segments)
        assertTrue(
            "STILL-only segments should produce no-data description: $description",
            description.contains("no activity", ignoreCase = true),
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
        assertTrue(
            "Description should NOT mention 'still': $description",
            !description.contains("still", ignoreCase = true),
        )
    }

    @Test
    fun `timelineContentDescription with all three activities skips STILL`() {
        val segments = listOf(
            TimelineSegment(startFraction = 0f, endFraction = 0.33f, activity = ActivityState.STILL),
            TimelineSegment(startFraction = 0.33f, endFraction = 0.66f, activity = ActivityState.WALKING),
            TimelineSegment(startFraction = 0.66f, endFraction = 1f, activity = ActivityState.CYCLING),
        )
        val description = timelineContentDescription(segments)
        assertTrue("Description should NOT mention 'still': $description",
            !description.contains("still", ignoreCase = true))
        assertTrue("Description should mention 'walking': $description",
            description.contains("walking", ignoreCase = true))
        assertTrue("Description should mention 'cycling': $description",
            description.contains("cycling", ignoreCase = true))
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
        assertTrue("Empty transitions should return empty list", result.isEmpty())
    }
}
