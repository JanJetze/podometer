// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.podometer.R
import com.podometer.data.sensor.SensorType
import com.podometer.util.checkEssentialPermissions
import kotlinx.coroutines.launch

/**
 * Dashboard screen displaying today's activity summary in a scrollable layout.
 *
 * Collects [DashboardViewModel.uiState] reactively. While [DashboardUiState.isLoading]
 * is true, a centred [CircularProgressIndicator] is shown. Once data is available:
 *
 * - When [DashboardUiState.permissionsDenied] is true, a [PermissionRecoveryScreen] is
 *   shown full-screen, hiding the normal dashboard content.
 * - Otherwise, the screen renders a scrollable [Column] with:
 *   - Optional [SensorNotice] when the sensor is [SensorType.ACCELEROMETER] or [SensorType.NONE].
 *   - [ActivityBadge] showing the current activity state.
 *   - [FirstLaunchEmptyState] when `todaySteps == 0 && transitions.isEmpty()`, OR
 *     [TodayCard] showing steps, progress ring, and distance when there is activity.
 *   - Activity Timeline, Transition Log, Weekly Steps, Cycling Sessions sections.
 *
 * Pull-to-refresh is not needed — all data flows are reactive.
 *
 * @param onNavigateToSettings Callback invoked when the user taps the settings gear icon.
 * @param onOpenSettings       Callback invoked when the user taps "Open App Settings" on the
 *                             [PermissionRecoveryScreen]. The caller should launch the system
 *                             app details settings intent.
 * @param modifier             Optional [Modifier] applied to the root [Scaffold].
 * @param viewModel            Hilt [DashboardViewModel]; override in previews/tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-check permissions every time the screen resumes (e.g. after returning from Settings).
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.refreshPermissions(checkEssentialPermissions(context))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.screen_dashboard)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                        )
                    }
                },
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
        } else if (uiState.permissionsDenied) {
            // Full-screen recovery: guide the user to grant permissions in system settings.
            PermissionRecoveryScreen(
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                // Sensor degraded-mode notice (shown when sensor is not the preferred type)
                if (uiState.sensorType == SensorType.ACCELEROMETER ||
                    uiState.sensorType == SensorType.NONE
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SensorNotice(
                        sensorType = uiState.sensorType,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Activity badge — centred below the app bar
                ActivityBadge(
                    activity = uiState.currentActivity,
                    modifier = Modifier
                        .align(CenterHorizontally)
                        .padding(vertical = 8.dp),
                )

                // Today card or first-launch empty state
                val isFirstLaunch = uiState.todaySteps == 0 && uiState.transitions.isEmpty()
                if (isFirstLaunch) {
                    FirstLaunchEmptyState(modifier = Modifier.fillMaxWidth())
                } else {
                    TodayCard(
                        steps = uiState.todaySteps,
                        goal = uiState.dailyGoal,
                        progressPercent = uiState.progressPercent,
                        distanceKm = uiState.distanceKm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Activity Timeline (placeholder — full implementation is a separate task)
                SectionHeader(title = stringResource(R.string.section_activity_timeline))
                PlaceholderSection(text = stringResource(R.string.placeholder_timeline))

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Transition Log
                SectionHeader(title = stringResource(R.string.section_transition_log))
                TransitionLog(
                    transitions = uiState.transitions,
                    onOverride = { transitionId, newActivity ->
                        viewModel.overrideTransition(transitionId, newActivity)
                    },
                    snackbarHostState = snackbarHostState,
                    onUndo = { viewModel.undoLastOverride() },
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Weekly Steps
                SectionHeader(title = stringResource(R.string.section_weekly_steps))
                WeeklyStepChart(
                    daySummaries = uiState.weeklySteps,
                    goal = uiState.dailyGoal,
                    todayDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Cycling Sessions
                SectionHeader(title = stringResource(R.string.section_cycling_sessions))
                CyclingSessionList(sessions = uiState.cyclingSessions)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Material 3 section header using [MaterialTheme.typography.titleMedium].
 *
 * @param title The localised section title to display.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/**
 * Placeholder body text shown in sections that are not yet implemented.
 *
 * Uses [MaterialTheme.typography.bodyMedium] with [MaterialTheme.colorScheme.onSurfaceVariant]
 * to visually de-emphasise the placeholder relative to real content.
 *
 * @param text The localised placeholder message to display.
 */
@Composable
private fun PlaceholderSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
