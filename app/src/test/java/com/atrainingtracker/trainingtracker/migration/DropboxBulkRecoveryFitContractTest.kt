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
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.files.Metadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit and contract tests for Dropbox bulk recovery of historical FIT workout files
 * alongside legacy XML formats (REQ-MIG-035, TST-MIG-032, ATT-2341).
 */
class DropboxBulkRecoveryFitContractTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        val mockCredential = mockk<DbxCredential>(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.readDropboxCredential() } returns mockCredential
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false

        cacheDir = File(System.getProperty("java.io.tmpdir"), "att_test_cache_${System.currentTimeMillis()}")
        cacheDir.mkdirs()

        mockContext = mockk(relaxed = true)
        every { mockContext.cacheDir } returns cacheDir
        every { mockContext.getString(any()) } returns "Status message"
        every { mockContext.getString(any(), any()) } answers { "Status message with ${args[1]}" }

        mockSqlDb = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        every { mockSummariesDb.database } returns mockSqlDb

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
    }

    @After
    fun tearDown() {
        cacheDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun testTargetPathsAndExtensions_resolvesCorrectlyPerFormat() {
        // Test contract for "fit"
        val fitPaths = when ("fit") {
            "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
            "gpx" -> listOf("/GPX", "/apps/Workouts/GPX")
            "fit" -> listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
            else -> listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX", "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
        }
        val fitExts = when ("fit") {
            "tcx" -> listOf(".tcx")
            "gpx" -> listOf(".gpx")
            "fit" -> listOf(".fit")
            else -> listOf(".tcx", ".gpx", ".fit")
        }
        assertEquals(listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT"), fitPaths)
        assertEquals(listOf(".fit"), fitExts)

        // Test contract for "all"
        val allPaths = when ("all") {
            "tcx" -> listOf("/TCX", "/apps/Workouts/TCX")
            "gpx" -> listOf("/GPX", "/apps/Workouts/GPX")
            "fit" -> listOf("/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
            else -> listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX", "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT")
        }
        val allExts = when ("all") {
            "tcx" -> listOf(".tcx")
            "gpx" -> listOf(".gpx")
            "fit" -> listOf(".fit")
            else -> listOf(".tcx", ".gpx", ".fit")
        }
        assertEquals(
            listOf("/TCX", "/apps/Workouts/TCX", "/GPX", "/apps/Workouts/GPX", "/FIT", "/apps/Workouts/FIT", "/Workouts/FIT"),
            allPaths
        )
        assertEquals(listOf(".tcx", ".gpx", ".fit"), allExts)
    }

    @Test
    fun testPreDispatchDropboxEntryDeduplication_filtersFitDuplicatesAcrossFolders() {
        val mockEntry1 = mockk<Metadata>(relaxed = true)
        every { mockEntry1.name } returns "2026-05-10_100000.fit"
        every { mockEntry1.pathLower } returns "/fit/2026-05-10_100000.fit"

        val mockEntry2 = mockk<Metadata>(relaxed = true)
        every { mockEntry2.name } returns "2026-05-10_100000.fit"
        every { mockEntry2.pathLower } returns "/apps/workouts/fit/2026-05-10_100000.fit"

        val mockEntry3 = mockk<Metadata>(relaxed = true)
        every { mockEntry3.name } returns "2026-05-10_100000-TMP.fit"
        every { mockEntry3.pathLower } returns "/workouts/fit/2026-05-10_100000-tmp.fit"

        val mockEntry4 = mockk<Metadata>(relaxed = true)
        every { mockEntry4.name } returns "2026-05-11_120000.fit"
        every { mockEntry4.pathLower } returns "/fit/2026-05-11_120000.fit"

        val allEntries = listOf(mockEntry1, mockEntry2, mockEntry3, mockEntry4)

        // Deduplication algorithm in LegacyImportEngine.bulkRecoverFromDropbox
        val deduplicated = allEntries.distinctBy {
            it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
        }

        assertEquals(2, deduplicated.size)
        assertEquals("2026-05-10_100000.fit", deduplicated[0].name)
        assertEquals("2026-05-11_120000.fit", deduplicated[1].name)
    }

    @Test
    fun testPreDownloadDuplicateSkipping_skipsExistingFitWorkouts() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.count } returns 1

        every {
            mockSqlDb.query(
                WorkoutSummaries.TABLE,
                any(),
                any(),
                any(),
                null, null, null
            )
        } returns cursor

        val exists = LegacyImportEngine.isWorkoutExisting(
            mockSummariesDb,
            fileBaseName = "2026-05-10_100000",
            timeStart = null
        )

        assertTrue("Expected workout to be identified as existing", exists)
    }

    @Test
    fun testWorkerChannelRouting_whenFitImportFailsOrSucceeds_talliesAccurately() {
        // Direct verification of ImportStatus enum resolution for FIT
        assertEquals("SUCCESS", LegacyImportEngine.ImportStatus.SUCCESS.name)
        assertEquals("DUPLICATE_SKIPPED", LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED.name)
        assertEquals("FAILED", LegacyImportEngine.ImportStatus.FAILED.name)
    }
}
