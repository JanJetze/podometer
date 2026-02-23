// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.podometer.ui.Screen
import com.podometer.ui.dashboard.DashboardScreen
import com.podometer.ui.settings.SettingsScreen
import com.podometer.ui.settings.SettingsViewModel
import com.podometer.ui.theme.PodometerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity entry point for Podometer.
 *
 * Annotated with @AndroidEntryPoint to enable Hilt injection in this activity.
 * Hosts the Compose NavHost with Dashboard (start destination) and Settings routes.
 * The PodometerTheme wraps all content so every screen inherits the Material 3 colour
 * scheme and typography.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodometerTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = Screen.Dashboard.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Screen.Dashboard.route) {
                        DashboardScreen(
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route)
                            },
                        )
                    }

                    composable(Screen.Settings.route) {
                        val viewModel: SettingsViewModel = hiltViewModel()
                        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                        SettingsScreen(
                            uiState = uiState,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onSetDailyStepGoal = viewModel::setDailyStepGoal,
                            onSetStrideLengthCm = viewModel::setStrideLengthCm,
                            onSetAutoStartEnabled = viewModel::setAutoStartEnabled,
                            onSetNotificationStyle = viewModel::setNotificationStyle,
                            onExportData = viewModel::exportData,
                            onResetExportState = viewModel::resetExportState,
                        )
                    }
                }
            }
        }
    }
}
