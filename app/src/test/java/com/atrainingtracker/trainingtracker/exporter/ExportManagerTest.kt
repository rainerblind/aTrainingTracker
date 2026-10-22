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

package com.atrainingtracker.trainingtracker.exporter

import android.content.ContentValues
import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying ExportManager unique work enqueueing, dual tagging, invalidation cancellation,
 * and entry input sanitization (REQ-DAT-029.4, REQ-DAT-029.6, TST-DAT-024.7, TST-DAT-024.13).
 */
class ExportManagerTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockWorkManager = mockk<WorkManager>(relaxed = true)
    private val mockContinuation = mockk<WorkContinuation>(relaxed = true)
    private val mockRepo = mockk<ExportStatusDatabaseManager>(relaxed = true)

    private lateinit var exportManager: ExportManager

    @Before
    fun setUp() {
        every { mockContext.applicationContext } returns mockContext

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } returns Unit
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } returns Unit

        mockkObject(WorkManager.Companion)
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns mockWorkManager

        mockkStatic(ExportStatusDatabaseManager::class)
        every { ExportStatusDatabaseManager.getInstance(any()) } returns mockRepo

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isWorkManagerAvailable() } returns true
        every { TrainingApplication.uploadToDropbox() } returns false
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadToCommunity(any()) } returns false

        mockkStatic(ExportStatusChangedBroadcaster::class)
        every { ExportStatusChangedBroadcaster.broadcastExportStatusChanged(any(), any()) } returns Unit

        every { mockWorkManager.beginUniqueWork(any(), any(), any<OneTimeWorkRequest>()) } returns mockContinuation

        exportManager = ExportManager(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGetUniqueWorkName() {
        val uniqueName = ExportManager.getUniqueWorkName("2026-10-19_08-00-00", FileFormat.GPX)
        assertEquals("export_2026-10-19_08-00-00_GPX", uniqueName)
    }

    @Test
    fun testExportManager_uniqueWorkAndDualTagging_TST_DAT_024_7() {
        val fileBaseName = "2026-10-19_08-00-00"
        val format = FileFormat.GPX

        val capturedWork = slot<OneTimeWorkRequest>()
        val capturedPolicy = slot<ExistingWorkPolicy>()
        val capturedName = slot<String>()

        every {
            mockWorkManager.beginUniqueWork(
                capture(capturedName),
                capture(capturedPolicy),
                capture(capturedWork)
            )
        } returns mockContinuation

        exportManager.exportWorkoutTo(fileBaseName, format)

        // Verify unique work name and REPLACE policy
        assertEquals("export_2026-10-19_08-00-00_GPX", capturedName.captured)
        assertEquals(ExistingWorkPolicy.REPLACE, capturedPolicy.captured)

        // Verify dual tagging (fileBaseName and exportInfo.toString())
        val tags = capturedWork.captured.tags
        val expectedExportInfo = ExportInfo(fileBaseName, format, ExportType.FILE)
        assert(tags.contains(fileBaseName)) { "Expected tags to contain fileBaseName $fileBaseName, but got: $tags" }
        assert(tags.contains(expectedExportInfo.toString())) { "Expected tags to contain $expectedExportInfo, but got: $tags" }

        // Test invalidation cancels by fileBaseName tag and unique names
        exportManager.invalidateWorkoutExport(fileBaseName)
        verify { mockWorkManager.cancelAllWorkByTag(fileBaseName) }
        verify { mockWorkManager.cancelUniqueWork("export_$fileBaseName") }
        verify { mockWorkManager.cancelUniqueWork("export_${fileBaseName}_GPX") }
    }

    @Test
    fun testExportManager_inputSanitizationAtEntry_TST_DAT_024_13() {
        val invalidNames = listOf(null, "", "   ", "null", "NULL", " Null ")

        for (invalid in invalidNames) {
            exportManager.newWorkout(invalid)
            exportManager.workoutFinished(invalid)
            if (invalid != null) {
                exportManager.exportWorkoutTo(invalid, FileFormat.CSV)
                exportManager.invalidateWorkoutExport(invalid)
            }
        }

        // Verify zero database add / update / work scheduling operations occurred for invalid inputs
        verify(exactly = 0) { mockRepo.addExportStatus(any()) }
        verify(exactly = 0) { mockRepo.workoutFinished(any()) }
        verify(exactly = 0) { mockWorkManager.beginUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }
}
