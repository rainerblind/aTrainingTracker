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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Devices.db v11 migration and SPEED_AND_LOCATION_ATT_FUSED registration
 * (REQ-SEN-028.1, TST-SEN-028.1, ATT-7751).
 */
class DevicesDatabaseManagerMigrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private val capturedValues = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        capturedValues.clear()
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            capturedValues[firstArg()] = secondArg()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } answers {
            capturedValues[firstArg()] = secondArg()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } answers {
            capturedValues[firstArg()] = secondArg()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } answers {
            capturedValues[firstArg()] = secondArg()
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Boolean>()) } answers {
            capturedValues[firstArg()] = secondArg()
        }
        every { anyConstructed<ContentValues>().getAsBoolean(any<String>()) } answers {
            capturedValues[firstArg()] as? Boolean
        }
        every { anyConstructed<ContentValues>().getAsString(any<String>()) } answers {
            capturedValues[firstArg()] as? String
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDbVersionIs11() {
        assertEquals("DevicesDbHelper.DB_VERSION must be 11", 11, DevicesDatabaseManager.DevicesDbHelper.DB_VERSION)
    }

    @Test
    fun testMigrateAddSpeedAndLocationAttFused_whenNotExists_insertsRowWithPairedFalse() {
        val helper = DevicesDatabaseManager.DevicesDbHelper(mockContext)

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns false
        every { cursor.close() } returns Unit

        val whereClause = "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL}=? AND " +
            "${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE}=?"

        every {
            mockDb.query(
                DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                arrayOf(DevicesDatabaseManager.DevicesDbHelper.C_ID),
                whereClause,
                arrayOf(Protocol.SMARTPHONE.name, DeviceType.SPEED_AND_LOCATION_ATT_FUSED.name),
                null, null, null
            )
        } returns cursor

        val valuesSlot = slot<ContentValues>()
        every {
            mockDb.insert(DevicesDatabaseManager.DevicesDbHelper.DEVICES, null, capture(valuesSlot))
        } returns 101L

        val resultId = helper.migrateAddSpeedAndLocationAttFused(mockDb)

        assertEquals(101L, resultId)
        val insertedValues = valuesSlot.captured
        assertEquals(
            Protocol.SMARTPHONE.name,
            insertedValues.getAsString(DevicesDatabaseManager.DevicesDbHelper.PROTOCOL)
        )
        assertEquals(
            DeviceType.SPEED_AND_LOCATION_ATT_FUSED.name,
            insertedValues.getAsString(DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE)
        )
        assertEquals(false, insertedValues.getAsBoolean(DevicesDatabaseManager.DevicesDbHelper.PAIRED))
    }

    @Test
    fun testMigrateAddSpeedAndLocationAttFused_whenAlreadyExists_isIdempotent() {
        val helper = DevicesDatabaseManager.DevicesDbHelper(mockContext)

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getLong(0) } returns 42L
        every { cursor.close() } returns Unit

        val whereClause = "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL}=? AND " +
            "${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE}=?"

        every {
            mockDb.query(
                DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                arrayOf(DevicesDatabaseManager.DevicesDbHelper.C_ID),
                whereClause,
                arrayOf(Protocol.SMARTPHONE.name, DeviceType.SPEED_AND_LOCATION_ATT_FUSED.name),
                null, null, null
            )
        } returns cursor

        val resultId = helper.migrateAddSpeedAndLocationAttFused(mockDb)

        assertEquals(42L, resultId)
        verify(exactly = 0) { mockDb.insert(any(), any(), any()) }
    }

    @Test
    fun testAddLocationDevicesToDb_insertsAttFusedDeviceWithPairedFalse() {
        val helper = DevicesDatabaseManager.DevicesDbHelper(mockContext)

        data class InsertedRow(val protocol: String?, val deviceType: String?, val paired: Boolean?)
        val insertedRows = mutableListOf<InsertedRow>()
        every {
            mockDb.insert(
                any(),
                any(),
                any<ContentValues>()
            )
        } answers {
            val cv = thirdArg<ContentValues>()
            insertedRows.add(
                InsertedRow(
                    protocol = cv.getAsString(DevicesDatabaseManager.DevicesDbHelper.PROTOCOL),
                    deviceType = cv.getAsString(DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE),
                    paired = cv.getAsBoolean(DevicesDatabaseManager.DevicesDbHelper.PAIRED)
                )
            )
            1L
        }

        helper.addLocationDevicesToDb(mockDb)

        val attFusedEntry = insertedRows.find {
            it.deviceType == DeviceType.SPEED_AND_LOCATION_ATT_FUSED.name
        }

        assertTrue("SPEED_AND_LOCATION_ATT_FUSED must be inserted by addLocationDevicesToDb", attFusedEntry != null)
        assertEquals(Protocol.SMARTPHONE.name, attFusedEntry?.protocol)
        assertEquals(false, attFusedEntry?.paired)
    }
}
