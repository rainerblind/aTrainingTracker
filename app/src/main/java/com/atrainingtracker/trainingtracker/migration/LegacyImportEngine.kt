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
import au.com.bytecode.opencsv.CSVReader
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Handles recreation of workouts from legacy export files (CSV, TCX).
 */
object LegacyImportEngine {
    private const val TAG = "LegacyImportEngine"
    private val tcxTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

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

    /**
     * Scans Dropbox and recovers all legacy workouts.
     */
    suspend fun bulkRecoverFromDropbox(context: Context, format: String, listener: ProgressListener? = null): Int {
        val credential = TrainingApplication.readDropboxCredential() ?: return 0
        val dbxClient = DbxClientV2(DbxRequestConfig(BuildConfig.DROPBOX_APP_KEY), credential)
        
        val possiblePaths = when (format.lowercase()) {
            "csv" -> listOf("/CSV", "/apps/Workouts/CSV")
            "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
            else -> return 0
        }

        var importedCount = 0
        try {
            var entries: List<com.dropbox.core.v2.files.Metadata> = emptyList()
            var foundPath: String? = null

            for (path in possiblePaths) {
                try {
                    listener?.onStatus("Scanning $path...")
                    val result = dbxClient.files().listFolder(path)
                    entries = result.entries.filter { it.name.lowercase().endsWith(".$format") }
                    if (entries.isNotEmpty()) {
                        foundPath = path
                        break
                    }
                } catch (e: Exception) {
                    // Path might not exist, try next
                }
            }

            if (foundPath == null) return 0
            
            val tempDir = File(context.cacheDir, "legacy_recovery")
            if (tempDir.exists()) tempDir.deleteRecursively()
            tempDir.mkdirs()

            entries.forEachIndexed { index, entry ->
                listener?.onProgress(index + 1, entries.size, entry.name)
                
                val tempFile = File(tempDir, entry.name)
                FileOutputStream(tempFile).use { fos ->
                    dbxClient.files().download(entry.pathLower).download(fos)
                }

                val success = when (format.lowercase()) {
                    "csv" -> importFromCsv(context, tempFile, listener)
                    "tcx" -> importFromTcx(context, tempFile, listener)
                    else -> false
                }
                if (success) importedCount++
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bulk recovery failed", e)
        }
        return importedCount
    }

    /**
     * Recreates a workout from a TCX file.
     */
    suspend fun importFromTcx(context: Context, tcxFile: File, listener: ProgressListener? = null): Boolean {
        try {
            val baseFileName = tcxFile.nameWithoutExtension.removeSuffix("-TMP").removeSuffix("~")
            val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)
            
            val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
            val targetDb = samplesDbManager.database

            var firstTime: String? = null
            var sportName: String? = null
            val points = mutableListOf<LatLng>()
            val altitudes = mutableListOf<Double>()
            val distances = mutableListOf<Double>()

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
                                    // Robust attribute extraction (ATT-306)
                                    sportName = parser.getAttributeValue(null, "Sport")
                                    if (sportName == null) {
                                        for (i in 0 until parser.attributeCount) {
                                            if (parser.getAttributeName(i).equals("Sport", ignoreCase = true)) {
                                                sportName = parser.getAttributeValue(i)
                                                break
                                            }
                                        }
                                    }
                                    Log.i(TAG, "TCX Import: Found Activity with Sport=$sportName")
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
                                    values.put(SensorType.LATITUDE.name, lat)
                                    currentLat = lat
                                }
                                "LongitudeDegrees" -> if (inTrackpoint) {
                                    val lng = parser.nextText().toDoubleOrNull()
                                    values.put(SensorType.LONGITUDE.name, lng)
                                    currentLng = lng
                                }
                                "AltitudeMeters" -> if (inTrackpoint) {
                                    currentAlt = parser.nextText().toDoubleOrNull()
                                    values.put(SensorType.ALTITUDE.name, currentAlt)
                                }
                                "DistanceMeters" -> if (inTrackpoint) {
                                    currentDist = parser.nextText().toDoubleOrNull()
                                    values.put(SensorType.DISTANCE_m.name, currentDist)
                                }
                                "Value" -> if (inTrackpoint && parser.getAttributeValue(null, "xsi:type") == null) {
                                    values.put(SensorType.HR.name, parser.nextText())
                                }
                                "Cadence" -> if (inTrackpoint) values.put(SensorType.CADENCE.name, parser.nextText())
                                "Watts" -> if (inTrackpoint) values.put(SensorType.POWER.name, parser.nextText())
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "Trackpoint") {
                                if (values.containsKey("time")) {
                                    if (!isWorkoutExisting(summaryDb, baseFileName)) {
                                        if (points.isEmpty() && altitudes.isEmpty()) {
                                            val sensorTypes = mutableListOf(SensorType.LATITUDE, SensorType.LONGITUDE, SensorType.ALTITUDE, 
                                                SensorType.DISTANCE_m, SensorType.HR, SensorType.CADENCE, SensorType.POWER)
                                            samplesDbManager.createNewTable(baseFileName, sensorTypes)
                                            targetDb.beginTransaction()
                                        }
                                        targetDb.insert(WorkoutSamplesDatabaseManager.getTableName(baseFileName), null, values)
                                    }
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
                if (targetDb.inTransaction()) {
                    targetDb.setTransactionSuccessful()
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
                        // Basic fallback for unknown TCX sport strings
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

    /**
     * Recreates a workout from a CSV file.
     */
    suspend fun importFromCsv(context: Context, csvFile: File, listener: ProgressListener? = null): Boolean {
        try {
            CSVReader(FileReader(csvFile)).use { reader ->
                val header = reader.readNext() ?: return false
                val columnMap = mapHeaderToSensorTypes(header)
                
                val baseFileName = csvFile.nameWithoutExtension.removeSuffix("-TMP")
                val summaryDb = WorkoutSummariesDatabaseManager.getInstance(context)
                
                val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
                val targetDb = samplesDbManager.database
                
                var firstTime: String? = null
                val points = mutableListOf<LatLng>()
                val altitudes = mutableListOf<Double>()
                val distances = mutableListOf<Double>()

                var nextLine: Array<String>? = reader.readNext()
                while (nextLine != null) {
                    val values = ContentValues()
                    var lat: Double? = null
                    var lng: Double? = null
                    var alt: Double? = null
                    var dist: Double? = null
                    
                    columnMap.forEach { (index, sensorType) ->
                        val value = nextLine?.getOrNull(index) ?: ""
                        if (value.isNotEmpty()) {
                            if (sensorType == null && header[index].replace("\"", "") == "time") {
                                val formattedTime = normalizeTimestamp(value)
                                values.put("time", formattedTime)
                                if (firstTime == null) firstTime = formattedTime
                            } else if (sensorType != null) {
                                values.put(sensorType.name, value)
                                if (sensorType == SensorType.LATITUDE) lat = value.toDoubleOrNull()
                                if (sensorType == SensorType.LONGITUDE) lng = value.toDoubleOrNull()
                                if (sensorType == SensorType.ALTITUDE) alt = value.toDoubleOrNull()
                                if (sensorType == SensorType.DISTANCE_m) dist = value.toDoubleOrNull()
                            }
                        }
                    }

                    if (values.containsKey("time") && !isWorkoutExisting(summaryDb, baseFileName)) {
                        if (points.isEmpty() && altitudes.isEmpty()) {
                            val sensorTypes = columnMap.values.filterNotNull().distinct().toMutableList()
                            samplesDbManager.createNewTable(baseFileName, sensorTypes)
                            targetDb.beginTransaction()
                        }
                        targetDb.insert(WorkoutSamplesDatabaseManager.getTableName(baseFileName), null, values)
                    }

                    if (lat != null && lng != null) points.add(LatLng(lat!!, lng!!))
                    alt?.let { altitudes.add(it) }
                    dist?.let { distances.add(it) }
                    
                    nextLine = reader.readNext()
                }

                if (targetDb.inTransaction()) {
                    targetDb.setTransactionSuccessful()
                    targetDb.endTransaction()
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

                    recalculateStats(context, workoutId, baseFileName, points, altitudes, distances, BSportType.UNKNOWN, listener)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import CSV: ${csvFile.name}", e)
        }
        return false
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
        val polyline = PolyUtil.encode(points)
        values.put(WorkoutSummaries.MAP_POLYLINE, polyline)
        values.put(WorkoutSummaries.ALTITUDE_STREAM, NumericalEncodingUtils.encodeDoubles(altitudes))
        values.put(WorkoutSummaries.DISTANCE_STREAM, NumericalEncodingUtils.encodeDoubles(distances))
        // Use the static field safely
        values.put("extremumValuesCalculated", 1)
        
        summariesDb.database.update(WorkoutSummaries.TABLE, values, "${WorkoutSummaries.C_ID} = ?", arrayOf(workoutId.toString()))

        // 4. Persistence of Extrema (ATT-299, ATT-301)
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
            val matchingCluster = clusterEngine.suggestCluster(start, end, apex, totalDistance, null)
            
            if (matchingCluster != null) {
                clusterEngine.assignClusterToWorkout(context, workoutId, matchingCluster.id, false)
            } else {
                val startTime = summariesDb.getString(workoutId, WorkoutSummaries.TIME_START)
                val (existingId, customName) = listener?.onNewClusterCandidate(
                    date = startTime ?: baseFileName,
                    start = start, end = end, apex = apex, 
                    distance = totalDistance, 
                    bSportType = bSportType,
                    polyline = polyline
                ) ?: Pair(null, null)
                
                if (existingId != null) {
                    clusterEngine.assignClusterToWorkout(context, workoutId, existingId, true)
                } else if (!customName.isNullOrBlank()) {
                    val newId = clusterEngine.learnFromWorkout(start, end, apex, totalDistance, customName, -1L, -1L)
                    clusterEngine.assignClusterToWorkout(context, workoutId, newId, true)
                }
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

    private fun mapHeaderToSensorTypes(header: Array<String>): Map<Int, SensorType?> {
        val map = mutableMapOf<Int, SensorType?>()
        header.forEachIndexed { index, colName ->
            val cleanName = colName.replace("\"", "")
            val sensorType = try {
                SensorType.valueOf(cleanName)
            } catch (e: Exception) {
                // Handle version variations like SPEED_mps vs SPEED
                when {
                    cleanName.startsWith("SPEED") -> SensorType.SPEED_mps
                    cleanName.startsWith("DISTANCE") -> SensorType.DISTANCE_m
                    cleanName.startsWith("LATITUDE") -> SensorType.LATITUDE
                    cleanName.startsWith("LONGITUDE") -> SensorType.LONGITUDE
                    cleanName.startsWith("ALTITUDE") -> SensorType.ALTITUDE
                    cleanName.startsWith("HR") -> SensorType.HR
                    cleanName.startsWith("CADENCE") -> SensorType.CADENCE
                    cleanName.startsWith("POWER") -> SensorType.POWER
                    cleanName == "TIME_ACTIVE" -> SensorType.TIME_ACTIVE
                    cleanName == "LAP_NR" -> SensorType.LAP_NR
                    else -> null
                }
            }
            map[index] = sensorType
        }
        return map
    }

    private fun normalizeTimestamp(raw: String): String {
        val clean = raw.replace("\"", "")
        return if (clean.contains("-")) {
            clean
        } else {
            // Placeholder for legacy numeric handling if needed
            clean
        }
    }
}
