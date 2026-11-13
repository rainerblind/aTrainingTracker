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
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Velocity
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Transforms [WorkoutData] and per-second sample records from [WorkoutSamplesRepository]
 * into standardized Android Health Connect records (REQ-SET-101, REQ-SET-102).
 */
object HealthConnectRecordMapper {

    private const val MAX_CHUNK_SIZE = 2000
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
        var endTime = startTime.plusSeconds(durationSec)
        val maxRouteTime = route?.route?.maxOfOrNull { it.time }
        if (maxRouteTime != null && !maxRouteTime.isBefore(endTime)) {
            endTime = maxRouteTime.plusSeconds(1)
        }
        val zoneOffset = ZoneId.systemDefault().rules.getOffset(startTime)

        val clientRecordId = "workout_${workoutData.fileBaseName ?: workoutData.id}"
        val metadata = createMetadata(clientRecordId)

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
     * Synchronous convenience overload for [extractRecords].
     */
    fun extractRecords(
        context: Context,
        workoutData: WorkoutData
    ): Pair<ExerciseSessionRecord, List<Record>> = runBlocking {
        extractRecords(context, workoutData, hasRoutePermission = true)
    }

    /**
     * Reads per-second samples for the workout via [WorkoutSamplesRepository] and constructs
     * telemetry series records. Route points are attached to [ExerciseRoute] only when
     * [hasRoutePermission] is true.
     */
    suspend fun extractRecords(
        context: Context,
        workoutData: WorkoutData,
        hasRoutePermission: Boolean = true,
        repository: WorkoutSamplesRepository = WorkoutSamplesRepository.getInstance(context)
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
        val cyclingCadenceSamples = mutableListOf<CyclingPedalingCadenceRecord.Sample>()
        val stepsCadenceSamples = mutableListOf<StepsCadenceRecord.Sample>()

        val fileBaseName = workoutData.fileBaseName
        if (!fileBaseName.isNullOrBlank()) {
            val samples = repository.getSamples(fileBaseName)
            for (sample in samples) {
                val sampleTime = parseSampleTime(sample.timeString, startTime, sample.sampleIdx)
                if (sampleTime.isBefore(startTime) || sampleTime.isAfter(startTime.plusSeconds(maxOf(durationSec, 86400L * 7)))) {
                    continue
                }

                // GPS Route (only for non-trainer outdoor sessions when route permission is granted)
                if (hasRoutePermission && !workoutData.trainer) {
                    val lat = sample.latitude
                    val lon = sample.longitude
                    if (lat != null && lon != null && !lat.isNaN() && !lon.isNaN() &&
                        lat >= -90.0 && lat <= 90.0 && lon >= -180.0 && lon <= 180.0 &&
                        (lat != 0.0 || lon != 0.0)
                    ) {
                        val alt = sample.altitude?.takeIf { !it.isNaN() && it in -500.0..9000.0 }?.let { Length.meters(it) }
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
                sample.heartRate?.takeIf { it in 1..299 }?.let { hr ->
                    hrSamples.add(HeartRateRecord.Sample(time = sampleTime, beatsPerMinute = hr.toLong()))
                }

                // Power
                sample.powerWatts?.takeIf { !it.isNaN() && it in 0.0..4000.0 }?.let { pwr ->
                    powerSamples.add(PowerRecord.Sample(time = sampleTime, power = Power.watts(pwr)))
                }

                // Speed
                sample.speedMps?.takeIf { !it.isNaN() && it in 0.0..150.0 }?.let { spd ->
                    speedSamples.add(SpeedRecord.Sample(time = sampleTime, speed = Velocity.metersPerSecond(spd)))
                }

                // Cadence
                sample.cadenceRpm?.takeIf { !it.isNaN() && it in 0.0..300.0 }?.let { cad ->
                    if (workoutData.bSportType == BSportType.BIKE) {
                        cyclingCadenceSamples.add(
                            CyclingPedalingCadenceRecord.Sample(time = sampleTime, revolutionsPerMinute = cad)
                        )
                    } else if (workoutData.bSportType == BSportType.RUN) {
                        stepsCadenceSamples.add(
                            StepsCadenceRecord.Sample(time = sampleTime, rate = cad)
                        )
                    }
                }
            }
        }

        val distinctRouteLocations = routeLocations.distinctBy { it.time }.sortedBy { it.time }
        val route = if (hasRoutePermission && distinctRouteLocations.isNotEmpty()) {
            ExerciseRoute(distinctRouteLocations)
        } else {
            null
        }

        val sessionRecord = buildExerciseSessionRecord(workoutData, route)
        val effectiveEndTime = sessionRecord.endTime
        val seriesRecords = mutableListOf<Record>()

        // Series records partitioned into chunks of <= 2,000 samples (REQ-SET-101.3)
        val distinctHrSamples = hrSamples.distinctBy { it.time }.sortedBy { it.time }
        addChunkedSeries(
            samples = distinctHrSamples,
            chunkSize = MAX_CHUNK_SIZE,
            target = seriesRecords
        ) { chunk, chunkStart, chunkEnd, idSuffix ->
            HeartRateRecord(
                startTime = chunkStart,
                startZoneOffset = zoneOffset,
                endTime = chunkEnd,
                endZoneOffset = zoneOffset,
                samples = chunk,
                metadata = createMetadata("${baseId}_hr$idSuffix")
            )
        }

        val distinctPowerSamples = powerSamples.distinctBy { it.time }.sortedBy { it.time }
        addChunkedSeries(
            samples = distinctPowerSamples,
            chunkSize = MAX_CHUNK_SIZE,
            target = seriesRecords
        ) { chunk, chunkStart, chunkEnd, idSuffix ->
            PowerRecord(
                startTime = chunkStart,
                startZoneOffset = zoneOffset,
                endTime = chunkEnd,
                endZoneOffset = zoneOffset,
                samples = chunk,
                metadata = createMetadata("${baseId}_power$idSuffix")
            )
        }

        val distinctSpeedSamples = speedSamples.distinctBy { it.time }.sortedBy { it.time }
        addChunkedSeries(
            samples = distinctSpeedSamples,
            chunkSize = MAX_CHUNK_SIZE,
            target = seriesRecords
        ) { chunk, chunkStart, chunkEnd, idSuffix ->
            SpeedRecord(
                startTime = chunkStart,
                startZoneOffset = zoneOffset,
                endTime = chunkEnd,
                endZoneOffset = zoneOffset,
                samples = chunk,
                metadata = createMetadata("${baseId}_speed$idSuffix")
            )
        }

        val distinctCyclingCadenceSamples = cyclingCadenceSamples.distinctBy { it.time }.sortedBy { it.time }
        addChunkedSeries(
            samples = distinctCyclingCadenceSamples,
            chunkSize = MAX_CHUNK_SIZE,
            target = seriesRecords
        ) { chunk, chunkStart, chunkEnd, idSuffix ->
            CyclingPedalingCadenceRecord(
                startTime = chunkStart,
                startZoneOffset = zoneOffset,
                endTime = chunkEnd,
                endZoneOffset = zoneOffset,
                samples = chunk,
                metadata = createMetadata("${baseId}_cadence$idSuffix")
            )
        }

        val distinctStepsCadenceSamples = stepsCadenceSamples.distinctBy { it.time }.sortedBy { it.time }
        addChunkedSeries(
            samples = distinctStepsCadenceSamples,
            chunkSize = MAX_CHUNK_SIZE,
            target = seriesRecords
        ) { chunk, chunkStart, chunkEnd, idSuffix ->
            StepsCadenceRecord(
                startTime = chunkStart,
                startZoneOffset = zoneOffset,
                endTime = chunkEnd,
                endZoneOffset = zoneOffset,
                samples = chunk,
                metadata = createMetadata("${baseId}_step_cadence$idSuffix")
            )
        }

        // Distance record
        if (workoutData.totalDistance > 0.0) {
            seriesRecords.add(
                DistanceRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = effectiveEndTime,
                    endZoneOffset = zoneOffset,
                    distance = Length.meters(workoutData.totalDistance),
                    metadata = createMetadata("${baseId}_distance")
                )
            )
        }

        // Elevation gained record
        if (workoutData.ascentMeters > 0) {
            seriesRecords.add(
                ElevationGainedRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = effectiveEndTime,
                    endZoneOffset = zoneOffset,
                    elevation = Length.meters(workoutData.ascentMeters.toDouble()),
                    metadata = createMetadata("${baseId}_elevation")
                )
            )
        }

        // Total calories burned record (numeric primitive from WorkoutData, REQ-SET-101.2)
        val calories = extractCalories(workoutData)
        if (calories > 0.0) {
            seriesRecords.add(
                TotalCaloriesBurnedRecord(
                    startTime = startTime,
                    startZoneOffset = zoneOffset,
                    endTime = effectiveEndTime,
                    endZoneOffset = zoneOffset,
                    energy = Energy.kilocalories(calories),
                    metadata = createMetadata("${baseId}_calories")
                )
            )
        }

        return Pair(sessionRecord, seriesRecords)
    }

