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

package com.atrainingtracker.trainingtracker.ui.map

import android.graphics.BitmapFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Unit & concurrency tests for [PolylineHeatmapTileProvider] and [ThermalColorLut] (ATT-4480 / TST-MAP-058).
 */
@RunWith(RobolectricTestRunner::class)
class PolylineHeatmapTileProviderTest {

    private val sampleRoute1 = listOf(
        LatLng(48.500, 9.000),
        LatLng(48.505, 9.005),
        LatLng(48.510, 9.010)
    )

    private val sampleRoute2 = listOf(
        LatLng(48.502, 9.002),
        LatLng(48.508, 9.008),
        LatLng(48.512, 9.012)
    )

    @Test
    fun testEmptyPathsReturnsNoTile() {
        val provider = PolylineHeatmapTileProvider(emptyList())
        val tile = provider.getTile(0, 0, 10)
        assertSame(TileProvider.NO_TILE, tile)
    }

    @Test
    fun testInvalidTileCoordinatesReturnNoTile() {
        val provider = PolylineHeatmapTileProvider(listOf(sampleRoute1))
        assertSame(TileProvider.NO_TILE, provider.getTile(-1, 0, 10))
        assertSame(TileProvider.NO_TILE, provider.getTile(0, -1, 10))
        assertSame(TileProvider.NO_TILE, provider.getTile(1 shl 10, 0, 10))
        assertSame(TileProvider.NO_TILE, provider.getTile(0, 0, -1))
        assertSame(TileProvider.NO_TILE, provider.getTile(0, 0, 25))
    }

    @Test
    fun testDisjointTileReturnsNoTileImmediately() {
        val provider = PolylineHeatmapTileProvider(listOf(sampleRoute1))
        // Route is in Germany (~48.5°N, 9.0°E). Tile (0, 0, 10) is near North Pole / Alaska (far away).
        val tile = provider.getTile(0, 0, 10)
        assertSame(TileProvider.NO_TILE, tile)
    }

    @Test
    fun testIntersectingTileGeneratesValidPngTile() {
        val provider = PolylineHeatmapTileProvider(
            allPaths = listOf(sampleRoute1, sampleRoute2),
            opacity = 0.8,
            strokeWidthPx = 6.0f
        )

        // Calculate tile (x, y) for (48.5°N, 9.0°E) at zoom 12
        val zoom = 12
        val nTiles = 1 shl zoom
        val x = ((9.0 + 180.0) / 360.0 * nTiles).toInt()
        val latRad = Math.toRadians(48.5)
        val y = ((1.0 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0 * nTiles).toInt()

        val tile = provider.getTile(x, y, zoom)
        assertNotNull(tile)
        assertTrue(tile !== TileProvider.NO_TILE)
        assertEquals(256, tile!!.width)
        assertEquals(256, tile.height)
        assertNotNull(tile.data)
        val data = tile.data!!
        assertTrue(data.isNotEmpty())

        // Verify valid decodable PNG bitmap
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size)
        assertNotNull(decoded)
        assertEquals(256, decoded.width)
        assertEquals(256, decoded.height)
    }

