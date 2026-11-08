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
import android.database.MatrixCursor
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Architectural contract tests for WorkoutDataMapper verifying Rule 20 mapping symmetry
 * for the recordingErrors column (TST-TRK-053.3, REQ-TRK-053.3).
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutDataMapperRecordingErrorsParityTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockSportDb = mockk<SportTypeDatabaseManager>(relaxed = true)
    private val mockEquipmentDb = mockk<EquipmentDbHelper>(relaxed = true)
    private val mockStravaDb = mockk<StravaUploadDbHelper>(relaxed = true)

    private lateinit var mapper: WorkoutDataMapper

    @Before
    fun setUp() {
        mapper = WorkoutDataMapper(
            context = mockContext,
            workoutSummariesDatabaseManager = mockSummariesDb,
            sportTypeDatabaseManager = mockSportDb,
            equipmentDbHelper = mockEquipmentDb,
            stravaUploadDbHelper = mockStravaDb
        )
    }

    private fun createSampleCursor(recordingErrors: String?): MatrixCursor {
        val columns = arrayOf(
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
            WorkoutSummaries.FINISHED,
            WorkoutSummaries.COMMUTE,
            WorkoutSummaries.TRAINER,
            WorkoutSummaries.RACE,
            WorkoutSummaries.SOURCE,
            WorkoutSummaries.UPLOAD_TO_STRAVA,
            WorkoutSummaries.BOUND_MIN_LAT,
            WorkoutSummaries.BOUND_MIN_LNG,
            WorkoutSummaries.BOUND_MAX_LAT,
            WorkoutSummaries.BOUND_MAX_LNG,
            WorkoutSummaries.TIME_ACTIVE_s,
            WorkoutSummaries.TIME_TOTAL_s,
            WorkoutSummaries.SPEED_AVERAGE_mps,
            WorkoutSummaries.ASCENDING,
            WorkoutSummaries.DESCENDING,
            WorkoutSummaries.DESCRIPTION,
            WorkoutSummaries.GOAL,
            WorkoutSummaries.METHOD,
            WorkoutSummaries.RECORDING_ERRORS
        )
        val cursor = MatrixCursor(columns)
        cursor.addRow(
            arrayOf<Any?>(
                1L, // _id
                1L, // sportId
                -1L, // equipmentId
                "2026-11-08 09:00:00",
                "2026-11-08_090000",
                10000.0,
                "",
                "",
                "",
                "Morning Run",
                -1L,
                1, // finished
                0, // commute
                0, // trainer
                0, // race
                "TRACKED",
                -1,
                null,
                null,
                null,
                null,
                3600L,
                3700L,
                2.78,
                50L,
                50L,
                "Test notes",
                "Sub 40 min",
                "Tempo",
                recordingErrors
            )
        )
        cursor.moveToFirst()
        return cursor
    }

    @Test
    fun testRule20MappingSymmetryWithNonNullErrors() {
        val payload = """[{"stage":"SAMPLE_WRITE","exceptionClass":"SQLiteException","count":10}]"""
        val cursor = createSampleCursor(payload)

        // Single cursor pathway
        val singleResult = mapper.fromCursor(cursor)
        assertEquals(payload, singleResult.recordingErrors)
        assertEquals(payload, singleResult.headerData.recordingErrors)

        // Batch snapshot pathway
        val snapshot = mapper.readCursorSnapshot(cursor)
        assertEquals(payload, snapshot.recordingErrors)

        val batch = WorkoutDataMapper.BatchMetadata(
            sportTypes = emptyMap(),
            equipmentNames = emptyMap(),
            clusterNames = emptyMap(),
            stravaData = emptyMap(),
            extrema = emptyMap(),
            laps = emptyMap()
        )
        val batchResult = mapper.fromSnapshot(snapshot, batch)
        assertEquals(payload, batchResult.recordingErrors)
        assertEquals(payload, batchResult.headerData.recordingErrors)

        assertEquals("Single and batch mapping must yield identical recordingErrors (Rule 20)", singleResult.recordingErrors, batchResult.recordingErrors)
    }

    @Test
    fun testRule20MappingSymmetryWithNullErrors() {
        val cursor = createSampleCursor(null)

        val singleResult = mapper.fromCursor(cursor)
        assertNull(singleResult.recordingErrors)
        assertNull(singleResult.headerData.recordingErrors)

        val snapshot = mapper.readCursorSnapshot(cursor)
        assertNull(snapshot.recordingErrors)

        val batch = WorkoutDataMapper.BatchMetadata(
            sportTypes = emptyMap(),
            equipmentNames = emptyMap(),
            clusterNames = emptyMap(),
            stravaData = emptyMap(),
            extrema = emptyMap(),
            laps = emptyMap()
        )
        val batchResult = mapper.fromSnapshot(snapshot, batch)
        assertNull(batchResult.recordingErrors)
        assertNull(batchResult.headerData.recordingErrors)

        assertEquals(singleResult.recordingErrors, batchResult.recordingErrors)
    }
}
