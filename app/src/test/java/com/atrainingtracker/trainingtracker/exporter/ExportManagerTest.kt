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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Unit tests verifying ExportManager unique work enqueueing, dual tagging, invalidation cancellation,
 * entry input sanitization, and raw sensor sidecar preservation on export invalidation
 * (REQ-DAT-029.4, REQ-DAT-029.6, REQ-EXP-048, TST-DAT-024.7, TST-DAT-024.13, TST-EXP-048.1).
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

    // REQ-EXP-048.1 / REQ-EXP-048.2 / TST-EXP-048.1
    @Test
    fun testInvalidateWorkoutExport_deletesDerivedCachesOnlyAndPreservesRawSensorSidecars_TST_EXP_048_1() {
        // 1. Exhaustive partition check over FileFormat.values()
        val expectedDerived = setOf(
            FileFormat.CSV,
            FileFormat.GC,
            FileFormat.TCX,
            FileFormat.GPX,
            FileFormat.FIT,
            FileFormat.STRAVA
        )
        val expectedNonDerived = setOf(
            FileFormat.RAW_SENSOR,
            FileFormat.HEALTH_CONNECT
        )
        val allFormats = FileFormat.values().toSet()
        assertEquals(allFormats, expectedDerived + expectedNonDerived)
        assertTrue(expectedDerived.intersect(expectedNonDerived).isEmpty())
        for (format in allFormats) {
            if (format in expectedDerived) {
                assertTrue("Expected $format to be a derived export file", format.isDerivedExportFile)
            } else {
                assertFalse("Expected $format NOT to be a derived export file", format.isDerivedExportFile)
            }
        }

        // 2. Populate real temporary filesDir with derived caches, raw sensor sidecars, and HealthConnect sentinel
        val fileBaseName = "2026-11-22_105503"
        val tempFilesDir = Files.createTempDirectory("att_9613_export_invalidation").toFile()
        try {
            every { mockContext.filesDir } returns tempFilesDir

            val derivedFiles = expectedDerived.associateWith { format ->
                val dir = File(tempFilesDir, format.dirName).apply { mkdirs() }
                File(dir, fileBaseName + format.fileEnding).apply {
                    writeText("derived-${format.name}-$fileBaseName")
                }
            }
            // Residual pre-upgrade Strava/*.tcx file must also be purged on invalidation (REQ-EXP-051.2 / TST-EXP-051.1)
            val legacyStravaTcx = File(File(tempFilesDir, FileFormat.STRAVA.dirName), "$fileBaseName.tcx").apply {
                writeText("<TrainingCenterDatabase/>")
            }

            val rawDir = File(tempFilesDir, FileFormat.RAW_SENSOR.dirName).apply { mkdirs() }
            val polarPayload = byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00, 0x11, 0x22, 0x33)
            val phonePayload = byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00, 0x44, 0x55, 0x66)
            val polarTmpPayload = byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00, 0x77, 0x88.toByte())
            val phoneTmpPayload = byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00, 0x99.toByte(), 0xAA.toByte())

            val polarSidecar = File(rawDir, "$fileBaseName.polar_raw.csv.gz").apply { writeBytes(polarPayload) }
            val phoneSidecar = File(rawDir, "$fileBaseName.phone_raw.csv.gz").apply { writeBytes(phonePayload) }
            val polarTmp = File(rawDir, "$fileBaseName.polar_raw.csv.gz.tmp").apply { writeBytes(polarTmpPayload) }
            val phoneTmp = File(rawDir, "$fileBaseName.phone_raw.csv.gz.tmp").apply { writeBytes(phoneTmpPayload) }

            val hcDir = File(tempFilesDir, FileFormat.HEALTH_CONNECT.dirName).apply { mkdirs() }
            val hcSentinelPayload = "hc-sentinel".toByteArray()
            val hcSentinel = File(hcDir, fileBaseName).apply { writeBytes(hcSentinelPayload) }

            // 3. Execute invalidation
            exportManager.invalidateWorkoutExport(fileBaseName)

            // 4. All 6 derived caches + residual legacy Strava .tcx MUST be deleted
            for ((format, file) in derivedFiles) {
                assertFalse("Derived cache for $format must be deleted: ${file.absolutePath}", file.exists())
            }
            assertFalse("Residual legacy Strava .tcx must be deleted on invalidation", legacyStravaTcx.exists())

            // 5. All raw_sensor sidecars & HealthConnect sentinel MUST survive byte-for-byte
            assertTrue("Polar raw sidecar must NOT be deleted", polarSidecar.exists())
            assertArrayEquals(polarPayload, polarSidecar.readBytes())

            assertTrue("Phone raw sidecar must NOT be deleted", phoneSidecar.exists())
            assertArrayEquals(phonePayload, phoneSidecar.readBytes())

            assertTrue("Polar raw .tmp sidecar must NOT be deleted", polarTmp.exists())
            assertArrayEquals(polarTmpPayload, polarTmp.readBytes())

            assertTrue("Phone raw .tmp sidecar must NOT be deleted", phoneTmp.exists())
            assertArrayEquals(phoneTmpPayload, phoneTmp.readBytes())

            assertTrue("HealthConnect sentinel must NOT be deleted", hcSentinel.exists())
            assertArrayEquals(hcSentinelPayload, hcSentinel.readBytes())

            // 6. WorkManager cancellation, DB WAITING reset, and status broadcast MUST still execute (REQ-DAT-029.4)
            verify { mockWorkManager.cancelAllWorkByTag(fileBaseName) }
            verify { mockWorkManager.cancelUniqueWork("export_$fileBaseName") }
            for (format in allFormats) {
                verify { mockWorkManager.cancelUniqueWork(ExportManager.getUniqueWorkName(fileBaseName, format)) }
                verify { mockRepo.updateExportStatus(any(), fileBaseName, null, format) }
            }
            verify { ExportStatusChangedBroadcaster.broadcastExportStatusChanged(mockContext, fileBaseName) }
        } finally {
            tempFilesDir.deleteRecursively()
        }
    }
}
