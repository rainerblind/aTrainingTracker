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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

/**
 * Unit tests verifying high-fidelity TCX telemetry parsing, multi-lap preservation,
 * duration calculation, calories, and metadata persistence (REQ-MIG-024, TST-MIG-021, ATT-617).
 */
class TcxHighFidelityImportTest {

    private lateinit var mockContext: Context
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
    private lateinit var mockSqlDb: SQLiteDatabase

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
    }

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

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testParsedLapDataModel() {
        val lap = ParsedLap(
            lapNr = 1L,
            startTime = "2026-09-04 07:10:00",
            totalTimeSeconds = 450.0,
            distanceMeters = 1500.0,
            maxSpeed = 4.8,
            calories = 110,
            avgHeartRate = 155
        )

        assertEquals(1L, lap.lapNr)
        assertEquals("2026-09-04 07:10:00", lap.startTime)
        assertEquals(450.0, lap.totalTimeSeconds, 0.001)
        assertEquals(1500.0, lap.distanceMeters, 0.001)
        assertEquals(4.8, lap.maxSpeed!!, 0.001)
        assertEquals(110, lap.calories)
        assertEquals(155, lap.avgHeartRate)
    }

    @Test
    fun testMultiLapTcxImportPreservesLapsAndTPXTelemetry() = runBlocking {
        val tcxContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
                xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-09-04T07:00:00Z</Id>
                  <Lap StartTime="2026-09-04T07:00:00Z">
                    <TotalTimeSeconds>300.0</TotalTimeSeconds>
                    <DistanceMeters>1000.0</DistanceMeters>
                    <MaximumSpeed>4.5</MaximumSpeed>
                    <Calories>75</Calories>
                    <AverageHeartRateBpm><Value>140</Value></AverageHeartRateBpm>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-04T07:00:00Z</Time>
                        <Position>
                          <LatitudeDegrees>48.0</LatitudeDegrees>
                          <LongitudeDegrees>11.0</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>500.0</AltitudeMeters>
                        <DistanceMeters>0.0</DistanceMeters>
                        <HeartRateBpm><Value>135</Value></HeartRateBpm>
                        <Extensions>
                          <ns3:TPX>
                            <ns3:Speed>3.33</ns3:Speed>
                            <ns3:RunCadence>85</ns3:RunCadence>
                          </ns3:TPX>
                        </Extensions>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-04T07:05:00Z</Time>
                        <Position>
                          <LatitudeDegrees>48.005</LatitudeDegrees>
                          <LongitudeDegrees>11.005</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>505.0</AltitudeMeters>
                        <DistanceMeters>1000.0</DistanceMeters>
                        <HeartRateBpm><Value>145</Value></HeartRateBpm>
                        <Extensions>
                          <ns3:TPX>
                            <ns3:Speed>3.40</ns3:Speed>
                            <ns3:RunCadence>86</ns3:RunCadence>
                          </ns3:TPX>
                        </Extensions>
                      </Trackpoint>
                    </Track>
                  </Lap>
                  <Lap StartTime="2026-09-04T07:10:00Z">
                    <TotalTimeSeconds>450.0</TotalTimeSeconds>
                    <DistanceMeters>1500.0</DistanceMeters>
                    <MaximumSpeed>5.0</MaximumSpeed>
                    <Calories>110</Calories>
                    <AverageHeartRateBpm><Value>155</Value></AverageHeartRateBpm>
                    <Track>
                      <Trackpoint>
                        <Time>2026-09-04T07:10:00Z</Time>
                        <Position>
                          <LatitudeDegrees>48.006</LatitudeDegrees>
                          <LongitudeDegrees>11.006</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>506.0</AltitudeMeters>
                        <DistanceMeters>1000.0</DistanceMeters>
                        <HeartRateBpm><Value>150</Value></HeartRateBpm>
                        <Extensions>
                          <ns3:TPX>
                            <ns3:Speed>3.55</ns3:Speed>
                            <ns3:RunCadence>88</ns3:RunCadence>
                          </ns3:TPX>
                        </Extensions>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-09-04T07:17:30Z</Time>
                        <Position>
                          <LatitudeDegrees>48.015</LatitudeDegrees>
                          <LongitudeDegrees>11.015</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>510.0</AltitudeMeters>
                        <DistanceMeters>2500.0</DistanceMeters>
                        <HeartRateBpm><Value>160</Value></HeartRateBpm>
                        <Extensions>
                          <ns3:TPX>
                            <ns3:Speed>3.60</ns3:Speed>
                            <ns3:RunCadence>90</ns3:RunCadence>
                          </ns3:TPX>
                        </Extensions>
                      </Trackpoint>
                    </Track>
                  </Lap>
                  <Notes>Morning progression intervals</Notes>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_multi_lap_", ".tcx")
        tempFile.writeText(tcxContent)

        val mockCursor = mockk<android.database.Cursor>(relaxed = true)
        every { mockCursor.count } returns 0
        every { mockCursor.moveToFirst() } returns false
        every { mockSqlDb.query(WorkoutSummaries.TABLE, any(), any(), any(), any(), any(), any()) } returns mockCursor

        val insertedSamples = mutableListOf<ContentValues>()
        every { mockSqlDb.insert(WorkoutSummaries.TABLE, any(), any()) } returns 101L
        every { mockSqlDb.insert(match { it != WorkoutSummaries.TABLE }, any(), capture(insertedSamples)) } returns 1L

        val updatedSummarySlot = slot<ContentValues>()
        every {
            mockSqlDb.update(
                WorkoutSummaries.TABLE,
                capture(updatedSummarySlot),
                any(),
                any()
            )
        } returns 1

        try {
            val success = LegacyImportEngine.importFromTcx(mockContext, tempFile)
            assertTrue("TCX Import must succeed", success)

            // 1. Verify multi-lap preservation in LapsDatabaseManager
            verify(exactly = 1) {
                mockLapsDb.deleteWorkout(101L)
            }
            verify(exactly = 1) {
                mockLapsDb.saveLap(101L, 0L, "2026-09-04 07:00:00", 300, 1000.0, any())
            }
            verify(exactly = 1) {
                mockLapsDb.saveLap(101L, 1L, "2026-09-04 07:10:00", 450, 1500.0, any())
            }

            // 2. Verify trackpoint samples include LAP_NR, SPEED_mps, and CADENCE from TPX
            assertTrue("Samples must be inserted", insertedSamples.isNotEmpty())
            val lap0Sample = insertedSamples.firstOrNull { it.getAsLong(SensorType.LAP_NR.name) == 0L }
            val lap1Sample = insertedSamples.firstOrNull { it.getAsLong(SensorType.LAP_NR.name) == 1L }
            assertNotNull("Must contain sample for lap 0", lap0Sample)
            assertNotNull("Must contain sample for lap 1", lap1Sample)
            assertNotNull("Sample must contain SPEED_mps", lap0Sample?.getAsDouble(SensorType.SPEED_mps.name))
            assertNotNull("Sample must contain CADENCE from RunCadence", lap0Sample?.getAsInteger(SensorType.CADENCE.name))

            // 3. Verify WorkoutSummaries contains active time, total elapsed time, calories, and notes
            val summaryValues = updatedSummarySlot.captured
            assertEquals(750, summaryValues.getAsInteger(WorkoutSummaries.TIME_ACTIVE_s)) // 300 + 450
            assertEquals(1050, summaryValues.getAsInteger(WorkoutSummaries.TIME_TOTAL_s)) // 07:00:00 to 07:17:30 = 1050s
            assertEquals(185, summaryValues.getAsInteger(WorkoutSummaries.CALORIES)) // 75 + 110
            assertEquals("Morning progression intervals", summaryValues.getAsString(WorkoutSummaries.DESCRIPTION))
            assertEquals(2, summaryValues.getAsInteger(WorkoutSummaries.LAPS))

            val gcData = summaryValues.getAsString(WorkoutSummaries.GC_DATA)
            assertTrue("GCData must reflect speed stream (S)", gcData.contains("S"))
            assertTrue("GCData must reflect cadence stream (C)", gcData.contains("C"))
        } finally {
            tempFile.delete()
        }
    }
}
