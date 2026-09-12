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
import android.util.Log
import android.util.Xml
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying TCX Lap info (Name and Description) parsing, bracket extraction,
 * structured extensions, character unescaping, and database persistence (REQ-DAT-012, TST-DAT-006, ATT-892).
 */
class LegacyImportEngineLapTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase

    private val contentValueStores = java.util.Collections.synchronizedMap(java.util.IdentityHashMap<ContentValues, MutableMap<String, Any?>>())

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

        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers {
            org.kxml2.io.KXmlParser()
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

        val mockCursor = mockk<android.database.Cursor>(relaxed = true)
        every { mockCursor.count } returns 0
        every { mockCursor.moveToFirst() } returns false
        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } returns mockCursor
        every { mockSqlDb.insert(WorkoutSummaries.TABLE, any(), any()) } returns 101L
        every { mockSqlDb.insert(match { it != WorkoutSummaries.TABLE }, any(), any()) } returns 1L
        every { mockSqlDb.update(WorkoutSummaries.TABLE, any(), any(), any()) } returns 1
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testImportTcxWithBracketLapNotes() = runBlocking {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-09-12T10:00:00Z</Id>
                  <Lap StartTime="2026-09-12T10:00:00Z">
                    <TotalTimeSeconds>300.0</TotalTimeSeconds>
                    <DistanceMeters>1000.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-12T10:00:00Z</Time>
                        <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-12T10:05:00Z</Time>
                        <Position><LatitudeDegrees>48.005</LatitudeDegrees><LongitudeDegrees>11.005</LongitudeDegrees></Position>
                        <DistanceMeters>1000.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                    <Notes>[Warm-up] 10m Zone 1 easy</Notes>
                  </Lap>
                  <Lap StartTime="2026-09-12T10:05:00Z">
                    <TotalTimeSeconds>600.0</TotalTimeSeconds>
                    <DistanceMeters>2500.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-12T10:05:00Z</Time>
                        <Position><LatitudeDegrees>48.005</LatitudeDegrees><LongitudeDegrees>11.005</LongitudeDegrees></Position>
                        <DistanceMeters>1000.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-12T10:15:00Z</Time>
                        <Position><LatitudeDegrees>48.015</LatitudeDegrees><LongitudeDegrees>11.015</LongitudeDegrees></Position>
                        <DistanceMeters>3500.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                    <Notes>[Interval] 4x1000m tempo</Notes>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_lap_notes_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            // Verify laps saved with parsed names and descriptions
            verify(exactly = 1) {
                mockLapsDb.saveLap(
                    101L,
                    0L,
                    "2026-09-12 10:00:00",
                    300,
                    1000.0,
                    any(),
                    "Warm-up",
                    "10m Zone 1 easy"
                )
            }
            verify(exactly = 1) {
                mockLapsDb.saveLap(
                    101L,
                    1L,
                    "2026-09-12 10:05:00",
                    600,
                    2500.0,
                    any(),
                    "Interval",
                    "4x1000m tempo"
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportTcxWithBracketNameOnly() = runBlocking {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-09-12T10:00:00Z</Id>
                  <Lap StartTime="2026-09-12T10:00:00Z">
                    <TotalTimeSeconds>180.0</TotalTimeSeconds>
                    <DistanceMeters>500.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-12T10:00:00Z</Time>
                        <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-12T10:03:00Z</Time>
                        <Position><LatitudeDegrees>48.002</LatitudeDegrees><LongitudeDegrees>11.002</LongitudeDegrees></Position>
                        <DistanceMeters>500.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                    <Notes>[Recovery]</Notes>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_lap_name_only_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            verify(exactly = 1) {
                mockLapsDb.saveLap(
                    101L,
                    0L,
                    "2026-09-12 10:00:00",
                    180,
                    500.0,
                    any(),
                    "Recovery",
                    null
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportTcxWithStructuredAttExtensions() = runBlocking {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-09-12T10:00:00Z</Id>
                  <Lap StartTime="2026-09-12T10:00:00Z">
                    <TotalTimeSeconds>45.0</TotalTimeSeconds>
                    <DistanceMeters>200.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-12T10:00:00Z</Time>
                        <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-12T10:00:45Z</Time>
                        <Position><LatitudeDegrees>48.001</LatitudeDegrees><LongitudeDegrees>11.001</LongitudeDegrees></Position>
                        <DistanceMeters>200.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                    <Notes>[Sprint 200m] Max effort</Notes>
                    <Extensions>
                      <att:LapExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
                        <att:Name>Sprint 200m</att:Name>
                        <att:Description>Max effort</att:Description>
                      </att:LapExtension>
                    </Extensions>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_lap_structured_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            verify(exactly = 1) {
                mockLapsDb.saveLap(
                    101L,
                    0L,
                    "2026-09-12 10:00:00",
                    45,
                    200.0,
                    any(),
                    "Sprint 200m",
                    "Max effort"
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportTcxWithMultilineLapNotes() = runBlocking {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-09-12T10:00:00Z</Id>
                  <Lap StartTime="2026-09-12T10:00:00Z">
                    <TotalTimeSeconds>500.0</TotalTimeSeconds>
                    <DistanceMeters>1200.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-12T10:00:00Z</Time>
                        <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-12T10:08:20Z</Time>
                        <Position><LatitudeDegrees>48.005</LatitudeDegrees><LongitudeDegrees>11.005</LongitudeDegrees></Position>
                        <DistanceMeters>1200.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                    <Notes>Hill Climb
                    Steep 8% grade
                    Maintain cadence</Notes>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_lap_multiline_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            verify(exactly = 1) {
                mockLapsDb.saveLap(
                    101L,
                    0L,
                    "2026-09-12 10:00:00",
                    500,
                    1200.0,
                    any(),
                    "Hill Climb",
                    match { it.contains("Steep 8% grade") && it.contains("Maintain cadence") }
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportTcxWithEscapedXmlCharacters() = runBlocking {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-09-12T10:00:00Z</Id>
                  <Lap StartTime="2026-09-12T10:00:00Z">
                    <TotalTimeSeconds>300.0</TotalTimeSeconds>
                    <DistanceMeters>1000.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-12T10:00:00Z</Time>
                        <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-12T10:05:00Z</Time>
                        <Position><LatitudeDegrees>48.005</LatitudeDegrees><LongitudeDegrees>11.005</LongitudeDegrees></Position>
                        <DistanceMeters>1000.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                    <Notes>[Interval &amp; Hill] Zone 4 &lt;hard&gt; &quot;effort&quot; &apos;test&apos;</Notes>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_lap_escaping_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            verify(exactly = 1) {
                mockLapsDb.saveLap(
                    101L,
                    0L,
                    "2026-09-12 10:00:00",
                    300,
                    1000.0,
                    any(),
                    "Interval & Hill",
                    "Zone 4 <hard> \"effort\" 'test'"
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportTcxActivityNotesDoesNotPolluteLaps() = runBlocking {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-09-12T10:00:00Z</Id>
                  <Lap StartTime="2026-09-12T10:00:00Z">
                    <TotalTimeSeconds>300.0</TotalTimeSeconds>
                    <DistanceMeters>1000.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-12T10:00:00Z</Time>
                        <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-12T10:05:00Z</Time>
                        <Position><LatitudeDegrees>48.005</LatitudeDegrees><LongitudeDegrees>11.005</LongitudeDegrees></Position>
                        <DistanceMeters>1000.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                  </Lap>
                  <Notes>Great overall morning session</Notes>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_activity_notes_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            // Laps must NOT have the activity notes; 6-parameter saveLap called
            verify(exactly = 1) {
                mockLapsDb.saveLap(
                    101L,
                    0L,
                    "2026-09-12 10:00:00",
                    300,
                    1000.0,
                    any()
                )
            }
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testImportLegacyTcxWithoutNotes() = runBlocking {
        val tcx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-09-12T10:00:00Z</Id>
                  <Lap StartTime="2026-09-12T10:00:00Z">
                    <TotalTimeSeconds>300.0</TotalTimeSeconds>
                    <DistanceMeters>1000.0</DistanceMeters>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-12T10:00:00Z</Time>
                        <Position><LatitudeDegrees>48.0</LatitudeDegrees><LongitudeDegrees>11.0</LongitudeDegrees></Position>
                        <DistanceMeters>0.0</DistanceMeters>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-12T10:05:00Z</Time>
                        <Position><LatitudeDegrees>48.005</LatitudeDegrees><LongitudeDegrees>11.005</LongitudeDegrees></Position>
                        <DistanceMeters>1000.0</DistanceMeters>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_legacy_clean_", ".tcx")
        tempFile.writeText(tcx)

        try {
            val result = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("Import must succeed", result)

            verify(exactly = 1) {
                mockLapsDb.saveLap(
                    101L,
                    0L,
                    "2026-09-12 10:00:00",
                    300,
                    1000.0,
                    any()
                )
            }
        } finally {
            tempFile.delete()
        }
    }
}
