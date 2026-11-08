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

import android.content.Context
import android.net.Uri
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutPhotoManager
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.migration.parsers.FitWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.GpxWorkoutParser
import com.atrainingtracker.trainingtracker.migration.parsers.ParsedWorkoutData
import com.atrainingtracker.trainingtracker.migration.parsers.TcxWorkoutParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Result metrics for a Strava bulk export archive import operation.
 */
data class StravaImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val totalScanned: Int,
    val lastImportedWorkoutId: Long? = null
)

/**
 * Dedicated engine for streaming Strava bulk export ZIP archives (REQ-MIG-048).
 *
 * Decompresses stream files (.fit.gz, .tcx.gz, .gpx.gz) on-the-fly, enriches workouts with
 * bilingual metadata from activities.csv, synchronizes user equipment from bikes.csv and shoes.csv,
 * prevents duplicate re-uploads to Strava, and enforces Zip-Slip path sanitization.
 */
object StravaBulkExportImporter {
    private const val TAG = "StravaBulkImporter"

    /**
     * Imports workouts from a Strava export archive given a content or file [Uri].
     */
    suspend fun importArchive(
        context: Context,
        archiveUri: Uri,
        listener: LegacyImportEngine.ProgressListener? = null
    ): StravaImportResult = withContext(Dispatchers.IO) {
        if (archiveUri.scheme == "file" && archiveUri.path != null) {
            val file = File(archiveUri.path!!)
            if (file.exists() && file.canRead()) {
                return@withContext importArchive(context, file, listener)
            }
        }

        // Staging the single compressed ZIP to cacheDir for random ZipFile access
        val tempZip = File(context.cacheDir, "strava_import_${System.currentTimeMillis()}.zip")
        try {
            context.contentResolver.openInputStream(archiveUri)?.use { input ->
                FileOutputStream(tempZip).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext StravaImportResult(0, 0, 1, 0)

            return@withContext importArchive(context, tempZip, listener)
        } finally {
            if (tempZip.exists()) {
                tempZip.delete()
            }
        }
    }

    /**
     * Imports workouts from a local Strava export archive [File].
     */
    suspend fun importArchive(
        context: Context,
        archiveFile: File,
        listener: LegacyImportEngine.ProgressListener? = null
    ): StravaImportResult = withContext(Dispatchers.IO) {
        var importedCount = 0
        var skippedCount = 0
        var failedCount = 0
        var lastWorkoutId: Long? = null

        val zipFile = try {
            ZipFile(archiveFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ZIP archive: ${archiveFile.absolutePath}", e)
            return@withContext StravaImportResult(0, 0, 1, 0)
        }

        zipFile.use { zip ->
            // 1. Sanitize entries and pre-load gear and captions
            syncEquipmentFromArchive(context, zip)

            val mediaCaptions = findZipEntryIgnoreCase(zip, "media.csv")?.let { entry ->
                try {
                    validateZipEntry(entry.name)
                    zip.getInputStream(entry).use { stream ->
                        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                            StravaCsvParser.parseMediaCaptions(reader)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not parse media.csv", e)
                    emptyMap()
                }
            } ?: emptyMap()

            // 2. Parse activities.csv
            val activitiesEntry = findZipEntryIgnoreCase(zip, "activities.csv")
            if (activitiesEntry == null) {
                Log.e(TAG, "Archive does not contain activities.csv")
                return@withContext StravaImportResult(0, 0, 1, 0)
            }

            validateZipEntry(activitiesEntry.name)
            val rawActivities = zip.getInputStream(activitiesEntry).use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    StravaCsvParser.parseActivities(reader)
                }
            }

            // Unify bulk import processing order to chronological ascending (oldest first, REQ-MIG-049.1, ATT-6039)
            val activities = if (rawActivities.any { it.activityId.toLongOrNull() != null }) {
                rawActivities.sortedBy { it.activityId.toLongOrNull() ?: Long.MAX_VALUE }
            } else {
                rawActivities.reversed()
            }

            val totalScanned = activities.size
            Log.i(TAG, "Parsed ${activities.size} activity rows from activities.csv")

            val equipmentDb = EquipmentDbHelper.getInstance(context)
            val existingSnapshot = ExistingWorkoutsSnapshot.create(context)

            // 3. Process each activity
            activities.forEachIndexed { index, row ->
                val progressName = row.activityName ?: row.filename ?: row.activityId
                listener?.onProgress(index + 1, totalScanned, progressName)

                val filename = row.filename
                if (filename.isNullOrBlank()) {
                    // Manual activity without recording stream
                    skippedCount++
                    return@forEachIndexed
                }

                val baseFileName = resolveBaseFileName(filename, row.activityId)
                if (existingSnapshot.isDuplicate(baseFileName, row.activityId)) {
                    if (com.atrainingtracker.trainingtracker.TrainingApplication.getDebug(true)) {
                        Log.d(TAG, "Fast-forwarding duplicate Strava activity: $baseFileName (id: ${row.activityId})")
                    }
                    skippedCount++
                    return@forEachIndexed
                }

                validateZipEntry(filename)
                val streamEntry = findZipEntryIgnoreCase(zip, filename)
                if (streamEntry == null) {
                    Log.w(TAG, "Stream entry not found in archive: $filename")
                    failedCount++
                    return@forEachIndexed
                }

                try {
                    val rawStream = zip.getInputStream(streamEntry)
                    val decompressedStream = if (filename.endsWith(".gz", ignoreCase = true)) {
                        GZIPInputStream(rawStream)
                    } else {
                        rawStream
                    }

                    val source: WorkoutSource
                    val parseResult: Result<ParsedWorkoutData>

                    decompressedStream.use { stream ->
                        val lower = filename.lowercase(Locale.ROOT)
                        when {
                            lower.contains(".tcx") -> {
                                source = WorkoutSource.TCX
                                parseResult = TcxWorkoutParser.parse(stream, context)
                            }
                            lower.contains(".gpx") -> {
                                source = WorkoutSource.GPX
                                parseResult = GpxWorkoutParser.parse(stream, context)
                            }
                            lower.contains(".fit") -> {
                                source = WorkoutSource.FIT
                                parseResult = FitWorkoutParser.parse(stream, context)
                            }
                            else -> {
                                Log.w(TAG, "Unsupported activity file format: $filename")
                                failedCount++
                                return@forEachIndexed
                            }
                        }
                    }

                    if (parseResult.isFailure) {
                        Log.e(TAG, "Failed to parse activity stream for $filename: ${parseResult.exceptionOrNull()?.message}")
                        failedCount++
                        return@forEachIndexed
                    }

                    val parsed = parseResult.getOrThrow()

                    // Enrich parsed workout with CSV metadata
                    val enrichedSportType = if (parsed.bSportType != BSportType.UNKNOWN) {
                        parsed.bSportType
                    } else if (row.bSportType != BSportType.UNKNOWN) {
                        row.bSportType
                    } else {
                        BSportType.UNKNOWN
                    }

                    val resolvedSportId = if (parsed.resolvedSportId > 0) {
                        parsed.resolvedSportId
                    } else if (enrichedSportType != BSportType.UNKNOWN) {
                        SportTypeDatabaseManager.getSportTypeId(enrichedSportType)
                    } else {
                        -1L
                    }

                    val resolvedEquipmentId = resolveEquipmentId(equipmentDb, row.gearName, enrichedSportType)

                    val enrichedWorkout = parsed.copy(
                        workoutName = if (!row.activityName.isNullOrBlank()) row.activityName.trim() else parsed.workoutName,
                        workoutNotes = if (!row.activityDescription.isNullOrBlank()) row.activityDescription.trim() else parsed.workoutNotes,
                        goal = if (!row.privateNote.isNullOrBlank()) row.privateNote.trim() else null,
                        bSportType = enrichedSportType,
                        resolvedSportId = resolvedSportId,
                        equipmentId = resolvedEquipmentId,
                        isCommute = row.commute,
                        stravaActivityId = row.activityId
                    )

                    val importResult = LegacyImportEngine.persistParsedWorkout(
                        context = context,
                        baseFileName = baseFileName,
                        parsedWorkout = enrichedWorkout,
                        source = source,
                        uploadToStrava = false, // Never re-upload existing Strava activities
                        listener = listener
                    )

                    when (importResult.status) {
                        LegacyImportEngine.ImportStatus.SUCCESS -> {
                            importedCount++
                            existingSnapshot.register(baseFileName, row.activityId)
                            if (importResult.workoutId != null) {
                                lastWorkoutId = importResult.workoutId
                                if (row.media.isNotEmpty()) {
                                    importActivityPhotos(
                                        context = context,
                                        zip = zip,
                                        workoutId = importResult.workoutId,
                                        mediaPaths = row.media,
                                        mediaCaptions = mediaCaptions,
                                        parsedWorkout = enrichedWorkout
                                    )
                                }
                            }
                        }
                        LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED -> {
                            skippedCount++
                            existingSnapshot.register(baseFileName, row.activityId)
                        }
                        LegacyImportEngine.ImportStatus.FAILED -> {
                            failedCount++
                        }
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.e(TAG, "Error importing activity $filename", t)
                    failedCount++
                }
            }

            return@withContext StravaImportResult(
                importedCount = importedCount,
                skippedCount = skippedCount,
                failedCount = failedCount,
                totalScanned = totalScanned,
                lastImportedWorkoutId = lastWorkoutId
            )
        }
    }

    /**
     * Synchronizes gear from bikes.csv and shoes.csv in the archive into [EquipmentDbHelper].
     */
    private fun syncEquipmentFromArchive(context: Context, zip: ZipFile) {
        val equipmentDb = EquipmentDbHelper.getInstance(context)

        // Bikes
        findZipEntryIgnoreCase(zip, "bikes.csv")?.let { entry ->
            try {
                validateZipEntry(entry.name)
                val bikes = zip.getInputStream(entry).use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                        StravaCsvParser.parseBikes(reader)
                    }
                }
                bikes.forEach { bike ->
                    if (equipmentDb.getEquipmentId(bike.name) == -1L) {
                        equipmentDb.addOrUpdateStravaGear(
                            bike.name,
                            bike.name,
                            EquipmentDbHelper.ROAD,
                            BSportType.BIKE.name,
                            false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync bikes.csv", e)
            }
        }

        // Shoes
        findZipEntryIgnoreCase(zip, "shoes.csv")?.let { entry ->
            try {
                validateZipEntry(entry.name)
                val shoes = zip.getInputStream(entry).use { stream ->
                    InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                        StravaCsvParser.parseShoes(reader)
                    }
                }
                shoes.forEach { shoe ->
                    if (equipmentDb.getEquipmentId(shoe.name) == -1L) {
                        equipmentDb.addOrUpdateStravaGear(
                            shoe.name,
                            shoe.name,
                            0,
                            BSportType.RUN.name,
                            false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not sync shoes.csv", e)
            }
        }
    }

    private fun resolveEquipmentId(equipmentDb: EquipmentDbHelper, gearName: String?, sportType: BSportType): Long {
        if (gearName.isNullOrBlank()) return -1L
        val trimmed = gearName.trim()
        val existingId = equipmentDb.getEquipmentId(trimmed)
        if (existingId > 0) return existingId

        // Auto-create gear if not found
        return try {
            equipmentDb.addOrUpdateStravaGear(
                trimmed,
                trimmed,
                if (sportType == BSportType.BIKE) EquipmentDbHelper.ROAD else 0,
                sportType.name,
                false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve equipment for $trimmed", e)
            -1L
        }
    }

    private fun resolveBaseFileName(filename: String, activityId: String): String {
        val entryFile = File(filename)
        var name = entryFile.name
        if (name.endsWith(".gz", ignoreCase = true)) {
            name = name.substring(0, name.length - 3)
        }
        val dotIdx = name.lastIndexOf('.')
        val baseName = if (dotIdx != -1) name.substring(0, dotIdx) else name
        return if (baseName.isNotBlank()) baseName else "strava_$activityId"
    }

    /**
     * Validates that an archive entry does not attempt Zip-Slip directory traversal (REQ-MIG-040).
     */
    fun validateZipEntry(entryName: String) {
        if (entryName.contains("..") || entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw SecurityException("Zip-Slip path traversal detected in archive entry: $entryName")
        }
    }

    private fun findZipEntryIgnoreCase(zip: ZipFile, targetPath: String): ZipEntry? {
        val normalizedTarget = targetPath.replace('\\', '/')
        val direct = zip.getEntry(normalizedTarget)
        if (direct != null) return direct

        val targetName = File(normalizedTarget).name
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val entryNormalized = entry.name.replace('\\', '/')
            if (entryNormalized.equals(normalizedTarget, ignoreCase = true) ||
                File(entryNormalized).name.equals(targetName, ignoreCase = true)
            ) {
                return entry
            }
        }
        return null
    }

    private fun importActivityPhotos(
        context: Context,
        zip: ZipFile,
        workoutId: Long,
        mediaPaths: List<String>,
        mediaCaptions: Map<String, String>,
        parsedWorkout: ParsedWorkoutData
    ) {
        val summariesDb = com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.getInstance(context)
        val targetDir = WorkoutPhotoManager.getWorkoutPhotosDir(context, workoutId)
        val startTimeMs: Long = parsedWorkout.firstTime?.let { timeStr ->
            runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(timeStr)?.time
            }.getOrNull() ?: runCatching {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(timeStr)?.time
            }.getOrNull()
        } ?: System.currentTimeMillis()

        mediaPaths.forEach { rawPath ->
            try {
                validateZipEntry(rawPath)
                val entry = findZipEntryIgnoreCase(zip, rawPath) ?: return@forEach
                val entryFile = File(entry.name)
                val targetFile = File(targetDir, entryFile.name)

                zip.getInputStream(entry).use { inStream ->
                    FileOutputStream(targetFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }

                val caption = mediaCaptions[rawPath] ?: mediaCaptions[entryFile.name]
                val exif = WorkoutPhotoManager.extractExifMetadata(targetFile)
                val takenAtMs = exif.takenAtMs ?: startTimeMs
                var latitude = exif.latitude
                var longitude = exif.longitude

                // Fallback trackpoint interpolation when EXIF is stripped
                if (latitude == null || longitude == null) {
                    val baseFileName = summariesDb.getBaseFileName(workoutId)
                    if (baseFileName != null) {
                        try {
                            val samplesDb = com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.getInstance(context).database
                            val interpolated = WorkoutPhotoManager.interpolateCoordinatesAtTimestamp(samplesDb, baseFileName, takenAtMs)
                            if (interpolated != null) {
                                latitude = interpolated.latitude
                                longitude = interpolated.longitude
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to interpolate coordinates for imported photo ${targetFile.name}", e)
                        }
                    }
                    if ((latitude == null || longitude == null) && parsedWorkout.points.isNotEmpty()) {
                        val firstGps = parsedWorkout.points.firstOrNull()
                        if (firstGps != null) {
                            latitude = firstGps.latitude
                            longitude = firstGps.longitude
                        }
                    }
                }

                summariesDb.insertPhoto(workoutId, targetFile.absolutePath, takenAtMs, latitude, longitude, caption)
                Log.d(TAG, "Imported workout photo: ${targetFile.name} (lat=$latitude, lng=$longitude, caption=$caption)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to import photo $rawPath for workout $workoutId", e)
            }
        }
    }
}
