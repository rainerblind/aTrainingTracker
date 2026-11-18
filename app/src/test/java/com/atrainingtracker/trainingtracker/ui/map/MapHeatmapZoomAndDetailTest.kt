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

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.PeriodType
import com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.getPeriodMapVisuals
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit & Robolectric tests verifying:
 * - [MapContentScopeImpl] heatmap DSL collection and zoom-adaptive layer styling (`TST-MAP-010` / `REQ-MAP-016`).
 * - Detailed map zoom `13..18` heatmap tile generation and terrain-preserving alpha bounds for
 *   Workout Cluster and Period Detail views (`TST-MAP-020` / `REQ-MAP-018`).
 *
 * Note: Live Google Maps GL `TileOverlay` compositing over native vector/terrain tiles is outside
 * JVM scope and verified on-device via Stage 5 emulator inspection.
 *
 * Linked test specs: TST-MAP-010, TST-MAP-020
 */
// Linked test specs: TST-MAP-010, TST-MAP-020
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapHeatmapZoomAndDetailTest {

    private lateinit var context: Context

    private val routeLatLngs = listOf(
        LatLng(48.500, 9.000),
        LatLng(48.505, 9.005),
        LatLng(48.510, 9.010)
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * TST-MAP-010.1: Verify MapContentScopeImpl DSL collects heatmap/tracks/markers and
     * resolveZoomAdaptiveStyling culls markers at city scale (zoom < 13) and brings tracks to 1.0f at zoom > 15.
     */
    @Test
    fun testMapContentScopeHeatmapDslAndZoomAdaptiveStyling() {
        val scope = MapContentScopeImpl(
            zoomFocus = MapZoomFocus.FIT_PRIMARY,
            primaryColor = Color.Blue,
            context = context,
            directionIcons = Triple(null, null, null),
            bSportType = BSportType.BIKE
        )

        val trackPoints = routeLatLngs.mapIndexed { idx, pt ->
            PathPoint(distance = idx * 500.0, latLng = pt)
        }
        scope.collect {
            heatmap(allPaths = listOf(routeLatLngs), opacity = 0.8)
            tracks(
                listOf(
                    MapTrack(
                        id = 1L,
                        type = TrackType.BEST,
                        bSportType = BSportType.BIKE,
                        path = trackPoints
                    )
                )
            )
            markers(
                listOf(
                    LocationMarker(position = routeLatLngs.first(), iconResId = android.R.drawable.ic_menu_mylocation, alpha = 0.7f)
                )
            )
        }

        assertEquals(1, scope.heatmaps.size)
        assertEquals(1, scope.heatmaps.first().allPaths.size)
        assertEquals(1, scope.tracks.size)
        assertEquals(1, scope.markers.size)

        val countryZoom = resolveZoomAdaptiveStyling(9f)
        assertEquals(0.4f, countryZoom.trackAlpha, 0.001f)
        assertEquals(0.0f, countryZoom.markerAlphaMult, 0.001f)
        assertEquals(0.15, countryZoom.heatmapWeight, 0.001)

        val cityZoom = resolveZoomAdaptiveStyling(11.5f)
        assertEquals(0.6f, cityZoom.trackAlpha, 0.001f)
        assertEquals(0.0f, cityZoom.markerAlphaMult, 0.001f)
        assertEquals(0.3, cityZoom.heatmapWeight, 0.001)

        val districtZoom = resolveZoomAdaptiveStyling(14f)
        assertEquals(0.8f, districtZoom.trackAlpha, 0.001f)
        assertEquals(0.2f, districtZoom.markerAlphaMult, 0.001f)
        assertEquals(0.6, districtZoom.heatmapWeight, 0.001)

        val detailZoom = resolveZoomAdaptiveStyling(17f)
        assertEquals(1.0f, detailZoom.trackAlpha, 0.001f)
        assertEquals(1.0f, detailZoom.markerAlphaMult, 0.001f)
        assertEquals(0.8, detailZoom.heatmapWeight, 0.001)
    }

    /**
     * TST-MAP-020.1: Verify detailed zoom 13..18 tile generation for both Workout Cluster
     * (CLASSIC_BLUE, base alpha <= 180/255) and Period Detail (HOT, getPeriodMapVisuals) views.
     */
    @Test
    fun testDetailedZoom13To18_clusterAndPeriodHeatmapTilesVisibleAndAlphaBounded() {
        val clusterProvider = PolylineHeatmapTileProvider(
            allPaths = listOf(routeLatLngs, routeLatLngs, routeLatLngs),
            opacity = 0.85,
            colorLut = ThermalColorLut.CLASSIC_BLUE,
            useClassicStepIntensity = true
        )

        // Verify CLASSIC_BLUE preserves terrain/road legibility (max LUT base alpha <= 180)
        val maxClusterAlpha = ThermalColorLut.CLASSIC_BLUE_STOPS.maxOf { it.second[3] }
        assertTrue("CLASSIC_BLUE max base alpha must stay <= 180", maxClusterAlpha <= 180)

        val periodVisuals = getPeriodMapVisuals(
            periodType = PeriodType.MONTH,
            allPaths = listOf(routeLatLngs, routeLatLngs),
            isInteractive = true,
            zoom = 16f,
            colorLut = ThermalColorLut.HOT
        )
        val periodProvider = periodVisuals.heatmapProvider as? PolylineHeatmapTileProvider
        assertNotNull("Interactive Period Detail map must build a PolylineHeatmapTileProvider", periodProvider)

        val sampleLat = 48.505
        val sampleLng = 9.005
        for (zoom in 13..18) {
            val nTiles = 1 shl zoom
            val x = ((sampleLng + 180.0) / 360.0 * nTiles).toInt()
            val latRad = Math.toRadians(sampleLat)
            val y = ((1.0 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2.0 * nTiles).toInt()

            listOf("cluster" to clusterProvider, "period" to periodProvider!!).forEach { (label, provider) ->
                val tile = provider.getTile(x, y, zoom)
                assertNotNull("$label tile at zoom $zoom must not be null", tile)
                assertTrue("$label tile at zoom $zoom must not be NO_TILE", tile !== TileProvider.NO_TILE)
                val bytes = tile!!.data
                assertNotNull("$label tile PNG bytes at zoom $zoom must not be null", bytes)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size)
                assertNotNull("$label tile at zoom $zoom must decode to a Bitmap", bitmap)
                assertEquals(256, bitmap.width)
                assertEquals(256, bitmap.height)

                var visiblePixels = 0
                for (py in 0 until bitmap.height step 2) {
                    for (px in 0 until bitmap.width step 2) {
                        val alpha = (bitmap.getPixel(px, py) ushr 24) and 0xFF
                        if (alpha > 0) {
                            visiblePixels++
                            if (label == "cluster") {
                                assertTrue("Cluster pixel alpha ($alpha) must stay <= 180", alpha <= 180)
                            }
                        }
                    }
                }
                assertTrue("$label tile at zoom $zoom must render visible heatmap pixels", visiblePixels > 0)
            }
        }
    }
}
