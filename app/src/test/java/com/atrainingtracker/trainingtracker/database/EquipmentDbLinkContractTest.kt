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
import android.util.Log
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Unit verification suite for EquipmentDbHelper atomic link operations and
 * DevicesDatabaseManager remote sensor discovery (REQ-UI-256, TST-UI-215, ATT-2126).
 */
class EquipmentDbLinkContractTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    private val contentValueStores = Collections.synchronizedMap(IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

    private fun io.mockk.MockKAnswerScope<*, *>.getRealInstance(): ContentValues {
        try {
            var obj: Any? = call.invocation.originalCall
            while (obj != null) {
                for (f in obj.javaClass.declaredFields) {
                    if (f.name == "self" || f.name == "\$self" || f.name.endsWith("\$self")) {
                        f.isAccessible = true
                        val s = f.get(obj)
                        if (s is ContentValues && s !== this.self) {
                            return s
                        }
                    }
                }
                val nextField = obj.javaClass.declaredFields.firstOrNull {
                    it.name.contains("originalCall") || it.name.contains("callable")
                }
                obj = nextField?.apply { isAccessible = true }?.get(obj)
            }
        } catch (_: Exception) { }
        return self as ContentValues
    }

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

        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Long>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Int>()
        }
        every { constructedWith<ContentValues>().getAsLong(any<String>()) } answers {
            val cv = getRealInstance()
            (contentValueStores[cv]?.get(firstArg<String>()) as? Number)?.toLong()
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores[cv]?.get(firstArg<String>())?.toString()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            (contentValueStores[cv]?.get(firstArg<String>()) as? Number)?.toInt()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testAddDeviceLink_whenNotPresent_insertsRow() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns false

        every {
            mockDb.query(
                eq("Links"),
                isNull(),
                eq("EquipmentId=? AND ANTDeviceId=?"),
                match { it contentEquals arrayOf("42", "101") },
                isNull(), isNull(), isNull()
            )
        } returns mockCursor

        val insertedValuesSlot = slot<ContentValues>()
        every { mockDb.insert("Links", null, capture(insertedValuesSlot)) } returns 1L

        val helper = object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        }

        helper.addDeviceLink(42L, 101L)

        verify(exactly = 1) { mockDb.insert("Links", null, any()) }
        verify(exactly = 1) { mockCursor.close() }
        assertEquals(42L, insertedValuesSlot.captured.getAsLong("EquipmentId"))
        assertEquals(101L, insertedValuesSlot.captured.getAsLong("ANTDeviceId"))
    }

    @Test
    fun testAddDeviceLink_whenAlreadyPresent_skipsInsert() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns true

        every {
            mockDb.query(
                eq("Links"),
                isNull(),
                eq("EquipmentId=? AND ANTDeviceId=?"),
                match { it contentEquals arrayOf("42", "101") },
                isNull(), isNull(), isNull()
            )
        } returns mockCursor

        val helper = object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        }

        helper.addDeviceLink(42L, 101L)

        verify(exactly = 0) { mockDb.insert("Links", any(), any()) }
        verify(exactly = 1) { mockCursor.close() }
    }

    @Test
    fun testAddDeviceLink_invalidIds_noop() {
        val helper = object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        }

        helper.addDeviceLink(-1L, 100L)
        helper.addDeviceLink(10L, 0L)

        verify(exactly = 0) { mockDb.query(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mockDb.insert(any(), any(), any()) }
    }

    @Test
    fun testRemoveDeviceLink_validIds_deletesMatchingRow() {
        every {
            mockDb.delete(
                eq("Links"),
                eq("EquipmentId=? AND ANTDeviceId=?"),
                match { it contentEquals arrayOf("42", "101") }
            )
        } returns 1

        val helper = object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        }

        helper.removeDeviceLink(42L, 101L)

        verify(exactly = 1) {
            mockDb.delete(
                eq("Links"),
                eq("EquipmentId=? AND ANTDeviceId=?"),
                match { it contentEquals arrayOf("42", "101") }
            )
        }
    }

    @Test
    fun testRemoveDeviceLink_invalidIds_noop() {
        val helper = object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        }

        helper.removeDeviceLink(0L, 100L)
        helper.removeDeviceLink(10L, -5L)

        verify(exactly = 0) { mockDb.delete(any(), any(), any()) }
    }

    @Test
    fun testSetDeviceLink_delegatesCorrectly() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.moveToFirst() } returns false
        every { mockDb.query(any(), any(), any(), any(), any(), any(), any()) } returns mockCursor
        every { mockDb.insert(any(), any(), any()) } returns 1L
        every { mockDb.delete(any(), any(), any()) } returns 1

        val helper = spyk(object : EquipmentDbHelper(mockContext) {
            override fun getWritableDatabase(): SQLiteDatabase = mockDb
        })

        // When isLinked == true -> calls addDeviceLink
        helper.setDeviceLink(55L, 202L, true)
        verify(exactly = 1) { helper.addDeviceLink(55L, 202L) }

        // When isLinked == false -> calls removeDeviceLink
        helper.setDeviceLink(55L, 202L, false)
        verify(exactly = 1) { helper.removeDeviceLink(55L, 202L) }
    }

    @Test
    fun testGetAllRemoteSensors_queriesWithExpectedSelectionAndSortOrder() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.getColumnIndex(any()) } answers {
            when (firstArg<String>()) {
                "_id" -> 0
                "name" -> 1
                "type" -> 2
                else -> -1
            }
        }
        every { mockCursor.isNull(any()) } returns false
        every { mockCursor.moveToNext() } returnsMany listOf(true, true, false)
        every { mockCursor.getLong(0) } returnsMany listOf(1L, 2L)
        every { mockCursor.getString(1) } returnsMany listOf("Garmin HRM-Pro", "Wahoo KICKR")
        every { mockCursor.getString(2) } returnsMany listOf("HRM", "BIKE_POWER")

        val selectionSlot = slot<String>()
        val sortSlot = slot<String>()

        every {
            mockDb.query(
                any<String>(),
                any<Array<String>>(),
                capture(selectionSlot),
                isNull(), isNull(), isNull(),
                capture(sortSlot)
            )
        } returns mockCursor

        val devicesManager = object : DevicesDatabaseManager(mockContext) {
            override fun getDatabase(): SQLiteDatabase = mockDb
        }

        val sensors = devicesManager.getAllRemoteSensors()

        assertEquals(2, sensors.size)
        assertEquals(1L, sensors[0].id)
        assertEquals("Garmin HRM-Pro", sensors[0].name)
        assertEquals(2L, sensors[1].id)
        assertEquals("Wahoo KICKR", sensors[1].name)

        assertTrue(
            "Selection must check NAME IS NOT NULL",
            selectionSlot.captured.contains("name IS NOT NULL", ignoreCase = true)
        )
        assertTrue(
            "Selection must include ANT_PLUS or BLUETOOTH_LE or sensor types",
            selectionSlot.captured.contains("ANT_PLUS") && selectionSlot.captured.contains("BLUETOOTH_LE")
        )
        assertTrue(
            "Sort order must be Name COLLATE NOCASE ASC",
            sortSlot.captured.contains("name COLLATE NOCASE ASC", ignoreCase = true)
        )
    }
}
