// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.podometer.domain.model.ActivityState
import com.podometer.domain.model.TransitionEvent
import com.podometer.ui.theme.PodometerTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// ─── Pure helper functions (unit-testable) ────────────────────────────────────

/**
 * Formats an epoch-millisecond timestamp as "HH:mm" using the given [timeZone].
 *
 * This is a plain Kotlin function (no `@Composable`, no `stringResource`) kept for JVM
 * unit testability. The [TransitionLogItem] composable uses `stringResource()` for
 * localised text; this function provides the time portion which is locale-independent.
 *
 * @param timestampMillis Epoch-millisecond timestamp to format.
 * @param timeZone        [TimeZone] to use for formatting; defaults to the system default.
 * @return Time string in "HH:mm" format, e.g. "09:15".
 */
fun formatTransitionTime(
    timestampMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.ROOT)
    sdf.timeZone = timeZone
    return sdf.format(timestampMillis)
}

/**
 * Returns the English description string for the given [ActivityState] as a transition
 * description.
 *
 * Like [formatTransitionTime], this is a pure-Kotlin function kept for JVM unit testability.
 * The [TransitionLogItem] composable uses `stringResource()` at runtime for proper i18n.
 *
 * @param activity The [ActivityState] to describe.
 * @return "Started walking", "Started cycling", or "Became still".
 */
fun transitionDescription(activity: ActivityState): String = when (activity) {
    ActivityState.WALKING -> "Started walking"
    ActivityState.CYCLING -> "Started cycling"
    ActivityState.STILL -> "Became still"
}

// ─── Composable ───────────────────────────────────────────────────────────────

/**
 * Renders a list of today's activity transition events.
 *
 * This is a pure presentational composable — it holds no business logic and receives
 * all data via parameters. The list is Column-based (not LazyColumn) because it lives
 * inside a scrollable Column in [DashboardScreen]; nested LazyColumn inside verticalScroll
 * is not allowed in Compose. The list is bounded (today's transitions only, typically <20).
 *
 * Each row shows:
 * - Timestamp formatted as "HH:mm"
 * - Activity icon (DirectionsWalk, DirectionsBike, or PauseCircle)
 * - Description text ("Started walking", "Started cycling", "Became still")
 * - Optional "Override" chip when [TransitionEvent.isManualOverride] is true
 *
 * Tapping a row opens a [ModalBottomSheet] with options to reclassify the transition.
 * On override, [onOverride] is called and a snackbar is shown with an "Undo" action.
 *
 * **Empty state**: When [transitions] is empty, shows "No activity transitions yet".
 *
 * @param transitions       List of [TransitionEvent]s to display.
 * @param onOverride        Callback invoked when the user confirms an override.
 *                          Receives the transition id and the new [ActivityState].
 * @param snackbarHostState [SnackbarHostState] used to show the "Activity overridden" snackbar.
 * @param onUndo            Callback invoked when the user taps "Undo" in the snackbar.
 * @param modifier          Optional [Modifier] applied to the root [Column].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionLog(
    transitions: List<TransitionEvent>,
    onOverride: (transitionId: Int, newActivity: ActivityState) -> Unit,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overriddenLabel = stringResource(R.string.transition_overridden)
    val undoLabel = stringResource(R.string.transition_undo)
    val scope = rememberCoroutineScope()

    var selectedTransition by remember { mutableStateOf<TransitionEvent?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = modifier) {
        if (transitions.isEmpty()) {
            Text(
                text = stringResource(R.string.placeholder_transitions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            transitions.forEach { event ->
                TransitionLogItem(
                    event = event,
                    onClick = { selectedTransition = event },
                )
            }
        }
    }

    // Bottom sheet — rendered outside the Column so it overlays the full screen
    if (selectedTransition != null) {
        val event = selectedTransition!!
        ModalBottomSheet(
            onDismissRequest = { selectedTransition = null },
            sheetState = sheetState,
        ) {
            TransitionOverrideSheet(
                event = event,
                onOptionSelected = { newActivity ->
                    selectedTransition = null
                    onOverride(event.id, newActivity)
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = overriddenLabel,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onUndo()
                        }
                    }
                },
            )
        }
    }
}

/**
 * A single row in the transition log displaying timestamp, icon, description, and
 * optional override badge.
 *
 * The row has a minimum tap target height of 48dp per Material Design accessibility
 * guidelines.
 *
 * @param event   The [TransitionEvent] to display.
 * @param onClick Callback invoked when the user taps this row.
 */
