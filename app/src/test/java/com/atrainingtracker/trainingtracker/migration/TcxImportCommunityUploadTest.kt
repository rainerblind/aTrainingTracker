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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying automated community upload scheduling upon TCX workout import (REQ-EXT-008, TST-EXT-005, ATT-602).
 */
class TcxImportCommunityUploadTest {

    private lateinit var mockContext: Context
    private lateinit var mockExportManager: ExportManager

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

        mockContext = mockk(relaxed = true)
        mockExportManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testImportSchedulesStravaUploadWhenEnabled() {
        val workoutId = 42L
        val baseFileName = "2026-09-03-12-00-00"

        // Strava upload is globally connected and enabled
        every { TrainingApplication.uploadToCommunity(FileFormat.STRAVA) } returns true

        LegacyImportEngine.schedulePostImportCommunityUpload(
            context = mockContext,
            workoutId = workoutId,
            baseFileName = baseFileName,
            exportManager = mockExportManager
        )

        // Verifies ExportManager.exportWorkoutTo was triggered for STRAVA
        verify(exactly = 1) {
            mockExportManager.exportWorkoutTo(workoutId, FileFormat.STRAVA)
        }
    }

    @Test
    fun testImportSkipsUploadWhenCommunityDisabled() {
        val workoutId = 43L
        val baseFileName = "2026-09-03-14-00-00"

        // Strava upload is disabled
        every { TrainingApplication.uploadToCommunity(FileFormat.STRAVA) } returns false

        LegacyImportEngine.schedulePostImportCommunityUpload(
            context = mockContext,
            workoutId = workoutId,
            baseFileName = baseFileName,
            exportManager = mockExportManager
        )

        // Verifies no export or upload tasks are scheduled
        verify(exactly = 0) {
            mockExportManager.exportWorkoutTo(any(), any())
        }
    }

    @Test
    fun testImportRobustnessAgainstSchedulingExceptions() {
        val workoutId = 44L
        val baseFileName = "2026-09-03-16-00-00"

        every { TrainingApplication.uploadToCommunity(FileFormat.STRAVA) } returns true
        every { mockExportManager.exportWorkoutTo(any(), any()) } throws RuntimeException("Simulated WorkManager failure")

        // Must catch the exception gracefully without propagating or crashing
        LegacyImportEngine.schedulePostImportCommunityUpload(
            context = mockContext,
            workoutId = workoutId,
            baseFileName = baseFileName,
            exportManager = mockExportManager
        )

        verify(exactly = 1) {
            mockExportManager.exportWorkoutTo(workoutId, FileFormat.STRAVA)
        }
    }

    @Test
    fun testImportSkipsStravaUploadWhenExplicitlyOptedOut() {
        val workoutId = 45L
        val baseFileName = "2026-09-03-18-00-00"

        every { TrainingApplication.uploadToCommunity(FileFormat.STRAVA) } returns true

        val mockSummaryDb = mockk<com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager>(relaxed = true)
        mockkStatic(com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager::class)
        every { com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.getInstance(mockContext) } returns mockSummaryDb

        val mockCursor = mockk<android.database.Cursor>(relaxed = true)
        every {
            mockSummaryDb.database.query(
                com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries.UPLOAD_TO_STRAVA),
                "${com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
                arrayOf(workoutId.toString()),
                null,
                null,
                null
            )
        } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getInt(0) } returns 0

        LegacyImportEngine.schedulePostImportCommunityUpload(
            context = mockContext,
            workoutId = workoutId,
            baseFileName = baseFileName,
            exportManager = mockExportManager
        )

        // When UPLOAD_TO_STRAVA == 0, Strava export must be bypassed
        verify(exactly = 0) {
            mockExportManager.exportWorkoutTo(any(), any())
        }
    }

    @Test
    fun testImportUploadsToStravaWhenExplicitlyOptedIn() {
        val workoutId = 46L
        val baseFileName = "2026-09-03-20-00-00"

        every { TrainingApplication.uploadToCommunity(FileFormat.STRAVA) } returns true

        val mockSummaryDb = mockk<com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager>(relaxed = true)
        mockkStatic(com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager::class)
        every { com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.getInstance(mockContext) } returns mockSummaryDb

        val mockCursor = mockk<android.database.Cursor>(relaxed = true)
        every {
            mockSummaryDb.database.query(
                com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                arrayOf(com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries.UPLOAD_TO_STRAVA),
                "${com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries.C_ID} = ?",
                arrayOf(workoutId.toString()),
                null,
                null,
                null
            )
        } returns mockCursor
        every { mockCursor.moveToFirst() } returns true
        every { mockCursor.getInt(0) } returns 1

        LegacyImportEngine.schedulePostImportCommunityUpload(
            context = mockContext,
            workoutId = workoutId,
            baseFileName = baseFileName,
            exportManager = mockExportManager
        )

        verify(exactly = 1) {
            mockExportManager.exportWorkoutTo(workoutId, FileFormat.STRAVA)
        }
    }
}
