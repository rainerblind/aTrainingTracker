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
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.components.workoutdescription.DescriptionDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutdetails.WorkoutDetailsDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataProvider
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderDataProvider
import java.text.ParseException
import java.text.SimpleDateFormat
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
    private val equipmentDbHelper: EquipmentDbHelper
) {
    // Define all sensors to check
    val sensorsToCheck = arrayOf(
        SensorType.HR,
        SensorType.SPEED_mps,
        SensorType.PACE_spm,
        SensorType.CADENCE,
        SensorType.POWER,
        SensorType.TORQUE,
        SensorType.PEDAL_POWER_BALANCE,
        SensorType.PEDAL_SMOOTHNESS_L,
        SensorType.PEDAL_SMOOTHNESS,
        SensorType.PEDAL_SMOOTHNESS_R,
        SensorType.ALTITUDE,
        SensorType.TEMPERATURE
    )

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


        // The mapper is responsible for assembling the final object from its constituent parts.
        return WorkoutData(
            id = workoutId,
            finished = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.FINISHED)) == 1,
            fileBaseName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.FILE_BASE_NAME)),
            workoutName = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_NAME)),
            sportId = sportId,
            sportName = sportName,
            formattedDate = dateTimeResult.date,
            formattedTime = dateTimeResult.time,
            startTimeS = dateTimeResult.timestampS,
            bSportType = bSportType,
            equipmentName = equipmentName,
            equipmentId = equipmentId,
            commute = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.COMMUTE)) == 1,
            trainer = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.TRAINER)) == 1,
            map_polyline = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE)) ?: "",

            totalDistance = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.DISTANCE_TOTAL_m)),
            maxDisplacement = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.LINE_DISTANCE_m, ExtremaType.MAX),
            activeTimeSec = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_ACTIVE_s)),
            totalTimeSec = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_TOTAL_s)),
            avgSpeedMps = cursor.getDouble(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPEED_AVERAGE_mps)),
            ascentMeters = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.ASCENDING)),
            descentMeters = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCENDING)),
            minAltitude = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MIN),
            maxAltitude = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.ALTITUDE, ExtremaType.MAX),

            description = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.DESCRIPTION)),
            goal = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.GOAL)),
            method = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.METHOD)),

            isCalculatingExtrema = cursor.getInt(cursor.getColumnIndexOrThrow(WorkoutSummaries.EXTREMA_VALUES_CALCULATED)) == 0,
            extremaRows = sensorsToCheck.mapNotNull { sensorType ->
                // Business logic: do not show speed for running activities
                if (bSportType == BSportType.RUN && sensorType == SensorType.SPEED_mps) {
                    return@mapNotNull null // Skip this sensor
                }
                // Business logic: show pace only for running activities
                if (bSportType != BSportType.RUN && sensorType == SensorType.PACE_spm) {
                    return@mapNotNull null // Skip this sensor
                }

                val min = getFormattedExtremaValue(workoutId, sensorType, ExtremaType.MIN)
                val avg = getFormattedExtremaValue(workoutId, sensorType, ExtremaType.AVG)
                val max = getFormattedExtremaValue(workoutId, sensorType, ExtremaType.MAX)

                val data = ExtremaDataRow(
                    sensorLabel = context.getString(sensorType.shortNameId),
                    unitLabel = context.getString(MyHelper.getUnitsId(sensorType)),
                    minValue = min,
                    avgValue = avg,
                    maxValue = max
                )

                // Only return the data object if it's not empty, otherwise return null
                if (data.hasAnyData()) data else null
            },

            trackPoints = emptyList(),  // will be added/merged by the repository
            exportStatuses = emptyList() // will be added/merged by the viewModel
        )
    }

    private data class DateTimeResult(val date: String, val time: String, val timestampS: Long)

    private fun formatDateTime(cursor: Cursor): DateTimeResult {
        val startTimeString = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSummaries.TIME_START))
        val dbFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        return try {
            val startTimeDate: Date = dbFormat.parse(startTimeString) ?: throw ParseException("Parsed date is null", 0)
            val localeDateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.DEFAULT)
            val localeTimeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)

            DateTimeResult(
                date = localeDateFormat.format(startTimeDate),
                time = localeTimeFormat.format(startTimeDate),
                timestampS = startTimeDate.time / 1000 // Convert ms to seconds
            )
        } catch (e: ParseException) {
            Log.e("WorkoutHeaderProvider", "Failed to parse date string: $startTimeString", e)
            DateTimeResult(context.getString(R.string.invalid_date), "", 0L)
        }
    }


    private fun getFormattedExtremaValue(workoutId: Long, sensorType: SensorType, extremaType: ExtremaType): String? {
        val value = WorkoutSummariesDatabaseManager.getInstance(context).getExtremaValue(workoutId, sensorType, extremaType)
        // if (DEBUG) Log.d(TAG, "${sensorType.name} ${extremaType.name} extremaValue=$value")
        // Use Kotlin's scope function 'let' for safe handling of nullable values
        return value?.let { sensorType.myFormatter.format(it) }
    }
}