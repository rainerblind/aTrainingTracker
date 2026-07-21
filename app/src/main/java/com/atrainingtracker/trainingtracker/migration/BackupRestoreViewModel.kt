/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BackupRestoreViewModel(application: Application) : AndroidViewModel(application) {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
        data class Progress(val current: Int, val total: Int, val name: String) : UiState()
        data class MappingRequired(val uri: Uri, val analysis: ImportEngine.AnalysisResult) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    fun createBackup(context: Context, onBackupReady: (Uri) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            val backupFile = BackupManager.createBackup(context)
            if (backupFile != null) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", backupFile)
                onBackupReady(uri)
                _uiState.value = UiState.Idle
            } else {
                _uiState.value = UiState.Error("Failed to create backup")
            }
        }
    }

    fun uploadToDropbox(context: Context) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val backupFile = withContext(Dispatchers.IO) { BackupManager.createBackup(context) }
            if (backupFile != null) {
                val success = DropboxBackupManager.uploadBackup(context, backupFile)
                if (success) {
                    _uiState.value = UiState.Success("Backup uploaded to Dropbox")
                } else {
                    _uiState.value = UiState.Error("Dropbox upload failed")
                }
            } else {
                _uiState.value = UiState.Error("Failed to create backup")
            }
        }
    }

    fun restoreFromDropbox(context: Context) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val tempFile = File(context.cacheDir, "dropbox_restore.attbackup")
            val downloadSuccess = DropboxBackupManager.downloadBackup(context, tempFile)
            if (downloadSuccess) {
                val success = withContext(Dispatchers.IO) { MigrationEngine.performFullRestore(context, tempFile) }
                if (!success) {
                    _uiState.value = UiState.Error("Restore failed")
                }
            } else {
                _uiState.value = UiState.Error("Failed to download from Dropbox")
            }
        }
    }

    fun performFullRestore(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            val tempFile = File(context.cacheDir, "restore_upload.attbackup")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                val success = MigrationEngine.performFullRestore(context, tempFile)
                if (!success) {
                    _uiState.value = UiState.Error("Restore failed")
                }
                // If success, the app restarts, so we don't need to update state
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to process backup file: ${e.message}")
            }
        }
    }

    fun analyzeImport(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            val tempFile = File(context.cacheDir, "import_analysis.attbackup")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                val analysis = ImportEngine.analyzeBackup(context, tempFile)
                if (analysis.sportTypes.isNotEmpty()) {
                    _uiState.value = UiState.MappingRequired(uri, analysis)
                } else {
                    _uiState.value = UiState.Error("No workouts found in backup file")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Analysis failed: ${e.message}")
            }
        }
    }

    fun performIncrementalImport(context: Context, uri: Uri, sportMapping: Map<Long, Long>, equipmentMapping: Map<Long, Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            val tempFile = File(context.cacheDir, "import_upload.attbackup")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val importedCount = ImportEngine.performIncrementalImport(
                    context, tempFile, sportMapping, equipmentMapping, 
                    object : ImportEngine.ProgressListener {
                        override fun onProgress(current: Int, total: Int, name: String) {
                            _uiState.value = UiState.Progress(current, total, name)
                        }
                    }
                )

                _uiState.value = UiState.Success("Successfully imported $importedCount workouts")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Import failed: ${e.message}")
            }
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }
}
