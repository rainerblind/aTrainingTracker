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

import android.content.ContentValues
import android.content.Context
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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Handles recreation of workouts from legacy export files (TCX).
 */
object LegacyImportEngine {
    private const val TAG = "LegacyImportEngine"
    private val tcxTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    private val recalculationMutex = Mutex()

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
            polyline: String
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
    suspend fun bulkRecoverFromDropbox(context: Context, format: String, listener: ProgressListener? = null): RecoveryResult {
        val credential = TrainingApplication.readDropboxCredential() ?: return RecoveryResult(0, 0, 0, 0)
        val dbxClient = DbxClientV2(DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY), credential)
        
        val possiblePaths = when (format.lowercase()) {
            "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
            else -> return RecoveryResult(0, 0, 0, 0)
        }

        val allEntries = mutableListOf<com.dropbox.core.v2.files.Metadata>()

        for (path in possiblePaths) {
            try {
                listener?.onStatus("Scanning $path...")
                var result = dbxClient.files().listFolderBuilder(path).withRecursive(true).start()
                
                while (true) {
                    allEntries.addAll(result.entries.filter { it.name.lowercase().endsWith(".$format") })
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

        // Deduplicate entries by path or name across scanned paths
        val entries = allEntries.distinctBy { it.pathLower ?: it.name }
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
                            val tempFile = File(tempDir, "${current}_${entry.name}")
                            try {
                                val downloaded = downloadFileWithRetry(dbxClient, entry.pathLower ?: entry.name, tempFile)
                                if (!downloaded) {
                                    failedCount.incrementAndGet()
                                    continue
                                }

                                val success = when (format.lowercase()) {
                                    "tcx" -> importFromTcx(context, tempFile, listener)
                                    else -> false
                                }
                                if (success) {
                                    importedCount.incrementAndGet()
                                } else {
                                    failedCount.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to download/import ${entry.name}", e)
                                failedCount.incrementAndGet()
                            } finally {
                                tempFile.delete()
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
     * Recreates a workout from a TCX file.
     */
    suspend fun importFromTcx(context: Context, tcxFile: File, listener: ProgressListener? = null): Boolean {
        try {
            val baseFileName = tcxFile.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)

            // ATT-314: Early exit if workout already exists to prevent redundant processing
            if (isWorkoutExisting(summaryDb, baseFileName)) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Skipping $baseFileName: Workout already exists.")
                return false
            }
            
            val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
            
            var firstTime: String? = null
            var sportName: String? = null
            val points = mutableListOf<LatLng>()
            val altitudes = mutableListOf<Double>()
            val distances = mutableListOf<Double>()
            
            val foundSensors = mutableSetOf<SensorType>()
            val bufferedSamples = mutableListOf<ContentValues>()

            FileInputStream(tcxFile).use { fis ->
                val parser = Xml.newPullParser()
                parser.setInput(fis, "UTF-8")
                
                var eventType = parser.eventType
                var values = ContentValues()
                var inTrackpoint = false
                var currentLat: Double? = null
                var currentLng: Double? = null
                var currentAlt: Double? = null
                var currentDist: Double? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (name) {
                                "Activity" -> {
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
                                "Trackpoint" -> {
                                    inTrackpoint = true
                                    values = ContentValues()
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
                                }
                                "Value" -> if (inTrackpoint && parser.getAttributeValue(null, "xsi:type") == null) {
                                    val hr = parser.nextText().toIntOrNull()
                                    if (hr != null) {
                                        values.put(SensorType.HR.name, hr)
                                        foundSensors.add(SensorType.HR)
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
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "Trackpoint") {
                                if (values.containsKey("time")) {
                                    bufferedSamples.add(values)
                                }

                                if (currentLat != null && currentLng != null) {
                                    points.add(LatLng(currentLat!!, currentLng!!))
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
            if (bufferedSamples.isNotEmpty()) {
                samplesDbManager.createNewTable(baseFileName, foundSensors.toList())
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
                var workoutId = getWorkoutId(summaryDb, baseFileName)
                if (workoutId == -1L) {
                    val summaryValues = ContentValues().apply {
                        put(WorkoutSummaries.FILE_BASE_NAME, baseFileName)
                        put(WorkoutSummaries.WORKOUT_NAME, baseFileName)
                        put(WorkoutSummaries.TIME_START, firstTime)
                        put(WorkoutSummaries.SPORT_ID, -1L)
                        put(WorkoutSummaries.EQUIPMENT_ID, -1L)
                        put(WorkoutSummaries.FINISHED, 1)
                    }
                    workoutId = summaryDb.database.insert(WorkoutSummaries.TABLE, null, summaryValues)
                }
                
                val sportTypeManager = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getInstance(context)
                var bSportType = BSportType.UNKNOWN
                
                if (sportName != null) {
                    val sportId = sportTypeManager.getSportTypeIdFromTcxName(sportName!!)
                    if (sportId != -1L) {
                        bSportType = sportTypeManager.getBSportType(sportId)
                        val updateValues = ContentValues().apply {
                            put(WorkoutSummaries.SPORT_ID, sportId)
                        }
                        summaryDb.database.update(WorkoutSummaries.TABLE, updateValues, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))
                    } else {
                        bSportType = when (sportName!!.lowercase()) {
                            "running" -> BSportType.RUN
                            "biking", "cycling" -> BSportType.BIKE
                            "walking" -> BSportType.RUN
                            else -> BSportType.UNKNOWN
                        }
                        if (bSportType != BSportType.UNKNOWN) {
                            val fallbackSportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                            summaryDb.database.update(WorkoutSummaries.TABLE, ContentValues().apply {
                                put(WorkoutSummaries.SPORT_ID, fallbackSportId)
                            }, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))
                        }
                    }
                }

                // ATT-316: Synchronous post-processing to support backpressure (ATT-349).
                // Refined: We no longer hold the mutex for the entire duration to allow the queue to grow,
                // but we await the recalculation to ensure the engine pauses if the UI queue is full.
                recalculateStats(context, workoutId, baseFileName, points, altitudes, distances, bSportType, listener)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import TCX: ${tcxFile.name}", e)
        }
        return false
    }

    private fun getWorkoutId(db: WorkoutSummariesDatabaseManager, fileBaseName: String): Long {
        db.database.query(WorkoutSummaries.TABLE, arrayOf(WorkoutSummaries.C_ID), 
            "${WorkoutSummaries.FILE_BASE_NAME} = ?", arrayOf(fileBaseName), null, null, null).use {
            return if (it.moveToFirst()) it.getLong(0) else -1L
        }
    }

    private fun isWorkoutExisting(db: WorkoutSummariesDatabaseManager, fileBaseName: String): Boolean {
        db.database.query(WorkoutSummaries.TABLE, arrayOf(WorkoutSummaries.C_ID), 
            "${WorkoutSummaries.FILE_BASE_NAME} = ?", arrayOf(fileBaseName), null, null, null).use {
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
        listener: ProgressListener? = null
    ) {
        val summariesDb = WorkoutSummariesDatabaseManager.getInstance(context)
        val samplesDb = WorkoutSamplesDatabaseManager.getInstance(context)
        
        // 1. Basic Stats
        var totalDistance = distances.lastOrNull() ?: 0.0
        if (totalDistance == 0.0 && points.size > 1) {
            totalDistance = calculateCumulativeDistance(points)
        }
        
        val activeTime = (points.size.coerceAtLeast(altitudes.size)).coerceAtLeast(distances.size)
        
        val values = ContentValues()
        values.put(WorkoutSummaries.DISTANCE_TOTAL_m, totalDistance)
        values.put(WorkoutSummaries.TIME_ACTIVE_s, activeTime)
        values.put(WorkoutSummaries.TIME_TOTAL_s, activeTime)
        if (activeTime > 0) {
            values.put(WorkoutSummaries.SPEED_AVERAGE_mps, totalDistance / activeTime)
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
                    summariesDb.updateExtremaValue(workoutId, sensor, type, value, null)
                }
            }
        }

        // 5. Clustering (Spatial Markers)
        if (points.isNotEmpty()) {
            // ATT-314: Skip clustering if a cluster is already assigned
            val existingClusterId = summariesDb.getLong(workoutId, WorkoutSummaries.CLUSTER_ID) ?: -1L
            if (existingClusterId != -1L) {
                if (TrainingApplication.getDebug(true)) Log.d(TAG, "Workout $workoutId already has cluster $existingClusterId assigned. Skipping clustering logic.")
                return
            }

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

            val clusterEngine = WorkoutClusterEngine.getInstance(context)
            val matchingCluster = clusterEngine.suggestCluster(start, end, apex, totalDistance, null, bSportType)
            
            if (matchingCluster != null) {
                var sportId = summariesDb.getLong(workoutId, WorkoutSummaries.SPORT_ID) ?: -1L
                if (sportId == -1L && bSportType != BSportType.UNKNOWN) {
                    sportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                }
                
                // ATT-316 Refinement: Only lock during the actual DB write/learning phase
                recalculationMutex.withLock {
                    // Refine existing cluster (ATT-308: ensure sport type is propagated/stored)
                    clusterEngine.learnFromWorkout(start, end, apex, totalDistance, matchingCluster.name, sportId, matchingCluster.id)
                    clusterEngine.assignClusterToWorkout(context, workoutId, matchingCluster.id, false)
                }
            } else {
                val startTime = summariesDb.getString(workoutId, WorkoutSummaries.TIME_START)
                val (existingId, customName) = listener?.onNewClusterCandidate(
                    date = startTime ?: baseFileName,
                    start = start, end = end, apex = apex, 
                    distance = totalDistance, 
                    bSportType = bSportType,
                    polyline = polyline
                ) ?: Pair(null, null)
                
                recalculationMutex.withLock {
                    if (existingId != null) {
                        val cluster = WorkoutClusterDatabaseManager.getInstance(context).getClusterById(existingId)
                        var sportId = summariesDb.getLong(workoutId, WorkoutSummaries.SPORT_ID) ?: -1L
                        if (sportId == -1L && bSportType != BSportType.UNKNOWN) {
                            sportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                        }
                        if (cluster != null) {
                            clusterEngine.learnFromWorkout(start, end, apex, totalDistance, cluster.name, sportId, existingId)
                        }
                        clusterEngine.assignClusterToWorkout(context, workoutId, existingId, true)
                    } else if (!customName.isNullOrBlank()) {
                        var sportId = summariesDb.getLong(workoutId, WorkoutSummaries.SPORT_ID) ?: -1L
                        if (sportId == -1L && bSportType != BSportType.UNKNOWN) {
                            sportId = com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getSportTypeId(bSportType)
                        }
                        val newId = clusterEngine.learnFromWorkout(start, end, apex, totalDistance, customName, sportId, -1L)
                        clusterEngine.assignClusterToWorkout(context, workoutId, newId, true)
                    }
                }
            }
        }

        // 6. Notify System (ATT-346 Hook)
        // This triggers WorkoutRepository to reload memory and notify PeriodsRepository
        val intent = android.content.Intent(com.atrainingtracker.trainingtracker.tracker.TrackerService.WORKOUT_UPDATED_INTENT)
        intent.putExtra(com.atrainingtracker.trainingtracker.tracker.TrackerService.WORKOUT_ID, workoutId)
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
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
}
