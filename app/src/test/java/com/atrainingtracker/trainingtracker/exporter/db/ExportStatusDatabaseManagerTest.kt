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

package com.atrainingtracker.trainingtracker.exporter.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportConfig
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying ExportStatusDatabaseManager safe upsert and null-safe update handling
 * (REQ-EXP-018, TST-EXP-015.1, TST-EXP-015.2, ATT-2310).
 */
class ExportStatusDatabaseManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockDb: SQLiteDatabase
    private lateinit var mockHelper: ExportStatusDatabaseManager.ExportStatusDbHelper
    private lateinit var dbManager: ExportStatusDatabaseManager

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
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

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
        every { constructedWith<ContentValues>().putAll(any<ContentValues>()) } answers {
            val cv = getRealInstance()
            val other = firstArg<ContentValues>()
            val existing = contentValueStores[other]
            if (existing != null) {
                contentValueStores.computeIfAbsent(cv) { mutableMapOf() }.putAll(existing)
            }
        }

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        mockDb = mockk(relaxed = true)
        mockHelper = mockk(relaxed = true)
        every { mockHelper.writableDatabase } returns mockDb
        every { mockHelper.readableDatabase } returns mockDb

        val helperField = ExportStatusDatabaseManager.ExportStatusDbHelper::class.java.getDeclaredField("sInstance")
        helperField.isAccessible = true
        helperField.set(null, mockHelper)

        ExportStatusDatabaseManager.resetForTesting(null)
        dbManager = ExportStatusDatabaseManager.getInstance(mockContext)
    }

    @After
    fun tearDown() {
        ExportStatusDatabaseManager.resetForTesting(null)
        val helperField = ExportStatusDatabaseManager.ExportStatusDbHelper::class.java.getDeclaredField("sInstance")
        helperField.isAccessible = true
        helperField.set(null, null)
        unmockkAll()
    }

    @Test
    fun testUpdateExportStatus_whenRowDoesNotExist_performsSafeInsert() {
        val fileBaseName = "2026-10-04-12-00-00"
        val exportType = ExportType.FILE
        val fileFormat = FileFormat.FIT

        every {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                any(),
                any(),
                any()
            )
        } returns 0

        val insertedValues = mutableListOf<ContentValues>()
        every {
            mockDb.insert(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                null,
                capture(insertedValues)
            )
        } returns 1L

        val cv = ContentValues()
        cv.put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.FINISHED_SUCCESS.name)
        cv.put(ExportStatusDatabaseManager.ANSWER, "Exported 5120 bytes")

        dbManager.updateExportStatus(cv, fileBaseName, exportType, fileFormat)

        verify(exactly = 1) {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                cv,
                "${WorkoutSummaries.FILE_BASE_NAME}=? AND ${ExportStatusDatabaseManager.TYPE}=? AND ${ExportStatusDatabaseManager.FORMAT}=?",
                arrayOf(fileBaseName, exportType.name, fileFormat.name)
            )
        }

        verify(exactly = 1) {
            mockDb.insert(ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE, null, any())
        }

        assertEquals(1, insertedValues.size)
        val inserted = insertedValues.first()
        assertEquals(fileBaseName, inserted.getAsString(WorkoutSummaries.FILE_BASE_NAME))
        assertEquals(exportType.name, inserted.getAsString(ExportStatusDatabaseManager.TYPE))
        assertEquals(fileFormat.name, inserted.getAsString(ExportStatusDatabaseManager.FORMAT))
        assertEquals(ExportStatus.FINISHED_SUCCESS.name, inserted.getAsString(ExportStatusDatabaseManager.EXPORT_STATUS))
        assertEquals("Exported 5120 bytes", inserted.getAsString(ExportStatusDatabaseManager.ANSWER))
    }

    @Test
    fun testUpdateExportStatus_whenRowExists_performsUpdateOnly() {
        val fileBaseName = "2026-10-04-12-00-00"
        val exportType = ExportType.FILE
        val fileFormat = FileFormat.FIT

        every {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                any(),
                any(),
                any()
            )
        } returns 1

        val cv = ContentValues()
        cv.put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.FINISHED_SUCCESS.name)

        dbManager.updateExportStatus(cv, fileBaseName, exportType, fileFormat)

        verify(exactly = 1) {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                cv,
                "${WorkoutSummaries.FILE_BASE_NAME}=? AND ${ExportStatusDatabaseManager.TYPE}=? AND ${ExportStatusDatabaseManager.FORMAT}=?",
                arrayOf(fileBaseName, exportType.name, fileFormat.name)
            )
        }

        verify(exactly = 0) {
            mockDb.insert(any(), any(), any())
        }
    }

    @Test
    fun testUpdateExportStatus_nullExportType_whenRowsExist_updatesWithoutNpe() {
        val fileBaseName = "2026-10-04-12-00-00"
        val fileFormat = FileFormat.FIT

        every {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                any(),
                any(),
                any()
            )
        } returns 2

        val cv = ContentValues()
        cv.put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.FINISHED_FAILED.name)

        dbManager.updateExportStatus(cv, fileBaseName, null, fileFormat)

        verify(exactly = 1) {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                cv,
                "${WorkoutSummaries.FILE_BASE_NAME}=? AND ${ExportStatusDatabaseManager.FORMAT}=?",
                arrayOf(fileBaseName, fileFormat.name)
            )
        }

        verify(exactly = 0) {
            mockDb.insert(any(), any(), any())
        }
    }

    @Test
    fun testUpdateExportStatus_nullExportType_whenNoRowsExist_insertsForSupportedExportTypes() {
        val fileBaseName = "2026-10-04-12-00-00"
        val fileFormat = FileFormat.FIT

        every {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                any(),
                any(),
                any()
            )
        } returns 0

        val insertedValues = mutableListOf<ContentValues>()
        every {
            mockDb.insert(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                null,
                capture(insertedValues)
            )
        } returns 1L

        val cv = ContentValues()
        cv.put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.FINISHED_FAILED.name)

        dbManager.updateExportStatus(cv, fileBaseName, null, fileFormat)

        val supportedTypes = ExportType.values().filter { it.exportToFileFormats.contains(fileFormat) }
        assertTrue("At least one ExportType must support FIT", supportedTypes.isNotEmpty())

        verify(exactly = supportedTypes.size) {
            mockDb.insert(ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE, null, any())
        }

        val insertedTypes = insertedValues.mapNotNull { it.getAsString(ExportStatusDatabaseManager.TYPE) }
        for (supportedType in supportedTypes) {
            assertTrue("Expected inserted row for type: ${supportedType.name}", insertedTypes.contains(supportedType.name))
        }
    }

    @Test
    fun testUpdateExportStatus_delegatesFromExportInfo() {
        val fileBaseName = "2026-10-04-12-00-00"
        val exportInfo = mockk<ExportInfo>()
        every { exportInfo.fileBaseName } returns fileBaseName
        every { exportInfo.exportType } returns ExportType.FILE
        every { exportInfo.fileFormat } returns FileFormat.FIT

        every {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                any(),
                any(),
                any()
            )
        } returns 1

        val cv = ContentValues()
        cv.put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.FINISHED_SUCCESS.name)

        dbManager.updateExportStatus(cv, exportInfo)

        verify(exactly = 1) {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                cv,
                "${WorkoutSummaries.FILE_BASE_NAME}=? AND ${ExportStatusDatabaseManager.TYPE}=? AND ${ExportStatusDatabaseManager.FORMAT}=?",
                arrayOf(fileBaseName, ExportType.FILE.name, FileFormat.FIT.name)
            )
        }
    }

    @Test
    fun testMarkDependentUploadsFailed_transactionalAtomicUpdate_TST_DAT_024_5() {
        val fileBaseName = "2026-10-19_08-00-00"
        val format = FileFormat.TCX

        every { mockDb.inTransaction() } returns true
        val capturedCv = slot<ContentValues>()
        every {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                capture(capturedCv),
                any(),
                any()
            )
        } returns 2

        dbManager.markDependentUploadsFailed(fileBaseName, format, "Local disk error")

        verifyOrder {
            mockDb.beginTransaction()
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                any(),
                "${WorkoutSummaries.FILE_BASE_NAME}=? AND ${ExportStatusDatabaseManager.FORMAT}=? AND ${ExportStatusDatabaseManager.TYPE}!=? AND ${ExportStatusDatabaseManager.EXPORT_STATUS}=?",
                arrayOf<String>(fileBaseName, format.name, ExportType.FILE.name, ExportStatus.WAITING.name)
            )
            mockDb.setTransactionSuccessful()
            mockDb.endTransaction()
        }
        assertEquals(ExportStatus.FINISHED_FAILED.name, capturedCv.captured.getAsString(ExportStatusDatabaseManager.EXPORT_STATUS))
        assertEquals("Local disk error", capturedCv.captured.getAsString(ExportStatusDatabaseManager.ANSWER))
    }

    @Test
    fun testStaleProcessingRecovery_healsStaleRows_TST_DAT_024_11() {
        val fileBaseName = "2026-10-19_08-00-00"
        val staleTimestamp = System.currentTimeMillis() - ExportConfig.MAX_EXPORT_DURATION_MS - 5000L

        // Phase 1 cursor mock
        val mockCursor1 = mockk<android.database.Cursor>(relaxed = true)
        every { mockCursor1.moveToNext() } returns true andThen false
        every { mockCursor1.getString(0) } returns fileBaseName
        every { mockCursor1.getString(1) } returns ExportType.FILE.name
        every { mockCursor1.getString(2) } returns FileFormat.GPX.name
        every { mockCursor1.isNull(3) } returns false
        every { mockCursor1.getLong(3) } returns staleTimestamp
        every { mockCursor1.isNull(4) } returns false
        every { mockCursor1.getInt(4) } returns 0

        // Phase 3 cursor mock
        val mockCursor3 = mockk<android.database.Cursor>(relaxed = true)
        every { mockCursor3.moveToFirst() } returns true
        every { mockCursor3.isNull(0) } returns false
        every { mockCursor3.getLong(0) } returns staleTimestamp
        every { mockCursor3.isNull(1) } returns false
        every { mockCursor3.getInt(1) } returns 0

        every {
            mockDb.query(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                arrayOf(WorkoutSummaries.FILE_BASE_NAME, ExportStatusDatabaseManager.TYPE, ExportStatusDatabaseManager.FORMAT, ExportStatusDatabaseManager.LAST_UPDATED, ExportStatusDatabaseManager.RUN_ATTEMPT_COUNT),
                any(), any(), null, null, null
            )
        } returns mockCursor1

        every {
            mockDb.query(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                arrayOf(ExportStatusDatabaseManager.LAST_UPDATED, ExportStatusDatabaseManager.RUN_ATTEMPT_COUNT),
                any(), any(), null, null, null
            )
        } returns mockCursor3

        every { mockDb.inTransaction() } returns true
        val capturedCv = slot<ContentValues>()
        every {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                capture(capturedCv),
                any(),
                any()
            )
        } returns 1

        dbManager.healStaleProcessingRows(fileBaseName)

        verify {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                any(),
                "${WorkoutSummaries.FILE_BASE_NAME}=? AND ${ExportStatusDatabaseManager.TYPE}=? AND ${ExportStatusDatabaseManager.FORMAT}=?",
                arrayOf(fileBaseName, ExportType.FILE.name, FileFormat.GPX.name)
            )
        }
        assertEquals(ExportStatus.FINISHED_FAILED.name, capturedCv.captured.getAsString(ExportStatusDatabaseManager.EXPORT_STATUS))
    }

    @Test
    fun testStaleProcessingRecovery_longRunningWithHeartbeat_doesNotTimeout_TST_DAT_024_12() {
        val fileBaseName = "2026-10-19_08-00-00"
        // Heartbeat updated 1 minute ago (well within 15 min bound)
        val freshTimestamp = System.currentTimeMillis() - 60_000L

        val mockCursor = mockk<android.database.Cursor>(relaxed = true)
        every { mockCursor.moveToNext() } returns true andThen false
        every { mockCursor.getString(0) } returns fileBaseName
        every { mockCursor.getString(1) } returns ExportType.FILE.name
        every { mockCursor.getString(2) } returns FileFormat.GPX.name
        every { mockCursor.isNull(3) } returns false
        every { mockCursor.getLong(3) } returns freshTimestamp
        every { mockCursor.isNull(4) } returns false
        every { mockCursor.getInt(4) } returns 0

        every {
            mockDb.query(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                arrayOf(WorkoutSummaries.FILE_BASE_NAME, ExportStatusDatabaseManager.TYPE, ExportStatusDatabaseManager.FORMAT, ExportStatusDatabaseManager.LAST_UPDATED, ExportStatusDatabaseManager.RUN_ATTEMPT_COUNT),
                any(), any(), null, null, null
            )
        } returns mockCursor

        dbManager.healStaleProcessingRows(fileBaseName)

        // Should not update to FINISHED_FAILED
        verify(exactly = 0) {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                match { it.getAsString(ExportStatusDatabaseManager.EXPORT_STATUS) == ExportStatus.FINISHED_FAILED.name },
                any(), any()
            )
        }
    }

    @Test
    fun testStaleProcessingRecovery_abortsIfHeartbeatUpdatedDuringCheck_TST_DAT_024_14() {
        val fileBaseName = "2026-10-19_08-00-00"
        val oldTimestamp = System.currentTimeMillis() - ExportConfig.MAX_EXPORT_DURATION_MS - 10000L
        val freshTimestamp = System.currentTimeMillis()

        // Phase 1 cursor mock: sees old timestamp
        val mockCursor1 = mockk<android.database.Cursor>(relaxed = true)
        every { mockCursor1.moveToNext() } returns true andThen false
        every { mockCursor1.getString(0) } returns fileBaseName
        every { mockCursor1.getString(1) } returns ExportType.FILE.name
        every { mockCursor1.getString(2) } returns FileFormat.GPX.name
        every { mockCursor1.isNull(3) } returns false
        every { mockCursor1.getLong(3) } returns oldTimestamp
        every { mockCursor1.isNull(4) } returns false
        every { mockCursor1.getInt(4) } returns 0

        // Phase 3 cursor mock: concurrent worker has updated timestamp
        val mockCursor3 = mockk<android.database.Cursor>(relaxed = true)
        every { mockCursor3.moveToFirst() } returns true
        every { mockCursor3.isNull(0) } returns false
        every { mockCursor3.getLong(0) } returns freshTimestamp // changed!
        every { mockCursor3.isNull(1) } returns false
        every { mockCursor3.getInt(1) } returns 0

        every {
            mockDb.query(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                arrayOf(WorkoutSummaries.FILE_BASE_NAME, ExportStatusDatabaseManager.TYPE, ExportStatusDatabaseManager.FORMAT, ExportStatusDatabaseManager.LAST_UPDATED, ExportStatusDatabaseManager.RUN_ATTEMPT_COUNT),
                any(), any(), null, null, null
            )
        } returns mockCursor1

        every {
            mockDb.query(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                arrayOf(ExportStatusDatabaseManager.LAST_UPDATED, ExportStatusDatabaseManager.RUN_ATTEMPT_COUNT),
                any(), any(), null, null, null
            )
        } returns mockCursor3

        every { mockDb.inTransaction() } returns true

        dbManager.healStaleProcessingRows(fileBaseName)

        // Aborted due to concurrent heartbeat update: no update executed
        verify(exactly = 0) {
            mockDb.update(
                ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE,
                match { it.getAsString(ExportStatusDatabaseManager.EXPORT_STATUS) == ExportStatus.FINISHED_FAILED.name },
                any(), any()
            )
        }
    }

    @Test
    fun testInputSanitization_rejectsInvalidFileBaseName() {
        val invalidNames = listOf(null, "", "   ", "null", "NULL")

        for (invalid in invalidNames) {
            assertFalse(ExportStatusDatabaseManager.isValidFileBaseName(invalid))

            if (invalid != null) {
                dbManager.workoutFinished(invalid)
                dbManager.deleteWorkout(invalid)
                dbManager.recordWorkerAttempt(invalid, ExportType.FILE, FileFormat.GPX, 1)
                dbManager.updateHeartbeat(invalid, ExportType.FILE, FileFormat.GPX)
                dbManager.markDependentUploadsFailed(invalid, FileFormat.GPX, "Fail")
            }
        }

        // Verify zero database updates occurred for invalid names
        verify(exactly = 0) {
            mockDb.update(any(), any(), any(), any())
        }
        verify(exactly = 0) {
            mockDb.delete(any(), any(), any())
        }
    }
}
