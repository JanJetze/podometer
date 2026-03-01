// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.donate

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.ui.theme.PodometerTheme

/**
 * Static donate screen encouraging users to support development via Liberapay
 * or Buy Me a Coffee.
 *
 * Displays a heart icon, a brief message explaining the app is free and donations
 * are welcome, two donate buttons (Liberapay primary, Buy Me a Coffee secondary),
 * and a short note about both platforms.
 * No ViewModel is needed — the only actions are opening external URLs.
 *
 * @param onNavigateBack Called when the user presses the back button.
 * @param onOpenDonateUrl Called when the user taps the Liberapay button.
 * @param onOpenBuyMeACoffeeUrl Called when the user taps the Buy Me a Coffee button.
 * @param modifier Optional [Modifier] for the root [Scaffold].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(
    onNavigateBack: () -> Unit,
    onOpenDonateUrl: () -> Unit,
    onOpenBuyMeACoffeeUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.screen_donate)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.screen_donate),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.donate_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onOpenDonateUrl,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.donate_button))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.donate_or),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onOpenBuyMeACoffeeUrl,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.donate_buymeacoffee_button))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.donate_platforms_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

/** Preview: Donate screen in light theme. */
@Preview(showBackground = true, name = "Donate - Light")
@Composable
private fun DonateScreenLightPreview() {
    PodometerTheme {
        DonateScreen(
            onNavigateBack = {},
            onOpenDonateUrl = {},
            onOpenBuyMeACoffeeUrl = {},
        )
    }
}

/** Preview: Donate screen in dark theme. */
@Preview(showBackground = true, name = "Donate - Dark")
@Composable
private fun DonateScreenDarkPreview() {
    PodometerTheme(darkTheme = true) {
        DonateScreen(
            onNavigateBack = {},
            onOpenDonateUrl = {},
            onOpenBuyMeACoffeeUrl = {},
        )
    }
}
