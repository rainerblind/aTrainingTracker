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
 * Unit tests verifying BaseExporter error classification and cancellation behavior
 * (REQ-DAT-029.1, REQ-DAT-029.3, TST-DAT-024.1, TST-DAT-024.2, TST-DAT-024.6).
 */
class BaseExporterTest {

    private val mockContext = mockk<Context>(relaxed = true)

    private class TestExporter(
        context: Context,
        var behavior: (ExportInfo) -> ExportResult = { ExportResult(true, false, "Success") }
    ) : BaseExporter(context) {
        override fun doExport(exportInfo: ExportInfo): ExportResult {
            return behavior(exportInfo)
        }
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
}
