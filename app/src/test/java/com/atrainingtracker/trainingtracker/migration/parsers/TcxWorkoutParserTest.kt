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

package com.atrainingtracker.trainingtracker.migration.parsers

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.util.Xml
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager
import com.atrainingtracker.banalservice.sensor.SensorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Targeted unit tests for [TcxWorkoutParser] (REQ-MIG-038, TST-MIG-035).
 */
class TcxWorkoutParserTest {

    private lateinit var mockContext: Context
    private lateinit var mockSportTypeDb: SportTypeDatabaseManager
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

        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers {
            org.kxml2.io.KXmlParser()
        }

        mockContext = mockk(relaxed = true)
        mockSportTypeDb = mockk(relaxed = true)
        every { mockSportTypeDb.getSportTypeIdFromTcxName("Running") } returns 1L
        every { mockSportTypeDb.getBSportType(1L) } returns BSportType.RUN
        mockkStatic(SportTypeDatabaseManager::class)
        every { SportTypeDatabaseManager.getInstance(any()) } returns mockSportTypeDb

        contentValueStores.clear()
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
        every { constructedWith<ContentValues>().containsKey(any<String>()) } answers {
            val cv = getRealInstance()
            val map = contentValueStores[cv]
            map?.containsKey(firstArg<String>()) ?: false
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testParseValidTcx() {
        val tcxContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">
              <Activities>
                <Activity Sport="Running">
                  <Id>2026-10-15T08:00:00Z</Id>
                  <Lap StartTime="2026-10-15T08:00:00Z">
                    <TotalTimeSeconds>300.0</TotalTimeSeconds>
                    <DistanceMeters>1000.0</DistanceMeters>
                    <Calories>75</Calories>
                    <AverageHeartRateBpm><Value>145</Value></AverageHeartRateBpm>
                    <Track>
                      <Trackpoint>
                        <Time>2026-10-15T08:00:00Z</Time>
                        <Position>
                          <LatitudeDegrees>48.137154</LatitudeDegrees>
                          <LongitudeDegrees>11.576124</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>520.0</AltitudeMeters>
                        <DistanceMeters>0.0</DistanceMeters>
                        <HeartRateBpm><Value>140</Value></HeartRateBpm>
                      </Trackpoint>
                      <Trackpoint>
                        <Time>2026-10-15T08:05:00Z</Time>
                        <Position>
                          <LatitudeDegrees>48.140000</LatitudeDegrees>
                          <LongitudeDegrees>11.580000</LongitudeDegrees>
                        </Position>
                        <AltitudeMeters>535.0</AltitudeMeters>
                        <DistanceMeters>1000.0</DistanceMeters>
                        <HeartRateBpm><Value>150</Value></HeartRateBpm>
                      </Trackpoint>
                    </Track>
                  </Lap>
                </Activity>
              </Activities>
            </TrainingCenterDatabase>
        """.trimIndent()

        val tempFile = File.createTempFile("test_run", ".tcx")
        try {
            tempFile.writeText(tcxContent)
            val result = TcxWorkoutParser.parse(tempFile, mockContext)
            assertTrue("TcxWorkoutParser should succeed", result.isSuccess)
            val workout = result.getOrThrow()

            assertEquals("Running", workout.sportName)
            assertEquals(BSportType.RUN, workout.bSportType)
            assertEquals(1L, workout.resolvedSportId)
            assertEquals("2026-10-15 08:00:00", workout.firstTime)
            assertEquals("2026-10-15 08:05:00", workout.lastTime)
            assertEquals(2, workout.points.size)
            assertEquals(2, workout.altitudes.size)
            assertEquals(520.0, workout.minAltVal, 0.001)
            assertEquals(535.0, workout.maxAltVal, 0.001)
            assertEquals(1, workout.parsedLaps.size)
            assertEquals(1000.0, workout.parsedLaps[0].distanceMeters, 0.001)
            assertEquals(75, workout.parsedLaps[0].calories)
            assertEquals(145, workout.parsedLaps[0].avgHeartRate)
            assertTrue(workout.foundSensors.contains(SensorType.HR))
            assertTrue(workout.foundSensors.contains(SensorType.ALTITUDE))
            assertEquals(2, workout.bufferedSamples.size)
        } finally {
            tempFile.delete()
        }
    }
}
