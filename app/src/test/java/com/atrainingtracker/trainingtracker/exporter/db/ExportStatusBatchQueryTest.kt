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

import android.app.Application
import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportStatus
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real SQLite batch-query tests verifying `> 999` SQLite bind-arg chunking (`<= 500`),
 * single upfront stale-row heal, dead overload removal, and Strava batch parity
 * (`REQ-DAT-045.1`, `REQ-DAT-045.2`, `TST-DAT-041.1`, `TST-DAT-041.2`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExportStatusBatchQueryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ExportStatusDatabaseManager.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
    }

    @After
    fun tearDown() {
        ExportStatusDatabaseManager.resetForTesting(null)
        StravaUploadDbHelper.resetForTesting(null)
    }

    @Test
    fun testGetExportRowsForWorkouts_chunksOver999BindArgsAndHealsOnce_TST_DAT_041_1() {
        val dbManager = spyk(ExportStatusDatabaseManager.getInstance(context))
        val db = dbManager.database
        val total = 1200
        val names = (1..total).map { "2026-11-21-%04d".format(it) }

        db.beginTransaction()
        try {
            names.forEachIndexed { idx, name ->
                val cv = ContentValues().apply {
                    put(WorkoutSummaries.FILE_BASE_NAME, name)
                    put(ExportStatusDatabaseManager.TYPE, ExportType.FILE.name)
                    put(ExportStatusDatabaseManager.FORMAT, FileFormat.FIT.name)
                    put(ExportStatusDatabaseManager.EXPORT_STATUS, ExportStatus.FINISHED_SUCCESS.name)
                    put(ExportStatusDatabaseManager.ANSWER, "ok_$idx")
                    put(ExportStatusDatabaseManager.LAST_UPDATED, 1_700_000_000_000L)
                    put(ExportStatusDatabaseManager.RUN_ATTEMPT_COUNT, 1)
                }
                db.insert(ExportStatusDatabaseManager.ExportStatusDbHelper.TABLE, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val batchMap = dbManager.getExportRowsForWorkouts(names + listOf("", "null", names.first()))

        assertEquals(total, batchMap.size)
        assertEquals("ok_0", batchMap[names.first()]?.single()?.answer)
        assertEquals("ok_${total - 1}", batchMap[names.last()]?.single()?.answer)
        verify(exactly = 1) { dbManager.healStaleProcessingRows() }
        verify(exactly = 1) { dbManager.healStaleProcessingRows(null) }
        verify(exactly = 0) { dbManager.healStaleProcessingRows(isNull(inverse = true)) }

        // Verify dead getExportStatusMap overloads are removed
        val methodNames = ExportStatusDatabaseManager::class.java.declaredMethods.map { it.name }
        assertFalse("Dead getExportStatusMap overloads must be removed", methodNames.contains("getExportStatusMap"))
    }

    @Test
    fun testStravaUploadDbHelper_getStravaActivityDataForWorkouts_chunksOver999BindArgs_TST_DAT_041_2() {
        val helper = StravaUploadDbHelper.getInstance(context)
        val db = helper.writableDatabase
        val total = 1200
        val names = (1..total).map { "strava-%04d".format(it) }

        db.beginTransaction()
        try {
            names.forEachIndexed { idx, name ->
                val cv = ContentValues().apply {
                    put(WorkoutSummaries.FILE_BASE_NAME, name)
                    put(StravaUploadDbHelper.UPLOAD_ID, "u_$idx")
                    put(StravaUploadDbHelper.ACTIVITY_ID, "a_$idx")
                    put(StravaUploadDbHelper.STATUS, "OK")
                    put(StravaUploadDbHelper.STRAVA_ACTIVITY_DATA, "{\"id\":$idx}")
                }
                db.insert(StravaUploadDbHelper.TABLE, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val dataMap = helper.getStravaActivityDataForWorkouts(names + listOf(names.first()))

        assertEquals(total, dataMap.size)
        assertTrue(dataMap[names.first()] == "{\"id\":0}")
        assertTrue(dataMap[names.last()] == "{\"id\":${total - 1}}")
    }
}
