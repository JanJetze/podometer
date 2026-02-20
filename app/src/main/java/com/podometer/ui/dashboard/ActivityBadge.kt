// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.domain.model.ActivityState
import com.podometer.ui.theme.PodometerTheme

// ─── Activity badge colour constants ──────────────────────────────────────────
// Defined here as local constants; to be moved to the theme in a future task.

/** Background colour for the Walking badge. */
private val WalkingBadgeBackground = Color(0xFF4CAF50)

/** Background colour for the Cycling badge. */
private val CyclingBadgeBackground = Color(0xFF2196F3)

/** Background colour for the Still badge. */
private val StillBadgeBackground = Color(0xFF9E9E9E)

/** Content (icon/text) colour rendered on top of the badge background. */
private val BadgeContentColor = Color.White

// ─── Public extension helpers (pure functions, fully unit-testable) ────────────

/**
 * Returns the English display label for this [ActivityState].
 *
 * This function is intentionally a plain Kotlin function (no `@Composable`, no
 * `stringResource`) so that it can be exercised in JVM unit tests without a
 * Compose runtime. The [ActivityBadge] composable uses `stringResource()` directly
 * for proper localisation; this function serves as a test helper only.
 *
 * @return "Walking", "Cycling", or "Still".
 */
fun ActivityState.displayText(): String = when (this) {
    ActivityState.WALKING -> "Walking"
    ActivityState.CYCLING -> "Cycling"
    ActivityState.STILL -> "Still"
}

/**
 * Returns the English TalkBack content description for this [ActivityState].
 *
 * Like [displayText], this is a plain Kotlin function kept for JVM unit
 * testability. The [ActivityBadge] composable uses `stringResource()` for
 * the actual localised content description rendered at runtime.
 *
 * @return "Currently walking", "Currently cycling", or "Currently still".
 */
fun ActivityState.contentDescriptionText(): String =
    "Currently ${displayText().lowercase()}"

// ─── Internal helpers (Compose-only, not unit-tested directly) ────────────────

/**
 * Background colour for the badge corresponding to this [ActivityState].
 */
internal fun ActivityState.backgroundColor(): Color = when (this) {
    ActivityState.WALKING -> WalkingBadgeBackground
    ActivityState.CYCLING -> CyclingBadgeBackground
    ActivityState.STILL -> StillBadgeBackground
}

/**
 * Icon image vector for this [ActivityState].
 */
internal fun ActivityState.icon(): ImageVector = when (this) {
    ActivityState.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
    ActivityState.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    ActivityState.STILL -> Icons.Filled.PauseCircle
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Chip/badge that displays the user's current activity state with an icon and text label.
 *
 * The badge animates its background colour smoothly (300 ms tween) whenever [activity] changes.
 * The content colour is always [BadgeContentColor] (white) and is therefore passed directly —
 * animating a constant value would waste recomposition cycles for no visual benefit.
 *
 * Display text and accessibility description are sourced from string resources for proper
 * localisation. The [displayText] and [contentDescriptionText] extension functions are kept
 * as pure-Kotlin helpers for JVM unit tests only.
 *
 * Accessibility: the outer [Surface] carries a `contentDescription` of
 * "Currently walking" / "Currently cycling" / "Currently still" for TalkBack.
 *
 * @param activity The current [ActivityState] to display.
 * @param modifier Optional [Modifier] applied to the root [Surface].
 */
@Composable
fun ActivityBadge(
    activity: ActivityState,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = activity.backgroundColor(),
        animationSpec = tween(durationMillis = 300),
        label = "badge_bg",
    )

    // Resolve localised strings from resources for proper i18n support.
    val displayText = when (activity) {
        ActivityState.WALKING -> stringResource(R.string.activity_walking)
        ActivityState.CYCLING -> stringResource(R.string.activity_cycling)
        ActivityState.STILL -> stringResource(R.string.activity_still)
    }
    val accessibilityDescription = stringResource(R.string.cd_activity_currently, displayText)

    Surface(
        color = backgroundColor,
        // BadgeContentColor (white) is constant — no need to animate it.
        contentColor = BadgeContentColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.semantics {
            contentDescription = accessibilityDescription
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                imageVector = activity.icon(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

// ─── Preview functions ─────────────────────────────────────────────────────────

/** Preview: Walking state (green). */
@Preview(showBackground = true, name = "ActivityBadge — Walking")
@Composable
private fun PreviewActivityBadgeWalking() {
    PodometerTheme {
        ActivityBadge(
            activity = ActivityState.WALKING,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: Cycling state (blue). */
@Preview(showBackground = true, name = "ActivityBadge — Cycling")
@Composable
private fun PreviewActivityBadgeCycling() {
    PodometerTheme {
        ActivityBadge(
            activity = ActivityState.CYCLING,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: Still state (gray). */
@Preview(showBackground = true, name = "ActivityBadge — Still")
@Composable
private fun PreviewActivityBadgeStill() {
    PodometerTheme {
        ActivityBadge(
            activity = ActivityState.STILL,
            modifier = Modifier.padding(16.dp),
        )
    }
}
