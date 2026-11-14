/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.DriveFileEntry
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthManager
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectWorkoutSyncWorker
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Incremental cloud auto-import engine for multi-device workout synchronization (REQ-MIG-052).
 *
 * Automatically monitors configured cloud folders (Dropbox and Google Drive), performs zero-I/O
 * pre-download deduplication using [ExistingWorkoutsSnapshot], parses new activities non-interactively
 * without modal cluster dialogs, and relays them to local Health Connect and Strava services.
 */
object AutoImportManager {

    private const val TAG = "AutoImportManager"
    const val COOLDOWN_MILLIS: Long = 15 * 60 * 1000L // 15-minute cooldown between background scans

    private val isSyncingState = AtomicBoolean(false)

    fun isSyncing(): Boolean = isSyncingState.get()

    data class SyncResult(
        val importedCount: Int,
        val skippedCount: Int,
        val failedCount: Int,
        val errorMessage: String? = null
    ) {
        val totalProcessed: Int get() = importedCount + skippedCount + failedCount
        val isSuccess: Boolean get() = errorMessage == null
    }

    data class CloudFileItem(
        val id: String,
        val name: String,
        val pathLower: String? = null
    )

    // --- VisibleForTesting Inversion Hooks ---
    @VisibleForTesting
    var snapshotProvider: ((Context) -> ExistingWorkoutsSnapshot)? = null

    @VisibleForTesting
    var dropboxLister: (suspend (context: Context, paths: List<String>, extensions: List<String>) -> List<CloudFileItem>)? = null

    @VisibleForTesting
    var dropboxDownloader: (suspend (context: Context, item: CloudFileItem, target: File) -> Boolean)? = null

    @VisibleForTesting
    var googleDriveLister: (suspend (context: Context, format: String) -> List<CloudFileItem>)? = null

    @VisibleForTesting
    var googleDriveDownloader: (suspend (context: Context, item: CloudFileItem, target: File) -> Boolean)? = null

    @VisibleForTesting
    fun resetForTesting() {
        isSyncingState.set(false)
        snapshotProvider = null
        dropboxLister = null
        dropboxDownloader = null
        googleDriveLister = null
        googleDriveDownloader = null
    }

