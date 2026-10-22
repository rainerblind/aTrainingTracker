/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.exporter.db.StravaUploadDbHelper
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * TST-DAT-026.3 / TST-DAT-026.6: Unit tests verifying DatabaseConnectionManager
 * checkpoint retry backoff on BUSY, singleton reuse without leaking helper instances,
 * and shared databaseLock contract (REQ-DAT-031.3, REQ-DAT-031.4).
 */
class DatabaseConnectionManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        val mockAppContext = mockk<Context>(relaxed = true)
        every { mockContext.applicationContext } returns mockAppContext
        every { mockAppContext.applicationContext } returns mockAppContext

        mockDb = mockk(relaxed = true)
        every { mockDb.isOpen } returns true

        StravaUploadDbHelper.resetForTesting()
        EquipmentDbHelper.resetForTesting()
        ActiveDevicesDbHelper.resetForTesting()
    }

    @After
    fun tearDown() {
        StravaUploadDbHelper.resetForTesting()
        EquipmentDbHelper.resetForTesting()
        ActiveDevicesDbHelper.resetForTesting()
        unmockkAll()
    }

    @Test
    fun testCheckpointWithRetry_returnsTrueWhenNotBusy() {
        val mockCursor = mockk<Cursor>()
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getInt(0) } returns 0 // 0 = not busy
        every { mockCursor.close() } just Runs
        every { mockDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null) } returns mockCursor

        val success = DatabaseConnectionManager.checkpointWithRetry(mockDb, mode = "TRUNCATE", maxRetries = 2)
        assertTrue("checkpointWithRetry must return true when busy == 0", success)
        verify(exactly = 1) { mockDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null) }
    }

    @Test
    fun testCheckpointWithRetry_retriesAndSucceedsAfterInitialBusy() {
        val busyCursor = mockk<Cursor>()
        every { busyCursor.moveToFirst() } returns true
        every { busyCursor.getInt(0) } returns 1 // 1 = busy
        every { busyCursor.close() } just Runs

        val okCursor = mockk<Cursor>()
        every { okCursor.moveToFirst() } returns true
        every { okCursor.getInt(0) } returns 0 // 0 = not busy
        every { okCursor.close() } just Runs

        every { mockDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null) } returnsMany listOf(busyCursor, okCursor)

        val success = DatabaseConnectionManager.checkpointWithRetry(mockDb, mode = "TRUNCATE", maxRetries = 2)
        assertTrue("checkpointWithRetry must succeed after retry", success)
        verify(exactly = 2) { mockDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null) }
    }

    @Test
    fun testCheckpointWithRetry_returnsFalseWhenContinuouslyBusy() {
        val busyCursor = mockk<Cursor>()
        every { busyCursor.moveToFirst() } returns true
        every { busyCursor.getInt(0) } returns 1 // 1 = busy
        every { busyCursor.close() } just Runs

        every { mockDb.rawQuery("PRAGMA wal_checkpoint(FULL);", null) } returns busyCursor

        val success = DatabaseConnectionManager.checkpointWithRetry(mockDb, mode = "FULL", maxRetries = 2)
        assertFalse("checkpointWithRetry must return false when retries are exhausted", success)
        verify(exactly = 3) { mockDb.rawQuery("PRAGMA wal_checkpoint(FULL);", null) }
    }

    @Test
    fun testGetActiveDatabases_reusesSingletonsAcrossInvocations() {
        val helper1 = StravaUploadDbHelper.getInstance(mockContext)
        val helper2 = EquipmentDbHelper.getInstance(mockContext)
        val helper3 = ActiveDevicesDbHelper.getInstance(mockContext)

        val list1 = DatabaseConnectionManager.getActiveDatabases(mockContext)
        val list2 = DatabaseConnectionManager.getActiveDatabases(mockContext)

        // Singletons must not be replaced
        assertSame(helper1, StravaUploadDbHelper.getInstance(mockContext))
        assertSame(helper2, EquipmentDbHelper.getInstance(mockContext))
        assertSame(helper3, ActiveDevicesDbHelper.getInstance(mockContext))
    }

    @Test
    fun testDatabaseLock_isAccessibleAndReentrant() {
        assertNotNull(DatabaseConnectionManager.databaseLock)
        val writeLock = DatabaseConnectionManager.databaseLock.writeLock()
        writeLock.lock()
        try {
            assertTrue("Lock must be held", DatabaseConnectionManager.databaseLock.isWriteLockedByCurrentThread)
        } finally {
            writeLock.unlock()
        }
    }
}
