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
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
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
import java.io.File

/**
 * Unit tests verifying TCX export serialization of workout name and description,
 * BaseFileWriter header querying, schema sequence compliance, and XML escaping
 * (REQ-DAT-013, TST-DAT-007, ATT-922, ATT-924).
 */
class TCXFileWriterWorkoutNameTest {

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

        tempDir = java.nio.file.Files.createTempDirectory("tcx_workout_name_test").toFile()
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

    private fun setupMockDatabase(
        workoutId: Long,
        fileBaseName: String,
        workoutName: String?,
        description: String?
    ) {
        val summaryCursor = mockk<Cursor>(relaxed = true)
        every { summaryCursor.moveToFirst() } returns true
        val summaryColumns = mutableMapOf(
            WorkoutSummaries.C_ID to workoutId.toString(),
            WorkoutSummaries.SPORT_ID to "1",
            WorkoutSummaries.TRAINER to "0",
            WorkoutSummaries.TIME_START to "2026-09-12 10:00:00",
            WorkoutSummaries.TIME_TOTAL_s to "600",
            WorkoutSummaries.GC_DATA to "-DSHCPNAG",
            WorkoutSummaries.GOAL to "",
            WorkoutSummaries.METHOD to "",
            WorkoutSummaries.DISTANCE_TOTAL_m to "2000",
            WorkoutSummaries.DESCRIPTION to (description ?: ""),
            WorkoutSummaries.WORKOUT_NAME to (workoutName ?: "")
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

        // Mock samples cursor with 2 points
        val sampleCursor = mockk<Cursor>(relaxed = true)
        every { sampleCursor.count } returns 2
        var row = -1
        every { sampleCursor.moveToNext() } answers {
            row++
            row < 2
        }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LAP_NR.name) } returns 0
        every { sampleCursor.getInt(0) } returns 1
        every { sampleCursor.getColumnIndexOrThrow(WorkoutSamplesDbHelper.TIME) } returns 1
        every { sampleCursor.getString(1) } answers { if (row == 0) "2026-09-12 10:00:00" else "2026-09-12 10:10:00" }
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LATITUDE.name) } returns 2
        every { sampleCursor.getDouble(2) } returns 48.0
        every { sampleCursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name) } returns 3
        every { sampleCursor.getDouble(3) } returns 11.0
        every { sampleCursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name) } returns 4
        every { sampleCursor.getDouble(4) } answers { (row * 1000).toDouble() }
        every { mockSqlDb.query(match { it != WorkoutSummaries.TABLE }, any(), any(), any(), any(), any(), any()) } returns sampleCursor

        every { mockLapsDb.getLaps(workoutId) } returns listOf(
            LapData(
                id = 1L,
                workoutId = workoutId,
                lapNr = 1L,
                timeStart = "2026-09-12 10:00:00",
                timeTotalS = 600,
                distanceTotalM = 2000.0,
                speedAverageMps = 3.33
            )
        )
    }

    @Test
    fun testBaseFileWriterQueriesWorkoutName() {
        val fileBaseName = "test_workout_header"
        setupMockDatabase(1001L, fileBaseName, "Morning Intervals", "Good pace")

        val writer = TCXFileWriter(mockContext)
        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)

        // Trigger export which calls getHeaderData
        val result = writer.export(exportInfo)
        assertTrue(result.success())
        assertEquals("Morning Intervals", writer.workoutName)
    }

    @Test
    fun testTcxExportSerializesWorkoutNameAndDescriptionInNotesAndExtensions() {
        val fileBaseName = "test_workout_name_desc"
        setupMockDatabase(1002L, fileBaseName, "Interval Training", "Felt strong & fast")

        val writer = TCXFileWriter(mockContext)
        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)

        val result = writer.export(exportInfo)
        assertTrue(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue(exportedFile.exists())
        val xml = exportedFile.readText()

        // Verify human-readable bracket format in <Activity><Notes>
        assertTrue(
            "XML must contain formatted bracket notes",
            xml.contains("<Notes>[Interval Training] Felt strong &amp; fast</Notes>")
        )

        // Verify structured activity extension
        assertTrue(
            "XML must contain ActivityExtension namespace declaration",
            xml.contains("<att:ActivityExtension xmlns:att=\"http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1\">")
        )
        assertTrue(
            "XML must contain structured att:Name",
            xml.contains("<att:Name>Interval Training</att:Name>")
        )
        assertTrue(
            "XML must contain structured att:Description",
            xml.contains("<att:Description>Felt strong &amp; fast</att:Description>")
        )

        // Verify schema sequence: Lap -> Notes -> Extensions -> /Activity
        val lapCloseIndex = xml.lastIndexOf("</Lap>")
        val notesIndex = xml.indexOf("<Notes>[Interval Training] Felt strong &amp; fast</Notes>")
        val extIndex = xml.indexOf("<att:ActivityExtension")
        val activityCloseIndex = xml.indexOf("</Activity>")

        assertTrue("Lap must close before Activity Notes", lapCloseIndex < notesIndex)
        assertTrue("Notes must appear before Extensions", notesIndex < extIndex)
        assertTrue("Extensions must appear before closing Activity", extIndex < activityCloseIndex)
    }

    @Test
    fun testTcxExportNameOnly() {
        val fileBaseName = "test_workout_name_only"
        setupMockDatabase(1003L, fileBaseName, "Tempo Run", null)

        val writer = TCXFileWriter(mockContext)
        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)

        val result = writer.export(exportInfo)
        assertTrue(result.success())

        val xml = File(tempDir, exportInfo.shortPath).readText()
        assertTrue("Must contain [Tempo Run] in Notes", xml.contains("<Notes>[Tempo Run]</Notes>"))
        assertTrue("Must contain structured att:Name", xml.contains("<att:Name>Tempo Run</att:Name>"))
        assertFalse("Must not contain structured att:Description", xml.contains("<att:Description>"))
    }

    @Test
    fun testTcxExportDescriptionOnly() {
        val fileBaseName = "test_workout_desc_only"
        setupMockDatabase(1004L, fileBaseName, null, "Just a recovery ride")

        val writer = TCXFileWriter(mockContext)
        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)

        val result = writer.export(exportInfo)
        assertTrue(result.success())

        val xml = File(tempDir, exportInfo.shortPath).readText()
        assertTrue("Must contain description in Notes without brackets", xml.contains("<Notes>Just a recovery ride</Notes>"))
        assertTrue("Must contain structured att:Description", xml.contains("<att:Description>Just a recovery ride</att:Description>"))
        assertFalse("Must not contain structured att:Name", xml.contains("<att:Name>"))
    }

    @Test
    fun testTcxExportOmitsDefaultFileBaseName() {
        val fileBaseName = "2026-09-12_10-00-00"
        // workoutName is identical to fileBaseName (default unnamed workout)
        setupMockDatabase(1005L, fileBaseName, fileBaseName, "Nice weather")

        val writer = TCXFileWriter(mockContext)
        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)

        val result = writer.export(exportInfo)
        assertTrue(result.success())

        val xml = File(tempDir, exportInfo.shortPath).readText()
        // Notes should only contain the description, not [2026-09-12_10-00-00]
        assertTrue("Notes should only contain description", xml.contains("<Notes>Nice weather</Notes>"))
        assertFalse("Notes should not bracket the fileBaseName", xml.contains("[$fileBaseName]"))
        assertFalse("Structured att:Name should not be written for fileBaseName", xml.contains("<att:Name>"))
        assertTrue("Structured att:Description should be written", xml.contains("<att:Description>Nice weather</att:Description>"))
    }

    @Test
    fun testTcxExportXmlCharacterEscaping() {
        val fileBaseName = "test_workout_escaping"
        setupMockDatabase(
            1006L,
            fileBaseName,
            "Hill & Interval <Session 1> \"Speed\" 'Test'",
            "Zone 4 & 5 <all-out> \"efforts\" 'ok'"
        )

        val writer = TCXFileWriter(mockContext)
        val exportInfo = ExportInfo(fileBaseName, FileFormat.TCX, ExportType.FILE)

        val result = writer.export(exportInfo)
        assertTrue(result.success())

        val xml = File(tempDir, exportInfo.shortPath).readText()
        val expectedNotes = "<Notes>[Hill &amp; Interval &lt;Session 1&gt; &quot;Speed&quot; &apos;Test&apos;] Zone 4 &amp; 5 &lt;all-out&gt; &quot;efforts&quot; &apos;ok&apos;</Notes>"
        assertTrue("Must properly escape XML special characters in Notes", xml.contains(expectedNotes))
        assertTrue("Must escape att:Name", xml.contains("<att:Name>Hill &amp; Interval &lt;Session 1&gt; &quot;Speed&quot; &apos;Test&apos;</att:Name>"))
        assertTrue("Must escape att:Description", xml.contains("<att:Description>Zone 4 &amp; 5 &lt;all-out&gt; &quot;efforts&quot; &apos;ok&apos;</att:Description>"))
    }
}
