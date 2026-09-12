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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.helpers.HavePressureSensor
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying TCX export serialization of lap names and descriptions,
 * schema placement after </Track> and before </Lap>, activity notes placement,
 * and XML special character escaping (REQ-DAT-012, TST-DAT-006, ATT-892).
 */
class TCXFileWriterLapTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.getAppName() } returns "aTrainingTracker"

        mockkStatic(HavePressureSensor::class)
        every { HavePressureSensor.havePressureSensor(any()) } returns false

        tempDir = java.nio.file.Files.createTempDirectory("tcx_export_test").toFile()
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

        mockSportTypeDb = mockk(relaxed = true)
        every { mockSportTypeDb.getTcxName(any()) } returns "Running"
        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.RUN
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkAll()
    }

    @Test
    fun testEscapeXml() {
        assertEquals("A &amp; B", TCXFileWriter.escapeXml("A & B"))
        assertEquals("&lt;tag&gt;", TCXFileWriter.escapeXml("<tag>"))
        assertEquals("&quot;hello&quot; &apos;world&apos;", TCXFileWriter.escapeXml("\"hello\" 'world'"))
        assertEquals("", TCXFileWriter.escapeXml(null))
        assertEquals("Plain text", TCXFileWriter.escapeXml("Plain text"))
    }

    @Test
    fun testTcxExportSerializesLapNotesExtensionsAndActivityNotes() {
        val workoutId = 1001L
        val fileBaseName = "test_workout_20260912"

        // Setup mock summary cursor
        val summaryCursor = mockk<Cursor>(relaxed = true)
        every { summaryCursor.moveToFirst() } returns true
        val summaryColumns = mapOf(
            WorkoutSummaries.C_ID to "1001",
            WorkoutSummaries.SPORT_ID to "1",
            WorkoutSummaries.TRAINER to "0",
            WorkoutSummaries.TIME_START to "2026-09-12 10:00:00",
            WorkoutSummaries.TIME_TOTAL_s to "900",
            WorkoutSummaries.GC_DATA to "-DSHCPNAG",
            WorkoutSummaries.GOAL to "",
            WorkoutSummaries.METHOD to "",
            WorkoutSummaries.DISTANCE_TOTAL_m to "3500",
            WorkoutSummaries.DESCRIPTION to "Morning progression ride & run"
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

        // Setup mock samples cursor (2 trackpoints in lap 1, 2 trackpoints in lap 2)
        val sampleCursor = mockk<Cursor>(relaxed = true)
        every { sampleCursor.count } returns 4
        var sampleRow = -1
        every { sampleCursor.moveToNext() } answers {
            sampleRow++
            sampleRow < 4
        }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LAP_NR.name) } returns 0
        every { sampleCursor.getInt(0) } answers {
            if (sampleRow < 2) 1 else 2
        }
        every { sampleCursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME) } returns 1
        every { sampleCursor.getString(1) } answers {
            when (sampleRow) {
                0 -> "2026-09-12 10:00:00"
                1 -> "2026-09-12 10:04:00"
                2 -> "2026-09-12 10:05:00"
                else -> "2026-09-12 10:15:00"
            }
        }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LATITUDE.name) } returns 2
        every { sampleCursor.getDouble(2) } returns 48.0
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name) } returns 3
        every { sampleCursor.getDouble(3) } returns 11.0
        every { sampleCursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name) } returns 4
        every { sampleCursor.getDouble(4) } answers {
            (sampleRow * 1000).toDouble()
        }
        every { mockSqlDb.query(match { it != WorkoutSummaries.TABLE }, any(), any(), any(), any(), any(), any()) } returns sampleCursor

        // Setup LapsDatabaseManager.getLaps
        val lapsList = listOf(
            LapData(
                id = 1L,
                workoutId = workoutId,
                lapNr = 1L,
                timeStart = "2026-09-12 10:00:00",
                timeTotalS = 300,
                distanceTotalM = 1000.0,
                speedAverageMps = 3.33,
                name = "Warm-up",
                description = "10m easy Zone 1"
            ),
            LapData(
                id = 2L,
                workoutId = workoutId,
                lapNr = 2L,
                timeStart = "2026-09-12 10:05:00",
                timeTotalS = 600,
                distanceTotalM = 2500.0,
                speedAverageMps = 4.16,
                name = "Hill & Climb",
                description = "Zone 4 <hard> \"tempo\" 'efforts'"
            )
        )
        every { mockLapsDb.getLaps(workoutId) } returns lapsList

        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)
        val writer = TCXFileWriter(mockContext)

        val result = writer.export(exportInfo)
        assertTrue("Export must succeed: ${result.answer()}", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue("Exported file must exist", exportedFile.exists())

        val xml = exportedFile.readText()
        println("GENERATED XML:\n$xml")

        // 1. Verify schema sequence for Lap 0: </Track> followed by <Notes>, then <Extensions>, then </Lap>
        val trackClose0 = xml.indexOf("</Track>")
        val lapNotes0 = xml.indexOf("<Notes>[Warm-up] 10m easy Zone 1</Notes>")
        val lapExt0 = xml.indexOf("<att:LapExtension xmlns:att=\"http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1\">")
        val lapClose0 = xml.indexOf("</Lap>")

        assertTrue("</Track> must appear before lap <Notes>", trackClose0 < lapNotes0)
        assertTrue("Lap <Notes> must appear before <Extensions>", lapNotes0 < lapExt0)
        assertTrue("<Extensions> must appear before </Lap>", lapExt0 < lapClose0)

        // 2. Verify structured extension tags in Lap 0
        assertTrue("Must contain <att:Name>Warm-up</att:Name>", xml.contains("<att:Name>Warm-up</att:Name>"))
        assertTrue("Must contain <att:Description>10m easy Zone 1</att:Description>", xml.contains("<att:Description>10m easy Zone 1</att:Description>"))

        // 3. Verify XML escaping in Lap 1
        assertTrue(
            "Must contain XML-escaped notes for Lap 1",
            xml.contains("<Notes>[Hill &amp; Climb] Zone 4 &lt;hard&gt; &quot;tempo&quot; &apos;efforts&apos;</Notes>")
        )
        assertTrue(
            "Must contain XML-escaped structured Name for Lap 1",
            xml.contains("<att:Name>Hill &amp; Climb</att:Name>")
        )
        assertTrue(
            "Must contain XML-escaped structured Description for Lap 1",
            xml.contains("<att:Description>Zone 4 &lt;hard&gt; &quot;tempo&quot; &apos;efforts&apos;</att:Description>")
        )

        // 4. Verify activity-level notes placed before </Activity>
        val actNotes = xml.indexOf("<Notes>Morning progression ride &amp; run</Notes>")
        val actClose = xml.indexOf("</Activity>")
        val lastLapClose = xml.lastIndexOf("</Lap>")

        assertTrue("Activity <Notes> must appear after last </Lap>", lastLapClose < actNotes)
        assertTrue("Activity <Notes> must appear before </Activity>", actNotes < actClose)
    }

    @Test
    fun testTcxExportWithLapNameOnly() {
        val workoutId = 1002L
        val fileBaseName = "test_workout_name_only"

        val summaryCursor = mockk<Cursor>(relaxed = true)
        every { summaryCursor.moveToFirst() } returns true
        val summaryColumns = mapOf(
            WorkoutSummaries.C_ID to "1002",
            WorkoutSummaries.SPORT_ID to "1",
            WorkoutSummaries.TRAINER to "0",
            WorkoutSummaries.TIME_START to "2026-09-12 10:00:00",
            WorkoutSummaries.TIME_TOTAL_s to "300",
            WorkoutSummaries.GC_DATA to "-DSHCPNAG",
            WorkoutSummaries.GOAL to "",
            WorkoutSummaries.METHOD to "",
            WorkoutSummaries.DISTANCE_TOTAL_m to "1000",
            WorkoutSummaries.DESCRIPTION to ""
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

        val sampleCursor = mockk<Cursor>(relaxed = true)
        every { sampleCursor.count } returns 2
        var sampleRow = -1
        every { sampleCursor.moveToNext() } answers {
            sampleRow++
            sampleRow < 2
        }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LAP_NR.name) } returns 0
        every { sampleCursor.getInt(0) } returns 1
        every { sampleCursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME) } returns 1
        every { sampleCursor.getString(1) } returns "2026-09-12 10:00:00"
        every { sampleCursor.isNull(any()) } returns true

        every { mockSqlDb.query(match { it != WorkoutSummaries.TABLE }, any(), any(), any(), any(), any(), any()) } returns sampleCursor

        val lapsList = listOf(
            LapData(
                id = 1L,
                workoutId = workoutId,
                lapNr = 1L,
                timeStart = "2026-09-12 10:00:00",
                timeTotalS = 300,
                distanceTotalM = 1000.0,
                speedAverageMps = 3.33,
                name = "Recovery Spin",
                description = null
            )
        )
        every { mockLapsDb.getLaps(workoutId) } returns lapsList

        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)
        val writer = TCXFileWriter(mockContext)

        val result = writer.export(exportInfo)
        assertTrue("Export must succeed: ${result.answer()}", result.success())

        val xml = File(tempDir, exportInfo.shortPath).readText()

        assertTrue("Must contain [Recovery Spin] in <Notes>", xml.contains("<Notes>[Recovery Spin]</Notes>"))
        assertTrue("Must contain <att:Name>Recovery Spin</att:Name>", xml.contains("<att:Name>Recovery Spin</att:Name>"))
        assertTrue("Must NOT contain <att:Description>", !xml.contains("<att:Description>"))
    }
}
