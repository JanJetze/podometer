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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * Returns the human-readable display label for this [ActivityState].
 *
 * These strings intentionally do NOT use `stringResource` so they remain
 * testable on the JVM without a Compose runtime. The UI uses them directly;
 * the string resource `cd_activity_currently` is used only for the
 * accessibility content description built in [ActivityBadge].
 *
 * @return "Walking", "Cycling", or "Still".
 */
fun ActivityState.displayText(): String = when (this) {
    ActivityState.WALKING -> "Walking"
    ActivityState.CYCLING -> "Cycling"
    ActivityState.STILL -> "Still"
}

/**
 * Returns the TalkBack content description for this [ActivityState].
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
 * The badge animates its background and content colours smoothly (300 ms tween) whenever
 * [activity] changes. It is purely presentational — wire it to [DashboardUiState.currentActivity]
 * in the parent screen.
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
    val contentColor by animateColorAsState(
        targetValue = BadgeContentColor,
        animationSpec = tween(durationMillis = 300),
        label = "badge_content",
    )

    val accessibilityDescription = activity.contentDescriptionText()

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
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
                text = activity.displayText(),
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
