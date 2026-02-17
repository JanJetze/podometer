// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.podometer.ui.dashboard.DashboardScreen
import com.podometer.ui.Screen
import com.podometer.ui.settings.SettingsScreen
import com.podometer.ui.theme.PodometerTheme

/**
 * Single-Activity entry point for Podometer.
 *
 * Hosts the Compose NavHost with Dashboard (start destination) and Settings routes.
 * The PodometerTheme wraps all content so every screen inherits the Material 3 colour
 * scheme and typography.
 */
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
                        SettingsScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                        )
                    }
                }
            }
        }
    }
}
