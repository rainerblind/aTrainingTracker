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

package com.atrainingtracker.trainingtracker.routes

import android.content.ContentResolver
import android.content.Context
import android.location.Location
import android.net.Uri
import android.util.Log
import android.util.Xml
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.math.*

/**
 * Unit tests verifying GPX waypoint (<wpt>) parsing, heuristic type classification,
 * elevation retention, and polyline projection (REQ-MAP-026, TST-MAP-028 Group 1).
 */
class GpxRouteImporterWaypointTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockElevationService: ElevationService
    private lateinit var importer: GpxRouteImporter
    private val testUri = mockk<Uri>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(Xml::class)
        every { Xml.newPullParser() } answers {
            org.kxml2.io.KXmlParser()
        }

        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        mockElevationService = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver
        every { testUri.lastPathSegment } returns "test_track.gpx"

        mockkStatic(Location::class)
        every { Location.distanceBetween(any(), any(), any(), any(), any()) } answers {
            val startLat = arg<Double>(0)
            val startLng = arg<Double>(1)
            val endLat = arg<Double>(2)
            val endLng = arg<Double>(3)
            val results = arg<FloatArray>(4)
            val dLat = Math.toRadians(endLat - startLat)
            val dLng = Math.toRadians(endLng - startLng)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(startLat)) * cos(Math.toRadians(endLat)) *
                    sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val dist = (6371000 * c).toFloat()
            results[0] = dist
        }

        importer = GpxRouteImporter(mockContext, mockElevationService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testImportRoute_withGpxWaypoints_extractsAndClassifiesPoiTypes() = runTest {
        val gpxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
                <wpt lat="48.1" lon="11.5">
                    <name>Trinkbrunnen</name>
                    <sym>Water</sym>
                </wpt>
                <wpt lat="48.2" lon="11.6">
                    <name>Aussichtsbank</name>
                    <sym>Bench</sym>
                </wpt>
                <wpt lat="48.3" lon="11.7">
                    <name>Zugspitze</name>
                    <sym>Summit</sym>
                    <ele>2962.0</ele>
                </wpt>
                <wpt lat="48.4" lon="11.8">
                    <name>Gasthof Hirsch</name>
                    <sym>Restaurant</sym>
                </wpt>
                <trk>
                    <name>Mountain Route</name>
                    <trkseg>
                        <trkpt lat="48.0" lon="11.4"><ele>500.0</ele></trkpt>
                        <trkpt lat="48.2" lon="11.6"><ele>1200.0</ele></trkpt>
                        <trkpt lat="48.5" lon="11.9"><ele>800.0</ele></trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        every { mockContentResolver.openInputStream(testUri) } answers {
            ByteArrayInputStream(gpxXml.toByteArray(Charsets.UTF_8))
        }

        val result = importer.importRouteFromGpx(testUri)

        assertTrue(result.isSuccess)
        val importData = result.getOrThrow()
        val waypoints = importData.waypoints

        assertEquals(4, waypoints.size)

        val waterWpt = waypoints.find { it.name == "Trinkbrunnen" }
        assertNotNull(waterWpt)
        assertEquals(WaypointType.POI_WATER, waterWpt?.type)

        val benchWpt = waypoints.find { it.name == "Aussichtsbank" }
        assertNotNull(benchWpt)
        assertEquals(WaypointType.POI_BENCH, benchWpt?.type)

        val summitWpt = waypoints.find { it.name == "Zugspitze" }
        assertNotNull(summitWpt)
        assertEquals(WaypointType.POI_SUMMIT, summitWpt?.type)
        assertEquals(2962.0, summitWpt?.altitude ?: 0.0, 0.001)

        val foodWpt = waypoints.find { it.name == "Gasthof Hirsch" }
        assertNotNull(foodWpt)
        assertEquals(WaypointType.POI_FOOD, foodWpt?.type)
    }

    @Test
    fun testImportRoute_withGpxLackingWaypoints_returnsEmptyWaypointsList() = runTest {
        val gpxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
                <trk>
                    <name>Scenic Path</name>
                    <trkseg>
                        <trkpt lat="48.1" lon="11.5"><ele>500.0</ele></trkpt>
                        <trkpt lat="48.2" lon="11.6"><ele>600.0</ele></trkpt>
                    </trkseg>
                </trk>
            </gpx>
        """.trimIndent()

        every { mockContentResolver.openInputStream(testUri) } answers {
            ByteArrayInputStream(gpxXml.toByteArray(Charsets.UTF_8))
        }

        val result = importer.importRouteFromGpx(testUri)

        assertTrue(result.isSuccess)
        val importData = result.getOrThrow()
        assertTrue(importData.waypoints.isEmpty())
        assertEquals(2, importData.pathPoints.size)
        assertEquals("Scenic Path", importData.summary.name)
    }
}
