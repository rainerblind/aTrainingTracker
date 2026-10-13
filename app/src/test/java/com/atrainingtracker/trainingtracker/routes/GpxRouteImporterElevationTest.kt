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
import com.atrainingtracker.trainingtracker.elevation.ElevationResult
import com.atrainingtracker.trainingtracker.elevation.ElevationService
import com.google.android.gms.maps.model.LatLng
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Unit tests for GpxRouteImporter focusing on automatic DEM elevation enrichment,
 * coordinate chunking to prevent HTTP 414 URI Too Long, metrics recalculation, and
 * offline/circuit breaker resilience.
 *
 * Traceability:
 * - REQ-MAP-025: Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Data.
 * - TST-MAP-027: Automatic DEM Elevation Enrichment for Imported GPX Routes Lacking Altitude Verification.
 */
class GpxRouteImporterElevationTest {

    private lateinit var mockContext: Context
    private lateinit var mockContentResolver: ContentResolver
    private lateinit var mockElevationService: ElevationService
    private lateinit var importer: GpxRouteImporter
    private val testUri = mockk<Uri>()

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
            val results = arg<FloatArray>(4)
            results[0] = 50.0f
        }

        mockContext = mockk(relaxed = true)
        mockContentResolver = mockk(relaxed = true)
        every { mockContext.contentResolver } returns mockContentResolver
        every { testUri.lastPathSegment } returns "test_track.gpx"

        mockElevationService = mockk(relaxed = true)
        importer = GpxRouteImporter(mockContext, mockElevationService)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Group 1: Verify that a GPX file with existing non-zero elevations preserves them
     * and strictly does NOT make outbound elevation API calls.
     */
    @Test
    fun testImportRoute_whenGpxHasEmbeddedElevations_preservesElevationsAndSkipsApi() = runBlocking {
        val gpxWithElevation = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="TestRunner">
              <trk>
                <name>Mountain Climb</name>
                <trkseg>
                  <trkpt lat="47.5" lon="11.2"><ele>650.0</ele></trkpt>
                  <trkpt lat="47.6" lon="11.3"><ele>720.0</ele></trkpt>
                  <trkpt lat="47.7" lon="11.4"><ele>810.0</ele></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        every { mockContentResolver.openInputStream(testUri) } answers {
            ByteArrayInputStream(gpxWithElevation.toByteArray(Charsets.UTF_8))
        }

        val result = importer.importRouteFromGpx(testUri)

        assertTrue(result.isSuccess)
        val (summary, points) = result.getOrThrow()

        // Elevation service MUST NOT be invoked when embedded elevations exist
        coVerify(exactly = 0) { mockElevationService.getBatchElevationsAsync(any()) }

        assertEquals(3, points.size)
        assertEquals(650.0, points[0].altitude, 0.001)
        assertEquals(720.0, points[1].altitude, 0.001)
        assertEquals(810.0, points[2].altitude, 0.001)

        // Elevation gain: (720 - 650) + (810 - 720) = 70 + 90 = 160.0m
        assertEquals(160.0, summary.elevationGain, 0.001)
    }

    /**
     * Group 1 & 3: Verify that a 2D GPX route lacking altitude tags triggers DEM enrichment
     * and updates PathPoints and RouteSummary elevationGain accurately.
     */
    @Test
    fun testImportRoute_whenGpxLacksAltitude_triggersEnrichmentAndCalculatesGain() = runBlocking {
        val gpxWithoutElevation = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="TestRunner">
              <trk>
                <name>Lake Trail 2D</name>
                <trkseg>
                  <trkpt lat="48.13" lon="11.57"/>
                  <trkpt lat="48.14" lon="11.58"/>
                  <trkpt lat="48.15" lon="11.59"/>
                  <trkpt lat="48.16" lon="11.60"/>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        every { mockContentResolver.openInputStream(testUri) } answers {
            ByteArrayInputStream(gpxWithoutElevation.toByteArray(Charsets.UTF_8))
        }

        // Mock DEM elevation API response
        coEvery { mockElevationService.getBatchElevationsAsync(any()) } returns
                ElevationResult.BatchSuccess(listOf(500.0, 520.0, 515.0, 540.0))

        val result = importer.importRouteFromGpx(testUri)

        assertTrue(result.isSuccess)
        val (summary, points) = result.getOrThrow()

        coVerify(exactly = 1) { mockElevationService.getBatchElevationsAsync(any()) }

        assertEquals(4, points.size)
        assertEquals(500.0, points[0].altitude, 0.001)
        assertEquals(520.0, points[1].altitude, 0.001)
        assertEquals(515.0, points[2].altitude, 0.001)
        assertEquals(540.0, points[3].altitude, 0.001)

        // Gain: (520 - 500 = +20) + (515 - 520 = -5, omitted) + (540 - 515 = +25) = 45.0m
        assertEquals(45.0, summary.elevationGain, 0.001)
    }

    /**
     * Group 2: Verify chunking when coordinates exceed MAX_BATCH_SIZE (100).
     * 250 points must result in exactly 3 requests (100, 100, 50).
     */
    @Test
    fun testImportRoute_whenPointsExceedBatchSize_chunksQueriesToMax100() = runBlocking {
        val count = 250
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1"><trk><name>Century</name><trkseg>""")
        for (i in 0 until count) {
            sb.append("""<trkpt lat="${48.0 + (i * 0.001)}" lon="${11.0 + (i * 0.001)}"/>""")
        }
        sb.append("""</trkseg></trk></gpx>""")

        every { mockContentResolver.openInputStream(testUri) } answers {
            ByteArrayInputStream(sb.toString().toByteArray(Charsets.UTF_8))
        }

        val capturedBatches = mutableListOf<List<LatLng>>()
        coEvery { mockElevationService.getBatchElevationsAsync(capture(capturedBatches)) } answers {
            val batch = capturedBatches.last()
            ElevationResult.BatchSuccess(batch.map { 400.0 })
        }

        val result = importer.importRouteFromGpx(testUri)

        assertTrue(result.isSuccess)
        val (_, points) = result.getOrThrow()

        assertEquals(250, points.size)
        // Verify exactly 3 batches were sent
        assertEquals(3, capturedBatches.size)
        assertEquals(100, capturedBatches[0].size)
        assertEquals(100, capturedBatches[1].size)
        assertEquals(50, capturedBatches[2].size)

        // All points enriched with 400.0
        assertTrue(points.all { it.altitude == 400.0 })
    }

    /**
     * Group 4: Verify resilient fallback when offline, timed out, or network error occurs.
     * The import must complete cleanly as a 2D route with 0.0m elevation without throwing exceptions.
     */
    @Test
    fun testImportRoute_whenNetworkTimesOut_importsRouteWithZeroGainSafely() = runBlocking {
        val gpxWithoutElevation = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <trk><name>Offline Trail</name>
                <trkseg>
                  <trkpt lat="48.1" lon="11.5"/>
                  <trkpt lat="48.2" lon="11.6"/>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        every { mockContentResolver.openInputStream(testUri) } answers {
            ByteArrayInputStream(gpxWithoutElevation.toByteArray(Charsets.UTF_8))
        }

        coEvery { mockElevationService.getBatchElevationsAsync(any()) } returns
                ElevationResult.NetworkError("Socket timeout")

        val result = importer.importRouteFromGpx(testUri)

        assertTrue("Import must succeed even on network failure", result.isSuccess)
        val (summary, points) = result.getOrThrow()

        assertEquals(2, points.size)
        assertEquals(0.0, points[0].altitude, 0.001)
        assertEquals(0.0, points[1].altitude, 0.001)
        assertEquals(0.0, summary.elevationGain, 0.001)
    }

    /**
     * Group 4: Verify circuit breaker / rate limiting fallback.
     */
    @Test
    fun testImportRoute_whenCircuitBreakerOpen_gracefullyFallbacksToZero() = runBlocking {
        val gpxWithoutElevation = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1">
              <trk><name>Rate Limited Trail</name>
                <trkseg>
                  <trkpt lat="48.1" lon="11.5"/>
                  <trkpt lat="48.2" lon="11.6"/>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        every { mockContentResolver.openInputStream(testUri) } answers {
            ByteArrayInputStream(gpxWithoutElevation.toByteArray(Charsets.UTF_8))
        }

        coEvery { mockElevationService.getBatchElevationsAsync(any()) } returns
                ElevationResult.CircuitBreakerOpen

        val result = importer.importRouteFromGpx(testUri)

        assertTrue(result.isSuccess)
        val (summary, points) = result.getOrThrow()

        assertEquals(0.0, points[0].altitude, 0.001)
        assertEquals(0.0, points[1].altitude, 0.001)
        assertEquals(0.0, summary.elevationGain, 0.001)
    }
}
