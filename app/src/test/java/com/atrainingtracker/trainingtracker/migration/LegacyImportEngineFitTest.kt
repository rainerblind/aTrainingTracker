/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.migration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.util.Log
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.garmin.fit.ActivityMesg
import com.garmin.fit.DateTime
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.LapMesg
import com.garmin.fit.Manufacturer
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unit tests verifying high-fidelity Garmin FIT file ingestion, coordinate semicircle decoding,
 * sport mapping, duplicate detection, and corruption resilience (REQ-DAT-019, TST-DAT-014).
 */
class LegacyImportEngineFitTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase

    private val contentValueStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())
    private val insertedSamples = mutableListOf<Map<String, Any?>>()
    private var insertedSummary: Map<String, Any?>? = null

    private fun io.mockk.MockKAnswerScope<*, *>.getRealInstance(): ContentValues {
        try {
            var obj: Any? = call.invocation.originalCall
            while (obj != null) {
                for (f in obj.javaClass.declaredFields) {
                    if (f.name == "self" || f.name == "\$self" || f.name.endsWith("\$self")) {
                        f.isAccessible = true
                        val s = f.get(obj)
                        if (s is ContentValues && s !== this.self) {
                            return s
                        }
                    }
                }
                val nextField = obj.javaClass.declaredFields.firstOrNull { 
                    it.name.contains("originalCall") || it.name.contains("callable") 
                }
                obj = nextField?.apply { isAccessible = true }?.get(obj)
            }
        } catch (_: Exception) { }
        return self as ContentValues
    }

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
        every { TrainingApplication.uploadToCommunity(any()) } returns false
        every { TrainingApplication.uploadImportedWorkoutsToStrava() } returns false

        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val results = arg<FloatArray>(4)
            results[0] = 100.0f
        }

        mockContext = mockk(relaxed = true)
        mockSqlDb = mockk(relaxed = true)

        mockSummariesDb = mockk(relaxed = true)
        every { mockSummariesDb.database } returns mockSqlDb
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockSamplesDb = mockk(relaxed = true)
        every { mockSamplesDb.database } returns mockSqlDb
        mockkStatic(WorkoutSamplesDatabaseManager::class)
        every { WorkoutSamplesDatabaseManager.getInstance(any()) } returns mockSamplesDb

        mockLapsDb = mockk(relaxed = true)
        every { mockLapsDb.database } returns mockSqlDb
        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any()) } returns mockLapsDb

        mockSportTypeDb = mockk(relaxed = true)
        every { mockSportTypeDb.getSportTypeIdFromTcxName(any()) } returns 1L
        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.BIKE
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb
        every { SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE) } returns 1L
        every { SportTypeDatabaseManager.getSportTypeId(BSportType.RUN) } returns 2L

        contentValueStores.clear()
        insertedSamples.clear()
        insertedSummary = null

        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<String>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Long>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Int>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Double>()
            Unit
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Float>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Float>()
            Unit
        }
        every { constructedWith<ContentValues>().containsKey(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.containsKey(firstArg<String>()) ?: false
        }
        every { constructedWith<ContentValues>().getAsString(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.get(firstArg<String>())?.toString()
        }
        every { constructedWith<ContentValues>().getAsLong(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toLong()
        }
        every { constructedWith<ContentValues>().getAsInteger(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toInt()
        }
        every { constructedWith<ContentValues>().getAsDouble(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            (map?.get(firstArg<String>()) as? Number)?.toDouble()
        }
        every { constructedWith<ContentValues>().size() } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.size ?: 0
        }

        // Database query mocking: workout does not exist initially
        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } answers {
            val cursor = mockk<android.database.Cursor>(relaxed = true)
            every { cursor.count } returns 0
            every { cursor.moveToFirst() } returns false
            cursor
        }

        // Database insert capture
        every { mockSqlDb.insert(any(), any(), any()) } answers {
            val table = firstArg<String>()
            val cv = thirdArg<ContentValues>()
            val cvMap = contentValueStores[cv]?.toMap() ?: emptyMap()
            if (table == WorkoutSummaries.TABLE) {
                insertedSummary = cvMap
                101L
            } else {
                insertedSamples.add(cvMap)
                1L
            }
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createSampleFitFile(
        file: File,
        includeGps: Boolean = true,
        sport: Sport = Sport.CYCLING,
        subSport: SubSport = SubSport.ROAD
    ) {
        val encoder = FileEncoder(file, Fit.ProtocolVersion.V2_0)

        val fileId = FileIdMesg()
        fileId.type = com.garmin.fit.File.ACTIVITY
        fileId.manufacturer = Manufacturer.GARMIN
        fileId.timeCreated = DateTime(Date(1696327200000L))
        encoder.write(fileId)

        val rec1 = RecordMesg()
        rec1.timestamp = DateTime(Date(1696327200000L))
        if (includeGps) {
            rec1.positionLat = Math.round(52.5200 * (2147483648.0 / 180.0)).toInt()
            rec1.positionLong = Math.round(13.4050 * (2147483648.0 / 180.0)).toInt()
            rec1.altitude = 45.0f
        }
        rec1.distance = 0.0f
        rec1.speed = 4.5f
        rec1.heartRate = 135.toShort()
        rec1.cadence = 80.toShort()
        rec1.power = 190
        encoder.write(rec1)

        val rec2 = RecordMesg()
        rec2.timestamp = DateTime(Date(1696327210000L))
        if (includeGps) {
            rec2.positionLat = Math.round(52.5210 * (2147483648.0 / 180.0)).toInt()
            rec2.positionLong = Math.round(13.4060 * (2147483648.0 / 180.0)).toInt()
            rec2.altitude = 48.0f
        }
        rec2.distance = 100.0f
        rec2.speed = 5.0f
        rec2.heartRate = 145.toShort()
        rec2.cadence = 85.toShort()
        rec2.power = 220
        encoder.write(rec2)

        val lap = LapMesg()
        lap.startTime = DateTime(Date(1696327200000L))
        lap.totalElapsedTime = 10.0f
        lap.totalTimerTime = 10.0f
        lap.totalDistance = 100.0f
        lap.totalCalories = 50
        encoder.write(lap)

        val session = SessionMesg()
        session.startTime = DateTime(Date(1696327200000L))
        session.totalElapsedTime = 10.0f
        session.totalTimerTime = 10.0f
        session.totalDistance = 100.0f
        session.sport = sport
        session.subSport = subSport
        encoder.write(session)

        val activity = ActivityMesg()
        activity.timestamp = DateTime(Date(1696327210000L))
        activity.numSessions = 1
        encoder.write(activity)

        encoder.close()
    }

    @Test
    fun importFromFit_parsesTrackpointsElevationAndTimestamps() = runBlocking {
        val tempFile = File.createTempFile("test_fit_workout", ".fit").apply { deleteOnExit() }
        createSampleFitFile(tempFile, includeGps = true, sport = Sport.CYCLING, subSport = SubSport.ROAD)

        val status = LegacyImportEngine.importFromFitInternal(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, status)

        assertEquals(2, insertedSamples.size)
        val s1 = insertedSamples[0]
        val expectedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(1696327200000L))
        assertEquals(expectedTime, s1["time"])
        assertEquals(135, s1[SensorType.HR.name])
        assertEquals(190.0, (s1[SensorType.POWER.name] as Number).toDouble(), 0.001)

        val lat = (s1[SensorType.LATITUDE.name] as Number).toDouble()
        val lng = (s1[SensorType.LONGITUDE.name] as Number).toDouble()
        assertEquals(52.5200, lat, 0.0001)
        assertEquals(13.4050, lng, 0.0001)

        assertNotNull(insertedSummary)
        assertEquals("FIT", insertedSummary?.get(WorkoutSummaries.SOURCE))
        assertEquals("BIKE", insertedSummary?.get(WorkoutSummaries.B_SPORT))
    }

    @Test
    fun importFromFit_skipsDuplicateWhenAlreadyExisting() = runBlocking {
        val tempFile = File.createTempFile("test_fit_duplicate", ".fit").apply { deleteOnExit() }
        createSampleFitFile(tempFile)

        // Mock that workout already exists in database
        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } answers {
            val cursor = mockk<android.database.Cursor>(relaxed = true)
            every { cursor.count } returns 1
            every { cursor.moveToFirst() } returns true
            every { cursor.getLong(0) } returns 42L
            cursor
        }

        val status = LegacyImportEngine.importFromFitInternal(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.DUPLICATE_SKIPPED, status)
        assertEquals(0, insertedSamples.size)
    }

    @Test
    fun importFromFit_handlesIndoorWorkoutWithoutGpsCoordinates() = runBlocking {
        val tempFile = File.createTempFile("test_fit_indoor", ".fit").apply { deleteOnExit() }
        createSampleFitFile(tempFile, includeGps = false, sport = Sport.FITNESS_EQUIPMENT, subSport = SubSport.INDOOR_CYCLING)

        val status = LegacyImportEngine.importFromFitInternal(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, status)

        assertEquals(2, insertedSamples.size)
        val s1 = insertedSamples[0]
        assertNull(s1[SensorType.LATITUDE.name])
        assertNull(s1[SensorType.LONGITUDE.name])
        assertEquals(135, s1[SensorType.HR.name])
        assertEquals(190.0, (s1[SensorType.POWER.name] as Number).toDouble(), 0.001)

        assertNotNull(insertedSummary)
        assertEquals("FIT", insertedSummary?.get(WorkoutSummaries.SOURCE))
        assertEquals("BIKE", insertedSummary?.get(WorkoutSummaries.B_SPORT))
    }

    @Test
    fun importFromFit_handlesCorruptedFileGracefully() = runBlocking {
        val tempFile = File.createTempFile("test_corrupted", ".fit").apply {
            writeBytes(byteArrayOf(0x00, 0x12, 0x34, 0x56, 0x78))
            deleteOnExit()
        }

        val status = LegacyImportEngine.importFromFitInternal(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.FAILED, status)
        assertEquals(0, insertedSamples.size)
    }
}
