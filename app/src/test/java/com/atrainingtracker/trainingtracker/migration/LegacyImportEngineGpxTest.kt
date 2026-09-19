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
import android.util.Xml
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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying high-fidelity GPX file ingestion, multi-segment lap preservation,
 * Garmin TPX telemetry extraction, distance accumulation, and deduplication (REQ-MIG-030, TST-MIG-027).
 */
class LegacyImportEngineGpxTest {

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

        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers {
            org.kxml2.io.KXmlParser()
        }

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
        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.RUN
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb

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

    @Test
    fun importFromGpx_parsesTrackpointsElevationAndTimestamps() = runBlocking {
        val gpxXml = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="StravaGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <time>2023-08-15T06:12:00Z</time>
    <name>Morning Mountain Run</name>
    <desc>Interval training in the forest</desc>
  </metadata>
  <trk>
    <name>Morning Mountain Run</name>
    <type>running</type>
    <trkseg>
      <trkpt lat="48.137154" lon="11.576124">
        <ele>519.2</ele>
        <time>2023-08-15T06:12:00Z</time>
      </trkpt>
      <trkpt lat="48.137254" lon="11.576224">
        <ele>525.5</ele>
        <time>2023-08-15T06:12:10Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>""".trimIndent()

        val tempFile = File.createTempFile("test_workout", ".gpx").apply {
            writeText(gpxXml)
            deleteOnExit()
        }

        val success = LegacyImportEngine.importFromGpx(mockContext, tempFile)
        assertTrue("Import should succeed", success)

        assertNotNull("Workout summary should be inserted", insertedSummary)
        assertEquals("Morning Mountain Run", insertedSummary?.get(WorkoutSummaries.WORKOUT_NAME))
        assertEquals("2023-08-15 06:12:00", insertedSummary?.get(WorkoutSummaries.TIME_START))

        assertEquals("Should have 2 sample points", 2, insertedSamples.size)
        assertEquals(48.137154, insertedSamples[0][SensorType.LATITUDE.name] as Double, 0.0001)
        assertEquals(11.576124, insertedSamples[0][SensorType.LONGITUDE.name] as Double, 0.0001)
        assertEquals(519.2, insertedSamples[0][SensorType.ALTITUDE.name] as Double, 0.01)

        assertEquals(48.137254, insertedSamples[1][SensorType.LATITUDE.name] as Double, 0.0001)
        assertEquals(11.576224, insertedSamples[1][SensorType.LONGITUDE.name] as Double, 0.0001)
        assertEquals(525.5, insertedSamples[1][SensorType.ALTITUDE.name] as Double, 0.01)

        // Cumulative distance calculated
        val dist1 = insertedSamples[1][SensorType.DISTANCE_m.name] as Double
        assertTrue("Cumulative distance should be > 0", dist1 > 0.0)
    }

    @Test
    fun importFromGpx_parsesGarminTpxExtensionsAndPower() = runBlocking {
        val gpxXml = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="Garmin Edge 1030" version="1.1" xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
  <trk>
    <name>Tempo Ride</name>
    <type>cycling</type>
    <trkseg>
      <trkpt lat="47.999" lon="11.456">
        <ele>600.0</ele>
        <time>2023-09-01T14:00:00Z</time>
        <extensions>
          <gpxtpx:TrackPointExtension>
            <gpxtpx:hr>155</gpxtpx:hr>
            <gpxtpx:cad>92</gpxtpx:cad>
            <gpxtpx:speed>8.5</gpxtpx:speed>
            <gpxtpx:atemp>22.0</gpxtpx:atemp>
          </gpxtpx:TrackPointExtension>
          <power>280</power>
        </extensions>
      </trkpt>
    </trkseg>
  </trk>
</gpx>""".trimIndent()

        val tempFile = File.createTempFile("ride_with_sensors", ".gpx").apply {
            writeText(gpxXml)
            deleteOnExit()
        }

        val success = LegacyImportEngine.importFromGpx(mockContext, tempFile)
        assertTrue(success)

        assertEquals(1, insertedSamples.size)
        val s = insertedSamples[0]
        assertEquals(155, s[SensorType.HR.name])
        assertEquals(92, s[SensorType.CADENCE.name])
        assertEquals(8.5, s[SensorType.SPEED_mps.name] as Double, 0.01)
        assertEquals(22.0, s[SensorType.TEMPERATURE.name] as Double, 0.01)
        assertEquals(280, s[SensorType.POWER.name])
    }

    @Test
    fun importFromGpx_multiSegmentCreatesDistinctLaps() = runBlocking {
        val gpxXml = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="IntervalGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
  <trk>
    <name>Interval Training</name>
    <type>running</type>
    <trkseg>
      <trkpt lat="48.100" lon="11.500">
        <ele>500.0</ele>
        <time>2023-08-20T10:00:00Z</time>
      </trkpt>
      <trkpt lat="48.101" lon="11.501">
        <ele>501.0</ele>
        <time>2023-08-20T10:02:00Z</time>
      </trkpt>
    </trkseg>
    <trkseg>
      <trkpt lat="48.102" lon="11.502">
        <ele>502.0</ele>
        <time>2023-08-20T10:05:00Z</time>
      </trkpt>
      <trkpt lat="48.103" lon="11.503">
        <ele>503.0</ele>
        <time>2023-08-20T10:07:00Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>""".trimIndent()

        val tempFile = File.createTempFile("multi_lap_gpx", ".gpx").apply {
            writeText(gpxXml)
            deleteOnExit()
        }

        val success = LegacyImportEngine.importFromGpx(mockContext, tempFile)
        assertTrue(success)

        assertEquals(4, insertedSamples.size)
        // Segment 1 samples tagged with lap 0
        assertEquals(0L, insertedSamples[0][SensorType.LAP_NR.name])
        assertEquals(0L, insertedSamples[1][SensorType.LAP_NR.name])
        // Segment 2 samples tagged with lap 1
        assertEquals(1L, insertedSamples[2][SensorType.LAP_NR.name])
        assertEquals(1L, insertedSamples[3][SensorType.LAP_NR.name])

        // Verify saveLap was called for both laps in LapsDatabaseManager
        verify(atLeast = 1) { mockLapsDb.saveLap(any(), eq(0L), any(), any(), any(), any()) }
        verify(atLeast = 1) { mockLapsDb.saveLap(any(), eq(1L), any(), any(), any(), any()) }
    }

    @Test
    fun importFromGpx_skipsExistingWorkout() = runBlocking {
        // Mock existing workout in database
        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } answers {
            val cursor = mockk<android.database.Cursor>(relaxed = true)
            every { cursor.count } returns 1
            every { cursor.moveToFirst() } returns true
            cursor
        }

        val gpxXml = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="Test" version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
  <trk><name>Old</name><trkseg><trkpt lat="48.0" lon="11.0"><time>2023-01-01T00:00:00Z</time></trkpt></trkseg></trk>
</gpx>""".trimIndent()

        val tempFile = File.createTempFile("existing_workout", ".gpx").apply {
            writeText(gpxXml)
            deleteOnExit()
        }

        val success = LegacyImportEngine.importFromGpx(mockContext, tempFile)
        assertFalse("Should skip existing workout", success)
    }
}
