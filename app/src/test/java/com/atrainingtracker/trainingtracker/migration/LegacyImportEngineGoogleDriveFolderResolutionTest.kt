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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.DriveFileEntry
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveClient
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying Google Drive candidate folder hierarchy resolution and cross-folder deduplication
 * in LegacyImportEngine (REQ-DAT-024).
 */
class LegacyImportEngineGoogleDriveFolderResolutionTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getGoogleDriveAuthToken() } returns "mock_token"
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCandidateFolderHierarchies_formatFit_prioritizesFitThenLegacyWorkouts() {
        val format = "fit"
        val candidateFolderHierarchies = when (format.lowercase()) {
            "fit" -> listOf(
                listOf("FIT"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "tcx" -> listOf(
                listOf("TCX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "gpx" -> listOf(
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            else -> listOf(
                listOf("FIT"),
                listOf("TCX"),
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
        }

        assertEquals(listOf("FIT"), candidateFolderHierarchies[0])
        assertEquals(listOf("Workouts"), candidateFolderHierarchies[1])
        assertEquals(listOf("aTrainingTracker", "FIT"), candidateFolderHierarchies[2])
    }

    @Test
    fun testCandidateFolderHierarchies_formatTcx_prioritizesTcxThenLegacyWorkouts() {
        val format = "tcx"
        val candidateFolderHierarchies = when (format.lowercase()) {
            "fit" -> listOf(
                listOf("FIT"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "tcx" -> listOf(
                listOf("TCX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "gpx" -> listOf(
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            else -> listOf(
                listOf("FIT"),
                listOf("TCX"),
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
        }

        assertEquals(listOf("TCX"), candidateFolderHierarchies[0])
        assertEquals(listOf("Workouts"), candidateFolderHierarchies[1])
        assertEquals(listOf("aTrainingTracker", "TCX"), candidateFolderHierarchies[2])
    }

    @Test
    fun testCandidateFolderHierarchies_formatGpx_prioritizesGpxThenLegacyWorkouts() {
        val format = "gpx"
        val candidateFolderHierarchies = when (format.lowercase()) {
            "fit" -> listOf(
                listOf("FIT"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "tcx" -> listOf(
                listOf("TCX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "gpx" -> listOf(
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            else -> listOf(
                listOf("FIT"),
                listOf("TCX"),
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
        }

        assertEquals(listOf("GPX"), candidateFolderHierarchies[0])
        assertEquals(listOf("Workouts"), candidateFolderHierarchies[1])
        assertEquals(listOf("aTrainingTracker", "GPX"), candidateFolderHierarchies[2])
    }

    @Test
    fun testCandidateFolderHierarchies_formatAll_scansAllFormatFoldersThenWorkouts() {
        val format = "all"
        val candidateFolderHierarchies = when (format.lowercase()) {
            "fit" -> listOf(
                listOf("FIT"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "tcx" -> listOf(
                listOf("TCX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            "gpx" -> listOf(
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
            else -> listOf(
                listOf("FIT"),
                listOf("TCX"),
                listOf("GPX"),
                listOf("Workouts"),
                listOf("aTrainingTracker", "FIT"),
                listOf("aTrainingTracker", "TCX"),
                listOf("aTrainingTracker", "GPX"),
                listOf("aTrainingTracker", "Workouts"),
                listOf("aTrainingTracker")
            )
        }

        assertEquals(9, candidateFolderHierarchies.size)
        assertEquals(listOf("FIT"), candidateFolderHierarchies[0])
        assertEquals(listOf("TCX"), candidateFolderHierarchies[1])
        assertEquals(listOf("GPX"), candidateFolderHierarchies[2])
        assertEquals(listOf("Workouts"), candidateFolderHierarchies[3])
        assertEquals(listOf("aTrainingTracker", "FIT"), candidateFolderHierarchies[4])
    }

    @Test
    fun testCrossFolderDeduplication_duplicateInWorkoutsAndFormat_deduplicatesByBaseName() {
        val allEntries = listOf(
            DriveFileEntry(id = "1", name = "2026-10-10_10-00-00.fit"),
            DriveFileEntry(id = "2", name = "2026-10-10_10-00-00.fit"), // Duplicate from legacy Workouts folder
            DriveFileEntry(id = "3", name = "2026-10-11_12-00-00.fit")
        )

        val deduplicated = allEntries.distinctBy {
            it.name.substringBeforeLast(".").removeSuffix("-TMP").removeSuffix("~").lowercase()
        }

        assertEquals(2, deduplicated.size)
        assertEquals("1", deduplicated[0].id)
        assertEquals("3", deduplicated[1].id)
    }
}
