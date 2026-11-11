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
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesRepository
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.database.WorkoutTelemetrySample
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDateTime

/**
 * Unit test suite verifying mapping between [WorkoutData] and Health Connect records,
 * sport type translation, and trackless indoor session parity (REQ-SET-101, REQ-SET-102, TST-SET-101, TST-SET-102).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HealthConnectRecordMapperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createWorkoutData(
        bSportType: BSportType = BSportType.BIKE,
        trainer: Boolean = false,
        distanceMeters: Double = 10000.0,
        ascentMeters: Long = 150L,
        calories: Double = 350.0,
        totalTimeSec: Long = 2000L
    ): WorkoutData {
        val testLdt = LocalDateTime.of(2026, 11, 10, 10, 0)
        val startTime = testLdt.toEpochSecond(java.time.ZoneOffset.UTC)
        return WorkoutData(
            id = 42L,
            finished = true,
            fileBaseName = "2026-11-10-100000",
            workoutName = "Morning Ride",
            sportId = 1L,
            sportName = "Bike",
            bSportType = bSportType,
            startTimeS = startTime,
            formattedDate = "10.11.2026",
            formattedTime = "10:00",
            localDateTime = testLdt,
            equipmentName = "Road Bike",
            equipmentId = 10L,
            commute = false,
            trainer = trainer,
            source = WorkoutSource.TRACKED,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 1,
            totalDistance = distanceMeters,
            maxDisplacement = 5000.0,
            activeTimeSec = 1800L,
            totalTimeSec = totalTimeSec,
            avgSpeedMps = 5.0,
            ascentMeters = ascentMeters,
            descentMeters = 140L,
            minAltitude = 200.0,
            maxAltitude = 350.0,
            description = "Test description",
            goal = null,
            method = null,
            calories = calories.toInt(),
            stravaSportName = "Ride",
            extremaRows = listOf(
                ExtremaDataRow(
                    sensorLabel = "Calories",
                    unitLabel = "kcal",
                    minValue = null,
                    avgValue = calories.toString(),
                    maxValue = calories.toString()
                )
            )
        )
    }

    @Test
    fun testSportTypeToExerciseTypeMapping() {
        // Outdoor sports
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            HealthConnectRecordMapper.mapSportTypeToExerciseType(BSportType.BIKE, isTrainer = false)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            HealthConnectRecordMapper.mapSportTypeToExerciseType(BSportType.RUN, isTrainer = false)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            HealthConnectRecordMapper.mapSportTypeToExerciseType(BSportType.UNKNOWN, isTrainer = false)
        )

        // Indoor trainer sports (REQ-SET-102)
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            HealthConnectRecordMapper.mapSportTypeToExerciseType(BSportType.BIKE, isTrainer = true)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            HealthConnectRecordMapper.mapSportTypeToExerciseType(BSportType.RUN, isTrainer = true)
        )
    }

    @Test
    fun testExerciseSessionRecordConstruction() {
        val workout = createWorkoutData()
        val record = HealthConnectRecordMapper.buildExerciseSessionRecord(workout)

        assertEquals("Morning Ride", record.title)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, record.exerciseType)
        assertEquals("workout_2026-11-10-100000", record.metadata.clientRecordId)
        val expectedStart = LocalDateTime.of(2026, 11, 10, 10, 0).toInstant(java.time.ZoneOffset.UTC)
        assertEquals(expectedStart, record.startTime)
        assertEquals(expectedStart.plusSeconds(2000L), record.endTime)
    }

    @Test
    fun testTracklessIndoorSessionExtractionParity() {
        // REQ-SET-102: Indoor workout without GPS
        val indoorWorkout = createWorkoutData(
            bSportType = BSportType.BIKE,
            trainer = true,
            distanceMeters = 15000.0,
            ascentMeters = 0L,
            calories = 450.0
        )

        val (sessionRecord, seriesRecords) = HealthConnectRecordMapper.extractRecords(context, indoorWorkout)

        assertNotNull(sessionRecord)
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY, sessionRecord.exerciseType)
        // REQ-SET-102: Indoor trackless workout cleanly omits route data
        assertTrue(sessionRecord.exerciseRouteResult == null || sessionRecord.exerciseRouteResult is ExerciseRouteResult.NoData)

        // Non-spatial records (distance, calories) are extracted without throwing exceptions
        val hasDistance = seriesRecords.any { it is DistanceRecord }
        assertTrue(hasDistance)

        val hasElevation = seriesRecords.any { it is ElevationGainedRecord }
        assertEquals(false, hasElevation) // Ascent was 0
    }

    @Test
    fun testTypedCaloriesExtractionFromWorkoutData() = runBlocking {
        val workout = createWorkoutData(calories = 540.0)
        val (_, seriesRecords) = HealthConnectRecordMapper.extractRecords(context, workout)
        val calorieRecord = seriesRecords.filterIsInstance<TotalCaloriesBurnedRecord>().firstOrNull()

        assertNotNull(calorieRecord)
        assertEquals(540.0, calorieRecord!!.energy.inKilocalories, 0.01)
        assertEquals("workout_2026-11-10-100000_calories", calorieRecord.metadata.clientRecordId)
    }

    @Test
    fun testRoutePermissionGatingWhenRouteDenied() = runBlocking {
        val mockRepo = mockk<WorkoutSamplesRepository>()
        coEvery { mockRepo.getSamples(any()) } returns listOf(
            WorkoutTelemetrySample(
                sampleIdx = 0L,
                timeString = "2026-11-10 10:00:01",
                latitude = 48.137,
                longitude = 11.575,
                altitude = 520.0
            )
        )

        val workout = createWorkoutData(trainer = false)

        // When route permission is granted
        val (sessionWithRoute, _) = HealthConnectRecordMapper.extractRecords(
            context = context,
            workoutData = workout,
            hasRoutePermission = true,
            repository = mockRepo
        )
        val routeResult = sessionWithRoute.exerciseRouteResult
        assertTrue(routeResult is ExerciseRouteResult.Data)
        assertNotNull((routeResult as ExerciseRouteResult.Data).exerciseRoute)

        // When route permission is denied (REQ-SET-101.2)
        val (sessionWithoutRoute, _) = HealthConnectRecordMapper.extractRecords(
            context = context,
            workoutData = workout,
            hasRoutePermission = false,
            repository = mockRepo
        )
        assertTrue(sessionWithoutRoute.exerciseRouteResult is ExerciseRouteResult.NoData)
    }

    @Test
    fun testCadenceSeriesMappingForBikeAndRun() = runBlocking {
        val mockRepo = mockk<WorkoutSamplesRepository>()
        coEvery { mockRepo.getSamples(any()) } returns listOf(
            WorkoutTelemetrySample(
                sampleIdx = 0L,
                timeString = "2026-11-10 10:00:01",
                cadenceRpm = 85.0
            ),
            WorkoutTelemetrySample(
                sampleIdx = 1L,
                timeString = "2026-11-10 10:00:02",
                cadenceRpm = 88.0
            )
        )

        // Bike workout maps to CyclingPedalingCadenceRecord
        val bikeWorkout = createWorkoutData(bSportType = BSportType.BIKE)
        val (_, bikeSeries) = HealthConnectRecordMapper.extractRecords(
            context = context,
            workoutData = bikeWorkout,
            repository = mockRepo
        )
        val cyclingCadence = bikeSeries.filterIsInstance<CyclingPedalingCadenceRecord>().firstOrNull()
        assertNotNull(cyclingCadence)
        assertEquals(2, cyclingCadence!!.samples.size)
        assertEquals(85.0, cyclingCadence.samples[0].revolutionsPerMinute, 0.01)

        // Run workout maps to StepsCadenceRecord
        val runWorkout = createWorkoutData(bSportType = BSportType.RUN)
        val (_, runSeries) = HealthConnectRecordMapper.extractRecords(
            context = context,
            workoutData = runWorkout,
            repository = mockRepo
        )
        val stepsCadence = runSeries.filterIsInstance<StepsCadenceRecord>().firstOrNull()
        assertNotNull(stepsCadence)
        assertEquals(2, stepsCadence!!.samples.size)
        assertEquals(85.0, stepsCadence.samples[0].rate, 0.01)
    }

    @Test
    fun testSeriesSampleChunkingAt2000Bound() = runBlocking {
        val mockRepo = mockk<WorkoutSamplesRepository>()
        // Generate 4,500 samples across duration
        val samples = (0 until 4500L).map { idx ->
            WorkoutTelemetrySample(
                sampleIdx = idx,
                timeString = null, // Will use fallbackStartTime.plusSeconds(idx)
                heartRate = 140
            )
        }
        coEvery { mockRepo.getSamples(any()) } returns samples

        val workout = createWorkoutData(totalTimeSec = 5000L)
        val (_, seriesRecords) = HealthConnectRecordMapper.extractRecords(
            context = context,
            workoutData = workout,
            repository = mockRepo
        )

        val hrRecords = seriesRecords.filterIsInstance<HeartRateRecord>()
        // 4,500 samples chunked into <= 2,000 gives 3 records (2000, 2000, 500)
        assertEquals(3, hrRecords.size)
        assertEquals(2000, hrRecords[0].samples.size)
        assertEquals(2000, hrRecords[1].samples.size)
        assertEquals(500, hrRecords[2].samples.size)

        assertEquals("workout_2026-11-10-100000_hr_0", hrRecords[0].metadata.clientRecordId)
        assertEquals("workout_2026-11-10-100000_hr_1", hrRecords[1].metadata.clientRecordId)
        assertEquals("workout_2026-11-10-100000_hr_2", hrRecords[2].metadata.clientRecordId)
    }
}
