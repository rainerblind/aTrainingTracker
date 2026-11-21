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

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.atrainingtracker.trainingtracker.exporter.BaseExporter.ExportResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.json.JSONException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.text.ParseException
import java.util.concurrent.CancellationException

/**
 * Unit tests verifying BaseExporter error classification, cancellation behavior, and cursor column-0 access
 * (REQ-DAT-029.1, REQ-DAT-029.3, TST-DAT-024.1, TST-DAT-024.2, TST-DAT-024.6, REQ-MIG-055.2, TST-MIG-055.3).
 */
// Linked test specs: TST-DAT-024, TST-MIG-055, TST-MIG-055.3
class BaseExporterTest {

    private val mockContext = mockk<Context>(relaxed = true)

    private class TestExporter(
        context: Context,
        var behavior: (ExportInfo) -> ExportResult = { ExportResult(true, false, "Success") }
    ) : BaseExporter(context) {
        override fun doExport(exportInfo: ExportInfo): ExportResult {
            return behavior(exportInfo)
        }

        fun callGetStringFromCursor(cursor: Cursor, key: String): String? =
            myGetStringFromCursor(cursor, key)

        fun callGetBooleanFromCursor(cursor: Cursor, key: String): Boolean =
            myGetBooleanFromCursor(cursor, key)
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testExport_success() {
        val exporter = TestExporter(mockContext) {
            ExportResult(true, false, "Success")
        }
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.GPX, ExportType.FILE)
        val result = exporter.export(exportInfo)

        assertTrue(result.success())
        assertFalse(result.shallRetry())
        assertEquals("Success", result.answer())
    }

    @Test
    fun testExport_deterministicErrors_shallNotRetry_TST_DAT_024_1() {
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.GPX, ExportType.FILE)

        // ParseException
        val parseExporter = TestExporter(mockContext) {
            throw ParseException("Unparseable timestamp", 0)
        }
        val parseResult = parseExporter.export(exportInfo)
        assertFalse(parseResult.success())
        assertFalse(parseResult.shallRetry())

        // JSONException
        val jsonExporter = TestExporter(mockContext) {
            throw JSONException("Malformed JSON")
        }
        val jsonResult = jsonExporter.export(exportInfo)
        assertFalse(jsonResult.success())
        assertFalse(jsonResult.shallRetry())

        // IllegalArgumentException
        val illegalArgExporter = TestExporter(mockContext) {
            throw IllegalArgumentException("Invalid argument")
        }
        val illegalArgResult = illegalArgExporter.export(exportInfo)
        assertFalse(illegalArgResult.success())
        assertFalse(illegalArgResult.shallRetry())

        // General non-fatal Throwable (e.g. NullPointerException or ArithmeticException)
        val fatalExporter = TestExporter(mockContext) {
            throw NullPointerException("Corrupted sample record")
        }
        val fatalResult = fatalExporter.export(exportInfo)
        assertFalse(fatalResult.success())
        assertFalse(fatalResult.shallRetry())
    }

    @Test
    fun testExport_transientIOException_shallRetry_TST_DAT_024_2() {
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.STRAVA, ExportType.COMMUNITY)
        val ioExporter = TestExporter(mockContext) {
            throw IOException("Connection reset by peer")
        }

        val result = ioExporter.export(exportInfo)
        assertFalse(result.success())
        assertTrue(result.shallRetry())
        assertTrue(result.answer().contains("Connection reset by peer"))
    }

    @Test
    fun testCooperativeCancellation_abortsExportAndReturnsFailureWithoutRetry_TST_DAT_024_6() {
        val exportInfo = ExportInfo("2026-10-19_08-00-00", FileFormat.FIT, ExportType.FILE)
        val cancellableExporter = TestExporter(mockContext) {
            throw CancellationException("Export cancelled by user or system")
        }

        var cancelRequested = false
        cancellableExporter.setCancellationSignal { cancelRequested }

        // When cancelled
        cancelRequested = true
        val result = cancellableExporter.export(exportInfo)

        assertFalse(result.success())
        assertFalse(result.shallRetry())
        assertTrue(result.answer().contains("cancelled"))
    }

    /**
     * TST-MIG-055.3: Verifies myGetStringFromCursor and myGetBooleanFromCursor read valid column index 0
     * (including empty-string -> null, > 0 boolean, and null handling) while rejecting index -1.
     */
    @Test
    fun testCursorHelpers_allowColumnIndexZeroAndRejectNegative_TST_MIG_055_3() {
        val exporter = TestExporter(mockContext)
        val cursor = mockk<Cursor>()

        every { cursor.getColumnIndex("col_str") } returns 0
        every { cursor.isNull(0) } returns false
        every { cursor.getString(0) } returns "Alpine Loop"
        assertEquals("Alpine Loop", exporter.callGetStringFromCursor(cursor, "col_str"))

        every { cursor.getColumnIndex("col_empty") } returns 0
        every { cursor.getString(0) } returns ""
        assertNull(exporter.callGetStringFromCursor(cursor, "col_empty"))

        every { cursor.getColumnIndex("col_missing") } returns -1
        assertNull(exporter.callGetStringFromCursor(cursor, "col_missing"))

        every { cursor.getColumnIndex("col_bool_true") } returns 0
        every { cursor.getInt(0) } returns 1
        assertTrue(exporter.callGetBooleanFromCursor(cursor, "col_bool_true"))

        every { cursor.getColumnIndex("col_bool_false") } returns 0
        every { cursor.getInt(0) } returns 0
        assertFalse(exporter.callGetBooleanFromCursor(cursor, "col_bool_false"))

        assertFalse(exporter.callGetBooleanFromCursor(cursor, "col_missing"))
    }
}
