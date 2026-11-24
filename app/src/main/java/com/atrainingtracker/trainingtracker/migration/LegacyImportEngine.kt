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
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.migration.parsers.FitWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.GpxWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.ParsedWorkoutData
import com.atrainingtracker.trainingtracker.migration.parsers.TcxWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.WorkoutParser
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusRepository
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.util.StreamSummaryCalculator
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
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
        ): ClusterDecision
    }

    data class ClusterDecision(
        val clusterId: Long? = null,
        val newClusterName: String? = null,
        val customWorkoutName: String? = null
    )

    data class RecoveryResult(
        val importedCount: Int,
        val skippedCount: Int,
        val failedCount: Int,
        val totalScanned: Int
    )

    /**
     * Scans Dropbox recursively across all target paths and recovers all legacy workouts
     * via the shared hardened [CloudWorkoutSourceScanner] and [executeBulkCloudRecovery] pipeline (`REQ-MIG-061.3`, `.4`).
     */
    suspend fun bulkRecoverFromDropbox(
        context: Context,
        format: String,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): RecoveryResult {
        val dbxClient = CloudWorkoutSourceScanner.createDropboxClient() ?: return RecoveryResult(0, 0, 0, 0)
        val entries = CloudWorkoutSourceScanner.listDropboxEntries(
            dbxClient = dbxClient,
            format = format,
            includeWorkoutsRoot = false,
            includeZip = true,
            onStatus = { status -> listener?.onStatus(status) }
        )
        return executeBulkCloudRecovery(
            context = context,
            entries = entries,
            cacheDirName = "legacy_recovery",
            downloadingStatusResId = R.string.legacy_import__downloading_dropbox,
            format = format,
            listener = listener,
            uploadToStrava = uploadToStrava,
            downloader = { entry, targetFile ->
                CloudWorkoutSourceScanner.downloadDropboxWithRetry(dbxClient, entry.pathLower ?: entry.name, targetFile)
            }
        )
    }

    /**
     * Scans Google Drive recursively across candidate paths and recovers historical workouts (`.fit`, `.tcx`, `.gpx`)
     * via the shared hardened [CloudWorkoutSourceScanner] and [executeBulkCloudRecovery] pipeline (`REQ-MIG-034`, `REQ-MIG-061.3`, `.4`).
     */
    suspend fun bulkRecoverFromGoogleDrive(
        context: Context,
        format: String = "all",
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): RecoveryResult {
        val driveClient = CloudWorkoutSourceScanner.createGoogleDriveClient(context) ?: return RecoveryResult(0, 0, 0, 0)
        val entries = CloudWorkoutSourceScanner.listGoogleDriveEntries(
            driveClient = driveClient,
            format = format,
            includeAppRoot = true,
            includeZip = true,
            onStatus = { status -> listener?.onStatus(status) }
        )
        return executeBulkCloudRecovery(
            context = context,
            entries = entries,
            cacheDirName = "gdrive_recovery",
            downloadingStatusResId = R.string.legacy_import__downloading_google_drive,
            format = format,
            listener = listener,
            uploadToStrava = uploadToStrava,
            downloader = { entry, targetFile ->
                CloudWorkoutSourceScanner.downloadGoogleDriveWithRetry(driveClient, entry.id, targetFile)
            }
        )
    }

    /**
     * Shared 3-worker concurrent cloud bulk-recovery pipeline enforcing pre-download deduplication,
     * [SecureZipExtractor] filename sanitization and `.zip` extraction, cooperative cancellation,
     * and guaranteed outer staging directory cleanup (`REQ-MIG-061.4`).
     */
    internal suspend fun executeBulkCloudRecovery(
        context: Context,
        entries: List<CloudWorkoutSourceScanner.CloudRemoteEntry>,
        cacheDirName: String,
        downloadingStatusResId: Int,
        format: String,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava(),
        existingSnapshotProvider: () -> ExistingWorkoutsSnapshot = { ExistingWorkoutsSnapshot.create(context) },
        downloader: suspend (CloudWorkoutSourceScanner.CloudRemoteEntry, File) -> Boolean
    ): RecoveryResult {
        if (entries.isEmpty()) return RecoveryResult(0, 0, 0, 0)

        val tempDir = File(context.cacheDir, cacheDirName)
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        val importedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val skippedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val failedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val processedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val existingSnapshot = existingSnapshotProvider()

        try {
            // ATT-493 Fix: Concurrent import pipeline using coroutineScope and a 3-worker channel.
            withBulkImportBatch {
                kotlinx.coroutines.coroutineScope {
                    val channel = kotlinx.coroutines.channels.Channel<Pair<Int, CloudWorkoutSourceScanner.CloudRemoteEntry>>(
                        kotlinx.coroutines.channels.Channel.UNLIMITED
                    )
                    entries.forEachIndexed { index, entry -> channel.trySend(Pair(index, entry)) }
                    channel.close()

                    (1..3).map {
                        launch(Dispatchers.IO) {
                            for ((_, entry) in channel) {
                                coroutineContext.ensureActive()
                                val current = processedCount.incrementAndGet()
                                listener?.onProgress(current, entries.size, entry.name)

                                val isZipArchive = entry.name.endsWith(".zip", ignoreCase = true)
                                val baseFileName = CloudWorkoutSourceScanner.normalizeBaseFileName(entry.name)
                                if (!isZipArchive && existingSnapshot.containsFileBaseName(baseFileName)) {
                                    if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                                    skippedCount.incrementAndGet()
                                    continue
                                }

                                listener?.onStatus(context.getString(downloadingStatusResId, entry.name))
                                val workerDir = File(tempDir, "job_$current").apply { mkdirs() }
                                val tempFile = CloudWorkoutSourceScanner.sanitizeRemoteTargetFile(entry.name, workerDir)
                                if (tempFile == null) {
                                    failedCount.incrementAndGet()
                                    continue
                                }

                                try {
                                    val downloaded = downloader(entry, tempFile)
                                    if (!downloaded) {
                                        failedCount.incrementAndGet()
                                        continue
                                    }

                                    val filesToImport = CloudWorkoutSourceScanner.prepareLocalImportFiles(tempFile, workerDir)
                                    if (filesToImport.isEmpty()) {
                                        failedCount.incrementAndGet()
                                        continue
                                    }

                                    for (workoutFile in filesToImport) {
                                        coroutineContext.ensureActive()
                                        val workoutBaseName = CloudWorkoutSourceScanner.normalizeBaseFileName(workoutFile.name)
                                        if (isZipArchive && existingSnapshot.containsFileBaseName(workoutBaseName)) {
                                            skippedCount.incrementAndGet()
                                            continue
                                        }

                                        val status = dispatchLocalFileInternal(context, workoutFile, format, listener, uploadToStrava)
                                        when (status) {
                                            ImportStatus.SUCCESS -> {
                                                existingSnapshot.register(workoutBaseName)
                                                importedCount.incrementAndGet()
                                            }
                                            ImportStatus.DUPLICATE_SKIPPED -> {
                                                existingSnapshot.register(workoutBaseName)
                                                skippedCount.incrementAndGet()
                                            }
                                            ImportStatus.FAILED -> failedCount.incrementAndGet()
                                        }
                                    }
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (se: SecurityException) {
                                    Log.e(TAG, "Security violation during cloud recovery for ${entry.name}", se)
                                    failedCount.incrementAndGet()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to download/import ${entry.name}", e)
                                    failedCount.incrementAndGet()
                                } finally {
                                    workerDir.deleteRecursively()
                                }
                            }
                        }
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Bulk recovery failed", e)
        } finally {
            tempDir.deleteRecursively()
        }

        return RecoveryResult(
            importedCount = importedCount.get(),
            skippedCount = skippedCount.get(),
            failedCount = failedCount.get(),
            totalScanned = entries.size
        )
    }

    private suspend fun dispatchLocalFileInternal(
        context: Context,
        file: File,
        formatHint: String,
        listener: ProgressListener?,
        uploadToStrava: Boolean
    ): ImportStatus {
        val ext = file.name.substringAfterLast('.').lowercase(Locale.ROOT)
        return when (ext) {
            "tcx" -> importFromTcxInternal(context, file, listener, uploadToStrava)
            "gpx" -> importFromGpxInternal(context, file, listener, uploadToStrava)
            "fit" -> importFromFitInternal(context, file, listener, uploadToStrava)
            else -> when (formatHint.lowercase(Locale.ROOT)) {
                "tcx" -> importFromTcxInternal(context, file, listener, uploadToStrava)
                "gpx" -> importFromGpxInternal(context, file, listener, uploadToStrava)
                "fit" -> importFromFitInternal(context, file, listener, uploadToStrava)
                else -> ImportStatus.FAILED
            }
        }
    }

    /**
     * Unified workout file ingestion routine delegating parsing to [parser] (`REQ-MIG-061.1`).
     */
    suspend fun importWorkoutFileResult(
        context: Context,
        file: File,
        parser: WorkoutParser,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportResult {
        try {
            val baseFileName = file.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")

            // ATT-314: Early exit if workout already exists to prevent redundant processing
            if (!baseFileName.startsWith("legacy_import", ignoreCase = true) &&
                WorkoutRepository.getInstance(context).isWorkoutExisting(baseFileName)
            ) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
            }

            val parseResult = parser.parse(file, context)
            if (parseResult.isFailure) {
                Log.e(TAG, "Failed to parse ${parser.formatExtension.uppercase(Locale.ROOT)}: ${parseResult.exceptionOrNull()?.message}")
                return ImportResult(ImportStatus.FAILED)
            }
            val parsedWorkout = parseResult.getOrThrow()

            return persistParsedWorkout(
                context = context,
                baseFileName = baseFileName,
                parsedWorkout = parsedWorkout,
                source = parser.workoutSource,
                uploadToStrava = uploadToStrava,
                listener = listener
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import ${parser.formatExtension.uppercase(Locale.ROOT)}: ${file.name}", e)
        }
        return ImportResult(ImportStatus.FAILED)
    }

    /**
     * Recreates a workout from a TCX file returning an enriched [ImportResult] (REQ-MIG-032, REQ-MIG-038).
     */
    suspend fun importFromTcxResult(
        context: Context,
        tcxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportResult = importWorkoutFileResult(context, tcxFile, TcxWorkoutParser, listener, uploadToStrava)

    /**
     * Recreates a workout from a TCX file.
     */
    suspend fun importFromTcx(
        context: Context,
        tcxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): Boolean = importFromTcxResult(context, tcxFile, listener, uploadToStrava).status == ImportStatus.SUCCESS

    internal suspend fun importFromTcxInternal(
        context: Context,
        tcxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportStatus = importFromTcxResult(context, tcxFile, listener, uploadToStrava).status

    /**
     * Recreates a workout from a GPX file (GPX 1.0 or 1.1) returning an enriched [ImportResult] (REQ-MIG-030, REQ-MIG-032).
     */
    suspend fun importFromGpxResult(
        context: Context,
        gpxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportResult = importWorkoutFileResult(context, gpxFile, GpxWorkoutParser, listener, uploadToStrava)

    /**
     * Recreates a workout from a GPX file (GPX 1.0 or 1.1).
     */
    suspend fun importFromGpx(
        context: Context,
        gpxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): Boolean = importFromGpxResult(context, gpxFile, listener, uploadToStrava).status == ImportStatus.SUCCESS

    internal suspend fun importFromGpxInternal(
        context: Context,
        gpxFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportStatus = importFromGpxResult(context, gpxFile, listener, uploadToStrava).status

    /**
     * Recreates a workout from an external Garmin FIT binary file returning an enriched [ImportResult] (REQ-DAT-019, REQ-MIG-032).
     */
    suspend fun importFromFitResult(
        context: Context,
        fitFile: File,
        listener: ProgressListener? = null,
        uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()
    ): ImportResult = importWorkoutFileResult(context, fitFile, FitWorkoutParser, listener, uploadToStrava)

    /**
     * Persists in-memory parsed workout data into SQLite database tables and triggers
     * stats recalculation and cloud synchronization (REQ-MIG-038, REQ-MIG-043).
     */
    internal suspend fun persistParsedWorkout(
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

        // Fail-fast if workout has no valid start time to avoid orphan database rows (REQ-MIG-043)
        if (firstTime == null) {
            Log.w(TAG, "Cannot import workout without valid start time: $baseFileName")
            return ImportResult(ImportStatus.FAILED)
        }

        val workoutRepo = WorkoutRepository.getInstance(context)
        val exportStatusRepo = ExportStatusRepository.getInstance(context)
        var workoutId = -1L

        // Mutex-guarded multi-dimensional deduplication and atomic insertion (REQ-MIG-031, REQ-MIG-043)
        val isDuplicate = try {
            importMutex.withLock {
                if (workoutRepo.isWorkoutExisting(effectiveBaseFileName, firstTime, parsedWorkout.bSportType)) {
                    if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $effectiveBaseFileName: Workout already exists.")
                    return@withLock true
                }

                workoutId = workoutRepo.getWorkoutIdByBaseFileName(effectiveBaseFileName)
                if (workoutId == -1L) {
                    try {
                        exportStatusRepo.deleteStravaWorkout(effectiveBaseFileName)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Could not clean StravaUploadDb for $effectiveBaseFileName: ${t.message}")
                    }

                    val resolvedEquipmentId = if (parsedWorkout.equipmentId > 0) {
                        parsedWorkout.equipmentId
                    } else if (!parsedWorkout.equipmentName.isNullOrBlank()) {
                        try {
                            com.atrainingtracker.trainingtracker.repositories.EquipmentRepository.getInstance(context)
                                .equipmentItems.find { it.name.equals(parsedWorkout.equipmentName.trim(), ignoreCase = true) }
                                ?.id ?: -1L
                        } catch (_: Exception) {
                            -1L
                        }
                    } else {
                        -1L
                    }

                    val uploadToStravaPref = parsedWorkout.stravaActivityId.isNullOrBlank() && uploadToStrava && TrainingApplication.uploadToCommunity(FileFormat.STRAVA)
                    val wName = if (!parsedWorkout.workoutName.isNullOrBlank()) parsedWorkout.workoutName.trim() else effectiveBaseFileName
                    workoutId = workoutRepo.insertImportedWorkout(
                        fileBaseName = effectiveBaseFileName,
                        workoutName = wName,
                        timeStart = firstTime,
                        sportId = parsedWorkout.resolvedSportId,
                        bSportType = parsedWorkout.bSportType,
                        equipmentId = resolvedEquipmentId,
                        workoutNotes = parsedWorkout.workoutNotes,
                        goal = parsedWorkout.goal,
                        isCommute = parsedWorkout.isCommute,
                        source = source.name,
                        uploadToStrava = uploadToStravaPref,
                        rpe = parsedWorkout.sessionRpe,
                        feel = parsedWorkout.sessionFeel,
                        method = parsedWorkout.method,
                        isTrainer = parsedWorkout.isTrainer,
                        isRace = parsedWorkout.isRace,
                        isEpic = parsedWorkout.isEpic
                    )
                    require(workoutId > 0) { "Failed to insert summary row for $effectiveBaseFileName" }

                    if (!parsedWorkout.stravaActivityId.isNullOrBlank()) {
                        try {
                            exportStatusRepo.updateStravaAll(
                                effectiveBaseFileName,
                                parsedWorkout.stravaActivityId,
                                parsedWorkout.stravaActivityId,
                                "Uploaded",
                                null
                            )
                        } catch (t: Throwable) {
                            Log.w(TAG, "Could not register Strava ID in StravaUploadDb for $effectiveBaseFileName: ${t.message}")
                        }
                    }
                }

                // Post-parsing: Bulk insertion and dynamic table creation (ATT-357 / ATT-602)
                if (parsedWorkout.bufferedSamples.isNotEmpty()) {
                    try {
                        workoutRepo.insertWorkoutSamplesBatch(
                            effectiveBaseFileName,
                            SensorType.values().toList(),
                            parsedWorkout.bufferedSamples
                        )
                    } catch (t: Throwable) {
                        // Compensatory cleanup: drop sample table and delete summary row
                        workoutRepo.executeCompensatoryImportCleanup(effectiveBaseFileName, workoutId)
                        throw t
                    }
                }
                false
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Failed during mutex-guarded insertion for $effectiveBaseFileName", t)
            return ImportResult(ImportStatus.FAILED)
        }

        if (isDuplicate) {
            return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
        }

        try {
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
                workoutName = parsedWorkout.workoutName?.trim()?.takeIf { it.isNotEmpty() } ?: effectiveBaseFileName,
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

            val hasBeats = (parsedWorkout.parsedRrBeats.isNotEmpty() || parsedWorkout.rawRrIntervals.isNotEmpty())
            if (hasBeats && workoutId > 0) {
                try {
                    val startMs = parseTimeToMs(firstTime)
                    val reconstructor = com.atrainingtracker.trainingtracker.database.hrv.RrBeatTimestampReconstructor(startMs)
                    val beatsToProcess = if (parsedWorkout.parsedRrBeats.isNotEmpty()) {
                        parsedWorkout.parsedRrBeats
                    } else {
                        parsedWorkout.rawRrIntervals.map { com.atrainingtracker.trainingtracker.migration.parsers.FitRrBeat(it, startMs) }
                    }
                    val rrEntities = ArrayList<com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval>(beatsToProcess.size)
                    for ((idx, beatInfo) in beatsToProcess.withIndex()) {
                        val anchorMs = if (beatInfo.anchorTimestampMs > 0L) beatInfo.anchorTimestampMs else startMs
                        val beat = reconstructor.reconstruct(anchorMs, beatInfo.rrMs)
                        rrEntities.add(
                            com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval(
                                workoutId = workoutId,
                                beatIndex = idx,
                                beatTimestampMs = beat.beatTimestampMs,
                                rrMs = beatInfo.rrMs.toFloat(),
                                artifactFlag = beat.artifactFlag,
                                gapBeats = beat.gapBeats
                            )
                        )
                    }
                    com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository.getInstance(context).insertIntervalsBlocking(rrEntities)
                    com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputer.recomputeWorkout(context, workoutId)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to persist imported RR intervals or recompute HRV for $effectiveBaseFileName", t)
                }
            }

            schedulePostImportCommunityUpload(context, workoutId, effectiveBaseFileName)
            return ImportResult(ImportStatus.SUCCESS, workoutId)
        } catch (ce: CancellationException) {
            workoutRepo.executeCompensatoryImportCleanup(effectiveBaseFileName, workoutId)
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "recalculateStats failed for $effectiveBaseFileName", t)
            workoutRepo.executeCompensatoryImportCleanup(effectiveBaseFileName, workoutId)
            return ImportResult(ImportStatus.FAILED)
        }
    }

    private fun parseTimeToMs(timeStr: String?): Long {
        if (timeStr == null) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd_HHmmss"
        )
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(timeStr)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
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

    /**
     * Checks if a workout already exists in WorkoutSummaries by base file name or start timestamp.
     * Evaluates exact FILE_BASE_NAME, exact TIME_START, and a ±30-second epoch window to prevent
     * duplicate imports of renamed activities (REQ-MIG-031).
     */
    internal fun isWorkoutExisting(
        workoutRepo: WorkoutRepository,
        fileBaseName: String,
        timeStart: String? = null,
        bSportType: BSportType? = null
    ): Boolean {
        return workoutRepo.isWorkoutExisting(fileBaseName, timeStart, bSportType)
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
        val workoutRepo = WorkoutRepository.getInstance(context)
        
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

        val avgSpeed = if (activeTime > 0) totalDistance / activeTime else 0.0
        val totalCalories = if (parsedLaps.any { (it.calories ?: 0) > 0 }) parsedLaps.sumOf { it.calories ?: 0 } else null

        // ATT-617: Persist lap count
        val lapCount = parsedLaps.size.coerceAtLeast(1)

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

        // ATT-617: Save multi-lap entries in LapsDatabaseManager for this imported workout
        try {
            workoutRepo.deleteWorkoutLaps(workoutId)
            if (parsedLaps.isNotEmpty()) {
                parsedLaps.forEach { lap ->
                    val lapDuration = if (lap.totalTimeSeconds > 0) lap.totalTimeSeconds.toInt() else activeTime
                    val lapDistance = if (lap.distanceMeters > 0) lap.distanceMeters else totalDistance
                    val lapAvgSpeed = if (lapDuration > 0) lapDistance / lapDuration else 0.0
                    if (lap.name != null || lap.description != null) {
                        workoutRepo.saveLap(
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
                        workoutRepo.saveLap(
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
                val defaultAvgSpeed = if (activeTime > 0) totalDistance / activeTime else 0.0
                workoutRepo.saveLap(workoutId, 0L, firstTime, activeTime, totalDistance, defaultAvgSpeed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save laps for workout $workoutId", e)
        }
        
        // 2. Ascent/Descent (5-minute moving average filter) (ATT-301, REQ-DAT-047.4)
        var totalAscent: Int? = null
        var totalDescent: Int? = null
        if (altitudes.isNotEmpty()) {
            val ascentDescent = StreamSummaryCalculator.computeAscentDescentMovingAverage(
                altitudes,
                StreamSummaryCalculator.DEFAULT_MOVING_AVERAGE_WINDOW
            )
            totalAscent = ascentDescent.ascending.toInt()
            totalDescent = ascentDescent.descending.toInt()
        }

        // 3. Map & Streams (REQ-DAT-023, REQ-DAT-047.4: Douglas-Peucker simplification & compact scalar streams)
        val polyline = StreamSummaryCalculator.encodePolyline(
            points,
            StreamSummaryCalculator.DEFAULT_POLYLINE_SIMPLIFY_TOLERANCE_M
        ) ?: ""

        val altitudeStream = StreamSummaryCalculator.encodeDoubleStream(
            StreamSummaryCalculator.subsampleLegacyStride(altitudes, StreamSummaryCalculator.DEFAULT_LEGACY_STRIDE)
        )
        val distanceStream = StreamSummaryCalculator.encodeDoubleStream(
            StreamSummaryCalculator.subsampleLegacyStride(distances, StreamSummaryCalculator.DEFAULT_LEGACY_STRIDE)
        )

        val bounds = StreamSummaryCalculator.computeBounds(points)
        val minLat = bounds?.minLat
        val minLng = bounds?.minLng
        val maxLat = bounds?.maxLat
        val maxLng = bounds?.maxLng

        workoutRepo.updateImportedWorkoutStats(
            workoutId = workoutId,
            totalDistance = totalDistance,
            activeTime = activeTime,
            totalTime = totalTime,
            avgSpeed = avgSpeed,
            calories = totalCalories,
            workoutNotes = workoutNotes,
            workoutName = workoutName,
            bSportType = bSportType,
            lapCount = lapCount,
            gcData = gcData,
            ascending = totalAscent,
            descending = totalDescent,
            polyline = polyline,
            altitudeStream = altitudeStream,
            distanceStream = distanceStream,
            minLat = minLat,
            minLng = minLng,
            maxLat = maxLat,
            maxLng = maxLng
        )

        // 4. Persistence of Extrema (ATT-299, ATT-301)
        workoutRepo.calculateAndSaveExtrema(workoutId, baseFileName, minAltPos, maxAltPos)

        // 4b. Calculate and persist Skiba W'bal extrema (MIN, AVG, END) and Aerobic Efficiency / Decoupling (REQ-UI-399.3, REQ-UI-400.2)
        try {
            val samples = workoutRepo.getPowerSpeedHrSamples(baseFileName)
            val powerSamples = samples.powerSamples
            val speedSamples = samples.speedSamples
            val hrSamples = samples.hrSamples

            if (powerSamples.isNotEmpty() || speedSamples.isNotEmpty() || hrSamples.isNotEmpty()) {
                val effectiveCp = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getEffectiveCpWatts(context)
                val effectiveWPrimeKj = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getEffectiveWPrimeKj(context).toDouble()
                if (effectiveCp != null && effectiveCp > 0 && effectiveWPrimeKj > 0.0 && powerSamples.any { it != null && it > 0.0 }) {
                    val wPrimeSummary = com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator.computeWPrimeBalanceSeries(
                        powerSamplesWatts = powerSamples,
                        cpWatts = effectiveCp.toDouble(),
                        wPrimeKj = effectiveWPrimeKj
                    )
                    wPrimeSummary.minWPrimeBalKj?.let {
                        workoutRepo.updateExtremaValue(workoutId, SensorType.W_PRIME_BAL, ExtremaType.MIN, it, null)
                    }
                    wPrimeSummary.avgWPrimeBalKj?.let {
                        workoutRepo.updateExtremaValue(workoutId, SensorType.W_PRIME_BAL, ExtremaType.AVG, it, null)
                    }
                    wPrimeSummary.endWPrimeBalKj?.let {
                        workoutRepo.updateExtremaValue(workoutId, SensorType.W_PRIME_BAL, ExtremaType.END, it, null)
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
                    workoutRepo.updateExtremaValue(workoutId, targetSensor, ExtremaType.EFFICIENCY_FACTOR, it, null)
                }
                aerobicResult.decouplingPct?.let {
                    workoutRepo.updateExtremaValue(workoutId, targetSensor, ExtremaType.AEROBIC_DECOUPLING, it, null)
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
                    workoutRepo.updateExtremaValue(workoutId, SensorType.NORMALIZED_POWER, ExtremaType.AVG, np, null)
                    workoutRepo.updateExtremaValue(workoutId, SensorType.NORMALIZED_POWER, ExtremaType.END, np, null)
                }
                powerMetrics.intensityFactor?.let { ifVal ->
                    workoutRepo.updateExtremaValue(workoutId, SensorType.INTENSITY_FACTOR, ExtremaType.AVG, ifVal, null)
                    workoutRepo.updateExtremaValue(workoutId, SensorType.INTENSITY_FACTOR, ExtremaType.END, ifVal, null)
                }
                powerMetrics.tss?.let { tss ->
                    workoutRepo.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.AVG, tss, null)
                    workoutRepo.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.MAX, tss, null)
                    workoutRepo.updateExtremaValue(workoutId, SensorType.TSS, ExtremaType.END, tss, null)
                }
                powerMetrics.workKj?.let { workKj ->
                    workoutRepo.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.AVG, workKj, null)
                    workoutRepo.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.MAX, workKj, null)
                    workoutRepo.updateExtremaValue(workoutId, SensorType.WORK_KJ, ExtremaType.END, workKj, null)
                }
                powerMetrics.avgPowerToWeight?.let { avgWPerKg ->
                    workoutRepo.updateExtremaValue(workoutId, SensorType.POWER_TO_WEIGHT, ExtremaType.AVG, avgWPerKg, null)
                }
                powerMetrics.maxPowerToWeight?.let { maxWPerKg ->
                    workoutRepo.updateExtremaValue(workoutId, SensorType.POWER_TO_WEIGHT, ExtremaType.MAX, maxWPerKg, null)
                }

                // REQ-REV-009.2 (ATT-3904): 1st-order dynamical Power-HR identification
                val dynamics = com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator.calculate(
                    powerSamples = powerSamples,
                    hrSamples = hrSamples,
                    workoutId = workoutId
                )
                if (dynamics != null) {
                    workoutRepo.saveCardioPowerDynamics(workoutId, dynamics)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not compute W'bal, Aerobic Efficiency, or Power Training extrema during recalculateStats: ${e.message}")
        }

        // 5. Clustering (Spatial Markers)
        if (points.isNotEmpty()) {
            val start = points.first()
            val end = points.last()
            val clusterEngine = WorkoutClusterEngine.getInstance(context)
            val apex = clusterEngine.findApexFromPoints(start, points, end)
            val maxDisp = clusterEngine.distanceBetween(start, apex).toDouble()

            workoutRepo.updateSpatialExtrema(workoutId, start, end, apex, maxDisp)

            // ATT-314: Skip clustering if a cluster is already assigned
            val existingClusterId = workoutRepo.getWorkoutClusterId(workoutId)
            if (existingClusterId != -1L) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Workout $workoutId already has cluster $existingClusterId assigned. Skipping clustering logic.")
            } else {
                // ATT-909 / REQ-MIG-027: Candidate sport inference and workout name matching
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
                    maxAltPos = maxAltPos,
                    workoutPolyline = points
                )
                
                if (matchingCluster != null) {
                    var sportId = workoutRepo.getWorkoutSportId(workoutId)
                    var effectiveBSport = bSportType
                    if (effectiveBSport == BSportType.UNKNOWN && matchingCluster.bSportType != BSportType.UNKNOWN) {
                        effectiveBSport = matchingCluster.bSportType
                    }
                    if (sportId == -1L && effectiveBSport != BSportType.UNKNOWN) {
                        sportId = SportTypesRepository.getSportTypeId(effectiveBSport)
                        workoutRepo.updateWorkoutSport(workoutId, sportId, effectiveBSport)
                    }
                    
                    // ATT-316 Refinement: Only lock during the actual DB write/learning phase
                    recalculationMutex.withLock {
                        // Refine existing cluster (ATT-308: ensure sport type is propagated/stored)
                        clusterEngine.learnFromWorkout(start, end, apex, totalDistance, matchingCluster.name, sportId, matchingCluster.id, minAltPos = minAltPos, maxAltPos = maxAltPos)
                        clusterEngine.assignClusterToWorkout(context, workoutId, matchingCluster.id, false)
                    }
                } else {
                    val askOnNewCluster = try {
                        TrainingApplication.askOnNewClusterDuringImport()
                    } catch (_: Exception) {
                        false
                    }
                    if (!askOnNewCluster) {
                        recalculationMutex.withLock {
                            var sportId = workoutRepo.getWorkoutSportId(workoutId)
                            if (sportId == -1L && bSportType != BSportType.UNKNOWN) {
                                sportId = SportTypesRepository.getSportTypeId(bSportType)
                                workoutRepo.updateWorkoutSport(workoutId, sportId, bSportType)
                            }
                            clusterEngine.createAutoClusterFromImport(
                                context = context,
                                workoutId = workoutId,
                                start = start,
                                end = end,
                                apex = apex,
                                distance = totalDistance,
                                sportId = sportId,
                                workoutName = workoutName,
                                baseFileName = baseFileName,
                                firstTime = firstTime,
                                minLat = minLat,
                                minLng = minLng,
                                maxLat = maxLat,
                                maxLng = maxLng,
                                minAltPos = minAltPos,
                                maxAltPos = maxAltPos
                            )
                        }
                    } else {
                        val startTime = workoutRepo.getWorkoutStartTime(workoutId)
                        val decision = listener?.onNewClusterCandidate(
                            date = startTime ?: baseFileName,
                            start = start, end = end, apex = apex,
                            distance = totalDistance,
                            bSportType = bSportType,
                            polyline = polyline,
                            workoutName = workoutName,
                            candidateSportTypes = candidateSports,
                            minAltPos = minAltPos,
                            maxAltPos = maxAltPos
                        ) ?: ClusterDecision()

                        val existingId = decision.clusterId
                        val customName = decision.newClusterName
                        val customWorkoutName = decision.customWorkoutName

                        recalculationMutex.withLock {
                            if (existingId != null) {
                                val cluster = WorkoutClusterRepository.getInstance(context).getClusterById(existingId)
                                var sportId = workoutRepo.getWorkoutSportId(workoutId)
                                if (sportId == -1L && bSportType != BSportType.UNKNOWN) {
                                    sportId = SportTypesRepository.getSportTypeId(bSportType)
                                }
                                if (cluster != null) {
                                    clusterEngine.learnFromWorkout(start, end, apex, totalDistance, cluster.name, sportId, existingId, minAltPos = minAltPos, maxAltPos = maxAltPos)
                                }
                                clusterEngine.assignClusterToWorkout(context, workoutId, existingId, true, customWorkoutName)
                            } else if (!customName.isNullOrBlank()) {
                                var sportId = workoutRepo.getWorkoutSportId(workoutId)
                                if (sportId == -1L && bSportType != BSportType.UNKNOWN) {
                                    sportId = SportTypesRepository.getSportTypeId(bSportType)
                                }
                                val newId = clusterEngine.learnFromWorkout(start, end, apex, totalDistance, customName, sportId, -1L, minAltPos = minAltPos, maxAltPos = maxAltPos)
                                clusterEngine.assignClusterToWorkout(context, workoutId, newId, true, customWorkoutName)
                            } else if (!customWorkoutName.isNullOrBlank()) {
                                workoutRepo.updateWorkoutName(workoutId, customWorkoutName)
                            }
                        }
                    }
                }
            }
        }

        // 6. Direct Notification Pipeline & Legacy Broadcast (ATT-909 / REQ-MIG-026, REQ-STB-021.3)
        // Direct invocation ensures WorkoutRepository and PeriodsRepository process the workout
        // even if WorkoutRepository was not previously instantiated in memory (unbuffered broadcast issue).
        // Inside BulkImportBatchContext, per-workout reloads and cluster rebuilds are deferred to
        // post-batch reconciliation; climbs are still persisted without per-workout LiveClimbsRepository refresh.
        val batchContext = kotlin.coroutines.coroutineContext[BulkImportBatchContext]
        var handledDirectly = false
        try {
            val app = (context.applicationContext as? Application) ?: (context as? Application)
            if (app != null) {
                handledDirectly = true
                val workoutRepo = WorkoutRepository.getInstance(app)
                if (batchContext != null) {
                    val persistedClimbIds = workoutRepo.extractAndPersistClimbsWithoutRefresh(workoutId)
                    if (persistedClimbIds.isNotEmpty()) {
                        batchContext.markClimbsPersisted()
                    }
                } else {
                    workoutRepo.reloadWorkoutData(workoutId)
                    WorkoutClusterRepository.getInstance(app).refreshClusters()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct repository notification skipped or failed: ${e.message}")
        }

        if (!handledDirectly) {
            try {
                val intent = android.content.Intent(com.atrainingtracker.trainingtracker.tracker.TrackerService.WORKOUT_UPDATED_INTENT)
                intent.putExtra(com.atrainingtracker.trainingtracker.tracker.TrackerService.WORKOUT_ID, workoutId)
                androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Legacy broadcast delivery skipped or failed: ${e.message}")
            }
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
                val exportStatusRepo = ExportStatusRepository.getInstance(context)
                val existingRows = exportStatusRepo.getExportRows(baseFileName)
                Log.i(TAG, "Existing export status rows for $baseFileName: count=${existingRows.size}")
                if (existingRows.isEmpty()) {
                    exportManager.newWorkout(baseFileName)
                    Log.i(TAG, "Initialized new export status rows for $baseFileName")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not initialize export status rows for $baseFileName", e)
            }

            for (format in ExportType.COMMUNITY.exportToFileFormats) {
                if (format == FileFormat.HEALTH_CONNECT) {
                    continue
                }
                val uploadEnabled = TrainingApplication.uploadToCommunity(format)
                Log.i(TAG, "Checking community format ${format.name}: uploadToCommunity=$uploadEnabled")
                if (uploadEnabled) {
                    if (format == FileFormat.STRAVA) {
                        val uploadToStrava = try {
                            WorkoutRepository.getInstance(context).getUploadToStravaStatus(workoutId)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not query uploadToStrava for workout $workoutId", e)
                            -1
                        }
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
}
