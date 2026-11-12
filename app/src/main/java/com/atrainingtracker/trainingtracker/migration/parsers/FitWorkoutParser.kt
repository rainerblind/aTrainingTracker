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
import com.garmin.fit.HrvMesg
import com.garmin.fit.HrvMesgListener
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
            val bytes = file.readBytes()
            parse(bytes, context)
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading FIT file ${file.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Parses a Garmin FIT binary stream into [ParsedWorkoutData] (REQ-MIG-048.1).
     */
    fun parse(stream: java.io.InputStream, context: Context): Result<ParsedWorkoutData> {
        return try {
            val bytes = stream.readBytes()
            parse(bytes, context)
        } catch (e: Exception) {
            Log.e(TAG, "Exception reading FIT stream", e)
            Result.failure(e)
        }
    }

    /**
     * Parses Garmin FIT binary bytes into [ParsedWorkoutData].
     */
    fun parse(bytes: ByteArray, context: Context): Result<ParsedWorkoutData> {
        return try {
            // Verify file integrity
            val decode = Decode()
            val isIntegrityValid = try {
                java.io.ByteArrayInputStream(bytes).use { bais ->
                    decode.checkFileIntegrity(bais)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Integrity check exception: ${t.message}")
                false
            }
            if (!isIntegrityValid) {
                Log.e(TAG, "FIT file integrity check failed")
                return Result.failure(IllegalArgumentException("FIT file integrity check failed"))
            }

            val rawFileIds = mutableListOf<FileIdMesg>()
            val rawSessions = mutableListOf<SessionMesg>()
            val rawLaps = mutableListOf<LapMesg>()
            val rawRecords = mutableListOf<RecordMesg>()
            val rawHrvs = mutableListOf<HrvMesg>()
            val extractedRrIntervals = mutableListOf<Double>()
            val extractedRrBeats = mutableListOf<FitRrBeat>()
            var currentAnchorTimestampMs: Long = 0L

            val broadcaster = MesgBroadcaster(decode)
            broadcaster.addListener(FileIdMesgListener {
                rawFileIds.add(it)
                val created = it.timeCreated?.date?.time
                if (created != null && created > 0L && currentAnchorTimestampMs == 0L) {
                    currentAnchorTimestampMs = created
                }
            })
            broadcaster.addListener(SessionMesgListener {
                rawSessions.add(it)
                val start = it.startTime?.date?.time
                if (start != null && start > 0L && currentAnchorTimestampMs == 0L) {
                    currentAnchorTimestampMs = start
                }
            })
            broadcaster.addListener(LapMesgListener { rawLaps.add(it) })
            broadcaster.addListener(RecordMesgListener {
                rawRecords.add(it)
                val ts = it.timestamp?.date?.time
                if (ts != null && ts > 0L) {
                    currentAnchorTimestampMs = ts
                }
            })
            broadcaster.addListener(HrvMesgListener { hrvMesg ->
                rawHrvs.add(hrvMesg)
                val numTime = hrvMesg.numTime
                for (i in 0 until numTime) {
                    val tSec = hrvMesg.getTime(i)
                    if (tSec != null && tSec > 0f && tSec < 10f) {
                        val rrMs = tSec.toDouble() * 1000.0
                        extractedRrIntervals.add(rrMs)
                        extractedRrBeats.add(FitRrBeat(rrMs = rrMs, anchorTimestampMs = currentAnchorTimestampMs))
                    }
                }
            })

            val readSuccess = try {
                java.io.ByteArrayInputStream(bytes).use { bais ->
                    decode.read(bais, broadcaster)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error decoding FIT bytes: ${t.message}", t)
                false
            }

            if (!readSuccess || rawRecords.isEmpty()) {
                Log.e(TAG, "Decoding failed or no record trackpoints found")
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
            val foundSensors = mutableSetOf<SensorType>()

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
                    foundSensors.add(SensorType.LATITUDE)
                    foundSensors.add(SensorType.LONGITUDE)

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
                    foundSensors.add(SensorType.ALTITUDE)
                    altitudes.add(alt)
                }

                record.distance?.toDouble()?.let { dist ->
                    values.put(SensorType.DISTANCE_m.name, dist)
                    foundSensors.add(SensorType.DISTANCE_m)
                    distances.add(dist)
                }

                val spdVal = (record.enhancedSpeed ?: record.speed)?.toDouble()
                if (spdVal != null && spdVal in 0.0..100.0) {
                    values.put(SensorType.SPEED_mps.name, spdVal)
                    foundSensors.add(SensorType.SPEED_mps)
                }

                record.heartRate?.toInt()?.let { hr ->
                    if (hr in 30..250) {
                        values.put(SensorType.HR.name, hr)
                        foundSensors.add(SensorType.HR)
                    }
                }

                record.cadence?.toDouble()?.let { cad ->
                    if (cad >= 0) {
                        values.put(SensorType.CADENCE.name, cad)
                        foundSensors.add(SensorType.CADENCE)
                    }
                }

                record.power?.toDouble()?.let { pwr ->
                    if (pwr >= 0) {
                        values.put(SensorType.POWER.name, pwr)
                        foundSensors.add(SensorType.POWER)
                    }
                }

                record.temperature?.toDouble()?.let { temp ->
                    values.put(SensorType.TEMPERATURE.name, temp)
                    foundSensors.add(SensorType.TEMPERATURE)
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

            // Sport resolution & optional FIT session power summaries (REQ-MIG-039.3)
            val primarySession = rawSessions.firstOrNull()
            val sessionNp = primarySession?.normalizedPower?.toDouble()?.takeIf { it > 0.0 }
            val sessionIf = primarySession?.intensityFactor?.toDouble()?.takeIf { it > 0.0 }
            val sessionTss = primarySession?.trainingStressScore?.toDouble()?.takeIf { it > 0.0 }
            val sessionWorkKj = primarySession?.totalWork?.toDouble()?.takeIf { it > 0.0 }?.let { it / 1000.0 }
            val sessionRpe = primarySession?.workoutRpe?.toInt()?.let { raw ->
                (raw / 10).coerceIn(1, 10)
            }
            val sessionFeel = primarySession?.workoutFeel?.toInt()?.let { raw ->
                (Math.round(raw.toFloat() / 25f) + 1).coerceIn(1, 5)
            }

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
                    foundSensors = foundSensors,
                    sessionNormalizedPowerWatts = sessionNp,
                    sessionIntensityFactor = sessionIf,
                    sessionTss = sessionTss,
                    sessionWorkKj = sessionWorkKj,
                    sessionRpe = sessionRpe,
                    sessionFeel = sessionFeel,
                    rawRrIntervals = extractedRrIntervals,
                    parsedRrBeats = extractedRrBeats
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing FIT data", e)
            Result.failure(e)
        }
    }
}
