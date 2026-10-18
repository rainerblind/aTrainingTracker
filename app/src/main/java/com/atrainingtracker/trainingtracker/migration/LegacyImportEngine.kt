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
import android.content.ContentValues
import android.content.Context
import android.location.Location
import android.util.Log
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.migration.parsers.FitWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.GpxWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.ParsedWorkoutData
import com.atrainingtracker.trainingtracker.migration.parsers.TcxWorkoutParser
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import com.atrainingtracker.trainingtracker.cloud.googledrive.DriveFileEntry
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Data model representing a parsed lap segment from a TCX file.
 */
data class ParsedLap(
    val lapNr: Long,
    var startTime: String? = null,
    var totalTimeSeconds: Double = 0.0,
    var distanceMeters: Double = 0.0,
    var maxSpeed: Double? = null,
    var calories: Int? = null,
    var avgHeartRate: Int? = null,
    var maxHeartRate: Int? = null,
    var name: String? = null,
    var description: String? = null
)

/**
 * Handles recreation of workouts from legacy export files (TCX).
 */
object LegacyImportEngine {
    private const val TAG = "LegacyImportEngine"
    private val tcxTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    private val recalculationMutex = Mutex()
    internal val importMutex = Mutex()

    enum class ImportStatus {
        SUCCESS,
        DUPLICATE_SKIPPED,
        FAILED
    }

    /**
     * Enriched result of an activity file import operation (REQ-MIG-032).
     *
     * @property status The execution status of the import attempt.
     * @property workoutId The primary key ID of the created workout in [WorkoutSummaries.TABLE] if successful.
     */
    data class ImportResult(
        val status: ImportStatus,
        val workoutId: Long? = null
    )

    interface ProgressListener {
        fun onProgress(current: Int, total: Int, name: String)
        fun onStatus(message: String)
        suspend fun onNewClusterCandidate(
            date: String, 
            start: LatLng, 
            end: LatLng, 
            apex: LatLng, 
            distance: Double,
            bSportType: BSportType,
            polyline: String,
            workoutName: String? = null,
            candidateSportTypes: Set<BSportType> = emptySet(),
            minAltPos: LatLng? = null,
            maxAltPos: LatLng? = null
        ): Pair<Long?, String?>
    }

    data class RecoveryResult(
        val importedCount: Int,
        val skippedCount: Int,
        val failedCount: Int,
        val totalScanned: Int
    )

    /**
     * Scans Dropbox recursively across all target paths and recovers all legacy workouts.
     */
    suspend fun bulkRecoverFromDropbox(
        context: Context,
        format: String,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): RecoveryResult {
        val credential = TrainingApplication.readDropboxCredential() ?: return RecoveryResult(0, 0, 0, 0)
        val dbxClient = DbxClientV2(DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY), credential)
        
        val possiblePaths = when (format.lowercase()) {
            "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
            "gpx" -> listOf("/GPX", "/apps/Workouts/GPX")
            "fit" -> listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
            else -> listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX", "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
        }

        val targetExtensions = when (format.lowercase()) {
            "tcx" -> listOf(".tcx")
            "gpx" -> listOf(".gpx")
            "fit" -> listOf(".fit")
            else -> listOf(".tcx", ".gpx", ".fit")
        }

        val allEntries = mutableListOf<com.dropbox.core.v2.files.Metadata>()

        for (path in possiblePaths) {
            try {
                listener?.onStatus("Scanning $path...")
                var result = dbxClient.files().listFolderBuilder(path).withRecursive(true).start()
                
                while (true) {
                    allEntries.addAll(result.entries.filter { entry ->
                        targetExtensions.any { entry.name.lowercase().endsWith(it) }
                    })
                    if (!result.hasMore) break
                    try {
                        result = dbxClient.files().listFolderContinue(result.cursor)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error continuing folder listing for $path, keeping ${allEntries.size} entries", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Folder scan failed for $path: ${e.message}")
            }
        }

        // Deduplicate entries by base name across scanned paths (REQ-MIG-031)
        val entries = allEntries.distinctBy {
            it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
        }
        if (entries.isEmpty()) return RecoveryResult(0, 0, 0, 0)
        
        val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)
        val tempDir = File(context.cacheDir, "legacy_recovery")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        val importedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val skippedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val processedCount = java.util.concurrent.atomic.AtomicInteger(0)

