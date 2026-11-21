/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.content.Context
import android.database.MatrixCursor
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying:
 * - TST-UNT-009 (REQ-UI-011): WorkoutDataMapper limits core extrema rows to the "Big 6" metrics
 *   (HR, Speed/Pace, Cadence, Power, Altitude, Temperature).
 * - TST-UI-027 (REQ-UI-014): Authoritative average speed harmonization (distance / activeTimeSec).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutExtremaBigSixMapperTest {

    private lateinit var context: Context
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaDb = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)
    private val mockClusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC
        every { TrainingApplication.getAppContext() } returns context

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        every { mockClusterDb.getClusterNameById(any()) } returns null

        every { mockSportDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportDb.getUIName(any()) } returns "Road Cycling"
        every { mockSummariesDb.getExtremaValue(any(), any(), any()) } returns null

        SettingsDataStore.setInstanceForTesting(mockk(relaxed = true))
    }

    @After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        SettingsDataStore.setInstanceForTesting(null)
        unmockkAll()
    }

    @Test
    fun fromCursor_mapsBigSixExtremaMetricsAndHarmonizedAverageSpeed_TST_UNT_009_TST_UI_027() {
        // TST-UNT-009, TST-UI-027
        val workoutId = 99L
        val distanceM = 100.0
        val activeSec = 100L
        val avgSpeedMps = distanceM / activeSec.toDouble() // 1.0 m/s

        // Populate extrema values for Big 6 + unrelated non-Big-6 metrics (TORQUE, DISTANCE_m)
        every { mockSummariesDb.getExtremaForWorkouts(listOf(workoutId)) } returns listOf(
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.POWER, ExtremaType.AVG, 210.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.POWER, ExtremaType.MAX, 450.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.HR, ExtremaType.MIN, 95.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.HR, ExtremaType.AVG, 145.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.HR, ExtremaType.MAX, 182.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.SPEED_mps, ExtremaType.AVG, avgSpeedMps, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.SPEED_mps, ExtremaType.MAX, 5.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.CADENCE, ExtremaType.AVG, 85.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.CADENCE, ExtremaType.MAX, 110.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.ALTITUDE, ExtremaType.MIN, 420.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.ALTITUDE, ExtremaType.AVG, 480.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.ALTITUDE, ExtremaType.MAX, 540.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.TEMPERATURE, ExtremaType.MIN, 12.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.TEMPERATURE, ExtremaType.AVG, 15.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.TEMPERATURE, ExtremaType.MAX, 19.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.TORQUE, ExtremaType.AVG, 40.0, null),
            WorkoutSummariesDatabaseManager.ExtremaRecord(workoutId, SensorType.DISTANCE_m, ExtremaType.MAX, distanceM, null)
        )

        val cursor = MatrixCursor(
            arrayOf(
                WorkoutSummaries.C_ID,
                WorkoutSummaries.SPORT_ID,
                WorkoutSummaries.EQUIPMENT_ID,
                WorkoutSummaries.TIME_START,
                WorkoutSummaries.FILE_BASE_NAME,
                WorkoutSummaries.DISTANCE_TOTAL_m,
                WorkoutSummaries.MAP_POLYLINE,
                WorkoutSummaries.ALTITUDE_STREAM,
                WorkoutSummaries.DISTANCE_STREAM,
                WorkoutSummaries.WORKOUT_NAME,
                WorkoutSummaries.CLUSTER_ID,
                WorkoutSummaries.TIME_ACTIVE_s,
                WorkoutSummaries.SPEED_AVERAGE_mps,
                WorkoutSummaries.FINISHED,
                WorkoutSummaries.COMMUTE,
                WorkoutSummaries.TRAINER,
                WorkoutSummaries.UPLOAD_TO_STRAVA,
                WorkoutSummaries.TIME_TOTAL_s,
                WorkoutSummaries.ASCENDING,
                WorkoutSummaries.DESCENDING,
                WorkoutSummaries.DESCRIPTION,
                WorkoutSummaries.GOAL,
                WorkoutSummaries.METHOD,
                WorkoutSummaries.BOUND_MIN_LAT,
                WorkoutSummaries.BOUND_MIN_LNG,
                WorkoutSummaries.BOUND_MAX_LAT,
                WorkoutSummaries.BOUND_MAX_LNG
            )
        )
        cursor.addRow(
            arrayOf<Any?>(
                workoutId,
                1L,
                -1L,
                "2026-11-20 08:00:00",
                "workout_99",
                distanceM,
                "",
                "",
                "",
                "Test Track",
                -1L,
                activeSec,
                avgSpeedMps,
                1,
                0,
                0,
                0,
                100L,
                10L,
                10L,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            )
        )
        assertTrue(cursor.moveToFirst())

        val mapper = WorkoutDataMapper(
            context = context,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaDb
        )
        val data = mapper.fromCursor(cursor)
        cursor.close()

        // TST-UI-027: authoritative average speed matches distance / activeTimeSec
        assertEquals(1.0, data.avgSpeedMps, 1e-6)

        // TST-UNT-009: Big 6 labels only (Power, HR, Speed, Cadence, Altitude, Temp)
        val expectedLabels = setOf(
            context.getString(SensorType.POWER.shortNameId),
            context.getString(SensorType.HR.shortNameId),
            context.getString(SensorType.SPEED_mps.shortNameId),
            context.getString(SensorType.CADENCE.shortNameId),
            context.getString(SensorType.ALTITUDE.shortNameId),
            context.getString(SensorType.TEMPERATURE.shortNameId)
        )
        val actualLabels = data.extremaRows.map { it.sensorLabel }.toSet()
        assertEquals(expectedLabels, actualLabels)
        assertFalse(actualLabels.contains(context.getString(SensorType.TORQUE.shortNameId)))
        assertFalse(actualLabels.contains(context.getString(SensorType.DISTANCE_m.shortNameId)))
    }
}
