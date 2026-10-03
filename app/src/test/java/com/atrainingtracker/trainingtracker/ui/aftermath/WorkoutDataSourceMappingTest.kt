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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.content.Context
import android.database.Cursor
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit tests verifying WorkoutSource provenance propagation between WorkoutData, WorkoutHeaderData, and WorkoutDataMapper (REQ-DAT-017, TST-DAT-012.2).
 */
class WorkoutDataSourceMappingTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaDb = mockk<StravaUploadDbHelper>(relaxed = true)

    private lateinit var mapper: WorkoutDataMapper

    private fun createWorkoutData(source: WorkoutSource): WorkoutData {
        return WorkoutData(
            id = 1L,
            finished = true,
            fileBaseName = "workout_1",
            workoutName = "Test Workout",
            sportId = 1L,
            sportName = "Cycling",
            bSportType = BSportType.BIKE,
            startTimeS = 1700000000L,
            formattedDate = "2026-10-03",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.now(),
            equipmentName = null,
            equipmentId = -1L,
            commute = false,
            trainer = false,
            race = false,
            source = source,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 25000.0,
            maxDisplacement = null,
            activeTimeSec = 3600L,
            totalTimeSec = 3600L,
            avgSpeedMps = 6.94,
            ascentMeters = 200L,
            descentMeters = 200L,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = "Ride"
        )
    }

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(LapsDatabaseManager::class)
        val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb
        every { mockLapsDb.getLaps(any()) } returns emptyList()

        every { mockSportDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportDb.getUIName(any()) } returns "Cycling"

        mapper = WorkoutDataMapper(
            context = mockContext,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaDb
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(LapsDatabaseManager::class)
        unmockkStatic(TrainingApplication::class)
        unmockkAll()
    }

    @Test
    fun testWorkoutData_defaultSourceIsTracked() {
        val workoutData = createWorkoutData(source = WorkoutSource.TRACKED)
        assertEquals(WorkoutSource.TRACKED, workoutData.source)
        assertEquals(WorkoutSource.TRACKED, workoutData.headerData.source)
    }

    @Test
    fun testWorkoutData_headerDataReflectsSource() {
        val workoutDataTcx = createWorkoutData(source = WorkoutSource.TCX)
        assertEquals(WorkoutSource.TCX, workoutDataTcx.source)
        assertEquals(WorkoutSource.TCX, workoutDataTcx.headerData.source)

        val workoutDataGpx = createWorkoutData(source = WorkoutSource.GPX)
        assertEquals(WorkoutSource.GPX, workoutDataGpx.source)
        assertEquals(WorkoutSource.GPX, workoutDataGpx.headerData.source)

        val workoutDataFit = createWorkoutData(source = WorkoutSource.FIT)
        assertEquals(WorkoutSource.FIT, workoutDataFit.source)
        assertEquals(WorkoutSource.FIT, workoutDataFit.headerData.source)
    }

    private fun createHeaderData(source: WorkoutSource = WorkoutSource.TRACKED): WorkoutHeaderData {
        return WorkoutHeaderData(
            workoutName = "Test",
            formattedDate = "2026-10-03",
            formattedTime = "10:00",
            startTimeS = 1700000000L,
            bSportType = BSportType.BIKE,
            sportName = "Cycling",
            equipmentName = null,
            commute = false,
            trainer = false,
            race = false,
            source = source,
            uploadToStrava = 0,
            finished = true
        )
    }

    @Test
    fun testWorkoutHeaderData_defaultSourceIsTracked() {
        val headerData = createHeaderData()
        assertEquals(WorkoutSource.TRACKED, headerData.source)
    }

    private fun createMockCursor(sourceString: String?): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)

        val columns: Map<String, Int> = mapOf(
            WorkoutSummaries.C_ID to 0,
            WorkoutSummaries.SPORT_ID to 1,
            WorkoutSummaries.EQUIPMENT_ID to 2,
            WorkoutSummaries.TIME_START to 3,
            WorkoutSummaries.FILE_BASE_NAME to 4,
            WorkoutSummaries.DISTANCE_TOTAL_m to 5,
            WorkoutSummaries.MAP_POLYLINE to 6,
            WorkoutSummaries.TIME_ACTIVE_s to 7,
            WorkoutSummaries.TIME_TOTAL_s to 8,
            WorkoutSummaries.SPEED_AVERAGE_mps to 9,
            WorkoutSummaries.ASCENDING to 10,
            WorkoutSummaries.DESCENDING to 11,
            WorkoutSummaries.ALTITUDE_STREAM to 12,
            WorkoutSummaries.DISTANCE_STREAM to 13,
            WorkoutSummaries.WORKOUT_NAME to 14,
            WorkoutSummaries.CLUSTER_ID to 15,
            WorkoutSummaries.FINISHED to 16,
            WorkoutSummaries.COMMUTE to 17,
            WorkoutSummaries.TRAINER to 18,
            WorkoutSummaries.UPLOAD_TO_STRAVA to 19,
            WorkoutSummaries.BOUND_MIN_LAT to 20,
            WorkoutSummaries.BOUND_MIN_LNG to 21,
            WorkoutSummaries.BOUND_MAX_LAT to 22,
            WorkoutSummaries.BOUND_MAX_LNG to 23,
            WorkoutSummaries.DESCRIPTION to 24,
            WorkoutSummaries.GOAL to 25,
            WorkoutSummaries.METHOD to 26,
            WorkoutSummaries.RACE to 27,
            WorkoutSummaries.SOURCE to 28
        )

        columns.forEach { (colName, index) ->
            every { cursor.getColumnIndexOrThrow(colName) } returns index
            every { cursor.getColumnIndex(colName) } returns index
        }

        every { cursor.getLong(0) } returns 101L
        every { cursor.getLong(1) } returns 1L
        every { cursor.getLong(2) } returns 0L
        every { cursor.getString(3) } returns "2026-10-03 10:00:00"
        every { cursor.getString(4) } returns "2026-10-03-10-00-00"
        every { cursor.getDouble(5) } returns 25000.0
        every { cursor.getString(6) } returns ""
        every { cursor.getLong(7) } returns 3600L
        every { cursor.getLong(8) } returns 3600L
        every { cursor.getDouble(9) } returns 6.94
        every { cursor.getLong(10) } returns 200L
        every { cursor.getLong(11) } returns 200L
        every { cursor.getString(12) } returns null
        every { cursor.getString(13) } returns null
        every { cursor.getString(14) } returns "Ride Session"
        every { cursor.getLong(15) } returns -1L
        every { cursor.getInt(16) } returns 1
        every { cursor.getInt(17) } returns 0
        every { cursor.getInt(18) } returns 0
        every { cursor.getInt(19) } returns 0
        every { cursor.getInt(27) } returns 0
        every { cursor.getString(28) } returns sourceString

        return cursor
    }

    @Test
    fun testWorkoutDataMapper_fromCursor_mapsValidSources() {
        val tcxCursor = createMockCursor("TCX")
        val tcxData = mapper.fromCursor(tcxCursor)
        assertEquals(WorkoutSource.TCX, tcxData.source)
        assertEquals(WorkoutSource.TCX, tcxData.headerData.source)

        val gpxCursor = createMockCursor("GPX")
        val gpxData = mapper.fromCursor(gpxCursor)
        assertEquals(WorkoutSource.GPX, gpxData.source)
        assertEquals(WorkoutSource.GPX, gpxData.headerData.source)

        val fitCursor = createMockCursor("FIT")
        val fitData = mapper.fromCursor(fitCursor)
        assertEquals(WorkoutSource.FIT, fitData.source)
        assertEquals(WorkoutSource.FIT, fitData.headerData.source)

        val trackedCursor = createMockCursor("TRACKED")
        val trackedData = mapper.fromCursor(trackedCursor)
        assertEquals(WorkoutSource.TRACKED, trackedData.source)
        assertEquals(WorkoutSource.TRACKED, trackedData.headerData.source)
    }

    @Test
    fun testWorkoutDataMapper_fromCursor_fallsBackToTrackedForNullOrInvalid() {
        val nullCursor = createMockCursor(null)
        val nullData = mapper.fromCursor(nullCursor)
        assertEquals(WorkoutSource.TRACKED, nullData.source)
        assertEquals(WorkoutSource.TRACKED, nullData.headerData.source)

        val invalidCursor = createMockCursor("UNKNOWN_SOURCE")
        val invalidData = mapper.fromCursor(invalidCursor)
        assertEquals(WorkoutSource.TRACKED, invalidData.source)
        assertEquals(WorkoutSource.TRACKED, invalidData.headerData.source)
    }
}
