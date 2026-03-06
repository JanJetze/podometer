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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.podometer.domain.model.ActivitySession
import com.podometer.domain.model.ActivityState
import com.podometer.ui.dashboard.ActivityLog
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
    var showDatePicker by remember { mutableStateOf(false) }
    var highlightedSessionIndex by remember { mutableIntStateOf(-1) }
    var editingSession by remember { mutableStateOf<ActivitySession?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.screen_activities)) },
            )
        },
        floatingActionButton = {
            if (!uiState.isLoading && uiState.windows.isNotEmpty()) {
                FloatingActionButton(
                    onClick = {
                        val dayStart = DateTimeUtils.startOfDayMillis(uiState.selectedDate)
                        val noon = dayStart + 12 * 3_600_000L
                        editingSession = ActivitySession(
                            activity = ActivityState.WALKING,
                            startTime = noon,
                            endTime = noon + ActivitySession.DEFAULT_DURATION_MS,
                            startTransitionId = 0,
                            isManualOverride = false,
                        )
                    },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add activity")
                }
            }
        },
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

                // Step Graph (shown even when sessions are empty, as long as windows exist)
                val dayStartMillis = DateTimeUtils.startOfDayMillis(uiState.selectedDate)
                val dayEndMillis = dayStartMillis + 86_400_000L
                val nowMillis = System.currentTimeMillis()

                if (uiState.windows.isNotEmpty()) {
                    val graphData = remember(uiState.windows, uiState.sessions, uiState.bucketSizeMs) {
                        buildStepGraphData(
                            windows = uiState.windows,
                            sessions = uiState.sessions,
                            bucketSizeMs = uiState.bucketSizeMs,
                            dayStartMillis = dayStartMillis,
                            dayEndMillis = dayEndMillis,
                        )
                    }

                    StepGraph(
                        graphData = graphData,
                        sessions = uiState.sessions,
                        dayStartMillis = dayStartMillis,
                        dayEndMillis = dayEndMillis,
                        highlightedSessionIndex = highlightedSessionIndex,
                        onSessionHighlight = { index ->
                            highlightedSessionIndex = if (highlightedSessionIndex == index) -1 else index
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    BucketSizeSelector(
                        selectedMs = uiState.bucketSizeMs,
                        onBucketSelected = { viewModel.setBucketSize(it) },
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (uiState.sessions.isEmpty() && uiState.windows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.activities_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }

                if (uiState.sessions.isNotEmpty()) {
                    ActivityLog(
                        sessions = uiState.sessions,
                        onSessionClick = { session ->
                            editingSession = session
                            val idx = uiState.sessions.indexOf(session)
                            highlightedSessionIndex = idx
                        },
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

    // Session edit bottom sheet
    if (editingSession != null) {
        val session = editingSession!!
        val dayStartMillis = DateTimeUtils.startOfDayMillis(uiState.selectedDate)
        val dayEndMillis = dayStartMillis + 86_400_000L
        val closeEditSheet = {
            editingSession = null
            highlightedSessionIndex = -1
        }

        ModalBottomSheet(
            onDismissRequest = closeEditSheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SessionEditSheet(
                session = session,
                windows = uiState.windows,
                dayStartMillis = dayStartMillis,
                dayEndMillis = dayEndMillis,
                onSave = { startMs, endMs, activity ->
                    val overrideId = if (session.isManualOverride) {
                        -session.startTransitionId.toLong()
                    } else {
                        0L
                    }
                    viewModel.saveSessionOverride(startMs, endMs, activity, overrideId)
                    closeEditSheet()
                },
                onCancel = closeEditSheet,
                onDelete = if (!session.isNew) {
                    {
                        if (session.isManualOverride) {
                            viewModel.deleteSessionOverride(-session.startTransitionId.toLong())
                        } else {
                            viewModel.saveSessionOverride(
                                session.startTime,
                                session.effectiveEndTime(),
                                ActivityState.STILL,
                            )
                        }
                        closeEditSheet()
                    }
                } else {
                    null
                },
            )
        }
    }
}
