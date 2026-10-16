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

package com.atrainingtracker.trainingtracker.migration.parsers

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.migration.ParsedLap
import com.atrainingtracker.trainingtracker.routes.parsers.TcxTrackpointParser
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dedicated parser for Garmin TCX workout XML files.
 *
 * Enforces REQ-MIG-038.
 */
object TcxWorkoutParser {
    private const val TAG = "TcxWorkoutParser"
    private val tcxTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    /**
     * Parses a TCX file into [ParsedWorkoutData].
     */
    fun parse(file: File, context: Context): Result<ParsedWorkoutData> {
        return try {
            val parseResult = FileInputStream(file).use { fis ->
                TcxTrackpointParser.parse(fis)
            }
            if (parseResult.isFailure) {
                Log.e(TAG, "Failed to parse TCX: ${parseResult.exceptionOrNull()?.message}")
                return Result.failure(parseResult.exceptionOrNull() ?: Exception("Unknown TCX parse error"))
            }
            val parsedTcx = parseResult.getOrThrow()

            var workoutName: String? = null
            var workoutNotes: String? = null
            val sportName = parsedTcx.sport

            if (!parsedTcx.name.isNullOrBlank()) {
                workoutName = parsedTcx.name
            }
            if (!parsedTcx.notes.isNullOrBlank()) {
                workoutNotes = parsedTcx.notes
            }

            var firstTime: String? = null
            var lastTime: String? = null
            val points = mutableListOf<LatLng>()
            val altitudes = mutableListOf<Double>()
            val distances = mutableListOf<Double>()
            val parsedLaps = mutableListOf<ParsedLap>()

            var minAltVal = Double.MAX_VALUE
            var minAltPos: LatLng? = null
            var maxAltVal = -Double.MAX_VALUE
            var maxAltPos: LatLng? = null

            val foundSensors = mutableSetOf<SensorType>()
            val bufferedSamples = mutableListOf<ContentValues>()

            for (lap in parsedTcx.laps) {
                val formattedStartTime = lap.startTime?.let { raw ->
                    try {
                        val date = tcxTimeFormat.parse(raw.substring(0, 19))
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date!!)
                    } catch (_: Exception) { raw }
                }

                val parsedLap = ParsedLap(
                    lapNr = lap.lapNr,
                    startTime = formattedStartTime,
                    totalTimeSeconds = lap.totalTimeSeconds ?: 0.0,
                    distanceMeters = lap.distanceMeters ?: 0.0,
                    maxSpeed = lap.maxSpeed,
                    calories = lap.calories,
                    avgHeartRate = lap.avgHeartRate,
                    name = lap.name,
                    description = lap.description
                )
                parsedLaps.add(parsedLap)

                for (pt in lap.trackpoints) {
                    val values = ContentValues().apply {
                        put(SensorType.LAP_NR.name, lap.lapNr)
                    }

                    pt.time?.let { rawTime ->
                        val formatted = try {
                            val date = tcxTimeFormat.parse(rawTime.substring(0, 19))
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date!!)
                        } catch (_: Exception) { rawTime }
                        values.put("time", formatted)
                        if (firstTime == null) firstTime = formatted
                        lastTime = formatted
                    }

                    values.put(SensorType.LATITUDE.name, pt.lat)
                    values.put(SensorType.LONGITUDE.name, pt.lng)
                    foundSensors.add(SensorType.LATITUDE)
                    foundSensors.add(SensorType.LONGITUDE)

                    val pos = LatLng(pt.lat, pt.lng)
                    points.add(pos)

                    pt.altitude?.let { alt ->
                        values.put(SensorType.ALTITUDE.name, alt)
                        foundSensors.add(SensorType.ALTITUDE)
                        altitudes.add(alt)
                        if (alt < minAltVal) {
                            minAltVal = alt
                            minAltPos = pos
                        }
                        if (alt > maxAltVal) {
                            maxAltVal = alt
                            maxAltPos = pos
                        }
                    }

                    pt.distanceMeters?.let { dist ->
                        values.put(SensorType.DISTANCE_m.name, dist)
                        foundSensors.add(SensorType.DISTANCE_m)
                        distances.add(dist)
                    }

                    pt.hr?.let { hr ->
                        values.put(SensorType.HR.name, hr)
                        foundSensors.add(SensorType.HR)
                    }

                    pt.cadence?.let { cad ->
                        values.put(SensorType.CADENCE.name, cad)
                        foundSensors.add(SensorType.CADENCE)
                    }

                    pt.power?.let { pwr ->
                        values.put(SensorType.POWER.name, pwr)
                        foundSensors.add(SensorType.POWER)
                    }

                    pt.speedMps?.let { spd ->
                        values.put(SensorType.SPEED_mps.name, spd)
                        foundSensors.add(SensorType.SPEED_mps)
                    }

                    if (values.containsKey("time")) {
                        bufferedSamples.add(values)
                    }
                }
            }

            if (firstTime == null && parsedLaps.isNotEmpty() && parsedLaps.first().startTime != null) {
                firstTime = parsedLaps.first().startTime
            }

            val sportTypeManager = SportTypeDatabaseManager.getInstance(context)
            var bSportType = BSportType.UNKNOWN
            var resolvedSportId = -1L
            if (sportName != null) {
                val sportId = sportTypeManager.getSportTypeIdFromTcxName(sportName)
                if (sportId != -1L) {
                    resolvedSportId = sportId
                    bSportType = sportTypeManager.getBSportType(sportId)
                } else {
                    bSportType = when (sportName.lowercase()) {
                        "running" -> BSportType.RUN
                        "biking", "cycling" -> BSportType.BIKE
                        "walking" -> BSportType.RUN
                        else -> BSportType.UNKNOWN
                    }
                    if (bSportType != BSportType.UNKNOWN) {
                        resolvedSportId = SportTypeDatabaseManager.getSportTypeId(bSportType)
                    }
                }
            }

            Result.success(
                ParsedWorkoutData(
                    workoutName = workoutName,
                    workoutNotes = workoutNotes,
                    sportName = sportName,
                    bSportType = bSportType,
                    resolvedSportId = resolvedSportId,
                    firstTime = firstTime,
                    lastTime = lastTime,
                    points = points,
                    altitudes = altitudes,
                    distances = distances,
                    minAltVal = minAltVal,
                    minAltPos = minAltPos,
                    maxAltVal = maxAltVal,
                    maxAltPos = maxAltPos,
                    parsedLaps = parsedLaps,
                    bufferedSamples = bufferedSamples,
                    foundSensors = foundSensors
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing TCX file ${file.name}", e)
            Result.failure(e)
        }
    }
}
