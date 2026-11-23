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
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.migration.ParsedLap
import com.atrainingtracker.trainingtracker.routes.parsers.GpxStreamListener
import com.atrainingtracker.trainingtracker.routes.parsers.GpxTrackpointParser
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.roundToInt

/**
 * Dedicated streaming parser for GPX workout XML files (GPX 1.0 and 1.1) consuming
 * [GpxTrackpointParser.parseStreaming] directly without intermediate trackpoint lists
 * (`REQ-MIG-038`, `REQ-MIG-055`, `REQ-MIG-058.3`, `REQ-MIG-061.1`).
 */
object GpxWorkoutParser : WorkoutParser {
    private const val TAG = "GpxWorkoutParser"

    override val formatExtension: String = "gpx"
    override val workoutSource: WorkoutSource = WorkoutSource.GPX

    /**
     * Parses a GPX file into [ParsedWorkoutData].
     */
    override fun parse(file: File, context: Context): Result<ParsedWorkoutData> {
        return try {
            FileInputStream(file).use { fis ->
                parse(fis, context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading GPX file ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Parses a GPX stream into [ParsedWorkoutData] in a single streaming XML pass (`REQ-MIG-048.1`, `REQ-MIG-061.1`).
     */
    override fun parse(stream: InputStream, context: Context): Result<ParsedWorkoutData> {
        return try {
            var workoutName: String? = null
            var workoutNotes: String? = null
            var sportName: String? = null

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

            var currentParsedLap: ParsedLap? = null
            var lapFirstTime: String? = null
            var lapLastTime: String? = null
            var lapStartDistance = 0.0
            var lapMaxExplicitSpeed: Double? = null
            var lapMaxSegmentSpeed: Double? = null
            var prevLapLat: Double? = null
            var prevLapLng: Double? = null
            var prevLapEpochMs: Long? = null

            val streamResult = GpxTrackpointParser.parseStreaming(stream, object : GpxStreamListener {
                override fun onLapStart(lapNr: Long) {
                    val lap = ParsedLap(lapNr = lapNr)
                    parsedLaps.add(lap)
                    currentParsedLap = lap
                    lapFirstTime = null
                    lapLastTime = null
                    lapStartDistance = cumDist
                    lapMaxExplicitSpeed = null
                    lapMaxSegmentSpeed = null
                    prevLapLat = null
                    prevLapLng = null
                    prevLapEpochMs = null
                }

                override fun onTrackpoint(
                    lapNr: Long,
                    lat: Double,
                    lng: Double,
                    altitude: Double?,
                    time: String?,
                    speedMps: Double?,
                    hr: Int?,
                    cadence: Int?,
                    temperature: Double?,
                    power: Int?
                ) {
                    val parsedLap = currentParsedLap ?: ParsedLap(lapNr = lapNr).also {
                        parsedLaps.add(it)
                        currentParsedLap = it
                    }
                    val values = ContentValues().apply {
                        put(SensorType.LAP_NR.name, lapNr)
                    }

                    val geoPrevLat = prevLat
                    val geoPrevLng = prevLng
                    var lastGeoStepDistM: Double? = null
                    if (geoPrevLat != null && geoPrevLng != null) {
                        val results = FloatArray(1)
                        Location.distanceBetween(geoPrevLat, geoPrevLng, lat, lng, results)
                        val stepDistM = results[0].toDouble()
                        lastGeoStepDistM = stepDistM
                        cumDist += stepDistM
                    }
                    prevLat = lat
                    prevLng = lng

                    values.put(SensorType.LATITUDE.name, lat)
                    values.put(SensorType.LONGITUDE.name, lng)
                    values.put(SensorType.DISTANCE_m.name, cumDist)
                    foundSensors.add(SensorType.LATITUDE)
                    foundSensors.add(SensorType.LONGITUDE)
                    foundSensors.add(SensorType.DISTANCE_m)

                    val pos = LatLng(lat, lng)
                    points.add(pos)

                    altitude?.let { alt ->
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

                    var ptEpochMs: Long? = null
                    time?.let { rawTime ->
                        val formatted = WorkoutTimestampParser.formatIsoToUtcDbString(rawTime)
                        ptEpochMs = WorkoutTimestampParser.parseIsoToEpochMillis(rawTime)

                        values.put("time", formatted)
                        if (firstTime == null) firstTime = formatted
                        lastTime = formatted
                        if (lapFirstTime == null) lapFirstTime = formatted
                        lapLastTime = formatted
                        if (parsedLap.startTime == null) parsedLap.startTime = formatted
                    }

                    speedMps?.let { spd ->
                        if (spd.isFinite() && spd in 0.0..100.0) {
                            values.put(SensorType.SPEED_mps.name, spd)
                            foundSensors.add(SensorType.SPEED_mps)
                            lapMaxExplicitSpeed = lapMaxExplicitSpeed?.let { maxOf(it, spd) } ?: spd
                        }
                    }

                    // Track peak intra-lap GPS segment speed as fallback when explicit <speed> is absent (REQ-MIG-058.3)
                    val curEpochMs = ptEpochMs
                    if (curEpochMs != null) {
                        val pLapLat = prevLapLat
                        val pLapLng = prevLapLng
                        val pLapEpochMs = prevLapEpochMs
                        if (pLapLat != null && pLapLng != null && pLapEpochMs != null) {
                            val dtMs = curEpochMs - pLapEpochMs
                            if (dtMs > 0L) {
                                val segDistM = if (lastGeoStepDistM != null && geoPrevLat == pLapLat && geoPrevLng == pLapLng) {
                                    lastGeoStepDistM
                                } else {
                                    val segResults = FloatArray(1)
                                    Location.distanceBetween(pLapLat, pLapLng, lat, lng, segResults)
                                    segResults[0].toDouble()
                                }
                                val segSpeed = segDistM / (dtMs / 1000.0)
                                if (segSpeed.isFinite() && segSpeed in 0.0..100.0) {
                                    lapMaxSegmentSpeed = lapMaxSegmentSpeed?.let { maxOf(it, segSpeed) } ?: segSpeed
                                }
                            }
                            if (curEpochMs >= pLapEpochMs) {
                                prevLapLat = lat
                                prevLapLng = lng
                                prevLapEpochMs = curEpochMs
                            }
                        } else {
                            prevLapLat = lat
                            prevLapLng = lng
                            prevLapEpochMs = curEpochMs
                        }
                    }

                    hr?.let { hrVal ->
                        values.put(SensorType.HR.name, hrVal)
                        foundSensors.add(SensorType.HR)
                    }
                    cadence?.let { cad ->
                        values.put(SensorType.CADENCE.name, cad)
                        foundSensors.add(SensorType.CADENCE)
                    }
                    temperature?.let { temp ->
                        values.put(SensorType.TEMPERATURE.name, temp)
                        foundSensors.add(SensorType.TEMPERATURE)
                    }
                    power?.let { pwr ->
                        values.put(SensorType.POWER.name, pwr)
                        foundSensors.add(SensorType.POWER)
                    }

                    distances.add(cumDist)

                    if (values.containsKey("time")) {
                        bufferedSamples.add(values)
                    }
                }

                override fun onLapEnd(lapNr: Long) {
                    val parsedLap = currentParsedLap ?: return
                    // Finalize lap stats (REQ-MIG-058.3: true peak maxSpeed, never lap average)
                    parsedLap.distanceMeters = (cumDist - lapStartDistance).coerceAtLeast(0.0)
                    val startT = lapFirstTime
                    val endT = lapLastTime
                    if (startT != null && endT != null) {
                        WorkoutTimestampParser.computeDurationSeconds(startT, endT)?.let { duration ->
                            parsedLap.totalTimeSeconds = duration
                        }
                    }
                    parsedLap.maxSpeed = lapMaxExplicitSpeed ?: lapMaxSegmentSpeed
                    currentParsedLap = null
                }

                override fun onDocumentMetadata(
                    name: String?,
                    description: String?,
                    trackName: String?,
                    trackDesc: String?,
                    sport: String?
                ) {
                    if (!name.isNullOrBlank()) workoutName = name
                    if (!trackName.isNullOrBlank()) workoutName = trackName
                    if (!description.isNullOrBlank()) workoutNotes = description
                    if (!trackDesc.isNullOrBlank()) workoutNotes = trackDesc
                    if (!sport.isNullOrBlank()) sportName = sport
                }
            })

            if (streamResult.isFailure) {
                Log.e(TAG, "Failed to parse GPX XML: ${streamResult.exceptionOrNull()?.message}")
                return Result.failure(streamResult.exceptionOrNull() ?: Exception("Unknown GPX parse error"))
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

            val resolvedSportName = sportName
            if (resolvedSportName != null) {
                val sportId = sportTypeManager.getSportTypeIdFromTcxName(resolvedSportName)
                if (sportId != -1L) {
                    resolvedSportId = sportId
                    bSportType = sportTypeManager.getBSportType(sportId)
                } else {
                    bSportType = when (resolvedSportName.lowercase()) {
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
            Log.e(TAG, "Exception parsing GPX stream", e)
            Result.failure(e)
        }
    }
}
