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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podometer.R

/**
 * Dashboard screen displaying today's activity summary in a scrollable layout.
 *
 * Collects [DashboardViewModel.uiState] reactively. While [DashboardUiState.isLoading]
 * is true, a centred [CircularProgressIndicator] is shown. Once data is available, the
 * screen renders a scrollable [Column] with:
 *  - [ActivityBadge] showing the current activity state
 *  - [TodayCard] showing steps, progress ring, and distance
 *  - Placeholder sections for Activity Timeline, Transition Log, Weekly Steps,
 *    and Cycling Sessions (full implementations are separate tasks)
 *
 * Pull-to-refresh is not needed — all data flows are reactive.
 *
 * @param onNavigateToSettings Callback invoked when the user taps the settings gear icon.
 * @param modifier             Optional [Modifier] applied to the root [Scaffold].
 * @param viewModel            Hilt [DashboardViewModel]; override in previews/tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                // Activity badge — centred below the app bar
                ActivityBadge(
                    activity = uiState.currentActivity,
                    modifier = Modifier
                        .align(CenterHorizontally)
                        .padding(vertical = 8.dp),
                )

                // Today card — full-width hero card with progress ring
                TodayCard(
                    steps = uiState.todaySteps,
                    goal = uiState.dailyGoal,
                    progressPercent = uiState.progressPercent,
                    distanceKm = uiState.distanceKm,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Activity Timeline (placeholder — full implementation is a separate task)
                SectionHeader(title = stringResource(R.string.section_activity_timeline))
                PlaceholderSection(text = stringResource(R.string.placeholder_timeline))

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Transition Log (placeholder — full implementation is a separate task)
                SectionHeader(title = stringResource(R.string.section_transition_log))
                PlaceholderSection(text = stringResource(R.string.placeholder_transitions))

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Weekly Steps (placeholder — full implementation is a separate task)
                SectionHeader(title = stringResource(R.string.section_weekly_steps))
                PlaceholderSection(text = stringResource(R.string.placeholder_weekly))

                Spacer(modifier = Modifier.height(16.dp))

                // Section: Cycling Sessions (placeholder — full implementation is a separate task)
                SectionHeader(title = stringResource(R.string.section_cycling_sessions))
                PlaceholderSection(text = stringResource(R.string.placeholder_cycling))

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
