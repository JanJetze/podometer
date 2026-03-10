// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.ui.theme.PodometerTheme

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Returns whether a streak is currently active (i.e., greater than zero).
 *
 * @param streakDays The current streak count.
 * @return `true` if [streakDays] > 0.
 */
fun isStreakActive(streakDays: Int): Boolean = streakDays > 0

/**
 * Builds a human-readable label for the streak display.
 *
 * Examples: "1 day streak", "7 days streak", "0 days streak".
 *
 * @param streakDays The current streak count.
 * @return A formatted label string.
 */
fun streakLabel(streakDays: Int): String {
    val dayWord = if (streakDays == 1) "day" else "days"
    return "$streakDays $dayWord streak"
}

/**
 * Builds an accessible plain-English description for the streak counter.
 *
 * Pure-Kotlin function kept separate from the Composable so it can be unit-tested on the JVM.
 *
 * @param streakDays The current streak count.
 * @param todayMet   Whether the minimum goal has been met today.
 * @return Human-readable string suitable for use as a `contentDescription`.
 */
fun streakContentDescription(streakDays: Int, todayMet: Boolean): String {
    val base = streakLabel(streakDays)
    return if (todayMet) "$base, today's goal met" else base
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Compact streak counter showing a flame icon, the streak count, and a "day streak" label.
 *
 * Visual treatment:
 * - When [streakDays] > 0 the flame icon and text use [MaterialTheme.colorScheme.tertiary]
 *   (amber/gold) to convey an active, motivating streak.
 * - When [streakDays] == 0 the component is muted using [MaterialTheme.colorScheme.onSurfaceVariant].
 * - When [todayMet] is `true`, a checkmark icon appears at the end to confirm today's progress.
 *
 * This is a purely presentational composable — it holds no internal state.
 *
 * @param streakDays The number of consecutive days the minimum goal has been met.
 * @param todayMet   Whether today's minimum goal has already been met.
 * @param modifier   Optional [Modifier] applied to the root [Row].
 */
@Composable
fun StreakCounter(
    streakDays: Int,
    todayMet: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = isStreakActive(streakDays)
    val contentColor: Color = if (active) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val accessibilityText = streakContentDescription(streakDays, todayMet)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.semantics { contentDescription = accessibilityText },
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null, // covered by row's contentDescription
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )

        Text(
            text = streakLabel(streakDays),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )

        if (todayMet) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null, // covered by row's contentDescription
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ─── Preview functions ────────────────────────────────────────────────────────

/** Preview: zero streak, today not met — fully muted. */
@Preview(showBackground = true, name = "StreakCounter — Zero streak")
@Composable
private fun PreviewStreakCounterZero() {
    PodometerTheme(dynamicColor = false) {
        StreakCounter(
            streakDays = 0,
            todayMet = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: active streak, today not met. */
@Preview(showBackground = true, name = "StreakCounter — Active streak, today not met")
@Composable
private fun PreviewStreakCounterActive() {
    PodometerTheme(dynamicColor = false) {
        StreakCounter(
            streakDays = 7,
            todayMet = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: active streak with today met — checkmark shown. */
@Preview(showBackground = true, name = "StreakCounter — Active streak, today met")
@Composable
private fun PreviewStreakCounterActiveTodayMet() {
    PodometerTheme(dynamicColor = false) {
        StreakCounter(
            streakDays = 14,
            todayMet = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: 1-day streak (singular label). */
@Preview(showBackground = true, name = "StreakCounter — 1 day streak")
@Composable
private fun PreviewStreakCounterOneDay() {
    PodometerTheme(dynamicColor = false) {
        StreakCounter(
            streakDays = 1,
            todayMet = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: dark theme, active streak with today met. */
@Preview(showBackground = true, backgroundColor = 0xFF0E1514, name = "StreakCounter — Dark theme")
@Composable
private fun PreviewStreakCounterDark() {
    PodometerTheme(darkTheme = true, dynamicColor = false) {
        StreakCounter(
            streakDays = 21,
            todayMet = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}
