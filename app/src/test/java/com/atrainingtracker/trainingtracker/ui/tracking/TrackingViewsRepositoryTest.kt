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

package com.atrainingtracker.trainingtracker.ui.tracking

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit test verifying null-safe and robust enum deserialization in TrackingViewsRepository
 * (REQ-UI-253, TST-UI-212).
 */
class TrackingViewsRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockViewsDbManager: TrackingViewsDatabaseManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        mockViewsDbManager = mockk(relaxed = true)
        mockDevicesDbManager = mockk(relaxed = true)

        every { mockViewsDbManager.database } returns mockDb

        mockkStatic(TrackingViewsDatabaseManager::class)
        every { TrackingViewsDatabaseManager.getInstance(any()) } returns mockViewsDbManager

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        resetSingleton()
    }

    @After
    fun tearDown() {
        unmockkAll()
        resetSingleton()
    }

    private fun resetSingleton() {
        try {
            val field: Field = TrackingViewsRepository::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (_: Exception) {
        }
    }

    @Test
    fun fetchSensorFieldConfigs_handlesNullEnumValuesWithoutCrashing() = runBlocking {
        val repository = TrackingViewsRepository.getInstance(mockContext)

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.moveToNext() } returns false

        // Column indices
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID) } returns 0
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR) } returns 1
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR) } returns 2
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE) } returns 3
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE) } returns 4
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT) } returns 5
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE) } returns 6
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID) } returns 7

        // Return nulls for all string enums (simulating fresh install or missing DB columns)
        every { cursor.getLong(0) } returns 101L
        every { cursor.getInt(1) } returns 1
        every { cursor.getInt(2) } returns 1
        every { cursor.getString(3) } returns null // SENSOR_TYPE null
        every { cursor.getString(4) } returns null // FILTER_TYPE null
        every { cursor.getDouble(5) } returns 1.0
        every { cursor.getString(6) } returns null // VIEW_SIZE null -> caused ATT-2179 crash
        every { cursor.getLong(7) } returns 0L

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=?",
                arrayOf("1"),
                null,
                null,
                any()
            )
        } returns cursor

        val configs = repository.getSensorFieldConfigsForView(1L).first()

        assertEquals(1, configs.size)
        val config = configs[0]
        assertEquals(ViewSize.NORMAL, config.viewSize)
        assertEquals(SensorType.TIME_ACTIVE, config.sensorType)
        assertEquals(FilterType.INSTANTANEOUS, config.filterType)
    }

    @Test
    fun fetchSensorFieldConfigs_handlesInvalidEnumValuesGracefully() = runBlocking {
        val repository = TrackingViewsRepository.getInstance(mockContext)

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.moveToNext() } returns false

        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID) } returns 0
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR) } returns 1
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR) } returns 2
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE) } returns 3
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE) } returns 4
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT) } returns 5
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE) } returns 6
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID) } returns 7

        every { cursor.getLong(0) } returns 102L
        every { cursor.getInt(1) } returns 1
        every { cursor.getInt(2) } returns 1
        every { cursor.getString(3) } returns "NON_EXISTENT_SENSOR"
        every { cursor.getString(4) } returns "UNKNOWN_FILTER"
        every { cursor.getDouble(5) } returns 0.0
        every { cursor.getString(6) } returns "GIANT_NOT_AN_ENUM"
        every { cursor.getLong(7) } returns 0L

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_ID}=?",
                arrayOf("1"),
                null,
                null,
                any()
            )
        } returns cursor

        val configs = repository.getSensorFieldConfigsForView(1L).first()

        assertEquals(1, configs.size)
        val config = configs[0]
        assertEquals(ViewSize.NORMAL, config.viewSize)
        assertEquals(SensorType.TIME_ACTIVE, config.sensorType)
        assertEquals(FilterType.INSTANTANEOUS, config.filterType)
    }

    @Test
    fun fetchSingleSensorFieldConfig_handlesNullEnumValues() = runBlocking {
        val repository = TrackingViewsRepository.getInstance(mockContext)

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true

        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.VIEW_SIZE) } returns 6
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SOURCE_DEVICE_ID) } returns 7
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.SENSOR_TYPE) } returns 3
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_TYPE) } returns 4
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.FILTER_CONSTANT) } returns 5
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_NR) } returns 1
        every { cursor.getColumnIndexOrThrow(TrackingViewsDatabaseManager.TrackingViewsDbHelper.COL_NR) } returns 2

        every { cursor.getString(6) } returns null
        every { cursor.getLong(7) } returns 0L
        every { cursor.getString(3) } returns null
        every { cursor.getString(4) } returns null
        every { cursor.getDouble(5) } returns 2.5
        every { cursor.getInt(1) } returns 2
        every { cursor.getInt(2) } returns 1

        every {
            mockDb.query(
                TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROWS_TABLE,
                null,
                "${TrackingViewsDatabaseManager.TrackingViewsDbHelper.ROW_ID}=?",
                arrayOf("200"),
                null,
                null,
                null
            )
        } returns cursor

        val config = repository.getSensorFieldConfig(200L).first()

        assertNotNull(config)
        assertEquals(ViewSize.NORMAL, config!!.viewSize)
        assertEquals(SensorType.TIME_ACTIVE, config.sensorType)
        assertEquals(FilterType.INSTANTANEOUS, config.filterType)
    }
}
