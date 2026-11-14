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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying Devices.db v12 migration and ALTITUDE_AND_SLOPE_ATT_FUSED registration
 * (REQ-FIL-016.3, TST-FIL-016.2, ATT-6279).
 */
class DevicesDatabaseManagerMigrationV12Test {

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
    fun testDbVersionIs12() {
        assertEquals("DevicesDbHelper.DB_VERSION must be 12", 12, DevicesDatabaseManager.DevicesDbHelper.DB_VERSION)
    }

    @Test
    fun testMigrateAddAltitudeAndSlopeAttFused_whenNotExists_insertsRowWithPairedFalse() {
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
                arrayOf(Protocol.SMARTPHONE.name, DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED.name),
                null, null, null
            )
        } returns cursor

        val valuesSlot = slot<ContentValues>()
        every {
            mockDb.insert(DevicesDatabaseManager.DevicesDbHelper.DEVICES, null, capture(valuesSlot))
        } returns 102L

        val resultId = helper.migrateAddAltitudeAndSlopeAttFused(mockDb)

        assertEquals(102L, resultId)
        val insertedValues = valuesSlot.captured
        assertEquals(
            Protocol.SMARTPHONE.name,
            insertedValues.getAsString(DevicesDatabaseManager.DevicesDbHelper.PROTOCOL)
        )
        assertEquals(
            DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED.name,
            insertedValues.getAsString(DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE)
        )
        assertEquals(false, insertedValues.getAsBoolean(DevicesDatabaseManager.DevicesDbHelper.PAIRED))
    }

    @Test
    fun testMigrateAddAltitudeAndSlopeAttFused_whenAlreadyExists_isIdempotent() {
        val helper = DevicesDatabaseManager.DevicesDbHelper(mockContext)

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { cursor.getLong(0) } returns 77L
        every { cursor.close() } returns Unit

        val whereClause = "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL}=? AND " +
            "${DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE}=?"

        every {
            mockDb.query(
                DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                arrayOf(DevicesDatabaseManager.DevicesDbHelper.C_ID),
                whereClause,
                arrayOf(Protocol.SMARTPHONE.name, DeviceType.ALTITUDE_AND_SLOPE_ATT_FUSED.name),
                null, null, null
            )
        } returns cursor

        val resultId = helper.migrateAddAltitudeAndSlopeAttFused(mockDb)

        assertEquals("Should return existing ID without re-inserting", 77L, resultId)
    }
}
