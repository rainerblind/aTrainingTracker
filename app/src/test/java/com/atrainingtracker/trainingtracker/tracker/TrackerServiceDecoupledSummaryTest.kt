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

package com.atrainingtracker.trainingtracker.tracker

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.banalservice.sensor.SensorValueType
import com.atrainingtracker.testutil.RealSqliteTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying decoupled summary telemetry updates and rate-limited logging
 * (`REQ-TRK-051.2` / `TST-TRK-051.2`, `ATT-4968`).
 */
@RunWith(RobolectricTestRunner::class)
class TrackerServiceDecoupledSummaryTest {

    private lateinit var db: SQLiteDatabase
    private val tableName = "\"2026-11-03_140000\""

    @Before
    fun setUp() {
        db = RealSqliteTestHelper.createInMemoryDatabase()
        db.execSQL(
            "CREATE TABLE $tableName (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "TIME_TOTAL int null, " +
                "TIME_ACTIVE int null, " +
                "DISTANCE_TOTAL double null" +
                ");"
        )
    }

    @After
    fun tearDown() {
        RealSqliteTestHelper.cleanupDatabase(db)
    }

    @Test
    fun testShouldLogSampleInsertFailure_rateLimitingPolicy() {
        // Initial burst: 1, 2, 3 must be logged
        assertTrue("Burst 1 must be logged", TrackerService.shouldLogSampleInsertFailure(1))
        assertTrue("Burst 2 must be logged", TrackerService.shouldLogSampleInsertFailure(2))
        assertTrue("Burst 3 must be logged", TrackerService.shouldLogSampleInsertFailure(3))

        // Intermediate values must be throttled
        assertFalse("4 must be throttled", TrackerService.shouldLogSampleInsertFailure(4))
        assertFalse("59 must be throttled", TrackerService.shouldLogSampleInsertFailure(59))

        // Interval values: 60, 120 must be logged
        assertTrue("Interval 60 must be logged", TrackerService.shouldLogSampleInsertFailure(60))
        assertFalse("61 must be throttled", TrackerService.shouldLogSampleInsertFailure(61))
        assertTrue("Interval 120 must be logged", TrackerService.shouldLogSampleInsertFailure(120))

        // Zero / negative counts should not be logged
        assertFalse("0 must not be logged", TrackerService.shouldLogSampleInsertFailure(0))
        assertFalse("-1 must not be logged", TrackerService.shouldLogSampleInsertFailure(-1))
    }

    @Test
    fun testQuoteAndUnquoteSqlColumnIdentifier_roundTrip() {
        val simple = "SPEED"
        val quotedSimple = TrackerService.quoteSqlColumnIdentifier(simple)
        assertEquals("\"SPEED\"", quotedSimple)
        assertEquals(simple, TrackerService.unquoteSqlColumnIdentifier(quotedSimple))

        val complex = "HR (Rainer's \"Sensor\")"
        val quotedComplex = TrackerService.quoteSqlColumnIdentifier(complex)
        assertEquals("\"HR (Rainer's \"\"Sensor\"\")\"", quotedComplex)
        assertEquals(complex, TrackerService.unquoteSqlColumnIdentifier(quotedComplex))
    }
}
