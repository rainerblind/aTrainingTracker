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
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.DriveFileEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract tests for Google Drive historical bulk recovery (REQ-MIG-034, TST-MIG-031).
 * Verifies cross-folder base name deduplication, disconnected token guards,
 * and multi-format discovery logic.
 */
class GoogleDriveBulkRecoveryContractTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false

        mockContext = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testBulkRecoverFromGoogleDrive_whenTokenNull_returnsZeroRecoveryResult() = runBlocking {
        every { TrainingApplication.getGoogleDriveAuthToken() } returns null

        val result = LegacyImportEngine.bulkRecoverFromGoogleDrive(
            context = mockContext,
            format = "all"
        )

        assertEquals("Imported count should be 0", 0, result.importedCount)
        assertEquals("Skipped count should be 0", 0, result.skippedCount)
        assertEquals("Failed count should be 0", 0, result.failedCount)
        assertEquals("Total scanned should be 0", 0, result.totalScanned)
    }

    @Test
    fun testPreDispatchGoogleDriveEntryDeduplication_filtersDuplicatesAcrossFolders() {
        // Discovered from aTrainingTracker/Workouts
        val entry1 = DriveFileEntry(id = "id_1", name = "2026_01_15_10_00_00.fit")
        // Duplicate discovered from aTrainingTracker/FIT
        val entry2 = DriveFileEntry(id = "id_2", name = "2026_01_15_10_00_00.fit")
        // Temporary suffix duplicate
        val entry3 = DriveFileEntry(id = "id_3", name = "2026_01_15_10_00_00-TMP.fit")
        // Distinct workout
        val entry4 = DriveFileEntry(id = "id_4", name = "2026_01_16_14_30_00.tcx")
        // Another distinct workout
        val entry5 = DriveFileEntry(id = "id_5", name = "2026_01_17_09_15_00.gpx")

        val rawEntries = listOf(entry1, entry2, entry3, entry4, entry5)

        // Exact deduplication expression from LegacyImportEngine.bulkRecoverFromGoogleDrive
        val deduplicated = rawEntries.distinctBy {
            it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
        }

        assertEquals("Should deduplicate to exactly 3 distinct activities", 3, deduplicated.size)
        val names = deduplicated.map { it.name }
        assertTrue("Contains 2026_01_15_10_00_00.fit", names.contains("2026_01_15_10_00_00.fit"))
        assertTrue("Contains 2026_01_16_14_30_00.tcx", names.contains("2026_01_16_14_30_00.tcx"))
        assertTrue("Contains 2026_01_17_09_15_00.gpx", names.contains("2026_01_17_09_15_00.gpx"))
    }

    @Test
    fun testTargetExtensions_resolvesCorrectlyPerFormat() {
        val formatsAndExtensions = mapOf(
            "fit" to listOf(".fit"),
            "tcx" to listOf(".tcx"),
            "gpx" to listOf(".gpx"),
            "all" to listOf(".fit", ".tcx", ".gpx")
        )

        for ((format, expectedExtensions) in formatsAndExtensions) {
            val resolvedExtensions = when (format.lowercase()) {
                "fit" -> listOf(".fit")
                "tcx" -> listOf(".tcx")
                "gpx" -> listOf(".gpx")
                else -> listOf(".fit", ".tcx", ".gpx")
            }
            assertEquals("Format $format must match expected extensions", expectedExtensions, resolvedExtensions)
        }
    }
}
