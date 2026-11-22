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

package com.atrainingtracker.trainingtracker.database

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.util.NumericalEncodingUtils
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dedicated Robolectric SQLite tests verifying atomic summary extrema, altitude stream,
 * and ascent/descent recalculation in [WorkoutSummariesDatabaseManager.shiftAltitudeData]
 * (REQ-FIL-004, TST-FIL-003.2, ATT-8329).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutSummariesAltitudeShiftTest {

    private lateinit var context: Context
    private lateinit var dbManager: WorkoutSummariesDatabaseManager
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getAppContext() } returns context
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        context.deleteDatabase(WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_NAME)
        dbManager = WorkoutSummariesDatabaseManager.getInstance(context)
        db = dbManager.database
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) {
            db.close()
        }
        WorkoutSummariesDatabaseManager.setInstanceForTesting(null)
        context.deleteDatabase(WorkoutSummariesDatabaseManager.WorkoutSummariesDbHelper.DB_NAME)
        unmockkAll()
    }

    private fun insertWorkoutWithAltitude(streamPoints: List<Double>, minAlt: Double, maxAlt: Double): Long {
        val summaryValues = ContentValues().apply {
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_NAME, "Altitude Shift Test")
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME, "alt_shift_test_2026_11_19")
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM, NumericalEncodingUtils.encodeDoubles(streamPoints))
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.ASCENDING, 50)
            put(WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCENDING, 20)
        }
        val workoutId = db.insertOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE, null, summaryValues)

        for ((extremaType, value) in listOf(ExtremaType.MIN to minAlt, ExtremaType.MAX to maxAlt)) {
            val extremaValues = ContentValues().apply {
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID, workoutId)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.EXTREMA_TYPE, extremaType.name)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.SENSOR_TYPE, SensorType.ALTITUDE.name)
                put(WorkoutSummariesDatabaseManager.WorkoutSummaries.VALUE, value)
            }
            db.insertOrThrow(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE_EXTREMA_VALUES, null, extremaValues)
        }
        return workoutId
    }

    private fun queryExtrema(workoutId: Long, extremaType: ExtremaType): Double {
        db.rawQuery(
            "SELECT ${WorkoutSummariesDatabaseManager.WorkoutSummaries.VALUE} FROM ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE_EXTREMA_VALUES} " +
                "WHERE ${WorkoutSummariesDatabaseManager.WorkoutSummaries.WORKOUT_ID} = ? AND ${WorkoutSummariesDatabaseManager.WorkoutSummaries.EXTREMA_TYPE} = ? " +
                "AND ${WorkoutSummariesDatabaseManager.WorkoutSummaries.SENSOR_TYPE} = ?",
            arrayOf(workoutId.toString(), extremaType.name, SensorType.ALTITUDE.name)
        ).use { cursor ->
            assertTrue("Expected ${extremaType.name} extrema row", cursor.moveToFirst())
            return cursor.getDouble(0)
        }
    }

    /**
     * TST-FIL-003.2 (Case A, t0Ms <= 0): Uniform altitude shift atomically updates
     * TABLE_EXTREMA_VALUES (MIN/MAX), ALTITUDE_STREAM, and recomputes ASCENDING/DESCENDING.
     */
    @Test
    fun testShiftAltitudeData_caseA_uniformShift_updatesExtremaStreamAndAscentDescent() {
        val workoutId = insertWorkoutWithAltitude(listOf(100.0, 150.0, 130.0), minAlt = 100.0, maxAlt = 150.0)

        dbManager.shiftAltitudeData(workoutId, 100.0, -1L, 1794304820000L)

        assertEquals(200.0, queryExtrema(workoutId, ExtremaType.MIN), 0.001)
        assertEquals(250.0, queryExtrema(workoutId, ExtremaType.MAX), 0.001)

        val encoded = dbManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM)
        assertNotNull(encoded)
        val decoded = NumericalEncodingUtils.decodeDoubles(encoded!!)
        assertEquals(3, decoded.size)
        assertEquals(200.0, decoded[0], 0.05)
        assertEquals(250.0, decoded[1], 0.05)
        assertEquals(230.0, decoded[2], 0.05)

        db.rawQuery(
            "SELECT ${WorkoutSummariesDatabaseManager.WorkoutSummaries.ASCENDING}, ${WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCENDING} " +
                "FROM ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE} WHERE ${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(50.0, cursor.getDouble(0), 0.1)
            assertEquals(20.0, cursor.getDouble(1), 0.1)
        }
    }

    /**
     * TST-FIL-003.2 (Case B, t0Ms > 0 && t1Ms > t0Ms): Piecewise-linear drift interpolation
     * atomically updates ALTITUDE_STREAM, recomputes ASCENDING/DESCENDING, and syncs MIN/MAX extrema.
     */
    @Test
    fun testShiftAltitudeData_caseB_piecewiseInterpolation_updatesStreamExtremaAndAscent() {
        val workoutId = insertWorkoutWithAltitude(listOf(100.0, 100.0, 100.0), minAlt = 100.0, maxAlt = 100.0)

        dbManager.shiftAltitudeData(workoutId, 40.0, 1000L, 3000L)

        val encoded = dbManager.getString(workoutId, WorkoutSummariesDatabaseManager.WorkoutSummaries.ALTITUDE_STREAM)
        assertNotNull(encoded)
        val decoded = NumericalEncodingUtils.decodeDoubles(encoded!!)
        assertEquals(3, decoded.size)
        assertEquals(100.0, decoded[0], 0.05)
        assertEquals(120.0, decoded[1], 0.05)
        assertEquals(140.0, decoded[2], 0.05)

        assertEquals(100.0, queryExtrema(workoutId, ExtremaType.MIN), 0.05)
        assertEquals(140.0, queryExtrema(workoutId, ExtremaType.MAX), 0.05)

        db.rawQuery(
            "SELECT ${WorkoutSummariesDatabaseManager.WorkoutSummaries.ASCENDING}, ${WorkoutSummariesDatabaseManager.WorkoutSummaries.DESCENDING} " +
                "FROM ${WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE} WHERE ${WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
            arrayOf(workoutId.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(40.0, cursor.getDouble(0), 0.1)
            assertEquals(0.0, cursor.getDouble(1), 0.1)
        }
    }
}