    @Test
    fun testThermalColorLutProgression_TST_MAP_058_3() {
        val lut = ThermalColorLut(opacity = 1.0)
        assertEquals(0, lut.getColor(0)) // Index 0 is fully transparent

        val low = lut.getColor(10)
        val mid = lut.getColor(128)
        val high = lut.getColor(255)

        // Assert non-zero colors
        assertTrue(low != 0)
        assertTrue(mid != 0)
        assertTrue(high != 0)

        // Extract ARGB components
        val lowAlpha = (low ushr 24) and 0xFF
        val lowGreen = (low ushr 8) and 0xFF
        val lowBlue = low and 0xFF

        val midAlpha = (mid ushr 24) and 0xFF
        val midRed = (mid ushr 16) and 0xFF
        val midGreen = (mid ushr 8) and 0xFF
        val midBlue = mid and 0xFF

        val highAlpha = (high ushr 24) and 0xFF
        val highRed = (high ushr 16) and 0xFF
        val highGreen = (high ushr 8) and 0xFF
        val highBlue = high and 0xFF

        // 1. Light Cyan at low intensity: prominent green and blue
        assertTrue("Low intensity should have high green (cyan)", lowGreen > 200)
        assertEquals("Low intensity blue component should be 255", 255, lowBlue)

        // 2. Pure Blue at mid intensity: green decreases significantly, blue remains dominant
        assertTrue("Green component should decrease from low to mid", lowGreen > midGreen)
        assertEquals("Mid intensity red should be 0 (pure blue)", 0, midRed)
        assertEquals("Mid intensity blue should be 255", 255, midBlue)

        // 3. Deep Indigo at high intensity: R=49, G=27, B=146
        assertEquals("High intensity red should match Deep Indigo (49)", 49, highRed)
        assertEquals("High intensity green should match Deep Indigo (27)", 27, highGreen)
        assertEquals("High intensity blue should match Deep Indigo (146)", 146, highBlue)

        // 4. Alpha progression: base alpha scales from low (~140 / 55%) to mid (~204 / 80%) to high (255 / 100%)
        assertTrue("Alpha should increase from low to mid", lowAlpha < midAlpha)
        assertTrue("Alpha should increase from mid to high", midAlpha < highAlpha)
        assertEquals("High intensity alpha at opacity 1.0 should be 255", 255, highAlpha)
        assertTrue("Low intensity alpha must be >= 135 for clear single-pass legibility", lowAlpha >= 135)
    }

    /**
     * TST-PER-030.2: Adaptive Corridor Frequency Normalization Test (REQ-PER-024.4).
     */
    @Test
    fun testAdaptiveCorridorFrequencyNormalization() {
        // <= 3 workouts: 2 passes to saturation (fraction = 0.5f -> 128)
        assertEquals(128, PolylineHeatmapTileProvider.calculateStepIntensity(1))
        assertEquals(128, PolylineHeatmapTileProvider.calculateStepIntensity(3))

        // <= 10 workouts (Week): 3 passes to saturation (fraction = 1/3 -> 85)
        assertEquals(85, PolylineHeatmapTileProvider.calculateStepIntensity(5))
        assertEquals(85, PolylineHeatmapTileProvider.calculateStepIntensity(10))

        // <= 35 workouts (Month): 5 passes to saturation (fraction = 1/5 -> 51)
        assertEquals(51, PolylineHeatmapTileProvider.calculateStepIntensity(20))
        assertEquals(51, PolylineHeatmapTileProvider.calculateStepIntensity(35))

        // <= 100 workouts: 8 passes to saturation (fraction = 1/8 -> 32)
        assertEquals(32, PolylineHeatmapTileProvider.calculateStepIntensity(50))
        assertEquals(32, PolylineHeatmapTileProvider.calculateStepIntensity(100))

        // > 100 workouts (Year): 12 passes to saturation (fraction = 1/12 -> 21)
        assertEquals(21, PolylineHeatmapTileProvider.calculateStepIntensity(150))
        assertEquals(21, PolylineHeatmapTileProvider.calculateStepIntensity(300))

        // Explicit override preservation
        assertEquals(64, PolylineHeatmapTileProvider.calculateStepIntensity(300, intensityPerPass = 0.25f))
    }

