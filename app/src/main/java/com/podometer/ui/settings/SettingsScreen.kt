// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.podometer.BuildConfig
import com.podometer.R
import com.podometer.ui.theme.PodometerTheme
import java.time.DayOfWeek

private const val NOTIFICATION_STYLE_MINIMAL = "minimal"
private const val NOTIFICATION_STYLE_DETAILED = "detailed"

/**
 * Settings screen allowing the user to configure preferences across five sections:
 * Goals, Calibration, Tracking, Data, and About.
 *
 * This is a pure presentational composable — all state and business logic is managed
 * by the caller (typically via [SettingsViewModel]). The SAF launcher for export is
 * contained here since it involves Android UI contracts.
 *
 * @param uiState Combined settings UI state including all preferences and export state.
 * @param onNavigateBack Called when the user presses the back navigation button.
 * @param onSetDailyStepGoal Called with the new step goal when the user confirms the dialog.
 * @param onSetStrideLengthCm Called with the new stride length (cm) when the slider changes.
 * @param onSetAutoStartEnabled Called when the auto-start switch is toggled.
 * @param onSetNotificationStyle Called with the selected style string when the dropdown changes.
 * @param onSetMinimumStepGoal Called with the validated minimum step goal when the user confirms.
 * @param onSetTargetStepGoal Called with the validated target step goal when the user confirms.
 * @param onSetStretchStepGoal Called with the validated stretch step goal when the user confirms.
 * @param onSetRestDays Called with the updated set of rest days when a chip is toggled.
 * @param onExportData Called with the SAF URI when the user has selected a save location.
 * @param onResetExportState Called to reset the export state after showing a result.
 * @param onOpenFeedbackUrl Called when the user taps the feedback row to open GitHub Issues.
 * @param modifier Optional [Modifier] for the root [Scaffold].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onSetDailyStepGoal: (Int) -> Unit,
    onSetStrideLengthCm: (Int) -> Unit,
    onSetAutoStartEnabled: (Boolean) -> Unit,
    onSetNotificationStyle: (String) -> Unit,
    onSetMinimumStepGoal: (Int) -> Unit = {},
    onSetTargetStepGoal: (Int) -> Unit = {},
    onSetStretchStepGoal: (Int) -> Unit = {},
    onSetRestDays: (Set<DayOfWeek>) -> Unit = {},
    onExportData: (Uri) -> Unit,
    onImportData: (Uri) -> Unit,
    onResetExportState: () -> Unit,
    onNavigateToDonate: () -> Unit = {},
    onOpenFeedbackUrl: () -> Unit = {},
    onSetUseTestData: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showStepGoalDialog by remember { mutableStateOf(false) }
    var showGoalTiersDialog by remember { mutableStateOf(false) }

    val exportFileName = stringResource(R.string.settings_export_file_name)
    val exportSuccessMessage = stringResource(R.string.settings_export_success)
    val exportErrorPrefix = stringResource(R.string.settings_export_error)

    // Show snackbar on success or error, then reset state.
    // try/finally guarantees onResetExportState() runs even if the coroutine is
    // cancelled mid-way through showSnackbar() (e.g. because the user triggers
    // another export and the LaunchedEffect key changes before the snackbar dismisses).
    LaunchedEffect(uiState.exportState) {
        when (uiState.exportState) {
            is ExportState.Success -> {
                try {
                    snackbarHostState.showSnackbar(exportSuccessMessage)
                } finally {
                    onResetExportState()
                }
            }
            is ExportState.Error -> {
                // Capture the message before the finally block resets state to Idle,
                // otherwise uiState.exportState.message would no longer be accessible.
                val message = "$exportErrorPrefix: ${uiState.exportState.message}"
                try {
                    snackbarHostState.showSnackbar(message)
                } finally {
                    onResetExportState()
                }
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

    // SAF launcher for data import — opens the file picker for a JSON file
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            onImportData(uri)
        }
    }

    if (showStepGoalDialog) {
        StepGoalDialog(
            currentGoal = uiState.dailyStepGoal,
            onConfirm = { newGoal ->
                onSetDailyStepGoal(newGoal)
                showStepGoalDialog = false
            },
            onDismiss = { showStepGoalDialog = false },
        )
    }

    if (showGoalTiersDialog) {
        GoalTiersDialog(
            currentMinimum = uiState.minimumStepGoal,
            currentTarget = uiState.targetStepGoal,
            currentStretch = uiState.stretchStepGoal,
            onConfirm = { tiers ->
                onSetMinimumStepGoal(tiers.minimum)
                onSetTargetStepGoal(tiers.target)
                onSetStretchStepGoal(tiers.stretch)
                showGoalTiersDialog = false
            },
            onDismiss = { showGoalTiersDialog = false },
        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── Goals section ──────────────────────────────────────────────

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionHeader(text = stringResource(R.string.settings_section_goals))

            GoalTiersRow(
                minimum = uiState.minimumStepGoal,
                target = uiState.targetStepGoal,
                stretch = uiState.stretchStepGoal,
                onClick = { showGoalTiersDialog = true },
            )

            RestDaysRow(
                title = stringResource(R.string.settings_rest_days_title),
                description = stringResource(R.string.settings_rest_days_description),
                restDays = uiState.restDays,
                onToggleDay = { day ->
                    val updated = if (day in uiState.restDays) {
                        uiState.restDays - day
                    } else {
                        uiState.restDays + day
                    }
                    onSetRestDays(updated)
                },
            )

            // ── Calibration section ────────────────────────────────────────

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionHeader(text = stringResource(R.string.settings_section_calibration))

            SettingRowWithSlider(
                title = stringResource(R.string.settings_stride_length_title),
                description = stringResource(R.string.settings_stride_length_description),
                valueLabel = stringResource(R.string.settings_stride_length_value, uiState.strideLengthCm),
                value = uiState.strideLengthCm.toFloat(),
                valueRange = 50f..120f,
                onValueChange = { onSetStrideLengthCm(it.toInt()) },
            )

            // ── Tracking section ───────────────────────────────────────────

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionHeader(text = stringResource(R.string.settings_section_tracking))

            SettingRowWithSwitch(
                title = stringResource(R.string.settings_auto_start_title),
                description = stringResource(R.string.settings_auto_start_description),
                checked = uiState.autoStartEnabled,
                onCheckedChange = onSetAutoStartEnabled,
            )

            SettingRowWithDropdown(
                title = stringResource(R.string.settings_notification_style_title),
                description = stringResource(R.string.settings_notification_style_description),
                selected = uiState.notificationStyle,
                options = listOf(NOTIFICATION_STYLE_MINIMAL, NOTIFICATION_STYLE_DETAILED),
                optionLabel = { style ->
                    when (style) {
                        NOTIFICATION_STYLE_DETAILED -> stringResource(R.string.settings_notification_style_detailed)
                        else -> stringResource(R.string.settings_notification_style_minimal)
                    }
                },
                onSelect = onSetNotificationStyle,
            )

            // ── Data section ───────────────────────────────────────────────

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionHeader(text = stringResource(R.string.settings_section_data))

            if (uiState.exportState is ExportState.InProgress) {
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
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { openDocumentLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.settings_import_button))
                }
            }

            // ── About section ──────────────────────────────────────────────

            Spacer(modifier = Modifier.height(16.dp))
            SettingsSectionHeader(text = stringResource(R.string.settings_section_about))

            SettingInfoRow(
                title = stringResource(R.string.settings_about_version_title),
                value = stringResource(R.string.settings_about_version_value),
            )

            SettingInfoRow(
                title = stringResource(R.string.settings_about_license_title),
                value = stringResource(R.string.settings_about_license_value),
            )

            SettingInfoRow(
                title = stringResource(R.string.settings_about_source_title),
                value = stringResource(R.string.settings_about_source_url),
            )

            SettingRowWithValue(
                title = stringResource(R.string.settings_about_feedback_title),
                description = stringResource(R.string.settings_about_feedback_description),
                value = "",
                onClick = onOpenFeedbackUrl,
            )

            SettingRowWithValue(
                title = stringResource(R.string.settings_about_donate_title),
                description = stringResource(R.string.settings_about_donate_description),
                value = "",
                onClick = onNavigateToDonate,
            )

            // ── Developer section (debug builds only) ───────────────────────
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSectionHeader(text = "Developer")

                SettingRowWithSwitch(
                    title = "Use test data",
                    description = "Replace real sensor data with generated test data for the step graph",
                    checked = uiState.useTestData,
                    onCheckedChange = onSetUseTestData,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─── Internal composable components ──────────────────────────────────────────

/**
 * Settings section header with primary colour text and a horizontal divider below.
 *
 * @param text The section title to display.
 * @param modifier Optional [Modifier] for the wrapping [Column].
 */
