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
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
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
import java.util.Date

/**
 * Unit tests verifying enhanced altitude and speed parsing in modern Garmin FIT workout files
 * with legacy field fallback and plausibility bounds checks (REQ-MIG-033, TST-MIG-030).
 */
class LegacyImportEngineFitAltitudeTest {

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

        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } answers {
            val cursor = mockk<android.database.Cursor>(relaxed = true)
            every { cursor.count } returns 0
            every { cursor.moveToFirst() } returns false
            cursor
        }

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

    private fun writeFitFile(
        file: File,
        records: List<RecordMesg>,
        laps: List<LapMesg> = listOf(LapMesg().apply {
            startTime = DateTime(Date(1696327200000L))
            totalElapsedTime = 60f
            totalTimerTime = 60f
            totalDistance = 500f
        }),
        session: SessionMesg = SessionMesg().apply {
            sport = Sport.CYCLING
            subSport = SubSport.ROAD
            totalElapsedTime = 60f
            totalTimerTime = 60f
            totalDistance = 500f
        }
    ) {
        val encoder = FileEncoder(file, Fit.ProtocolVersion.V2_0)

        val fileId = FileIdMesg()
        fileId.type = com.garmin.fit.File.ACTIVITY
        fileId.manufacturer = Manufacturer.GARMIN
        fileId.timeCreated = DateTime(Date(1696327200000L))
        encoder.write(fileId)

        records.forEach { encoder.write(it) }
        laps.forEach { encoder.write(it) }
        encoder.write(session)

        encoder.close()
    }

    @Test
    fun testEnhancedAltitude_parsedCorrectly_whenLegacyAltitudeIsNull() = runBlocking {
        val tempFile = File.createTempFile("enhanced_alt_only", ".fit")
        tempFile.deleteOnExit()

        val rec1 = RecordMesg().apply {
            timestamp = DateTime(Date(1696327200000L))
            positionLat = Math.round(52.5200 * (2147483648.0 / 180.0)).toInt()
            positionLong = Math.round(13.4050 * (2147483648.0 / 180.0)).toInt()
            enhancedAltitude = 250.0f
            altitude = null
            distance = 0f
            speed = 5f
        }
        val rec2 = RecordMesg().apply {
            timestamp = DateTime(Date(1696327210000L))
            positionLat = Math.round(52.5210 * (2147483648.0 / 180.0)).toInt()
            positionLong = Math.round(13.4060 * (2147483648.0 / 180.0)).toInt()
            enhancedAltitude = 265.0f
            altitude = null
            distance = 100f
            speed = 5.5f
        }

        writeFitFile(tempFile, listOf(rec1, rec2))

        val result = LegacyImportEngine.importFromFitResult(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, result.status)
        assertEquals(101L, result.workoutId)

        // Verify altitude samples were ingested
        val altSamples = insertedSamples.filter { it.containsKey(SensorType.ALTITUDE.name) }
        assertEquals(2, altSamples.size)
        assertEquals(250.0, (altSamples[0][SensorType.ALTITUDE.name] as Number).toDouble(), 0.01)
        assertEquals(265.0, (altSamples[1][SensorType.ALTITUDE.name] as Number).toDouble(), 0.01)
    }

    @Test
    fun testLegacyAltitude_parsedCorrectly_whenEnhancedAltitudeIsNull() = runBlocking {
        val tempFile = File.createTempFile("legacy_alt_only", ".fit")
        tempFile.deleteOnExit()

        val rec1 = RecordMesg().apply {
            timestamp = DateTime(Date(1696327200000L))
            positionLat = Math.round(52.5200 * (2147483648.0 / 180.0)).toInt()
            positionLong = Math.round(13.4050 * (2147483648.0 / 180.0)).toInt()
            altitude = 180.0f
            enhancedAltitude = null
            distance = 0f
        }

        writeFitFile(tempFile, listOf(rec1))

        val result = LegacyImportEngine.importFromFitResult(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, result.status)

        val altSamples = insertedSamples.filter { it.containsKey(SensorType.ALTITUDE.name) }
        assertEquals(1, altSamples.size)
        assertEquals(180.0, (altSamples[0][SensorType.ALTITUDE.name] as Number).toDouble(), 0.01)
    }

    @Test
    fun testEnhancedAltitude_takesPrecedence_whenBothPresent() = runBlocking {
        val tempFile = File.createTempFile("both_alt", ".fit")
        tempFile.deleteOnExit()

        val rec1 = RecordMesg().apply {
            timestamp = DateTime(Date(1696327200000L))
            positionLat = Math.round(52.5200 * (2147483648.0 / 180.0)).toInt()
            positionLong = Math.round(13.4050 * (2147483648.0 / 180.0)).toInt()
            enhancedAltitude = 350.0f
            altitude = 200.0f
            distance = 0f
        }

        writeFitFile(tempFile, listOf(rec1))

        val result = LegacyImportEngine.importFromFitResult(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, result.status)

        val altSamples = insertedSamples.filter { it.containsKey(SensorType.ALTITUDE.name) }
        assertEquals(1, altSamples.size)
        assertEquals(350.0, (altSamples[0][SensorType.ALTITUDE.name] as Number).toDouble(), 0.01)
    }

    @Test
    fun testEnhancedSpeed_parsedCorrectly_whenLegacySpeedIsNull() = runBlocking {
        val tempFile = File.createTempFile("enhanced_speed_only", ".fit")
        tempFile.deleteOnExit()

        val rec1 = RecordMesg().apply {
            timestamp = DateTime(Date(1696327200000L))
            enhancedSpeed = 8.5f
            speed = null
            distance = 0f
        }

        writeFitFile(tempFile, listOf(rec1))

        val result = LegacyImportEngine.importFromFitResult(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, result.status)

        val speedSamples = insertedSamples.filter { it.containsKey(SensorType.SPEED_mps.name) }
        assertEquals(1, speedSamples.size)
        assertEquals(8.5, (speedSamples[0][SensorType.SPEED_mps.name] as Number).toDouble(), 0.01)
    }

    @Test
    fun testInvalidAltitudeSentinel_filteredOut() = runBlocking {
        val tempFile = File.createTempFile("sentinel_alt", ".fit")
        tempFile.deleteOnExit()

        val rec1 = RecordMesg().apply {
            timestamp = DateTime(Date(1696327200000L))
            enhancedAltitude = 50000.0f // Unrealistic/sentinel altitude (> 10000m)
            altitude = 50000.0f
            distance = 0f
        }

        writeFitFile(tempFile, listOf(rec1))

        val result = LegacyImportEngine.importFromFitResult(mockContext, tempFile)
        assertEquals(LegacyImportEngine.ImportStatus.SUCCESS, result.status)

        val altSamples = insertedSamples.filter { it.containsKey(SensorType.ALTITUDE.name) }
        assertEquals(0, altSamples.size)
    }
}
