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
import com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriter
import com.atrainingtracker.trainingtracker.migration.FitSportMapper
import com.atrainingtracker.trainingtracker.migration.ParsedLap
import com.garmin.fit.Decode
import com.garmin.fit.DeveloperDataIdMesgListener
import com.garmin.fit.DeveloperField
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileIdMesgListener
import com.garmin.fit.Fit
import com.garmin.fit.HrvMesg
import com.garmin.fit.HrvMesgListener
import com.garmin.fit.LapMesg
import com.garmin.fit.LapMesgListener
import com.garmin.fit.LeftRightBalance
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dedicated parser for Garmin FIT binary workout files.
 *
 * Enforces REQ-MIG-038, REQ-MIG-053, REQ-MIG-058.1, REQ-MIG-061.1.
 */
object FitWorkoutParser : WorkoutParser {
    private const val TAG = "FitWorkoutParser"

    override val formatExtension: String = "fit"
    override val workoutSource: WorkoutSource = WorkoutSource.FIT

    /**
     * Parses a Garmin FIT binary file into [ParsedWorkoutData].
     */
    override fun parse(file: File, context: Context): Result<ParsedWorkoutData> {
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
    override fun parse(stream: java.io.InputStream, context: Context): Result<ParsedWorkoutData> {
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
            val devDataAppIds = HashMap<Short, ByteArray>()
            val extractedRrIntervals = mutableListOf<Double>()
            val extractedRrBeats = mutableListOf<FitRrBeat>()
            var currentAnchorTimestampMs: Long = 0L

            val broadcaster = MesgBroadcaster(decode)
            broadcaster.addListener(DeveloperDataIdMesgListener { devIdMesg ->
                val devIndex = devIdMesg.developerDataIndex
                if (devIndex != null && devIndex != Fit.UINT8_INVALID.toShort()) {
                    val appIdBytes = extractValidAppId(devIdMesg.applicationId)
                    if (appIdBytes != null) {
                        devDataAppIds[devIndex] = appIdBytes
                    } else {
                        devDataAppIds.remove(devIndex)
                    }
                }
            })
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

            // Build parsed laps and precompute numeric epoch boundaries (REQ-MIG-058.1)
            val lapStartEpochs = LongArray(rawLaps.size) { Long.MIN_VALUE }
            rawLaps.forEachIndexed { index, lapMesg ->
                val lapStartDate = lapMesg.startTime?.date
                if (lapStartDate != null) {
                    lapStartEpochs[index] = lapStartDate.time
                }
                val lapStartTime = lapStartDate?.let { timeFormat.format(it) }
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
            val canBinarySearchLaps = lapStartEpochs.size > 1 && isNonNullSorted(lapStartEpochs)

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

                // Decode Cycling Power Dynamics (REQ-EXP-043.4 / ATT-8196)
                record.leftRightBalance?.let { rawBalance ->
                    val balanceInt = rawBalance.toInt()
                    val isRight = (balanceInt and LeftRightBalance.RIGHT.toInt()) != 0
                    val pct = (balanceInt and LeftRightBalance.MASK.toInt()).toDouble()
                    if (isRight && pct in 0.0..100.0) {
                        values.put(SensorType.PEDAL_POWER_BALANCE.name, pct)
                        foundSensors.add(SensorType.PEDAL_POWER_BALANCE)
                    }
                }
                record.leftTorqueEffectiveness?.toDouble()?.let { teL ->
                    if (teL in 0.0..100.0) {
                        values.put(SensorType.TORQUE_EFFECTIVENESS_L.name, teL)
                        foundSensors.add(SensorType.TORQUE_EFFECTIVENESS_L)
                    }
                }
                record.rightTorqueEffectiveness?.toDouble()?.let { teR ->
                    if (teR in 0.0..100.0) {
                        values.put(SensorType.TORQUE_EFFECTIVENESS_R.name, teR)
                        foundSensors.add(SensorType.TORQUE_EFFECTIVENESS_R)
                    }
                }
                record.leftPedalSmoothness?.toDouble()?.let { psL ->
                    if (psL in 0.0..100.0) {
                        values.put(SensorType.PEDAL_SMOOTHNESS_L.name, psL)
                        foundSensors.add(SensorType.PEDAL_SMOOTHNESS_L)
                    }
                }
                record.rightPedalSmoothness?.toDouble()?.let { psR ->
                    if (psR in 0.0..100.0) {
                        values.put(SensorType.PEDAL_SMOOTHNESS_R.name, psR)
                        foundSensors.add(SensorType.PEDAL_SMOOTHNESS_R)
                    }
                }
                record.combinedPedalSmoothness?.toDouble()?.let { psComb ->
                    if (psComb in 0.0..100.0) {
                        values.put(SensorType.PEDAL_SMOOTHNESS.name, psComb)
                        foundSensors.add(SensorType.PEDAL_SMOOTHNESS)
                    }
                }

                // Decode Garmin FIT V2.0 Developer Fields for Bike Radar strictly scoped by APP_ID (REQ-MIG-053.4, REQ-MIG-058.1)
                record.developerFields?.forEach { devField ->
                    if (!matchesAppId(devField, devDataAppIds, FitFileWriter.RADAR_DEVELOPER_DATA_APP_ID) &&
                        !matchesAppId(devField, devDataAppIds, FitFileWriter.LEGACY_RADAR_DEVELOPER_DATA_APP_ID)
                    ) {
                        return@forEach
                    }
                    val fieldName = devField.name
                    val num = devField.num
                    if (fieldName == "radar_current" || num == 0) {
                        val count = devField.integerValue ?: (devField.value as? Number)?.toInt()
                        if (count != null) {
                            values.put(SensorType.RADAR_THREAT_COUNT.name, count)
                            foundSensors.add(SensorType.RADAR_THREAT_COUNT)
                        }
                    } else if (fieldName == "radar_total" || num == 1) {
                        val total = devField.integerValue ?: (devField.value as? Number)?.toInt()
                        if (total != null) {
                            values.put(SensorType.RADAR_VEHICLE_COUNT.name, total)
                            foundSensors.add(SensorType.RADAR_VEHICLE_COUNT)
                        }
                    } else if (fieldName == "passing_speed" || num == 2) {
                        val speed = devField.doubleValue ?: (devField.value as? Number)?.toDouble()
                        if (speed != null) {
                            values.put(SensorType.RADAR_APPROACH_SPEED_mps.name, speed)
                            foundSensors.add(SensorType.RADAR_APPROACH_SPEED_mps)
                        }
                    } else if (fieldName == "passing_distance" || num == 4) {
                        val dist = devField.doubleValue ?: (devField.value as? Number)?.toDouble()
                        if (dist != null) {
                            values.put(SensorType.RADAR_PASSING_DISTANCE_m.name, dist)
                            foundSensors.add(SensorType.RADAR_PASSING_DISTANCE_m)
                        }
                    } else if (fieldName == "large_vehicle_count" || num == 5) {
                        val large = devField.integerValue ?: (devField.value as? Number)?.toInt()
                        if (large != null) {
                            values.put(SensorType.RADAR_LARGE_VEHICLE_COUNT.name, large)
                            foundSensors.add(SensorType.RADAR_LARGE_VEHICLE_COUNT)
                        }
                    }
                }

                // Determine lapNr via precomputed numeric epoch boundaries (REQ-MIG-058.1)
                val sampleLapNr = resolveLapIndex(date.time, lapStartEpochs, canBinarySearchLaps)
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

            // Decode Garmin FIT V2.0 Developer Fields on SessionMesg (REQ-MIG-053.3 / ATT-7747)
            var metaWorkoutName: String? = null
            var metaWorkoutNotes: String? = null
            var metaEquipmentName: String? = null
            var metaMethod: String? = null
            var metaGoal: String? = null
            var metaIsCommute = false
            var metaIsTrainer = false
            var metaIsRace = false
            var metaIsEpic = false

            primarySession?.developerFields?.forEach { devField ->
                if (!matchesAppId(devField, devDataAppIds, FitFileWriter.APP_METADATA_DEVELOPER_DATA_ID)) {
                    return@forEach
                }
                val fieldName = devField.name
                val num = devField.num
                if (fieldName == "workout_name" || num == 0) {
                    metaWorkoutName = devField.stringValue?.takeIf { it.isNotBlank() }
                        ?: (devField.value as? String)?.takeIf { it.isNotBlank() }
                } else if (fieldName == "notes" || num == 1) {
                    metaWorkoutNotes = devField.stringValue?.takeIf { it.isNotBlank() }
                        ?: (devField.value as? String)?.takeIf { it.isNotBlank() }
                } else if (fieldName == "equipment_name" || num == 2) {
                    metaEquipmentName = devField.stringValue?.takeIf { it.isNotBlank() }
                        ?: (devField.value as? String)?.takeIf { it.isNotBlank() }
                } else if (fieldName == "method" || num == 3) {
                    metaMethod = devField.stringValue?.takeIf { it.isNotBlank() }
                        ?: (devField.value as? String)?.takeIf { it.isNotBlank() }
                } else if (fieldName == "goal" || num == 4) {
                    metaGoal = devField.stringValue?.takeIf { it.isNotBlank() }
                        ?: (devField.value as? String)?.takeIf { it.isNotBlank() }
                } else if (fieldName == "is_commute" || num == 5) {
                    val raw = devField.integerValue ?: (devField.value as? Number)?.toInt()
                    metaIsCommute = (raw ?: 0) > 0
                } else if (fieldName == "is_trainer" || num == 6) {
                    val raw = devField.integerValue ?: (devField.value as? Number)?.toInt()
                    metaIsTrainer = (raw ?: 0) > 0
                } else if (fieldName == "is_race" || num == 7) {
                    val raw = devField.integerValue ?: (devField.value as? Number)?.toInt()
                    metaIsRace = (raw ?: 0) > 0
                } else if (fieldName == "is_epic" || num == 8) {
                    val raw = devField.integerValue ?: (devField.value as? Number)?.toInt()
                    metaIsEpic = (raw ?: 0) > 0
                }
            }

            if (!metaIsTrainer) {
                if (primarySession?.subSport == com.garmin.fit.SubSport.INDOOR_CYCLING ||
                    primarySession?.subSport == com.garmin.fit.SubSport.TREADMILL ||
                    primarySession?.sport == com.garmin.fit.Sport.FITNESS_EQUIPMENT) {
                    metaIsTrainer = true
                }
            }

            Result.success(
                ParsedWorkoutData(
                    workoutName = metaWorkoutName,
                    workoutNotes = metaWorkoutNotes,
                    equipmentName = metaEquipmentName,
                    method = metaMethod,
                    goal = metaGoal,
                    isCommute = metaIsCommute,
                    isTrainer = metaIsTrainer,
                    isRace = metaIsRace,
                    isEpic = metaIsEpic,
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

    internal fun isNonNullSorted(lapStartEpochs: LongArray): Boolean {
        for (i in lapStartEpochs.indices) {
            if (lapStartEpochs[i] == Long.MIN_VALUE) return false
            if (i > 0 && lapStartEpochs[i] < lapStartEpochs[i - 1]) return false
        }
        return true
    }

    internal fun resolveLapIndex(
        recordEpochMs: Long,
        lapStartEpochs: LongArray,
        canBinarySearch: Boolean
    ): Long {
        if (lapStartEpochs.size <= 1) return 0L
        if (canBinarySearch) {
            var low = 0
            var high = lapStartEpochs.lastIndex
            var matched = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (lapStartEpochs[mid] <= recordEpochMs) {
                    matched = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return if (matched >= 0) matched.toLong() else 0L
        }
        for (i in lapStartEpochs.indices.reversed()) {
            val lapStart = lapStartEpochs[i]
            if (lapStart != Long.MIN_VALUE && recordEpochMs >= lapStart) {
                return i.toLong()
            }
        }
        return 0L
    }

    private fun extractValidAppId(rawAppId: Array<Byte?>?): ByteArray? {
        if (rawAppId == null || rawAppId.size != 16) return null
        val bytes = ByteArray(16)
        for (i in 0 until 16) {
            val b = rawAppId[i] ?: return null
            bytes[i] = b
        }
        return bytes
    }

    private fun matchesAppId(
        devField: DeveloperField,
        devDataAppIds: Map<Short, ByteArray>,
        expectedAppId: ByteArray
    ): Boolean {
        val devIndex = devField.developerDataIndex
        if (devIndex == Fit.UINT8_INVALID.toShort()) return false
        val resolvedAppId = extractValidAppId(devField.appId) ?: devDataAppIds[devIndex] ?: return false
        return resolvedAppId.contentEquals(expectedAppId)
    }
}
