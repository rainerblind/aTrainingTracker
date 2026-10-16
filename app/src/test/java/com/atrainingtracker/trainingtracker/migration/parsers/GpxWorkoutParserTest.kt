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
import android.location.Location
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Targeted unit tests for [GpxWorkoutParser] (REQ-MIG-038, TST-MIG-035).
 */
class GpxWorkoutParserTest {

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

        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val results = args[4] as FloatArray
            results[0] = 500f
        }

        mockContext = mockk(relaxed = true)
        mockSportTypeDb = mockk(relaxed = true)
        every { mockSportTypeDb.getSportTypeIdFromTcxName("Cycling") } returns 2L
        every { mockSportTypeDb.getBSportType(2L) } returns BSportType.BIKE
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
    fun testParseValidGpx() {
        val gpxContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1"
                 xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
              <trk>
                <name>Afternoon Ride</name>
                <type>Cycling</type>
                <trkseg>
                  <trkpt lat="48.137154" lon="11.576124">
                    <ele>520.0</ele>
                    <time>2026-10-15T14:00:00Z</time>
                    <extensions>
                      <gpxtpx:TrackPointExtension>
                        <gpxtpx:hr>130</gpxtpx:hr>
                        <gpxtpx:cad>85</gpxtpx:cad>
                      </gpxtpx:TrackPointExtension>
                    </extensions>
                  </trkpt>
                  <trkpt lat="48.140000" lon="11.580000">
                    <ele>540.0</ele>
                    <time>2026-10-15T14:05:00Z</time>
                    <extensions>
                      <gpxtpx:TrackPointExtension>
                        <gpxtpx:hr>145</gpxtpx:hr>
                        <gpxtpx:cad>90</gpxtpx:cad>
                      </gpxtpx:TrackPointExtension>
                    </extensions>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val tempFile = File.createTempFile("test_ride", ".gpx")
        try {
            tempFile.writeText(gpxContent)
            val result = GpxWorkoutParser.parse(tempFile, mockContext)
            assertTrue("GpxWorkoutParser should succeed", result.isSuccess)
            val workout = result.getOrThrow()

            assertEquals("Afternoon Ride", workout.workoutName)
            assertEquals("Cycling", workout.sportName)
            assertEquals(BSportType.BIKE, workout.bSportType)
            assertEquals(2L, workout.resolvedSportId)
            assertEquals("2026-10-15 14:00:00", workout.firstTime)
            assertEquals("2026-10-15 14:05:00", workout.lastTime)
            assertEquals(2, workout.points.size)
            assertEquals(2, workout.altitudes.size)
            assertEquals(520.0, workout.minAltVal, 0.001)
            assertEquals(540.0, workout.maxAltVal, 0.001)
            assertEquals(1, workout.parsedLaps.size)
            assertEquals(500.0, workout.parsedLaps[0].distanceMeters, 0.001)
            assertTrue(workout.foundSensors.contains(SensorType.HR))
            assertTrue(workout.foundSensors.contains(SensorType.CADENCE))
            assertTrue(workout.foundSensors.contains(SensorType.ALTITUDE))
            assertTrue(workout.foundSensors.contains(SensorType.DISTANCE_m))
            assertEquals(2, workout.bufferedSamples.size)
        } finally {
            tempFile.delete()
        }
    }
}