        try {
            // ATT-493 Fix: Concurrent import pipeline using coroutineScope and a worker channel.
            // Spawns 3 concurrent background workers (bounded by interactionSemaphore(3)) so that
            // file downloading and importing continues concurrently while waiting for user candidate resolution.
            kotlinx.coroutines.coroutineScope {
                val channel = kotlinx.coroutines.channels.Channel<Pair<Int, com.dropbox.core.v2.files.Metadata>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                entries.forEachIndexed { index, entry -> channel.trySend(Pair(index, entry)) }
                channel.close()

                (1..3).map {
                    launch(Dispatchers.IO) {
                        for ((_, entry) in channel) {
                            val current = processedCount.incrementAndGet()
                            listener?.onProgress(current, entries.size, entry.name)

                            val baseFileName = entry.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~")
                            if (isWorkoutExisting(summaryDb, baseFileName)) {
                                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                                skippedCount.incrementAndGet()
                                continue
                            }

                            listener?.onStatus(context.getString(R.string.legacy_import__downloading_dropbox, entry.name))
                            val workerDir = File(tempDir, "job_$current").apply { mkdirs() }
                            val tempFile = File(workerDir, entry.name)
                            try {
                                val downloaded = downloadFileWithRetry(dbxClient, entry.pathLower ?: entry.name, tempFile)
                                if (!downloaded) {
                                    failedCount.incrementAndGet()
                                    continue
                                }

                                val ext = entry.name.substringAfterLast('.').lowercase()
                                val status = when (ext) {
                                    "tcx" -> importFromTcxInternal(context, tempFile, listener, uploadToStrava)
                                    "gpx" -> importFromGpxInternal(context, tempFile, listener, uploadToStrava)
                                    "fit" -> importFromFitInternal(context, tempFile, listener, uploadToStrava)
                                    else -> when (format.lowercase()) {
                                        "tcx" -> importFromTcxInternal(context, tempFile, listener, uploadToStrava)
                                        "gpx" -> importFromGpxInternal(context, tempFile, listener, uploadToStrava)
                                        "fit" -> importFromFitInternal(context, tempFile, listener, uploadToStrava)
                                        else -> ImportStatus.FAILED
                                    }
                                }
                                when (status) {
                                    ImportStatus.SUCCESS -> importedCount.incrementAndGet()
                                    ImportStatus.DUPLICATE_SKIPPED -> skippedCount.incrementAndGet()
                                    ImportStatus.FAILED -> failedCount.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to download/import ${entry.name}", e)
                                failedCount.incrementAndGet()
                            } finally {
                                tempFile.delete()
                                workerDir.delete()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bulk recovery failed", e)
        }

        return RecoveryResult(
            importedCount = importedCount.get(),
            skippedCount = skippedCount.get(),
            failedCount = failedCount.get(),
            totalScanned = entries.size
        )
    }

    private fun downloadFileWithRetry(
        dbxClient: DbxClientV2,
        pathLower: String,
        targetFile: File,
        maxRetries: Int = 3
    ): Boolean {
        var attempt = 0
        while (attempt < maxRetries) {
            attempt++
            try {
                FileOutputStream(targetFile).use { fos ->
                    dbxClient.files().download(pathLower).download(fos)
                }
                return true
            } catch (e: com.dropbox.core.RateLimitException) {
                val backoffMs = e.backoffMillis + 500L
                Log.w(TAG, "Dropbox rate limit hit for $pathLower. Backing off for ${backoffMs}ms (attempt $attempt/$maxRetries)...")
                try { Thread.sleep(backoffMs) } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Transient download error for $pathLower (attempt $attempt/$maxRetries): ${e.message}")
                if (attempt >= maxRetries) return false
                try { Thread.sleep(1000L * attempt) } catch (_: Exception) {}
            }
        }
        return false
    }

    /**
     * Scans Google Drive recursively across candidate paths and recovers historical workouts (.fit, .tcx, .gpx).
     *
     * Adheres to REQ-MIG-034:
     * - Discovers .fit, .tcx, and .gpx archives across standard folders
     * - Skips existing workouts prior to download based on base file name
     * - Deduplicates identical base filenames across folders
     * - Downloads and parses using internal format importers
     * - Returns a [RecoveryResult] tallying imported, skipped, and failed activities
     */
    suspend fun bulkRecoverFromGoogleDrive(
        context: Context,
        format: String = "all",
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): RecoveryResult {
        val token = TrainingApplication.getGoogleDriveAuthToken() ?: return RecoveryResult(0, 0, 0, 0)
        val driveClient = GoogleDriveClient(tokenProvider = { TrainingApplication.getGoogleDriveAuthToken() })

        val targetExtensions = when (format.lowercase()) {
            "fit" -> listOf(".fit")
            "tcx" -> listOf(".tcx")
            "gpx" -> listOf(".gpx")
            else -> listOf(".fit", ".tcx", ".gpx")
        }

        val candidateFolderHierarchies = when (format.lowercase()) {
            "fit" -> listOf(
                listOf("FIT"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "tcx" -> listOf(
                listOf("TCX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "gpx" -> listOf(
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            else -> listOf(
                listOf("FIT"),
                listOf("TCX"),
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
        }

        val allEntries = mutableListOf<DriveFileEntry>()
        val searchedFolderIds = mutableSetOf<String>()

        for (hierarchy in candidateFolderHierarchies) {
            try {
                val folderNameDisplay = hierarchy.joinToString("/")
                listener?.onStatus("Scanning Google Drive: $folderNameDisplay...")
                val folderId = driveClient.resolveFolderHierarchy(hierarchy)
                if (folderId != null && searchedFolderIds.add(folderId)) {
                    val entries = driveClient.listFilesRecursively(folderId, targetExtensions)
                    allEntries.addAll(entries)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Google Drive folder scan failed for ${hierarchy.joinToString("/")}: ${e.message}")
            }
        }

        // Deduplicate entries by base name across scanned paths (REQ-MIG-031 / REQ-MIG-034)
        val entries = allEntries.distinctBy {
            it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
        }
        if (entries.isEmpty()) return RecoveryResult(0, 0, 0, 0)

        val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)
        val tempDir = File(context.cacheDir, "gdrive_recovery")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        val importedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val skippedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val processedCount = java.util.concurrent.atomic.AtomicInteger(0)

        try {
            kotlinx.coroutines.coroutineScope {
                val channel = kotlinx.coroutines.channels.Channel<Pair<Int, DriveFileEntry>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                entries.forEachIndexed { index, entry -> channel.trySend(Pair(index, entry)) }
                channel.close()

                (1..3).map {
                    launch(Dispatchers.IO) {
                        for ((_, entry) in channel) {
                            val current = processedCount.incrementAndGet()
                            listener?.onProgress(current, entries.size, entry.name)

                            val baseFileName = entry.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~")
                            if (isWorkoutExisting(summaryDb, baseFileName)) {
                                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                                skippedCount.incrementAndGet()
                                continue
                            }

                            listener?.onStatus(context.getString(R.string.legacy_import__downloading_google_drive, entry.name))
                            val workerDir = File(tempDir, "job_$current").apply { mkdirs() }
                            val tempFile = try {
                                SecureZipExtractor.sanitizeDriveFileName(entry.name, workerDir)
                            } catch (e: SecurityException) {
                                Log.e(TAG, "Security violation: Invalid remote file name: ${entry.name}", e)
                                failedCount.incrementAndGet()
                                continue
                            }
                            try {
                                val downloaded = downloadGoogleDriveFileWithRetry(driveClient, entry.id, tempFile)
                                if (!downloaded) {
                                    failedCount.incrementAndGet()
                                    continue
                                }

                                val ext = entry.name.substringAfterLast('.').lowercase()
                                val status = when (ext) {
                                    "fit" -> importFromFitInternal(context, tempFile, listener, uploadToStrava)
                                    "tcx" -> importFromTcxInternal(context, tempFile, listener, uploadToStrava)
                                    "gpx" -> importFromGpxInternal(context, tempFile, listener, uploadToStrava)
                                    else -> when (format.lowercase()) {
                                        "fit" -> importFromFitInternal(context, tempFile, listener, uploadToStrava)
                                        "tcx" -> importFromTcxInternal(context, tempFile, listener, uploadToStrava)
                                        "gpx" -> importFromGpxInternal(context, tempFile, listener, uploadToStrava)
                                        else -> ImportStatus.FAILED
                                    }
                                }
                                when (status) {
                                    ImportStatus.SUCCESS -> importedCount.incrementAndGet()
                                    ImportStatus.DUPLICATE_SKIPPED -> skippedCount.incrementAndGet()
                                    ImportStatus.FAILED -> failedCount.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to download/import ${entry.name}", e)
                                failedCount.incrementAndGet()
                            } finally {
                                tempFile.delete()
                                workerDir.delete()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Drive bulk recovery failed", e)
        }

        return RecoveryResult(
            importedCount = importedCount.get(),
            skippedCount = skippedCount.get(),
            failedCount = failedCount.get(),
            totalScanned = entries.size
        )
    }

    private fun downloadGoogleDriveFileWithRetry(
        driveClient: GoogleDriveClient,
        fileId: String,
        targetFile: File,
        maxRetries: Int = 3
    ): Boolean {
        var attempt = 0
        while (attempt < maxRetries) {
            attempt++
            try {
                val success = driveClient.downloadFileById(fileId, targetFile)
                if (success) return true
                if (attempt >= maxRetries) return false
                try { Thread.sleep(1000L * attempt) } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Transient download error for Google Drive file $fileId (attempt $attempt/$maxRetries): ${e.message}")
                if (attempt >= maxRetries) return false
                try { Thread.sleep(1000L * attempt) } catch (_: Exception) {}
            }
        }
        return false
    }

    /**
     * Recreates a workout from a TCX file returning an enriched [ImportResult] (REQ-MIG-032).
     */
    suspend fun importFromTcxResult(
        context: Context,
        tcxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportResult {
        try {
            val baseFileName = tcxFile.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)

            // ATT-314: Early exit if workout already exists to prevent redundant processing
            if (!baseFileName.startsWith("legacy_import", ignoreCase = true) && isWorkoutExisting(summaryDb, baseFileName)) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
            }

            val parseResult = TcxWorkoutParser.parse(tcxFile, context)
            if (parseResult.isFailure) {
                Log.e(TAG, "Failed to parse TCX: ${parseResult.exceptionOrNull()?.message}")
                return ImportResult(ImportStatus.FAILED)
            }
            val parsedWorkout = parseResult.getOrThrow()

            return persistParsedWorkout(
                context = context,
                baseFileName = baseFileName,
                parsedWorkout = parsedWorkout,
                source = WorkoutSource.TCX,
                uploadToStrava = uploadToStrava,
                listener = listener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import TCX: ${tcxFile.name}", e)
        }
        return ImportResult(ImportStatus.FAILED)
    }

    /**
     * Recreates a workout from a TCX file.
     */
    suspend fun importFromTcx(
        context: Context,
        tcxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): Boolean {
        return importFromTcxResult(context, tcxFile, listener, uploadToStrava).status == ImportStatus.SUCCESS
    }

    internal suspend fun importFromTcxInternal(
        context: Context,
        tcxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportStatus {
        return importFromTcxResult(context, tcxFile, listener, uploadToStrava).status
    }

    /**
     * Recreates a workout from a GPX file (GPX 1.0 or 1.1) returning an enriched [ImportResult] (REQ-MIG-032).
     * (REQ-MIG-030)
     */
    suspend fun importFromGpxResult(
        context: Context,
        gpxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportResult {
        try {
            val baseFileName = gpxFile.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)

            // Early exit if workout already exists to prevent redundant processing
            if (!baseFileName.startsWith("legacy_import", ignoreCase = true) && isWorkoutExisting(summaryDb, baseFileName)) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
            }

            val parseResult = GpxWorkoutParser.parse(gpxFile, context)
            if (parseResult.isFailure) {
                Log.e(TAG, "Failed to parse GPX: ${parseResult.exceptionOrNull()?.message}")
                return ImportResult(ImportStatus.FAILED)
            }
            val parsedWorkout = parseResult.getOrThrow()

            return persistParsedWorkout(
                context = context,
                baseFileName = baseFileName,
                parsedWorkout = parsedWorkout,
                source = WorkoutSource.GPX,
                uploadToStrava = uploadToStrava,
                listener = listener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import GPX: ${gpxFile.name}", e)
        }
        return ImportResult(ImportStatus.FAILED)
    }

    /**
     * Recreates a workout from a GPX file (GPX 1.0 or 1.1).
     * (REQ-MIG-030)
     */
    suspend fun importFromGpx(
        context: Context,
        gpxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): Boolean {
        return importFromGpxResult(context, gpxFile, listener, uploadToStrava).status == ImportStatus.SUCCESS
    }

    internal suspend fun importFromGpxInternal(
        context: Context,
        gpxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportStatus {
        return importFromGpxResult(context, gpxFile, listener, uploadToStrava).status
    }

    /**
     * Recreates a workout from an external Garmin FIT binary file returning an enriched [ImportResult] (REQ-MIG-032).
     * (REQ-DAT-019)
     */
    suspend fun importFromFitResult(
        context: Context,
        fitFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportResult {
        try {
            val baseFileName = fitFile.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)

            // Early exit if workout already exists to prevent redundant processing
            if (!baseFileName.startsWith("legacy_import", ignoreCase = true) && isWorkoutExisting(summaryDb, baseFileName)) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
            }

            val parseResult = FitWorkoutParser.parse(fitFile, context)
            if (parseResult.isFailure) {
                Log.e(TAG, "Failed to parse FIT: ${parseResult.exceptionOrNull()?.message}")
                return ImportResult(ImportStatus.FAILED)
            }
            val parsedWorkout = parseResult.getOrThrow()

            return persistParsedWorkout(
                context = context,
                baseFileName = baseFileName,
                parsedWorkout = parsedWorkout,
                source = WorkoutSource.FIT,
                uploadToStrava = uploadToStrava,
                listener = listener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import FIT: ${fitFile.name}", e)
        }
        return ImportResult(ImportStatus.FAILED)
    }

    /**
     * Persists in-memory parsed workout data into SQLite database tables and triggers
     * stats recalculation and cloud synchronization (REQ-MIG-038).
     */
    private suspend fun persistParsedWorkout(
        context: Context,
        baseFileName: String,
        parsedWorkout: ParsedWorkoutData,
        source: WorkoutSource,
        uploadToStrava: Boolean,
        listener: ProgressListener?
    ): ImportResult {
        var effectiveBaseFileName = baseFileName
        var firstTime = parsedWorkout.firstTime

        if (firstTime == null && parsedWorkout.parsedLaps.isNotEmpty() && parsedWorkout.parsedLaps.first().startTime != null) {
            firstTime = parsedWorkout.parsedLaps.first().startTime
        }

        if (effectiveBaseFileName.startsWith("legacy_import", ignoreCase = true) && firstTime != null) {
            effectiveBaseFileName = firstTime.replace(" ", "_").replace(":", "")
        }

        val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)
        val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
        var workoutId = -1L

        // Mutex-guarded multi-dimensional deduplication and atomic insertion (REQ-MIG-031)
        val isDuplicate = importMutex.withLock {
            if (isWorkoutExisting(summaryDb, effectiveBaseFileName, firstTime, parsedWorkout.bSportType)) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $effectiveBaseFileName: Workout already exists.")
                return@withLock true
            }

            // Post-parsing: Bulk insertion and dynamic table creation (ATT-357 / ATT-602)
            if (parsedWorkout.bufferedSamples.isNotEmpty()) {
                samplesDbManager.createNewTable(effectiveBaseFileName, SensorType.values().toList())
                val targetDb = samplesDbManager.database
                val tableName = WorkoutSamplesDatabaseManager.getTableName(effectiveBaseFileName)
                targetDb.beginTransaction()
                try {
                    parsedWorkout.bufferedSamples.forEach { sampleValues ->
                        targetDb.insert(tableName, null, sampleValues)
                    }
                    targetDb.setTransactionSuccessful()
                } finally {
                    targetDb.endTransaction()
                }
            }

            if (firstTime != null) {
                workoutId = getWorkoutId(summaryDb, effectiveBaseFileName)
                if (workoutId == -1L) {
                    try {
                        StravaUploadDbHelper(context).deleteWorkout(effectiveBaseFileName)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Could not clean StravaUploadDb for $effectiveBaseFileName: ${t.message}")
                    }

                    val summaryValues = ContentValues().apply {
                        put(WorkoutSummaries.FILE_BASE_NAME, effectiveBaseFileName)
                        put(
                            WorkoutSummaries.WORKOUT_NAME,
                            if (!parsedWorkout.workoutName.isNullOrBlank()) parsedWorkout.workoutName.trim() else effectiveBaseFileName
                        )
                        put(WorkoutSummaries.TIME_START, firstTime)
                        put(WorkoutSummaries.SPORT_ID, parsedWorkout.resolvedSportId)
                        put(WorkoutSummaries.B_SPORT, parsedWorkout.bSportType.name)
                        put(WorkoutSummaries.EQUIPMENT_ID, -1L)
                        put(WorkoutSummaries.FINISHED, 1)
                        put(WorkoutSummaries.SOURCE, source.name)
                        if (uploadToStrava && TrainingApplication.uploadToCommunity(FileFormat.STRAVA)) {
                            put(WorkoutSummaries.UPLOAD_TO_STRAVA, 1)
                        } else {
                            put(WorkoutSummaries.UPLOAD_TO_STRAVA, 0)
                        }
                    }
                    workoutId = summaryDb.database.insert(WorkoutSummaries.TABLE, null, summaryValues)
                }
            }
            false
        }

        if (isDuplicate) {
            return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
        }

        if (firstTime != null) {
            recalculateStats(
                context = context,
                workoutId = workoutId,
                baseFileName = effectiveBaseFileName,
                points = parsedWorkout.points,
                altitudes = parsedWorkout.altitudes,
                distances = parsedWorkout.distances,
                bSportType = parsedWorkout.bSportType,
                foundSensors = parsedWorkout.foundSensors,
                parsedLaps = parsedWorkout.parsedLaps,
                workoutNotes = parsedWorkout.workoutNotes,
                workoutName = parsedWorkout.workoutName ?: effectiveBaseFileName,
                firstTime = firstTime,
                lastTime = parsedWorkout.lastTime,
                minAltPos = parsedWorkout.minAltPos,
                maxAltPos = parsedWorkout.maxAltPos,
                listener = listener,
                sessionNormalizedPowerWatts = parsedWorkout.sessionNormalizedPowerWatts,
                sessionIntensityFactor = parsedWorkout.sessionIntensityFactor,
                sessionTss = parsedWorkout.sessionTss,
                sessionWorkKj = parsedWorkout.sessionWorkKj
            )

            schedulePostImportCommunityUpload(context, workoutId, effectiveBaseFileName)

            return ImportResult(ImportStatus.SUCCESS, workoutId)
        }

        return ImportResult(ImportStatus.FAILED)
    }

    /**
     * Recreates a workout from an external Garmin FIT binary file (REQ-DAT-019).
     */
    suspend fun importFromFit(
        context: Context,
        fitFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): Boolean {
        return importFromFitResult(context, fitFile, listener, uploadToStrava).status == ImportStatus.SUCCESS
    }

    internal suspend fun importFromFitInternal(
        context: Context,
        fitFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportStatus {
        return importFromFitResult(context, fitFile, listener, uploadToStrava).status
    }

    private fun getWorkoutId(db: WorkoutSummariesDatabaseManager, fileBaseName: String): Long {
        db.database.query(WorkoutSummaries.TABLE, arrayOf(WorkoutSummaries.C_ID), 
            "${WorkoutSummaries.FILE_BASE_NAME} = ?", arrayOf(fileBaseName), null, null, null).use {
            return if (it.moveToFirst()) it.getLong(0) else -1L
        }
    }

    /**
     * Checks if a workout already exists in WorkoutSummaries by base file name or start timestamp.
     * Evaluates exact FILE_BASE_NAME, exact TIME_START, and a ±30-second epoch window to prevent
     * duplicate imports of renamed activities (REQ-MIG-031).
     */
    internal fun isWorkoutExisting(
        db: WorkoutSummariesDatabaseManager, 
        fileBaseName: String, 
        timeStart: String? = null,
        bSportType: BSportType? = null
    ): Boolean {
        if (timeStart.isNullOrBlank()) {
            db.database.query(
                WorkoutSummaries.TABLE, 
                arrayOf(WorkoutSummaries.C_ID), 
                "${WorkoutSummaries.FILE_BASE_NAME} = ?", 
                arrayOf(fileBaseName), 
                null, null, null
            ).use {
                return it.count > 0
            }
        }

        val hasSport = bSportType != null && bSportType != BSportType.UNKNOWN
        val selection = if (hasSport) {
            "${WorkoutSummaries.FILE_BASE_NAME} = ? OR " +
                    "${WorkoutSummaries.TIME_START} = ? OR " +
                    "(${WorkoutSummaries.B_SPORT} = ? AND ${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)"
        } else {
            "${WorkoutSummaries.FILE_BASE_NAME} = ? OR " +
                    "${WorkoutSummaries.TIME_START} = ? OR " +
                    "(${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)"
        }

        val selectionArgs = if (hasSport) {
            arrayOf(fileBaseName, timeStart, bSportType.name, timeStart)
        } else {
            arrayOf(fileBaseName, timeStart, timeStart)
        }

        db.database.query(
            WorkoutSummaries.TABLE, 
            arrayOf(WorkoutSummaries.C_ID), 
            selection, 
            selectionArgs, 
            null, null, null
        ).use {
            return it.count > 0
        }
    }

    private suspend fun recalculateStats(
        context: Context, 
        workoutId: Long, 
        baseFileName: String, 
        points: List<LatLng>,
        altitudes: List<Double>,
        distances: List<Double>,
        bSportType: BSportType,
        foundSensors: Set<SensorType> = emptySet(),
        parsedLaps: List<ParsedLap> = emptyList(),
        workoutNotes: String? = null,
        workoutName: String? = null,
        firstTime: String? = null,
        lastTime: String? = null,
        minAltPos: LatLng? = null,
        maxAltPos: LatLng? = null,
        listener: ProgressListener? = null,
        sessionNormalizedPowerWatts: Double? = null,
        sessionIntensityFactor: Double? = null,
        sessionTss: Double? = null,
        sessionWorkKj: Double? = null
    ) {
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context)
        
        // 1. Basic Stats
        var totalDistance = distances.lastOrNull() ?: 0.0
        if (totalDistance == 0.0 && points.size > 1) {
            totalDistance = calculateCumulativeDistance(points)
        }
        
        val activeTime = if (parsedLaps.any { it.totalTimeSeconds > 0 }) {
            parsedLaps.sumOf { it.totalTimeSeconds }.roundToInt()
        } else {
            (points.size.coerceAtLeast(altitudes.size)).coerceAtLeast(distances.size)
        }

        val totalTime = if (firstTime != null && lastTime != null) {
            try {
                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val t1 = format.parse(firstTime)?.time ?: 0L
                val t2 = format.parse(lastTime)?.time ?: 0L
                val elapsed = ((t2 - t1) / 1000).toInt()
                elapsed.coerceAtLeast(activeTime)
            } catch (_: Exception) {
                activeTime
            }
        } else {
            activeTime
        }
        
        val values = ContentValues()
        values.put(WorkoutSummaries.DISTANCE_TOTAL_m, totalDistance)
        values.put(WorkoutSummaries.TIME_ACTIVE_s, activeTime)
        values.put(WorkoutSummaries.TIME_TOTAL_s, totalTime)
        if (activeTime > 0) {
            values.put(WorkoutSummaries.SPEED_AVERAGE_mps, totalDistance / activeTime)
        }

        // ATT-617: Persist total calories burned if present
        if (parsedLaps.any { (it.calories ?: 0) > 0 }) {
            val totalCalories = parsedLaps.sumOf { it.calories ?: 0 }
            values.put(WorkoutSummaries.CALORIES, totalCalories)
        }

        // ATT-617: Persist workout notes if present
        if (!workoutNotes.isNullOrBlank()) {
            values.put(WorkoutSummaries.DESCRIPTION, workoutNotes.trim())
        }

        // ATT-922: Persist workout name if present
        if (!workoutName.isNullOrBlank()) {
            values.put(WorkoutSummaries.WORKOUT_NAME, workoutName.trim())
        }

        // ATT-909 / REQ-MIG-027: Persist base sport type in WorkoutSummaries
        values.put(WorkoutSummaries.B_SPORT, bSportType.name)

        // ATT-617: Persist lap count
        val lapCount = parsedLaps.size.coerceAtLeast(1)
        values.put(WorkoutSummaries.LAPS, lapCount)

        // ATT-602: Build GC_DATA so BaseFileWriter and exporters know which streams exist
        val gcData = StringBuilder(MySensorManager.EMPTY_GC_DATA).apply {
            setCharAt(0, 'T')
            if (totalDistance > 0 || distances.isNotEmpty() || foundSensors.contains(SensorType.DISTANCE_m)) setCharAt(1, 'D')
            if (foundSensors.contains(SensorType.SPEED_mps) || (activeTime > 0 && totalDistance > 0)) setCharAt(2, 'S')
            if (foundSensors.contains(SensorType.POWER)) setCharAt(3, 'P')
            if (foundSensors.contains(SensorType.HR)) setCharAt(4, 'H')
            if (foundSensors.contains(SensorType.CADENCE)) setCharAt(5, 'C')
            if (foundSensors.contains(SensorType.TORQUE)) setCharAt(6, 'N')
            if (altitudes.isNotEmpty() || foundSensors.contains(SensorType.ALTITUDE)) setCharAt(7, 'A')
            if (points.isNotEmpty() || foundSensors.contains(SensorType.LATITUDE) || foundSensors.contains(SensorType.LONGITUDE)) setCharAt(8, 'G')
        }.toString()
        values.put(WorkoutSummaries.GC_DATA, gcData)

        // ATT-617: Save multi-lap entries in LapsDatabaseManager for this imported workout
        try {
            val lapsDb = LapsDatabaseManager.getInstance(context)
            lapsDb.deleteWorkout(workoutId)
            if (parsedLaps.isNotEmpty()) {
                parsedLaps.forEach { lap ->
                    val lapDuration = if (lap.totalTimeSeconds > 0) lap.totalTimeSeconds.toInt() else activeTime
                    val lapDistance = if (lap.distanceMeters > 0) lap.distanceMeters else totalDistance
                    val lapAvgSpeed = if (lapDuration > 0) lapDistance / lapDuration else 0.0
                    if (lap.name != null || lap.description != null) {
                        lapsDb.saveLap(
                            workoutId,
                            lap.lapNr,
                            lap.startTime ?: firstTime,
                            lapDuration,
                            lapDistance,
                            lapAvgSpeed,
                            lap.name,
                            lap.description
                        )
                    } else {
                        lapsDb.saveLap(
                            workoutId,
                            lap.lapNr,
                            lap.startTime ?: firstTime,
                            lapDuration,
                            lapDistance,
                            lapAvgSpeed
                        )
                    }
                }
            } else {
                val avgSpeed = if (activeTime > 0) totalDistance / activeTime else 0.0
                lapsDb.saveLap(workoutId, 0L, firstTime, activeTime, totalDistance, avgSpeed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save laps for workout $workoutId", e)
        }
        
        // 2. Ascent/Descent (5-minute moving average filter) (ATT-301)
        if (altitudes.isNotEmpty()) {
            var totalAscent = 0.0
            var totalDescent = 0.0
            val windowSize = 300 // 5 mins @ 1Hz
            var windowSum = 0.0
            val windowQueue = java.util.ArrayDeque<Double>()
            var lastFiltered: Double? = null

            altitudes.forEach { rawAlt ->
                windowSum += rawAlt
                windowQueue.addLast(rawAlt)
                if (windowQueue.size > windowSize) {
                    windowSum -= windowQueue.removeFirst()
                }
                val filtered = windowSum / windowQueue.size
                val prevFiltered = lastFiltered
                if (prevFiltered != null) {
                    val delta = filtered - prevFiltered
                    if (delta > 0) totalAscent += delta
                    else if (delta < 0) totalDescent += -delta
                }
                lastFiltered = filtered
            }
            values.put(WorkoutSummaries.ASCENDING, totalAscent.toInt())
            values.put(WorkoutSummaries.DESCENDING, totalDescent.toInt())
        }

        // 3. Map & Streams (REQ-DAT-023: Douglas-Peucker simplification & compact scalar streams)
        val simplifiedPoints = if (points.isNotEmpty()) PolyUtil.simplify(points, 10.0) else emptyList()
        val polyline = if (simplifiedPoints.isNotEmpty()) PolyUtil.encode(simplifiedPoints) else ""
        values.put(WorkoutSummaries.MAP_POLYLINE, polyline)

        val sampledAltitudes = if (altitudes.size > WorkoutSummaries.ENCODING_STEP_SIZE) {
            altitudes.filterIndexed { index, _ -> (index + 1) % WorkoutSummaries.ENCODING_STEP_SIZE == 0 }
        } else {
            altitudes
        }
        if (sampledAltitudes.isNotEmpty()) {
            values.put(WorkoutSummaries.ALTITUDE_STREAM, NumericalEncodingUtils.encodeDoubles(sampledAltitudes))
        }

        val sampledDistances = if (distances.size > WorkoutSummaries.ENCODING_STEP_SIZE) {
            distances.filterIndexed { index, _ -> (index + 1) % WorkoutSummaries.ENCODING_STEP_SIZE == 0 }
        } else {
            distances
        }
        if (sampledDistances.isNotEmpty()) {
            values.put(WorkoutSummaries.DISTANCE_STREAM, NumericalEncodingUtils.encodeDoubles(sampledDistances))
        }

        // --- ATT-352: Persist spatial bounds for zero-latency periods framing ---
        if (points.isNotEmpty()) {
            val minLat = points.minOf { it.latitude }
            val maxLat = points.maxOf { it.latitude }
            val minLng = points.minOf { it.longitude }
            val maxLng = points.maxOf { it.longitude }
            values.put(WorkoutSummaries.BOUND_MIN_LAT, minLat)
            values.put(WorkoutSummaries.BOUND_MIN_LNG, minLng)
            values.put(WorkoutSummaries.BOUND_MAX_LAT, maxLat)
            values.put(WorkoutSummaries.BOUND_MAX_LNG, maxLng)
        }

        // Use the static field safely
        values.put("extremumValuesCalculated", 1)
        
        summariesDb.database.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))

        // 4. Persistence of Extrema (ATT-299, ATT-301)
        // ... (remaining sensors logic)
        val sensorsToCalculate = listOf(
            SensorType.HR, SensorType.CADENCE, SensorType.POWER, SensorType.SPEED_mps, 
            SensorType.ALTITUDE, SensorType.TEMPERATURE
        )
        val extremaTypes = listOf(ExtremaType.MIN, ExtremaType.MAX, ExtremaType.AVG)
        
        sensorsToCalculate.forEach { sensor ->
            extremaTypes.forEach { type ->
                val value = samplesDb.calcExtremaValue(summariesDb, baseFileName, type, sensor)
                if (value != null && !value.isNaN()) {
                    val pos = if (sensor == SensorType.ALTITUDE) {
                        when (type) {
                            ExtremaType.MIN -> minAltPos
                            ExtremaType.MAX -> maxAltPos
                            else -> null
                        }
                    } else null
                    summariesDb.updateExtremaValue(workoutId, sensor, type, value, pos)
                }
            }
        }

        // 4b. Calculate and persist Skiba W'bal extrema (MIN, AVG, END) and Aerobic Efficiency / Decoupling (REQ-UI-399.3, REQ-UI-400.2)
        try {
            if (samplesDb.existsTable(baseFileName)) {
                val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
                val powerSamples = mutableListOf<Double?>()
                val speedSamples = mutableListOf<Double?>()
                val hrSamples = mutableListOf<Double?>()
                samplesDb.database.query(tableName, null, null, null, null, null, null).use { cursor ->
                    val powerIdx = cursor.getColumnIndex(SensorType.POWER.name)
                    val speedIdx = cursor.getColumnIndex(SensorType.SPEED_mps.name)
                    val hrIdx = cursor.getColumnIndex(SensorType.HR.name)
                    while (cursor.moveToNext()) {
                        val p = if (powerIdx != -1 && !cursor.isNull(powerIdx)) cursor.getDouble(powerIdx) else null
                        val s = if (speedIdx != -1 && !cursor.isNull(speedIdx)) cursor.getDouble(speedIdx) else null
                        val hr = if (hrIdx != -1 && !cursor.isNull(hrIdx)) cursor.getDouble(hrIdx) else null
                        powerSamples.add(p)
                        speedSamples.add(s)
                        hrSamples.add(hr)
                    }
                }

                val effectiveCp = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getEffectiveCpWatts(context)
                val effectiveWPrimeKj = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getEffectiveWPrimeKj(context).toDouble()
                if (effectiveCp != null && effectiveCp > 0 && effectiveWPrimeKj > 0.0 && powerSamples.any { it != null && it > 0.0 }) {
                    val wPrimeSummary = com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeWPrimeBalanceSeries(
                        powerSamplesWatts = powerSamples,
                        cpWatts = effectiveCp.toDouble(),
                        wPrimeKj = effectiveWPrimeKj
                    )
                    wPrimeSummary.minWPrimeBalKj?.let {
                        summariesDb.updateExtremaValue(workoutId, SensorType.W_PRIME_BAL, ExtremaType.MIN, it, null)
                    }
                    wPrimeSummary.avgWPrimeBalKj?.let {
                        summariesDb.updateExtremaValue(workoutId, SensorType.W_PRIME_BAL, ExtremaType.AVG, it, null)
                    }
                    wPrimeSummary.endWPrimeBalKj?.let {
                        summariesDb.updateExtremaValue(workoutId, SensorType.W_PRIME_BAL, ExtremaType.END, it, null)
                    }
                }

                // REQ-UI-400.2: Efficiency Factor (EF) & Aerobic Decoupling (Pw:Hr / Pa:Hr)
                val aerobicResult = com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
                    powerSamplesWatts = powerSamples,
                    speedSamplesMps = speedSamples,
                    hrSamplesBpm = hrSamples,
                    bSportType = bSportType
                )
                val targetSensor = if (aerobicResult.isPowerBased) SensorType.POWER else SensorType.SPEED_mps
                aerobicResult.efficiencyFactor?.let {
                    summariesDb.updateExtremaValue(workoutId, targetSensor, ExtremaType.EFFICIENCY_FACTOR, it, null)
                }
                aerobicResult.decouplingPct?.let {
                    summariesDb.updateExtremaValue(workoutId, targetSensor, ExtremaType.AEROBIC_DECOUPLING, it, null)
                }

                // REQ-MIG-039.4 (ATT-3860): Calculate and persist session power training metrics (NP, IF, TSS, Work kJ, W/kg)
                val workoutStartMs: Long? = firstTime?.let {
                    try {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(it)?.time
                    } catch (_: Exception) {
                        null
                    }
                }
                val effectiveFtp = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.resolveEffectiveFtpAt(context, workoutStartMs)
                val effectiveWeightKg = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.resolveEffectiveWeightKgAt(context, workoutStartMs)
                val powerMetrics = com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computePowerTrainingMetrics(
                    powerSamplesWatts = powerSamples,
                    activeTimeSeconds = activeTime,
                    ftpWatts = effectiveFtp?.toDouble(),
                    athleteWeightKg = effectiveWeightKg.toDouble().takeIf { it > 0.0 },
                    fallbackNpWatts = sessionNormalizedPowerWatts,
                    fallbackIf = sessionIntensityFactor,
                    fallbackTss = sessionTss,
                    fallbackWorkKj = sessionWorkKj
                )
                powerMetrics.normalizedPowerWatts?.let { np ->
                    summariesDb.updateExtremaValue(workoutId, SensorType.NORMALIZED_POWER, ExtremaType.AVG, np, null)
                    summariesDb.updateExtremaValue(workoutId, SensorType.NORMALIZED_POWER, ExtremaType.END, np, null)
                }
                powerMetrics.intensityFactor?.let { ifVal ->
                    summariesDb.updateExtremaValue(workoutId, SensorType.INTENSITY_FACTOR, ExtremaType.AVG, ifVal, null)
                    summariesDb.updateExtremaValue(workoutId, SensorType.INTENSITY_FACTOR, ExtremaType.END, ifVal, null)
                }
                powerMetrics.tss?.let { tss ->
                    summariesDb.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.AVG, tss, null)
                    summariesDb.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.MAX, tss, null)
                    summariesDb.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.END, tss, null)
                }
                powerMetrics.workKj?.let { workKj ->
                    summariesDb.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.AVG, workKj, null)
                    summariesDb.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.MAX, workKj, null)
                    summariesDb.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.END, workKj, null)
                }
                powerMetrics.avgPowerToWeight?.let { avgWPerKg ->
                    summariesDb.updateExtremaValue(workoutId, SensorType.POWER_TO_WEIGHT, ExtremaType.AVG, avgWPerKg, null)
                }
                powerMetrics.maxPowerToWeight?.let { maxWPerKg ->
                    summariesDb.updateExtremaValue(workoutId, SensorType.POWER_TO_WEIGHT, ExtremaType.MAX, maxWPerKg, null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not compute W'bal, Aerobic Efficiency, or Power Training extrema during recalculateStats: ${e.message}")
        }

        // 5. Clustering (Spatial Markers)
        if (points.isNotEmpty()) {
            val start = points.first()
            val end = points.last()
            
            var maxDisp = -1.0
            var apex = start
            points.forEach { pt ->
                val d = WorkoutClusterEngine.getInstance(context).distanceBetween(start, pt).toDouble()
                if (d > maxDisp) {
                    maxDisp = d
                    apex = pt
                }
            }

            summariesDb.updateExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.START, start.latitude, start)
            summariesDb.updateExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.START, start.longitude, start)
            summariesDb.updateExtremaValue(workoutId, SensorType.LATITUDE, ExtremaType.END, end.latitude, end)
            summariesDb.updateExtremaValue(workoutId, SensorType.LONGITUDE, ExtremaType.END, end.longitude, end)
            summariesDb.updateExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX, maxDisp, apex)

            // ATT-314: Skip clustering if a cluster is already assigned
            val existingClusterId = summariesDb.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) ?: -1L
            if (existingClusterId != -1L) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Workout $workoutId already has cluster $existingClusterId assigned. Skipping clustering logic.")
            } else {
                val clusterEngine = WorkoutClusterEngine.getInstance(context)
                // ATT-909 / REQ-MIG-027: Candidate sport inference and workout name matching
                val avgSpeed = if (activeTime > 0) totalDistance / activeTime else 0.0
                val candidateSports = if (bSportType != BSportType.UNKNOWN) {
                    setOf(bSportType)
                } else {
                    try {
                        EquipmentAndSportTypeDiscoveryManager.getInstance(context)
                            .getCandidateBSportTypes(BSportType.UNKNOWN, avgSpeed)
                    } catch (e: Exception) {
                        emptySet()
                    }
                }

                val matchingCluster = clusterEngine.suggestCluster(
                    start = start,
                    end = end,
                    apex = apex,
                    distance = totalDistance,
                    workoutName = workoutName,
                    candidateSportTypes = candidateSports,
                    minAltPos = minAltPos,
                    maxAltPos = maxAltPos
                )
                
                if (matchingCluster != null) {
                    var sportId = summariesDb.getLong(workoutId, WorkoutSummaries.SPORT_ID) ?: -1L
                    var effectiveBSport = bSportType
                    if (effectiveBSport == BSportType.UNKNOWN && matchingCluster.bSportType != BSportType.UNKNOWN) {
                        effectiveBSport = matchingCluster.bSportType
                    }
                    if (sportId == -1L && effectiveBSport != BSportType.UNKNOWN) {
                        sportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(effectiveBSport)
                        val updateVals = ContentValues().apply {
                            put(WorkoutSummaries.SPORT_ID, sportId)
                            put(WorkoutSummaries.B_SPORT, effectiveBSport.name)
                        }
                        summariesDb.database.update(WorkoutSummaries.TABLE, updateVals, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))
                    }
                    
                    // ATT-316 Refinement: Only lock during the actual DB write/learning phase
                    recalculationMutex.withLock {
                        // Refine existing cluster (ATT-308: ensure sport type is propagated/stored)
                        clusterEngine.learnFromWorkout(start, end, apex, totalDistance, matchingCluster.name, sportId, matchingCluster.id, minAltPos = minAltPos, maxAltPos = maxAltPos)
                        clusterEngine.assignClusterToWorkout(context, workoutId, matchingCluster.id, false)
                    }
                } else {
                    val startTime = summariesDb.getString(workoutId, WorkoutSummaries.TIME_START)
                    val (existingId, customName) = listener?.onNewClusterCandidate(
                        date = startTime ?: baseFileName,
                        start = start, end = end, apex = apex, 
                        distance = totalDistance, 
                        bSportType = bSportType,
                        polyline = polyline,
                        workoutName = workoutName,
                        candidateSportTypes = candidateSports,
                        minAltPos = minAltPos,
                        maxAltPos = maxAltPos
                    ) ?: Pair(null, null)
                    
                    recalculationMutex.withLock {
                        if (existingId != null) {
                            val cluster = WorkoutClusterDatabaseManager.getInstance(context).getClusterById(existingId)
                            var sportId = summariesDb.getLong(workoutId, WorkoutSummaries.SPORT_ID) ?: -1L
                            if (sportId == -1L && bSportType != BSportType.UNKNOWN) {
                                sportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                            }
                            if (cluster != null) {
                                clusterEngine.learnFromWorkout(start, end, apex, totalDistance, cluster.name, sportId, existingId, minAltPos = minAltPos, maxAltPos = maxAltPos)
                            }
                            clusterEngine.assignClusterToWorkout(context, workoutId, existingId, true)
                        } else if (!customName.isNullOrBlank()) {
                            var sportId = summariesDb.getLong(workoutId, WorkoutSummaries.SPORT_ID) ?: -1L
                            if (sportId == -1L && bSportType != BSportType.UNKNOWN) {
                                sportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                            }
                            val newId = clusterEngine.learnFromWorkout(start, end, apex, totalDistance, customName, sportId, -1L, minAltPos = minAltPos, maxAltPos = maxAltPos)
                            clusterEngine.assignClusterToWorkout(context, workoutId, newId, true)
                        }
                    }
                }
            }
        }

        // 6. Direct Notification Pipeline & Legacy Broadcast (ATT-909 / REQ-MIG-026)
        // Direct invocation ensures WorkoutRepository and PeriodsRepository process the workout
        // even if WorkoutRepository was not previously instantiated in memory (unbuffered broadcast issue).
        try {
            val app = (context.applicationContext as? Application) ?: (context as? Application)
            if (app != null) {
                val workoutRepo = WorkoutRepository.getInstance(app)
                workoutRepo.reloadWorkoutData(workoutId)
                WorkoutClusterRepository.getInstance(app).refreshClusters()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct repository notification skipped or failed: ${e.message}")
        }

        try {
            val intent = android.content.Intent(com.atrainingtracker.trainingtracker.tracker.TrackerService.WORKOUT_UPDATED_INTENT)
            intent.putExtra(com.atrainingtracker.trainingtracker.tracker.TrackerService.WORKOUT_ID, workoutId)
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Legacy broadcast delivery skipped or failed: ${e.message}")
        }
    }

    private fun calculateCumulativeDistance(points: List<LatLng>): Double {
        var totalDist = 0.0
        val results = FloatArray(1)
        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]
            android.location.Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
            totalDist += results[0]
        }
        return totalDist
    }

    /**
     * Schedules asynchronous upload of an imported workout to Strava and active online communities.
     *
     * REQ-EXT-008 (ATT-602): Evaluates active community export formats. If community upload is enabled
     * (e.g. TrainingApplication.uploadToCommunity(FileFormat.STRAVA)) and not opted-out for this workout,
     * enqueues an asynchronous export and upload task via [ExportManager].
     */
    internal fun schedulePostImportCommunityUpload(
        context: Context,
        workoutId: Long,
        baseFileName: String,
        exportManager: ExportManager = ExportManager(context)
    ) {
        try {
            Log.i(TAG, "schedulePostImportCommunityUpload: workoutId=$workoutId, baseFileName=$baseFileName")
            // ATT-602: Ensure export status entries exist for this workout in ExportStatusDatabaseManager
            try {
                val exportStatusDb = ExportStatusDatabaseManager.getInstance(context)
                val existingRows = exportStatusDb.getExportRows(baseFileName)
                Log.i(TAG, "Existing export status rows for $baseFileName: count=${existingRows.size}")
                if (existingRows.isEmpty()) {
                    exportManager.newWorkout(baseFileName)
                    Log.i(TAG, "Initialized new export status rows for $baseFileName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize export status rows for $baseFileName", e)
            }

            for (format in ExportType.COMMUNITY.exportToFileFormats) {
                val uploadEnabled = TrainingApplication.uploadToCommunity(format)
                Log.i(TAG, "Checking community format ${format.name}: uploadToCommunity=$uploadEnabled")
                if (uploadEnabled) {
                    if (format == FileFormat.STRAVA) {
                        val summaryDb = try {
                            WorkoutSummariesDatabaseManager.getInstance(context)
                        } catch (_: Exception) {
                            null
                        }
                        val uploadToStrava = getUploadToStravaStatus(summaryDb, workoutId)
                        Log.i(TAG, "Strava uploadToStrava status in DB for workout $workoutId: $uploadToStrava")
                        if (uploadToStrava == 0) {
                            Log.i(TAG, "Skipping Strava upload for workout $workoutId: Explicitly opted out.")
                            continue
                        }
                    }
                    Log.i(TAG, "Calling exportManager.exportWorkoutTo(workoutId=$workoutId, format=${format.name})")
                    exportManager.exportWorkoutTo(workoutId, format)
                    Log.i(TAG, "Scheduled community upload for workout $workoutId ($baseFileName) to ${format.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule community upload for workout $workoutId ($baseFileName)", e)
        }
    }

    private fun getUploadToStravaStatus(db: WorkoutSummariesDatabaseManager?, workoutId: Long): Int {
        return try {
            val database = db?.database ?: return -1
            database.query(
                WorkoutSummaries.TABLE,
                arrayOf(WorkoutSummaries.UPLOAD_TO_STRAVA),
                "${WorkoutSummaries.C_ID} = ?",
                arrayOf(workoutId.toString()),
                null, null, null
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else -1
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query uploadToStrava for workout $workoutId", e)
            -1
        }
    }
}
