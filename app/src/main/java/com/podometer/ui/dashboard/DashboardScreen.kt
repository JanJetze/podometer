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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podometer.R
import com.podometer.domain.model.DaySummary
import com.podometer.service.startTrackingServiceIfPermitted
import com.podometer.ui.theme.PodometerTheme
import com.podometer.util.checkEssentialPermissions
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dashboard screen (v2) displaying today's step progress and weekly activity.
 *
 * Collects [DashboardViewModel.uiState] reactively and renders a scrollable layout:
 *
 * 1. [ProgressRing] — large centered ring with step count and tier label.
 * 2. [StreakCounter] — compact streak display below the ring.
 * 3. [TodayStepChart] — bar chart for today's buckets with resolution switcher.
 * 4. [WeeklyStepChart] — daily summary chart for the current week.
 *
 * When [DashboardUiState.isLoading] is true a centered [CircularProgressIndicator] is shown.
 * When [DashboardUiState.permissionsDenied] is true, [PermissionRecoveryScreen] is shown
 * full-screen.
 *
 * Pull-to-refresh is not needed — all data flows are reactive.
 *
 * @param onNavigateToSettings Callback invoked when the user taps the settings gear icon.
 * @param onOpenSettings       Callback invoked when the user taps "Open App Settings" on the
 *                             [PermissionRecoveryScreen].
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

    // Re-check permissions and ensure the tracking service is running every time the
    // screen resumes. startTrackingServiceIfPermitted is idempotent.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(checkEssentialPermissions(context))
                startTrackingServiceIfPermitted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
            PermissionRecoveryScreen(
                onOpenSettings = onOpenSettings,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            DashboardContent(
                uiState = uiState,
                onResolutionChange = viewModel::setChartResolution,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

/**
 * Scrollable content area for the dashboard, composed of the four main sections:
 * ProgressRing, StreakCounter, TodayStepChart, and WeeklyStepChart.
 *
 * Pure presentational — no ViewModel access.
 *
 * @param uiState          The current dashboard state to render.
 * @param onResolutionChange Callback when the user selects a different chart resolution.
 * @param modifier         Applied to the root [Column].
 */
@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onResolutionChange: (ChartResolution) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Section 1: Progress Ring
        ProgressRing(
            steps = uiState.todaySteps,
            minimumGoal = uiState.minimumGoal,
            targetGoal = uiState.targetGoal,
            stretchGoal = uiState.stretchGoal,
            isRestDay = uiState.isRestDay,
            modifier = Modifier.size(220.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Section 2: Streak Counter
        StreakCounter(
            streakDays = uiState.streakDays,
            todayMet = uiState.todayGoalMet,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: Today's Step Chart
        SectionHeader(title = stringResource(R.string.section_today_steps))
        TodayStepChart(
            bars = uiState.todayBuckets,
            resolution = uiState.chartResolution,
            onResolutionChange = onResolutionChange,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section 4: Weekly Step Chart
        SectionHeader(title = stringResource(R.string.section_weekly_steps))
        WeeklyStepChart(
            daySummaries = uiState.weeklyDays,
            goal = uiState.targetGoal,
            todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

// ─── Preview functions ────────────────────────────────────────────────────────

private fun previewUiState(
    steps: Int = 0,
    streakDays: Int = 0,
    todayGoalMet: Boolean = false,
    isRestDay: Boolean = false,
    chartResolution: ChartResolution = ChartResolution.HOURLY,
    weeklyDays: List<DaySummary> = emptyList(),
): DashboardUiState = DashboardUiState(
    todaySteps = steps,
    todayDistance = steps * 0.00075,
    minimumGoal = 5_000,
    targetGoal = 8_000,
    stretchGoal = 12_000,
    isRestDay = isRestDay,
    streakDays = streakDays,
    todayGoalMet = todayGoalMet,
    todayBuckets = emptyList(),
    chartResolution = chartResolution,
    weeklyDays = weeklyDays,
    isLoading = false,
    permissionsDenied = false,
)

/** Preview: zero steps, no streak — fresh start of the day. */
@Preview(showBackground = true, name = "DashboardScreen — Zero steps")
@Composable
private fun PreviewDashboardScreenZero() {
    PodometerTheme(dynamicColor = false) {
        DashboardContent(
            uiState = previewUiState(steps = 0),
            onResolutionChange = {},
        )
    }
}

/** Preview: minimum goal reached with an active streak. */
@Preview(showBackground = true, name = "DashboardScreen — Minimum goal reached")
@Composable
private fun PreviewDashboardScreenMinimum() {
    PodometerTheme(dynamicColor = false) {
        DashboardContent(
            uiState = previewUiState(steps = 5_200, streakDays = 3, todayGoalMet = true),
            onResolutionChange = {},
        )
    }
}

/** Preview: target goal reached, long streak. */
@Preview(showBackground = true, name = "DashboardScreen — Target goal reached")
@Composable
private fun PreviewDashboardScreenTarget() {
    PodometerTheme(dynamicColor = false) {
        DashboardContent(
            uiState = previewUiState(steps = 8_400, streakDays = 14, todayGoalMet = true),
            onResolutionChange = {},
        )
    }
}

/** Preview: stretch goal fully reached. */
@Preview(showBackground = true, name = "DashboardScreen — Stretch goal complete")
@Composable
private fun PreviewDashboardScreenStretch() {
    PodometerTheme(dynamicColor = false) {
        DashboardContent(
            uiState = previewUiState(steps = 12_500, streakDays = 30, todayGoalMet = true),
            onResolutionChange = {},
        )
    }
}

/** Preview: rest day — ring is muted. */
@Preview(showBackground = true, name = "DashboardScreen — Rest day")
@Composable
private fun PreviewDashboardScreenRestDay() {
    PodometerTheme(dynamicColor = false) {
        DashboardContent(
            uiState = previewUiState(steps = 2_000, streakDays = 7, isRestDay = true),
            onResolutionChange = {},
        )
    }
}

/** Preview: dark theme, active progress. */
@Preview(showBackground = true, backgroundColor = 0xFF0E1514, name = "DashboardScreen — Dark theme")
@Composable
private fun PreviewDashboardScreenDark() {
    PodometerTheme(darkTheme = true, dynamicColor = false) {
        DashboardContent(
            uiState = previewUiState(steps = 6_800, streakDays = 5, todayGoalMet = true),
            onResolutionChange = {},
        )
    }
}
