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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A class to map database Cursors to UI-specific data classes like WorkoutData.
 * This lives in the UI package because WorkoutData is a UI model.
 * It depends on specific data providers to build the complex WorkoutData object.
 */
class WorkoutDataMapper(
    private val context: Context,
    private val workoutSummariesDatabaseManager: WorkoutSummariesDatabaseManager,
    private val sportTypeDatabaseManager: SportTypeDatabaseManager,
    private val equipmentDbHelper: EquipmentDbHelper,
    private val stravaUploadDbHelper: StravaUploadDbHelper
) {
    // Define all sensors to check
    val sensorsToCheck = SensorType.CORE_METRICS

    /**
     * Creates a WorkoutData object from the current position of a cursor.
     *
     * @param cursor The cursor, already positioned at the desired row.
     * @return A new WorkoutData object.
     */
    fun fromCursor(cursor: Cursor): WorkoutData {

        val workoutId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID))

        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val bSportType = sportTypeDatabaseManager.getBSportType(sportId)
        val sportName = sportTypeDatabaseManager.getUIName(sportId)

        val equipmentId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))
        val equipmentName = equipmentDbHelper.getEquipmentNameFromId(equipmentId)

        val dateTimeResult = formatDateTime(cursor)

        val fileBaseName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME))
        val stravaActivityData = if (fileBaseName != null) stravaUploadDbHelper.getStravaActivityData(fileBaseName) else null

        val totalDistance = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m))
        val startLatLng = workoutSummariesDatabaseManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START)
        val endLatLng = workoutSummariesDatabaseManager.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END)
        val maxDispLatLng = workoutSummariesDatabaseManager.getExtremaPosition(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX)

        val workoutName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME))
        val clusterId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.CLUSTER_ID))
        val clusterName = WorkoutClusterDatabaseManager.getInstance(context).getClusterNameById(clusterId)

        // The mapper is responsible for assembling the final object from its constituent parts.
        return WorkoutData(
            id = workoutId,
            finished = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.FINISHED)) == 1,
            fileBaseName = fileBaseName,
            workoutName = workoutName,
            sportId = sportId,
            sportName = sportName,
            formattedDate = dateTimeResult.date,
            formattedTime = dateTimeResult.time,
            startTimeS = dateTimeResult.timestampS,
            localDateTime = dateTimeResult.localDateTime,
            bSportType = bSportType,
            equipmentName = equipmentName,
            equipmentId = equipmentId,
            commute = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.COMMUTE)) == 1,
            trainer = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TRAINER)) == 1,
            uploadToStrava = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.UPLOAD_TO_STRAVA)),
            mapPolyline = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE)) ?: "",
            encodedAltitudes = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.ALTITUDE_STREAM)) ?: "",
            encodedDistances = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_STREAM)) ?: "",
            clusterId = clusterId,
            clusterName = clusterName,

            minLat = if (cursor.isNull(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT)),
            minLng = if (cursor.isNull(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LNG))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LNG)),
            maxLat = if (cursor.isNull(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LAT))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LAT)),
            maxLng = if (cursor.isNull(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LNG))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LNG)),

            totalDistance = totalDistance,
            maxDisplacement = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX),
            activeTimeSec = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)),
            totalTimeSec = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_TOTAL_s)),
            avgSpeedMps = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPEED_AVERAGE_mps)),
            ascentMeters = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING)),
            descentMeters = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCENDING)),
            minAltitude = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MIN),
            maxAltitude = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MAX),
            maxAltitudeLatLng = workoutSummariesDatabaseManager.getExtremaPosition(workoutId, SensorType.ALTITUDE, ExtremaType.MAX),
            maxDisplacementLatLng = maxDispLatLng,
            startLatLng = startLatLng,
            endLatLng = endLatLng,

            description = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCRIPTION)),
            goal = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.GOAL)),
            method = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.METHOD)),

            stravaSportName = sportTypeDatabaseManager.getStravaName(sportId),

            stravaActivityData = stravaActivityData,

            extremaRows = sensorsToCheck.flatMap { sensorType ->
                val rows = mutableListOf<ExtremaDataRow>()

                // Primary row data
                val rawMinVal = workoutSummariesDatabaseManager.getExtremaValue(workoutId, sensorType, ExtremaType.MIN)
                val rawMinPos = workoutSummariesDatabaseManager.getExtremaPosition(workoutId, sensorType, ExtremaType.MIN)
                val avgVal = workoutSummariesDatabaseManager.getExtremaValue(workoutId, sensorType, ExtremaType.AVG)
                val rawMaxVal = workoutSummariesDatabaseManager.getExtremaValue(workoutId, sensorType, ExtremaType.MAX)
                val rawMaxPos = workoutSummariesDatabaseManager.getExtremaPosition(workoutId, sensorType, ExtremaType.MAX)

                // 1. Create standard row
                val standardRow = createExtremaRow(
                    sensorType,
                    rawMinVal, rawMinPos,
                    avgVal,
                    rawMaxVal, rawMaxPos,
                    bSportType == BSportType.RUN && sensorType == SensorType.SPEED_mps // Special case: label speed even in run
                )
                if (standardRow != null) rows.add(standardRow)

                // 2. Special Case: Derive Pace from Speed for Runs
                if (bSportType == BSportType.RUN && sensorType == SensorType.SPEED_mps) {
                    val paceRow = createDerivedPaceRow(rawMinVal, rawMinPos, avgVal, rawMaxVal, rawMaxPos)
                    if (paceRow != null) rows.add(paceRow)
                }

                rows
            },

            exportStatuses = emptyList() // will be added/merged by the viewModel
        )
    }

    /**
     * DTO for batch-loaded metadata (ATT-359).
     */
    data class BatchMetadata(
        val extrema: Map<Long, List<WorkoutSummariesDatabaseManager.ExtremaRecord>>,
        val stravaData: Map<String, String>,
        val clusterNames: Map<Long, String> = emptyMap()
    )

    /**
     * Optimized mapping using pre-fetched batch metadata (ATT-359).
     * Eliminates N+1 database queries.
     */
    fun fromCursor(cursor: Cursor, batch: BatchMetadata): WorkoutData {
        val workoutId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID))

        val sportId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID))
        val bSportType = sportTypeDatabaseManager.getBSportType(sportId)
        val sportName = sportTypeDatabaseManager.getUIName(sportId)

        val equipmentId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))
        val equipmentName = equipmentDbHelper.getEquipmentNameFromId(equipmentId)

        val dateTimeResult = formatDateTime(cursor)
        val fileBaseName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME))
        
        val stravaActivityData = if (fileBaseName != null) batch.stravaData[fileBaseName] else null
        val workoutExtrema = batch.extrema[workoutId] ?: emptyList()

        fun getBatchVal(sensor: SensorType, type: ExtremaType) = workoutExtrema.find { it.sensorType == sensor && it.extremaType == type }?.value
        fun getBatchPos(sensor: SensorType, type: ExtremaType) = workoutExtrema.find { it.sensorType == sensor && it.extremaType == type }?.position

        val totalDistance = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m))
        val startLatLng = getBatchPos(SensorType.LATITUDE, ExtremaType.START)
        val endLatLng = getBatchPos(SensorType.LATITUDE, ExtremaType.END)
        val maxDispLatLng = getBatchPos(SensorType.LINE_DISTANCE_m, ExtremaType.MAX)

        val workoutName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME))
        val clusterId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.CLUSTER_ID))
        val clusterName = batch.clusterNames[clusterId]

        return WorkoutData(
            id = workoutId,
            finished = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.FINISHED)) == 1,
            fileBaseName = fileBaseName,
            workoutName = workoutName,
            sportId = sportId,
            sportName = sportName,
            formattedDate = dateTimeResult.date,
            formattedTime = dateTimeResult.time,
            startTimeS = dateTimeResult.timestampS,
            localDateTime = dateTimeResult.localDateTime,
            bSportType = bSportType,
            equipmentName = equipmentName,
            equipmentId = equipmentId,
            commute = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.COMMUTE)) == 1,
            trainer = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TRAINER)) == 1,
            uploadToStrava = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.UPLOAD_TO_STRAVA)),
            mapPolyline = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE)) ?: "",
            encodedAltitudes = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.ALTITUDE_STREAM)) ?: "",
            encodedDistances = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_STREAM)) ?: "",
            clusterId = clusterId,
            clusterName = clusterName,

            minLat = if (cursor.isNull(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LAT)),
            minLng = if (cursor.isNull(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LNG))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MIN_LNG)),
            maxLat = if (cursor.isNull(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LAT))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LAT)),
            maxLng = if (cursor.isNull(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LNG))) null else cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.BOUND_MAX_LNG)),

            totalDistance = totalDistance,
            maxDisplacement = getBatchVal(SensorType.LINE_DISTANCE_m, ExtremaType.MAX),
            activeTimeSec = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)),
            totalTimeSec = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_TOTAL_s)),
            avgSpeedMps = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPEED_AVERAGE_mps)),
            ascentMeters = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING)),
            descentMeters = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCENDING)),
            minAltitude = getBatchVal(SensorType.ALTITUDE, ExtremaType.MIN),
            maxAltitude = getBatchVal(SensorType.ALTITUDE, ExtremaType.MAX),
            maxAltitudeLatLng = getBatchPos(SensorType.ALTITUDE, ExtremaType.MAX),
            maxDisplacementLatLng = maxDispLatLng,
            startLatLng = startLatLng,
            endLatLng = endLatLng,

            description = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCRIPTION)),
            goal = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.GOAL)),
            method = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.METHOD)),

            stravaSportName = sportTypeDatabaseManager.getStravaName(sportId),
            stravaActivityData = stravaActivityData,

            extremaRows = sensorsToCheck.flatMap { sensorType ->
                val rows = mutableListOf<ExtremaDataRow>()
                val rawMinVal = getBatchVal(sensorType, ExtremaType.MIN)
                val rawMinPos = getBatchPos(sensorType, ExtremaType.MIN)
                val avgVal = getBatchVal(sensorType, ExtremaType.AVG)
                val rawMaxVal = getBatchVal(sensorType, ExtremaType.MAX)
                val rawMaxPos = getBatchPos(sensorType, ExtremaType.MAX)

                val standardRow = createExtremaRow(
                    sensorType, rawMinVal, rawMinPos, avgVal, rawMaxVal, rawMaxPos,
                    bSportType == BSportType.RUN && sensorType == SensorType.SPEED_mps
                )
                if (standardRow != null) rows.add(standardRow)
                if (bSportType == BSportType.RUN && sensorType == SensorType.SPEED_mps) {
                    val paceRow = createDerivedPaceRow(rawMinVal, rawMinPos, avgVal, rawMaxVal, rawMaxPos)
                    if (paceRow != null) rows.add(paceRow)
                }
                rows
            },
            exportStatuses = emptyList()
        )
    }

    private fun createExtremaRow(
        sensorType: SensorType,
        rawMinVal: Double?, minPos: com.google.android.gms.maps.model.LatLng?,
        avgVal: Double?,
        rawMaxVal: Double?, maxPos: com.google.android.gms.maps.model.LatLng?,
        forceSpeedLabel: Boolean = false
    ): ExtremaDataRow? {
        val min = rawMinVal?.let { sensorType.myFormatter.format(it) }
        val avg = avgVal?.let { sensorType.myFormatter.format(it) }
        val max = rawMaxVal?.let { sensorType.myFormatter.format(it) }

        val iconResId = when (sensorType) {
            SensorType.HR -> R.drawable.ic_heart_rate
            SensorType.SPEED_mps -> R.drawable.ic_speed
            SensorType.PACE_spm -> R.drawable.ic_speed
            SensorType.CADENCE -> R.drawable.ic_cadence
            SensorType.POWER -> R.drawable.ic_power
            SensorType.ALTITUDE -> R.drawable.ic_altitude
            SensorType.TEMPERATURE -> R.drawable.ic_temp_max
            else -> null
        }

        val isMinRelevant = when (sensorType) {
            SensorType.SPEED_mps, SensorType.PACE_spm, SensorType.CADENCE, SensorType.POWER -> {
                min != null && min != "0" && min != "0.0"&& min != "0,0" && min != "0:00" && min != "~~"
            }
            else -> true
        }

        val data = ExtremaDataRow(
            sensorLabel = context.getString(sensorType.shortNameId),
            unitLabel = context.getString(com.atrainingtracker.trainingtracker.MyHelper.getUnitsId(sensorType)),
            minValue = min,
            minLatLng = minPos,
            avgValue = avg,
            maxValue = max,
            maxLatLng = maxPos,
            iconResId = iconResId,
            isMinRelevant = isMinRelevant,
            boldMin = sensorType == SensorType.ALTITUDE || sensorType == SensorType.TEMPERATURE,
            boldAvg = sensorType == SensorType.HR || sensorType == SensorType.SPEED_mps || sensorType == SensorType.PACE_spm || sensorType == SensorType.CADENCE || sensorType == SensorType.POWER,
            boldMax = sensorType == SensorType.HR || sensorType == SensorType.POWER || sensorType == SensorType.ALTITUDE || sensorType == SensorType.TEMPERATURE
        )

        return if (data.hasAnyData()) data else null
    }

    private fun createDerivedPaceRow(
        speedMin: Double?, minPos: com.google.android.gms.maps.model.LatLng?,
        speedAvg: Double?,
        speedMax: Double?, maxPos: com.google.android.gms.maps.model.LatLng?
    ): ExtremaDataRow? {
        val paceType = SensorType.PACE_spm

        // Strict mathematical ordering:
        // paceAtMaxSpeed is the smallest number (Fastest).
        // paceAtMinSpeed is the largest number (Slowest).

        val paceAtMaxSpeed = if (speedMax != null && speedMax > 0.001) paceType.myFormatter.format(1.0 / speedMax) else null
        val paceAtAvgSpeed = if (speedAvg != null && speedAvg > 0.001) paceType.myFormatter.format(1.0 / speedAvg) else null
        val paceAtMinSpeed = if (speedMin != null && speedMin > 0.001) paceType.myFormatter.format(1.0 / speedMin) else null

        val isMinRelevant = paceAtMaxSpeed != null && paceAtMaxSpeed != "0:00" && paceAtMaxSpeed != "~~"

        val data = ExtremaDataRow(
            sensorLabel = context.getString(paceType.shortNameId),
            unitLabel = context.getString(com.atrainingtracker.trainingtracker.MyHelper.getUnitsId(paceType)),
            minValue = paceAtMaxSpeed, // Numerical Min (Fastest)
            minLatLng = maxPos,       // Location of Max Speed = Location of Fastest Pace
            avgValue = paceAtAvgSpeed,
            maxValue = paceAtMinSpeed, // Numerical Max (Slowest)
            maxLatLng = minPos,       // Location of Min Speed = Location of Slowest Pace
            iconResId = R.drawable.ic_speed,
            isMinRelevant = isMinRelevant,
            boldMin = false,
            boldAvg = true,
            boldMax = false
        )

        return if (data.hasAnyData()) data else null
    }

    private data class DateTimeResult(
        val date: String,
        val time: String,
        val timestampS: Long,
        val localDateTime: LocalDateTime)

    private fun formatDateTime(cursor: Cursor): DateTimeResult {
        val startTimeString = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_START))
        val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return try {
            val startTimeDate: Date = dbFormat.parse(startTimeString) ?: throw ParseException("Parsed date is null", 0)
            val localeDateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.DEFAULT)
            val localeTimeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
            // Convert java.util.Date to java.time.LocalDateTime using the system default timezone
            val localDateTime = startTimeDate.toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime()

            DateTimeResult(
                date = localeDateFormat.format(startTimeDate),
                time = localeTimeFormat.format(startTimeDate),
                timestampS = startTimeDate.time / 1000, // Convert ms to seconds
                localDateTime = localDateTime
            )
        } catch (e: ParseException) {
            Log.e("WorkoutHeaderProvider", "Failed to parse date string: $startTimeString", e)
            DateTimeResult(context.getString(R.string.invalid_date), "", 0L, java.time.LocalDateTime.now())
        }
    }


}