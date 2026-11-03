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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.trainingtracker.ui.tracking.ViewSize
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
        every { anyConstructed<ContentValues>().put(any<String>(), any<Float>()) } returns Unit
        every { anyConstructed<ContentValues>().putNull(any<String>()) } returns Unit
        every { anyConstructed<ContentValues>().remove(any<String>()) } returns Unit
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
    fun testOnCreate_seedsCanonicalDefaultTabConfigurationMatrix() {
        val dbHelper = spyk(TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext))
        every { mockDb.insert(any(), any(), any()) } returns 1L

        dbHelper.onCreate(mockDb)

        val nonPowerActivities = listOf(
            com.atrainingtracker.banalservice.ActivityType.GENERIC_HR,
            com.atrainingtracker.banalservice.ActivityType.RUN_SPEED_AND_CADENCE,
            com.atrainingtracker.banalservice.ActivityType.BIKE_SPEED_AND_CADENCE
        )

        for (activityType in nonPowerActivities) {
            val isCycling = activityType == com.atrainingtracker.banalservice.ActivityType.BIKE_SPEED_AND_CADENCE
            // Standard Tab: layoutNr 1, live segments, live climbs, nav hints, showBikeBell = isCycling
            verify(atLeast = 1) {
                dbHelper.addDefaultTab(
                    mockDb,
                    activityType,
                    any(),
                    1,
                    false,
                    false,
                    true,
                    true,
                    true,
                    isCycling,
                    any()
                )
            }
            // Map Tab: layoutNr 2, map, elevation profile, nav hints, no live climbs or segments, showBikeBell = isCycling
            verify(atLeast = 1) {
                dbHelper.addDefaultTab(
                    mockDb,
                    activityType,
                    any(),
                    2,
                    false,
                    true,
                    false,
                    false,
                    true,
                    isCycling,
                    any()
                )
            }
            // Laps Tab: layoutNr 3, lap button only, no live climbs, nav hints, or segments, showBikeBell = isCycling
            verify(atLeast = 1) {
                dbHelper.addDefaultTab(
                    mockDb,
                    activityType,
                    any(),
                    3,
                    true,
                    false,
                    false,
                    false,
                    false,
                    isCycling,
                    any()
                )
            }
        }

        // BIKE_POWER (REQ-UI-409.2): 4 tabs (1 = Standard, 2 = Power, 3 = Map, 4 = Laps), all have showBikeBell = true
        val bikePower = com.atrainingtracker.banalservice.ActivityType.BIKE_POWER
        verify(atLeast = 1) {
            dbHelper.addDefaultTab(mockDb, bikePower, any(), 1, false, false, true, true, true, true, any())
            dbHelper.addDefaultTab(mockDb, bikePower, any(), 2, false, false, false, false, true, true, any())
            dbHelper.addDefaultTab(mockDb, bikePower, any(), 3, false, true, false, false, true, true, any())
            dbHelper.addDefaultTab(mockDb, bikePower, any(), 4, true, false, false, false, false, true, any())
        }
    }

    @Test
    fun testAddDefaultTab_propagatesAllConfigurableToggles() {
        val dbHelper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext)
        val rowData = TrackingViewsDatabaseManager.TrackingViewsDbHelper.RowData(
            com.atrainingtracker.banalservice.sensor.SensorType.HR,
            com.atrainingtracker.trainingtracker.ui.tracking.ViewSize.NORMAL,
            1,
            1
        )
        every { mockDb.insert(any(), any(), any()) } returns 1L

        // Test with showLiveClimbs = false, showNavigationHints = false
        dbHelper.addDefaultTab(
            mockDb,
            com.atrainingtracker.banalservice.ActivityType.GENERIC_HR,
            "CustomTabDisabled",
            1,
            false,
            false,
            false,
            false,
            false,
            listOf(rowData)
        )

        verify {
            anyConstructed<ContentValues>().put(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_CLIMBS,
                0
            )
            anyConstructed<ContentValues>().put(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_NAVIGATION_HINTS,
                0
            )
        }

        // Test with showLiveClimbs = true, showNavigationHints = true
        dbHelper.addDefaultTab(
            mockDb,
            com.atrainingtracker.banalservice.ActivityType.GENERIC_HR,
            "CustomTabEnabled",
            1,
            false,
            false,
            false,
            true,
            true,
            listOf(rowData)
        )

        verify {
            anyConstructed<ContentValues>().put(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_LIVE_CLIMBS,
                1
            )
            anyConstructed<ContentValues>().put(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SHOW_NAVIGATION_HINTS,
                1
            )
        }
    }

    @Test
    fun testAddDefaultTab_backwardCompatibleOverload_delegatesWithClimbsAndHintsEnabled() {
        val dbHelper = spyk(TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext))
        val rowData = TrackingViewsDatabaseManager.TrackingViewsDbHelper.RowData(
            com.atrainingtracker.banalservice.sensor.SensorType.HR,
            com.atrainingtracker.trainingtracker.ui.tracking.ViewSize.NORMAL,
            1,
            1
        )
        every { mockDb.insert(any(), any(), any()) } returns 1L

        dbHelper.addDefaultTab(
            mockDb,
            com.atrainingtracker.banalservice.ActivityType.GENERIC_HR,
            "LegacyTab",
            1,
            false,
            false,
            true,
            listOf(rowData)
        )

        verify(exactly = 1) {
            dbHelper.addDefaultTab(
                mockDb,
                com.atrainingtracker.banalservice.ActivityType.GENERIC_HR,
                "LegacyTab",
                1,
                false,
                false,
                true,
                true,
                true,
                listOf(rowData)
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

    @Test
    fun testDatabaseVersionIs18() {
        assertEquals(18, TrackingViewsDatabaseManager.TrackingViewsDbHelper.DB_VERSION)
    }

    @Test
    fun testIsBarSupported_sportDependentFeasibility() {
        // HR: Run and Bike only
        assertTrue(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.HR, com.atrainingtracker.banalservice.BSportType.RUN))
        assertTrue(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.HR, com.atrainingtracker.banalservice.BSportType.BIKE))
        assertFalse(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.HR, com.atrainingtracker.banalservice.BSportType.UNKNOWN))

        // Power, Pedal Balance, W'Bal: Bike only
        assertTrue(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.POWER, com.atrainingtracker.banalservice.BSportType.BIKE))
        assertFalse(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.POWER, com.atrainingtracker.banalservice.BSportType.RUN))

        assertTrue(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.PEDAL_POWER_BALANCE, com.atrainingtracker.banalservice.BSportType.BIKE))
        assertFalse(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.PEDAL_POWER_BALANCE, com.atrainingtracker.banalservice.BSportType.RUN))

        assertTrue(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.W_PRIME_BAL, com.atrainingtracker.banalservice.BSportType.BIKE))
        assertFalse(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.W_PRIME_BAL, com.atrainingtracker.banalservice.BSportType.RUN))

        // Others: false
        assertFalse(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(SensorType.SPEED_mps, com.atrainingtracker.banalservice.BSportType.BIKE))
        assertFalse(TrackingViewsDatabaseManager.TrackingViewsDbHelper.isBarSupported(null as SensorType?, com.atrainingtracker.banalservice.BSportType.BIKE))
    }

    @Test
    fun testOnUpgrade_from16To17_addsColWeight() {
        val dbHelper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext)
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(match { it.contains("PRAGMA table_info") }, any()) } returns mockCursor
        every { mockCursor.moveToNext() } returns false
        val tableExistsCursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(match { it.contains("sqlite_master") }, any()) } returns tableExistsCursor
        every { tableExistsCursor.moveToFirst() } returns true

        dbHelper.onUpgrade(mockDb, 16, 17)

        verify(atLeast = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE LayoutRowsTable ADD COLUMN ColWeight real DEFAULT 1.0;") })
            mockDb.execSQL(match { it.contains("UPDATE LayoutRowsTable SET ColWeight = 1.0 WHERE ColWeight IS NULL;") })
        }
    }

    @Test
    fun testOnUpgrade_from15To16_addsShowValueAndShowBar() {
        val dbHelper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext)
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(match { it.contains("PRAGMA table_info") }, any()) } returns mockCursor
        every { mockCursor.moveToNext() } returns false
        // The v16 upgrade first checks that LayoutRowsTable exists via sqlite_master (ATT-4841)
        val tableExistsCursor = mockk<Cursor>(relaxed = true)
        every { mockDb.rawQuery(match { it.contains("sqlite_master") }, any()) } returns tableExistsCursor
        every { tableExistsCursor.moveToFirst() } returns true

        dbHelper.onUpgrade(mockDb, 15, 16)

        verify(atLeast = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE LayoutRowsTable ADD COLUMN ShowValue int DEFAULT 1;") })
            mockDb.execSQL(match { it.contains("ALTER TABLE LayoutRowsTable ADD COLUMN ShowBar int DEFAULT 0;") })
            mockDb.execSQL(match { it.contains("UPDATE LayoutRowsTable SET ShowValue = 1 WHERE ShowValue IS NULL;") })
            mockDb.execSQL(match { it.contains("UPDATE LayoutRowsTable SET ShowBar = 1 WHERE SensorType IN") })
            mockDb.execSQL(match { it.contains("UPDATE LayoutRowsTable SET ShowBar = 0 WHERE ShowBar IS NULL;") })
        }
    }

    @Test
    fun testOnUpgrade_from13To14_createsSectionDividersTable() {
        val dbHelper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext)
        dbHelper.onUpgrade(mockDb, 13, 14)

        verify(atLeast = 1) {
            mockDb.execSQL(match { it.contains("CREATE TABLE IF NOT EXISTS SectionDividersTable") })
        }
    }

    @Test
    fun testOnUpgrade_from11To12_addsColumnsAndExecutesRepairSql() {
        val dbHelper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext)
        dbHelper.onUpgrade(mockDb, 11, 12)

        verify(atLeast = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE ViewsTable ADD COLUMN ShowLiveClimbs int DEFAULT 1;") })
            mockDb.execSQL(match { it.contains("ALTER TABLE ViewsTable ADD COLUMN ShowNavigationHints int DEFAULT 1;") })
            mockDb.execSQL(match { it.contains("UPDATE ViewsTable SET ShowLiveClimbs = 1 WHERE ShowLiveClimbs IS NULL;") })
            mockDb.execSQL(match { it.contains("UPDATE ViewsTable SET ShowNavigationHints = 1 WHERE ShowNavigationHints IS NULL;") })
        }
    }

    @Test
    fun testOnUpgrade_from12To13_addsElevationProfileColumns() {
        val dbHelper = TrackingViewsDatabaseManager.TrackingViewsDbHelper(mockContext)
        dbHelper.onUpgrade(mockDb, 12, 13)

        verify(atLeast = 1) {
            mockDb.execSQL(match { it.contains("ALTER TABLE ViewsTable ADD COLUMN ElevationProfileScope int DEFAULT 0;") })
            mockDb.execSQL(match { it.contains("ALTER TABLE ViewsTable ADD COLUMN ElevationProfileLookahead int DEFAULT 2000;") })
            mockDb.execSQL(match { it.contains("UPDATE ViewsTable SET ElevationProfileScope = 0 WHERE ElevationProfileScope IS NULL;") })
            mockDb.execSQL(match { it.contains("UPDATE ViewsTable SET ElevationProfileLookahead = 2000 WHERE ElevationProfileLookahead IS NULL;") })
        }
    }

    @Test
    fun testUpdateShowLiveClimbs_updatesViewsTable() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.updateShowLiveClimbs(42L, false)

        verify(exactly = 1) {
            mockDb.update(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
                arrayOf("42")
            )
        }
    }

    @Test
    fun testUpdateShowNavigationHints_updatesViewsTable() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.updateShowNavigationHints(42L, false)

        verify(exactly = 1) {
            mockDb.update(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
                arrayOf("42")
            )
        }
    }

    @Test
    fun testUpdateElevationProfileScope_updatesViewsTable() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.updateElevationProfileScope(42L, 1)

        verify(exactly = 1) {
            mockDb.update(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
                arrayOf("42")
            )
        }
    }

    @Test
    fun testUpdateElevationProfileLookahead_updatesViewsTable() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.updateElevationProfileLookahead(42L, 5000)

        verify(exactly = 1) {
            mockDb.update(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
                arrayOf("42")
            )
        }
    }

    @Test
    fun testSetSectionDivider_insertsWithOnConflictReplace() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.setSectionDivider(10L, 3, "POWER METRICS")

        verify(exactly = 1) {
            mockDb.insertWithOnConflict(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                null,
                any(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    @Test
    fun testDeleteSectionDivider_deletesRow() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.deleteSectionDivider(10L, 3)

        verify(exactly = 1) {
            mockDb.delete(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=? AND ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR}=?",
                arrayOf("10", "3")
            )
        }
    }

    @Test
    fun testDeleteTabView_deletesSectionDividers() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.deleteTabView(10L)

        verify(exactly = 1) {
            mockDb.delete(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=?",
                arrayOf("10")
            )
        }
    }

    @Test
    fun testInsertSensorFieldAt_newRow_shiftsSectionDividersAndExecutesTransactionally() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        manager.insertSensorFieldAt(
            10L,
            2,
            -1,
            SensorType.CADENCE,
            ViewSize.LARGE,
            null,
            FilterType.INSTANTANEOUS,
            1.0
        )

        verify(exactly = 1) { mockDb.beginTransaction() }
        verify(atLeast = 1) {
            mockDb.execSQL(
                match { it.contains("UPDATE LayoutRowsTable SET RowNr=RowNr+1 WHERE ViewId=? AND RowNr >= ?") },
                match<Array<Any>> { it.size == 2 && it[0] == 10L && it[1] == 2 }
            )
        }
        verify(atLeast = 1) {
            mockDb.execSQL(
                match { it.contains("UPDATE SectionDividersTable SET RowNr=RowNr+1 WHERE ViewId=? AND RowNr >= ?") },
                match<Array<Any>> { it.size == 2 && it[0] == 10L && it[1] == 2 }
            )
        }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.endTransaction() }
    }

    @Test
    fun testInsertSensorFieldAt_parameterAndBoundaryValidation() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        // Invalid tabViewId <= 0
        manager.insertSensorFieldAt(
            0L,
            1,
            -1,
            SensorType.CADENCE,
            ViewSize.LARGE,
            null,
            FilterType.INSTANTANEOUS,
            0.0
        )
        manager.insertSensorFieldAt(
            -5L,
            1,
            -1,
            SensorType.CADENCE,
            ViewSize.LARGE,
            null,
            FilterType.INSTANTANEOUS,
            0.0
        )

        // Invalid rowNr < 1
        manager.insertSensorFieldAt(
            10L,
            0,
            -1,
            SensorType.CADENCE,
            ViewSize.LARGE,
            null,
            FilterType.INSTANTANEOUS,
            0.0
        )
        manager.insertSensorFieldAt(
            10L,
            -1,
            -1,
            SensorType.CADENCE,
            ViewSize.LARGE,
            null,
            FilterType.INSTANTANEOUS,
            0.0
        )

        // Verify zero transactions or queries initiated for invalid inputs
        verify(exactly = 0) { mockDb.beginTransaction() }
    }

    @Test
    fun testCopyTabViewToActivityTypes_emptyTargetList_returnsEmptyList() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val result = manager.copyTabViewToActivityTypes(10L, emptyList())

        assertTrue(result.isEmpty())
        verify(exactly = 0) { mockDb.beginTransaction() }
    }

    @Test
    fun testCopyTabViewToActivityTypes_copiesViewsRowsAndDividersTransactionally() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val viewCursor = mockk<Cursor>(relaxed = true)
        every { viewCursor.moveToFirst() } returns true
        every { viewCursor.columnCount } returns 3
        every { viewCursor.getColumnName(0) } returns "_id"
        every { viewCursor.getType(0) } returns Cursor.FIELD_TYPE_INTEGER
        every { viewCursor.getLong(0) } returns 10L
        every { viewCursor.getColumnName(1) } returns "Name"
        every { viewCursor.getType(1) } returns Cursor.FIELD_TYPE_STRING
        every { viewCursor.getString(1) } returns "Custom Tab"
        every { viewCursor.getColumnName(2) } returns "ShowLapButton"
        every { viewCursor.getType(2) } returns Cursor.FIELD_TYPE_INTEGER
        every { viewCursor.getLong(2) } returns 1L

        val rowsCursor = mockk<Cursor>(relaxed = true)
        every { rowsCursor.moveToNext() } returnsMany listOf(true, false)
        every { rowsCursor.columnCount } returns 2
        every { rowsCursor.getColumnName(0) } returns "RowId"
        every { rowsCursor.getType(0) } returns Cursor.FIELD_TYPE_INTEGER
        every { rowsCursor.getLong(0) } returns 100L
        every { rowsCursor.getColumnName(1) } returns "SensorType"
        every { rowsCursor.getType(1) } returns Cursor.FIELD_TYPE_STRING
        every { rowsCursor.getString(1) } returns "CADENCE"

        val dividersCursor = mockk<Cursor>(relaxed = true)
        every { dividersCursor.moveToNext() } returnsMany listOf(true, false)
        every { dividersCursor.columnCount } returns 2
        every { dividersCursor.getColumnName(0) } returns "_id"
        every { dividersCursor.getType(0) } returns Cursor.FIELD_TYPE_INTEGER
        every { dividersCursor.getLong(0) } returns 200L
        every { dividersCursor.getColumnName(1) } returns "Title"
        every { dividersCursor.getType(1) } returns Cursor.FIELD_TYPE_STRING
        every { dividersCursor.getString(1) } returns "Main Section"

        val maxLayoutCursor = mockk<Cursor>(relaxed = true)
        every { maxLayoutCursor.moveToFirst() } returns true
        every { maxLayoutCursor.isNull(0) } returns false
        every { maxLayoutCursor.getInt(0) } returns 2

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(), any(), any(), any(), any(), any()
            )
        } returns viewCursor

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                any(), any(), any(), any(), any(), any()
            )
        } returns rowsCursor

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE,
                any(), any(), any(), any(), any(), any()
            )
        } returns dividersCursor

        every {
            mockDb.rawQuery(any(), any())
        } returns maxLayoutCursor

        every {
            mockDb.insert(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE, null, any())
        } returns 11L

        every {
            mockDb.insert(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE, null, any())
        } returns 101L

        every {
            mockDb.insert(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, null, any())
        } returns 201L

        val result = manager.copyTabViewToActivityTypes(10L, listOf(com.atrainingtracker.banalservice.ActivityType.BIKE_POWER))

        assertEquals(1, result.size)
        assertEquals(11L, result[0])
        verify(exactly = 1) { mockDb.beginTransaction() }
        verify(exactly = 1) { mockDb.insert(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE, null, any()) }
        verify(exactly = 1) { mockDb.insert(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE, null, any()) }
        verify(exactly = 1) { mockDb.insert(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SECTION_DIVIDERS_TABLE, null, any()) }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.endTransaction() }
    }

    @Test
    fun testMoveTabView_viewNotFound_returnsMinusOne() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns false
        every { cursor.moveToNext() } returns false
        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(), any(), any(), any(), any(), any()
            )
        } returns cursor

        val result = manager.moveTabView(999L, true)
        assertEquals(-1, result)
        verify(exactly = 0) { mockDb.beginTransaction() }
    }

    @Test
    fun testMoveTabView_atLeftBoundary_returnsZeroAndDoesNotModify() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val actTypeCursor = mockk<Cursor>(relaxed = true)
        every { actTypeCursor.moveToFirst() } returns true
        every { actTypeCursor.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE) } returns 0
        every { actTypeCursor.getString(0) } returns "GENERIC_HR"

        val tabsCursor = mockk<Cursor>(relaxed = true)
        every { tabsCursor.moveToNext() } returnsMany listOf(true, true, false)
        every { tabsCursor.getLong(0) } returnsMany listOf(10L, 20L)

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
                arrayOf("10"),
                any(), any(), any()
            )
        } returns actTypeCursor

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE}=?",
                arrayOf("GENERIC_HR"),
                null, null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR} ASC, ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID} ASC"
            )
        } returns tabsCursor

        val result = manager.moveTabView(10L, true)
        assertEquals(0, result)
        verify(exactly = 0) { mockDb.beginTransaction() }
    }

    @Test
    fun testMoveTabView_swapsAdjacentTabsAndNormalizesLayoutNrAtomically() {
        val manager = TrackingViewsDatabaseManager.getInstance(mockContext)
        injectMockDatabase(manager, mockDb)

        val actTypeCursor = mockk<Cursor>(relaxed = true)
        every { actTypeCursor.moveToFirst() } returns true
        every { actTypeCursor.getColumnIndex(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE) } returns 0
        every { actTypeCursor.getString(0) } returns "BIKE_POWER"

        val tabsCursor = mockk<Cursor>(relaxed = true)
        every { tabsCursor.moveToNext() } returnsMany listOf(true, true, true, false)
        every { tabsCursor.getLong(0) } returnsMany listOf(10L, 20L, 30L)

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
                arrayOf("20"),
                any(), any(), any()
            )
        } returns actTypeCursor

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                arrayOf(TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ACTIVITY_TYPE}=?",
                arrayOf("BIKE_POWER"),
                null, null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.LAYOUT_NR} ASC, ${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID} ASC"
            )
        } returns tabsCursor

        val result = manager.moveTabView(20L, false)
        assertEquals(2, result)
        verify(exactly = 1) { mockDb.beginTransaction() }
        verify(exactly = 1) { mockDb.setTransactionSuccessful() }
        verify(exactly = 1) { mockDb.endTransaction() }

        verify(exactly = 3) {
            mockDb.update(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEWS_TABLE,
                any(),
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.C_ID}=?",
                any()
            )
        }
    }
}