@Composable
private fun SettingsSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * A tappable preference row that displays a title, description, and current value.
 * Tapping the row triggers [onClick] (typically to open a dialog).
 *
 * @param title The preference title.
 * @param description Short description of what the preference controls.
 * @param value The current value displayed as a string.
 * @param onClick Called when the row is tapped.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun SettingRowWithValue(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * A preference row containing a labelled [Slider] for numeric value selection.
 *
 * @param title The preference title.
 * @param description Short description of what the preference controls.
 * @param valueLabel Formatted current value label (e.g. "75 cm").
 * @param value Current slider value.
 * @param valueRange Allowed slider range.
 * @param onValueChange Called with the new value when the slider changes.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun SettingRowWithSlider(
    title: String,
    description: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = (valueRange.endInclusive - valueRange.start).toInt() - 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * A preference row with a [Switch] toggle control.
 *
 * @param title The preference title.
 * @param description Short description of what the preference controls.
 * @param checked Whether the switch is currently on.
 * @param onCheckedChange Called when the switch is toggled.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun SettingRowWithSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * A preference row that opens a dropdown [DropdownMenu] to select from [options].
 *
 * @param title The preference title.
 * @param description Short description of what the preference controls.
 * @param selected The currently selected option string.
 * @param options List of all available option strings.
 * @param optionLabel Mapping function from option string to display label.
 * @param onSelect Called with the newly selected option string.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun SettingRowWithDropdown(
    title: String,
    description: String,
    selected: String,
    options: List<String>,
    optionLabel: @Composable (String) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = optionLabel(selected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/**
 * A static read-only info row for the About section, displaying a title and value.
 *
 * @param title The info label (e.g. "Version").
 * @param value The info value (e.g. "1.0.0").
 * @param modifier Optional [Modifier].
 */
@Composable
private fun SettingInfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.5f),
        )
    }
}

