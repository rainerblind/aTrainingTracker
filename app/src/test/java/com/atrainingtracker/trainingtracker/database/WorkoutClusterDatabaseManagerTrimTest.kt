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
 */

package com.atrainingtracker.trainingtracker.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager.WorkoutClusterContract
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying DB-layer cluster name normalization and blank-rejection
 * in [WorkoutClusterDatabaseManager] (TST-PER-049.1, REQ-PER-041.1).
 */
class WorkoutClusterDatabaseManagerTrimTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var dbManager: WorkoutClusterDatabaseManager
    private val capturedStringValues = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        mockDb = mockk(relaxed = true)

        capturedStringValues.clear()
        mockkConstructor(ContentValues::class)
        every { anyConstructed<ContentValues>().put(any<String>(), any<String>()) } answers {
            val value = secondArg<String?>()
            if (value != null) capturedStringValues[firstArg()] = value
        }
        every { anyConstructed<ContentValues>().put(any<String>(), any<Long>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<Int>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<Double>()) } just Runs
        every { anyConstructed<ContentValues>().put(any<String>(), any<Boolean>()) } just Runs
        every { anyConstructed<ContentValues>().putNull(any()) } just Runs

        val realManager = WorkoutClusterDatabaseManager.getInstance(mockContext)
        dbManager = spyk(realManager)
        every { dbManager.getDatabase() } returns mockDb
    }

    @After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testInsertAndUpdateCluster_trimsWhitespaceAndRejectsBlank_TST_PER_049_1() {
        val baseCluster = WorkoutCluster(
            id = 42L,
            name = "   ",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.0,
            endLng = 11.0,
            maxDispLat = 48.0,
            maxDispLng = 11.0,
            refDistance = 5000.0,
            hitCount = 1,
            bSportType = BSportType.RUN
        )

        assertEquals(-1L, dbManager.insertCluster(baseCluster))
        verify(exactly = 0) { mockDb.insert(any(), any(), any()) }

        every { mockDb.insert(WorkoutClusterContract.TABLE_NAME, null, any()) } returns 42L
        assertEquals(42L, dbManager.insertCluster(baseCluster.copy(name = "  Kurze Runde  ")))
        assertEquals("Kurze Runde", capturedStringValues[WorkoutClusterContract.COLUMN_NAME])

        dbManager.updateCluster(baseCluster.copy(name = " \t "))
        verify(exactly = 0) { mockDb.update(any(), any(), any(), any()) }

        capturedStringValues.clear()
        dbManager.updateCluster(baseCluster.copy(name = "  Isar Runde  "))
        assertEquals("Isar Runde", capturedStringValues[WorkoutClusterContract.COLUMN_NAME])
        verify(exactly = 1) { mockDb.update(WorkoutClusterContract.TABLE_NAME, any(), any(), arrayOf("42")) }
    }

    @Test
    fun testIsNameTaken_trimsWhitespaceAndRejectsBlank_TST_PER_049_1() {
        assertFalse(dbManager.isNameTaken("   ", -1L))
        verify(exactly = 0) { mockDb.query(any(), any(), any(), any(), any(), any(), any()) }

        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.count } returns 1
        every {
            mockDb.query(
                WorkoutClusterContract.TABLE_NAME,
                any(),
                "${WorkoutClusterContract.COLUMN_NAME} = ?",
                arrayOf("Kurze Runde"),
                null,
                null,
                null
            )
        } returns cursor

        assertTrue(dbManager.isNameTaken("  Kurze Runde  ", -1L))
    }
}
