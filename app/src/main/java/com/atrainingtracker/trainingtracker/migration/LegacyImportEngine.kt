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
import android.util.Xml
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
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
import org.xmlpull.v1.XmlPullParser
import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileIdMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.garmin.fit.LapMesg
import com.garmin.fit.LapMesgListener
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
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

        val candidateFolderHierarchies = listOf(
            listOf("aTrainingTracker", "Workouts"),
            listOf("aTrainingTracker", "FIT"),
            listOf("aTrainingTracker", "TCX"),
            listOf("aTrainingTracker", "GPX"),
            listOf("aTrainingTracker")
        )

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
                            val tempFile = File(workerDir, entry.name)
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
            var baseFileName = tcxFile.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)

            // ATT-314: Early exit if workout already exists to prevent redundant processing
            if (!baseFileName.startsWith("legacy_import", ignoreCase = true) && isWorkoutExisting(summaryDb, baseFileName)) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
            }
            
            val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
            
            var firstTime: String? = null
            var lastTime: String? = null
            var sportName: String? = null
            var workoutNotes: String? = null
            var workoutName: String? = null
            val points = mutableListOf<LatLng>()
            val altitudes = mutableListOf<Double>()
            val distances = mutableListOf<Double>()
            val parsedLaps = mutableListOf<ParsedLap>()
            var currentLap: ParsedLap? = null
            
            var minAltVal = Double.MAX_VALUE
            var minAltPos: LatLng? = null
            var maxAltVal = -Double.MAX_VALUE
            var maxAltPos: LatLng? = null

            val foundSensors = mutableSetOf<SensorType>()
            val bufferedSamples = mutableListOf<ContentValues>()

            FileInputStream(tcxFile).use { fis ->
                val parser = Xml.newPullParser()
                parser.setInput(fis, "UTF-8")
                
                var eventType = parser.eventType
                var values = ContentValues()
                var inActivity = false
                var inLap = false
                var inTrackpoint = false
                var inCreator = false
                var inAuthor = false
                var currentLat: Double? = null
                var currentLng: Double? = null
                var currentAlt: Double? = null
                var currentDist: Double? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val rawName = parser.name
                    val name = rawName?.substringAfterLast(':')
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (name) {
                                "Activity" -> {
                                    inActivity = true
                                    sportName = parser.getAttributeValue(null, "Sport")
                                    if (sportName == null) {
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i).equals("Sport", ignoreCase = true)) {
                                                sportName = parser.getAttributeValue(i)
                                                break
                                            }
                                        }
                                    }
                                }
                                "Creator" -> inCreator = true
                                "Author" -> inAuthor = true
                                "Lap" -> {
                                    inLap = true
                                    val rawStartTime = parser.getAttributeValue(null, "StartTime") ?: run {
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i).equals("StartTime", ignoreCase = true)) {
                                                return@run parser.getAttributeValue(i)
                                            }
                                        }
                                        null
                                    }
                                    val formattedStartTime = rawStartTime?.let { raw ->
                                        try {
                                            val date = tcxTimeFormat.parse(raw.substring(0, 19))
                                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date!!)
                                        } catch (_: Exception) { raw }
                                    }
                                    val lap = ParsedLap(
                                        lapNr = parsedLaps.size.toLong(),
                                        startTime = formattedStartTime
                                    )
                                    parsedLaps.add(lap)
                                    currentLap = lap
                                }
                                "TotalTimeSeconds" -> if (!inTrackpoint) {
                                    currentLap?.totalTimeSeconds = parser.nextText().toDoubleOrNull() ?: 0.0
                                }
                                "MaximumSpeed" -> if (!inTrackpoint) {
                                    currentLap?.maxSpeed = parser.nextText().toDoubleOrNull()
                                }
                                "Calories" -> if (!inTrackpoint) {
                                    currentLap?.calories = parser.nextText().toIntOrNull()
                                }
                                "Notes" -> if (!inTrackpoint) {
                                    val text = parser.nextText()
                                    if (!text.isNullOrBlank()) {
                                        if (inLap && currentLap != null) {
                                            val trimmed = text.trim()
                                            val bracketMatch = Regex("""^\[(.*?)\](?:\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL).find(trimmed)
                                            if (bracketMatch != null) {
                                                val extractedName = bracketMatch.groupValues[1].trim()
                                                val extractedDesc = bracketMatch.groupValues.getOrNull(2)?.trim()
                                                if (currentLap.name.isNullOrBlank() && extractedName.isNotEmpty()) {
                                                    currentLap.name = extractedName
                                                }
                                                if (currentLap.description.isNullOrBlank() && !extractedDesc.isNullOrEmpty()) {
                                                    currentLap.description = extractedDesc
                                                }
                                            } else {
                                                val lines = trimmed.lines()
                                                if (lines.size > 1) {
                                                    val firstLine = lines.first().trim()
                                                    if (currentLap.name.isNullOrBlank() && firstLine.length <= 40) {
                                                        currentLap.name = firstLine
                                                        if (currentLap.description.isNullOrBlank()) {
                                                            val remaining = lines.drop(1).joinToString("\n").trim()
                                                            if (remaining.isNotEmpty()) {
                                                                currentLap.description = remaining
                                                            }
                                                        }
                                                    } else if (currentLap.description.isNullOrBlank()) {
                                                        currentLap.description = trimmed
                                                    }
                                                } else {
                                                    if (trimmed.length <= 40 && currentLap.name.isNullOrBlank()) {
                                                        currentLap.name = trimmed
                                                    } else if (currentLap.description.isNullOrBlank()) {
                                                        currentLap.description = trimmed
                                                    }
                                                }
                                            }
                                        } else {
                                            val trimmed = text.trim()
                                            val bracketMatch = Regex("""^\[(.*?)\](?:\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL).find(trimmed)
                                            if (bracketMatch != null) {
                                                val extractedName = bracketMatch.groupValues[1].trim()
                                                val extractedDesc = bracketMatch.groupValues.getOrNull(2)?.trim()
                                                if (workoutName.isNullOrBlank() && extractedName.isNotEmpty()) {
                                                    workoutName = extractedName
                                                }
                                                if (workoutNotes.isNullOrBlank() && !extractedDesc.isNullOrEmpty()) {
                                                    workoutNotes = extractedDesc
                                                }
                                            } else {
                                                val lines = trimmed.lines()
                                                if (lines.size > 1) {
                                                    val firstLine = lines.first().trim()
                                                    if (workoutName.isNullOrBlank() && firstLine.length <= 60) {
                                                        workoutName = firstLine
                                                        if (workoutNotes.isNullOrBlank()) {
                                                            val remaining = lines.drop(1).joinToString("\n").trim()
                                                            if (remaining.isNotEmpty()) {
                                                                workoutNotes = remaining
                                                            }
                                                        }
                                                    } else if (workoutNotes.isNullOrBlank()) {
                                                        workoutNotes = trimmed
                                                    }
                                                } else {
                                                    if (workoutNotes.isNullOrBlank()) {
                                                        workoutNotes = trimmed
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                "Name" -> if (!inTrackpoint) {
                                    val text = parser.nextText()
                                    if (!text.isNullOrBlank()) {
                                        if (inLap && currentLap != null) {
                                            currentLap.name = text.trim()
                                        } else if (inActivity && !inLap && !inCreator && !inAuthor) {
                                            workoutName = text.trim()
                                        }
                                    }
                                }
                                "Description" -> if (!inTrackpoint) {
                                    val text = parser.nextText()
                                    if (!text.isNullOrBlank()) {
                                        if (inLap && currentLap != null) {
                                            currentLap.description = text.trim()
                                        } else if (inActivity && !inLap && !inCreator && !inAuthor) {
                                            workoutNotes = text.trim()
                                        }
                                    }
                                }
                                "Trackpoint" -> {
                                    inTrackpoint = true
                                    val lapNr = (parsedLaps.size - 1).coerceAtLeast(0).toLong()
                                    values = ContentValues().apply {
                                        put(SensorType.LAP_NR.name, lapNr)
                                    }
                                    currentLat = null
                                    currentLng = null
                                    currentAlt = null
                                    currentDist = null
                                }
                                "Time" -> if (inTrackpoint) {
                                    val rawTime = parser.nextText()
                                    val formatted = try { 
                                        val date = tcxTimeFormat.parse(rawTime.substring(0, 19))
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date!!)
                                    } catch (e: Exception) { rawTime }
                                    values.put("time", formatted)
                                    if (firstTime == null) firstTime = formatted
                                    lastTime = formatted
                                }
                                "LatitudeDegrees" -> if (inTrackpoint) {
                                    val lat = parser.nextText().toDoubleOrNull()
                                    if (lat != null) {
                                        values.put(SensorType.LATITUDE.name, lat)
                                        currentLat = lat
                                        foundSensors.add(SensorType.LATITUDE)
                                    }
                                }
                                "LongitudeDegrees" -> if (inTrackpoint) {
                                    val lng = parser.nextText().toDoubleOrNull()
                                    if (lng != null) {
                                        values.put(SensorType.LONGITUDE.name, lng)
                                        currentLng = lng
                                        foundSensors.add(SensorType.LONGITUDE)
                                    }
                                }
                                "AltitudeMeters" -> if (inTrackpoint) {
                                    currentAlt = parser.nextText().toDoubleOrNull()
                                    if (currentAlt != null) {
                                        values.put(SensorType.ALTITUDE.name, currentAlt)
                                        foundSensors.add(SensorType.ALTITUDE)
                                    }
                                }
                                "DistanceMeters" -> if (inTrackpoint) {
                                    currentDist = parser.nextText().toDoubleOrNull()
                                    if (currentDist != null) {
                                        values.put(SensorType.DISTANCE_m.name, currentDist)
                                        foundSensors.add(SensorType.DISTANCE_m)
                                    }
                                } else {
                                    currentLap?.distanceMeters = parser.nextText().toDoubleOrNull() ?: 0.0
                                }
                                "Value" -> {
                                    if (inTrackpoint && parser.getAttributeValue(null, "xsi:type") == null) {
                                        val hr = parser.nextText().toIntOrNull()
                                        if (hr != null) {
                                            values.put(SensorType.HR.name, hr)
                                            foundSensors.add(SensorType.HR)
                                        }
                                    } else if (!inTrackpoint) {
                                        val hr = parser.nextText().toIntOrNull()
                                        if (hr != null && currentLap != null && currentLap.avgHeartRate == null) {
                                            currentLap.avgHeartRate = hr
                                        }
                                    }
                                }
                                "Cadence" -> if (inTrackpoint) {
                                    val cad = parser.nextText().toIntOrNull()
                                    if (cad != null) {
                                        values.put(SensorType.CADENCE.name, cad)
                                        foundSensors.add(SensorType.CADENCE)
                                    }
                                }
                                "Watts" -> if (inTrackpoint) {
                                    val pwr = parser.nextText().toIntOrNull()
                                    if (pwr != null) {
                                        values.put(SensorType.POWER.name, pwr)
                                        foundSensors.add(SensorType.POWER)
                                    }
                                }
                                "Speed" -> if (inTrackpoint) {
                                    val spd = parser.nextText().toDoubleOrNull()
                                    if (spd != null) {
                                        values.put(SensorType.SPEED_mps.name, spd)
                                        foundSensors.add(SensorType.SPEED_mps)
                                    }
                                }
                                "RunCadence" -> if (inTrackpoint) {
                                    val cad = parser.nextText().toIntOrNull()
                                    if (cad != null) {
                                        values.put(SensorType.CADENCE.name, cad)
                                        foundSensors.add(SensorType.CADENCE)
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "Activity") {
                                inActivity = false
                            }
                            if (name == "Creator") {
                                inCreator = false
                            }
                            if (name == "Author") {
                                inAuthor = false
                            }
                            if (name == "Lap") {
                                inLap = false
                                currentLap = null
                            }
                            if (name == "Trackpoint") {
                                if (values.containsKey("time")) {
                                    bufferedSamples.add(values)
                                }

                                if (currentLat != null && currentLng != null) {
                                    val pos = LatLng(currentLat!!, currentLng!!)
                                    points.add(pos)
                                    if (currentAlt != null) {
                                        if (currentAlt!! < minAltVal) {
                                            minAltVal = currentAlt!!
                                            minAltPos = pos
                                        }
                                        if (currentAlt!! > maxAltVal) {
                                            maxAltVal = currentAlt!!
                                            maxAltPos = pos
                                        }
                                    }
                                }
                                currentAlt?.let { altitudes.add(it) }
                                currentDist?.let { distances.add(it) }
                                inTrackpoint = false
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            // Post-parsing: Bulk insertion and dynamic table creation (ATT-357)
            if (firstTime == null && parsedLaps.isNotEmpty() && parsedLaps.first().startTime != null) {
                firstTime = parsedLaps.first().startTime
            }

            if (baseFileName.startsWith("legacy_import", ignoreCase = true) && firstTime != null) {
                baseFileName = firstTime!!.replace(" ", "_").replace(":", "")
            }

            val sportTypeManager = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getInstance(context)
            var bSportType = BSportType.UNKNOWN
            var resolvedSportId = -1L
            if (sportName != null) {
                val sportId = sportTypeManager.getSportTypeIdFromTcxName(sportName!!)
                if (sportId != -1L) {
                    resolvedSportId = sportId
                    bSportType = sportTypeManager.getBSportType(sportId)
                } else {
                    bSportType = when (sportName!!.lowercase()) {
                        "running" -> BSportType.RUN
                        "biking", "cycling" -> BSportType.BIKE
                        "walking" -> BSportType.RUN
                        else -> BSportType.UNKNOWN
                    }
                    if (bSportType != BSportType.UNKNOWN) {
                        resolvedSportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                    }
                }
            }

            var workoutId = -1L
            // ATT-2023 / REQ-MIG-031: Mutex-guarded multi-dimensional deduplication and atomic insertion
            val isDuplicate = importMutex.withLock {
                if (isWorkoutExisting(summaryDb, baseFileName, firstTime, bSportType)) {
                    if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                    return@withLock true
                }

                // Post-parsing: Bulk insertion and dynamic table creation (ATT-357)
                if (bufferedSamples.isNotEmpty()) {
                    // ATT-602: Always create table with all SensorType values (same as TrackerService) so LAP_NR and standard columns exist
                    samplesDbManager.createNewTable(baseFileName, SensorType.values().toList())
                    val targetDb = samplesDbManager.database
                    val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
                    targetDb.beginTransaction()
                    try {
                        bufferedSamples.forEach { sampleValues ->
                            targetDb.insert(tableName, null, sampleValues)
                        }
                        targetDb.setTransactionSuccessful()
                    } finally {
                        targetDb.endTransaction()
                    }
                }

                if (firstTime != null) {
                    workoutId = getWorkoutId(summaryDb, baseFileName)
                    if (workoutId == -1L) {
                        // ATT-1105: Ensure clean slate in StravaUpload.db for fresh workout imports
                        try {
                            StravaUploadDbHelper(context).deleteWorkout(baseFileName)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Could not clean StravaUploadDb for $baseFileName: ${t.message}")
                        }

                        val summaryValues = ContentValues().apply {
                            put(WorkoutSummaries.FILE_BASE_NAME, baseFileName)
                            put(WorkoutSummaries.WORKOUT_NAME, if (!workoutName.isNullOrBlank()) workoutName!!.trim() else baseFileName)
                            put(WorkoutSummaries.TIME_START, firstTime)
                            put(WorkoutSummaries.SPORT_ID, resolvedSportId)
                            put(WorkoutSummaries.B_SPORT, bSportType.name)
                            put(WorkoutSummaries.EQUIPMENT_ID, -1L)
                            put(WorkoutSummaries.FINISHED, 1)
                            put(WorkoutSummaries.SOURCE, WorkoutSource.TCX.name)
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

                // ATT-316: Synchronous post-processing to support backpressure (ATT-349).
                // Refined: We no longer hold the mutex for the entire duration to allow the queue to grow,
                // but we await the recalculation to ensure the engine pauses if the UI queue is full.
                recalculateStats(
                    context = context,
                    workoutId = workoutId,
                    baseFileName = baseFileName,
                    points = points,
                    altitudes = altitudes,
                    distances = distances,
                    bSportType = bSportType,
                    foundSensors = foundSensors,
                    parsedLaps = parsedLaps,
                    workoutNotes = workoutNotes,
                    workoutName = workoutName,
                    firstTime = firstTime,
                    lastTime = lastTime,
                    minAltPos = minAltPos,
                    maxAltPos = maxAltPos,
                    listener = listener
                )

                // ATT-602 (REQ-EXT-008): Automatically schedule background upload to Strava and online communities
                schedulePostImportCommunityUpload(context, workoutId, baseFileName)

                return ImportResult(ImportStatus.SUCCESS, workoutId)
            }
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
            var baseFileName = gpxFile.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)

            // Early exit if workout already exists to prevent redundant processing
            if (!baseFileName.startsWith("legacy_import", ignoreCase = true) && isWorkoutExisting(summaryDb, baseFileName)) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
            }

            val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)

            var firstTime: String? = null
            var lastTime: String? = null
            var sportName: String? = null
            var workoutNotes: String? = null
            var workoutName: String? = null
            val points = mutableListOf<LatLng>()
            val altitudes = mutableListOf<Double>()
            val distances = mutableListOf<Double>()
            val parsedLaps = mutableListOf<ParsedLap>()
            var currentLap: ParsedLap? = null
            var lapFirstTime: String? = null
            var lapLastTime: String? = null
            var lapStartDistance = 0.0

            var minAltVal = Double.MAX_VALUE
            var minAltPos: LatLng? = null
            var maxAltVal = -Double.MAX_VALUE
            var maxAltPos: LatLng? = null

            val foundSensors = mutableSetOf<SensorType>()
            val bufferedSamples = mutableListOf<ContentValues>()

            var prevLat: Double? = null
            var prevLng: Double? = null
            var cumDist = 0.0

            FileInputStream(gpxFile).use { fis ->
                val parser = Xml.newPullParser()
                parser.setInput(fis, "UTF-8")

                var eventType = parser.eventType
                var values = ContentValues()
                var inMetadata = false
                var inTrk = false
                var inTrackpoint = false
                var currentLat: Double? = null
                var currentLng: Double? = null
                var currentAlt: Double? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val rawName = parser.name
                    val name = rawName?.substringAfterLast(':')
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (name) {
                                "metadata" -> inMetadata = true
                                "trk" -> inTrk = true
                                "trkseg" -> {
                                    lapStartDistance = cumDist
                                    lapFirstTime = null
                                    lapLastTime = null
                                    val lap = ParsedLap(
                                        lapNr = parsedLaps.size.toLong()
                                    )
                                    parsedLaps.add(lap)
                                    currentLap = lap
                                }
                                "trkpt", "rtept" -> {
                                    inTrackpoint = true
                                    currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                        ?: parser.getAttributeValue("", "lat")?.toDoubleOrNull()
                                    currentLng = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                        ?: parser.getAttributeValue("", "lon")?.toDoubleOrNull()
                                    if (currentLat == null || currentLng == null) {
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i).equals("lat", ignoreCase = true)) {
                                                currentLat = parser.getAttributeValue(i).toDoubleOrNull()
                                            } else if (parser.getAttributeName(i).equals("lon", ignoreCase = true)) {
                                                currentLng = parser.getAttributeValue(i).toDoubleOrNull()
                                            }
                                        }
                                    }

                                    if (currentLap == null && parsedLaps.isEmpty()) {
                                        val lap = ParsedLap(lapNr = 0L)
                                        parsedLaps.add(lap)
                                        currentLap = lap
                                        lapStartDistance = cumDist
                                    }

                                    val lapNr = (parsedLaps.size - 1).coerceAtLeast(0).toLong()
                                    values = ContentValues().apply {
                                        put(SensorType.LAP_NR.name, lapNr)
                                    }

                                    if (currentLat != null && currentLng != null) {
                                        if (prevLat != null && prevLng != null) {
                                            val results = FloatArray(1)
                                            Location.distanceBetween(prevLat!!, prevLng!!, currentLat!!, currentLng!!, results)
                                            cumDist += results[0].toDouble()
                                        }
                                        prevLat = currentLat
                                        prevLng = currentLng

                                        values.put(SensorType.LATITUDE.name, currentLat)
                                        values.put(SensorType.LONGITUDE.name, currentLng)
                                        values.put(SensorType.DISTANCE_m.name, cumDist)
                                        foundSensors.add(SensorType.LATITUDE)
                                        foundSensors.add(SensorType.LONGITUDE)
                                        foundSensors.add(SensorType.DISTANCE_m)
                                    }
                                    currentAlt = null
                                }
                                "ele" -> if (inTrackpoint) {
                                    currentAlt = parser.nextText().toDoubleOrNull()
                                    if (currentAlt != null) {
                                        values.put(SensorType.ALTITUDE.name, currentAlt)
                                        foundSensors.add(SensorType.ALTITUDE)
                                    }
                                }
                                "time" -> {
                                    val rawTime = parser.nextText()
                                    val formatted = try {
                                        val date = tcxTimeFormat.parse(rawTime.substring(0, 19))
                                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date!!)
                                    } catch (_: Exception) { rawTime }

                                    if (inTrackpoint) {
                                        values.put("time", formatted)
                                        if (firstTime == null) firstTime = formatted
                                        lastTime = formatted
                                        if (lapFirstTime == null) lapFirstTime = formatted
                                        lapLastTime = formatted
                                        if (currentLap?.startTime == null) currentLap?.startTime = formatted
                                    } else if (inMetadata && firstTime == null) {
                                        firstTime = formatted
                                    }
                                }
                                "speed" -> if (inTrackpoint) {
                                    val spd = parser.nextText().toDoubleOrNull()
                                    if (spd != null) {
                                        values.put(SensorType.SPEED_mps.name, spd)
                                        foundSensors.add(SensorType.SPEED_mps)
                                    }
                                }
                                "hr", "heartrate" -> if (inTrackpoint) {
                                    val hr = parser.nextText().toIntOrNull()
                                    if (hr != null) {
                                        values.put(SensorType.HR.name, hr)
                                        foundSensors.add(SensorType.HR)
                                    }
                                }
                                "cad", "cadence" -> if (inTrackpoint) {
                                    val cad = parser.nextText().toIntOrNull()
                                    if (cad != null) {
                                        values.put(SensorType.CADENCE.name, cad)
                                        foundSensors.add(SensorType.CADENCE)
                                    }
                                }
                                "atemp", "temp" -> if (inTrackpoint) {
                                    val temp = parser.nextText().toDoubleOrNull()
                                    if (temp != null) {
                                        values.put(SensorType.TEMPERATURE.name, temp)
                                        foundSensors.add(SensorType.TEMPERATURE)
                                    }
                                }
                                "power", "watts" -> if (inTrackpoint) {
                                    val pwr = parser.nextText().toIntOrNull()
                                    if (pwr != null) {
                                        values.put(SensorType.POWER.name, pwr)
                                        foundSensors.add(SensorType.POWER)
                                    }
                                }
                                "name" -> if (!inTrackpoint) {
                                    val text = parser.nextText()
                                    if (!text.isNullOrBlank() && workoutName.isNullOrBlank()) {
                                        workoutName = text.trim()
                                    }
                                }
                                "desc", "description" -> if (!inTrackpoint) {
                                    val text = parser.nextText()
                                    if (!text.isNullOrBlank() && workoutNotes.isNullOrBlank()) {
                                        workoutNotes = text.trim()
                                    }
                                }
                                "type", "activity", "sport" -> if (!inTrackpoint) {
                                    val text = parser.nextText()
                                    if (!text.isNullOrBlank() && sportName.isNullOrBlank()) {
                                        sportName = text.trim()
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (name) {
                                "metadata" -> inMetadata = false
                                "trk" -> inTrk = false
                                "trkseg" -> {
                                    currentLap?.let { lap ->
                                        lap.distanceMeters = (cumDist - lapStartDistance).coerceAtLeast(0.0)
                                        if (lapFirstTime != null && lapLastTime != null) {
                                            try {
                                                val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                                val t1 = format.parse(lapFirstTime!!)?.time ?: 0L
                                                val t2 = format.parse(lapLastTime!!)?.time ?: 0L
                                                val duration = ((t2 - t1) / 1000.0).coerceAtLeast(0.0)
                                                lap.totalTimeSeconds = duration
                                                if (duration > 0) {
                                                    lap.maxSpeed = lap.distanceMeters / duration
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    currentLap = null
                                }
                                "trkpt", "rtept" -> {
                                    if (values.containsKey("time")) {
                                        bufferedSamples.add(values)
                                    }

                                    if (currentLat != null && currentLng != null) {
                                        val pos = LatLng(currentLat!!, currentLng!!)
                                        points.add(pos)
                                        if (currentAlt != null) {
                                            if (currentAlt!! < minAltVal) {
                                                minAltVal = currentAlt!!
                                                minAltPos = pos
                                            }
                                            if (currentAlt!! > maxAltVal) {
                                                maxAltVal = currentAlt!!
                                                maxAltPos = pos
                                            }
                                        }
                                    }
                                    currentAlt?.let { altitudes.add(it) }
                                    distances.add(cumDist)
                                    inTrackpoint = false
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            // Finalize any unfinished lap
            currentLap?.let { lap ->
                if (lap.distanceMeters <= 0.0 && cumDist > lapStartDistance) {
                    lap.distanceMeters = (cumDist - lapStartDistance).coerceAtLeast(0.0)
                }
                if (lapFirstTime != null && lapLastTime != null && lap.totalTimeSeconds <= 0.0) {
                    try {
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        val t1 = format.parse(lapFirstTime!!)?.time ?: 0L
                        val t2 = format.parse(lapLastTime!!)?.time ?: 0L
                        lap.totalTimeSeconds = ((t2 - t1) / 1000.0).coerceAtLeast(0.0)
                    } catch (_: Exception) {}
                }
            }

            if (parsedLaps.isEmpty() && points.isNotEmpty()) {
                parsedLaps.add(ParsedLap(lapNr = 0L, startTime = firstTime, distanceMeters = cumDist))
            } else if (parsedLaps.size == 1 && parsedLaps[0].distanceMeters <= 0.0 && cumDist > 0.0) {
                parsedLaps[0].distanceMeters = cumDist
            }

            if (firstTime == null && parsedLaps.isNotEmpty() && parsedLaps.first().startTime != null) {
                firstTime = parsedLaps.first().startTime
            }

            if (baseFileName.startsWith("legacy_import", ignoreCase = true) && firstTime != null) {
                baseFileName = firstTime!!.replace(" ", "_").replace(":", "")
            }

            val sportTypeManager = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getInstance(context)
            var bSportType = BSportType.UNKNOWN
            var resolvedSportId = -1L

            if (sportName != null) {
                val sportId = sportTypeManager.getSportTypeIdFromTcxName(sportName!!)
                if (sportId != -1L) {
                    resolvedSportId = sportId
                    bSportType = sportTypeManager.getBSportType(sportId)
                } else {
                    bSportType = when (sportName!!.lowercase()) {
                        "running", "run", "jogging" -> BSportType.RUN
                        "biking", "cycling", "bike", "ride", "mountain biking", "road cycling" -> BSportType.BIKE
                        "walking", "hiking", "hike", "walk" -> BSportType.RUN
                        else -> BSportType.UNKNOWN
                    }
                    if (bSportType != BSportType.UNKNOWN) {
                        resolvedSportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                    }
                }
            }

            var workoutId = -1L
            // ATT-2023 / REQ-MIG-031: Mutex-guarded multi-dimensional deduplication and atomic insertion
            val isDuplicate = importMutex.withLock {
                if (isWorkoutExisting(summaryDb, baseFileName, firstTime, bSportType)) {
                    if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                    return@withLock true
                }

                // Post-parsing: Bulk insertion and dynamic table creation
                if (bufferedSamples.isNotEmpty()) {
                    samplesDbManager.createNewTable(baseFileName, SensorType.values().toList())
                    val targetDb = samplesDbManager.database
                    val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
                    targetDb.beginTransaction()
                    try {
                        bufferedSamples.forEach { sampleValues ->
                            targetDb.insert(tableName, null, sampleValues)
                        }
                        targetDb.setTransactionSuccessful()
                    } finally {
                        targetDb.endTransaction()
                    }
                }

                if (firstTime != null) {
                    workoutId = getWorkoutId(summaryDb, baseFileName)
                    if (workoutId == -1L) {
                        try {
                            StravaUploadDbHelper(context).deleteWorkout(baseFileName)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Could not clean StravaUploadDb for $baseFileName: ${t.message}")
                        }

                        val summaryValues = ContentValues().apply {
                            put(WorkoutSummaries.FILE_BASE_NAME, baseFileName)
                            put(WorkoutSummaries.WORKOUT_NAME, if (!workoutName.isNullOrBlank()) workoutName!!.trim() else baseFileName)
                            put(WorkoutSummaries.TIME_START, firstTime)
                            put(WorkoutSummaries.SPORT_ID, resolvedSportId)
                            put(WorkoutSummaries.B_SPORT, bSportType.name)
                            put(WorkoutSummaries.EQUIPMENT_ID, -1L)
                            put(WorkoutSummaries.FINISHED, 1)
                            put(WorkoutSummaries.SOURCE, WorkoutSource.GPX.name)
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

                // If sport is still unknown, infer from movement speed (ATT-1116)
                if (bSportType == BSportType.UNKNOWN && cumDist > 0) {
                    val activeTimeSec = if (parsedLaps.any { it.totalTimeSeconds > 0 }) {
                        parsedLaps.sumOf { it.totalTimeSeconds }.roundToInt()
                    } else {
                        (points.size.coerceAtLeast(altitudes.size)).coerceAtLeast(distances.size)
                    }
                    val avgSpd = if (activeTimeSec > 0) cumDist / activeTimeSec else 0.0
                    val candidateSports = try {
                        EquipmentAndSportTypeDiscoveryManager.getInstance(context)
                            .getCandidateBSportTypes(BSportType.UNKNOWN, avgSpd)
                    } catch (_: Exception) {
                        emptySet()
                    }
                    if (candidateSports.size == 1) {
                        bSportType = candidateSports.first()
                        val inferredSportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                        if (inferredSportId != -1L) {
                            summaryDb.database.update(WorkoutSummaries.TABLE, ContentValues().apply {
                                put(WorkoutSummaries.SPORT_ID, inferredSportId)
                                put(WorkoutSummaries.B_SPORT, bSportType.name)
                            }, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))
                        }
                    }
                }

                recalculateStats(
                    context = context,
                    workoutId = workoutId,
                    baseFileName = baseFileName,
                    points = points,
                    altitudes = altitudes,
                    distances = distances,
                    bSportType = bSportType,
                    foundSensors = foundSensors,
                    parsedLaps = parsedLaps,
                    workoutNotes = workoutNotes,
                    workoutName = workoutName,
                    firstTime = firstTime,
                    lastTime = lastTime,
                    minAltPos = minAltPos,
                    maxAltPos = maxAltPos,
                    listener = listener
                )

                schedulePostImportCommunityUpload(context, workoutId, baseFileName)

                return ImportResult(ImportStatus.SUCCESS, workoutId)
            }
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
            var baseFileName = fitFile.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)

            // Early exit if workout already exists to prevent redundant processing
            if (!baseFileName.startsWith("legacy_import", ignoreCase = true) && isWorkoutExisting(summaryDb, baseFileName)) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                return ImportResult(ImportStatus.DUPLICATE_SKIPPED)
            }

            // Verify file integrity
            val decode = Decode()
            val isIntegrityValid = try {
                FileInputStream(fitFile).use { fis ->
                    decode.checkFileIntegrity(fis)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Integrity check exception for ${fitFile.name}: ${t.message}")
                false
            }
            if (!isIntegrityValid) {
                Log.e(TAG, "FIT file integrity check failed for ${fitFile.name}")
                return ImportResult(ImportStatus.FAILED)
            }

            val rawFileIds = mutableListOf<FileIdMesg>()
            val rawSessions = mutableListOf<SessionMesg>()
            val rawLaps = mutableListOf<LapMesg>()
            val rawRecords = mutableListOf<RecordMesg>()

            val broadcaster = MesgBroadcaster(decode)
            broadcaster.addListener(FileIdMesgListener { rawFileIds.add(it) })
            broadcaster.addListener(SessionMesgListener { rawSessions.add(it) })
            broadcaster.addListener(LapMesgListener { rawLaps.add(it) })
            broadcaster.addListener(RecordMesgListener { rawRecords.add(it) })

            val readSuccess = try {
                FileInputStream(fitFile).use { fis ->
                    decode.read(fis, broadcaster)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error decoding FIT file ${fitFile.name}: ${t.message}", t)
                false
            }

            if (!readSuccess || rawRecords.isEmpty()) {
                Log.e(TAG, "Decoding failed or no record trackpoints found in ${fitFile.name}")
                return ImportResult(ImportStatus.FAILED)
            }

            val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            var firstTime: String? = null
            var lastTime: String? = null
            val points = mutableListOf<LatLng>()
            val altitudes = mutableListOf<Double>()
            val distances = mutableListOf<Double>()
            val bufferedSamples = mutableListOf<ContentValues>()
            val parsedLaps = mutableListOf<ParsedLap>()

            var minAltVal = Double.MAX_VALUE
            var maxAltVal = -Double.MAX_VALUE
            var minAltPos: LatLng? = null
            var maxAltPos: LatLng? = null

            // Build parsed laps
            rawLaps.forEachIndexed { index, lapMesg ->
                val lapStartTime = lapMesg.startTime?.date?.let { timeFormat.format(it) }
                val lapTotalTime = (lapMesg.totalTimerTime ?: lapMesg.totalElapsedTime ?: 0f).toDouble()
                val lapDist = (lapMesg.totalDistance ?: 0f).toDouble()
                val lapMaxSpeed = (lapMesg.enhancedMaxSpeed ?: lapMesg.maxSpeed)?.toDouble()
                val lapCalories = lapMesg.totalCalories
                val lapAvgHr = lapMesg.avgHeartRate?.toInt()
                val lapMaxHr = lapMesg.maxHeartRate?.toInt()

                parsedLaps.add(
                    ParsedLap(
                        lapNr = index.toLong(),
                        startTime = lapStartTime,
                        totalTimeSeconds = lapTotalTime,
                        distanceMeters = lapDist,
                        maxSpeed = lapMaxSpeed,
                        calories = lapCalories,
                        avgHeartRate = lapAvgHr,
                        maxHeartRate = lapMaxHr
                    )
                )
            }

            // Build samples from raw records
            rawRecords.forEach { record ->
                val date = record.timestamp?.date ?: return@forEach
                val formattedTime = timeFormat.format(date)
                if (firstTime == null) {
                    firstTime = formattedTime
                }
                lastTime = formattedTime

                val values = ContentValues()
                values.put("time", formattedTime)

                val latSemi = record.positionLat
                val lngSemi = record.positionLong
                val altVal = (record.enhancedAltitude ?: record.altitude)?.toDouble()?.takeIf { it in -500.0..10000.0 }

                if (latSemi != null && lngSemi != null && latSemi != 0x7FFFFFFF && lngSemi != 0x7FFFFFFF) {
                    val latDeg = latSemi * (180.0 / 2147483648.0)
                    val lngDeg = lngSemi * (180.0 / 2147483648.0)
                    values.put(SensorType.LATITUDE.name, latDeg)
                    values.put(SensorType.LONGITUDE.name, lngDeg)

                    val pos = LatLng(latDeg, lngDeg)
                    points.add(pos)

                    altVal?.let { alt ->
                        if (alt < minAltVal) {
                            minAltVal = alt
                            minAltPos = pos
                        }
                        if (alt > maxAltVal) {
                            maxAltVal = alt
                            maxAltPos = pos
                        }
                    }
                }

                altVal?.let { alt ->
                    values.put(SensorType.ALTITUDE.name, alt)
                    altitudes.add(alt)
                }

                record.distance?.toDouble()?.let { dist ->
                    values.put(SensorType.DISTANCE_m.name, dist)
                    distances.add(dist)
                }

                val spdVal = (record.enhancedSpeed ?: record.speed)?.toDouble()
                if (spdVal != null && spdVal in 0.0..100.0) {
                    values.put(SensorType.SPEED_mps.name, spdVal)
                }

                record.heartRate?.toInt()?.let { hr ->
                    if (hr in 30..250) {
                        values.put(SensorType.HR.name, hr)
                    }
                }

                record.cadence?.toDouble()?.let { cad ->
                    if (cad >= 0) {
                        values.put(SensorType.CADENCE.name, cad)
                    }
                }

                record.power?.toDouble()?.let { pwr ->
                    if (pwr >= 0) {
                        values.put(SensorType.POWER.name, pwr)
                    }
                }

                record.temperature?.toDouble()?.let { temp ->
                    values.put(SensorType.TEMPERATURE.name, temp)
                }

                // Determine lapNr
                var sampleLapNr = 0L
                if (parsedLaps.size > 1) {
                    val recordEpoch = date.time
                    for (i in parsedLaps.indices.reversed()) {
                        val lapStart = parsedLaps[i].startTime?.let {
                            try { timeFormat.parse(it)?.time } catch (_: Exception) { null }
                        }
                        if (lapStart != null && recordEpoch >= lapStart) {
                            sampleLapNr = i.toLong()
                            break
                        }
                    }
                }
                values.put(SensorType.LAP_NR.name, sampleLapNr)

                bufferedSamples.add(values)
            }

            if (firstTime == null && parsedLaps.isNotEmpty() && parsedLaps.first().startTime != null) {
                firstTime = parsedLaps.first().startTime
            }

            if (baseFileName.startsWith("legacy_import", ignoreCase = true) && firstTime != null) {
                baseFileName = firstTime!!.replace(" ", "_").replace(":", "")
            }

            // Fallback lap if none parsed
            if (parsedLaps.isEmpty()) {
                val totalDist = distances.lastOrNull() ?: 0.0
                parsedLaps.add(ParsedLap(lapNr = 0L, startTime = firstTime, distanceMeters = totalDist))
            }

            // Sport resolution
            val primarySession = rawSessions.firstOrNull()
            var bSportType = FitSportMapper.mapFitSport(primarySession?.sport, primarySession?.subSport)
            var resolvedSportId = if (bSportType != BSportType.UNKNOWN) {
                com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
            } else {
                -1L
            }

            // Infer sport from speed if still unknown (ATT-1116)
            if (bSportType == BSportType.UNKNOWN && distances.isNotEmpty() && bufferedSamples.size > 1) {
                val totalDist = distances.last()
                val durationSec = parsedLaps.sumOf { it.totalTimeSeconds }.takeIf { it > 0 } ?: (bufferedSamples.size.toDouble())
                val avgSpeed = if (durationSec > 0) totalDist / durationSec else 0.0
                if (avgSpeed > 7.0) {
                    bSportType = BSportType.BIKE
                    resolvedSportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE)
                } else if (avgSpeed in 1.5..7.0) {
                    bSportType = BSportType.RUN
                    resolvedSportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(BSportType.RUN)
                }
            }

            var workoutId = -1L

            // Mutex-guarded multi-dimensional deduplication and atomic insertion
            val isDuplicate = importMutex.withLock {
                if (isWorkoutExisting(summaryDb, baseFileName, firstTime, bSportType)) {
                    if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                    return@withLock true
                }

                if (bufferedSamples.isNotEmpty()) {
                    samplesDbManager.createNewTable(baseFileName, SensorType.values().toList())
                    val targetDb = samplesDbManager.database
                    val tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
                    targetDb.beginTransaction()
                    try {
                        bufferedSamples.forEach { sampleValues ->
                            targetDb.insert(tableName, null, sampleValues)
                        }
                        targetDb.setTransactionSuccessful()
                    } finally {
                        targetDb.endTransaction()
                    }
                }

                if (firstTime != null) {
                    workoutId = getWorkoutId(summaryDb, baseFileName)
                    if (workoutId == -1L) {
                        try {
                            StravaUploadDbHelper(context).deleteWorkout(baseFileName)
                        } catch (t: Throwable) {
                            Log.w(TAG, "Could not clean StravaUploadDb for $baseFileName: ${t.message}")
                        }

                        val summaryValues = ContentValues().apply {
                            put(WorkoutSummaries.FILE_BASE_NAME, baseFileName)
                            put(WorkoutSummaries.WORKOUT_NAME, baseFileName)
                            put(WorkoutSummaries.TIME_START, firstTime)
                            put(WorkoutSummaries.SPORT_ID, resolvedSportId)
                            put(WorkoutSummaries.B_SPORT, bSportType.name)
                            put(WorkoutSummaries.EQUIPMENT_ID, -1L)
                            put(WorkoutSummaries.FINISHED, 1)
                            put(WorkoutSummaries.SOURCE, WorkoutSource.FIT.name)
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
                    baseFileName = baseFileName,
                    points = points,
                    altitudes = altitudes,
                    distances = distances,
                    bSportType = bSportType,
                    foundSensors = emptySet(),
                    parsedLaps = parsedLaps,
                    workoutNotes = null,
                    workoutName = baseFileName,
                    firstTime = firstTime,
                    lastTime = lastTime,
                    minAltPos = minAltPos,
                    maxAltPos = maxAltPos,
                    listener = listener
                )

                schedulePostImportCommunityUpload(context, workoutId, baseFileName)

                return ImportResult(ImportStatus.SUCCESS, workoutId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import FIT: ${fitFile.name}", e)
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
        listener: ProgressListener? = null
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
                if (lastFiltered != null) {
                    val delta = filtered - lastFiltered!!
                    if (delta > 0) totalAscent += delta
                    else if (delta < 0) totalDescent += -delta
                }
                lastFiltered = filtered
            }
            values.put(WorkoutSummaries.ASCENDING, totalAscent.toInt())
            values.put(WorkoutSummaries.DESCENDING, totalDescent.toInt())
        }

        // 3. Map & Streams
        val polyline = if (points.isNotEmpty()) PolyUtil.encode(points) else ""
        values.put(WorkoutSummaries.MAP_POLYLINE, polyline)
        if (altitudes.isNotEmpty()) {
            values.put(WorkoutSummaries.ALTITUDE_STREAM, NumericalEncodingUtils.encodeDoubles(altitudes))
        }
        if (distances.isNotEmpty()) {
            values.put(WorkoutSummaries.DISTANCE_STREAM, NumericalEncodingUtils.encodeDoubles(distances))
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
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else -1
            } ?: -1
        } catch (e: Exception) {
            Log.w(TAG, "Could not query uploadToStrava for workout $workoutId", e)
            -1
        }
    }
}
