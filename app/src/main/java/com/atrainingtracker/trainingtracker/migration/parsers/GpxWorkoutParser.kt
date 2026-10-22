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
import android.location.Location
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.migration.ParsedLap
import com.atrainingtracker.trainingtracker.routes.parsers.GpxTrackpointParser
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dedicated parser for GPX workout XML files (GPX 1.0 and 1.1).
 *
 * Enforces REQ-MIG-038.
 */
object GpxWorkoutParser {
    private const val TAG = "GpxWorkoutParser"
    private val tcxTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

    /**
     * Parses a GPX file into [ParsedWorkoutData].
     */
    fun parse(file: File, context: Context): Result<ParsedWorkoutData> {
        return try {
            val parseResult = FileInputStream(file).use { fis ->
                GpxTrackpointParser.parse(fis)
            }
            if (parseResult.isFailure) {
                Log.e(TAG, "Failed to parse GPX XML: ${parseResult.exceptionOrNull()?.message}")
                return Result.failure(parseResult.exceptionOrNull() ?: Exception("Unknown GPX parse error"))
            }
            val parsedGpx = parseResult.getOrThrow()

            var workoutName: String? = null
            var workoutNotes: String? = null
            var sportName: String? = null

            if (!parsedGpx.name.isNullOrBlank()) {
                workoutName = parsedGpx.name
            }
            if (!parsedGpx.trackName.isNullOrBlank()) {
                workoutName = parsedGpx.trackName
            }
            if (!parsedGpx.description.isNullOrBlank()) {
                workoutNotes = parsedGpx.description
            }
            if (!parsedGpx.trackDesc.isNullOrBlank()) {
                workoutNotes = parsedGpx.trackDesc
            }
            if (!parsedGpx.sport.isNullOrBlank()) {
                sportName = parsedGpx.sport
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

            var prevLat: Double? = null
            var prevLng: Double? = null
            var cumDist = 0.0

            for (lap in parsedGpx.laps) {
                var lapFirstTime: String? = null
                var lapLastTime: String? = null
                val lapStartDistance = cumDist

                val parsedLap = ParsedLap(
                    lapNr = lap.lapNr
                )
                parsedLaps.add(parsedLap)

                for (pt in lap.trackpoints) {
                    val lapNr = lap.lapNr
                    val values = ContentValues().apply {
                        put(SensorType.LAP_NR.name, lapNr)
                    }

                    if (pt.lat != null && pt.lng != null) {
                        val pLat = prevLat
                        val pLng = prevLng
                        if (pLat != null && pLng != null) {
                            val results = FloatArray(1)
                            Location.distanceBetween(pLat, pLng, pt.lat, pt.lng, results)
                            cumDist += results[0].toDouble()
                        }
                        prevLat = pt.lat
                        prevLng = pt.lng

                        values.put(SensorType.LATITUDE.name, pt.lat)
                        values.put(SensorType.LONGITUDE.name, pt.lng)
                        values.put(SensorType.DISTANCE_m.name, cumDist)
                        foundSensors.add(SensorType.LATITUDE)
                        foundSensors.add(SensorType.LONGITUDE)
                        foundSensors.add(SensorType.DISTANCE_m)

                        val pos = LatLng(pt.lat, pt.lng)
                        points.add(pos)
                    }

                    pt.altitude?.let { alt ->
                        values.put(SensorType.ALTITUDE.name, alt)
                        foundSensors.add(SensorType.ALTITUDE)
                        altitudes.add(alt)
                        if (pt.lat != null && pt.lng != null) {
                            val pos = LatLng(pt.lat, pt.lng)
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

                    pt.time?.let { rawTime ->
                        val formatted = try {
                            val date = tcxTimeFormat.parse(rawTime.substring(0, 19))
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date!!)
                        } catch (_: Exception) { rawTime }

                        values.put("time", formatted)
                        if (firstTime == null) firstTime = formatted
                        lastTime = formatted
                        if (lapFirstTime == null) lapFirstTime = formatted
                        lapLastTime = formatted
                        if (parsedLap.startTime == null) parsedLap.startTime = formatted
                    }

                    pt.speedMps?.let { spd ->
                        values.put(SensorType.SPEED_mps.name, spd)
                        foundSensors.add(SensorType.SPEED_mps)
                    }
                    pt.hr?.let { hr ->
                        values.put(SensorType.HR.name, hr)
                        foundSensors.add(SensorType.HR)
                    }
                    pt.cadence?.let { cad ->
                        values.put(SensorType.CADENCE.name, cad)
                        foundSensors.add(SensorType.CADENCE)
                    }
                    pt.temperature?.let { temp ->
                        values.put(SensorType.TEMPERATURE.name, temp)
                        foundSensors.add(SensorType.TEMPERATURE)
                    }
                    pt.power?.let { pwr ->
                        values.put(SensorType.POWER.name, pwr)
                        foundSensors.add(SensorType.POWER)
                    }

                    distances.add(cumDist)

                    if (values.containsKey("time")) {
                        bufferedSamples.add(values)
                    }
                }

                // Finalize lap stats
                parsedLap.distanceMeters = (cumDist - lapStartDistance).coerceAtLeast(0.0)
                if (lapFirstTime != null && lapLastTime != null) {
                    try {
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        val t1 = format.parse(lapFirstTime)?.time ?: 0L
                        val t2 = format.parse(lapLastTime)?.time ?: 0L
                        val duration = ((t2 - t1) / 1000.0).coerceAtLeast(0.0)
                        parsedLap.totalTimeSeconds = duration
                        if (duration > 0) {
                            parsedLap.maxSpeed = parsedLap.distanceMeters / duration
                        }
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
                        "running", "run", "jogging" -> BSportType.RUN
                        "biking", "cycling", "bike", "ride", "mountain biking", "road cycling" -> BSportType.BIKE
                        "walking", "hiking", "hike", "walk" -> BSportType.RUN
                        else -> BSportType.UNKNOWN
                    }
                    if (bSportType != BSportType.UNKNOWN) {
                        resolvedSportId = SportTypeDatabaseManager.getSportTypeId(bSportType)
                    }
                }
            }

            // Infer sport from movement speed if still unknown (ATT-1116)
            if (bSportType == BSportType.UNKNOWN && cumDist > 0) {
                val activeTimeSec = if (parsedLaps.any { it.totalTimeSeconds > 0 }) {
                    parsedLaps.sumOf { it.totalTimeSeconds }.roundToInt()
                } else {
                    points.size.coerceAtLeast(altitudes.size).coerceAtLeast(distances.size)
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
                    val inferredSportId = SportTypeDatabaseManager.getSportTypeId(bSportType)
                    if (inferredSportId != -1L) {
                        resolvedSportId = inferredSportId
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
            Log.e(TAG, "Exception parsing GPX file ${file.name}", e)
            Result.failure(e)
        }
    }
}
