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
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.garmin.fit.Decode
import com.garmin.fit.HrvMesg
import com.garmin.fit.HrvMesgListener
import com.garmin.fit.MesgBroadcaster
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/**
 * TST-PER-042.4: Unit tests for Garmin FIT HrvMesg export interleaving and non-HRV omission (REQ-PER-034.4).
 */
class FitFileWriterHrvTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockRrDb: WorkoutRrDatabaseManager
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

        tempDir = Files.createTempDirectory("fit_writer_hrv_test").toFile()

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
        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.RUN

        mockkObject(WorkoutRepository.Companion)
        val mockRepo = mockk<WorkoutRepository>(relaxed = true)
        every { WorkoutRepository.getInstance(any()) } returns mockRepo
        every { mockRepo.workoutSummariesDatabaseManager } returns mockSummariesDb
        every { mockRepo.workoutSamplesDatabaseManager } returns mockSamplesDb
        every { mockRepo.lapsDatabaseManagerInstance } returns mockLapsDb
        every { mockRepo.getLaps(any()) } returns emptyList()

        mockRrDb = mockk(relaxed = true)
        WorkoutRrDatabaseManager.resetForTesting(mockRrDb)
    }

    @After
    fun tearDown() {
        WorkoutRrDatabaseManager.resetForTesting(null)
        unmockkAll()
        tempDir.deleteRecursively()
    }

    private fun createMockSummaryCursor(
        workoutId: Long = 101L,
        timeStart: String = "2026-11-10 12:00:00",
        totalTime: String = "60.0",
        distanceTotal: String = "100.0"
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
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.WORKOUT_NAME)) } returns "Workout"
        every { cursor.getLong(0) } returns workoutId
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.C_ID)) } returns workoutId
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.SPORT_ID)) } returns 1L
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.TRAINER)) } returns 0
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.CALORIES)) } returns 50
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.ASCENDING)) } returns 10
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.DESCENDING)) } returns 5
        every { cursor.getDouble(columnNames.indexOf(WorkoutSummaries.SPEED_AVERAGE_mps)) } returns 3.0

        return cursor
    }

    private fun createMockSamplesCursor(samples: List<Map<String, Any?>>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        val allColumns = arrayOf(
            WorkoutSamplesDbHelper.C_ID,
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
    fun testFitFileWriter_writesHrvMesgWhenRrIntervalsExist() {
        val baseFileName = "2026-11-10_120000"
        val workoutId = 101L

        val summaryCursor = createMockSummaryCursor(workoutId = workoutId)
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-11-10 12:00:01",
                SensorType.LATITUDE.name to 52.5200,
                SensorType.LONGITUDE.name to 13.4050,
                SensorType.ALTITUDE.name to 45.0,
                SensorType.DISTANCE_m.name to 10.0,
                SensorType.SPEED_mps.name to 5.0,
                SensorType.HR.name to 75,
                SensorType.CADENCE.name to 85.0,
                SensorType.POWER.name to 200.0,
                SensorType.TEMPERATURE.name to 21.0
            ),
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-11-10 12:00:02",
                SensorType.LATITUDE.name to 52.5201,
                SensorType.LONGITUDE.name to 13.4052,
                SensorType.ALTITUDE.name to 46.0,
                SensorType.DISTANCE_m.name to 20.0,
                SensorType.SPEED_mps.name to 5.2,
                SensorType.HR.name to 76,
                SensorType.CADENCE.name to 86.0,
                SensorType.POWER.name to 210.0,
                SensorType.TEMPERATURE.name to 21.0
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        val rrList = listOf(
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 0, beatTimestampMs = 1700000000000L, rrMs = 800f),
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 1, beatTimestampMs = 1700000000800L, rrMs = 820f),
            WorkoutRrInterval(workoutId = workoutId, beatIndex = 2, beatTimestampMs = 1700000001620L, rrMs = 810f)
        )
        every { mockRrDb.getIntervalsForWorkout(workoutId) } returns rrList

        val exportInfo = ExportInfo(baseFileName, FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue("FIT export must succeed: ${result.answer()}", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue(exportedFile.exists())

        val hrvMesgs = mutableListOf<HrvMesg>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(HrvMesgListener { hrvMesgs.add(it) })

        FileInputStream(exportedFile).use { decode.read(it, broadcaster) }

        assertTrue("Exported FIT must contain at least one HrvMesg", hrvMesgs.isNotEmpty())
        val decodedTimes = mutableListOf<Float>()
        for (m in hrvMesgs) {
            for (i in 0 until m.numTime) {
                val t = m.getTime(i)
                if (t != null) decodedTimes.add(t)
            }
        }
        assertEquals("Must decode 3 intervals", 3, decodedTimes.size)
        assertEquals(0.800f, decodedTimes[0], 0.001f)
        assertEquals(0.820f, decodedTimes[1], 0.001f)
        assertEquals(0.810f, decodedTimes[2], 0.001f)
    }

    @Test
    fun testFitFileWriter_omitsHrvMesgWhenNoRrIntervals() {
        val baseFileName = "2026-11-10_130000"
        val workoutId = 102L

        val summaryCursor = createMockSummaryCursor(workoutId = workoutId)
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-11-10 13:00:01",
                SensorType.LATITUDE.name to 52.5200,
                SensorType.LONGITUDE.name to 13.4050,
                SensorType.ALTITUDE.name to 45.0,
                SensorType.DISTANCE_m.name to 10.0,
                SensorType.SPEED_mps.name to 5.0,
                SensorType.HR.name to 75,
                SensorType.CADENCE.name to 85.0,
                SensorType.POWER.name to 200.0,
                SensorType.TEMPERATURE.name to 21.0
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor
        every { mockRrDb.getIntervalsForWorkout(workoutId) } returns emptyList()

        val exportInfo = ExportInfo(baseFileName, FileFormat.FIT, ExportType.FILE)
        val writer = FitFileWriter(mockContext)
        val result = writer.doExport(exportInfo)

        assertTrue("FIT export must succeed: ${result.answer()}", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        val hrvMesgs = mutableListOf<HrvMesg>()
        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(HrvMesgListener { hrvMesgs.add(it) })

        FileInputStream(exportedFile).use { decode.read(it, broadcaster) }

        assertEquals("Zero HrvMesg must be written when no RR intervals exist", 0, hrvMesgs.size)
    }
}