    private fun createMetadata(clientRecordId: String): Metadata {
        return Metadata.activelyRecorded(
            device = Device(type = Device.TYPE_PHONE),
            clientRecordId = clientRecordId
        )
    }

    private fun <T> addChunkedSeries(
        samples: List<T>,
        chunkSize: Int,
        target: MutableList<Record>,
        factory: (chunk: List<T>, chunkStart: Instant, chunkEnd: Instant, idSuffix: String) -> Record
    ) where T : Any {
        if (samples.isEmpty()) return
        val chunks = samples.chunked(chunkSize)
        chunks.forEachIndexed { idx, chunk ->
            val chunkStart = getSampleTime(chunk.first())
            val lastTime = getSampleTime(chunk.last())
            val chunkEnd = if (lastTime > chunkStart) lastTime else chunkStart.plusSeconds(1)
            val suffix = if (chunks.size == 1) "" else "_$idx"
            target.add(factory(chunk, chunkStart, chunkEnd, suffix))
        }
    }

    private fun getSampleTime(sample: Any): Instant {
        return when (sample) {
            is HeartRateRecord.Sample -> sample.time
            is PowerRecord.Sample -> sample.time
            is SpeedRecord.Sample -> sample.time
            is CyclingPedalingCadenceRecord.Sample -> sample.time
            is StepsCadenceRecord.Sample -> sample.time
            else -> Instant.now()
        }
    }

    private fun parseSampleTime(timeStr: String?, fallbackStartTime: Instant, sampleIdx: Long): Instant {
        if (!timeStr.isNullOrBlank()) {
            try {
                val ldt = LocalDateTime.parse(timeStr.trim(), DATE_TIME_FORMATTER)
                return ldt.toInstant(ZoneOffset.UTC)
            } catch (ignored: DateTimeParseException) {
            }
        }
        return fallbackStartTime.plusSeconds(sampleIdx)
    }

    private fun extractCalories(workoutData: WorkoutData): Double {
        workoutData.calories?.takeIf { it > 0 }?.let { return it.toDouble() }
        for (row in workoutData.extremaRows) {
            if (row.unitLabel.contains("kcal", ignoreCase = true) || row.sensorLabel.contains("cal", ignoreCase = true)) {
                val str = row.maxValue ?: row.avgValue
                str?.toDoubleOrNull()?.let { return it }
            }
        }
        return 0.0
    }
}
