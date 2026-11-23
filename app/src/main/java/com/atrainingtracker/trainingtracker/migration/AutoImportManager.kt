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
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.healthconnect.HealthConnectWorkoutSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Incremental cloud auto-import engine for multi-device workout synchronization
 * (`REQ-MIG-052`, `REQ-MIG-061.3`).
 *
 * Routes all cloud discovery, rate-limited download retries, remote filename traversal
 * sanitization, and `.zip` archive extraction through [CloudWorkoutSourceScanner] and
 * [SecureZipExtractor], performs zero-I/O pre-download deduplication via
 * [ExistingWorkoutsSnapshot], parses new activities non-interactively, and relays them
 * to local Health Connect and Strava services.
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

        val counters = SyncCounters()
        val tempDir = File(appContext.cacheDir, "auto_import_temp").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        try {
            withBulkImportBatch { batch ->
                // 1. Process Dropbox
                if (dropboxEnabled) {
                    onStatus?.invoke("Scanning Dropbox...")
                    val dropboxEntries = queryDropboxEntries(appContext, format)
                    processCloudEntries(
                        context = appContext,
                        sourceLabel = "Dropbox",
                        entries = dropboxEntries,
                        existingSnapshot = existingSnapshot,
                        tempDir = tempDir,
                        format = format,
                        syncHealthConnect = syncHealthConnect,
                        uploadToStrava = uploadToStrava,
                        counters = counters,
                        downloader = ::downloadDropboxFile
                    )
                }

                // 2. Process Google Drive
                if (googleDriveEnabled) {
                    onStatus?.invoke("Scanning Google Drive...")
                    val driveEntries = queryGoogleDriveEntries(appContext, format)
                    processCloudEntries(
                        context = appContext,
                        sourceLabel = "Google Drive",
                        entries = driveEntries,
                        existingSnapshot = existingSnapshot,
                        tempDir = tempDir,
                        format = format,
                        syncHealthConnect = syncHealthConnect,
                        uploadToStrava = uploadToStrava,
                        counters = counters,
                        downloader = ::downloadGoogleDriveFile
                    )
                }

                // 3. Post-import reconciliation (single pass for the whole sync batch, REQ-STB-021.3)
                if (counters.imported > 0) {
                    reconcileBulkImportBatch(
                        context = appContext,
                        refreshClimbs = batch?.hasPersistedClimbs == true,
                        logTag = TAG
                    )
                }
            }

            TrainingApplication.setAutoImportLastSyncTime(System.currentTimeMillis())
            val summary = "${counters.imported} imported, ${counters.skipped} skipped"
            TrainingApplication.setAutoImportLastSyncResult(summary)

            return@withContext SyncResult(counters.imported, counters.skipped, counters.failed)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Incremental auto-import failed", e)
            val errorMsg = e.message ?: "Unknown error"
            TrainingApplication.setAutoImportLastSyncResult("Failed: $errorMsg")
            return@withContext SyncResult(counters.imported, counters.skipped, counters.failed, errorMsg)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private class SyncCounters(
        var imported: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0,
        var jobCounter: Int = 0
    )

    private suspend fun processCloudEntries(
        context: Context,
        sourceLabel: String,
        entries: List<CloudFileItem>,
        existingSnapshot: ExistingWorkoutsSnapshot,
        tempDir: File,
        format: String,
        syncHealthConnect: Boolean,
        uploadToStrava: Boolean,
        counters: SyncCounters,
        downloader: suspend (Context, CloudFileItem, File) -> Boolean
    ) {
        for (entry in entries) {
            val isZipArchive = entry.name.endsWith(".zip", ignoreCase = true)
            val entryBaseName = CloudWorkoutSourceScanner.normalizeBaseFileName(entry.name)
            if (!isZipArchive && existingSnapshot.containsFileBaseName(entryBaseName)) {
                counters.skipped++
                continue
            }

            val workerDir = File(tempDir, "job_${++counters.jobCounter}").apply { mkdirs() }
            // Hardened filename traversal validation BEFORE download (REQ-MIG-061.3, ATT-6174)
            val targetFile = CloudWorkoutSourceScanner.sanitizeRemoteTargetFile(entry.name, workerDir)
            if (targetFile == null) {
                counters.failed++
                continue
            }

            try {
                val downloaded = downloader(context, entry, targetFile)
                if (!downloaded || !targetFile.exists()) {
                    counters.failed++
                    continue
                }

                // Hardened Zip-Slip / zip-bomb extraction for .zip entries via SecureZipExtractor
                val filesToImport = CloudWorkoutSourceScanner.prepareLocalImportFiles(targetFile, workerDir)
                if (filesToImport.isEmpty()) {
                    counters.failed++
                    continue
                }

                for (workoutFile in filesToImport) {
                    val workoutBaseName = CloudWorkoutSourceScanner.normalizeBaseFileName(workoutFile.name)
                    if (isZipArchive && existingSnapshot.containsFileBaseName(workoutBaseName)) {
                        counters.skipped++
                        continue
                    }

                    val importResult = importLocalFileNonInteractively(context, workoutFile, format, uploadToStrava)
                    when (importResult.status) {
                        LegacyImportEngine.ImportStatus.SUCCESS -> {
                            existingSnapshot.register(workoutBaseName)
                            counters.imported++
                            importResult.workoutId?.let { workoutId ->
                                if (syncHealthConnect) {
                                    HealthConnectWorkoutSyncWorker.enqueue(context, workoutId)
                                }
                            }
                        }
                        LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED -> {
                            existingSnapshot.register(workoutBaseName)
                            counters.skipped++
                        }
                        LegacyImportEngine.ImportStatus.FAILED -> counters.failed++
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (se: SecurityException) {
                Log.e(TAG, "Security violation importing ${entry.name} from $sourceLabel: ${se.message}", se)
                counters.failed++
            } catch (e: Exception) {
                Log.e(TAG, "Error importing file ${entry.name} from $sourceLabel", e)
                counters.failed++
            } finally {
                workerDir.deleteRecursively()
            }
        }
    }

    private suspend fun queryDropboxEntries(context: Context, format: String): List<CloudFileItem> {
        val targetExtensions = CloudWorkoutSourceScanner.targetExtensionsFor(format)
        val possiblePaths = CloudWorkoutSourceScanner.dropboxPathsFor(format, includeWorkoutsRoot = true)

        dropboxLister?.let { return it.invoke(context, possiblePaths, targetExtensions) }

        val dbxClient = CloudWorkoutSourceScanner.createDropboxClient() ?: return emptyList()
        return CloudWorkoutSourceScanner.listDropboxEntries(
            dbxClient = dbxClient,
            format = format,
            includeWorkoutsRoot = true,
            includeZip = true
        ).map { CloudFileItem(id = it.id, name = it.name, pathLower = it.pathLower) }
    }

    private suspend fun downloadDropboxFile(context: Context, item: CloudFileItem, targetFile: File): Boolean {
        dropboxDownloader?.let { return it.invoke(context, item, targetFile) }

        val dbxClient = CloudWorkoutSourceScanner.createDropboxClient() ?: return false
        val path = item.pathLower ?: item.name
        return CloudWorkoutSourceScanner.downloadDropboxWithRetry(dbxClient, path, targetFile)
    }

    private suspend fun queryGoogleDriveEntries(context: Context, format: String): List<CloudFileItem> {
        googleDriveLister?.let { return it.invoke(context, format) }

        val driveClient = CloudWorkoutSourceScanner.createGoogleDriveClient(context) ?: return emptyList()
        return CloudWorkoutSourceScanner.listGoogleDriveEntries(
            driveClient = driveClient,
            format = format,
            includeAppRoot = false,
            includeZip = true
        ).map { CloudFileItem(id = it.id, name = it.name) }
    }

    private suspend fun downloadGoogleDriveFile(context: Context, item: CloudFileItem, targetFile: File): Boolean {
        googleDriveDownloader?.let { return it.invoke(context, item, targetFile) }

        val driveClient = CloudWorkoutSourceScanner.createGoogleDriveClient(context) ?: return false
        return CloudWorkoutSourceScanner.downloadGoogleDriveWithRetry(driveClient, item.id, targetFile)
    }

    private suspend fun importLocalFileNonInteractively(
        context: Context,
        file: File,
        format: String,
        uploadToStrava: Boolean
    ): LegacyImportEngine.ImportResult {
        val ext = file.name.substringAfterLast('.').lowercase(Locale.ROOT)
        return when (ext) {
            "fit" -> LegacyImportEngine.importFromFitResult(context, file, null, uploadToStrava)
            "tcx" -> LegacyImportEngine.importFromTcxResult(context, file, null, uploadToStrava)
            "gpx" -> LegacyImportEngine.importFromGpxResult(context, file, null, uploadToStrava)
            else -> when (format.lowercase(Locale.ROOT)) {
                "fit" -> LegacyImportEngine.importFromFitResult(context, file, null, uploadToStrava)
                "tcx" -> LegacyImportEngine.importFromTcxResult(context, file, null, uploadToStrava)
                "gpx" -> LegacyImportEngine.importFromGpxResult(context, file, null, uploadToStrava)
                else -> LegacyImportEngine.ImportResult(LegacyImportEngine.ImportStatus.FAILED)
            }
        }
    }
}
