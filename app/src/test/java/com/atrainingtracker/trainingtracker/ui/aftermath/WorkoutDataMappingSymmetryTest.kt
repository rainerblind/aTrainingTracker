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
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSource
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import android.util.Log
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Architectural contract test verifying database, DTO, and snapshot mapping symmetry (ATT-2387 / Rule 20).
 * Enforces that all cursor columns mapped in single-item [WorkoutDataMapper.fromCursor]
 * are symmetrically extracted into [WorkoutDataMapper.RawCursorSnapshot] and forwarded
 * into [WorkoutData] via [WorkoutDataMapper.fromSnapshot] and [WorkoutDataMapper.fromCursor] batch.
 */
class WorkoutDataMappingSymmetryTest {

    private val context = mockk<Context>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportTypeDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaUploadDb = mockk<StravaUploadDbHelper>(relaxed = true)
    private val mockClusterDb = mockk<WorkoutClusterDatabaseManager>(relaxed = true)
    private lateinit var mapper: WorkoutDataMapper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        mockkStatic(LapsDatabaseManager::class)
        val mockLapsDb = mockk<LapsDatabaseManager>(relaxed = true)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb
        every { mockLapsDb.getLaps(any()) } returns emptyList()

        WorkoutClusterDatabaseManager.resetForTesting(mockClusterDb)
        every { mockClusterDb.getClusterNameById(any()) } returns "Test Cluster"

        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportTypeDb.getUIName(any()) } returns "Cycling"
        every { mockSportTypeDb.getStravaName(any()) } returns "Ride"
        every { mockEquipmentDb.getEquipmentNameFromId(any()) } returns "Road Bike"

        mapper = WorkoutDataMapper(
            context = context,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportTypeDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaUploadDb,
            knownLocationsDatabaseManager = null
        )
    }

    @org.junit.After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        unmockkStatic(LapsDatabaseManager::class)
        unmockkStatic(TrainingApplication::class)
        unmockkStatic(Log::class)
    }

    private fun createMockCursor(sourceString: String? = "TCX", raceInt: Int = 1): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)

        val columns = mapOf(
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

        every { cursor.getLong(0) } returns 5023L
        every { cursor.getLong(1) } returns 2L
        every { cursor.getLong(2) } returns 10L
        every { cursor.getString(3) } returns "2026-09-09 10:02:00"
        every { cursor.getString(4) } returns "2026-09-09-10-02-00"
        every { cursor.getDouble(5) } returns 5220.0
        every { cursor.getString(6) } returns "k~g_I~...polyline"
        every { cursor.getLong(7) } returns 1811L
        every { cursor.getLong(8) } returns 1811L
        every { cursor.getDouble(9) } returns 2.88
        every { cursor.getLong(10) } returns 53L
        every { cursor.getLong(11) } returns 200L
        every { cursor.getString(12) } returns null
        every { cursor.getString(13) } returns null
        every { cursor.getString(14) } returns "Runter in die Stadt #1"
        every { cursor.getLong(15) } returns 42L
        every { cursor.getInt(16) } returns 1
        every { cursor.getInt(17) } returns 1
        every { cursor.getInt(18) } returns 0
        every { cursor.getInt(19) } returns 1
        every { cursor.getInt(27) } returns raceInt
        every { cursor.getString(28) } returns sourceString

        return cursor
    }

    @Test
    fun testRule20_rawCursorSnapshot_declaresSourceProperty() {
        val snapshotFields = WorkoutDataMapper.RawCursorSnapshot::class.java.declaredFields.map { it.name }
        assertTrue("RawCursorSnapshot must declare 'source' field (Rule 20)", snapshotFields.contains("source"))
        assertTrue("RawCursorSnapshot must declare 'race' field", snapshotFields.contains("race"))
        assertTrue("RawCursorSnapshot must declare 'workoutId' field", snapshotFields.contains("workoutId"))
    }

    @Test
    fun testRule20_workoutData_and_rawCursorSnapshot_sourceTypeParity() {
        val snapshotSourceField = WorkoutDataMapper.RawCursorSnapshot::class.java.getDeclaredField("source")
        val workoutDataSourceField = WorkoutData::class.java.getDeclaredField("source")

        assertEquals(WorkoutSource::class.java, snapshotSourceField.type)
        assertEquals(snapshotSourceField.type, workoutDataSourceField.type)
    }

    @Test
    fun testRule20_singleAndBatchMapping_yieldIdenticalProvenanceAndFlags() {
        val cursor = createMockCursor(sourceString = "TCX", raceInt = 1)
        val singleData = mapper.fromCursor(cursor)

        val batchMetadata = WorkoutDataMapper.BatchMetadata(
            extrema = emptyMap(),
            stravaData = emptyMap(),
            clusterNames = mapOf(42L to "Test Cluster"),
            laps = emptyMap()
        )

        // 1. Batch via fromCursor(cursor, batch)
        val batchData = mapper.fromCursor(cursor, batchMetadata)

        // 2. Snapshot via readCursorSnapshot + fromSnapshot
        val snapshot = mapper.readCursorSnapshot(cursor)
        val snapshotData = mapper.fromSnapshot(snapshot, batchMetadata)

        // Verify Source symmetry across all paths
        assertEquals("Single fromCursor source", WorkoutSource.TCX, singleData.source)
        assertEquals("Batch fromCursor source", WorkoutSource.TCX, batchData.source)
        assertEquals("Snapshot source", WorkoutSource.TCX, snapshot.source)
        assertEquals("fromSnapshot source", WorkoutSource.TCX, snapshotData.source)

        // Verify HeaderData Source propagation
        assertEquals("Single HeaderData source", WorkoutSource.TCX, singleData.headerData.source)
        assertEquals("Batch HeaderData source", WorkoutSource.TCX, batchData.headerData.source)
        assertEquals("Snapshot HeaderData source", WorkoutSource.TCX, snapshotData.headerData.source)

        // Verify Race and other primitive flags symmetry
        assertEquals("Race flag single vs batch", singleData.race, batchData.race)
        assertEquals("Race flag single vs snapshot", singleData.race, snapshotData.race)
        assertEquals("Commute flag single vs batch", singleData.commute, batchData.commute)
        assertEquals("Trainer flag single vs batch", singleData.trainer, batchData.trainer)
        assertEquals("Finished flag single vs batch", singleData.finished, batchData.finished)
    }
}