/**
 * A tappable preference row that displays the current three goal tiers (minimum, target, stretch)
 * in a compact hierarchy. Tapping the row opens the [GoalTiersDialog].
 *
 * @param minimum The current minimum step goal.
 * @param target The current target step goal.
 * @param stretch The current stretch step goal.
 * @param onClick Called when the row is tapped.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun GoalTiersRow(
    minimum: Int,
    target: Int,
    stretch: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_goal_tiers_title),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_goal_tiers_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.settings_goal_minimum_label) + ": $minimum",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Text(
                text = stringResource(R.string.settings_goal_target_label) + ": $target",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.settings_goal_stretch_label) + ": $stretch",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/**
 * A preference row with a row of [FilterChip] toggles for each day of the week.
 * Selected days are visually distinct and represent rest days.
 *
 * @param title The preference title.
 * @param description Short description of what the preference controls.
 * @param restDays The currently selected rest days.
 * @param onToggleDay Called with the [DayOfWeek] when a chip is tapped.
 * @param modifier Optional [Modifier].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestDaysRow(
    title: String,
    description: String,
    restDays: Set<DayOfWeek>,
    onToggleDay: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayLabels = listOf(
        DayOfWeek.MONDAY to stringResource(R.string.settings_rest_day_mon),
        DayOfWeek.TUESDAY to stringResource(R.string.settings_rest_day_tue),
        DayOfWeek.WEDNESDAY to stringResource(R.string.settings_rest_day_wed),
        DayOfWeek.THURSDAY to stringResource(R.string.settings_rest_day_thu),
        DayOfWeek.FRIDAY to stringResource(R.string.settings_rest_day_fri),
        DayOfWeek.SATURDAY to stringResource(R.string.settings_rest_day_sat),
        DayOfWeek.SUNDAY to stringResource(R.string.settings_rest_day_sun),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            dayLabels.forEach { (day, label) ->
                val selected = day in restDays
                val cdState = if (selected) "on" else "off"
                val cdText = stringResource(R.string.cd_rest_day_chip, label, cdState)
                FilterChip(
                    selected = selected,
                    onClick = { onToggleDay(day) },
                    label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.semantics { contentDescription = cdText },
                )
            }
        }
    }
}

/**
 * AlertDialog for entering a new daily step goal.
 *
 * Contains a number-input [OutlinedTextField]. The confirm button is only enabled
 * when [validateStepGoal] returns a non-null value.
 *
 * @param currentGoal The current goal, pre-populated in the text field.
 * @param onConfirm Called with the valid parsed goal when the user confirms.
 * @param onDismiss Called when the dialog is dismissed without confirmation.
 */
@Composable
private fun StepGoalDialog(
    currentGoal: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf(currentGoal.toString()) }
    val parsed = validateStepGoal(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_step_goal_dialog_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(text = stringResource(R.string.settings_step_goal_dialog_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = parsed == null,
                supportingText = if (parsed == null) {
                    { Text(text = stringResource(R.string.settings_step_goal_dialog_error)) }
                } else null,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (parsed != null) onConfirm(parsed) },
                enabled = parsed != null,
            ) {
                Text(text = stringResource(R.string.settings_step_goal_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_step_goal_dialog_cancel))
            }
        },
    )
}

