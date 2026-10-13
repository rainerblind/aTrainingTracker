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

package com.atrainingtracker.banalservice.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.testutil.MockCursorFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit verification suite for sport-specific sensor ordering in [DevicesDatabaseManager.getSensorsForSportType]
 * (REQ-UI-283, TST-UI-243.3, ATT-2464).
 */
class DevicesDatabaseManagerOrderingTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetSensorsForSportType_bike_returnsPrioritizedOrder() {
        // Cursor simulates alphabetical query return from SQLite
        val cursor = MockCursorFactory.create(
            columns = listOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.NAME,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE
            ),
            rows = listOf(
                listOf(1L, "Garmin HRM-Dual", "HRM"),
                listOf(2L, "Garmin Tempe", "ENVIRONMENT"),
                listOf(3L, "Stages Power", "BIKE_POWER"),
                listOf(4L, "Wahoo SPEED", "BIKE_SPEED")
            )
        )

        every {
            mockDb.query(
                any<String>(),
                any<Array<String>>(),
                any<String>(),
                isNull(),
                isNull(),
                isNull(),
                any<String>()
            )
        } returns cursor

        val manager = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        val result = manager.getSensorsForSportType(BSportType.BIKE)

        assertEquals(4, result.size)
        // Bike priority: Power (3) -> Speed (4) -> HRM (1) -> Environment (2)
        assertEquals(listOf(3L, 4L, 1L, 2L), result.map { it.id })
        assertEquals(listOf("Stages Power", "Wahoo SPEED", "Garmin HRM-Dual", "Garmin Tempe"), result.map { it.name })
        assertEquals(DeviceType.BIKE_POWER, result[0].deviceType)
        assertEquals(DeviceType.BIKE_SPEED, result[1].deviceType)
        assertEquals(DeviceType.HRM, result[2].deviceType)
        assertEquals(DeviceType.ENVIRONMENT, result[3].deviceType)
    }

    @Test
    fun testGetSensorsForSportType_run_returnsPrioritizedOrder() {
        // Cursor simulates alphabetical query return from SQLite
        val cursor = MockCursorFactory.create(
            columns = listOf(
                DevicesDatabaseManager.DevicesDbHelper.C_ID,
                DevicesDatabaseManager.DevicesDbHelper.NAME,
                DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE
            ),
            rows = listOf(
                listOf(10L, "Garmin HRM-Dual", "HRM"),
                listOf(20L, "Garmin Tempe", "ENVIRONMENT"),
                listOf(30L, "Stryd Footpod", "RUN_SPEED")
            )
        )

        every {
            mockDb.query(
                any<String>(),
                any<Array<String>>(),
                any<String>(),
                isNull(),
                isNull(),
                isNull(),
                any<String>()
            )
        } returns cursor

        val manager = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        val result = manager.getSensorsForSportType(BSportType.RUN)

        assertEquals(3, result.size)
        // Run priority: Footpod/Run speed (30) -> HRM (10) -> Environment (20)
        assertEquals(listOf(30L, 10L, 20L), result.map { it.id })
        assertEquals(listOf("Stryd Footpod", "Garmin HRM-Dual", "Garmin Tempe"), result.map { it.name })
        assertEquals(DeviceType.RUN_SPEED, result[0].deviceType)
        assertEquals(DeviceType.HRM, result[1].deviceType)
        assertEquals(DeviceType.ENVIRONMENT, result[2].deviceType)
    }
}
