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
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.migration.ParsedLap
import com.atrainingtracker.trainingtracker.tcx.model.TcxActivityExtensions
import com.atrainingtracker.trainingtracker.tcx.model.TcxCreator
import com.atrainingtracker.trainingtracker.tcx.model.TcxLapExtensions
import com.atrainingtracker.trainingtracker.tcx.parser.TcxParser
import com.atrainingtracker.trainingtracker.tcx.parser.TcxStreamListener
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Dedicated streaming parser for Garmin TCX workout XML files consuming [TcxParser.parseStreaming]
 * directly without intermediate trackpoint DTO lists (`REQ-MIG-038`, `REQ-DAT-040`, `REQ-MIG-055`, `REQ-MIG-061.1`).
 */
object TcxWorkoutParser : WorkoutParser {
    private const val TAG = "TcxWorkoutParser"

    override val formatExtension: String = "tcx"
    override val workoutSource: WorkoutSource = WorkoutSource.TCX

    /**
     * Parses a TCX file into [ParsedWorkoutData].
     */
    override fun parse(file: File, context: Context): Result<ParsedWorkoutData> {
        return try {
            FileInputStream(file).use { fis ->
                parse(fis, context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading TCX file ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Parses a TCX stream into [ParsedWorkoutData] in a single streaming XML pass (`REQ-MIG-048.1`, `REQ-MIG-061.1`).
     */
    override fun parse(stream: InputStream, context: Context): Result<ParsedWorkoutData> {
        return try {
            var hasActivity = false
            var sportName = ""
            var workoutName: String? = null
            var workoutNotes: String? = null

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

            val streamResult = TcxParser.parseStreaming(stream, object : TcxStreamListener {
                override fun onActivityStart(activityIndex: Int, sport: String) {
                    if (activityIndex == 0) {
                        hasActivity = true
                        sportName = sport
                    }
                }

                override fun onTrackpoint(
                    inCourse: Boolean,
                    ownerIndex: Int,
                    inLap: Boolean,
                    lapIndex: Int,
                    time: String,
                    lat: Double?,
                    lng: Double?,
                    altitudeMeters: Double?,
                    distanceMeters: Double?,
                    heartRateBpm: Int?,
                    cadence: Int?,
                    speed: Double?,
                    watts: Int?,
                    runCadence: Int?
                ) {
                    if (inCourse || ownerIndex != 0 || !inLap) return

                    val values = ContentValues().apply {
                        put(SensorType.LAP_NR.name, lapIndex.toLong())
                    }

                    time.takeIf { it.isNotBlank() }?.let { rawTime ->
                        val formatted = WorkoutTimestampParser.formatIsoToUtcDbString(rawTime)
                        values.put("time", formatted)
                        if (firstTime == null) firstTime = formatted
                        lastTime = formatted
                    }

                    var pos: LatLng? = null
                    if (lat != null && lng != null) {
                        values.put(SensorType.LATITUDE.name, lat)
                        values.put(SensorType.LONGITUDE.name, lng)
                        foundSensors.add(SensorType.LATITUDE)
                        foundSensors.add(SensorType.LONGITUDE)

                        pos = LatLng(lat, lng)
                        points.add(pos)
                    }

                    altitudeMeters?.let { alt ->
                        values.put(SensorType.ALTITUDE.name, alt)
                        foundSensors.add(SensorType.ALTITUDE)
                        altitudes.add(alt)
                        if (pos != null) {
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

                    distanceMeters?.let { dist ->
                        values.put(SensorType.DISTANCE_m.name, dist)
                        foundSensors.add(SensorType.DISTANCE_m)
                        distances.add(dist)
                    }

                    heartRateBpm?.let { hr ->
                        values.put(SensorType.HR.name, hr)
                        foundSensors.add(SensorType.HR)
                    }

                    cadence?.let { cad ->
                        values.put(SensorType.CADENCE.name, cad)
                        foundSensors.add(SensorType.CADENCE)
                    }

                    watts?.let { pwr ->
                        values.put(SensorType.POWER.name, pwr)
                        foundSensors.add(SensorType.POWER)
                    }

                    speed?.let { spd ->
                        values.put(SensorType.SPEED_mps.name, spd)
                        foundSensors.add(SensorType.SPEED_mps)
                    }

                    runCadence?.let { runCad ->
                        if (!values.containsKey(SensorType.CADENCE.name)) {
                            values.put(SensorType.CADENCE.name, runCad)
                            foundSensors.add(SensorType.CADENCE)
                        }
                    }

                    if (values.containsKey("time")) {
                        bufferedSamples.add(values)
                    }
                }

                override fun onLapEnd(
                    inCourse: Boolean,
                    ownerIndex: Int,
                    lapIndex: Int,
                    startTime: String,
                    totalTimeSeconds: Double,
                    distanceMeters: Double,
                    maximumSpeed: Double?,
                    calories: Int?,
                    averageHeartRateBpm: Int?,
                    maximumHeartRateBpm: Int?,
                    intensity: String?,
                    cadence: Int?,
                    triggerMethod: String?,
                    notes: String?,
                    extensions: TcxLapExtensions?
                ) {
                    if (inCourse || ownerIndex != 0) return

                    val formattedStartTime = startTime.takeIf { it.isNotBlank() }
                        ?.let(WorkoutTimestampParser::formatIsoToUtcDbString)

                    parsedLaps.add(
                        ParsedLap(
                            lapNr = lapIndex.toLong(),
                            startTime = formattedStartTime,
                            totalTimeSeconds = totalTimeSeconds,
                            distanceMeters = distanceMeters,
                            maxSpeed = maximumSpeed,
                            calories = calories,
                            avgHeartRate = averageHeartRateBpm,
                            name = extensions?.name,
                            description = extensions?.description
                        )
                    )
                }

                override fun onActivityEnd(
                    activityIndex: Int,
                    sport: String,
                    id: String,
                    notes: String?,
                    creator: TcxCreator?,
                    extensions: TcxActivityExtensions?
                ) {
                    if (activityIndex != 0) return
                    sportName = sport
                    workoutName = extensions?.customName?.takeIf { it.isNotBlank() }
                    workoutNotes = if (extensions != null) {
                        extensions.customDescription?.takeIf { it.isNotBlank() }
                    } else {
                        notes?.takeIf { it.isNotBlank() }
                    }
                }
            })

            if (streamResult.isFailure) {
                Log.e(TAG, "Failed to parse TCX: ${streamResult.exceptionOrNull()?.message}")
                return Result.failure(streamResult.exceptionOrNull() ?: Exception("Unknown TCX parse error"))
            }
            if (!hasActivity) {
                return Result.failure(Exception("TCX document contains no activities"))
            }

            if (firstTime == null && parsedLaps.isNotEmpty() && parsedLaps.first().startTime != null) {
                firstTime = parsedLaps.first().startTime
            }

            val sportTypeManager = SportTypeDatabaseManager.getInstance(context)
            var bSportType = BSportType.UNKNOWN
            var resolvedSportId = -1L
            if (sportName.isNotBlank()) {
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
            Log.e(TAG, "Exception parsing TCX stream", e)
            Result.failure(e)
        }
    }
}
