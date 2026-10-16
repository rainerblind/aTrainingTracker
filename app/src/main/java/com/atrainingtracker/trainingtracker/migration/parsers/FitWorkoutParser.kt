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
import com.atrainingtracker.trainingtracker.migration.FitSportMapper
import com.atrainingtracker.trainingtracker.migration.ParsedLap
import com.garmin.fit.Decode
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileIdMesgListener
import com.garmin.fit.LapMesg
import com.garmin.fit.LapMesgListener
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dedicated parser for Garmin FIT binary workout files.
 *
 * Enforces REQ-MIG-038.
 */
object FitWorkoutParser {
    private const val TAG = "FitWorkoutParser"

    /**
     * Parses a Garmin FIT binary file into [ParsedWorkoutData].
     */
    fun parse(file: File, context: Context): Result<ParsedWorkoutData> {
        return try {
            // Verify file integrity
            val decode = Decode()
            val isIntegrityValid = try {
                FileInputStream(file).use { fis ->
                    decode.checkFileIntegrity(fis)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Integrity check exception for ${file.name}: ${t.message}")
                false
            }
            if (!isIntegrityValid) {
                Log.e(TAG, "FIT file integrity check failed for ${file.name}")
                return Result.failure(IllegalArgumentException("FIT file integrity check failed"))
            }

            val rawFileIds = mutableListOf<FileIdMesg>()
            val rawSessions = mutableListOf<SessionMesg>()
            val rawLaps = mutableListOf<LapMesg>()
            val rawRecords = mutableListOf<RecordMesg>()

            val broadcaster = MesgBroadcaster(decode)
            broadcaster.addListener(FileIdMesgListener { rawFileIds.add(it) })
            broadcaster.addListener(SessionMesgListener { rawSessions.add(it) })
            broadcaster.addListener(LapMesgListener { rawLaps.add(it) })
            broadcaster.addListener(RecordMesgListener { rawRecords.add(it) })

            val readSuccess = try {
                FileInputStream(file).use { fis ->
                    decode.read(fis, broadcaster)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error decoding FIT file ${file.name}: ${t.message}", t)
                false
            }

            if (!readSuccess || rawRecords.isEmpty()) {
                Log.e(TAG, "Decoding failed or no record trackpoints found in ${file.name}")
                return Result.failure(IllegalArgumentException("Decoding failed or no record trackpoints found"))
            }

            val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

            var firstTime: String? = null
            var lastTime: String? = null
            val points = mutableListOf<LatLng>()
            val altitudes = mutableListOf<Double>()
            val distances = mutableListOf<Double>()
            val bufferedSamples = mutableListOf<ContentValues>()
            val parsedLaps = mutableListOf<ParsedLap>()

            var minAltVal = Double.MAX_VALUE
            var maxAltVal = -Double.MAX_VALUE
            var minAltPos: LatLng? = null
            var maxAltPos: LatLng? = null

            // Build parsed laps
            rawLaps.forEachIndexed { index, lapMesg ->
                val lapStartTime = lapMesg.startTime?.date?.let { timeFormat.format(it) }
                val lapTotalTime = (lapMesg.totalTimerTime ?: lapMesg.totalElapsedTime ?: 0f).toDouble()
                val lapDist = (lapMesg.totalDistance ?: 0f).toDouble()
                val lapMaxSpeed = (lapMesg.enhancedMaxSpeed ?: lapMesg.maxSpeed)?.toDouble()
                val lapCalories = lapMesg.totalCalories
                val lapAvgHr = lapMesg.avgHeartRate?.toInt()
                val lapMaxHr = lapMesg.maxHeartRate?.toInt()

                parsedLaps.add(
                    ParsedLap(
                        lapNr = index.toLong(),
                        startTime = lapStartTime,
                        totalTimeSeconds = lapTotalTime,
                        distanceMeters = lapDist,
                        maxSpeed = lapMaxSpeed,
                        calories = lapCalories,
                        avgHeartRate = lapAvgHr,
                        maxHeartRate = lapMaxHr
                    )
                )
            }

            // Build samples from raw records
            rawRecords.forEach { record ->
                val date = record.timestamp?.date ?: return@forEach
                val formattedTime = timeFormat.format(date)
                if (firstTime == null) {
                    firstTime = formattedTime
                }
                lastTime = formattedTime

                val values = ContentValues()
                values.put("time", formattedTime)

                val latSemi = record.positionLat
                val lngSemi = record.positionLong
                val altVal = (record.enhancedAltitude ?: record.altitude)?.toDouble()?.takeIf { it in -500.0..10000.0 }

                if (latSemi != null && lngSemi != null && latSemi != 0x7FFFFFFF && lngSemi != 0x7FFFFFFF) {
                    val latDeg = latSemi * (180.0 / 2147483648.0)
                    val lngDeg = lngSemi * (180.0 / 2147483648.0)
                    values.put(SensorType.LATITUDE.name, latDeg)
                    values.put(SensorType.LONGITUDE.name, lngDeg)

                    val pos = LatLng(latDeg, lngDeg)
                    points.add(pos)

                    altVal?.let { alt ->
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

                altVal?.let { alt ->
                    values.put(SensorType.ALTITUDE.name, alt)
                    altitudes.add(alt)
                }

                record.distance?.toDouble()?.let { dist ->
                    values.put(SensorType.DISTANCE_m.name, dist)
                    distances.add(dist)
                }

                val spdVal = (record.enhancedSpeed ?: record.speed)?.toDouble()
                if (spdVal != null && spdVal in 0.0..100.0) {
                    values.put(SensorType.SPEED_mps.name, spdVal)
                }

                record.heartRate?.toInt()?.let { hr ->
                    if (hr in 30..250) {
                        values.put(SensorType.HR.name, hr)
                    }
                }

                record.cadence?.toDouble()?.let { cad ->
                    if (cad >= 0) {
                        values.put(SensorType.CADENCE.name, cad)
                    }
                }

                record.power?.toDouble()?.let { pwr ->
                    if (pwr >= 0) {
                        values.put(SensorType.POWER.name, pwr)
                    }
                }

                record.temperature?.toDouble()?.let { temp ->
                    values.put(SensorType.TEMPERATURE.name, temp)
                }

                // Determine lapNr
                var sampleLapNr = 0L
                if (parsedLaps.size > 1) {
                    val recordEpoch = date.time
                    for (i in parsedLaps.indices.reversed()) {
                        val lapStart = parsedLaps[i].startTime?.let {
                            try { timeFormat.parse(it)?.time } catch (_: Exception) { null }
                        }
                        if (lapStart != null && recordEpoch >= lapStart) {
                            sampleLapNr = i.toLong()
                            break
                        }
                    }
                }
                values.put(SensorType.LAP_NR.name, sampleLapNr)

                bufferedSamples.add(values)
            }

            if (firstTime == null && parsedLaps.isNotEmpty() && parsedLaps.first().startTime != null) {
                firstTime = parsedLaps.first().startTime
            }

            // Fallback lap if none parsed
            if (parsedLaps.isEmpty()) {
                val totalDist = distances.lastOrNull() ?: 0.0
                parsedLaps.add(ParsedLap(lapNr = 0L, startTime = firstTime, distanceMeters = totalDist))
            }

            // Sport resolution
            val primarySession = rawSessions.firstOrNull()
            var bSportType = FitSportMapper.mapFitSport(primarySession?.sport, primarySession?.subSport)
            var resolvedSportId = if (bSportType != BSportType.UNKNOWN) {
                SportTypeDatabaseManager.getSportTypeId(bSportType)
            } else {
                -1L
            }

            // Infer sport from speed if still unknown (ATT-1116)
            if (bSportType == BSportType.UNKNOWN && distances.isNotEmpty() && bufferedSamples.size > 1) {
                val totalDist = distances.last()
                val durationSec = parsedLaps.sumOf { it.totalTimeSeconds }.takeIf { it > 0 } ?: (bufferedSamples.size.toDouble())
                val avgSpeed = if (durationSec > 0) totalDist / durationSec else 0.0
                if (avgSpeed > 7.0) {
                    bSportType = BSportType.BIKE
                    resolvedSportId = SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE)
                } else if (avgSpeed in 1.5..7.0) {
                    bSportType = BSportType.RUN
                    resolvedSportId = SportTypeDatabaseManager.getSportTypeId(BSportType.RUN)
                }
            }

            Result.success(
                ParsedWorkoutData(
                    workoutName = null,
                    workoutNotes = null,
                    sportName = primarySession?.sport?.name,
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
                    foundSensors = emptySet()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing FIT file ${file.name}", e)
            Result.failure(e)
        }
    }
}
