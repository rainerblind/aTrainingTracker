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
import java.util.TimeZone
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Targeted unit tests for [GpxWorkoutParser] (REQ-MIG-038, TST-MIG-035, REQ-MIG-055, TST-MIG-055).
 */
// Linked test specs: TST-MIG-035, TST-MIG-055, TST-MIG-055.1, TST-MIG-055.2
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

    /**
     * TST-MIG-055.2: Verifies DST spring-forward/fall-back boundaries under Europe/Berlin,
     * numeric offsets (+02:00, -05:00), fractional seconds (.SSS), bare local ISO, lap duration,
     * and malformed raw-string fallback without +1h DST shift.
     */
    @Test
    fun testParseDstBoundariesOffsetsAndFractionalSeconds_TST_MIG_055_2() {
        val originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        try {
            val gpxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk>
                    <name>DST &amp; Offsets</name>
                    <type>Cycling</type>
                    <trkseg>
                      <trkpt lat="48.10" lon="11.50"><time>2026-03-29T02:30:00Z</time></trkpt>
                      <trkpt lat="48.11" lon="11.51"><time>2026-03-29T02:35:00.789Z</time></trkpt>
                    </trkseg>
                  </trk>
                </gpx>
            """.trimIndent()
            val result = GpxWorkoutParser.parse(gpxContent.byteInputStream(), mockContext)
            assertTrue(result.isSuccess)
            val workout = result.getOrThrow()
            assertEquals("2026-03-29 02:30:00", workout.firstTime)
            assertEquals("2026-03-29 02:35:00", workout.lastTime)
            assertEquals(300.0, workout.parsedLaps[0].totalTimeSeconds, 0.001)

            val offsetFallBackGpx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk>
                    <trkseg>
                      <trkpt lat="48.10" lon="11.50"><time>2026-10-25T04:30:00+02:00</time></trkpt>
                      <trkpt lat="48.11" lon="11.51"><time>2026-10-25T02:30:00</time></trkpt>
                      <trkpt lat="48.12" lon="11.52"><time>2026-10-25T00:00:00-05:00</time></trkpt>
                    </trkseg>
                  </trk>
                </gpx>
            """.trimIndent()
            val offsetWorkout = GpxWorkoutParser.parse(offsetFallBackGpx.byteInputStream(), mockContext).getOrThrow()
            assertEquals("2026-10-25 02:30:00", offsetWorkout.firstTime)
            assertEquals("2026-10-25 05:00:00", offsetWorkout.lastTime)
            assertEquals(9000.0, offsetWorkout.parsedLaps[0].totalTimeSeconds, 0.001)

            val malformedGpx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
                  <trk><trkseg><trkpt lat="48.10" lon="11.50"><time>not-a-timestamp</time></trkpt></trkseg></trk>
                </gpx>
            """.trimIndent()
            val malformedWorkout = GpxWorkoutParser.parse(malformedGpx.byteInputStream(), mockContext).getOrThrow()
            assertEquals("not-a-timestamp", malformedWorkout.firstTime)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    /**
     * TST-MIG-055.1: Concurrent multi-thread parsing across 4 worker threads produces
     * deterministic timestamps without SimpleDateFormat race corruption.
     */
    @Test
    fun testConcurrentMultiThreadGpxParsing_isDeterministic_TST_MIG_055_1() {
        val threadCount = 4
        val iterationsPerThread = 25
        val pool = Executors.newFixedThreadPool(threadCount)
        val ready = CountDownLatch(threadCount)
        val go = CountDownLatch(1)
        try {
            val futures = (0 until threadCount).map { workerIdx ->
                val day = 10 + workerIdx
                val xml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
                      <trk>
                        <trkseg>
                          <trkpt lat="48.1" lon="11.5"><time>2026-05-${day}T06:15:00Z</time></trkpt>
                          <trkpt lat="48.2" lon="11.6"><time>2026-05-${day}T06:25:00Z</time></trkpt>
                        </trkseg>
                      </trk>
                    </gpx>
                """.trimIndent()
                pool.submit(Callable {
                    ready.countDown()
                    assertTrue(go.await(5, TimeUnit.SECONDS))
                    repeat(iterationsPerThread) {
                        val workout = GpxWorkoutParser.parse(xml.byteInputStream(), mockContext).getOrThrow()
                        assertEquals("2026-05-$day 06:15:00", workout.firstTime)
                        assertEquals("2026-05-$day 06:25:00", workout.lastTime)
                        assertEquals(600.0, workout.parsedLaps[0].totalTimeSeconds, 0.001)
                    }
                })
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            futures.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
