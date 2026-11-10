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

package com.atrainingtracker.trainingtracker.cloud.healthconnect

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Transforms [WorkoutData] and per-second sample records from [WorkoutSamplesDatabaseManager]
 * into standardized Android Health Connect records (REQ-SET-101, REQ-SET-102).
 */
object HealthConnectRecordMapper {

    private const val TAG = "HealthConnectMapper"
    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Maps [BSportType] and indoor trainer flag to Health Connect exercise type integer constant.
     */
    fun mapSportTypeToExerciseType(sportType: BSportType?, isTrainer: Boolean): Int {
        return when (sportType) {
            BSportType.BIKE -> {
                if (isTrainer) ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
                else ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            }
            BSportType.RUN -> {
                if (isTrainer) ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
                else ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            }
            else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }

    /**
     * Builds the main [ExerciseSessionRecord] for Health Connect.
     */
    fun buildExerciseSessionRecord(
        workoutData: WorkoutData,
        route: ExerciseRoute? = null
    ): ExerciseSessionRecord {
        val startTime = Instant.ofEpochSecond(workoutData.startTimeS)
        val durationSec = maxOf(1L, workoutData.totalTimeSec)
        val endTime = startTime.plusSeconds(durationSec)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)

        val clientRecordId = "workout_${workoutData.fileBaseName ?: workoutData.id}"
        val metadata = Metadata(clientRecordId = clientRecordId)

        return ExerciseSessionRecord(
            startTime = startTime,
            startZoneOffset = zoneOffset,
            endTime = endTime,
            endZoneOffset = zoneOffset,
            exerciseType = mapSportTypeToExerciseType(workoutData.bSportType, workoutData.trainer),
            title = workoutData.workoutName.ifBlank { "Workout" },
            metadata = metadata,
            exerciseRoute = route
        )
    }

    /**
     * Reads per-second samples for the workout and constructs telemetry series records.
     * Route points are extracted into [ExerciseRoute] (or null for trackless indoor sessions).
     */
    fun extractRecords(
        context: Context,
        workoutData: WorkoutData
    ): Pair<ExerciseSessionRecord, List<Record>> {
        val startTime = Instant.ofEpochSecond(workoutData.startTimeS)
        val durationSec = maxOf(1L, workoutData.totalTimeSec)
        val endTime = startTime.plusSeconds(durationSec)
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)
        val baseId = "workout_${workoutData.fileBaseName ?: workoutData.id}"

        val routeLocations = mutableListOf<ExerciseRoute.Location>()
        val hrSamples = mutableListOf<HeartRateRecord.Sample>()
        val powerSamples = mutableListOf<PowerRecord.Sample>()
        val speedSamples = mutableListOf<SpeedRecord.Sample>()

