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
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.garmin.fit.Activity
import com.garmin.fit.ActivityMesg
import com.garmin.fit.ActivityMesgListener
import com.garmin.fit.Decode
import com.garmin.fit.FileIdMesg
import com.garmin.fit.FileIdMesgListener
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.LapMesgListener
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.garmin.fit.Sport
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/**
 * Unit and round-trip verification tests for FitFileWriter (REQ-DAT-018, TST-DAT-013).
 */
class FitFileWriterTest {

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
        every { Log.e(any<String>(), any<String>(), any()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getDebug(any()) } returns false
        every { TrainingApplication.getAppName() } returns "aTrainingTracker"

        tempDir = Files.createTempDirectory("fit_export_test").toFile()
        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext
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
    }

    @After
    fun tearDown() {
        unmockkAll()
        tempDir.deleteRecursively()
    }

    private fun createMockSummaryCursor(
        timeStart: String = "2026-10-03 10:00:00",
        totalTime: String = "120",
        distanceTotal: String = "1500.0",
        isTrainer: Int = 0,
        calories: Int = 180,
        ascent: Int = 50,
        descent: Int = 45,
        avgSpeed: Double = 12.5
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
            WorkoutSummaries.TRAINER,
            WorkoutSummaries.CALORIES,
            WorkoutSummaries.ASCENDING,
            WorkoutSummaries.DESCENDING,
            WorkoutSummaries.SPEED_AVERAGE_mps
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
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.GC_DATA)) } returns "DSHCPAG"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.GOAL)) } returns "Test Goal"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.METHOD)) } returns "Test Method"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.DISTANCE_TOTAL_m)) } returns distanceTotal
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.DESCRIPTION)) } returns "Test Ride"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.WORKOUT_NAME)) } returns "Morning Spin"
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.C_ID)) } returns 101L
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.SPORT_ID)) } returns 1L
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.TRAINER)) } returns isTrainer
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.CALORIES)) } returns calories
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.ASCENDING)) } returns ascent
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.DESCENDING)) } returns descent
        every { cursor.getDouble(columnNames.indexOf(WorkoutSummaries.SPEED_AVERAGE_mps)) } returns avgSpeed

        return cursor
    }

    private fun createMockSamplesCursor(samples: List<Map<String, Any?>>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        val allColumns = listOf(
            WorkoutSamplesDbHelper.TIME,
            SensorType.LATITUDE.name,
            SensorType.LONGITUDE.name,
            SensorType.ALTITUDE.name,
            SensorType.DISTANCE_m.name,
            SensorType.SPEED_mps.name,
            SensorType.HR.name,
            SensorType.CADENCE.name,
            SensorType.POWER.name,
            SensorType.TEMPERATURE.name
        )

        every { cursor.count } returns samples.size
        every { cursor.getColumnIndex(any()) } answers {
            allColumns.indexOf(firstArg<String>())
        }
        every { cursor.getColumnIndexOrThrow(any()) } answers {
            val idx = allColumns.indexOf(firstArg<String>())
            if (idx == -1) throw IllegalArgumentException("Column not found: " + firstArg<String>())
            idx
        }

        var currentIndex = -1
        every { cursor.moveToNext() } answers {
            currentIndex++
            currentIndex < samples.size
        }

        every { cursor.isNull(any()) } answers {
            val colIdx = firstArg<Int>()
            if (colIdx < 0 || colIdx >= allColumns.size) true
            else {
                val colName = allColumns[colIdx]
                samples[currentIndex][colName] == null
            }
        }

        every { cursor.getString(any()) } answers {
            val colIdx = firstArg<Int>()
            val colName = allColumns[colIdx]
            samples[currentIndex][colName]?.toString() ?: ""
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
    fun testFitFileWriter_generatesValidBinaryFitFile_andDecodesCleanly() {
        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:00",
                SensorType.LATITUDE.name to 52.5200,
                SensorType.LONGITUDE.name to 13.4050,
                SensorType.ALTITUDE.name to 45.0,
                SensorType.DISTANCE_m.name to 10.0,
                SensorType.SPEED_mps.name to 5.0,
                SensorType.HR.name to 140,
                SensorType.CADENCE.name to 85.0,
                SensorType.POWER.name to 200.0,
                SensorType.TEMPERATURE.name to 21.0
            ),
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:01",
                SensorType.LATITUDE.name to 52.5201,
                SensorType.LONGITUDE.name to 13.4052,
                SensorType.ALTITUDE.name to 46.0,
                SensorType.DISTANCE_m.name to 20.0,
                SensorType.SPEED_mps.name to 5.2,
                SensorType.HR.name to 142,
                SensorType.CADENCE.name to 86.0,
                SensorType.POWER.name to 210.0,
                SensorType.TEMPERATURE.name to 21.0
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.BIKE
        every { mockSportTypeDb.getTcxName(1L) } returns "Biking"

        val laps = listOf(
            LapData(
                id = 1L,
                workoutId = 101L,
                lapNr = 1L,
                timeStart = "2026-10-03 10:00:00",
                timeTotalS = 120,
                distanceTotalM = 1500.0,
                speedAverageMps = 12.5,
                name = "Lap 1",
                description = "Warmup"
            )
        )
        every { mockLapsDb.getLaps(101L) } returns laps

        val exportInfo = ExportInfo("workout_20261003_100000", FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue("FIT file must exist", exportedFile.exists())
        assertTrue("FIT file size must be > 14 bytes", exportedFile.length() > 14)

        // Verify with Garmin FIT SDK Decode
        val fileIds = mutableListOf<FileIdMesg>()
        val records = mutableListOf<RecordMesg>()
        val lapMesgs = mutableListOf<LapMesg>()
        val sessions = mutableListOf<SessionMesg>()
        val activities = mutableListOf<ActivityMesg>()

        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(FileIdMesgListener { fileIds.add(it) })
        broadcaster.addListener(RecordMesgListener { records.add(it) })
        broadcaster.addListener(LapMesgListener { lapMesgs.add(it) })
        broadcaster.addListener(SessionMesgListener { sessions.add(it) })
        broadcaster.addListener(ActivityMesgListener { activities.add(it) })

        FileInputStream(exportedFile).use { fis ->
            val decodeResult = decode.read(fis, broadcaster)
            assertTrue("Decode must return true", decodeResult)
        }

        assertEquals(1, fileIds.size)
        assertEquals(com.garmin.fit.File.ACTIVITY, fileIds[0].type)
        assertEquals(2, records.size)
        assertEquals(1, lapMesgs.size)
        assertEquals(1, sessions.size)
        assertEquals(1, activities.size)

        // Semicircles precision check
        val expectedLat = Math.round(52.5200 * (2147483648.0 / 180.0)).toInt()
        val expectedLng = Math.round(13.4050 * (2147483648.0 / 180.0)).toInt()
        assertEquals(expectedLat, records[0].positionLat)
        assertEquals(expectedLng, records[0].positionLong)
        assertEquals(140.toShort(), records[0].heartRate)
        assertEquals(85.toShort(), records[0].cadence)
        assertEquals(200, records[0].power)

        // Session check
        assertEquals(Sport.CYCLING, sessions[0].sport)
        assertEquals(120f, sessions[0].totalTimerTime)
        assertEquals(1500f, sessions[0].totalDistance)
    }

    @Test
    fun testFitFileWriter_indoorWorkoutOmitsCoordinates() {
        val summaryCursor = createMockSummaryCursor(isTrainer = 1)
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:00",
                SensorType.LATITUDE.name to Double.NaN,
                SensorType.LONGITUDE.name to Double.NaN,
                SensorType.ALTITUDE.name to null,
                SensorType.DISTANCE_m.name to 15.0,
                SensorType.SPEED_mps.name to 7.5,
                SensorType.HR.name to 155,
                SensorType.CADENCE.name to 92.0,
                SensorType.POWER.name to 240.0,
                SensorType.TEMPERATURE.name to 20.0
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.BIKE
        every { mockSportTypeDb.getTcxName(1L) } returns "Biking"
        every { mockLapsDb.getLaps(101L) } returns emptyList()

        val exportInfo = ExportInfo("workout_indoor_100000", FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        val records = mutableListOf<RecordMesg>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(RecordMesgListener { records.add(it) })

        FileInputStream(exportedFile).use { fis ->
            decode.read(fis, broadcaster)
        }

        assertEquals(1, records.size)
        assertNull("Indoor session must omit positionLat", records[0].positionLat)
        assertNull("Indoor session must omit positionLong", records[0].positionLong)
        assertEquals(155.toShort(), records[0].heartRate)
        assertEquals(92.toShort(), records[0].cadence)
        assertEquals(240, records[0].power)
    }

    @Test
    fun testFitFileWriter_utcTimestampPreservationUnderDifferentTimezones() {
        val originalTz = java.util.TimeZone.getDefault()
        try {
            val testZones = listOf("GMT+2", "GMT+9", "GMT-5")
            for (zoneId in testZones) {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zoneId))

                val summaryCursor = createMockSummaryCursor(timeStart = "2026-10-03 10:00:00", totalTime = "120")
                every {
                    mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
                } returns summaryCursor

                val sampleList = listOf(
                    mapOf(
                        WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:00",
                        SensorType.LATITUDE.name to 52.5200,
                        SensorType.LONGITUDE.name to 13.4050
                    )
                )
                val samplesCursor = createMockSamplesCursor(sampleList)
                every {
                    mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
                } returns samplesCursor

                every { mockSportTypeDb.getBSportType(1L) } returns BSportType.BIKE
                every { mockSportTypeDb.getTcxName(1L) } returns "Biking"
                every { mockLapsDb.getLaps(101L) } returns emptyList()

                val exportInfo = ExportInfo("workout_tz_$zoneId", FileFormat.FIT, ExportType.FILE)
                val writer = FitFileWriter(mockContext)
                val result = writer.doExport(exportInfo)

                assertTrue("Export must succeed under $zoneId", result.success())

                val exportedFile = File(tempDir, exportInfo.shortPath)
                val fileIds = mutableListOf<FileIdMesg>()
                val records = mutableListOf<RecordMesg>()
                val decode = Decode()
                val broadcaster = MesgBroadcaster(decode)
                broadcaster.addListener(FileIdMesgListener { fileIds.add(it) })
                broadcaster.addListener(RecordMesgListener { records.add(it) })

                FileInputStream(exportedFile).use { fis ->
                    decode.read(fis, broadcaster)
                }

                // Expected timestamp in UTC: "2026-10-03 10:00:00" UTC
                // 2026-10-03 10:00:00 UTC epoch seconds
                val expectedUtcDate = java.time.LocalDateTime.parse("2026-10-03T10:00:00")
                    .toInstant(java.time.ZoneOffset.UTC)

                val fileIdTimestamp = fileIds[0].timeCreated.date.toInstant()
                assertEquals("FileId timeCreated must match UTC ground truth under $zoneId",
                    expectedUtcDate.epochSecond, fileIdTimestamp.epochSecond)

                val recordTimestamp = records[0].timestamp.date.toInstant()
                assertEquals("Record timestamp must match UTC ground truth under $zoneId",
                    expectedUtcDate.epochSecond, recordTimestamp.epochSecond)
            }
        } finally {
            java.util.TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun testFitFileWriter_synthesizesTransientLapWhenNoLapsInDatabase() {
        val summaryCursor = createMockSummaryCursor(totalTime = "180", distanceTotal = "2000.0")
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:00",
                SensorType.DISTANCE_m.name to 0.0
            ),
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:03:00",
                SensorType.DISTANCE_m.name to 2000.0
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.BIKE
        every { mockSportTypeDb.getTcxName(1L) } returns "Biking"
        // 0 laps in Laps.db
        every { mockLapsDb.getLaps(101L) } returns emptyList()

        val exportInfo = ExportInfo("workout_synth_lap", FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        val lapMesgs = mutableListOf<LapMesg>()
        val sessions = mutableListOf<SessionMesg>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(LapMesgListener { lapMesgs.add(it) })
        broadcaster.addListener(SessionMesgListener { sessions.add(it) })

        FileInputStream(exportedFile).use { fis ->
            decode.read(fis, broadcaster)
        }

        assertEquals("Exactly 1 synthesized lap must be encoded", 1, lapMesgs.size)
        assertEquals(180f, lapMesgs[0].totalTimerTime)
        assertEquals(180f, lapMesgs[0].totalElapsedTime)
        assertEquals(2000f, lapMesgs[0].totalDistance)
        assertEquals(1, sessions[0].numLaps)
        assertEquals(180f, sessions[0].totalTimerTime)
        assertEquals(180f, sessions[0].totalElapsedTime)
    }

    @Test
    fun testFitFileWriter_failsFastOnMissingOrCorruptStartTime() {
        val corruptedStarts = listOf("", "   ", "invalid-date", "2026/10/03 10:00:00")
        for (badStart in corruptedStarts) {
            val summaryCursor = createMockSummaryCursor(timeStart = badStart)
            every {
                mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
            } returns summaryCursor

            val exportInfo = ExportInfo("workout_bad_start", FileFormat.FIT, ExportType.FILE)
            val writer = FitFileWriter(mockContext)
            val result = writer.doExport(exportInfo)

            org.junit.Assert.assertFalse("Export must fail on bad start time: $badStart", result.success())
        }
    }

    @Test
    fun testFitFileWriter_failsFastOnSampleCursorException() {
        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val badSamplesCursor = mockk<Cursor>(relaxed = true)
        every { badSamplesCursor.moveToNext() } throws android.database.sqlite.SQLiteException("Simulated I/O corruption")
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns badSamplesCursor

        val exportInfo = ExportInfo("workout_cursor_error", FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        org.junit.Assert.assertFalse("Export must fail on cursor exception", result.success())
    }

    @Test
    fun testFitFileWriter_reExportHistoricalWorkoutPreservesGroundTruth() {
        val summaryCursor = createMockSummaryCursor(timeStart = "2024-05-15 08:30:00", totalTime = "60")
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(WorkoutSamplesDbHelper.TIME to "2024-05-15 08:30:00")
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.RUN
        every { mockSportTypeDb.getTcxName(1L) } returns "Running"
        every { mockLapsDb.getLaps(101L) } returns emptyList()

        val exportInfo = ExportInfo("workout_historical", FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        val fileIds = mutableListOf<FileIdMesg>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(FileIdMesgListener { fileIds.add(it) })
        FileInputStream(exportedFile).use { decode.read(it, broadcaster) }

        val expectedUtc = java.time.LocalDateTime.parse("2024-05-15T08:30:00").toInstant(java.time.ZoneOffset.UTC)
        assertEquals(expectedUtc.epochSecond, fileIds[0].timeCreated.date.toInstant().epochSecond)
    }

    @Test
    fun testFitFileWriter_deletesPartialFileOnExportFailure() {
        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val badSamplesCursor = mockk<Cursor>(relaxed = true)
        every { badSamplesCursor.moveToNext() } throws java.lang.RuntimeException("Stream serialization error")
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns badSamplesCursor

        val exportInfo = ExportInfo("workout_partial_delete", FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        org.junit.Assert.assertFalse(result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        org.junit.Assert.assertFalse("Partial FIT file must be deleted upon export failure", exportedFile.exists())
    }

    @Test
    fun testFitFileWriter_abortsOnNegativeTotalTimeInconsistency() {
        val summaryCursor = createMockSummaryCursor(totalTime = "-120.0")
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val exportInfo = ExportInfo("workout_negative_time", FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        org.junit.Assert.assertFalse("Export must fail on negative totalTime", result.success())
        assertTrue("Error answer must mention negative totalTime", result.answer().contains("negative totalTime"))
    }

    @Test
    fun testFitFileWriter_allowsWorkoutWithLongPauses() {
        // Workout active timer time is 300s (5 min), but elapsed time between first and last sample is 900s (15 min)
        val summaryCursor = createMockSummaryCursor(timeStart = "2026-10-03 10:00:00", totalTime = "300")
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:00"),
            mapOf(WorkoutSamplesDbHelper.TIME to "2026-10-03 10:15:00") // 900 seconds later (10 minute pause)
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.RUN
        every { mockSportTypeDb.getTcxName(1L) } returns "Running"
        every { mockLapsDb.getLaps(101L) } returns emptyList()

        val exportInfo = ExportInfo("workout_with_pause", FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue("Export must succeed for workout with pauses", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        val sessions = mutableListOf<SessionMesg>()
        val lapMesgs = mutableListOf<LapMesg>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(SessionMesgListener { sessions.add(it) })
        broadcaster.addListener(LapMesgListener { lapMesgs.add(it) })

        FileInputStream(exportedFile).use { decode.read(it, broadcaster) }

        assertEquals(1, sessions.size)
        assertEquals(300f, sessions[0].totalTimerTime)
        assertEquals(900f, sessions[0].totalElapsedTime)
        assertEquals(1, lapMesgs.size)
        assertEquals(300f, lapMesgs[0].totalTimerTime)
        assertEquals(900f, lapMesgs[0].totalElapsedTime)
    }
}

