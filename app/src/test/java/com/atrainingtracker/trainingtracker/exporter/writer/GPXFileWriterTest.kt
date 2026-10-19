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

package com.atrainingtracker.trainingtracker.exporter.writer

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit tests verifying GPX export XML well-formedness, segment state machine,
 * and zero orphan </trkseg> tags across cropped lap 0 and zero-sample workouts
 * (REQ-EXP-022, TST-EXP-019.1, ATT-4013).
 */
class GPXFileWriterTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.getAppName() } returns "aTrainingTracker"

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        tempDir = java.nio.file.Files.createTempDirectory("gpx_export_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir

        mockSqlDb = mockk(relaxed = true)

        mockSummariesDb = mockk(relaxed = true)
        every { mockSummariesDb.database } returns mockSqlDb
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockSamplesDb = mockk(relaxed = true)
        every { mockSamplesDb.database } returns mockSqlDb
        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb
        every { WorkoutSamplesDatabaseManager.getTableName(any()) } answers { "samples_" + firstArg<String>() }

        mockLapsDb = mockk(relaxed = true)
        every { mockLapsDb.database } returns mockSqlDb
        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb

        val mockSportTypeDb = mockk<com.atrainingtracker.banalservice.database.SportTypeDatabaseManager>(relaxed = true)
        every { mockSportTypeDb.getBSportType(any()) } returns com.atrainingtracker.banalservice.BSportType.RUN
        mockkStatic(com.atrainingtracker.banalservice.database.SportTypeDatabaseManager::class)
        every { com.atrainingtracker.banalservice.database.SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.deleteRecursively()
    }

    private fun setupSummaryCursor(workoutId: Long = 1001L, startTime: String = "2026-10-19 10:00:00") {
        val summaryCursor = mockk<Cursor>(relaxed = true)
        every { summaryCursor.moveToFirst() } returns true
        val summaryColumns = mapOf(
            WorkoutSummaries.C_ID to workoutId.toString(),
            WorkoutSummaries.SPORT_ID to "1",
            WorkoutSummaries.TRAINER to "0",
            WorkoutSummaries.TIME_START to startTime,
            WorkoutSummaries.TIME_TOTAL_s to "600",
            WorkoutSummaries.GC_DATA to "-DSHCPNAG",
            WorkoutSummaries.GOAL to "",
            WorkoutSummaries.METHOD to "",
            WorkoutSummaries.DISTANCE_TOTAL_m to "2000",
            WorkoutSummaries.DESCRIPTION to "Test Workout",
            WorkoutSummaries.WORKOUT_NAME to "Test Name"
        )
        val columnIndices = summaryColumns.keys.toList()
        columnIndices.forEachIndexed { index, col ->
            every { summaryCursor.getColumnIndex(col) } returns index
            every { summaryCursor.getColumnIndexOrThrow(col) } returns index
            every { summaryCursor.getString(index) } returns summaryColumns[col]!!
            every { summaryCursor.getLong(index) } returns (summaryColumns[col]!!.toLongOrNull() ?: 0L)
            every { summaryCursor.getInt(index) } returns (summaryColumns[col]!!.toIntOrNull() ?: 0)
        }

        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } returns summaryCursor
    }

    @Test
    fun testGpxExportWithCroppedLap0_startsAtLap1_noOrphanClosingTag() {
        val workoutId = 1001L
        val fileBaseName = "test_gpx_cropped_lap"
        setupSummaryCursor(workoutId)

        // Samples start at lap 1 (lap 0 was cropped)
        val sampleCursor = mockk<Cursor>(relaxed = true)
        every { sampleCursor.count } returns 2
        var sampleRow = -1
        every { sampleCursor.moveToNext() } answers {
            sampleRow++
            sampleRow < 2
        }

        every { sampleCursor.getColumnIndexOrThrow(SensorType.LAP_NR.name) } returns 0
        every { sampleCursor.getInt(0) } returns 1 // Lap 1!
        every { sampleCursor.isNull(any()) } returns false
        every { sampleCursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME) } returns 1
        every { sampleCursor.getString(1) } answers {
            if (sampleRow == 0) "2026-10-19 10:00:00" else "2026-10-19 10:01:00"
        }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LATITUDE.name) } returns 2
        every { sampleCursor.getDouble(2) } returns 48.137
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name) } returns 3
        every { sampleCursor.getDouble(3) } answers { if (sampleRow == 0) 11.575 else 11.576 }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name) } returns 4
        every { sampleCursor.getDouble(4) } returns 520.0

        every { mockSqlDb.query(match { it != WorkoutSummaries.TABLE && it != LapsDatabaseManager.Laps.TABLE }, any(), any(), any(), any(), any(), any()) } returns sampleCursor

        val exportInfo = ExportInfo(fileBaseName, FileFormat.GPX, ExportType.FILE)
        val writer = GPXFileWriter(mockContext)

        val result = writer.export(exportInfo)
        assertTrue("Export must succeed: ${result.answer()}", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue("Exported file must exist", exportedFile.exists())
        val xml = exportedFile.readText()

        // 1. Verify XML parses cleanly (well-formedness)
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc: Document = builder.parse(ByteArrayInputStream(xml.toByteArray()))
        val trksegNodes = doc.getElementsByTagName("trkseg")
        assertEquals("Must contain exactly 1 trkseg element", 1, trksegNodes.length)

        // 2. String check: verify the first trkseg appears BEFORE any </trkseg>
        val openTrkSegIndex = xml.indexOf("<trkseg>")
        val closeTrkSegIndex = xml.indexOf("</trkseg>")
        assertTrue("Must contain <trkseg>", openTrkSegIndex != -1)
        assertTrue("Must contain </trkseg>", closeTrkSegIndex != -1)
        assertTrue("<trkseg> must appear before </trkseg>", openTrkSegIndex < closeTrkSegIndex)

        // Count occurrences of <trkseg> vs </trkseg>
        val openCount = xml.split("<trkseg>").size - 1
        val closeCount = xml.split("</trkseg>").size - 1
        assertEquals("Open and close tag count must match", openCount, closeCount)
    }

    @Test
    fun testGpxExportWithZeroSamples_noOrphanClosingTag() {
        val workoutId = 1002L
        val fileBaseName = "test_gpx_zero_samples"
        setupSummaryCursor(workoutId)

        val emptyCursor = mockk<Cursor>(relaxed = true)
        every { emptyCursor.count } returns 0
        every { emptyCursor.moveToNext() } returns false

        every { mockSqlDb.query(match { it != WorkoutSummaries.TABLE && it != LapsDatabaseManager.Laps.TABLE }, any(), any(), any(), any(), any(), any()) } returns emptyCursor

        val exportInfo = ExportInfo(fileBaseName, FileFormat.GPX, ExportType.FILE)
        val writer = GPXFileWriter(mockContext)

        val result = writer.export(exportInfo)
        assertTrue("Export must succeed: ${result.answer()}", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue(exportedFile.exists())
        val xml = exportedFile.readText()

        // Verify XML parses cleanly
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc: Document = builder.parse(ByteArrayInputStream(xml.toByteArray()))
        val trksegNodes = doc.getElementsByTagName("trkseg")
        assertEquals("Must contain 0 trkseg elements", 0, trksegNodes.length)

        assertFalse("Must NOT contain orphan </trkseg> tag", xml.contains("</trkseg>"))
        assertFalse("Must NOT contain <trkseg> tag", xml.contains("<trkseg>"))
    }

    @Test
    fun testGpxExportWithMultipleLaps_matchingOpenAndCloseTags() {
        val workoutId = 1003L
        val fileBaseName = "test_gpx_multi_laps"
        setupSummaryCursor(workoutId)

        val sampleCursor = mockk<Cursor>(relaxed = true)
        every { sampleCursor.count } returns 3
        var sampleRow = -1
        every { sampleCursor.moveToNext() } answers {
            sampleRow++
            sampleRow < 3
        }

        every { sampleCursor.getColumnIndexOrThrow(SensorType.LAP_NR.name) } returns 0
        // lap sequence: 0, 1, 2
        every { sampleCursor.getInt(0) } answers { sampleRow }
        every { sampleCursor.isNull(any()) } returns false
        every { sampleCursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME) } returns 1
        every { sampleCursor.getString(1) } answers { "2026-10-19 10:0$sampleRow:00" }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LATITUDE.name) } returns 2
        every { sampleCursor.getDouble(2) } returns 48.137
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name) } returns 3
        every { sampleCursor.getDouble(3) } answers { 11.575 + (sampleRow * 0.001) }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.ALTITUDE.name) } returns 4
        every { sampleCursor.getDouble(4) } returns 520.0

        every { mockSqlDb.query(match { it != WorkoutSummaries.TABLE && it != LapsDatabaseManager.Laps.TABLE }, any(), any(), any(), any(), any(), any()) } returns sampleCursor

        val exportInfo = ExportInfo(fileBaseName, FileFormat.GPX, ExportType.FILE)
        val writer = GPXFileWriter(mockContext)

        val result = writer.export(exportInfo)
        assertTrue(result.success())

        val xml = File(tempDir, exportInfo.shortPath).readText()
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc: Document = builder.parse(ByteArrayInputStream(xml.toByteArray()))
        val trksegNodes = doc.getElementsByTagName("trkseg")
        assertEquals("Must contain 3 trkseg elements for laps 0, 1, 2", 3, trksegNodes.length)

        val openCount = xml.split("<trkseg>").size - 1
        val closeCount = xml.split("</trkseg>").size - 1
        assertEquals("Open and close tag counts must match", openCount, closeCount)
        assertEquals("Must equal 3 segments", 3, openCount)
    }
}
