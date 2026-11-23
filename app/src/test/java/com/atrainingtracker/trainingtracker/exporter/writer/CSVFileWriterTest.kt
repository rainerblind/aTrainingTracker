/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.exporter.writer

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.exporter.BaseExporter
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

/**
 * Unit tests verifying CSV header generation, sensor column ordering, data row serialization,
 * null-value handling, and cooperative cancellation in [CSVFileWriter] (REQ-TST-001.4, TST-TST-001.1-3).
 */
class CSVFileWriterTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var mockSqlDb: SQLiteDatabase
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager

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

        tempDir = Files.createTempDirectory("csv_export_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.filesDir } returns tempDir

        mockSqlDb = mockk(relaxed = true)
        mockSamplesDb = mockk(relaxed = true)
        every { mockSamplesDb.database } returns mockSqlDb

        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb
        every { WorkoutSamplesDatabaseManager.getTableName(any()) } answers { "samples_" + firstArg<String>() }
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.deleteRecursively()
    }

    private fun createMockSamplesCursor(
        columnNames: Array<String>,
        rows: List<Map<String, Any?>>
    ): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.columnNames } returns columnNames
        every { cursor.columnCount } returns columnNames.size
        every { cursor.count } returns rows.size

        columnNames.forEachIndexed { index, name ->
            every { cursor.getColumnIndex(name) } returns index
        }

        var rowIndex = -1
        every { cursor.moveToNext() } answers {
            rowIndex++
            rowIndex < rows.size
        }

        every { cursor.isNull(any()) } answers {
            val colIndex = firstArg<Int>()
            val colName = columnNames[colIndex]
            rows[rowIndex][colName] == null
        }

        every { cursor.getDouble(any()) } answers {
            val colIndex = firstArg<Int>()
            val colName = columnNames[colIndex]
            (rows[rowIndex][colName] as? Number)?.toDouble() ?: 0.0
        }

        every { cursor.getInt(any()) } answers {
            val colIndex = firstArg<Int>()
            val colName = columnNames[colIndex]
            (rows[rowIndex][colName] as? Number)?.toInt() ?: 0
        }

        every { cursor.getString(any()) } answers {
            val colIndex = firstArg<Int>()
            val colName = columnNames[colIndex]
            rows[rowIndex][colName]?.toString()
        }

        return cursor
    }

    @Test
    fun testCSVFileWriter_headerAndSourceSuffixSorting() {
        // Columns: unsourced appear first in cursor order; sourced columns (_gps, (Heart Rate)) are sorted and appended
        val columns = arrayOf(
            SensorType.TIME_TOTAL.name,
            SensorType.LATITUDE.name + "_gps",
            SensorType.HR.name,
            SensorType.SPEED_mps.name + "_network",
            SensorType.ALTITUDE.name + " (Barometer)"
        )
        val sampleRows = listOf(
            mapOf(
                SensorType.TIME_TOTAL.name to 10,
                SensorType.LATITUDE.name + "_gps" to 48.137,
                SensorType.HR.name to 142,
                SensorType.SPEED_mps.name + "_network" to 5.2,
                SensorType.ALTITUDE.name + " (Barometer)" to 520.0
            )
        )
        val cursor = createMockSamplesCursor(columns, sampleRows)
        every { mockSqlDb.query(any(), any(), any(), any(), any(), any(), any()) } returns cursor

        val exportInfo = ExportInfo("workout_test_csv", FileFormat.CSV, ExportType.FILE)
        val writer = CSVFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue("doExport must succeed", result.success())
        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue("CSV file must exist", exportedFile.exists())

        val lines = exportedFile.readLines()
        assertTrue("Must contain header and at least one data row", lines.size >= 2)

        // Unsourced columns: TIME_TOTAL, HR in original encounter order
        // Sourced columns: sorted alphabetically -> ALTITUDE (Barometer), LATITUDE_gps, SPEED_mps_network
        val expectedHeader = "\"TIME_TOTAL\",\"HR\",\"ALTITUDE (Barometer)\",\"LATITUDE_gps\",\"SPEED_mps_network\""
        assertEquals(expectedHeader, lines[0])

        // Verify data row
        val expectedData = "\"10\",\"142\",\"520.0\",\"48.137\",\"5.2\""
        assertEquals(expectedData, lines[1])
    }

    @Test
    fun testCSVFileWriter_handlesNullValuesAsEmptyStrings() {
        val columns = arrayOf(
            SensorType.TIME_TOTAL.name,
            SensorType.HR.name,
            SensorType.SPEED_mps.name
        )
        val sampleRows = listOf(
            mapOf(
                SensorType.TIME_TOTAL.name to 1,
                SensorType.HR.name to null,
                SensorType.SPEED_mps.name to 4.5
            ),
            mapOf(
                SensorType.TIME_TOTAL.name to 2,
                SensorType.HR.name to 135,
                SensorType.SPEED_mps.name to null
            )
        )
        val cursor = createMockSamplesCursor(columns, sampleRows)
        every { mockSqlDb.query(any(), any(), any(), any(), any(), any(), any()) } returns cursor

        val exportInfo = ExportInfo("workout_null_csv", FileFormat.CSV, ExportType.FILE)
        val writer = CSVFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue(result.success())
        val lines = File(tempDir, exportInfo.shortPath).readLines()
        assertEquals(3, lines.size)

        // Row 1: HEART_RATE is null -> empty string
        assertEquals("\"1\",\"\",\"4.5\"", lines[1])
        // Row 2: SPEED_mps is null -> empty string
        assertEquals("\"2\",\"135\",\"\"", lines[2])
    }

    @Test
    fun testCSVFileWriter_cooperativeCancellation_abortsAndDeletesOutputFile() {
        val columns = arrayOf(SensorType.TIME_TOTAL.name, SensorType.SPEED_mps.name)
        val sampleRows = listOf(
            mapOf(SensorType.TIME_TOTAL.name to 1, SensorType.SPEED_mps.name to 3.0),
            mapOf(SensorType.TIME_TOTAL.name to 2, SensorType.SPEED_mps.name to 3.5),
            mapOf(SensorType.TIME_TOTAL.name to 3, SensorType.SPEED_mps.name to 4.0)
        )
        val cursor = createMockSamplesCursor(columns, sampleRows)
        every { mockSqlDb.query(any(), any(), any(), any(), any(), any(), any()) } returns cursor

        val exportInfo = ExportInfo("workout_cancel_csv", FileFormat.CSV, ExportType.FILE)
        val writer = CSVFileWriter(mockContext)

        // Simulate cancellation after first sample row
        var readCount = 0
        writer.setCancellationSignal(object : BaseExporter.CancellationSignal {
            override fun isCancelled(): Boolean {
                readCount++
                return readCount > 1
            }
        })

        try {
            writer.doExport(exportInfo)
            fail("Expected CancellationException when cancellation signal triggers")
        } catch (e: CancellationException) {
            assertEquals("Export cancelled by WorkManager", e.message)
        }

        val outputFile = File(tempDir, exportInfo.shortPath)
        assertFalse("Output CSV file must be deleted upon cancellation", outputFile.exists())
    }

    @Test
    fun testCSVFileWriter_emptyCursor_writesHeaderOnly() {
        val columns = arrayOf(SensorType.TIME_TOTAL.name, SensorType.DISTANCE_m.name)
        val cursor = createMockSamplesCursor(columns, emptyList())
        every { mockSqlDb.query(any(), any(), any(), any(), any(), any(), any()) } returns cursor

        val exportInfo = ExportInfo("workout_empty_csv", FileFormat.CSV, ExportType.FILE)
        val writer = CSVFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue(result.success())
        val file = File(tempDir, exportInfo.shortPath)
        assertTrue(file.exists())
        val lines = file.readLines()
        assertEquals("Only header line should be present", 1, lines.size)
        assertEquals("\"TIME_TOTAL\",\"DISTANCE_m\"", lines[0])
    }

    /**
     * TST-EXP-049.1: Verifies that metadata columns (`_id`, `time`) are short-circuited without
     * enum lookup, nested-parentheses device names (such as `ALTITUDE (Topografische Höhe (DEM))`)
     * and all location provider suffixes resolve to their canonical [SensorType] and
     * [com.atrainingtracker.banalservice.sensor.SensorValueType], and zero `Log.w` /
     * `IllegalArgumentException` warnings are emitted.
     */
    @Test
    fun testCSVFileWriter_metadataAndNestedParenthesesDeviceNames_noEnumWarnings_TST_EXP_049() {
        val columns = arrayOf(
            WorkoutSamplesDbHelper.C_ID,
            WorkoutSamplesDbHelper.TIME,
            SensorType.TIME_TOTAL.name,
            "ALTITUDE (Topografische Höhe (DEM))",
            "HR (Polar H10 (BLE) (Chest))",
            "ALTITUDE (Device_gps)",
            SensorType.LATITUDE.name + "_gps",
            SensorType.LONGITUDE.name + "_network",
            SensorType.BEARING.name + "_google_fused",
            SensorType.SPEED_mps.name + "_att_fused"
        )
        val sampleRows = listOf(
            mapOf(
                WorkoutSamplesDbHelper.C_ID to 1,
                WorkoutSamplesDbHelper.TIME to "2026-11-20 06:08:46",
                SensorType.TIME_TOTAL.name to 42,
                // Pass Int for DOUBLE column -> Double.toString yields "543.0" only if SensorType.ALTITUDE resolved
                "ALTITUDE (Topografische Höhe (DEM))" to 543,
                // Pass Double for INTEGER column -> Integer.toString yields "148" only if SensorType.HR resolved
                "HR (Polar H10 (BLE) (Chest))" to 148.9,
                "ALTITUDE (Device_gps)" to 512,
                SensorType.LATITUDE.name + "_gps" to 48.137,
                SensorType.LONGITUDE.name + "_network" to 11.576,
                SensorType.BEARING.name + "_google_fused" to 90.0,
                SensorType.SPEED_mps.name + "_att_fused" to 7
            )
        )
        val cursor = createMockSamplesCursor(columns, sampleRows)
        every { mockSqlDb.query(any(), any(), any(), any(), any(), any(), any()) } returns cursor

        val exportInfo = ExportInfo("workout_dem_csv", FileFormat.CSV, ExportType.FILE)
        val writer = CSVFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue("doExport must succeed", result.success())
        val lines = File(tempDir, exportInfo.shortPath).readLines()
        assertEquals(2, lines.size)

        val expectedHeader = listOf(
            WorkoutSamplesDbHelper.C_ID,
            WorkoutSamplesDbHelper.TIME,
            SensorType.TIME_TOTAL.name,
            "ALTITUDE (Device_gps)",
            "ALTITUDE (Topografische Höhe (DEM))",
            SensorType.BEARING.name + "_google_fused",
            "HR (Polar H10 (BLE) (Chest))",
            SensorType.LATITUDE.name + "_gps",
            SensorType.LONGITUDE.name + "_network",
            SensorType.SPEED_mps.name + "_att_fused"
        ).joinToString(",") { "\"$it\"" }
        assertEquals(expectedHeader, lines[0])

        val expectedRow = "\"1\",\"2026-11-20 06:08:46\",\"42\",\"512.0\",\"543.0\",\"90.0\",\"148\",\"48.137\",\"11.576\",\"7.0\""
        assertEquals(expectedRow, lines[1])

        verify(exactly = 0) { Log.w(any<String>(), any<String>()) }
        verify(exactly = 0) { Log.w(any<String>(), any<String>(), any()) }
    }

    @Test
    fun testCSVFileWriter_extractSourcedSensorTypeName_handlesNestedParensAndSuffixes() {
        assertEquals("ALTITUDE", CSVFileWriter.extractSourcedSensorTypeName("ALTITUDE (Topografische Höhe (DEM))"))
        assertEquals("HR", CSVFileWriter.extractSourcedSensorTypeName("HR (Polar H10 (BLE) (Chest))"))
        assertEquals("ALTITUDE", CSVFileWriter.extractSourcedSensorTypeName("ALTITUDE (Device_gps)"))
        assertEquals("LATITUDE", CSVFileWriter.extractSourcedSensorTypeName("LATITUDE_gps"))
        assertEquals("LONGITUDE", CSVFileWriter.extractSourcedSensorTypeName("LONGITUDE_network"))
        assertEquals("BEARING", CSVFileWriter.extractSourcedSensorTypeName("BEARING_google_fused"))
        assertEquals("SPEED_mps", CSVFileWriter.extractSourcedSensorTypeName("SPEED_mps_att_fused"))
        assertNull(CSVFileWriter.extractSourcedSensorTypeName("HR"))
        assertNull(CSVFileWriter.extractSourcedSensorTypeName(WorkoutSamplesDbHelper.C_ID))
        assertNull(CSVFileWriter.extractSourcedSensorTypeName(WorkoutSamplesDbHelper.TIME))
        assertNull(CSVFileWriter.extractSourcedSensorTypeName("_gps"))
        assertNull(CSVFileWriter.extractSourcedSensorTypeName(" (leading)"))
    }
}
