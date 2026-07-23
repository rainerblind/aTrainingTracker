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
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BackupRestoreViewModel(application: Application) : AndroidViewModel(application) {

    sealed class UiState {
        object Idle : UiState()
        data class Loading(val message: String? = null) : UiState()
        data class Success(val message: String) : UiState()
        data class Error(val message: String) : UiState()
        data class Progress(val current: Int, val total: Int, val name: String) : UiState()
        data class MappingRequired(val uri: Uri, val analysis: ImportEngine.AnalysisResult) : UiState()
    }

    data class ClusterInteraction(
        val date: String,
        val start: LatLng,
        val end: LatLng,
        val apex: LatLng,
        val distance: Double,
        val bSportType: BSportType,
        val polyline: String,
        val existingClusters: List<WorkoutCluster>
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _activeInteraction = MutableStateFlow<ClusterInteraction?>(null)
    val activeInteraction: StateFlow<ClusterInteraction?> = _activeInteraction.asStateFlow()

    private var clusterDecision: CompletableDeferred<Pair<Long?, String?>>? = null

    fun provideClusterDecision(clusterId: Long?, name: String?) {
        clusterDecision?.complete(Pair(clusterId, name))
    }

    data class LastBackupInfo(val timestamp: Long, val status: String)

    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)

    val lastBackupInfo: StateFlow<LastBackupInfo?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "last_backup_timestamp" || key == "last_backup_status") {
                trySend(readLastBackupInfo(p))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(readLastBackupInfo(prefs))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), readLastBackupInfo(prefs))

    private fun readLastBackupInfo(p: SharedPreferences): LastBackupInfo? {
        val ts = p.getLong("last_backup_timestamp", 0L)
        val st = p.getString("last_backup_status", null)
        return if (ts == 0L && st == null) null else LastBackupInfo(ts, st ?: "UNKNOWN")
    }

    var automatedBackupsEnabled by mutableStateOf(prefs.getBoolean("automated_backups", true))
        private set

    var backupIntervalDays by mutableIntStateOf(prefs.getString("backup_interval_days", "1")?.toInt() ?: 1)
        private set

    // Clustering Tolerances (ATT-315)
    var endpointTolerance by mutableStateOf(TrainingApplication.getClusterTolEndpoints())
    var apexTolerance by mutableStateOf(TrainingApplication.getClusterTolApex())
    var distanceTolerance by mutableStateOf(TrainingApplication.getClusterTolDistance())

    fun updateAutomatedBackupsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("automated_backups", enabled).apply()
        automatedBackupsEnabled = enabled
        BackupWorker.schedule(getApplication())
    }

    fun updateBackupIntervalDays(days: Int) {
        prefs.edit().putString("backup_interval_days", days.toString()).apply()
        backupIntervalDays = days
        BackupWorker.schedule(getApplication())
    }

    fun saveClusteringTolerances() {
        prefs.edit()
            .putFloat(TrainingApplication.SP_CLUSTER_TOL_ENDPOINTS, endpointTolerance)
            .putFloat(TrainingApplication.SP_CLUSTER_TOL_APEX, apexTolerance)
            .putFloat(TrainingApplication.SP_CLUSTER_TOL_DISTANCE, distanceTolerance)
            .apply()
    }

    fun createBackup(context: Context, onBackupReady: (Uri) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading("Preparing backup...")
            val backupFile = BackupManager.createBackup(context, object : BackupManager.ProgressListener {
                override fun onProgress(message: String) {
                    _uiState.value = UiState.Loading(message)
                }
            })
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
            _uiState.value = UiState.Loading("Creating backup...")
            val backupFile = withContext(Dispatchers.IO) { 
                BackupManager.createBackup(context, object : BackupManager.ProgressListener {
                    override fun onProgress(message: String) {
                        _uiState.value = UiState.Loading(message)
                    }
                }) 
            }
            if (backupFile != null) {
                _uiState.value = UiState.Loading("Uploading to Dropbox...")
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
            _uiState.value = UiState.Loading("Downloading from Dropbox...")
            val tempFile = File(context.cacheDir, "dropbox_restore.attbackup")
            val downloadSuccess = DropboxBackupManager.downloadBackup(context, tempFile)
            if (downloadSuccess) {
                val success = withContext(Dispatchers.IO) { 
                    MigrationEngine.performFullRestore(context, tempFile, object : MigrationEngine.ProgressListener {
                        override fun onProgress(message: String) {
                            _uiState.value = UiState.Loading(message)
                        }
                    }) 
                }
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
            _uiState.value = UiState.Loading("Processing backup file...")
            val tempFile = File(context.cacheDir, "restore_upload.attbackup")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                val success = MigrationEngine.performFullRestore(context, tempFile, object : MigrationEngine.ProgressListener {
                    override fun onProgress(message: String) {
                        _uiState.value = UiState.Loading(message)
                    }
                })
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
            _uiState.value = UiState.Loading("Analyzing backup content...")
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
            _uiState.value = UiState.Loading("Preparing import...")
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
                        override fun onStatus(message: String) {
                            _uiState.value = UiState.Loading(message)
                        }
                    }
                )

                _uiState.value = UiState.Success("Successfully imported $importedCount workouts")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Import failed: ${e.message}")
            }
        }
    }

    fun importLegacyFile(context: Context, uri: Uri, format: String) {
        saveClusteringTolerances()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading("Importing legacy file...")
            val tempFile = File(context.cacheDir, "legacy_import.$format")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            val success = when (format.lowercase()) {
                "tcx" -> LegacyImportEngine.importFromTcx(context, tempFile, createLegacyListener())
                else -> false
            }
            tempFile.delete()
            if (success) {
                _uiState.value = UiState.Success("Successfully imported workout from $format file.")
            } else {
                _uiState.value = UiState.Error("Failed to import workout. It might already exist or the file format is invalid.")
            }
        }
    }

    fun bulkRecoverLegacyData(context: Context, format: String) {
        saveClusteringTolerances()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading("Initializing legacy recovery...")
            val count = LegacyImportEngine.bulkRecoverFromDropbox(context, format, createLegacyListener())
            _uiState.value = UiState.Success("Recovery finished. Imported $count new workouts from $format files.")
        }
    }

    private fun createLegacyListener() = object : LegacyImportEngine.ProgressListener {
        override fun onProgress(current: Int, total: Int, name: String) {
            _uiState.value = UiState.Progress(current, total, name)
        }

        override fun onStatus(message: String) {
            _uiState.value = UiState.Loading(message)
        }

        override suspend fun onNewClusterCandidate(
            date: String,
            start: LatLng,
            end: LatLng,
            apex: LatLng,
            distance: Double,
            bSportType: BSportType,
            polyline: String
        ): Pair<Long?, String?> {
            val deferred = CompletableDeferred<Pair<Long?, String?>>()
            clusterDecision = deferred
            val clusters = withContext(Dispatchers.IO) {
                WorkoutClusterDatabaseManager.getInstance(getApplication()).getAllClusters()
            }
            
            // ATT-316: Set active interaction without hiding the background progress
            _activeInteraction.value = ClusterInteraction(date, start, end, apex, distance, bSportType, polyline, clusters)
            
            val decision = deferred.await()
            _activeInteraction.value = null
            clusterDecision = null
            return decision
        }
    }

    fun clearState() {
        _uiState.value = UiState.Idle
    }
}
