// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.ui.theme.PodometerTheme

/** Ordered list of runtime permissions requested during onboarding. */
internal val ONBOARDING_PERMISSIONS = arrayOf(
    Manifest.permission.ACTIVITY_RECOGNITION,
    Manifest.permission.POST_NOTIFICATIONS,
)

/**
 * Onboarding screen shown on first launch.
 *
 * Displays the app logo, a tagline, plain-language descriptions of each required
 * permission, a privacy callout, and a "Get Started" button that triggers
 * [ActivityResultContracts.RequestMultiplePermissions] for both runtime
 * permissions in a single system dialog sequence.
 *
 * This is a pure presentational composable. ViewModel access and navigation are
 * handled by the caller (see [com.podometer.MainActivity]).
 *
 * @param onPermissionsResult Called with the permissions result map after the system
 *   dialog closes. The caller is responsible for marking onboarding complete and
 *   navigating to the Dashboard — regardless of the grant outcome.
 * @param modifier Optional [Modifier] applied to the root [Scaffold].
 */
@Composable
fun OnboardingScreen(
    onPermissionsResult: (Map<String, Boolean>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = onPermissionsResult,
    )

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ── App logo ───────────────────────────────────────────────────────

            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.cd_onboarding_app_logo),
                modifier = Modifier.size(96.dp),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── App name ───────────────────────────────────────────────────────

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Tagline ────────────────────────────────────────────────────────

            Text(
                text = stringResource(R.string.onboarding_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(40.dp))

            // ── Permission explanations ────────────────────────────────────────

            PermissionRow(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                },
                title = stringResource(R.string.onboarding_permission_activity_title),
                description = stringResource(R.string.onboarding_permission_activity_description),
            )

            Spacer(modifier = Modifier.height(20.dp))

            PermissionRow(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                },
                title = stringResource(R.string.onboarding_permission_notifications_title),
                description = stringResource(R.string.onboarding_permission_notifications_description),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Privacy callout card ───────────────────────────────────────────

            PrivacyCallout()

            Spacer(modifier = Modifier.height(32.dp))

            // ── Get Started button ─────────────────────────────────────────────

            Button(
                onClick = { permissionLauncher.launch(ONBOARDING_PERMISSIONS) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.onboarding_get_started))
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ─── Internal components ──────────────────────────────────────────────────────

/**
 * A permission explanation row showing an icon, title, and description.
 *
 * @param icon Composable slot for the leading permission icon.
 * @param title Short permission title (e.g. "Step counting sensor").
 * @param description Plain-language explanation of why the permission is needed.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun PermissionRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.fillMaxWidth(),
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Card displaying the privacy guarantee callout.
 *
 * Uses [MaterialTheme.colorScheme.secondaryContainer] to visually distinguish the
 * privacy message from the rest of the screen content.
 *
 * @param modifier Optional [Modifier].
 */
@Composable
private fun PrivacyCallout(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.onboarding_privacy_callout),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ─── Preview functions ────────────────────────────────────────────────────────

/** Preview: full onboarding screen in light theme. */
@Preview(showBackground = true, showSystemUi = true, name = "Onboarding — Light")
@Composable
private fun OnboardingScreenLightPreview() {
    PodometerTheme(dynamicColor = false) {
        OnboardingScreen(onPermissionsResult = {})
    }
}

/** Preview: full onboarding screen in dark theme. */
@Preview(showBackground = true, showSystemUi = true, name = "Onboarding — Dark")
@Composable
private fun OnboardingScreenDarkPreview() {
    PodometerTheme(darkTheme = true, dynamicColor = false) {
        OnboardingScreen(onPermissionsResult = {})
    }
}

/** Preview: privacy callout card in isolation. */
@Preview(showBackground = true, name = "Onboarding — PrivacyCallout")
@Composable
private fun PrivacyCalloutPreview() {
    PodometerTheme(dynamicColor = false) {
        PrivacyCallout(modifier = Modifier.padding(16.dp))
    }
}

/** Preview: single permission row in isolation. */
@Preview(showBackground = true, name = "Onboarding — PermissionRow")
@Composable
private fun PermissionRowPreview() {
    PodometerTheme(dynamicColor = false) {
        PermissionRow(
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            },
            title = "Step counting sensor",
            description = "Reads the on-device pedometer to count your steps accurately.",
            modifier = Modifier.padding(16.dp),
        )
    }
}
