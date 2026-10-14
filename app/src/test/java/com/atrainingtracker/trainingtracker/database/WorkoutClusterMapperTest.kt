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

import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager.WorkoutClusterContract
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit and contract tests for cursor-to-entity mapping in [WorkoutClusterDatabaseManager]
 * and [WorkoutClusterRepository] enforcing Rule 20, Rule 36, REQ-DAT-025, and TST-DAT-020.
 */
class WorkoutClusterMapperTest {

    private lateinit var mockContext: Context
    private lateinit var manager: WorkoutClusterDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
        WorkoutClusterDatabaseManager.resetForTesting(null)
        manager = WorkoutClusterDatabaseManager.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        WorkoutClusterDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun testWorkoutCluster_fromCursor_extractsAllFields() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            BaseColumns._ID to 0,
            WorkoutClusterContract.COLUMN_NAME to 1,
            WorkoutClusterContract.COLUMN_PROBABLE_SPORT_ID to 2,
            WorkoutClusterContract.COLUMN_START_LAT to 3,
            WorkoutClusterContract.COLUMN_START_LNG to 4,
            WorkoutClusterContract.COLUMN_END_LAT to 5,
            WorkoutClusterContract.COLUMN_END_LNG to 6,
            WorkoutClusterContract.COLUMN_MAX_DISP_LAT to 7,
            WorkoutClusterContract.COLUMN_MAX_DISP_LNG to 8,
            WorkoutClusterContract.COLUMN_REF_DISTANCE to 9,
            WorkoutClusterContract.COLUMN_HIT_COUNT to 10,
            WorkoutClusterContract.COLUMN_SPORT_TYPE to 11,
            WorkoutClusterContract.COLUMN_BOUND_MIN_LAT to 12,
            WorkoutClusterContract.COLUMN_BOUND_MIN_LNG to 13,
            WorkoutClusterContract.COLUMN_BOUND_MAX_LAT to 14,
            WorkoutClusterContract.COLUMN_BOUND_MAX_LNG to 15,
            WorkoutClusterContract.COLUMN_PREVIEW_PATHS to 16,
            WorkoutClusterContract.COLUMN_ROUTE_POLYLINE to 17,
            WorkoutClusterContract.COLUMN_MIN_ALT_LAT to 18,
            WorkoutClusterContract.COLUMN_MIN_ALT_LNG to 19,
            WorkoutClusterContract.COLUMN_MAX_ALT_LAT to 20,
            WorkoutClusterContract.COLUMN_MAX_ALT_LNG to 21,
            WorkoutClusterContract.COLUMN_HAS_COUNTER to 22
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: error("Column not found: ${firstArg<String>()}") }
        every { cursor.getColumnIndex(any()) } answers { columnMap[firstArg()] ?: -1 }

        every { cursor.getLong(0) } returns 15L
        every { cursor.getString(1) } returns "River Trail"
        every { cursor.getLong(2) } returns 1L
        every { cursor.getDouble(3) } returns 48.1
        every { cursor.getDouble(4) } returns 11.5
        every { cursor.getDouble(5) } returns 48.2
        every { cursor.getDouble(6) } returns 11.6
        every { cursor.getDouble(7) } returns 48.3
        every { cursor.getDouble(8) } returns 11.7
        every { cursor.getDouble(9) } returns 12000.0
        every { cursor.getInt(10) } returns 8
        every { cursor.getString(11) } returns "BIKE"
        every { cursor.isNull(12) } returns false
        every { cursor.getDouble(12) } returns 48.05
        every { cursor.isNull(13) } returns false
        every { cursor.getDouble(13) } returns 11.45
        every { cursor.isNull(14) } returns false
        every { cursor.getDouble(14) } returns 48.35
        every { cursor.isNull(15) } returns false
        every { cursor.getDouble(15) } returns 11.75
        every { cursor.isNull(16) } returns true
        every { cursor.isNull(17) } returns true
        every { cursor.isNull(18) } returns true
        every { cursor.isNull(19) } returns true
        every { cursor.isNull(20) } returns true
        every { cursor.isNull(21) } returns true
        every { cursor.isNull(22) } returns false
        every { cursor.getInt(22) } returns 1

        val cluster = manager.fromCursor(cursor)

        assertEquals(15L, cluster.id)
        assertEquals("River Trail", cluster.name)
        assertEquals(1L, cluster.probableSportId)
        assertEquals(48.1, cluster.startLat, 0.001)
        assertEquals(11.5, cluster.startLng, 0.001)
        assertEquals(12000.0, cluster.refDistance, 0.1)
        assertEquals(8, cluster.hitCount)
        assertEquals(BSportType.BIKE, cluster.bSportType)
        assertTrue(cluster.hasCounter)
        assertEquals(48.05, cluster.minLat ?: 0.0, 0.001)
    }

    @Test
    fun testWorkoutClusterRepository_fromCursorBatchKeys() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID to 0,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME to 1,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID to 2
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: -1 }

        var row = 0
        every { cursor.moveToNext() } answers {
            row++
            row <= 3
        }
        every { cursor.getLong(0) } answers { 100L + row }
        every { cursor.isNull(1) } returns false
        every { cursor.getString(1) } answers { "workout_$row.fit" }
        every { cursor.getLong(2) } answers { if (row == 1) -1L else 50L + row }

        val batchKeys = WorkoutClusterRepository.fromCursorBatchKeys(cursor)

        assertEquals(listOf(101L, 102L, 103L), batchKeys.idList)
        assertEquals(listOf("workout_1.fit", "workout_2.fit", "workout_3.fit"), batchKeys.fileNameList)
        // clusterId == -1 should be excluded per existing contract
        assertEquals(setOf(52L, 53L), batchKeys.clusterIdList)
        verify(exactly = 0) { cursor.close() }
    }

    @Test
    fun testWorkoutBatchKey_fromCursor_singleRowSymmetry() {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnMap = mapOf(
            WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID to 0,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.FILE_BASE_NAME to 1,
            WorkoutSummariesDatabaseManager.WorkoutSummaries.CLUSTER_ID to 2
        )
        every { cursor.getColumnIndexOrThrow(any()) } answers { columnMap[firstArg()] ?: -1 }
        every { cursor.getLong(0) } returns 999L
        every { cursor.isNull(1) } returns false
        every { cursor.getString(1) } returns "session_999.tcx"
        every { cursor.getLong(2) } returns 77L

        val key = WorkoutBatchKey.fromCursor(cursor)

        assertEquals(999L, key.id)
        assertEquals("session_999.tcx", key.fileName)
        assertEquals(77L, key.clusterId)
        verify(exactly = 0) { cursor.moveToNext() }
        verify(exactly = 0) { cursor.close() }
    }
}
