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
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.MyUnits
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit test verifying WorkoutDataMapper laps mapping in single and batch modes (REQ-UI-141, TST-UI-094, ATT-510).
 */
class WorkoutDataMapperLapsTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaDb = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)
    private val mockClusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)

    private lateinit var mapper: WorkoutDataMapper

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        every { mockClusterDb.getClusterNameById(any()) } returns null

        every { mockSportDb.getBSportType(any()) } returns BSportType.RUN
        every { mockSportDb.getUIName(any()) } returns "Running"

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
        WorkoutClusterDatabaseManager.resetForTesting(null)
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
        every { cursor.getString(3) } returns "2026-09-11 10:00:00"
        every { cursor.getString(4) } returns "2026-09-11-10-00-00"
        every { cursor.getDouble(5) } returns 5000.0
        every { cursor.getString(6) } returns ""
        every { cursor.getLong(7) } returns 1500L
        every { cursor.getLong(8) } returns 1600L
        every { cursor.getDouble(9) } returns 3.33
        every { cursor.getLong(10) } returns 50L
        every { cursor.getLong(11) } returns 50L
        every { cursor.getString(12) } returns null
        every { cursor.getString(13) } returns null
        every { cursor.getString(14) } returns "Interval Run"
        every { cursor.getLong(15) } returns -1L
        every { cursor.getInt(16) } returns 1
        every { cursor.getInt(17) } returns 0
        every { cursor.getInt(18) } returns 0
        every { cursor.getInt(19) } returns 0
        every { cursor.isNull(20) } returns true
        every { cursor.isNull(21) } returns true
        every { cursor.isNull(22) } returns true
        every { cursor.isNull(23) } returns true
        every { cursor.getString(24) } returns null
        every { cursor.getString(25) } returns null
        every { cursor.getString(26) } returns null

        return cursor
    }

    @Test
    fun testFromCursor_single_queriesLapsDatabaseManager() {
        val workoutId = 123L
        val cursor = createMockCursor(workoutId)

        val sampleLap = LapData(
            id = 1,
            workoutId = workoutId,
            lapNr = 1,
            timeStart = "2026-09-11 10:00:00",
            timeTotalS = 300,
            distanceTotalM = 1000.0,
            speedAverageMps = 3.33,
            name = "Warmup",
            description = "Zone 2"
        )
        every { mockLapsDb.getLaps(workoutId) } returns listOf(sampleLap)

        val workoutData = mapper.fromCursor(cursor)

        assertNotNull(workoutData)
        assertEquals(1, workoutData.laps.size)
        assertEquals("Warmup", workoutData.laps[0].name)
        assertEquals("Zone 2", workoutData.laps[0].description)
        assertEquals(300, workoutData.laps[0].timeTotalS)
        verify(exactly = 1) { mockLapsDb.getLaps(workoutId) }
    }

    @Test
    fun testFromCursor_batch_mapsLapsFromBatchMetadataWithoutDatabaseQuery() {
        val workoutId = 456L
        val cursor = createMockCursor(workoutId)

        val lap1 = LapData(
            id = 1,
            workoutId = workoutId,
            lapNr = 1,
            timeStart = "2026-09-11 10:00:00",
            timeTotalS = 240,
            distanceTotalM = 800.0,
            speedAverageMps = 3.33
        )
        val lap2 = LapData(
            id = 2,
            workoutId = workoutId,
            lapNr = 2,
            timeStart = "2026-09-11 10:04:00",
            timeTotalS = 230,
            distanceTotalM = 800.0,
            speedAverageMps = 3.48
        )

        val batch = WorkoutDataMapper.BatchMetadata(
            extrema = emptyMap(),
            stravaData = emptyMap(),
            clusterNames = emptyMap(),
            laps = mapOf(workoutId to listOf(lap1, lap2))
        )

        val workoutData = mapper.fromCursor(cursor, batch)

        assertNotNull(workoutData)
        assertEquals(2, workoutData.laps.size)
        assertEquals(lap1, workoutData.laps[0])
        assertEquals(lap2, workoutData.laps[1])

        // Verify zero queries to LapsDatabaseManager during batch processing (N+1 protection)
        verify(exactly = 0) { mockLapsDb.getLaps(any()) }
    }

    @Test
    fun testFromCursor_batch_workoutWithoutLaps_defaultsToEmptyList() {
        val workoutId = 789L
        val cursor = createMockCursor(workoutId)

        val batch = WorkoutDataMapper.BatchMetadata(
            extrema = emptyMap(),
            stravaData = emptyMap(),
            clusterNames = emptyMap(),
            laps = emptyMap()
        )

        val workoutData = mapper.fromCursor(cursor, batch)

        assertNotNull(workoutData)
        assertTrue("Laps should be empty when not present in batch metadata", workoutData.laps.isEmpty())
        verify(exactly = 0) { mockLapsDb.getLaps(any()) }
    }
}
