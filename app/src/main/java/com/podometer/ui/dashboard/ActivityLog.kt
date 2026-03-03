// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import com.podometer.ui.theme.PodometerTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Snackbar auto-dismiss timeout in milliseconds (5 seconds per spec). */
private const val SNACKBAR_TIMEOUT_MS = 5_000L

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Formats an epoch-millisecond timestamp as "HH:mm" using the given [timeZone].
 *
 * @param timestampMillis Epoch-millisecond timestamp to format.
 * @param timeZone        [TimeZone] to use for formatting; defaults to the system default.
 * @return Time string in "HH:mm" format, e.g. "09:15".
 */
fun formatActivityTime(
    timestampMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.ROOT)
    sdf.timeZone = timeZone
    return sdf.format(timestampMillis)
}

/**
 * Formats a session time range as "HH:mm – HH:mm" or "HH:mm –" for ongoing sessions.
 *
 * @param startMillis Epoch-millisecond timestamp when the session started.
 * @param endMillis   Epoch-millisecond timestamp when the session ended, or null if ongoing.
 * @param timeZone    [TimeZone] to use for formatting; defaults to the system default.
 * @return Range string in "HH:mm – HH:mm" or "HH:mm –" format.
 */
fun formatActivityRange(
    startMillis: Long,
    endMillis: Long?,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val startLabel = formatActivityTime(startMillis, timeZone)
    return if (endMillis != null) {
        "$startLabel – ${formatActivityTime(endMillis, timeZone)}"
    } else {
        "$startLabel –"
    }
}

/**
 * Formats a duration in milliseconds as a human-readable string.
 *
 * @param durationMs Duration in milliseconds.
 * @return Duration string like "1h 15m", "45m", or "ongoing".
 */