        val fileBaseName = workoutData.fileBaseName
        if (!fileBaseName.isNullOrBlank()) {
            val samplesDbManager = WorkoutSamplesDatabaseManager.getInstance(context)
            try {
                val db = samplesDbManager.database
                val tableName = WorkoutSamplesDatabaseManager.getTableName(fileBaseName)
                if (db != null && db.isOpen) {
                    db.query(tableName, null, null, null, null, null, null).use { cursor ->
                        var sampleIdx = 0L
                        while (cursor.moveToNext()) {
                            val sampleTime = parseSampleTime(cursor, startTime, sampleIdx++)

                            // Filter to session time bounds
                            if (sampleTime.isBefore(startTime) || sampleTime.isAfter(endTime)) {
                                continue
                            }

                            // GPS Route (only for non-trainer outdoor sessions with valid non-zero coords)
                            if (!workoutData.trainer &&
                                dataValid(cursor, SensorType.LATITUDE.name) &&
                                dataValid(cursor, SensorType.LONGITUDE.name)
                            ) {
                                val lat = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name))
                                val lon = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name))
                                if (!lat.isNaN() && !lon.isNaN() &&
                                    lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0 &&
                                    (lat != 0.0 || lon != 0.0)
                                ) {
                                    val alt = if (dataValid(cursor, SensorType.ALTITUDE.name)) {
                                        val a = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name))
                                        if (!a.isNaN() && a >= -500.0 && a <= 9000.0) Length.meters(a) else null
                                    } else null

                                    routeLocations.add(
                                        ExerciseRoute.Location(
                                            time = sampleTime,
                                            latitude = lat,
                                            longitude = lon,
                                            altitude = alt
                                        )
                                    )
                                }
                            }

                            // Heart Rate
                            if (dataValid(cursor, SensorType.HR.name)) {
                                val hr = cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.HR.name))
                                if (hr in 1..299) {
                                    hrSamples.add(HeartRateRecord.Sample(time = sampleTime, beatsPerMinute = hr.toLong()))
                                }
                            }

                            // Power
                            if (dataValid(cursor, SensorType.POWER.name)) {
                                val pwr = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.POWER.name))
                                if (!pwr.isNaN() && pwr >= 0.0 && pwr <= 4000.0) {
                                    powerSamples.add(PowerRecord.Sample(time = sampleTime, power = Power.watts(pwr)))
                                }
                            }

                            // Speed
                            if (dataValid(cursor, SensorType.SPEED_mps.name)) {
                                val spd = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.SPEED_mps.name))
                                if (!spd.isNaN() && spd >= 0.0 && spd <= 150.0) {
                                    speedSamples.add(SpeedRecord.Sample(time = sampleTime, speed = Velocity.metersPerSecond(spd)))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading samples for Health Connect mapping: ${e.message}")
            }
        }

        // Build route if valid outdoor points exist, otherwise null (REQ-SET-102 trackless parity)
        val route = if (routeLocations.isNotEmpty()) {
            ExerciseRoute(routeLocations)
        } else {
            null
        }

        val sessionRecord = buildExerciseSessionRecord(workoutData, route)
        val seriesRecords = mutableListOf<Record>()

        if (hrSamples.isNotEmpty()) {
            seriesRecords.add(
                HeartRateRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    samples = hrSamples,
                    metadata = Metadata(clientRecordId = "${baseId}_hr")
                )
            )
        }

        if (powerSamples.isNotEmpty()) {
            seriesRecords.add(
                PowerRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    samples = powerSamples,
                    metadata = Metadata(clientRecordId = "${baseId}_power")
                )
            )
        }

        if (speedSamples.isNotEmpty()) {
            seriesRecords.add(
                SpeedRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    samples = speedSamples,
                    metadata = Metadata(clientRecordId = "${baseId}_speed")
                )
            )
        }

        // Distance record (if total distance > 0)
        if (workoutData.totalDistance > 0.0) {
            seriesRecords.add(
                DistanceRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    distance = Length.meters(workoutData.totalDistance),
                    metadata = Metadata(clientRecordId = "${baseId}_distance")
                )
            )
        }

        // Elevation gained record (if ascent > 0)
        if (workoutData.ascentMeters > 0) {
            seriesRecords.add(
                ElevationGainedRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    elevation = Length.meters(workoutData.ascentMeters.toDouble()),
                    metadata = Metadata(clientRecordId = "${baseId}_elevation")
                )
            )
        }

        // Total calories burned record
        val calories = extractCalories(workoutData)
        if (calories > 0.0) {
            seriesRecords.add(
                TotalCaloriesBurnedRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = endTime,
                    endZoneOffset = zoneOffset,
                    energy = Energy.kilocalories(calories),
                    metadata = Metadata(clientRecordId = "${baseId}_calories")
                )
            )
        }

        return Pair(sessionRecord, seriesRecords)
    }

    private fun dataValid(cursor: Cursor, columnName: String): Boolean {
        val idx = cursor.getColumnIndex(columnName)
        return idx >= 0 && !cursor.isNull(idx)
    }

    private fun parseSampleTime(cursor: Cursor, fallbackStartTime: Instant, sampleIdx: Long): Instant {
        if (dataValid(cursor, WorkoutSamplesDbHelper.TIME)) {
            val timeStr = cursor.getString(cursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME))
            if (!timeStr.isNullOrBlank()) {
                try {
                    val ldt = LocalDateTime.parse(timeStr.trim(), DATE_TIME_FORMATTER)
                    return ldt.toInstant(ZoneOffset.UTC)
                } catch (ignored: DateTimeParseException) {
                }
            }
        }
        return fallbackStartTime.plusSeconds(sampleIdx)
    }

    private fun extractCalories(workoutData: WorkoutData): Double {
        for (row in workoutData.extremaRows) {
            if (row.unitLabel.contains("kcal", ignoreCase = true) || row.sensorLabel.contains("cal", ignoreCase = true)) {
                val str = row.maxValue ?: row.avgValue
                str?.toDoubleOrNull()?.let { return it }
            }
        }
        return 0.0
    }
}
