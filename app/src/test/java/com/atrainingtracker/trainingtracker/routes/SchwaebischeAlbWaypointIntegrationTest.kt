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
import java.io.File
import java.io.FileInputStream
import kotlin.math.*

/**
 * Real-world integration tests utilizing the 5 GPX test files from
 * `/home/rainer/Downloads/Schwaebische_Alb` to verify waypoint extraction,
 * semantic classification, elevation retention, and polyline projection
 * (REQ-MAP-026, TST-MAP-028.4).
 */
class SchwaebischeAlbWaypointIntegrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockElevationService: ElevationService
    private lateinit var importer: GpxRouteImporter

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

        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        mockElevationService = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver

        importer = GpxRouteImporter(mockContext, mockElevationService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testParseAllSchwaebischeAlbGpxFiles_extractsAll29WaypointsAccurately() = runTest {
        val dir = File("/home/rainer/Downloads/Schwaebische_Alb")
        if (!dir.exists()) {
            println("Directory /home/rainer/Downloads/Schwaebische_Alb not found, skipping real-world file test.")
            return@runTest
        }

        val gpxFiles = dir.listFiles { _, name -> name.endsWith(".gpx") } ?: emptyArray()
        assertTrue("Expected 5 GPX files in Schwäbische Alb dataset", gpxFiles.size >= 5)

        var totalWaypointsExtracted = 0

        for (file in gpxFiles) {
            val uri = mockk<Uri>(relaxed = true)
            every { uri.lastPathSegment } returns file.name
            every { uri.toString() } returns file.absolutePath
            every { mockContentResolver.openInputStream(uri) } answers {
                FileInputStream(file)
            }

            val result = importer.importRouteFromGpx(uri)
            assertTrue("Import should succeed for ${file.name}", result.isSuccess)

            val importData = result.getOrThrow()
            val waypoints = importData.waypoints
            assertFalse("Waypoints should not be empty for ${file.name}", waypoints.isEmpty())
            totalWaypointsExtracted += waypoints.size

            println("File: ${file.name} -> ${waypoints.size} waypoints, ${importData.pathPoints.size} trackpoints")

            // Validate all waypoints have valid coordinates and non-negative projected distance
            for (wpt in waypoints) {
                assertTrue("Latitude out of range", wpt.latLng.latitude in 45.0..55.0)
                assertTrue("Longitude out of range", wpt.latLng.longitude in 5.0..15.0)
                assertTrue("Distance along route must be non-negative", wpt.distanceFromStart >= 0.0)
                assertTrue("Distance along route cannot exceed total distance", wpt.distanceFromStart <= importData.summary.distance + 50.0)
                assertNotNull("Waypoint type must be non-null", wpt.type)
            }

            // Specific validations based on filename
            when {
                file.name.contains("Jusi") -> {
                    assertEquals(9, waypoints.size)
                    val summit = waypoints.find { it.name.contains("Gipfelkreuz") }
                    assertNotNull("Gipfelkreuz must be extracted", summit)
                    assertEquals(WaypointType.POI_SUMMIT, summit?.type)

                    val view = waypoints.find { it.name.contains("Panoramablick") }
                    assertNotNull("Panoramablick must be extracted", view)
                    assertEquals(WaypointType.POI_VIEWPOINT, view?.type)
                }
                file.name.contains("Wental") -> {
                    assertEquals(9, waypoints.size)
                    val bench = waypoints.find { it.name.contains("Sitzgelegenheit") }
                    assertNotNull("Holz-Sitzgelegenheit must be extracted", bench)
                    assertEquals(WaypointType.POI_BENCH, bench?.type)
                }
                file.name.contains("Floriansberg") -> {
                    assertEquals(9, waypoints.size)
                    val view = waypoints.find { it.name.contains("Aussichtspunkt Floriansberg") }
                    assertNotNull("Aussichtspunkt must be extracted", view)
                    assertEquals(WaypointType.POI_VIEWPOINT, view?.type)
                }
                file.name.contains("Nebelhoehle") -> {
                    assertEquals(1, waypoints.size)
                    val shelter = waypoints[0]
                    assertEquals("Start Tour 13", shelter.name)
                    assertEquals("Unterstand", shelter.description)
                    assertEquals(WaypointType.POI_BENCH, shelter.type)
                }
                file.name.contains("Uracher") -> {
                    assertEquals(1, waypoints.size)
                    val startTour = waypoints[0]
                    assertEquals("Start Tour 18", startTour.name)
                }
            }
        }

        assertEquals("Total extracted waypoints across all 5 files must equal 29", 29, totalWaypointsExtracted)
    }
}