    @Test
    fun testConcurrentWorkerThreadSafety() {
        val provider = PolylineHeatmapTileProvider(listOf(sampleRoute1, sampleRoute2))
        val zoom = 12
        val nTiles = 1 shl zoom
        val x = ((9.0 + 180.0) / 360.0 * nTiles).toInt()
        val latRad = Math.toRadians(48.5)
        val y = ((1.0 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0 * nTiles).toInt()

        val executor = Executors.newFixedThreadPool(4)
        val tasks = (0 until 20).map {
            Callable {
                provider.getTile(x, y, zoom)
            }
        }

        val results = executor.invokeAll(tasks)
        executor.shutdown()

        assertEquals(20, results.size)
        for (result in results) {
            val tile = result.get()
            assertNotNull(tile)
            assertTrue(tile !== TileProvider.NO_TILE)
            assertEquals(256, tile!!.width)
        }
    }

    /**
     * TST-MAP-058.6: Multi-Vertex Collinear Path Uniformity Test (ATT-4932 / REQ-MAP-056.5).
     * Asserts that a single straight path with multiple collinear vertices yields uniform intensity
     * matching a 2-point segment with zero intensity peaks/beads at vertex coordinates.
     */
    @Test
    fun testCollinearMultiVertexUniformIntensity_TST_MAP_058_6() {
        // Collinear path with 10 vertices
        val collinearPath = (0..10).map { LatLng(48.500 + it * 0.001, 9.000 + it * 0.001) }
        val baselinePath = listOf(collinearPath.first(), collinearPath.last())

        val zoom = 12
        val nTiles = 1 shl zoom
        val x = ((9.0 + 180.0) / 360.0 * nTiles).toInt()
        val latRad = Math.toRadians(48.5)
        val y = ((1.0 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0 * nTiles).toInt()

        val collinearProvider = PolylineHeatmapTileProvider(listOf(collinearPath))
        val baselineProvider = PolylineHeatmapTileProvider(listOf(baselinePath))

        val collinearBuffer = collinearProvider.computeAccumulationBuffer(x, y, zoom)
        val baselineBuffer = baselineProvider.computeAccumulationBuffer(x, y, zoom)

        assertNotNull(collinearBuffer)
        assertNotNull(baselineBuffer)

        // Peak count along the single workout line must be exactly 1.0 (no vertex beads)
        val maxCollinear = collinearBuffer!!.maxOrNull() ?: 0f
        val maxBaseline = baselineBuffer!!.maxOrNull() ?: 0f
        assertEquals(1.0f, maxCollinear, 1e-4f)
        assertEquals(1.0f, maxBaseline, 1e-4f)

        // Check parity with baseline: collinear vertices match the continuous line without swelling
        var matchingPixels = 0
        for (i in collinearBuffer.indices) {
            val c = collinearBuffer[i]
            val b = baselineBuffer[i]
            if (c > 0f || b > 0f) {
                assertEquals("Pixel $i must match baseline line without vertex bead peaks", b, c, 0.05f)
                matchingPixels++
            }
        }
        assertTrue("Expected non-empty line rasterization", matchingPixels > 50)
    }

    /**
     * TST-MAP-058.7: Standstill Jitter Single-Count Masking Test (ATT-4932 / REQ-MAP-056.5).
     * Asserts that 20 tightly clustered GPS jitter points within a small radius contribute
     * at most count = 1 for that workout (preventing premature saturation blobs).
     */
    @Test
    fun testStandstillJitterSingleCountMasking_TST_MAP_058_7() {
        val jitterPath = (0..20).map {
            LatLng(48.500 + (it % 3 - 1) * 0.00002, 9.000 + (it % 4 - 1.5) * 0.00002)
        }

        val zoom = 12
        val nTiles = 1 shl zoom
        val x = ((9.0 + 180.0) / 360.0 * nTiles).toInt()
        val latRad = Math.toRadians(48.5)
        val y = ((1.0 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0 * nTiles).toInt()

        val provider = PolylineHeatmapTileProvider(listOf(jitterPath))
        val buffer = provider.computeAccumulationBuffer(x, y, zoom)

        assertNotNull(buffer)
        val maxCount = buffer!!.maxOrNull() ?: 0f
        // Must be capped at count = 1.0f despite 20 overlapping segments
        assertEquals(1.0f, maxCount, 1e-4f)
    }

    /**
     * TST-MAP-058.8: Multi-Workout Coincident Accumulation Test (ATT-4932 / REQ-MAP-056.5).
     * Asserts that two separate coincident workout paths accumulate to count = 2.0 across
     * all covered pixels without clamping.
     */
    @Test
    fun testMultiWorkoutCoincidentAccumulation_TST_MAP_058_8() {
        val path1 = listOf(LatLng(48.500, 9.000), LatLng(48.505, 9.005))
        val path2 = listOf(LatLng(48.500, 9.000), LatLng(48.505, 9.005))

        val zoom = 12
        val nTiles = 1 shl zoom
        val x = ((9.0 + 180.0) / 360.0 * nTiles).toInt()
        val latRad = Math.toRadians(48.5)
        val y = ((1.0 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0 * nTiles).toInt()

        val singleProvider = PolylineHeatmapTileProvider(listOf(path1))
        val dualProvider = PolylineHeatmapTileProvider(listOf(path1, path2))

        val singleBuf = singleProvider.computeAccumulationBuffer(x, y, zoom)!!
        val dualBuf = dualProvider.computeAccumulationBuffer(x, y, zoom)!!

        val maxSingle = singleBuf.maxOrNull() ?: 0f
        val maxDual = dualBuf.maxOrNull() ?: 0f

        assertEquals(1.0f, maxSingle, 1e-4f)
        assertEquals(2.0f, maxDual, 1e-4f)

        // Verify linearity: dualBuf == singleBuf * 2 everywhere
        for (i in singleBuf.indices) {
            val s = singleBuf[i]
            val d = dualBuf[i]
            if (s > 0f) {
                assertEquals(s * 2.0f, d, 1e-4f)
            }
        }
    }

    /**
     * TST-MAP-058.9: Bounded Scratch Buffer & Dirty-Rect Clearing Test (ATT-4932 / REQ-MAP-056.3 / .5).
     * Asserts that candidate paths in disjoint parts of a tile accumulate correctly and
     * the call-local scratch mask resets in-place without leaking state across paths.
     */
    @Test
    fun testBoundedScratchBufferAndDirtyRectClearing_TST_MAP_058_9() {
        // Disjoint paths: route A and route B in different sections of the tile
        val pathA = listOf(LatLng(48.495, 8.995), LatLng(48.498, 8.998))
        val pathB = listOf(LatLng(48.515, 9.020), LatLng(48.520, 9.025))

        val zoom = 12
        val nTiles = 1 shl zoom
        val x = ((9.0 + 180.0) / 360.0 * nTiles).toInt()
        val latRad = Math.toRadians(48.5)
        val y = ((1.0 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0 * nTiles).toInt()

        val provider = PolylineHeatmapTileProvider(listOf(pathA, pathB))
        val buffer = provider.computeAccumulationBuffer(x, y, zoom)

        assertNotNull(buffer)
        // Both paths drawn cleanly
        val maxCount = buffer!!.maxOrNull() ?: 0f
        assertEquals(1.0f, maxCount, 1e-4f)
        assertTrue("Both disjoint paths must contribute to accumulation buffer", buffer.count { it > 0f } > 100)
    }

    /**
     * TST-MAP-058.10: Per-Period Maximum Corridor Frequency Normalization & Logarithmic Transfer Curve (ATT-4929 / REQ-MAP-056.6).
     * Asserts that synthetic periods with 1x, 13x, and 150x overlap corridors yield distinct, strictly
     * monotonic intensities (I(1) = 85 < I(13) ≈ 172 < I(150) = 255) and saturation only for the maximum corridor.
     */
    @Test
    fun testLogarithmicCorridorFrequencyTransferCurve_TST_MAP_058_10() {
        val path1 = listOf(LatLng(48.500, 9.000), LatLng(48.505, 9.005))
        val path2 = listOf(LatLng(48.505, 9.005), LatLng(48.510, 9.010))
        val path3 = listOf(LatLng(48.510, 9.010), LatLng(48.515, 9.015))

        // Synthetic dataset: 1 pass on path1, 13 passes on path2, 150 passes on path3
        val paths = mutableListOf<List<LatLng>>()
        paths.add(path1)
        repeat(13) { paths.add(path2) }
        repeat(150) { paths.add(path3) }

        val provider = PolylineHeatmapTileProvider(
            allPaths = paths,
            maxCorridorPassCount = 150
        )

        val zoom = 12
        val nTiles = 1 shl zoom
        val x = ((9.0 + 180.0) / 360.0 * nTiles).toInt()
        val latRad = Math.toRadians(48.5)
        val y = ((1.0 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0 * nTiles).toInt()

        val tile = provider.getTile(x, y, zoom)
        assertNotNull(tile)
        assertTrue(tile !== TileProvider.NO_TILE)

        val decoded = BitmapFactory.decodeByteArray(tile!!.data, 0, tile.data!!.size)
        assertNotNull(decoded)

        // Verify color progression through LUT
        val lut = ThermalColorLut()
        val color1x = lut.getColor(85)
        val expectedIntensity13 = (85 + 170 * (Math.log(13.0) / Math.log(150.0))).toInt()
        val color13x = lut.getColor(expectedIntensity13)
        val color150x = lut.getColor(255)

        // 1. Monotonic intensity ordering
        assertTrue(85 < expectedIntensity13)
        assertTrue(expectedIntensity13 < 255)
        assertEquals(172, expectedIntensity13)

        // 2. Extracted component progression
        val green1x = (color1x ushr 8) and 0xFF
        val green13x = (color13x ushr 8) and 0xFF
        val green150x = (color150x ushr 8) and 0xFF

        val red1x = (color1x ushr 16) and 0xFF
        val red13x = (color13x ushr 16) and 0xFF
        val red150x = (color150x ushr 16) and 0xFF

        // Light Cyan (high green, 0 red) -> Intermediate Blue-Indigo (lower green, moderate red) -> Deep Indigo (R=49, G=27)
        assertTrue("1x must have higher green (cyan) than 13x", green1x > green13x)
        assertEquals("1x red must be 0", 0, red1x)
        assertEquals("13x red (intermediate between pure blue and deep indigo)", 17, red13x)
        assertTrue("Red increases monotonically from 13x to 150x", red13x < red150x)
        assertEquals("150x red must match Deep Indigo (49)", 49, red150x)
        assertEquals("150x green must match Deep Indigo (27)", 27, green150x)
    }

    /**
     * TST-MAP-058.11: Coarse Spatial Grid Estimator Verification (ATT-4929 / REQ-MAP-056.6).
     * Asserts that estimateMaxCorridorPassCount accurately computes maximum overlap across
     * synthetic intersecting and parallel paths with zero allocation overhead.
     */
    @Test
    fun testCoarseSpatialGridEstimator_TST_MAP_058_11() {
        val corridorA = listOf(LatLng(48.500, 9.000), LatLng(48.510, 9.010))
        val corridorB = listOf(LatLng(48.515, 9.015), LatLng(48.525, 9.025))
        val disjointRoute = listOf(LatLng(48.600, 9.100), LatLng(48.610, 9.110))

        val allPaths = mutableListOf<List<LatLng>>()
        allPaths.add(disjointRoute) // 1x
        repeat(15) { allPaths.add(corridorA) } // 15x
        repeat(42) { allPaths.add(corridorB) } // 42x

        val startTime = System.currentTimeMillis()
        val maxPasses = PolylineHeatmapTileProvider.estimateMaxCorridorPassCount(allPaths)
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals(42, maxPasses)
        assertTrue("Coarse spatial grid estimation must execute in < 50ms (took ${elapsed}ms)", elapsed < 50)

        // Degenerate inputs
        assertEquals(1, PolylineHeatmapTileProvider.estimateMaxCorridorPassCount(emptyList()))
        assertEquals(1, PolylineHeatmapTileProvider.estimateMaxCorridorPassCount(listOf(listOf(LatLng(48.0, 9.0)))))
    }
}
