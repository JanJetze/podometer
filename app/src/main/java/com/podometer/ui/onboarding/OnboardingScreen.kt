// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.podometer.R

/**
 * Placeholder Onboarding screen.
 *
 * The actual onboarding flow will be implemented in a separate task.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = stringResource(R.string.screen_onboarding),
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}