@Composable
private fun TransitionLogItem(
    event: TransitionEvent,
    onClick: () -> Unit,
) {
    val timeLabel = formatTransitionTime(event.timestamp)
    val descriptionText = when (event.toActivity) {
        ActivityState.WALKING -> stringResource(R.string.transition_started_walking)
        ActivityState.CYCLING -> stringResource(R.string.transition_started_cycling)
        ActivityState.STILL -> stringResource(R.string.transition_became_still)
    }
    val overrideBadgeLabel = stringResource(R.string.transition_override_badge)
    val itemContentDescription = stringResource(R.string.cd_transition_item, timeLabel, descriptionText)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = itemContentDescription },
    ) {
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = event.toActivity.icon(),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = descriptionText,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        if (event.isManualOverride) {
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
 * Content of the override bottom sheet.
 *
 * Shows buttons for each [ActivityState] that differs from the transition's current
 * [TransitionEvent.toActivity]. Each button has a minimum tap target of 48dp.
 *
 * @param event            The [TransitionEvent] being overridden.
 * @param onOptionSelected Callback invoked with the chosen [ActivityState].
 */
@Composable
private fun TransitionOverrideSheet(
    event: TransitionEvent,
    onOptionSelected: (ActivityState) -> Unit,
) {
    val sheetCdLabel = stringResource(
        R.string.cd_transition_override_options,
        formatTransitionTime(event.timestamp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 24.dp)
            .semantics { contentDescription = sheetCdLabel },
    ) {
        if (event.toActivity != ActivityState.WALKING) {
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

        if (event.toActivity != ActivityState.CYCLING) {
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

        if (event.toActivity != ActivityState.STILL) {
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

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Preview functions ─────────────────────────────────────────────────────────

/** Preview: empty state — shows "No activity transitions yet". */
@Preview(showBackground = true, name = "TransitionLog — Empty")
@Composable
private fun PreviewTransitionLogEmpty() {
    PodometerTheme {
        TransitionLog(
            transitions = emptyList(),
            onOverride = { _, _ -> },
            snackbarHostState = remember { SnackbarHostState() },
            onUndo = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: single transition item without override badge. */
@Preview(showBackground = true, name = "TransitionLog — Single Item")
@Composable
private fun PreviewTransitionLogSingleItem() {
    val transitions = listOf(
        TransitionEvent(
            id = 1,
            timestamp = (9L * 60 + 15L) * 60_000L,
            fromActivity = ActivityState.STILL,
            toActivity = ActivityState.WALKING,
            isManualOverride = false,
        ),
    )
    PodometerTheme {
        TransitionLog(
            transitions = transitions,
            onOverride = { _, _ -> },
            snackbarHostState = remember { SnackbarHostState() },
            onUndo = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: multiple transition items with mixed activity types. */
@Preview(showBackground = true, name = "TransitionLog — Multiple Items")
@Composable
private fun PreviewTransitionLogMultipleItems() {
    val hour = 60L * 60_000L
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
    )
    PodometerTheme {
        TransitionLog(
            transitions = transitions,
            onOverride = { _, _ -> },
            snackbarHostState = remember { SnackbarHostState() },
            onUndo = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: item with override badge visible. */
@Preview(showBackground = true, name = "TransitionLog — Item With Override Badge")
@Composable
private fun PreviewTransitionLogWithOverrideBadge() {
    val transitions = listOf(
        TransitionEvent(
            id = 1,
            timestamp = (14L * 60 + 30L) * 60_000L,
            fromActivity = ActivityState.WALKING,
            toActivity = ActivityState.CYCLING,
            isManualOverride = true,
        ),
        TransitionEvent(
            id = 2,
            timestamp = (16L * 60) * 60_000L,
            fromActivity = ActivityState.CYCLING,
            toActivity = ActivityState.STILL,
            isManualOverride = false,
        ),
    )
    PodometerTheme {
        TransitionLog(
            transitions = transitions,
            onOverride = { _, _ -> },
            snackbarHostState = remember { SnackbarHostState() },
            onUndo = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
