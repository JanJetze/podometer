// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.podometer.service.startTrackingServiceIfPermitted
import com.podometer.ui.Screen
import com.podometer.ui.dashboard.DashboardScreen
import com.podometer.ui.donate.DonateScreen
import com.podometer.ui.navigation.BottomNavBar
import com.podometer.ui.onboarding.OnboardingScreen
import com.podometer.ui.onboarding.OnboardingViewModel
import com.podometer.ui.settings.SettingsScreen
import com.podometer.ui.settings.SettingsViewModel
import com.podometer.ui.theme.PodometerTheme
import com.podometer.ui.trends.TrendsScreen
import com.podometer.util.areEssentialPermissionsGranted
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity entry point for Podometer.
 *
 * Annotated with @AndroidEntryPoint to enable Hilt injection in this activity.
 * Hosts the Compose NavHost with Onboarding (conditional start destination),
 * Dashboard, and Settings routes.
 *
 * On first launch [OnboardingViewModel.startDestination] emits
 * [Screen.Onboarding.route]; on subsequent launches it emits [Screen.Dashboard.route].
 * The NavHost is only composed once the destination is known (non-null) to avoid a
 * flash of the wrong screen.
 *
 * The PodometerTheme wraps all content so every screen inherits the Material 3
 * colour scheme and typography.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PodometerTheme {
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                val startDestination by onboardingViewModel.startDestination
                    .collectAsStateWithLifecycle()

                // Wait until the DataStore preference has been read before rendering
                // the NavHost to avoid a brief flash of the wrong screen.
                if (startDestination == null) {
                    Box(modifier = Modifier.fillMaxSize())
                    return@PodometerTheme
                }

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val showBottomBar = currentRoute == Screen.Dashboard.route ||
                    currentRoute == Screen.Trends.route

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(
                                currentRoute = currentRoute,
                                onNavigate = { screen ->
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                    },
                ) { scaffoldPadding ->

                NavHost(
                    navController = navController,
                    startDestination = startDestination!!,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding),
                ) {
                    composable(Screen.Onboarding.route) {
                        OnboardingScreen(
                            onPermissionsResult = { results ->
                                onboardingViewModel.completeOnboarding()
                                // Start the tracking service immediately if permissions are granted.
                                // The Dashboard will re-check on resume and handle the denied case.
                                if (areEssentialPermissionsGranted(results)) {
                                    startTrackingServiceIfPermitted(this@MainActivity)
                                }
                                navController.navigate(Screen.Dashboard.route) {
                                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                                }
                            },
                        )
                    }

                    composable(Screen.Dashboard.route) {
                        DashboardScreen(
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route)
                            },
                            onOpenSettings = {
                                // Launch the system app details settings so the user can grant
                                // the denied permissions and return to the Dashboard.
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                ).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
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
                            onSetMinimumStepGoal = viewModel::setMinimumStepGoal,
                            onSetTargetStepGoal = viewModel::setTargetStepGoal,
                            onSetStretchStepGoal = viewModel::setStretchStepGoal,
                            onSetRestDays = viewModel::setRestDays,
                            onExportData = viewModel::exportData,
                            onImportData = viewModel::importData,
                            onResetExportState = viewModel::resetExportState,
                            onNavigateToDonate = {
                                navController.navigate(Screen.Donate.route)
                            },
                            onOpenFeedbackUrl = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/JanJetze/podometer/issues"),
                                )
                                startActivity(intent)
                            },
                            onSetUseTestData = viewModel::setUseTestData,
                        )
                    }

                    composable(Screen.Trends.route) {
                        TrendsScreen()
                    }

                    composable(Screen.Donate.route) {
                        DonateScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onOpenDonateUrl = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://liberapay.com/JanJetze/"),
                                )
                                startActivity(intent)
                            },
                            onOpenBuyMeACoffeeUrl = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://buymeacoffee.com/janjetze"),
                                )
                                startActivity(intent)
                            },
                        )
                    }
                }

                } // end Scaffold
            }
        }
    }
}
