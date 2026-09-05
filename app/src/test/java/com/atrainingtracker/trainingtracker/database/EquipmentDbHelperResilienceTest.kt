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

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.EquipmentDataProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying EquipmentDbHelper boundary checks and unassigned equipment query optimization
 * (REQ-STB-005, TST-STB-005, ATT-644).
 */
class EquipmentDbHelperResilienceTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var helper: EquipmentDbHelper

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk<Context>(relaxed = true)
        mockDb = mockk<SQLiteDatabase>(relaxed = true)

        helper = object : EquipmentDbHelper(mockContext) {
            override fun getReadableDatabase(): SQLiteDatabase = mockDb
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testEquipmentName_zeroAndNegativeId_returnsNullWithoutQueryOrErrorLog() {
        // IDs <= 0 (e.g. 0L from SQL NULL, -1L from legacy unassigned, -100L)
        val resultZero = helper.getEquipmentNameFromId(0L)
        val resultNegativeOne = helper.getEquipmentNameFromId(-1L)
        val resultNegativeHundred = helper.getEquipmentNameFromId(-100L)

        assertNull("0L equipment ID must return null", resultZero)
        assertNull("-1L equipment ID must return null", resultNegativeOne)
        assertNull("-100L equipment ID must return null", resultNegativeHundred)

        // Must never query the database
        verify(exactly = 0) { mockDb.query(any(), any(), any(), any(), any(), any(), any()) }

        // Must never emit an ERROR log
        verify(exactly = 0) { Log.e(any<String>(), any<String>()) }
    }

    @Test
    fun testEquipmentName_positiveId_queriesDatabaseAndReturnsName() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every {
            mockDb.query(EquipmentDbHelper.EQUIPMENT, null, EquipmentDbHelper.C_ID + "=?", arrayOf("5"), null, null, null)
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndex(EquipmentDbHelper.NAME) } returns 1
        every { mockCursor.getString(1) } returns "Canyon Endurace"

        val result = helper.getEquipmentNameFromId(5L)

        assertEquals("Canyon Endurace", result)
        verify(exactly = 1) { mockDb.query(EquipmentDbHelper.EQUIPMENT, null, EquipmentDbHelper.C_ID + "=?", arrayOf("5"), null, null, null) }
        verify(exactly = 1) { mockCursor.close() }
        verify(exactly = 0) { Log.e(any<String>(), any<String>()) }
    }

    @Test
    fun testEquipmentName_positiveIdNotFound_logsDiagnosticError() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every {
            mockDb.query(EquipmentDbHelper.EQUIPMENT, null, EquipmentDbHelper.C_ID + "=?", arrayOf("999"), null, null, null)
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns false

        val result = helper.getEquipmentNameFromId(999L)

        assertNull(result)
        verify(exactly = 1) { mockCursor.close() }
        verify(exactly = 1) { Log.e("EquipmentDbHelper", "ERROR: in getEquipmentFromId: no name for id: 999") }
    }

    @Test
    fun testStravaId_zeroAndNegativeId_returnsNullWithoutQueryOrErrorLog() {
        assertNull(helper.getStravaIdFromId(0))
        assertNull(helper.getStravaIdFromId(-1))
        assertNull(helper.getStravaIdFromId(0L))
        assertNull(helper.getStravaIdFromId(-1L))

        verify(exactly = 0) { mockDb.query(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { Log.e(any<String>(), any<String>()) }
    }

    @Test
    fun testStravaId_positiveId_queriesDatabaseAndReturnsStravaId() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every {
            mockDb.query(EquipmentDbHelper.EQUIPMENT, null, EquipmentDbHelper.C_ID + "=?", arrayOf("12"), null, null, null)
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getColumnIndex(EquipmentDbHelper.STRAVA_ID) } returns 2
        every { mockCursor.getString(2) } returns "b890123"

        val resultInt = helper.getStravaIdFromId(12)
        assertEquals("b890123", resultInt)

        val resultLong = helper.getStravaIdFromId(12L)
        assertEquals("b890123", resultLong)

        verify(exactly = 2) { mockCursor.close() }
        verify(exactly = 0) { Log.e(any<String>(), any<String>()) }
    }

    @Test
    fun testStravaId_positiveIdNotFound_logsDiagnosticError() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every {
            mockDb.query(EquipmentDbHelper.EQUIPMENT, null, EquipmentDbHelper.C_ID + "=?", arrayOf("88"), null, null, null)
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns false

        val result = helper.getStravaIdFromId(88)

        assertNull(result)
        verify(exactly = 1) { mockCursor.close() }
        verify(exactly = 1) { Log.e("EquipmentDbHelper", "ERROR: in getStravaIdFromId: no stravaId for id: 88") }
    }

    @Test
    fun testDeviceIdsForEquipment_zeroAndNegativeId_returnsEmptyListWithoutQuery() {
        val resultZero = helper.getDeviceIdsForEquipment(0L)
        val resultNegative = helper.getDeviceIdsForEquipment(-1L)

        assertTrue(resultZero.isEmpty())
        assertTrue(resultNegative.isEmpty())

        verify(exactly = 0) { mockDb.query(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun testDeviceIdsForEquipment_positiveId_queriesLinksTable() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every {
            mockDb.query(
                EquipmentDbHelper.LINKS,
                arrayOf(EquipmentDbHelper.ANT_DEVICE_ID),
                EquipmentDbHelper.EQUIPMENT_ID + "=?",
                arrayOf("3"),
                null, null, null
            )
        } returns mockCursor

        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getLong(0) } returns 5555L
        every { mockCursor.moveToNext() } returns false

        val deviceIds = helper.getDeviceIdsForEquipment(3L)

        assertEquals(listOf(5555L), deviceIds)
        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testUpdateAndDeleteEquipment_nonPositiveId_earlyReturnWithoutWriteTransaction() {
        helper.updateEquipment(0L, "Invalid", 1, emptyList())
        helper.updateEquipment(-1L, "Invalid", 1, emptyList())
        helper.deleteEquipment(0L)
        helper.deleteEquipment(-1L)

        verify(exactly = 0) { mockDb.beginTransaction() }
        verify(exactly = 0) { mockDb.delete(any(), any(), any()) }
        verify(exactly = 0) { mockDb.update(any(), any(), any(), any()) }
    }

    @Test
    fun testEquipmentDataProvider_unassignedEquipment_shortCircuitsWithoutQueryingHelper() {
        val mockEquipmentDbHelper = mockk<EquipmentDbHelper>()
        val mockSportTypeManager = mockk<SportTypeDatabaseManager>()
        val mockCursor = mockk<Cursor>()

        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID) } returns 0
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID) } returns 1
        every { mockCursor.getLong(0) } returns 3L
        every { mockCursor.getLong(1) } returns 0L // unassigned (0L)

        every { mockSportTypeManager.getBSportType(3L) } returns BSportType.RUN

        val provider = EquipmentDataProvider(mockEquipmentDbHelper, mockSportTypeManager)
        val data = provider.getEquipmentData(mockCursor)

        assertEquals(BSportType.RUN, data.bSportType)
        assertEquals(0L, data.equipmentId)
        assertNull("equipmentName must be null for unassigned equipment", data.equipmentName)

        // Helper must NEVER be called when equipmentId <= 0
        verify(exactly = 0) { mockEquipmentDbHelper.getEquipmentNameFromId(any()) }
    }

    @Test
    fun testEquipmentDataProvider_assignedEquipment_queriesHelperAndResolvesName() {
        val mockEquipmentDbHelper = mockk<EquipmentDbHelper>()
        val mockSportTypeManager = mockk<SportTypeDatabaseManager>()
        val mockCursor = mockk<Cursor>()

        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID) } returns 0
        every { mockCursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID) } returns 1
        every { mockCursor.getLong(0) } returns 1L
        every { mockCursor.getLong(1) } returns 15L // assigned (15L)

        every { mockSportTypeManager.getBSportType(1L) } returns BSportType.BIKE
        every { mockEquipmentDbHelper.getEquipmentNameFromId(15L) } returns "Gravel Bike"

        val provider = EquipmentDataProvider(mockEquipmentDbHelper, mockSportTypeManager)
        val data = provider.getEquipmentData(mockCursor)

        assertEquals(BSportType.BIKE, data.bSportType)
        assertEquals(15L, data.equipmentId)
        assertEquals("Gravel Bike", data.equipmentName)

        verify(exactly = 1) { mockEquipmentDbHelper.getEquipmentNameFromId(15L) }
    }
}
