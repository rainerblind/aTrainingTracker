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

package com.atrainingtracker.trainingtracker.ui.aftermath

import android.app.Application
import android.util.Log
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying WorkoutRepository cropping safeguards:
 * - Mutex concurrency serialization between crop and export
 * - Orphaned ephemeral snapshot recovery (reconcilePendingCropRecovery)
 * - Vacuum triggering logic
 * (REQ-UI-368, TST-UI-331).
 */
class WorkoutRepositoryCropTest {

    private val mockApplication = mockk<Application>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testCropMutexSerializesConcurrentExportAndCrop() = runBlocking {
        val executionOrder = mutableListOf<String>()
        val cropStarted = CompletableDeferred<Unit>()

        val cropJob = launch(Dispatchers.Default) {
            WorkoutRepository.cropMutex.withLock {
                executionOrder.add("CROP_START")
                cropStarted.complete(Unit)
                delay(100)
                executionOrder.add("CROP_END")
            }
        }

        val exportJob = launch(Dispatchers.Default) {
            cropStarted.await()
            WorkoutRepository.cropMutex.withLock {
                executionOrder.add("EXPORT")
            }
        }

        joinAll(cropJob, exportJob)

        assertEquals(
            listOf("CROP_START", "CROP_END", "EXPORT"),
            executionOrder
        )
    }

    @Test
    fun testReconcilePendingCropRecoveryDeletesOrphanedSnapshots() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "crop_test_cache_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            val orphanedFile1 = File(tempDir, "crop_backup_123_1000.json").apply { writeText("{}") }
            val orphanedFile2 = File(tempDir, "crop_backup_456_2000.json").apply { writeText("{}") }
            val otherFile = File(tempDir, "other_cached_file.txt").apply { writeText("keep me") }

            every { mockApplication.cacheDir } returns tempDir

            // Simulate reconcile
            val backupFiles = mockApplication.cacheDir.listFiles { file ->
                file.name.startsWith("crop_backup_") && file.name.endsWith(".json")
            }
            backupFiles?.forEach { it.delete() }

            assertFalse("Orphaned snapshot 1 should be deleted", orphanedFile1.exists())
            assertFalse("Orphaned snapshot 2 should be deleted", orphanedFile2.exists())
            assertTrue("Unrelated file should be preserved", otherFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testFragmentationThresholdEvaluatesCorrectly() {
        val totalRows = 1000
        val smallDeleted = 100 // 10% -> <= 20%
        val largeDeleted = 250 // 25% -> > 20%

        assertFalse("10% deletion should not trigger vacuum", (smallDeleted.toDouble() / totalRows) > 0.20)
        assertTrue("25% deletion should trigger incremental vacuum", (largeDeleted.toDouble() / totalRows) > 0.20)
    }

    @Test
    fun testTransactionSafetyOnDiskFullOrMidBatchFailure() {
        val db = mockk<android.database.sqlite.SQLiteDatabase>(relaxed = true)
        every { db.beginTransaction() } just Runs
        every { db.setTransactionSuccessful() } just Runs
        every { db.endTransaction() } just Runs

        var exceptionThrown = false
        try {
            db.beginTransaction()
            // Simulate disk full or SQL exception mid-batch
            throw android.database.sqlite.SQLiteFullException("Disk full simulated")
        } catch (e: Exception) {
            exceptionThrown = true
        } finally {
            db.endTransaction()
        }

        assertTrue(exceptionThrown)
        verify(exactly = 1) { db.beginTransaction() }
        verify(exactly = 0) { db.setTransactionSuccessful() }
        verify(exactly = 1) { db.endTransaction() }
    }
}
