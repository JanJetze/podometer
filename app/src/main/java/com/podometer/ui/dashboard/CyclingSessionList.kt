// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.ui.theme.PodometerTheme
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Formats an epoch-millisecond timestamp as "HH:mm" using the given [timeZone].
 *
 * This is a plain Kotlin function (no `@Composable`, no `stringResource`) kept for JVM
 * unit testability. The [CyclingSessionItem] composable uses `stringResource()` for
 * localised text; this function provides the time portion which is locale-independent.
 *
 * @param timestampMillis Epoch-millisecond timestamp to format.
 * @param timeZone        [TimeZone] to use for formatting; defaults to the system default.
 * @return Time string in "HH:mm" format, e.g. "09:15".
 */
fun formatSessionTime(
    timestampMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.ROOT)
    sdf.timeZone = timeZone
    return sdf.format(timestampMillis)
}

/**
 * Formats a cycling session time range as "HH:mm – HH:mm" or "HH:mm – ongoing".
 *
 * This is a plain Kotlin function kept for JVM unit testability. When [endMillis] is
 * null (session is still ongoing), the end time is represented as "ongoing".
 *
 * @param startMillis Epoch-millisecond timestamp when the session started.
 * @param endMillis   Epoch-millisecond timestamp when the session ended, or null if ongoing.
 * @param timeZone    [TimeZone] to use for formatting; defaults to the system default.
 * @return Range string in "HH:mm – HH:mm" or "HH:mm – ongoing" format.
 */
fun formatSessionRange(
    startMillis: Long,
    endMillis: Long?,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val startLabel = formatSessionTime(startMillis, timeZone)
    val endLabel = if (endMillis != null) formatSessionTime(endMillis, timeZone) else "ongoing"
    return "$startLabel – $endLabel"
}

/**
 * Formats a duration in minutes as "N min".
 *
 * This is a plain Kotlin function kept for JVM unit testability. The [CyclingSessionItem]
 * composable uses `stringResource()` at runtime for proper localisation.
 *
 * @param minutes Duration in minutes.
 * @return Duration string, e.g. "27 min".
 */
fun formatSessionDuration(minutes: Int): String = "$minutes min"

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Placeholder composable shown in the cycling sessions section when there are no cycling sessions.
 *
 * In v2.0.0, cycling session tracking is not available. This composable always renders
 * the "No cycling detected today" placeholder.
 *
 * @param modifier Optional [Modifier] applied to the root [Column].
 */
@Composable
fun CyclingSessionList(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.placeholder_cycling),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Preview functions ─────────────────────────────────────────────────────────

/** Preview: empty state — shows "No cycling detected today". */
@Preview(showBackground = true, name = "CyclingSessionList — Empty")
@Composable
private fun PreviewCyclingSessionListEmpty() {
    PodometerTheme {
        CyclingSessionList(
            modifier = Modifier.padding(16.dp),
        )
    }
}
