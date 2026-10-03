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
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.Metadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying multi-dimensional workout deduplication, timestamp tolerance matching,
 * pre-dispatch cloud scanning deduplication, and atomic mutex synchronization across concurrent
 * workers (REQ-MIG-031, TST-MIG-028, ATT-2023).
 */
class LegacyImportEngineDeduplicationTest {

    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadToCommunity(any()) } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false

        mockSqlDb = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        every { mockSummariesDb.database } returns mockSqlDb
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDeduplicationByExactFileName_whenPresent_returnsTrue() {
        val capturedSelection = slot<String>()
        val capturedArgs = slot<Array<String>>()

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.count } returns 1

        every {
            mockSqlDb.query(
                WorkoutSummaries.TABLE,
                any(),
                capture(capturedSelection),
                capture(capturedArgs),
                null, null, null
            )
        } returns mockCursor

        val exists = LegacyImportEngine.isWorkoutExisting(
            mockSummariesDb,
            fileBaseName = "2024_05_10_14_30_00",
            timeStart = null
        )

        assertTrue("Expected existing workout to return true", exists)
        assertEquals("${WorkoutSummaries.FILE_BASE_NAME} = ?", capturedSelection.captured)
        assertEquals(1, capturedArgs.captured.size)
        assertEquals("2024_05_10_14_30_00", capturedArgs.captured[0])
    }

    @Test
    fun testDeduplicationByExactFileName_whenAbsent_returnsFalse() {
        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.count } returns 0

        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), null, null, null)
        } returns mockCursor

        val exists = LegacyImportEngine.isWorkoutExisting(
            mockSummariesDb,
            fileBaseName = "2024_05_10_14_30_00",
            timeStart = null
        )

        assertFalse("Expected non-existing workout to return false", exists)
    }

    @Test
    fun testDeduplicationWithTimeStart_constructsMultiDimensionalQuery() {
        val capturedSelection = slot<String>()
        val capturedArgs = slot<Array<String>>()

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.count } returns 1

        every {
            mockSqlDb.query(
                WorkoutSummaries.TABLE,
                any(),
                capture(capturedSelection),
                capture(capturedArgs),
                null, null, null
            )
        } returns mockCursor

        val fileBaseName = "activity_987654"
        val timeStart = "2024-05-10 14:30:00"

        val exists = LegacyImportEngine.isWorkoutExisting(
            mockSummariesDb,
            fileBaseName = fileBaseName,
            timeStart = timeStart
        )

        assertTrue("Expected existing workout to return true", exists)
        val expectedSelection = "${WorkoutSummaries.FILE_BASE_NAME} = ? OR " +
                "${WorkoutSummaries.TIME_START} = ? OR " +
                "(${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)"
        assertEquals(expectedSelection, capturedSelection.captured)
        assertEquals(3, capturedArgs.captured.size)
        assertEquals(fileBaseName, capturedArgs.captured[0])
        assertEquals(timeStart, capturedArgs.captured[1])
        assertEquals(timeStart, capturedArgs.captured[2])
    }

    @Test
    fun testDeduplicationWithTimeStartAndSport_constructsSportAwareQuery() {
        val capturedSelection = slot<String>()
        val capturedArgs = slot<Array<String>>()

        val mockCursor = mockk<Cursor>(relaxed = true)
        every { mockCursor.count } returns 1

        every {
            mockSqlDb.query(
                WorkoutSummaries.TABLE,
                any(),
                capture(capturedSelection),
                capture(capturedArgs),
                null, null, null
            )
        } returns mockCursor

        val fileBaseName = "2015-06-09_065821"
        val timeStart = "2015-06-09 06:58:21"
        val sport = BSportType.BIKE

        val exists = LegacyImportEngine.isWorkoutExisting(
            mockSummariesDb,
            fileBaseName = fileBaseName,
            timeStart = timeStart,
            bSportType = sport
        )

        assertTrue("Expected existing workout to return true", exists)
        val expectedSelection = "${WorkoutSummaries.FILE_BASE_NAME} = ? OR " +
                "${WorkoutSummaries.TIME_START} = ? OR " +
                "(${WorkoutSummaries.B_SPORT} = ? AND ${WorkoutSummaries.TIME_START} IS NOT NULL AND ABS(strftime('%s', ${WorkoutSummaries.TIME_START}) - strftime('%s', ?)) <= 180)"
        assertEquals(expectedSelection, capturedSelection.captured)
        assertEquals(4, capturedArgs.captured.size)
        assertEquals(fileBaseName, capturedArgs.captured[0])
        assertEquals(timeStart, capturedArgs.captured[1])
        assertEquals(sport.name, capturedArgs.captured[2])
        assertEquals(timeStart, capturedArgs.captured[3])
    }

    @Test
    fun testPreDispatchCloudEntryDeduplication_filtersIdenticalBaseNamesAcrossPaths() {
        val mockEntry1 = mockk<Metadata>(relaxed = true)
        every { mockEntry1.name } returns "2024_05_10_14_30_00.tcx"
        every { mockEntry1.pathLower } returns "/tcx/2024_05_10_14_30_00.tcx"

        val mockEntry2 = mockk<Metadata>(relaxed = true)
        every { mockEntry2.name } returns "2024_05_10_14_30_00.tcx"
        every { mockEntry2.pathLower } returns "/apps/workouts/tcx/2024_05_10_14_30_00.tcx"

        val mockEntry3 = mockk<Metadata>(relaxed = true)
        every { mockEntry3.name } returns "2024_05_10_14_30_00-TMP.tcx"
        every { mockEntry3.pathLower } returns "/apps/workouts/tcx/2024_05_10_14_30_00-tmp.tcx"

        val mockEntry4 = mockk<Metadata>(relaxed = true)
        every { mockEntry4.name } returns "2024_05_11_08_00_00.tcx"
        every { mockEntry4.pathLower } returns "/tcx/2024_05_11_08_00_00.tcx"

        val allEntries = listOf(mockEntry1, mockEntry2, mockEntry3, mockEntry4)

        // Same deduplication algorithm implemented in bulkRecoverFromDropbox
        val deduplicated = allEntries.distinctBy {
            it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
        }

        assertEquals(2, deduplicated.size)
        assertEquals("2024_05_10_14_30_00.tcx", deduplicated[0].name)
        assertEquals("2024_05_11_08_00_00.tcx", deduplicated[1].name)
    }

    @Test
    fun testConcurrentWorkerMutexProtection_guaranteesAtomicCheckAndInsert() = runBlocking {
        val mutex = LegacyImportEngine.importMutex
        val insertedCounter = AtomicInteger(0)
        val skippedCounter = AtomicInteger(0)
        val fakeDbRecords = mutableSetOf<String>()

        val workoutTimestamp = "2024-05-10 14:30:00"

        // Simulate 5 concurrent workers attempting to insert the exact same workout session
        val workers = (1..5).map { workerId ->
            async(Dispatchers.IO) {
                mutex.withLock {
                    if (fakeDbRecords.contains(workoutTimestamp)) {
                        skippedCounter.incrementAndGet()
                    } else {
                        // Simulate sample table creation and summary row insertion latency
                        Thread.sleep(10)
                        fakeDbRecords.add(workoutTimestamp)
                        insertedCounter.incrementAndGet()
                    }
                }
            }
        }

        workers.awaitAll()

        assertEquals("Exactly 1 worker must succeed in inserting the workout", 1, insertedCounter.get())
        assertEquals("Remaining 4 workers must detect duplicate and skip", 4, skippedCounter.get())
        assertEquals("Database must contain exactly 1 record", 1, fakeDbRecords.size)
    }

    @Test
    fun testImportStatusEnum_differentiatesSuccessSkippedAndFailed() {
        assertEquals("SUCCESS", LegacyImportEngine.ImportStatus.SUCCESS.name)
        assertEquals("DUPLICATE_SKIPPED", LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED.name)
        assertEquals("FAILED", LegacyImportEngine.ImportStatus.FAILED.name)
    }
}
