// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podometer.R

/**
 * Dashboard screen that displays today's step count and progress.
 *
 * Accepts a [viewModel] (defaulting to a Hilt-provided instance) and collects
 * its [DashboardViewModel.uiState] with [collectAsStateWithLifecycle]. The detailed
 * dashboard UI (progress ring, cards, etc.) will be implemented in separate tasks;
 * this screen currently shows the step count.
 *
 * @param onNavigateToSettings Callback invoked when the user taps the Settings icon.
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Text(
                text = if (uiState.isLoading) {
                    stringResource(R.string.screen_dashboard)
                } else {
                    uiState.todaySteps.toString()
                },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
