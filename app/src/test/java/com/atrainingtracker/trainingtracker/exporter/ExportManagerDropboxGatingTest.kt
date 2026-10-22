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
import androidx.work.NetworkType
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
 * Unit tests verifying ExportManager Dropbox workout export gating and Wi-Fi network constraints (TST-DAT-031.2).
 */
class ExportManagerDropboxGatingTest {

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
        every { TrainingApplication.uploadToGoogleDrive() } returns false
        every { TrainingApplication.uploadWorkoutsToGoogleDrive() } returns false
        every { TrainingApplication.uploadToCommunity(any()) } returns false
        every { TrainingApplication.exportToFile(any()) } returns true

        mockkStatic(ExportStatusChangedBroadcaster::class)
        every { ExportStatusChangedBroadcaster.broadcastExportStatusChanged(any(), any()) } returns Unit

        every { mockWorkManager.beginUniqueWork(any(), any(), any<OneTimeWorkRequest>()) } returns mockContinuation
        every { mockContinuation.then(any<List<OneTimeWorkRequest>>()) } returns mockContinuation
        every { mockContinuation.enqueue() } returns mockk(relaxed = true)

        exportManager = ExportManager(mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testExportCompleted_whenDropboxUploadWorkoutsDisabled_doesNotScheduleDropboxUpload() {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadWorkoutsToDropbox() } returns false

        val chainedWorks = mutableListOf<List<OneTimeWorkRequest>>()
        every { mockContinuation.then(capture(chainedWorks)) } returns mockContinuation

        exportManager.exportWorkoutTo("2026-10-22_09-00-00", FileFormat.FIT)

        // None of the chained works should target Dropbox
        val hasDropboxWork = chainedWorks.flatten().any { req ->
            req.tags.any { it.contains(ExportType.DROPBOX.name) }
        }
        assertEquals("Dropbox upload work must not be scheduled when uploadWorkoutsToDropbox is false", false, hasDropboxWork)
    }

    @Test
    fun testExportCompleted_whenDropboxUploadWorkoutsEnabled_schedulesDropboxUpload() {
        every { TrainingApplication.uploadToDropbox() } returns true
        every { TrainingApplication.uploadWorkoutsToDropbox() } returns true

        val chainedWorks = mutableListOf<List<OneTimeWorkRequest>>()
        every { mockContinuation.then(capture(chainedWorks)) } returns mockContinuation

        exportManager.exportWorkoutTo("2026-10-22_09-00-00", FileFormat.FIT)

        val hasDropboxWork = chainedWorks.flatten().any { req ->
            req.tags.any { it.contains(ExportType.DROPBOX.name) }
        }
        assertEquals("Dropbox upload work must be scheduled when uploadWorkoutsToDropbox is true", true, hasDropboxWork)
    }

    @Test
    fun testCreateWorkRequest_enforcesWifiConstraint_forDropbox() {
        val exportInfo = ExportInfo("2026-10-22_09-00-00", FileFormat.FIT, ExportType.DROPBOX)

        // When Wi-Fi only is true
        every { TrainingApplication.uploadToDropboxOnlyOnWifi() } returns true
        val requestWifi = exportManager.createWorkRequest(exportInfo)
        assertEquals(NetworkType.UNMETERED, requestWifi.workSpec.constraints.requiredNetworkType)

        // When Wi-Fi only is false
        every { TrainingApplication.uploadToDropboxOnlyOnWifi() } returns false
        val requestCellular = exportManager.createWorkRequest(exportInfo)
        assertEquals(NetworkType.CONNECTED, requestCellular.workSpec.constraints.requiredNetworkType)
    }
}
