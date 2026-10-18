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
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Unit and schema verification tests for GCFileWriter conforming to REQ-DAT-027 and TST-DAT-022.
 */
class GCFileWriterTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase
    private lateinit var tempDir: File
    private var originalLocale: Locale = Locale.getDefault()

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()

        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.getAppName() } returns "aTrainingTracker"

        tempDir = Files.createTempDirectory("gc_export_test").toFile()
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
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb
        every { mockSportTypeDb.getGcName(any()) } returns "Bike"
        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.BIKE
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        unmockkAll()
        tempDir.deleteRecursively()
    }

    private fun createMockSummaryCursor(
        timeStart: String = "2026-10-18 10:00:00",
        totalTime: String = "120",
        distanceTotal: String = "1500.0",
        workoutName: String = "Sunday Ride",
        description: String = "Sunny morning ride",
        sportId: Long = 1L,
        isTrainer: Int = 0,
        gcData: String = "TDSHCPAGN"
    ): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        val columnNames = arrayOf(
            WorkoutSummaries.TIME_START,
            WorkoutSummaries.TIME_TOTAL_s,
            WorkoutSummaries.GC_DATA,
            WorkoutSummaries.GOAL,
            WorkoutSummaries.METHOD,
            WorkoutSummaries.DISTANCE_TOTAL_m,
            WorkoutSummaries.DESCRIPTION,
            WorkoutSummaries.WORKOUT_NAME,
            WorkoutSummaries.C_ID,
            WorkoutSummaries.SPORT_ID,
            WorkoutSummaries.TRAINER
        )

        every { cursor.getColumnIndex(any()) } answers {
            columnNames.indexOf(firstArg<String>())
        }
        every { cursor.getColumnIndexOrThrow(any()) } answers {
            val idx = columnNames.indexOf(firstArg<String>())
            if (idx == -1) throw IllegalArgumentException("Column not found: " + firstArg<String>())
            idx
        }
        every { cursor.isNull(any()) } returns false
        every { cursor.moveToFirst() } returns true

        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.TIME_START)) } returns timeStart
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.TIME_TOTAL_s)) } returns totalTime
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.GC_DATA)) } returns gcData
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.GOAL)) } returns "Intervals"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.METHOD)) } returns "HIIT"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.DISTANCE_TOTAL_m)) } returns distanceTotal
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.DESCRIPTION)) } returns description
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.WORKOUT_NAME)) } returns workoutName
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.C_ID)) } returns 101L
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.SPORT_ID)) } returns sportId
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.TRAINER)) } returns isTrainer

        return cursor
    }

    private fun createMockSamplesCursor(samples: List<Map<String, Any?>>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        val allColumns = listOf(
            SensorType.TIME_TOTAL.name,
            SensorType.DISTANCE_m.name,
            SensorType.SPEED_mps.name,
            SensorType.POWER.name,
            SensorType.PEDAL_POWER_BALANCE.name,
            SensorType.TORQUE_EFFECTIVENESS_L.name,
            SensorType.TORQUE_EFFECTIVENESS_R.name,
            SensorType.PEDAL_SMOOTHNESS_L.name,
            SensorType.PEDAL_SMOOTHNESS_R.name,
            SensorType.HR.name,
            SensorType.CADENCE.name,
            SensorType.TORQUE.name,
            SensorType.ALTITUDE.name,
            SensorType.LATITUDE.name,
            SensorType.LONGITUDE.name
        )

        var currentIndex = -1
        every { cursor.moveToNext() } answers {
            currentIndex++
            currentIndex < samples.size
        }
        every { cursor.count } answers { samples.size }
        every { cursor.getColumnIndex(any()) } answers { allColumns.indexOf(firstArg<String>()) }
        every { cursor.getColumnIndexOrThrow(any()) } answers {
            val idx = allColumns.indexOf(firstArg<String>())
            if (idx == -1) throw IllegalArgumentException("Column not found: " + firstArg<String>())
            idx
        }
        every { cursor.isNull(any()) } answers {
            val colIdx = firstArg<Int>()
            val colName = allColumns[colIdx]
            samples[currentIndex][colName] == null
        }
        every { cursor.getDouble(any()) } answers {
            val colIdx = firstArg<Int>()
            val colName = allColumns[colIdx]
            (samples[currentIndex][colName] as? Number)?.toDouble() ?: 0.0
        }
        every { cursor.getInt(any()) } answers {
            val colIdx = firstArg<Int>()
            val colName = allColumns[colIdx]
            (samples[currentIndex][colName] as? Number)?.toInt() ?: 0
        }

        return cursor
    }

    @Test
    fun testGCFileWriter_validNumericJson_and_localeIndependence() {
        // Enforce German locale to verify decimal comma elimination (TST-DAT-022.1, TST-DAT-022.2)
        Locale.setDefault(Locale.GERMANY)

        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                SensorType.TIME_TOTAL.name to 1,
                SensorType.DISTANCE_m.name to 1234.56,
                SensorType.SPEED_mps.name to 7.89,
                SensorType.POWER.name to 250.4,
                SensorType.PEDAL_POWER_BALANCE.name to 50.0,
                SensorType.TORQUE_EFFECTIVENESS_L.name to 75.0,
                SensorType.TORQUE_EFFECTIVENESS_R.name to 76.0,
                SensorType.PEDAL_SMOOTHNESS_L.name to 22.0,
                SensorType.PEDAL_SMOOTHNESS_R.name to 23.0,
                SensorType.HR.name to 145,
                SensorType.CADENCE.name to 90.5,
                SensorType.TORQUE.name to 25.123,
                SensorType.ALTITUDE.name to 543.2,
                SensorType.LATITUDE.name to 48.137154,
                SensorType.LONGITUDE.name to 11.576124
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockLapsDb.getLaps(any()) } returns emptyList()

        val exportInfo = ExportInfo("workout_gc_test", FileFormat.GC, ExportType.FILE)
        val writer = GCFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue("doExport must succeed", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue("Exported file must exist", exportedFile.exists())
        val jsonContent = exportedFile.readText()

        // 1. Assert JSON syntax is valid
        val root = JSONObject(jsonContent)
        val ride = root.getJSONObject("RIDE")
        val samplesArray = ride.getJSONArray("SAMPLES")
        assertEquals(1, samplesArray.length())

        val sampleObj = samplesArray.getJSONObject(0)

        // 2. Assert values are Number primitives, not Strings
        assertTrue(sampleObj.get("KM") is Number)
        assertTrue(sampleObj.get("KPH") is Number)
        assertTrue(sampleObj.get("WATTS") is Number)
        assertTrue(sampleObj.get("HR") is Number)
        assertTrue(sampleObj.get("CAD") is Number)
        assertTrue(sampleObj.get("ALT") is Number)
        assertTrue(sampleObj.get("LAT") is Number)
        assertTrue(sampleObj.get("LON") is Number)

        // 3. Assert values equal expected numbers
        assertEquals(1.235, sampleObj.getDouble("KM"), 0.001)
        assertEquals(28.404, sampleObj.getDouble("KPH"), 0.001)
        assertEquals(250.4, sampleObj.getDouble("WATTS"), 0.01)
        assertEquals(145, sampleObj.getInt("HR"))
        assertEquals(90.5, sampleObj.getDouble("CAD"), 0.01)
        assertEquals(543.2, sampleObj.getDouble("ALT"), 0.01)

        // 4. Assert no decimal commas in the raw JSON output
        assertFalse("JSON must not contain decimal commas in numbers", jsonContent.contains(",\"KM\":\"1,235\""))
        assertFalse("JSON must not contain any comma formatted metric numbers", jsonContent.contains("1,235"))
    }

    @Test
    fun testGCFileWriter_gpsValidation_and_doublePrecision() {
        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                SensorType.TIME_TOTAL.name to 1,
                SensorType.LATITUDE.name to 48.137154876,
                SensorType.LONGITUDE.name to 11.576124123
            ),
            // Row with null longitude: must be skipped by GPS validator
            mapOf(
                SensorType.TIME_TOTAL.name to 2,
                SensorType.LATITUDE.name to 48.137155000,
                SensorType.LONGITUDE.name to null
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockLapsDb.getLaps(any()) } returns emptyList()

        val exportInfo = ExportInfo("workout_gps_test", FileFormat.GC, ExportType.FILE)
        val writer = GCFileWriter(mockContext)
        val result = writer.doExport(exportInfo)
        assertTrue(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        val root = JSONObject(exportedFile.readText())
        val samples = root.getJSONObject("RIDE").getJSONArray("SAMPLES")
        assertEquals(2, samples.length())

        val firstSample = samples.getJSONObject(0)
        assertEquals(48.137154876, firstSample.getDouble("LAT"), 1e-8)
        assertEquals(11.576124123, firstSample.getDouble("LON"), 1e-8)

        // Second sample should NOT have LAT or LON because LONGITUDE was null
        val secondSample = samples.getJSONObject(1)
        assertFalse("Sample with invalid longitude must not contain LAT", secondSample.has("LAT"))
        assertFalse("Sample with invalid longitude must not contain LON", secondSample.has("LON"))
    }

    @Test
    fun testGCFileWriter_intervalsExport_fromRecordedLaps() {
        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val samplesCursor = createMockSamplesCursor(listOf(
            mapOf(SensorType.TIME_TOTAL.name to 1)
        ))
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        val laps = listOf(
            LapData(
                id = 1L,
                workoutId = 101L,
                lapNr = 1L,
                timeStart = "2026-10-18 10:00:00",
                timeTotalS = 300,
                distanceTotalM = 2000.0,
                speedAverageMps = 6.67,
                name = "Warmup",
                description = "Easy pace"
            ),
            LapData(
                id = 2L,
                workoutId = 101L,
                lapNr = 2L,
                timeStart = "2026-10-18 10:05:00",
                timeTotalS = 600,
                distanceTotalM = 5000.0,
                speedAverageMps = 8.33,
                name = null, // Test fallback to "Lap 2"
                description = null
            )
        )
        every { mockLapsDb.getLaps(101L) } returns laps

        val exportInfo = ExportInfo("workout_laps_test", FileFormat.GC, ExportType.FILE)
        val writer = GCFileWriter(mockContext)
        val result = writer.doExport(exportInfo)
        assertTrue(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        val root = JSONObject(exportedFile.readText())
        val ride = root.getJSONObject("RIDE")
        assertTrue("RIDE must contain INTERVALS array", ride.has("INTERVALS"))

        val intervals = ride.getJSONArray("INTERVALS")
        assertEquals(2, intervals.length())

        val interval0 = intervals.getJSONObject(0)
        assertEquals("Warmup", interval0.getString("NAME"))
        assertEquals(0, interval0.getInt("START"))
        assertEquals(300, interval0.getInt("STOP"))

        val interval1 = intervals.getJSONObject(1)
        assertEquals("Lap 2", interval1.getString("NAME"))
        assertEquals(300, interval1.getInt("START"))
        assertEquals(900, interval1.getInt("STOP"))
    }

    @Test
    fun testGCFileWriter_tagsMetadataAlignment() {
        val summaryCursor = createMockSummaryCursor(
            workoutName = "Tour de Munich",
            description = "Fabulous ride along the Isar",
            sportId = 1L
        )
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val samplesCursor = createMockSamplesCursor(listOf(
            mapOf(SensorType.TIME_TOTAL.name to 1)
        ))
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockLapsDb.getLaps(any()) } returns emptyList()

        val exportInfo = ExportInfo("workout_tags_test", FileFormat.GC, ExportType.FILE)
        val writer = GCFileWriter(mockContext)
        val result = writer.doExport(exportInfo)
        assertTrue(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        val root = JSONObject(exportedFile.readText())
        val tags = root.getJSONObject("RIDE").getJSONObject("TAGS")

        assertEquals("Bike", tags.getString("Sport"))
        assertEquals("Tour de Munich", tags.getString("Workout Title"))
        assertEquals("Fabulous ride along the Isar", tags.getString("Notes"))
        assertEquals("Intervals HIIT", tags.getString("Workout Code"))
        assertEquals("aTrainingTracker", tags.getString("Device"))
        assertEquals("TDSHCPAGN", tags.getString("Data"))
    }

    @Test
    fun testGCFileWriter_resourceSafetyOnException() {
        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val throwingCursor = mockk<Cursor>(relaxed = true)
        every { throwingCursor.moveToNext() } throws IllegalStateException("Simulated DB read error")
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns throwingCursor

        every { mockLapsDb.getLaps(any()) } returns emptyList()

        val exportInfo = ExportInfo("workout_error_test", FileFormat.GC, ExportType.FILE)
        val writer = GCFileWriter(mockContext)

        try {
            writer.doExport(exportInfo)
        } catch (ignored: Exception) {
            // Expected
        }

        // Verify throwingCursor was closed
        io.mockk.verify { throwingCursor.close() }
    }
}
