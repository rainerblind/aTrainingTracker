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
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper.ActiveDevices
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying continuous active device persistence and idempotency in [ActiveDevicesDbHelper]
 * (REQ-CON-007, TST-CON-010.3, ATT-3312).
 */
class ActiveDevicesDbHelperTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var helper: ActiveDevicesDbHelper

    private val contentValuesMap = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        contentValuesMap.clear()
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } answers {
            contentValuesMap[firstArg<String>()] = secondArg<Long>()
        }
        every { anyConstructed<ContentValues>().getAsLong(any<String>()) } answers {
            contentValuesMap[firstArg<String>()] as? Long
        }

        mockContext = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)

        helper = spyk(ActiveDevicesDbHelper(mockContext))
        every { helper.writableDatabase } returns mockDb
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testAddActiveDevice_whenNotExists_insertsRecord() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns false
        every {
            mockDb.query(
                ActiveDevices.TABLE,
                any(),
                any(),
                any(),
                null,
                null,
                null
            )
        } returns cursor

        val capturedValues = slot<ContentValues>()
        every { mockDb.insert(ActiveDevices.TABLE, null, capture(capturedValues)) } returns 1L

        helper.addActiveDevice(101L, 42L)

        verify(exactly = 1) { mockDb.insert(ActiveDevices.TABLE, null, any()) }
        assertEquals(101L, contentValuesMap[ActiveDevices.WORKOUT_ID])
        assertEquals(42L, contentValuesMap[ActiveDevices.DEVICE_DB_ID])
    }

    @Test
    fun testAddActiveDevice_whenAlreadyExists_skipsInsert() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every {
            mockDb.query(
                ActiveDevices.TABLE,
                any(),
                any(),
                any(),
                null,
                null,
                null
            )
        } returns cursor

        helper.addActiveDevice(101L, 42L)

        verify(exactly = 0) { mockDb.insert(any(), any(), any()) }
    }

    @Test
    fun testAddActiveDevice_withInvalidIds_doesNothing() {
        helper.addActiveDevice(0L, 42L)
        helper.addActiveDevice(-1L, 42L)
        helper.addActiveDevice(101L, -1L)

        verify(exactly = 0) { mockDb.query(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mockDb.insert(any(), any(), any()) }
    }
}