    /**
     * Triggers incremental auto-import on [Dispatchers.IO] if enabled, connected, and cooldown has elapsed.
     */
    fun triggerSyncIfNeeded(context: Context) {
        if (!TrainingApplication.isAutoImportEnabled()) {
            return
        }

        val dropboxEnabled = TrainingApplication.isAutoImportDropboxEnabled() &&
                TrainingApplication.uploadToDropbox() &&
                TrainingApplication.readDropboxCredential() != null

        val googleDriveEnabled = TrainingApplication.isAutoImportGoogleDriveEnabled() &&
                TrainingApplication.uploadToGoogleDrive()

        if (!dropboxEnabled && !googleDriveEnabled) {
            return
        }

        val now = System.currentTimeMillis()
        val lastSync = TrainingApplication.getAutoImportLastSyncTime()
        if (now - lastSync < COOLDOWN_MILLIS) {
            return
        }

        if (!isSyncingState.compareAndSet(false, true)) {
            return
        }

        val appContext = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncIncremental(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "Background auto-import failed: ${e.message}")
            } finally {
                isSyncingState.set(false)
            }
        }
    }

    /**
     * Executes non-interactive incremental cloud synchronization on [Dispatchers.IO].
     */
    suspend fun syncIncremental(
        context: Context,
        onStatus: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        if (!TrainingApplication.isAutoImportEnabled()) {
            return@withContext SyncResult(0, 0, 0, "Auto-import is disabled")
        }

        val dropboxEnabled = TrainingApplication.isAutoImportDropboxEnabled() &&
                TrainingApplication.uploadToDropbox() &&
                TrainingApplication.readDropboxCredential() != null

        val googleDriveEnabled = TrainingApplication.isAutoImportGoogleDriveEnabled() &&
                TrainingApplication.uploadToGoogleDrive()

        if (!dropboxEnabled && !googleDriveEnabled) {
            return@withContext SyncResult(0, 0, 0, "No cloud storage sources connected")
        }

        val appContext = context.applicationContext ?: context
        val existingSnapshot = snapshotProvider?.invoke(appContext) ?: ExistingWorkoutsSnapshot.create(appContext)
        val format = TrainingApplication.getAutoImportFormat()
        val syncHealthConnect = TrainingApplication.isSyncImportedWorkoutsToHealthConnect()
        val uploadToStrava = TrainingApplication.uploadImportedWorkoutsToStrava()

        var importedCount = 0
        var skippedCount = 0
        var failedCount = 0

        val tempDir = File(appContext.cacheDir, "auto_import_temp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        try {
            // 1. Process Dropbox
            if (dropboxEnabled) {
                onStatus?.invoke("Scanning Dropbox...")
                val dropboxEntries = queryDropboxEntries(appContext, format)
                for (entry in dropboxEntries) {
                    val baseFileName = entry.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~")
                    if (existingSnapshot.containsFileBaseName(baseFileName)) {
                        skippedCount++
                        continue
                    }

                    val targetFile = File(tempDir, entry.name)
                    val downloaded = downloadDropboxFile(appContext, entry, targetFile)
                    if (!downloaded || !targetFile.exists()) {
                        failedCount++
                        continue
                    }

                    try {
                        val importResult = importLocalFileNonInteractively(appContext, targetFile, format, uploadToStrava)
                        when (importResult.status) {
                            LegacyImportEngine.ImportStatus.SUCCESS -> {
                                existingSnapshot.register(baseFileName)
                                importedCount++
                                importResult.workoutId?.let { workoutId ->
                                    if (syncHealthConnect) {
                                        HealthConnectWorkoutSyncWorker.enqueue(appContext, workoutId)
                                    }
                                }
                            }
                            LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED -> {
                                existingSnapshot.register(baseFileName)
                                skippedCount++
                            }
                            LegacyImportEngine.ImportStatus.FAILED -> failedCount++
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.e(TAG, "Error importing file ${entry.name} from Dropbox", e)
                        failedCount++
                    } finally {
                        targetFile.delete()
                    }
                }
            }

            // 2. Process Google Drive
            if (googleDriveEnabled) {
                onStatus?.invoke("Scanning Google Drive...")
                val driveEntries = queryGoogleDriveEntries(appContext, format)
                for (entry in driveEntries) {
                    val baseFileName = entry.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~")
                    if (existingSnapshot.containsFileBaseName(baseFileName)) {
                        skippedCount++
                        continue
                    }

                    val targetFile = File(tempDir, entry.name)
                    val downloaded = downloadGoogleDriveFile(appContext, entry, targetFile)
                    if (!downloaded || !targetFile.exists()) {
                        failedCount++
                        continue
                    }

                    try {
                        val importResult = importLocalFileNonInteractively(appContext, targetFile, format, uploadToStrava)
                        when (importResult.status) {
                            LegacyImportEngine.ImportStatus.SUCCESS -> {
                                existingSnapshot.register(baseFileName)
                                importedCount++
                                importResult.workoutId?.let { workoutId ->
                                    if (syncHealthConnect) {
                                        HealthConnectWorkoutSyncWorker.enqueue(appContext, workoutId)
                                    }
                                }
                            }
                            LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED -> {
                                existingSnapshot.register(baseFileName)
                                skippedCount++
                            }
                            LegacyImportEngine.ImportStatus.FAILED -> failedCount++
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        Log.e(TAG, "Error importing file ${entry.name} from Google Drive", e)
                        failedCount++
                    } finally {
                        targetFile.delete()
                    }
                }
            }

            // 3. Post-import reconciliation
            if (importedCount > 0) {
                try {
                    WorkoutRepository.getInstance(appContext).loadAllWorkouts()
                    PeriodsRepository.getInstance(appContext).syncPeriodsIfDiscrepancy()
                    WorkoutClusterRepository.getInstance(appContext).refreshClusters()
                } catch (e: Exception) {
                    Log.w(TAG, "Post-import repository reconciliation failed: ${e.message}")
                }
            }

            TrainingApplication.setAutoImportLastSyncTime(System.currentTimeMillis())
            val summary = "$importedCount imported, $skippedCount skipped"
            TrainingApplication.setAutoImportLastSyncResult(summary)

            return@withContext SyncResult(importedCount, skippedCount, failedCount)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Incremental auto-import failed", e)
            val errorMsg = e.message ?: "Unknown error"
            TrainingApplication.setAutoImportLastSyncResult("Failed: $errorMsg")
            return@withContext SyncResult(importedCount, skippedCount, failedCount, errorMsg)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private suspend fun queryDropboxEntries(context: Context, format: String): List<CloudFileItem> {
        val targetExtensions = when (format.lowercase()) {
            "tcx" -> listOf(".tcx")
            "gpx" -> listOf(".gpx")
            "fit" -> listOf(".fit")
            else -> listOf(".tcx", ".gpx", ".fit")
        }

        val possiblePaths = when (format.lowercase()) {
            "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
            "gpx" -> listOf("/GPX", "/apps/Workouts/GPX")
            "fit" -> listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT", "/Workouts")
            else -> listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT", "/Workouts", "/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX")
        }

        dropboxLister?.let { return it.invoke(context, possiblePaths, targetExtensions) }

        val credential = TrainingApplication.readDropboxCredential() ?: return emptyList()
        val dbxClient = DbxClientV2(DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY), credential)
        val allEntries = mutableListOf<CloudFileItem>()

        for (path in possiblePaths) {
            try {
                var result = dbxClient.files().listFolderBuilder(path).withRecursive(true).start()
                while (true) {
                    result.entries
                        .filter { entry -> targetExtensions.any { entry.name.lowercase().endsWith(it) } }
                        .forEach { entry ->
                            allEntries.add(CloudFileItem(id = entry.pathLower ?: entry.name, name = entry.name, pathLower = entry.pathLower))
                        }
                    if (!result.hasMore) break
                    result = dbxClient.files().listFolderContinue(result.cursor)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Dropbox scan failed for $path: ${e.message}")
            }
        }

        return allEntries.distinctBy { it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase() }
    }

    private suspend fun downloadDropboxFile(context: Context, item: CloudFileItem, targetFile: File): Boolean {
        dropboxDownloader?.let { return it.invoke(context, item, targetFile) }

        val credential = TrainingApplication.readDropboxCredential() ?: return false
        val dbxClient = DbxClientV2(DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY), credential)
        val path = item.pathLower ?: item.name

        return try {
            FileOutputStream(targetFile).use { fos ->
                dbxClient.files().download(path).download(fos)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download Dropbox file $path", e)
            false
        }
    }

    private suspend fun queryGoogleDriveEntries(context: Context, format: String): List<CloudFileItem> {
        googleDriveLister?.let { return it.invoke(context, format) }

        var token = TrainingApplication.getGoogleDriveAuthToken()
        if (token.isNullOrBlank()) {
            token = GoogleDriveAuthManager.refreshTokenSync(context.applicationContext ?: context).getOrNull()
        }
        if (token.isNullOrBlank()) return emptyList()

        val driveClient = GoogleDriveClient(
            tokenProvider = { TrainingApplication.getGoogleDriveAuthToken() },
            tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context.applicationContext ?: context).getOrNull() },
            accountProvider = { TrainingApplication.getGoogleDriveAccountEmail() }
        )

        val targetExtensions = when (format.lowercase()) {
            "fit" -> listOf(".fit")
            "tcx" -> listOf(".tcx")
            "gpx" -> listOf(".gpx")
            else -> listOf(".fit", ".tcx", ".gpx")
        }

        val candidateFolderHierarchies = when (format.lowercase()) {
            "fit" -> listOf(listOf("FIT"), listOf("Workouts"), listOf("aTrainingTracker", "FIT"), listOf("aTrainingTracker", "Workouts"))
            "tcx" -> listOf(listOf("TCX"), listOf("Workouts"), listOf("aTrainingTracker", "TCX"), listOf("aTrainingTracker", "Workouts"))
            "gpx" -> listOf(listOf("GPX"), listOf("Workouts"), listOf("aTrainingTracker", "GPX"), listOf("aTrainingTracker", "Workouts"))
            else -> listOf(listOf("Workouts"), listOf("FIT"), listOf("TCX"), listOf("GPX"), listOf("aTrainingTracker", "Workouts"))
        }

        val allEntries = mutableListOf<CloudFileItem>()
        for (hierarchy in candidateFolderHierarchies) {
            val folderId = driveClient.resolveFolderHierarchy(hierarchy) ?: continue
            val entries = driveClient.listFilesRecursively(folderId)
            entries.filter { file -> targetExtensions.any { file.name.lowercase().endsWith(it) } }
                .forEach { file -> allEntries.add(CloudFileItem(id = file.id, name = file.name)) }
        }

        return allEntries.distinctBy { it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase() }
    }

    private suspend fun downloadGoogleDriveFile(context: Context, item: CloudFileItem, targetFile: File): Boolean {
        googleDriveDownloader?.let { return it.invoke(context, item, targetFile) }

        var token = TrainingApplication.getGoogleDriveAuthToken()
        if (token.isNullOrBlank()) {
            token = GoogleDriveAuthManager.refreshTokenSync(context.applicationContext ?: context).getOrNull()
        }
        if (token.isNullOrBlank()) return false

        val driveClient = GoogleDriveClient(
            tokenProvider = { TrainingApplication.getGoogleDriveAuthToken() },
            tokenRefresher = { GoogleDriveAuthManager.refreshTokenSync(context.applicationContext ?: context).getOrNull() },
            accountProvider = { TrainingApplication.getGoogleDriveAccountEmail() }
        )

        return try {
            driveClient.downloadFileById(item.id, targetFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download Google Drive file ${item.name}", e)
            false
        }
    }

    private suspend fun importLocalFileNonInteractively(
        context: Context,
        file: File,
        format: String,
        uploadToStrava: Boolean
    ): LegacyImportEngine.ImportResult {
        val ext = file.name.substringAfterLast('.').lowercase()
        return when (ext) {
            "fit" -> LegacyImportEngine.importFromFitResult(context, file, null, uploadToStrava)
            "tcx" -> LegacyImportEngine.importFromTcxResult(context, file, null, uploadToStrava)
            "gpx" -> LegacyImportEngine.importFromGpxResult(context, file, null, uploadToStrava)
            else -> when (format.lowercase()) {
                "fit" -> LegacyImportEngine.importFromFitResult(context, file, null, uploadToStrava)
                "tcx" -> LegacyImportEngine.importFromTcxResult(context, file, null, uploadToStrava)
                "gpx" -> LegacyImportEngine.importFromGpxResult(context, file, null, uploadToStrava)
                else -> LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.FAILED)
            }
        }
    }
}
