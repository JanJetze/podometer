// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.R
import com.podometer.ui.theme.PodometerTheme

/**
 * Settings screen that allows the user to export all app data as JSON.
 *
 * This is a pure presentational composable — all state and business logic
 * is managed by the caller (typically via [SettingsViewModel]). The SAF
 * launcher is contained here since it involves Android UI contracts, but
 * the actual export logic is triggered via [onExportData].
 *
 * @param exportState The current state of the export operation.
 * @param onNavigateBack Called when the user presses the back navigation button.
 * @param onExportData Called with the SAF URI when the user has selected a save location.
 * @param onResetExportState Called to reset the export state after showing a result.
 * @param modifier Optional [Modifier] for the root [Scaffold].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    exportState: ExportState,
    onNavigateBack: () -> Unit,
    onExportData: (Uri) -> Unit,
    onResetExportState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val exportFileName = stringResource(R.string.settings_export_file_name)
    val exportSuccessMessage = stringResource(R.string.settings_export_success)
    val exportErrorPrefix = stringResource(R.string.settings_export_error)

    // Show snackbar on success or error, then reset state
    LaunchedEffect(exportState) {
        when (exportState) {
            is ExportState.Success -> {
                snackbarHostState.showSnackbar(exportSuccessMessage)
                onResetExportState()
            }
            is ExportState.Error -> {
                snackbarHostState.showSnackbar("$exportErrorPrefix: ${exportState.message}")
                onResetExportState()
            }
            else -> Unit
        }
    }

    // SAF launcher — opens the "Create Document" file picker
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri != null) {
            onExportData(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.screen_settings)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_section_data),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (exportState is ExportState.InProgress) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Text(
                            text = stringResource(R.string.settings_export_progress),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                Button(
                    onClick = { createDocumentLauncher.launch(exportFileName) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.settings_export_button))
                }
            }
        }
    }
}

// ─── Previews ────────────────────────────────────────────────────────────────

/** Preview: Settings screen in idle state (export button visible). */
@Preview(showBackground = true, name = "Settings - Idle")
@Composable
private fun SettingsScreenIdlePreview() {
    PodometerTheme {
        SettingsScreen(
            exportState = ExportState.Idle,
            onNavigateBack = {},
            onExportData = {},
            onResetExportState = {},
        )
    }
}

/** Preview: Settings screen while export is in progress. */
@Preview(showBackground = true, name = "Settings - Exporting")
@Composable
private fun SettingsScreenExportingPreview() {
    PodometerTheme {
        SettingsScreen(
            exportState = ExportState.InProgress,
            onNavigateBack = {},
            onExportData = {},
            onResetExportState = {},
        )
    }
}

/** Preview: Settings screen after a successful export (snackbar would appear). */
@Preview(showBackground = true, name = "Settings - Export Success")
@Composable
private fun SettingsScreenSuccessPreview() {
    PodometerTheme {
        SettingsScreen(
            exportState = ExportState.Success,
            onNavigateBack = {},
            onExportData = {},
            onResetExportState = {},
        )
    }
}
