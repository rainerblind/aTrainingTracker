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
    fun testThermalColorLutProgression() {
        val lut = ThermalColorLut(opacity = 1.0)
        assertEquals(0, lut.getColor(0)) // Index 0 is fully transparent

        val low = lut.getColor(10)
        val mid = lut.getColor(128)
        val high = lut.getColor(255)

        // Assert non-zero colors
        assertTrue(low != 0)
        assertTrue(mid != 0)
        assertTrue(high != 0)

        // Assert RGB progression: high intensity has higher red/green component than low (cool blue)
        val lowRed = (low ushr 16) and 0xFF
        val highRed = (high ushr 16) and 0xFF
        assertTrue(highRed >= lowRed)
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
}
