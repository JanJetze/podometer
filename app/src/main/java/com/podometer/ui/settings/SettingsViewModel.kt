// SPDX-License-Identifier: GPL-3.0-or-later
package com.podometer.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podometer.domain.usecase.ExportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * ViewModel for the Settings screen.
 *
 * Manages the data export flow: gathering all data via [ExportDataUseCase],
 * serializing to JSON, and writing to a URI provided by the Storage Access
 * Framework (SAF).
 *
 * @param exportDataUseCase Use case that gathers and serializes all app data.
 * @param context Application context used to open an output stream for the SAF URI.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportDataUseCase: ExportDataUseCase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)

    /** Observable state of the current export operation. */
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

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

    /** Resets [exportState] back to [ExportState.Idle] after the user dismisses a result. */
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    companion object {
        /** The device model string injected into export metadata. */
        val deviceModel: String = Build.MODEL
    }
}
