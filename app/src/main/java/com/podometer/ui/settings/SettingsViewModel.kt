// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.data.repository.PreferencesManager
import com.podometer.domain.usecase.ExportDataUseCase
import com.podometer.domain.usecase.ImportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * Represents the current state of the export operation.
 */
sealed interface ExportState {
    /** No export has been started or the last export has been dismissed. */
    data object Idle : ExportState

    /** An export is currently in progress. */
    data object InProgress : ExportState

    /** The export completed successfully. */
    data object Success : ExportState

    /** The export failed with the given [message]. */
    data class Error(val message: String) : ExportState
}

/**
 * Combined UI state for the Settings screen, including all user preferences.
 *
 * @param dailyStepGoal The user's daily step goal (e.g. 10,000).
 * @param strideLengthCm Stride length in centimetres for UI display (e.g. 75).
 * @param autoStartEnabled Whether auto-start on boot is enabled.
 * @param notificationStyle The selected notification style ("minimal" or "detailed").
 * @param exportState The current state of the export operation.
 */
data class SettingsUiState(
    val dailyStepGoal: Int = 10_000,
    val strideLengthCm: Int = 75,
    val autoStartEnabled: Boolean = true,
    val notificationStyle: String = "minimal",
    val exportState: ExportState = ExportState.Idle,
    val useTestData: Boolean = false,
)

/**
 * ViewModel for the Settings screen.
 *
 * Manages all user preferences from [PreferencesManager] as well as the data
 * export flow via [ExportDataUseCase].
 *
 * All preferences are exposed via a single [uiState] [StateFlow]. Individual
 * write operations are provided as suspend-backed functions called from the UI.
 *
 * @param exportDataUseCase Use case that gathers and serializes all app data.
 * @param preferencesManager DataStore-backed preferences repository.
 * @param context Application context used to open an output stream for the SAF URI.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportDataUseCase: ExportDataUseCase,
    private val importDataUseCase: ImportDataUseCase,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)

    /** Observable state of the current export operation. */
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /**
     * Combined UI state combining all preference flows and export state.
     *
     * Starts with default values; updates reactively as preferences or export state change.
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferencesManager.dailyStepGoal(),
            preferencesManager.strideLengthKm(),
            preferencesManager.isAutoStartEnabled(),
        ) { goal, stride, autoStart -> Triple(goal, stride, autoStart) },
        preferencesManager.notificationStyle(),
        _exportState,
        preferencesManager.useTestData(),
    ) { (dailyStepGoal, strideLengthKm, autoStart), notifStyle, exportState, useTestData ->
        SettingsUiState(
            dailyStepGoal = dailyStepGoal,
            strideLengthCm = strideLengthKmToCm(strideLengthKm),
            autoStartEnabled = autoStart,
            notificationStyle = notifStyle,
            exportState = exportState,
            useTestData = useTestData,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    // ─── Preference updates ───────────────────────────────────────────────────

    /**
     * Persists the given [goal] as the user's daily step goal.
     *
     * @param goal A positive step goal integer, typically in the range [1, 100_000].
     */
    fun setDailyStepGoal(goal: Int) {
        viewModelScope.launch {
            preferencesManager.setDailyStepGoal(goal)
        }
    }

    /**
     * Persists the given [cm] stride length (in centimetres) by converting to km for storage.
     *
     * @param cm Stride length in centimetres (e.g. 75 for 75 cm).
     */
    fun setStrideLengthCm(cm: Int) {
        viewModelScope.launch {
            preferencesManager.setStrideLengthKm(strideLengthCmToKm(cm))
        }
    }

    /**
     * Persists the given [enabled] flag for the auto-start preference.
     *
     * @param enabled `true` to start the tracking service on boot; `false` to disable.
     */
    fun setAutoStartEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoStartEnabled(enabled)
        }
    }

    /**
     * Persists the given [style] as the notification style preference.
     *
     * @param style "minimal" or "detailed".
     */
    fun setNotificationStyle(style: String) {
        viewModelScope.launch {
            preferencesManager.setNotificationStyle(style)
        }
    }

    /**
     * Persists the given [enabled] flag for the debug test-data mode.
     *
     * @param enabled `true` to use generated test data; `false` to use real sensor data.
     */
    fun setUseTestData(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setUseTestData(enabled)
        }
    }

    // ─── Export ───────────────────────────────────────────────────────────────

    /**
     * Starts the export process using the [uri] selected by the user via SAF.
     *
     * Switches to [Dispatchers.IO] for the database queries and file write.
     * Updates [exportState] to reflect the progress, success, or failure.
     *
     * @param uri The URI of the file chosen by the user to save the export.
     */
    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _exportState.value = ExportState.InProgress
            try {
                withContext(Dispatchers.IO) {
                    val exportData = exportDataUseCase.buildExportData()
                    val jsonString = exportDataUseCase.serializeToJson(exportData)
                    val outputStream = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Cannot open output stream for URI")
                    outputStream.use { stream ->
                        stream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                _exportState.value = ExportState.Success
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Imports data from a previously exported JSON file at the given SAF [uri].
     *
     * Reads the file contents, deserializes the JSON, and inserts all records
     * into the local database.
     *
     * @param uri The URI of the JSON file chosen by the user.
     */
    fun importData(uri: Uri) {
        viewModelScope.launch {
            _exportState.value = ExportState.InProgress
            try {
                withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Cannot open input stream for URI")
                    val jsonString = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    importDataUseCase.importFromJson(jsonString)
                }
                _exportState.value = ExportState.Success
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** Resets [exportState] back to [ExportState.Idle] after the user dismisses a result. */
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    companion object {
        /** The device model string injected into export metadata. */
        val deviceModel: String = Build.MODEL
    }
}
