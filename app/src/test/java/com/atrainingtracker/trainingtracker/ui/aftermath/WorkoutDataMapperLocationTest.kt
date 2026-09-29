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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager

import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying dynamic favorite start and destination location resolution
 * in [WorkoutDataMapper] (ATT-1400 / REQ-UI-184 / TST-UI-137.1).
 */
class WorkoutDataMapperLocationTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaDb = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockKnownLocationsDb = mockk<KnownLocationsDatabaseManager>(relaxed = true)
    private val mockClusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
    private val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)

    private lateinit var mapper: WorkoutDataMapper

    private val startPos = LatLng(48.137, 11.576)
    private val endPos = LatLng(48.140, 11.580)

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb
        every { mockLapsDb.getLaps(any()) } returns emptyList()

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        every { mockClusterDb.getClusterNameById(any()) } returns null

        every { mockSportDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportDb.getUIName(any()) } returns "Cycling"

        mapper = WorkoutDataMapper(
            context = mockContext,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaDb,
            knownLocationsDatabaseManager = mockKnownLocationsDb
        )
    }

    @After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        unmockkStatic(LapsDatabaseManager::class)
        unmockkStatic(TrainingApplication::class)
        unmockkAll()
    }


    private fun createMockCursor(workoutId: Long): Cursor {
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
            WorkoutSummaries.METHOD to 26
        )

        columns.forEach { (colName, index) ->
            every { cursor.getColumnIndexOrThrow(colName) } returns index
            every { cursor.getColumnIndex(colName) } returns index
        }

        every { cursor.getLong(0) } returns workoutId
        every { cursor.getLong(1) } returns 1L
        every { cursor.getLong(2) } returns 0L
        every { cursor.getString(3) } returns "2026-09-29 10:00:00"
        every { cursor.getString(4) } returns "2026-09-29-10-00-00"
        every { cursor.getDouble(5) } returns 15000.0
        every { cursor.getString(6) } returns ""
        every { cursor.getLong(7) } returns 1800L
        every { cursor.getLong(8) } returns 2000L
        every { cursor.getDouble(9) } returns 8.33
        every { cursor.getLong(10) } returns 150L
        every { cursor.getLong(11) } returns 150L
        every { cursor.getString(12) } returns ""
        every { cursor.getString(13) } returns ""
        every { cursor.getString(14) } returns "Morning Ride"
        every { cursor.getLong(15) } returns -1L
        every { cursor.getInt(16) } returns 1
        every { cursor.getInt(17) } returns 0
        every { cursor.getInt(18) } returns 0
        every { cursor.getInt(19) } returns 0
        every { cursor.isNull(any()) } returns true
        every { cursor.isNull(0) } returns false
        every { cursor.isNull(1) } returns false
        every { cursor.isNull(5) } returns false
        every { cursor.isNull(7) } returns false
        every { cursor.isNull(8) } returns false
        every { cursor.isNull(9) } returns false
        every { cursor.isNull(14) } returns false
        every { cursor.isNull(15) } returns false
        every { cursor.isNull(16) } returns false
        every { cursor.isNull(17) } returns false
        every { cursor.isNull(18) } returns false
        every { cursor.isNull(19) } returns false

        return cursor
    }

    @Test
    fun testFromCursor_pointToPoint_resolvesBothStartAndDestination() {
        val workoutId = 100L
        val cursor = createMockCursor(workoutId)

        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns startPos
        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns endPos

        val startLoc = KnownLocationsDatabaseManager.MyLocation(1L, startPos.latitude, startPos.longitude, "Zuhause", 520.0, 200, 1)
        val endLoc = KnownLocationsDatabaseManager.MyLocation(2L, endPos.latitude, endPos.longitude, "Büro", 515.0, 200, 1)

        every { mockKnownLocationsDb.getMyLocation(startPos) } returns startLoc
        every { mockKnownLocationsDb.getMyLocation(endPos) } returns endLoc

        val workoutData = mapper.fromCursor(cursor)

        assertEquals("Zuhause", workoutData.startLocationName)
        assertEquals("Büro", workoutData.endLocationName)

        // Verify direct headerData delegation
        assertEquals("Zuhause", workoutData.headerData.startLocationName)
        assertEquals("Büro", workoutData.headerData.endLocationName)
    }

    @Test
    fun testFromCursor_roundTrip_resolvesSameLocationForBothEndpoints() {
        val workoutId = 101L
        val cursor = createMockCursor(workoutId)

        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns startPos
        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns startPos

        val startLoc = KnownLocationsDatabaseManager.MyLocation(1L, startPos.latitude, startPos.longitude, "Zuhause", 520.0, 200, 1)

        every { mockKnownLocationsDb.getMyLocation(startPos) } returns startLoc

        val workoutData = mapper.fromCursor(cursor)

        assertEquals("Zuhause", workoutData.startLocationName)
        assertEquals("Zuhause", workoutData.endLocationName)
        assertEquals(workoutData.startLocationName, workoutData.endLocationName)
        assertEquals("Zuhause", workoutData.headerData.startLocationName)
        assertEquals("Zuhause", workoutData.headerData.endLocationName)
    }

    @Test
    fun testFromCursor_singleEndpoint_resolvesOnlyRecognizedStart() {
        val workoutId = 102L
        val cursor = createMockCursor(workoutId)

        val outsidePos = LatLng(49.000, 12.000)
        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns startPos
        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns outsidePos

        val startLoc = KnownLocationsDatabaseManager.MyLocation(1L, startPos.latitude, startPos.longitude, "Zuhause", 520.0, 200, 1)

        every { mockKnownLocationsDb.getMyLocation(startPos) } returns startLoc
        every { mockKnownLocationsDb.getMyLocation(outsidePos) } returns null

        val workoutData = mapper.fromCursor(cursor)

        assertEquals("Zuhause", workoutData.startLocationName)
        assertNull(workoutData.endLocationName)
        assertEquals("Zuhause", workoutData.headerData.startLocationName)
        assertNull(workoutData.headerData.endLocationName)
    }

    @Test
    fun testFromCursor_outsideGeofencesOrNullCoordinates_resolvesToNull() {
        val workoutId = 103L
        val cursor = createMockCursor(workoutId)

        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns null
        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns null

        val workoutData = mapper.fromCursor(cursor)

        assertNull(workoutData.startLocationName)
        assertNull(workoutData.endLocationName)
        assertNull(workoutData.headerData.startLocationName)
        assertNull(workoutData.headerData.endLocationName)
    }

    @Test
    fun testFromCursor_nullDatabaseManager_safelyReturnsNullWithoutCrashing() {
        val workoutId = 104L
        val cursor = createMockCursor(workoutId)

        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.START) } returns startPos
        every { mockSummariesDb.getExtremaPosition(workoutId, SensorType.LATITUDE, ExtremaType.END) } returns endPos

        val nullDbMapper = WorkoutDataMapper(
            context = mockContext,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaDb,
            knownLocationsDatabaseManager = null
        )

        val workoutData = nullDbMapper.fromCursor(cursor)

        assertNull(workoutData.startLocationName)
        assertNull(workoutData.endLocationName)
    }
}
