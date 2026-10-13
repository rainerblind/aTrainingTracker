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
}
