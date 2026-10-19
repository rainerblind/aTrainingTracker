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
import com.atrainingtracker.trainingtracker.exporter.BaseExporter.ExportResult
import androidx.work.Data
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager
import com.atrainingtracker.trainingtracker.ui.components.export.ExportNotificationManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying ExportWorker retry cap, uncaught exception handling, and error cascading
 * (REQ-DAT-029.2, REQ-DAT-029.5, REQ-DAT-029.6, TST-DAT-024.3, TST-DAT-024.4, TST-DAT-024.5).
 */
class ExportWorkerTest {

    private val mockContext = mockk<Context>(relaxed = true)
    private val mockWorkerParams = mockk<WorkerParameters>(relaxed = true)
    private val mockExporter = mockk<BaseExporter>(relaxed = true)
    private val mockRepo = mockk<ExportStatusDatabaseManager>(relaxed = true)
    private val mockNotificationManager = mockk<ExportNotificationManager>(relaxed = true)
    private val contentValueStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

    private fun io.mockk.MockKAnswerScope<*, *>.getRealInstance(): ContentValues {
        try {
            var obj: Any? = call.invocation.originalCall
            while (obj != null) {
                for (f in obj.javaClass.declaredFields) {
                    if (f.name == "self" || f.name == "\$self" || f.name.endsWith("\$self")) {
                        f.isAccessible = true
                        val s = f.get(obj)
                        if (s is ContentValues && s !== this.self) {
                            return s
                        }
                    }
                }
                val nextField = obj.javaClass.declaredFields.firstOrNull {
                    it.name.contains("originalCall") || it.name.contains("callable")
                }
                obj = nextField?.apply { isAccessible = true }?.get(obj)
            }
        } catch (_: Exception) { }
        return self as ContentValues
    }

    @Before
    fun setUp() {
        contentValueStores.clear()
        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Long>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            contentValueStores.computeIfAbsent(cv) { mutableMapOf() }[firstArg<String>()] = secondArg<Int>()
        }
        every { constructedWith<ContentValues>().containsKey(any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores[cv]?.containsKey(firstArg<String>()) ?: false
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            contentValueStores[cv]?.get(firstArg<String>())?.toString()
        }

        every { mockContext.applicationContext } returns mockContext

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkStatic(ExportManager::class)
        every { ExportManager.getExporter(any(), any()) } returns mockExporter

        mockkStatic(ExportStatusDatabaseManager::class)
        every { ExportStatusDatabaseManager.getInstance(any()) } returns mockRepo
        every { ExportStatusDatabaseManager.isValidFileBaseName(any()) } answers {
            val name = firstArg<String?>()
            name != null && name.isNotBlank() && !name.equals("null", ignoreCase = true)
        }

        mockkObject(ExportNotificationManager.Companion)
        every { ExportNotificationManager.getInstance(any()) } returns mockNotificationManager

        mockkStatic(ExportStatusChangedBroadcaster::class)
        every { ExportStatusChangedBroadcaster.broadcastExportStatusChanged(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createWorker(
        exportInfo: ExportInfo,
        runAttemptCount: Int = 0
    ): ExportWorker {
        val inputData = Data.Builder()
            .putString(ExportWorker.KEY_EXPORT_INFO, exportInfo.toJson())
            .build()
        every { mockWorkerParams.inputData } returns inputData
        every { mockWorkerParams.runAttemptCount } returns runAttemptCount

        return ExportWorker(mockContext, mockWorkerParams)
    }

    @Test
    fun testExportWorker_success() = runBlocking {
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.GPX, ExportType.FILE)
        every { mockExporter.export(any()) } returns ExportResult(true, false, "Success")

        val worker = createWorker(exportInfo, runAttemptCount = 0)
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        val cvList = mutableListOf<ContentValues>()
        verify { mockRepo.updateExportStatus(capture(cvList), any<ExportInfo>()) }
        assertEquals(ExportStatus.FINISHED_SUCCESS.name, cvList.last().getAsString(ExportStatusDatabaseManager.EXPORT_STATUS))
    }

    @Test
    fun testExportWorker_maxRetriesEnforced_TST_DAT_024_3() = runBlocking {
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.STRAVA, ExportType.COMMUNITY)
        every { mockExporter.export(any()) } returns ExportResult(false, true, "Transient server error")

        // 1. When worker starts with runAttemptCount >= MAX_RETRIES (3)
        val workerAtMax = createWorker(exportInfo, runAttemptCount = ExportConfig.MAX_RETRIES)
        val resultAtMax = workerAtMax.doWork()

        assertEquals(Result.failure(), resultAtMax)
        val cvList1 = mutableListOf<ContentValues>()
        verify { mockRepo.updateExportStatus(capture(cvList1), any<ExportInfo>()) }
        assertEquals(ExportStatus.FINISHED_FAILED.name, cvList1.last().getAsString(ExportStatusDatabaseManager.EXPORT_STATUS))

        // 2. When export fails with shallRetry=true on attempt 2 (which is the 3rd execution, 0-indexed)
        // so it cannot retry further
        clearMocks(mockRepo, answers = false)
        val workerOnAttempt2 = createWorker(exportInfo, runAttemptCount = ExportConfig.MAX_RETRIES - 1)
        val resultOnAttempt2 = workerOnAttempt2.doWork()

        assertEquals(Result.failure(), resultOnAttempt2)
        val cvList2 = mutableListOf<ContentValues>()
        verify { mockRepo.updateExportStatus(capture(cvList2), any<ExportInfo>()) }
        assertEquals(ExportStatus.FINISHED_FAILED.name, cvList2.last().getAsString(ExportStatusDatabaseManager.EXPORT_STATUS))
    }

    @Test
    fun testExportWorker_transientFailureBelowMaxRetries_retries() = runBlocking {
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.STRAVA, ExportType.COMMUNITY)
        every { mockExporter.export(any()) } returns ExportResult(false, true, "Transient server timeout")

        val worker = createWorker(exportInfo, runAttemptCount = 0)
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        val cvList = mutableListOf<ContentValues>()
        verify { mockRepo.updateExportStatus(capture(cvList), any<ExportInfo>()) }
        assertEquals(ExportStatus.WAITING.name, cvList.last().getAsString(ExportStatusDatabaseManager.EXPORT_STATUS))
    }

    @Test
    fun testExportWorker_uncaughtRuntimeException_writesFailedStatus_TST_DAT_024_4() = runBlocking {
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.FIT, ExportType.FILE)
        every { mockExporter.export(any()) } throws IllegalStateException("Uncaught corrupt native state")

        val worker = createWorker(exportInfo, runAttemptCount = 0)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        val cvList = mutableListOf<ContentValues>()
        verify { mockRepo.updateExportStatus(capture(cvList), any<ExportInfo>()) }
        assertEquals(ExportStatus.FINISHED_FAILED.name, cvList.last().getAsString(ExportStatusDatabaseManager.EXPORT_STATUS))
    }

    @Test
    fun testExportWorker_fileFailure_marksDependentUploadsFailedInOrder_TST_DAT_024_5() = runBlocking {
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.TCX, ExportType.FILE)
        every { mockExporter.export(any()) } returns ExportResult(false, false, "Local filesystem full")

        val worker = createWorker(exportInfo, runAttemptCount = 0)
        val result = worker.doWork()

        assertEquals(Result.failure(), result)
        verifyOrder {
            mockRepo.updateExportStatus(any<ContentValues>(), any<ExportInfo>())
            mockRepo.markDependentUploadsFailed("2026-10-19_08-00-00", FileFormat.TCX, any())
        }
    }
}
