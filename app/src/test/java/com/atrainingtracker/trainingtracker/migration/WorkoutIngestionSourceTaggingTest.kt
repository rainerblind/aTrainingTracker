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

package com.atrainingtracker.trainingtracker.migration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.location.Location
import android.util.Log
import android.util.Xml
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying that TCX and GPX imports tag workout records with their respective WorkoutSource (REQ-DAT-017, TST-DAT-012.3).
 */
class WorkoutIngestionSourceTaggingTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase

    private val contentValueStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())
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
        every { Log.e(any<String>(), any<String>()) } answers { println("LOG.E: ${args[1]}"); 0 }
        every { Log.e(any<String>(), any<String>(), any()) } answers { 
            println("LOG.E: ${args[1]}")
            (args[2] as? Throwable)?.printStackTrace()
            0 
        }
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
            results[0] = 50.0f
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
        every { mockSportTypeDb.getBSportType(any<Long>()) } returns BSportType.RUN
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb

        contentValueStores.clear()
        insertedSummary = null

        mockkConstructor(ContentValues::class)
        every { constructedWith<ContentValues>().put(any<String>(), any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<String>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Long>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Long>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Int>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Int>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Double>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Double>()
        }
        every { constructedWith<ContentValues>().put(any<String>(), any<Float>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores.computeIfAbsent(cv) { mutableMapOf() }
            map[firstArg<String>()] = secondArg<Float>()
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
                1L
            }
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testImportGpx_tagsSourceAsGpx() = runBlocking {
        val gpxXml = """<?xml version="1.0" encoding="UTF-8"?>
<gpx creator="TestGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
  <trk>
    <name>Test GPX Ingestion</name>
    <trkseg>
      <trkpt lat="48.1371" lon="11.5761">
        <ele>520.0</ele>
        <time>2026-10-03T10:00:00Z</time>
      </trkpt>
      <trkpt lat="48.1372" lon="11.5762">
        <ele>521.0</ele>
        <time>2026-10-03T10:00:10Z</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>""".trimIndent()

        val tempFile = File.createTempFile("test_ingest", ".gpx").apply {
            writeText(gpxXml)
            deleteOnExit()
        }

        val success = LegacyImportEngine.importFromGpx(mockContext, tempFile)
        assertTrue("GPX import should succeed", success)
        assertNotNull("Workout summary must be inserted", insertedSummary)
        assertEquals("GPX", insertedSummary?.get(WorkoutSummaries.SOURCE))
    }

    @Test
    fun testImportTcx_tagsSourceAsTcx() = runBlocking {
        val tcxXml = """<?xml version="1.0" encoding="UTF-8"?>
<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
  <Activities>
    <Activity Sport="Running">
      <Id>2026-10-03T10:00:00Z</Id>
      <Lap StartTime="2026-10-03T10:00:00Z">
        <TotalTimeSeconds>60.0</TotalTimeSeconds>
        <DistanceMeters>200.0</DistanceMeters>
        <Calories>10</Calories>
        <Intensity>Active</Intensity>
        <TriggerMethod>Manual</TriggerMethod>
        <Track>
          <Trackpoint>
            <Time>2026-10-03T10:00:00Z</Time>
            <Position>
              <LatitudeDegrees>48.1371</LatitudeDegrees>
              <LongitudeDegrees>11.5761</LongitudeDegrees>
            </Position>
            <AltitudeMeters>520.0</AltitudeMeters>
            <DistanceMeters>0.0</DistanceMeters>
          </Trackpoint>
          <Trackpoint>
            <Time>2026-10-03T10:01:00Z</Time>
            <Position>
              <LatitudeDegrees>48.1380</LatitudeDegrees>
              <LongitudeDegrees>11.5770</LongitudeDegrees>
            </Position>
            <AltitudeMeters>522.0</AltitudeMeters>
            <DistanceMeters>200.0</DistanceMeters>
          </Trackpoint>
        </Track>
      </Lap>
    </Activity>
  </Activities>
</TrainingCenterDatabase>""".trimIndent()

        val tempFile = File.createTempFile("test_ingest", ".tcx").apply {
            writeText(tcxXml)
            deleteOnExit()
        }

        val success = LegacyImportEngine.importFromTcx(mockContext, tempFile)
        assertTrue("TCX import should succeed", success)
        assertNotNull("Workout summary must be inserted", insertedSummary)
        assertEquals("TCX", insertedSummary?.get(WorkoutSummaries.SOURCE))
    }
}
