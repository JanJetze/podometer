// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.activities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podometer.R
import com.podometer.domain.model.TransitionEvent
import com.podometer.ui.dashboard.ActivityLog
import com.podometer.ui.dashboard.ActivityTimeline
import com.podometer.ui.dashboard.buildTimelineSegments
import com.podometer.util.DateTimeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Activities screen displaying recomputed activity sessions for a selected date.
 *
 * Features a date navigation row with previous/next arrows, a date picker dialog,
 * an activity timeline bar, and the consolidated activity log.
 *
 * @param modifier  Optional [Modifier] applied to the root [Scaffold].
 * @param viewModel Hilt [ActivitiesViewModel]; override in previews/tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
    modifier: Modifier = Modifier,
    viewModel: ActivitiesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.screen_activities)) },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                DateNavigationRow(
                    dateLabel = uiState.dateLabel,
                    isToday = uiState.isToday,
                    isNextEnabled = !uiState.isToday,
                    onPrevious = { viewModel.goToPreviousDay() },
                    onNext = { viewModel.goToNextDay() },
                    onDateClick = { showDatePicker = true },
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.sessions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.activities_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    // Activity Timeline
                    val dayStartMillis = DateTimeUtils.startOfDayMillis(uiState.selectedDate)
                    val dayEndMillis = dayStartMillis + 86_400_000L
                    val nowMillis = System.currentTimeMillis()

                    // Build transitions from sessions for the timeline.
                    // The recomputed sessions already have correct timestamps.
                    val transitions = sessionsToTransitions(uiState.sessions)

                    ActivityTimeline(
                        segments = buildTimelineSegments(
                            transitions = transitions,
                            dayStartMillis = dayStartMillis,
                            dayEndMillis = dayEndMillis,
                            nowMillis = if (uiState.isToday) nowMillis else dayEndMillis,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Activity Log (read-only on past days — override is disabled)
                    ActivityLog(
                        sessions = uiState.sessions,
                        onOverride = { _, _ -> }, // overrides not supported on recomputed data
                        snackbarHostState = snackbarHostState,
                        onUndo = {},
                        nowMillis = if (uiState.isToday) nowMillis else dayEndMillis,
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateTimeUtils.startOfDayMillis(uiState.selectedDate),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            viewModel.selectDate(date)
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Converts a list of [com.podometer.domain.model.ActivitySession]s into
 * [TransitionEvent]s suitable for [buildTimelineSegments].
 *
 * Each session produces a start transition (STILL → activity) and, if closed,
 * an end transition (activity → STILL).
 */
private fun sessionsToTransitions(
    sessions: List<com.podometer.domain.model.ActivitySession>,
): List<TransitionEvent> {
    val transitions = mutableListOf<TransitionEvent>()
    var id = 1
    for (session in sessions) {
        transitions.add(
            TransitionEvent(
                id = id++,
                timestamp = session.startTime,
                fromActivity = com.podometer.domain.model.ActivityState.STILL,
                toActivity = session.activity,
                isManualOverride = false,
            ),
        )
        if (session.endTime != null) {
            transitions.add(
                TransitionEvent(
                    id = id++,
                    timestamp = session.endTime,
                    fromActivity = session.activity,
                    toActivity = com.podometer.domain.model.ActivityState.STILL,
                    isManualOverride = false,
                ),
            )
        }
    }
    return transitions
}
