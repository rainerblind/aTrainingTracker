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
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.testutil.MockCursorFactory
import io.mockk.every
import io.mockk.mockk
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
 * Unit tests verifying [DevicesDatabaseManager.hasPairedRemoteDevices] query logic,
 * parameter mapping, cursor closing, and boundary conditions (REQ-UI-259, TST-UI-218, ATT-2057).
 */
class DevicesDatabaseManagerTest {

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
    fun testHasPairedRemoteDevices_whenNoMatchingDevicesExist_returnsFalse() {
        val emptyCursor = MockCursorFactory.create(
            columns = listOf("_id"),
            rows = emptyList()
        )

        val selectionSlot = slot<String>()
        val selectionArgsSlot = slot<Array<String>>()
        val limitSlot = slot<String>()

        every {
            mockDb.query(
                any<String>(),
                any<Array<String>>(),
                capture(selectionSlot),
                capture(selectionArgsSlot),
                isNull(),
                isNull(),
                isNull(),
                capture(limitSlot)
            )
        } returns emptyCursor

        val manager = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        val result = manager.hasPairedRemoteDevices()

        assertFalse("Expected false when no paired remote devices exist in DB", result)
        assertTrue("Selection must check paired > 0", selectionSlot.captured.contains("paired > 0"))
        assertEquals("Limit must be 1 for fast query short-circuiting", "1", limitSlot.captured)
        assertEquals(2, selectionArgsSlot.captured.size)
        assertEquals(Protocol.ANT_PLUS.name, selectionArgsSlot.captured[0])
        assertEquals(Protocol.BLUETOOTH_LE.name, selectionArgsSlot.captured[1])

        verify(exactly = 1) { emptyCursor.close() }
    }

    @Test
    fun testHasPairedRemoteDevices_whenPairedDeviceExists_returnsTrue() {
        val populatedCursor = MockCursorFactory.create(
            columns = listOf("_id"),
            rows = listOf(listOf(42L))
        )

        every {
            mockDb.query(
                any<String>(),
                any<Array<String>>(),
                any<String>(),
                any<Array<String>>(),
                isNull(),
                isNull(),
                isNull(),
                any<String>()
            )
        } returns populatedCursor

        val manager = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        val result = manager.hasPairedRemoteDevices()

        assertTrue("Expected true when at least one paired remote device exists", result)
        verify(exactly = 1) { populatedCursor.close() }
    }

    @Test
    fun testHasPairedRemoteDevices_whenQueryThrowsException_returnsFalseWithoutCrash() {
        every {
            mockDb.query(
                any<String>(),
                any<Array<String>>(),
                any<String>(),
                any<Array<String>>(),
                isNull(),
                isNull(),
                isNull(),
                any<String>()
            )
        } throws RuntimeException("Disk I/O error")

        val manager = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        val result = manager.hasPairedRemoteDevices()

        assertFalse("Expected false when database throws exception", result)
    }

    @Test
    fun testHasPairedRemoteDevices_whenDbNullOrClosed_returnsFalse() {
        val managerNull = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase? = null
        }
        assertFalse("Expected false when database is null", managerNull.hasPairedRemoteDevices())

        every { mockDb.isOpen } returns false
        val managerClosed = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }
        assertFalse("Expected false when database is closed", managerClosed.hasPairedRemoteDevices())
    }
}
