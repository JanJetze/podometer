// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.data.sensor.SensorType
import com.podometer.ui.theme.PodometerTheme

// ─── FirstLaunchEmptyState ────────────────────────────────────────────────────

/**
 * Full-width empty state displayed in the TodayCard area when the user has zero steps and
 * no activity transitions — i.e. it is their first launch or they have not yet started moving.
 *
 * Shows an encouraging walking icon and the message "Start walking! Your steps will appear here."
 * This is visually distinct from the TodayCard progress ring so the user is not confused by
 * a zero-step display.
 *
 * This is a pure presentational composable — no ViewModel or Intent access.
 *
 * @param modifier Optional [Modifier] applied to the root [Card].
 */
@Composable
fun FirstLaunchEmptyState(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.empty_first_launch_message),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─── SensorNotice ─────────────────────────────────────────────────────────────

/**
 * Info/warning banner displayed when the step-counting sensor is operating in a degraded mode.
 *
 * - [SensorType.ACCELEROMETER]: Shows an info banner: "Using accelerometer for step detection.
 *   Accuracy may vary."
 * - [SensorType.NONE]: Shows a warning banner: "No step sensor available on this device."
 * - All other [SensorType] values: renders nothing (returns without drawing).
 *
 * Uses [MaterialTheme.colorScheme.secondaryContainer] for the info (ACCELEROMETER) card and
 * [MaterialTheme.colorScheme.errorContainer] for the warning (NONE) card so that severity
 * is conveyed through both colour and icon rather than colour alone.
 *
 * This is a pure presentational composable — no ViewModel access inside this file.
 *
 * @param sensorType The active [SensorType]; only [SensorType.ACCELEROMETER] and
 *                   [SensorType.NONE] produce visible output.
 * @param modifier   Optional [Modifier] applied to the root [Card].
 */
@Composable
fun SensorNotice(
    sensorType: SensorType,
    modifier: Modifier = Modifier,
) {
    when (sensorType) {
        SensorType.ACCELEROMETER -> {
            SensorNoticeBanner(
                icon = Icons.Filled.Info,
                message = stringResource(R.string.sensor_notice_accelerometer),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = modifier,
            )
        }
        SensorType.NONE -> {
            SensorNoticeBanner(
                icon = Icons.Filled.Warning,
                message = stringResource(R.string.sensor_notice_none),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = modifier,
            )
        }
        else -> {
            // STEP_COUNTER and STEP_DETECTOR are nominal — no notice needed.
        }
    }
}

/**
 * Internal helper that renders the coloured card strip for [SensorNotice].
 *
 * @param icon           The icon to display at the start of the banner row.
 * @param message        The notice text to display.
 * @param containerColor Background colour of the [Card].
 * @param contentColor   Colour applied to the [Icon] and [Text].
 * @param modifier       Modifier forwarded from [SensorNotice].
 */
@Composable
private fun SensorNoticeBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// ─── PermissionRecoveryScreen ─────────────────────────────────────────────────

/**
 * Full-screen recovery composable displayed when the user has denied all required permissions.
 *
 * Displays:
 * - A [Settings] icon
 * - An explanation of why permissions are required
 * - A button labelled "Open App Settings" that invokes [onOpenSettings]
 *
 * The [onOpenSettings] callback is responsible for launching the system
 * `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` intent. No intent is launched
 * directly inside this composable — it is purely presentational.
 *
 * @param onOpenSettings Callback invoked when the user taps "Open App Settings".
 *                       The caller should launch [android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS].
 * @param modifier       Optional [Modifier] applied to the root [Column].
 */
@Composable
fun PermissionRecoveryScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permissions_required_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.permissions_required_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(R.string.permissions_button_open_settings))
        }
    }
}

// ─── Preview functions ────────────────────────────────────────────────────────

/** Preview: first launch empty state card. */
@Preview(showBackground = true, name = "EmptyStates — FirstLaunchEmptyState")
@Composable
private fun PreviewFirstLaunchEmptyState() {
    PodometerTheme(dynamicColor = false) {
        FirstLaunchEmptyState(modifier = Modifier.padding(16.dp))
    }
}

/** Preview: sensor notice — accelerometer info banner. */
@Preview(showBackground = true, name = "EmptyStates — SensorNotice (Accelerometer)")
@Composable
private fun PreviewSensorNoticeAccelerometer() {
    PodometerTheme(dynamicColor = false) {
        SensorNotice(
            sensorType = SensorType.ACCELEROMETER,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: sensor notice — no sensor warning banner. */
@Preview(showBackground = true, name = "EmptyStates — SensorNotice (None)")
@Composable
private fun PreviewSensorNoticeNone() {
    PodometerTheme(dynamicColor = false) {
        SensorNotice(
            sensorType = SensorType.NONE,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: sensor notice — step counter (no banner rendered). */
@Preview(showBackground = true, name = "EmptyStates — SensorNotice (StepCounter, no banner)")
@Composable
private fun PreviewSensorNoticeStepCounter() {
    PodometerTheme(dynamicColor = false) {
        SensorNotice(
            sensorType = SensorType.STEP_COUNTER,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** Preview: permissions denied full-screen recovery screen. */
@Preview(showBackground = true, name = "EmptyStates — PermissionRecoveryScreen")
@Composable
private fun PreviewPermissionRecoveryScreen() {
    PodometerTheme(dynamicColor = false) {
        PermissionRecoveryScreen(
            onOpenSettings = {},
        )
    }
}

/** Preview: dark theme permissions recovery. */
@Preview(showBackground = true, name = "EmptyStates — PermissionRecoveryScreen (Dark)")
@Composable
private fun PreviewPermissionRecoveryScreenDark() {
    PodometerTheme(darkTheme = true, dynamicColor = false) {
        PermissionRecoveryScreen(
            onOpenSettings = {},
        )
    }
}
