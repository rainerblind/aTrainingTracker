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
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.exporter.ExportInfo
import com.atrainingtracker.trainingtracker.exporter.ExportType
import com.atrainingtracker.trainingtracker.exporter.FileFormat
import com.atrainingtracker.trainingtracker.ui.aftermath.SampleConsumer
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.garmin.fit.Decode
import com.garmin.fit.DeveloperDataIdMesg
import com.garmin.fit.DeveloperDataIdMesgListener
import com.garmin.fit.DeveloperField
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventMesgListener
import com.garmin.fit.EventType
import com.garmin.fit.FieldDescriptionMesg
import com.garmin.fit.FieldDescriptionMesgListener
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RadarThreatLevelType
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files

/**
 * Unit and round-trip verification tests for Garmin FIT radar export (REQ-EXP-042 / TST-EXP-042).
 * Verifies native [EventMesg] RADAR_THREAT_ALERT events and FIT V2.0 developer fields on [RecordMesg].
 */
class FitFileWriterRadarTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockRepo: WorkoutRepository
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

        tempDir = Files.createTempDirectory("fit_radar_export_test").toFile()
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
        every { mockSportTypeDb.getBSportType(any()) } returns BSportType.BIKE
        every { mockSportTypeDb.getTcxName(any()) } returns "Biking"

        mockRepo = mockk(relaxed = true)
        every { mockRepo.getWorkoutExportHeader(any()) } answers {
            com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutExportHeader(
                workoutId = 101L,
                fileBaseName = firstArg<String>(),
                startTime = "2026-10-03 10:00:00",
                totalTime = "120",
                data = "DSHCPAG",
                goal = "Radar Ride",
                method = "Outdoor",
                totalDistance = "2000.0",
                description = "Radar Test",
                workoutName = "Varia Ride",
                sportTypeId = 1L,
                indoorTrainerSession = false,
                calories = 150,
                totalAscent = 10,
                totalDescent = 10,
                avgSpeedMps = 7.5
            )
        }
        every { mockRepo.getWorkoutIdByBaseFileName(any()) } returns 101L
        WorkoutRepository.resetForTesting(mockRepo)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
        tempDir.deleteRecursively()
    }

    private fun createMockSummaryCursor(): Cursor {
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

        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.TIME_START)) } returns "2026-10-03 10:00:00"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.TIME_TOTAL_s)) } returns "120"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.GC_DATA)) } returns "DSHCPAG"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.GOAL)) } returns "Radar Ride"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.METHOD)) } returns "Outdoor"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.DISTANCE_TOTAL_m)) } returns "2000.0"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.DESCRIPTION)) } returns "Radar Test"
        every { cursor.getString(columnNames.indexOf(WorkoutSummaries.WORKOUT_NAME)) } returns "Varia Ride"
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.C_ID)) } returns 101L
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.SPORT_ID)) } returns 1L
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.TRAINER)) } returns 0
        every { cursor.getInt(columnNames.indexOf(WorkoutSummaries.CALORIES)) } returns 150
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.ASCENDING)) } returns 10L
        every { cursor.getLong(columnNames.indexOf(WorkoutSummaries.DESCENDING)) } returns 10L
        every { cursor.getDouble(columnNames.indexOf(WorkoutSummaries.SPEED_AVERAGE_mps)) } returns 7.5

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
            SensorType.TEMPERATURE.name,
            SensorType.RADAR_THREAT_COUNT.name,
            SensorType.RADAR_CLOSEST_DISTANCE_m.name,
            SensorType.RADAR_APPROACH_SPEED_mps.name,
            SensorType.RADAR_VEHICLE_COUNT.name
        )

        every { cursor.columnNames } returns allColumns.toTypedArray()
        every { cursor.getColumnIndex(any()) } answers { allColumns.indexOf(firstArg<String>()) }
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
        every { cursor.moveToFirst() } answers {
            if (samples.isNotEmpty()) {
                currentIndex = 0
                true
            } else {
                false
            }
        }
        every { cursor.count } returns samples.size

        every { cursor.isNull(any()) } answers {
            val colIdx = firstArg<Int>()
            val colName = allColumns[colIdx]
            samples[currentIndex][colName] == null
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
    fun testFitFileWriter_withRadarSamples_exportsRadarThreatAlertEventsAndDeveloperFields() {
        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:00",
                SensorType.LATITUDE.name to 48.137,
                SensorType.LONGITUDE.name to 11.576,
                SensorType.DISTANCE_m.name to 10.0,
                SensorType.SPEED_mps.name to 7.0,
                SensorType.RADAR_VEHICLE_COUNT.name to 0,
                SensorType.RADAR_THREAT_COUNT.name to 0,
                SensorType.RADAR_APPROACH_SPEED_mps.name to 0.0
            ),
            mapOf(
                // Vehicle 1 passed at 5 m/s closing speed (normal threat < 25 km/h)
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:01",
                SensorType.LATITUDE.name to 48.138,
                SensorType.LONGITUDE.name to 11.577,
                SensorType.DISTANCE_m.name to 20.0,
                SensorType.SPEED_mps.name to 7.0,
                SensorType.RADAR_VEHICLE_COUNT.name to 1,
                SensorType.RADAR_THREAT_COUNT.name to 1,
                SensorType.RADAR_APPROACH_SPEED_mps.name to 5.0
            ),
            mapOf(
                // Vehicle 2 passed at 20 m/s closing speed (high threat)
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:02",
                SensorType.LATITUDE.name to 48.139,
                SensorType.LONGITUDE.name to 11.578,
                SensorType.DISTANCE_m.name to 30.0,
                SensorType.SPEED_mps.name to 7.0,
                SensorType.RADAR_VEHICLE_COUNT.name to 2,
                SensorType.RADAR_THREAT_COUNT.name to 2,
                SensorType.RADAR_APPROACH_SPEED_mps.name to 20.0
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockRepo.hasRadarData(any(), any()) } returns true
        every { mockRepo.getExtremaValue(any(), any(), any()) } returns null
        every { mockRepo.getLaps(any()) } returns emptyList()

        // Delegate streamSamples to real WorkoutRepository implementation pattern
        every { mockRepo.streamSamples(any(), any()) } answers {
            val consumer = secondArg<SampleConsumer>()
            sampleList.forEach { s ->
                consumer.accept(
                    com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSampleRecord(
                        time = s[WorkoutSamplesDbHelper.TIME] as String,
                        latitude = (s[SensorType.LATITUDE.name] as? Number)?.toDouble(),
                        longitude = (s[SensorType.LONGITUDE.name] as? Number)?.toDouble(),
                        distanceM = (s[SensorType.DISTANCE_m.name] as? Number)?.toDouble(),
                        speedMps = (s[SensorType.SPEED_mps.name] as? Number)?.toDouble(),
                        radarThreatCount = (s[SensorType.RADAR_THREAT_COUNT.name] as? Number)?.toInt(),
                        radarApproachSpeedMps = (s[SensorType.RADAR_APPROACH_SPEED_mps.name] as? Number)?.toDouble(),
                        radarVehicleCount = (s[SensorType.RADAR_VEHICLE_COUNT.name] as? Number)?.toInt()
                    )
                )
            }
        }

        val exportInfo = ExportInfo(
            "test_radar_workout",
            FileFormat.FIT,
            ExportType.FILE
        )

        val fitFileWriter = FitFileWriter(mockContext)
        val result = fitFileWriter.doExport(exportInfo)
        assertTrue("Export must succeed: ${result.answer()}", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue("FIT file must exist", exportedFile.exists())

        // Verify with Garmin FIT SDK Decode
        val devIds = mutableListOf<DeveloperDataIdMesg>()
        val devFieldDescs = mutableListOf<FieldDescriptionMesg>()
        val events = mutableListOf<EventMesg>()
        val records = mutableListOf<RecordMesg>()

        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(DeveloperDataIdMesgListener { devIds.add(it) })
        broadcaster.addListener(FieldDescriptionMesgListener { devFieldDescs.add(it) })
        broadcaster.addListener(EventMesgListener { events.add(it) })
        broadcaster.addListener(RecordMesgListener { records.add(it) })

        FileInputStream(exportedFile).use { fis ->
            val decodeResult = decode.read(fis, broadcaster)
            assertTrue("Decode must return true", decodeResult)
        }

        // 1. Verify Developer Data ID
        assertEquals(1, devIds.size)
        assertEquals(0.toShort(), devIds[0].developerDataIndex)

        // 2. Verify Field Descriptions (radar_current, radar_total, passing_speed, passing_speed_abs)
        assertEquals(4, devFieldDescs.size)
        val fieldNames = devFieldDescs.map { it.getFieldName(0) }
        assertTrue(fieldNames.contains("radar_current"))
        assertTrue(fieldNames.contains("radar_total"))
        assertTrue(fieldNames.contains("passing_speed"))
        assertTrue(fieldNames.contains("passing_speed_abs"))

        // 3. Verify Radar Threat Alert Events (2 pass events: 0->1 and 1->2)
        assertEquals(2, events.size)
        assertEquals(Event.RADAR_THREAT_ALERT, events[0].event)
        assertEquals(EventType.MARKER, events[0].eventType)
        assertEquals(1.toShort(), events[0].radarThreatCount)
        assertEquals(RadarThreatLevelType.THREAT_APPROACHING, events[0].radarThreatLevelMax)
        assertEquals(5.0f, events[0].radarThreatMaxApproachSpeed)

        assertEquals(Event.RADAR_THREAT_ALERT, events[1].event)
        assertEquals(EventType.MARKER, events[1].eventType)
        assertEquals(2.toShort(), events[1].radarThreatCount)
        assertEquals(RadarThreatLevelType.THREAT_APPROACHING_FAST, events[1].radarThreatLevelMax)
        assertEquals(20.0f, events[1].radarThreatMaxApproachSpeed)

        // 4. Verify RecordMesg Developer Fields
        assertEquals(3, records.size)
        val rec2DevFields = records[1].developerFields.associateBy { it.num }
        assertNotNull(rec2DevFields[0]) // radar_current
        assertNotNull(rec2DevFields[1]) // radar_total
        assertNotNull(rec2DevFields[2]) // passing_speed
        assertNotNull(rec2DevFields[3]) // passing_speed_abs

        assertEquals(1, (rec2DevFields[0]?.value as Number).toInt())
        assertEquals(1, (rec2DevFields[1]?.value as Number).toInt())
        assertEquals(5.0, (rec2DevFields[2]?.value as Number).toDouble(), 0.05)
        assertEquals(12.0, (rec2DevFields[3]?.value as Number).toDouble(), 0.05) // 7.0 rider + 5.0 approach = 12.0
    }

    @Test
    fun testFitFileWriter_withoutRadarSamples_emitsZeroRadarEventsAndZeroDeveloperFields() {
        val summaryCursor = createMockSummaryCursor()
        every {
            mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any())
        } returns summaryCursor

        val sampleList = listOf(
            mapOf(
                WorkoutSamplesDbHelper.TIME to "2026-10-03 10:00:00",
                SensorType.LATITUDE.name to 48.137,
                SensorType.LONGITUDE.name to 11.576,
                SensorType.DISTANCE_m.name to 10.0,
                SensorType.SPEED_mps.name to 7.0
            )
        )
        val samplesCursor = createMockSamplesCursor(sampleList)
        every {
            mockSqlDb.query(match { it.startsWith("samples_") }, any(), any(), any(), any(), any(), any())
        } returns samplesCursor

        every { mockRepo.hasRadarData(any(), any()) } returns false
        every { mockRepo.getExtremaValue(any(), any(), any()) } returns null
        every { mockRepo.getLaps(any()) } returns emptyList()

        every { mockRepo.streamSamples(any(), any()) } answers {
            val consumer = secondArg<SampleConsumer>()
            sampleList.forEach { s ->
                consumer.accept(
                    com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSampleRecord(
                        time = s[WorkoutSamplesDbHelper.TIME] as String,
                        latitude = (s[SensorType.LATITUDE.name] as? Number)?.toDouble(),
                        longitude = (s[SensorType.LONGITUDE.name] as? Number)?.toDouble(),
                        distanceM = (s[SensorType.DISTANCE_m.name] as? Number)?.toDouble(),
                        speedMps = (s[SensorType.SPEED_mps.name] as? Number)?.toDouble()
                    )
                )
            }
        }

        val exportInfo = ExportInfo(
            "test_non_radar_workout",
            FileFormat.FIT,
            ExportType.FILE
        )

        val fitFileWriter = FitFileWriter(mockContext)
        val result = fitFileWriter.doExport(exportInfo)
        assertTrue("Export must succeed: ${result.answer()}", result.success())

        val exportedFile = File(tempDir, exportInfo.shortPath)
        assertTrue("FIT file must exist", exportedFile.exists())

        val devIds = mutableListOf<DeveloperDataIdMesg>()
        val events = mutableListOf<EventMesg>()
        val records = mutableListOf<RecordMesg>()

        val decode = Decode()
        val broadcaster = MesgBroadcaster(decode)
        broadcaster.addListener(DeveloperDataIdMesgListener { devIds.add(it) })
        broadcaster.addListener(EventMesgListener { events.add(it) })
        broadcaster.addListener(RecordMesgListener { records.add(it) })

        FileInputStream(exportedFile).use { fis ->
            val decodeResult = decode.read(fis, broadcaster)
            assertTrue("Decode must return true", decodeResult)
        }

        // Verify zero developer IDs, zero radar threat events, and zero developer fields
        assertEquals(0, devIds.size)
        assertEquals(0, events.filter { it.event == Event.RADAR_THREAT_ALERT }.size)
        assertTrue(records.all { !it.developerFields.iterator().hasNext() })
    }
}
