// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.podometer.R
import com.podometer.ui.Screen

/**
 * Bottom navigation bar with Dashboard and Activities tabs.
 *
 * @param currentRoute The currently active navigation route.
 * @param onNavigate   Callback invoked with the target [Screen] when a tab is tapped.
 * @param modifier     Optional [Modifier] applied to the [NavigationBar].
 */
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.screen_dashboard)) },
            selected = currentRoute == Screen.Dashboard.route,
            onClick = { onNavigate(Screen.Dashboard) },
        )
        NavigationBarItem(
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text(stringResource(R.string.screen_activities)) },
            selected = currentRoute == Screen.Activities.route,
            onClick = { onNavigate(Screen.Activities) },
        )
    }
}