fun formatActivityDuration(durationMs: Long?): String {
    if (durationMs == null) return "ongoing"
    val totalMinutes = (durationMs / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

/**
 * Returns the English label for the given [ActivityState].
 *
 * @param activity The [ActivityState] to describe.
 * @return "Walking" or "Cycling".
 */
fun activityLabel(activity: ActivityState): String = when (activity) {
    ActivityState.WALKING -> "Walking"
    ActivityState.CYCLING -> "Cycling"
    ActivityState.STILL -> "Still"
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Renders a consolidated list of today's activity sessions.
 *
 * Replaces both [TransitionLog] and [CyclingSessionList] with a unified view.
 * Each row shows an icon, activity label, time range, and duration.
 *
 * Tapping a row opens a [ModalBottomSheet] with reclassification options.
 * On override, [onOverride] is called and a snackbar is shown with an "Undo" action.
 *
 * **Empty state**: When [sessions] is empty, shows "No activities detected today".
 *
 * @param sessions          List of [ActivitySession]s to display.
 * @param onOverride        Callback invoked when the user confirms an override.
 *                          Receives the transition id and the new [ActivityState].
 * @param snackbarHostState [SnackbarHostState] used to show the "Activity overridden" snackbar.
 * @param onUndo            Callback invoked when the user taps "Undo" in the snackbar.
 * @param nowMillis         Current wall-clock time for calculating ongoing session duration.
 * @param modifier          Optional [Modifier] applied to the root [Column].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLog(
    sessions: List<ActivitySession>,
    onOverride: (transitionId: Int, newActivity: ActivityState) -> Unit,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier,
) {
    val overriddenLabel = stringResource(R.string.transition_overridden)
    val undoLabel = stringResource(R.string.transition_undo)
    val scope = rememberCoroutineScope()

    var selectedSession by remember { mutableStateOf<ActivitySession?>(null) }
    var snackbarJob by remember { mutableStateOf<Job?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = modifier) {
        if (sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.placeholder_activities),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sessions.forEach { session ->
                ActivityLogItem(
                    session = session,
                    nowMillis = nowMillis,
                    onClick = { selectedSession = session },
                )
            }
        }
    }

    // Bottom sheet — rendered outside the Column so it overlays the full screen
    if (selectedSession != null) {
        val session = selectedSession!!
        ModalBottomSheet(
            onDismissRequest = { selectedSession = null },
            sheetState = sheetState,
        ) {
            ActivityOverrideSheet(
                session = session,
                onOptionSelected = { newActivity ->
                    selectedSession = null
                    onOverride(session.startTransitionId, newActivity)
                    snackbarJob?.cancel()
                    snackbarJob = scope.launch {
                        try {
                            val result = withTimeoutOrNull(SNACKBAR_TIMEOUT_MS) {
                                snackbarHostState.showSnackbar(
                                    message = overriddenLabel,
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Indefinite,
                                )
                            }
                            if (result == SnackbarResult.ActionPerformed) {
                                onUndo()
                            }
                        } finally {
                            snackbarHostState.currentSnackbarData?.dismiss()
                        }
                    }
                },
            )
        }
    }
}

/**
 * A single row in the activity log displaying icon, activity label, time range,
 * duration, and optional override badge.
 *
 * @param session   The [ActivitySession] to display.
 * @param nowMillis Current wall-clock time for ongoing duration.
 * @param onClick   Callback invoked when the user taps this row.
 */
@Composable
private fun ActivityLogItem(
    session: ActivitySession,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val label = when (session.activity) {
        ActivityState.WALKING -> stringResource(R.string.activity_walking)
        ActivityState.CYCLING -> stringResource(R.string.activity_cycling)
        ActivityState.STILL -> stringResource(R.string.activity_still)
    }
    val rangeText = formatActivityRange(session.startTime, session.endTime)
    val durationMs = if (session.endTime != null) {
        session.endTime - session.startTime
    } else {
        nowMillis - session.startTime
    }
    val durationText = if (session.endTime != null) {
        formatActivityDuration(durationMs)
    } else {
        stringResource(R.string.activity_session_ongoing)
    }
    val overrideBadgeLabel = stringResource(R.string.transition_override_badge)
    val itemContentDescription = stringResource(
        R.string.cd_activity_session_item,
        label,
        rangeText,
        durationText,
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = itemContentDescription },
    ) {
        Icon(
            imageVector = session.activity.icon(),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = rangeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = durationText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        if (session.isManualOverride) {
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = onClick,
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

/**
 * Content of the override bottom sheet for an activity session.
 *
 * Shows buttons for each [ActivityState] that differs from the session's current activity.
 *
 * @param session          The [ActivitySession] being overridden.
 * @param onOptionSelected Callback invoked with the chosen [ActivityState].
 */
@Composable
private fun ActivityOverrideSheet(
    session: ActivitySession,
    onOptionSelected: (ActivityState) -> Unit,
) {
    val sheetCdLabel = stringResource(
        R.string.cd_activity_override_options,
        formatActivityTime(session.startTime),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
            .semantics { contentDescription = sheetCdLabel },
    ) {
        if (session.activity != ActivityState.WALKING) {
            TextButton(
                onClick = { onOptionSelected(ActivityState.WALKING) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = ActivityState.WALKING.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.transition_mark_as_walking))
            }
        }

        if (session.activity != ActivityState.CYCLING) {
            TextButton(
                onClick = { onOptionSelected(ActivityState.CYCLING) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = ActivityState.CYCLING.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.transition_mark_as_cycling))
            }
        }

        if (session.activity != ActivityState.STILL) {
            TextButton(
                onClick = { onOptionSelected(ActivityState.STILL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Icon(
                    imageVector = ActivityState.STILL.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.transition_mark_as_still))
            }
        }
    }
}

// ─── Preview functions ─────────────────────────────────────────────────────────

/** Preview: empty state — shows "No activities detected today". */
@Preview(showBackground = true, name = "ActivityLog — Empty")
@Composable
private fun PreviewActivityLogEmpty() {
    PodometerTheme {
        ActivityLog(
            sessions = emptyList(),
            onOverride = { _, _ -> },
            snackbarHostState = remember { SnackbarHostState() },
            onUndo = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: multiple sessions with mixed activity types. */
@Preview(showBackground = true, name = "ActivityLog — Multiple Sessions")
@Composable
private fun PreviewActivityLogMultiple() {
    val hour = 60L * 60_000L
    val sessions = listOf(
        ActivitySession(
            activity = ActivityState.WALKING,
            startTime = 9L * hour + 15L * 60_000L,
            endTime = 10L * hour + 30L * 60_000L,
            startTransitionId = 1,
            isManualOverride = false,
        ),
        ActivitySession(
            activity = ActivityState.CYCLING,
            startTime = 10L * hour + 30L * 60_000L,
            endTime = 11L * hour + 45L * 60_000L,
            startTransitionId = 2,
            isManualOverride = false,
        ),
        ActivitySession(
            activity = ActivityState.WALKING,
            startTime = 14L * hour,
            endTime = null,
            startTransitionId = 3,
            isManualOverride = false,
        ),
    )
    PodometerTheme {
        ActivityLog(
            sessions = sessions,
            onOverride = { _, _ -> },
            snackbarHostState = remember { SnackbarHostState() },
            onUndo = {},
            nowMillis = 14L * hour + 30L * 60_000L,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: session with override badge. */
@Preview(showBackground = true, name = "ActivityLog — With Override")
@Composable
private fun PreviewActivityLogWithOverride() {
    val hour = 60L * 60_000L
    val sessions = listOf(
        ActivitySession(
            activity = ActivityState.CYCLING,
            startTime = 9L * hour,
            endTime = 10L * hour,
            startTransitionId = 1,
            isManualOverride = true,
        ),
        ActivitySession(
            activity = ActivityState.WALKING,
            startTime = 10L * hour,
            endTime = 11L * hour,
            startTransitionId = 2,
            isManualOverride = false,
        ),
    )
    PodometerTheme {
        ActivityLog(
            sessions = sessions,
            onOverride = { _, _ -> },
            snackbarHostState = remember { SnackbarHostState() },
            onUndo = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