/**
 * AlertDialog for entering the three step goal tiers (minimum, target, stretch).
 *
 * All three fields are number inputs. The confirm button is only enabled when
 * [validateGoalTiers] returns a non-null [GoalTiers] (i.e. all three values are
 * in range and satisfy the ordering: minimum < target < stretch).
 *
 * @param currentMinimum The current minimum step goal, pre-populated in the field.
 * @param currentTarget The current target step goal, pre-populated in the field.
 * @param currentStretch The current stretch step goal, pre-populated in the field.
 * @param onConfirm Called with the validated [GoalTiers] when the user confirms.
 * @param onDismiss Called when the dialog is dismissed without confirmation.
 */
@Composable
private fun GoalTiersDialog(
    currentMinimum: Int,
    currentTarget: Int,
    currentStretch: Int,
    onConfirm: (GoalTiers) -> Unit,
    onDismiss: () -> Unit,
) {
    var minimumInput by remember { mutableStateOf(currentMinimum.toString()) }
    var targetInput by remember { mutableStateOf(currentTarget.toString()) }
    var stretchInput by remember { mutableStateOf(currentStretch.toString()) }
    val tiers = validateGoalTiers(minimumInput, targetInput, stretchInput)
    val isError = tiers == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_goal_tiers_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minimumInput,
                    onValueChange = { minimumInput = it },
                    label = { Text(text = stringResource(R.string.settings_goal_minimum_label)) },
                    placeholder = { Text(text = stringResource(R.string.settings_goal_minimum_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it },
                    label = { Text(text = stringResource(R.string.settings_goal_target_label)) },
                    placeholder = { Text(text = stringResource(R.string.settings_goal_target_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = stretchInput,
                    onValueChange = { stretchInput = it },
                    label = { Text(text = stringResource(R.string.settings_goal_stretch_label)) },
                    placeholder = { Text(text = stringResource(R.string.settings_goal_stretch_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isError) {
                    Text(
                        text = stringResource(R.string.settings_goal_tiers_dialog_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (tiers != null) onConfirm(tiers) },
                enabled = tiers != null,
            ) {
                Text(text = stringResource(R.string.settings_goal_tiers_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_goal_tiers_dialog_cancel))
            }
        },
    )
}

// ─── Previews ────────────────────────────────────────────────────────────────

/** Preview: Settings screen in idle state — all sections visible with default goal tiers. */
@Preview(showBackground = true, name = "Settings - Idle")
@Composable
private fun SettingsScreenIdlePreview() {
    PodometerTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                dailyStepGoal = 10_000,
                strideLengthCm = 75,
                autoStartEnabled = true,
                notificationStyle = NOTIFICATION_STYLE_MINIMAL,
                exportState = ExportState.Idle,
                minimumStepGoal = 5_000,
                targetStepGoal = 8_000,
                stretchStepGoal = 12_000,
            ),
            onNavigateBack = {},
            onSetDailyStepGoal = {},
            onSetStrideLengthCm = {},
            onSetAutoStartEnabled = {},
            onSetNotificationStyle = {},
            onExportData = {},
            onImportData = {},
            onResetExportState = {},
        )
    }
}

/** Preview: Settings screen with weekend rest days configured. */
@Preview(showBackground = true, name = "Settings - Weekend Rest Days")
@Composable
private fun SettingsScreenWeekendRestDaysPreview() {
    PodometerTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                minimumStepGoal = 5_000,
                targetStepGoal = 8_000,
                stretchStepGoal = 12_000,
                restDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                exportState = ExportState.Idle,
            ),
            onNavigateBack = {},
            onSetDailyStepGoal = {},
            onSetStrideLengthCm = {},
            onSetAutoStartEnabled = {},
            onSetNotificationStyle = {},
            onExportData = {},
            onImportData = {},
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
            uiState = SettingsUiState(exportState = ExportState.InProgress),
            onNavigateBack = {},
            onSetDailyStepGoal = {},
            onSetStrideLengthCm = {},
            onSetAutoStartEnabled = {},
            onSetNotificationStyle = {},
            onExportData = {},
            onImportData = {},
            onResetExportState = {},
        )
    }
}

