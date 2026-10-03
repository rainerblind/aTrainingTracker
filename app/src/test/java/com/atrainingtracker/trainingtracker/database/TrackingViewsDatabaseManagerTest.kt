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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import java.lang.reflect.Field

/**
 * Unit tests verifying TrackingViewsDatabaseManager's tile swapping, moving,
 * and grid normalization capabilities (REQ-UI-200, TST-UI-154.1, TST-UI-154.2).
 */
class TrackingViewsDatabaseManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } returns Unit
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } returns Unit
        every { anyConstructed<ContentValues>().clear() } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
        resetSingleton()
    }

    private fun resetSingleton() {
        try {
            val instanceField: Field = TrackingViewsDatabaseManager::class.java.getDeclaredField("cInstance")
            instanceField.isAccessible = true
            instanceField.set(null, null)
        } catch (_: Exception) {
        }
    }

    private fun injectMockDatabase(manager: TrackingViewsDatabaseManager, db: SQLiteDatabase) {
        val dbField: Field = TrackingViewsDatabaseManager::class.java.getDeclaredField("mDatabase")
        dbField.isAccessible = true
        dbField.set(manager, db)
    }

    @Test
    fun testSwapSensorFields_sameId_isNoOp() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.swapSensorFields(42L, 42L)

        verify(exactly = 0) { mockDb.beginTransaction() }
        verify(exactly = 0) { mockDb.query(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun testSwapSensorFields_distinctIds_swapsCoordinatesAtomically() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val cursorA = mockk<Cursor>(relaxed = true)
        val cursorB = mockk<Cursor>(relaxed = true)

        every { cursorA.moveToFirst() } returns true
        every { cursorA.getInt(0) } returns 1 // rowA
        every { cursorA.getInt(1) } returns 1 // colA

        every { cursorB.moveToFirst() } returns true
        every { cursorB.getInt(0) } returns 2 // rowB
        every { cursorB.getInt(1) } returns 3 // colB

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR, TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID}=?",
                arrayOf("10"),
                null, null, null
            )
        } returns cursorA

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR, TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID}=?",
                arrayOf("20"),
                null, null, null
            )
        } returns cursorB

        manager.swapSensorFields(10L, 20L)

        verify(exactly = 1) { mockDb.beginTransaction() }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.endTransaction() }
        verify(exactly = 1) { cursorA.close() }
        verify(exactly = 1) { cursorB.close() }

        // Field 10 receives row 2, col 3; Field 20 receives row 1, col 1
        verify(exactly = 2) {
            mockDb.update(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                any(),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID}=?",
                any()
            )
        }
    }

    @Test
    fun testMoveSensorField_parksCompactsAndNormalizes() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val fieldCursor = mockk<Cursor>(relaxed = true)
        every { fieldCursor.moveToFirst() } returns true
        every { fieldCursor.getLong(0) } returns 5L // tabViewId
        every { fieldCursor.getInt(1) } returns 1  // oldRow
        every { fieldCursor.getInt(2) } returns 2  // oldCol

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(
                    TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID,
                    TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR,
                    TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR
                ),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID}=?",
                arrayOf("101"),
                null, null, null
            )
        } returns fieldCursor

        val countCursor = mockk<Cursor>(relaxed = true)
        every { countCursor.count } returns 1 // 1 remaining field in oldRow
        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=? AND ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR}=?",
                arrayOf("5", "1"),
                null, null, null
            )
        } returns countCursor

        val distinctRowsCursor = mockk<Cursor>(relaxed = true)
        every { distinctRowsCursor.moveToNext() } returns false
        every {
            mockDb.query(
                true,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=?",
                arrayOf("5"),
                null, null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR} ASC",
                null
            )
        } returns distinctRowsCursor

        manager.moveSensorField(101L, 2, 1)

        verify(exactly = 1) { mockDb.beginTransaction() }
        verify(atLeast = 1) { mockDb.execSQL(any()) }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.endTransaction() }
    }

    @Test
    fun testMoveSensorField_newRowRequested_createsRowAndNormalizes() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val fieldCursor = mockk<Cursor>(relaxed = true)
        every { fieldCursor.moveToFirst() } returns true
        every { fieldCursor.getLong(0) } returns 5L // tabViewId
        every { fieldCursor.getInt(1) } returns 2  // oldRow
        every { fieldCursor.getInt(2) } returns 1  // oldCol

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(
                    TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID,
                    TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR,
                    TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR
                ),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID}=?",
                arrayOf("102"),
                null, null, null
            )
        } returns fieldCursor

        val countCursor = mockk<Cursor>(relaxed = true)
        every { countCursor.count } returns 0 // oldRow emptied!
        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=? AND ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR}=?",
                arrayOf("5", "2"),
                null, null, null
            )
        } returns countCursor

        val distinctRowsCursor = mockk<Cursor>(relaxed = true)
        every { distinctRowsCursor.moveToNext() } returns false
        every {
            mockDb.query(
                true,
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=?",
                arrayOf("5"),
                null, null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR} ASC",
                null
            )
        } returns distinctRowsCursor

        // targetCol = -1 creates a new row at targetRow
        manager.moveSensorField(102L, 1, -1)

        verify(exactly = 1) { mockDb.beginTransaction() }
        verify(atLeast = 1) { mockDb.execSQL(match { it.contains("RowNr") && it.contains("+1") }) }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.endTransaction() }
    }

    @Test
    fun testAddDefaultTab_insertsViewSize() {
        val dbHelper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext)
        val rowData = TrackingViewsDatabaseManager.TrackingViewsDbHelper.RowData(
            com.atrainingtracker.banalservice.sensor.SensorType.HR,
            com.atrainingtracker.trainingtracker.ui.tracking.ViewSize.LARGE,
            1,
            1
        )
        every { mockDb.insert(any(), any(), any()) } returns 1L

        dbHelper.addDefaultTab(
            mockDb,
            com.atrainingtracker.banalservice.ActivityType.GENERIC_HR,
            "Default",
            1,
            true,
            false,
            false,
            listOf(rowData)
        )

        verify {
            anyConstructed<ContentValues>().put(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE,
                "LARGE"
            )
        }
    }

    @Test
    fun testOnUpgrade_from10To11_executesRepairSql() {
        val dbHelper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext)
        dbHelper.onUpgrade(mockDb, 10, 11)

        verify(exactly = 1) {
            mockDb.execSQL(match {
                it.contains("LayoutRowsTable") && it.contains("ViewSize") && it.contains("NORMAL")
            })
        }
    }

    @Test
    fun testGetAllFilterData_handlesNullAndInvalidEnumsWithoutThrowing() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returnsMany listOf(true, true, false)

        every { cursor.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE) } returns 0
        every { cursor.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID) } returns 1
        every { cursor.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE) } returns 2
        every { cursor.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT) } returns 3

        every { cursor.getString(0) } returnsMany listOf(null, "INVALID_SENSOR")
        every { cursor.getInt(1) } returns 0
        every { cursor.getString(2) } returnsMany listOf(null, "INVALID_FILTER")
        every { cursor.getDouble(3) } returns 1.0

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                null,
                null,
                null,
                null,
                null,
                null
            )
        } returns cursor

        val mockDevicesDbManager = mockk<com.atrainingtracker.banalservice.database.DevicesDatabaseManager>(relaxed = true)
        val filterList = manager.getAllFilterData(mockDevicesDbManager)

        assertEquals(2, filterList.size)
        assertEquals(com.atrainingtracker.banalservice.sensor.SensorType.TIME_ACTIVE, filterList[0].sensorType)
        assertEquals(com.atrainingtracker.banalservice.filters.FilterType.INSTANTANEOUS, filterList[0].filterType)
        assertEquals(com.atrainingtracker.banalservice.sensor.SensorType.TIME_ACTIVE, filterList[1].sensorType)
        assertEquals(com.atrainingtracker.banalservice.filters.FilterType.INSTANTANEOUS, filterList[1].filterType)
    }
}
