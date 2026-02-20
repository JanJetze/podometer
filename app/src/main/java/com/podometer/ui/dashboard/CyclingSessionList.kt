// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.data.db.CyclingSession
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

/**
 * Sums the [CyclingSession.durationMinutes] across all sessions in [sessions].
 *
 * Returns 0 when [sessions] is empty.
 *
 * @param sessions List of [CyclingSession]s to sum.
 * @return Total duration in minutes.
 */
fun totalDurationMinutes(sessions: List<CyclingSession>): Int =
    sessions.sumOf { it.durationMinutes }

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Renders a column-based list of today's cycling sessions.
 *
 * This is a pure presentational composable — it holds no business logic and receives
 * all data via parameters. The list is Column-based (not LazyColumn) because it lives
 * inside a scrollable Column in [DashboardScreen]; nested LazyColumn inside verticalScroll
 * is not allowed in Compose. The list is bounded (today's sessions only, typically <10).
 *
 * Each row shows:
 * - Bike icon ([Icons.AutoMirrored.Filled.DirectionsBike], 20dp)
 * - Session range text: "HH:mm – HH:mm · N min" (or "ongoing" for in-progress sessions)
 * - Optional "Override" chip when [CyclingSession.isManualOverride] is true
 *
 * Below all session rows, a right-aligned total duration row displays the sum of all
 * session durations.
 *
 * **Empty state**: When [sessions] is empty, shows "No cycling detected today".
 *
 * **Section header**: The "Cycling Sessions" section header is already rendered in
 * [DashboardScreen] — this composable only renders the list content.
 *
 * @param sessions List of [CyclingSession]s to display.
 * @param modifier Optional [Modifier] applied to the root [Column].
 */
@Composable
fun CyclingSessionList(
    sessions: List<CyclingSession>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.placeholder_cycling),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sessions.forEach { session ->
                CyclingSessionItem(session = session)
            }
            val total = totalDurationMinutes(sessions)
            val totalText = stringResource(
                R.string.cycling_total_duration,
                stringResource(R.string.cycling_session_duration_format, total),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = totalText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A single row in the cycling session list displaying bike icon, session range and
 * duration, and an optional override badge.
 *
 * The row has a minimum tap target height of 48dp per Material Design accessibility
 * guidelines.
 *
 * @param session The [CyclingSession] to display.
 */
@Composable
private fun CyclingSessionItem(session: CyclingSession) {
    val startLabel = formatSessionTime(session.startTime)
    val endLabel = if (session.endTime != null) {
        formatSessionTime(session.endTime)
    } else {
        stringResource(R.string.cycling_session_ongoing)
    }
    val durationLabel = stringResource(R.string.cycling_session_duration_format, session.durationMinutes)
    val rangeText = stringResource(R.string.cycling_session_range, startLabel, endLabel, durationLabel)
    val overrideBadgeLabel = stringResource(R.string.cycling_override_badge)
    val itemContentDescription = stringResource(
        R.string.cd_cycling_session_item,
        startLabel,
        endLabel,
        session.durationMinutes,
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = itemContentDescription },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = rangeText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        if (session.isManualOverride) {
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = overrideBadgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

// ─── Preview functions ─────────────────────────────────────────────────────────

/** Preview: empty state — shows "No cycling detected today". */
@Preview(showBackground = true, name = "CyclingSessionList — Empty")
@Composable
private fun PreviewCyclingSessionListEmpty() {
    PodometerTheme {
        CyclingSessionList(
            sessions = emptyList(),
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: single completed session. */
@Preview(showBackground = true, name = "CyclingSessionList — Single Completed Session")
@Composable
private fun PreviewCyclingSessionListSingle() {
    val hour = 60L * 60_000L
    val sessions = listOf(
        CyclingSession(
            id = 1,
            startTime = 9L * hour + 15L * 60_000L,
            endTime = 9L * hour + 42L * 60_000L,
            durationMinutes = 27,
            isManualOverride = false,
        ),
    )
    PodometerTheme {
        CyclingSessionList(
            sessions = sessions,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: multiple sessions — mix of completed and one ongoing. */
@Preview(showBackground = true, name = "CyclingSessionList — Multiple Sessions")
@Composable
private fun PreviewCyclingSessionListMultiple() {
    val hour = 60L * 60_000L
    val sessions = listOf(
        CyclingSession(
            id = 1,
            startTime = 8L * hour,
            endTime = 8L * hour + 27L * 60_000L,
            durationMinutes = 27,
            isManualOverride = false,
        ),
        CyclingSession(
            id = 2,
            startTime = 12L * hour + 30L * 60_000L,
            endTime = 12L * hour + 57L * 60_000L,
            durationMinutes = 27,
            isManualOverride = false,
        ),
        CyclingSession(
            id = 3,
            startTime = 17L * hour,
            endTime = null,
            durationMinutes = 12,
            isManualOverride = false,
        ),
    )
    PodometerTheme {
        CyclingSessionList(
            sessions = sessions,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: session with override badge visible. */
@Preview(showBackground = true, name = "CyclingSessionList — Session With Override Badge")
@Composable
private fun PreviewCyclingSessionListWithOverrideBadge() {
    val hour = 60L * 60_000L
    val sessions = listOf(
        CyclingSession(
            id = 1,
            startTime = 9L * hour + 15L * 60_000L,
            endTime = 9L * hour + 42L * 60_000L,
            durationMinutes = 27,
            isManualOverride = true,
        ),
        CyclingSession(
            id = 2,
            startTime = 14L * hour,
            endTime = 14L * hour + 45L * 60_000L,
            durationMinutes = 45,
            isManualOverride = false,
        ),
    )
    PodometerTheme {
        CyclingSessionList(
            sessions = sessions,
            modifier = Modifier.padding(16.dp),
        )
    }
}