/** Preview: Settings with auto-start disabled and detailed notification style. */
@Preview(showBackground = true, name = "Settings - Detailed Notifications")
@Composable
private fun SettingsScreenDetailedPreview() {
    PodometerTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                dailyStepGoal = 8_000,
                strideLengthCm = 90,
                autoStartEnabled = false,
                notificationStyle = NOTIFICATION_STYLE_DETAILED,
                exportState = ExportState.Idle,
            ),
            onNavigateBack = {},
            onSetDailyStepGoal = {},
            onSetStrideLengthCm = {},
            onSetAutoStartEnabled = {},
            onSetNotificationStyle = {},
            onExportData = {},
            onImportData = {},
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
            uiState = SettingsUiState(exportState = ExportState.Success),
            onNavigateBack = {},
            onSetDailyStepGoal = {},
            onSetStrideLengthCm = {},
            onSetAutoStartEnabled = {},
            onSetNotificationStyle = {},
            onExportData = {},
            onImportData = {},
            onResetExportState = {},
        )
    }
}

/** Preview: Settings screen when export failed with an error message (snackbar would appear). */
@Preview(showBackground = true, name = "Settings - Export Error")
@Composable
private fun SettingsScreenErrorPreview() {
    PodometerTheme {
        SettingsScreen(
            uiState = SettingsUiState(exportState = ExportState.Error("Cannot open output stream for URI")),
            onNavigateBack = {},
            onSetDailyStepGoal = {},
            onSetStrideLengthCm = {},
            onSetAutoStartEnabled = {},
            onSetNotificationStyle = {},
            onExportData = {},
            onImportData = {},
            onResetExportState = {},
        )
    }
}

/** Preview: StepGoalDialog with a valid pre-populated value — no error shown. */
@Preview(showBackground = true, name = "StepGoalDialog - Valid Input")
@Composable
private fun StepGoalDialogValidPreview() {
    PodometerTheme {
        StepGoalDialog(
            currentGoal = 10_000,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

/** Preview: StepGoalDialog with a moderate valid goal — shows the dialog works with various goals. */
@Preview(showBackground = true, name = "StepGoalDialog - Moderate Goal")
@Composable
private fun StepGoalDialogModeratePreview() {
    PodometerTheme {
        StepGoalDialog(
            currentGoal = 5_000,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

/** Preview: StepGoalDialog with a low valid goal — shows the dialog works with small targets. */
@Preview(showBackground = true, name = "StepGoalDialog - Low Goal")
@Composable
private fun StepGoalDialogLowPreview() {
    PodometerTheme {
        StepGoalDialog(
            currentGoal = 1_000,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

/** Preview: GoalTiersDialog with default valid values pre-populated. */
@Preview(showBackground = true, name = "GoalTiersDialog - Default Values")
@Composable
private fun GoalTiersDialogDefaultPreview() {
    PodometerTheme {
        GoalTiersDialog(
            currentMinimum = 5_000,
            currentTarget = 8_000,
            currentStretch = 12_000,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

/** Preview: GoalTiersDialog showing the error state when goals are not strictly ordered. */
@Preview(showBackground = true, name = "GoalTiersDialog - Invalid (Error State)")
@Composable
private fun GoalTiersDialogInvalidPreview() {
    PodometerTheme {
        GoalTiersDialog(
            currentMinimum = 8_000,
            currentTarget = 5_000,
            currentStretch = 12_000,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

/** Preview: GoalTiersRow with default goal tiers showing tier hierarchy. */
@Preview(showBackground = true, name = "GoalTiersRow - Default Tiers")
@Composable
private fun GoalTiersRowPreview() {
    PodometerTheme {
        GoalTiersRow(
            minimum = 5_000,
            target = 8_000,
            stretch = 12_000,
            onClick = {},
        )
    }
}

/** Preview: RestDaysRow with no days selected (no rest days). */
@Preview(showBackground = true, name = "RestDaysRow - No Rest Days")
@Composable
private fun RestDaysRowNoSelectionPreview() {
    PodometerTheme {
        RestDaysRow(
            title = "Rest days",
            description = "Goals are relaxed on these days",
            restDays = emptySet(),
            onToggleDay = {},
        )
    }
}

/** Preview: RestDaysRow with weekend days selected as rest days. */
@Preview(showBackground = true, name = "RestDaysRow - Weekend Rest Days")
@Composable
private fun RestDaysRowWeekendPreview() {
    PodometerTheme {
        RestDaysRow(
            title = "Rest days",
            description = "Goals are relaxed on these days",
            restDays = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            onToggleDay = {},
        )
    }
}

/** Preview: RestDaysRow with all days selected as rest days. */
@Preview(showBackground = true, name = "RestDaysRow - All Days Rest")
@Composable
private fun RestDaysRowAllDaysPreview() {
    PodometerTheme {
        RestDaysRow(
            title = "Rest days",
            description = "Goals are relaxed on these days",
            restDays = DayOfWeek.values().toSet(),
            onToggleDay = {},
        )
    }
}
