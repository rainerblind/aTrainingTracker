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
 */

package com.atrainingtracker.trainingtracker.ui.map

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Unit, layout safety, and Rule 8.5 Light/Dark preview verification for [PathPreviewMap]
 * synchronous bounds resolution, Web Mercator initial camera seeding, and layout retry
 * (ATT-8736, REQ-MAP-068.1, REQ-MAP-068.2, TST-MAP-071.1..TST-MAP-071.3).
 *
 * Adheres strictly to REQ-PRO-049.1 (zero src/main file reads).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class PathPreviewMapBoundsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private companion object {
        const val PREVIEW_HOST_TAG = "att_8736_path_preview_host"
        const val INTRINSIC_PREVIEW_TAG = "att_8736_intrinsic_preview"
        const val PREVIEW_PNG_REL_PATH = "docs/engineering/walkthroughs/img/ATT-8736_path_preview_map.png"
        const val PREVIEW_WIDTH_DP = 360
        const val PREVIEW_GAP_DP = 12
        const val PREVIEW_PADDING_DP = 12
        const val PNG_QUALITY = 100
        const val LAT_SW = 48.1300
        const val LNG_SW = 11.5700
        const val LAT_NE = 48.1500
        const val LNG_NE = 11.6000
    }

    private fun sampleTrack(
        minLat: Double? = LAT_SW,
        minLng: Double? = LNG_SW,
        maxLat: Double? = LAT_NE,
        maxLng: Double? = LNG_NE
    ): MapTrack = MapTrack(
        id = 1L,
        type = TrackType.BEST,
        bSportType = BSportType.BIKE,
        path = listOf(
            PathPoint(0.0, LatLng(48.1351, 11.5820)),
            PathPoint(1200.0, LatLng(48.1450, 11.5920)),
            PathPoint(2400.0, LatLng(48.1520, 11.6100)),
            PathPoint(3600.0, LatLng(48.1410, 11.6050)),
            PathPoint(4800.0, LatLng(48.1360, 11.5840))
        ),
        minLat = minLat,
        minLng = minLng,
        maxLat = maxLat,
        maxLng = maxLng
    )

    private fun resolvePreviewOutputFile(): File {
        val cwd = File(".").canonicalFile
        val repoRoot = if (cwd.name == "app") cwd.parentFile else cwd
        return File(repoRoot, PREVIEW_PNG_REL_PATH)
    }

    @Test
    fun buildValidatedBounds_rejectsNullNonFiniteOutOfRangeAndInvertedLat() {
        assertNull(buildValidatedBounds(null, LNG_SW, LAT_NE, LNG_NE))
        assertNull(buildValidatedBounds(LAT_SW, null, LAT_NE, LNG_NE))
        assertNull(buildValidatedBounds(LAT_SW, LNG_SW, null, LNG_NE))
        assertNull(buildValidatedBounds(LAT_SW, LNG_SW, LAT_NE, null))

        assertNull(buildValidatedBounds(Double.NaN, LNG_SW, LAT_NE, LNG_NE))
        assertNull(buildValidatedBounds(LAT_SW, Double.POSITIVE_INFINITY, LAT_NE, LNG_NE))
        assertNull(buildValidatedBounds(LAT_SW, LNG_SW, Double.NEGATIVE_INFINITY, LNG_NE))

        assertNull(buildValidatedBounds(-90.1, LNG_SW, LAT_NE, LNG_NE))
        assertNull(buildValidatedBounds(LAT_SW, LNG_SW, 90.1, LNG_NE))
        assertNull(buildValidatedBounds(LAT_SW, -180.1, LAT_NE, LNG_NE))
        assertNull(buildValidatedBounds(LAT_SW, LNG_SW, LAT_NE, 180.1))

        assertNull(buildValidatedBounds(48.20, LNG_SW, 48.10, LNG_NE))
    }

    @Test
    fun buildValidatedBounds_acceptsStandardAndAntimeridianBounds() {
        val standard = buildValidatedBounds(LAT_SW, LNG_SW, LAT_NE, LNG_NE)
        assertNotNull(standard)
        assertEquals(LAT_SW, standard!!.southwest.latitude, 1e-6)
        assertEquals(LNG_NE, standard.northeast.longitude, 1e-6)

        val antimeridian = buildValidatedBounds(-18.0, 179.0, -17.0, -179.0)
        assertNotNull(antimeridian)
        assertEquals(179.0, antimeridian!!.southwest.longitude, 1e-6)
        assertEquals(-179.0, antimeridian.northeast.longitude, 1e-6)
    }

    @Test
    fun resolvePreviewBounds_prioritizesTargetThenPathThenCompositePoints() {
        val explicitTarget = LatLngBounds(LatLng(47.0, 10.0), LatLng(47.5, 10.5))
        val track = sampleTrack(minLat = 48.0, minLng = 11.0, maxLat = 48.5, maxLng = 11.5)

        val fromTarget = resolvePreviewBounds(explicitTarget, listOf(track), null, null, null)
        assertEquals(explicitTarget, fromTarget)

        val fromTrackBounds = resolvePreviewBounds(null, listOf(track), null, null, null)
        assertNotNull(fromTrackBounds)
        assertEquals(48.0, fromTrackBounds!!.southwest.latitude, 1e-6)
        assertEquals(11.5, fromTrackBounds.northeast.longitude, 1e-6)

        val trackWithoutValidBounds = sampleTrack(minLat = 48.5, minLng = 11.0, maxLat = 48.0, maxLng = 11.5)
        val fromPoints = resolvePreviewBounds(
            targetBounds = null,
            allPaths = listOf(trackWithoutValidBounds),
            start = LatLng(48.1351, 11.5820),
            end = LatLng(48.1360, 11.5840),
            apex = LatLng(48.1600, 11.6200)
        )
        assertNotNull(fromPoints)
        assertEquals(48.1600, fromPoints!!.northeast.latitude, 1e-6)
        assertEquals(11.6200, fromPoints.northeast.longitude, 1e-6)
    }

    @Test
    fun estimateInitialCameraPosition_clampsZoomAndHandlesSinglePointAndAntimeridian() {
        val singlePoint = LatLngBounds(LatLng(48.1351, 11.5820), LatLng(48.1351, 11.5820))
        val singleCam = estimateInitialCameraPosition(singlePoint)
        assertEquals(48.1351, singleCam.target.latitude, 1e-6)
        assertEquals(11.5820, singleCam.target.longitude, 1e-6)
        assertEquals(SINGLE_POINT_DEFAULT_ZOOM, singleCam.zoom, 0.01f)
        assertTrue(singleCam.zoom.isFinite())

        val regional = LatLngBounds(LatLng(LAT_SW, LNG_SW), LatLng(LAT_NE, LNG_NE))
        val regionalCam = estimateInitialCameraPosition(
            bounds = regional,
            widthDp = 360f,
            heightDp = 200f,
            paddingPx = 20,
            density = 2f
        )
        assertTrue(regionalCam.zoom in MIN_ESTIMATED_ZOOM..MAX_ESTIMATED_ZOOM)
        assertNotEquals(0.0, regionalCam.target.latitude, 1e-3)
        assertNotEquals(0.0, regionalCam.target.longitude, 1e-3)

        val global = LatLngBounds(LatLng(-60.0, -170.0), LatLng(70.0, 170.0))
        val globalCam = estimateInitialCameraPosition(global)
        assertEquals(MIN_ESTIMATED_ZOOM, globalCam.zoom, 0.01f)

        val antimeridian = LatLngBounds(LatLng(-18.0, 179.0), LatLng(-17.0, -179.0))
        val antiCam = estimateInitialCameraPosition(antimeridian)
        assertEquals(-17.5, antiCam.target.latitude, 1e-6)
        assertEquals(180.0, kotlin.math.abs(antiCam.target.longitude), 1e-6)
        assertTrue(antiCam.zoom in MIN_ESTIMATED_ZOOM..MAX_ESTIMATED_ZOOM)
    }

    @Test
    fun createLiteModeMapOptions_enforcesLiteModeAndAttachesCamera() {
        val cam = estimateInitialCameraPosition(LatLngBounds(LatLng(LAT_SW, LNG_SW), LatLng(LAT_NE, LNG_NE)))
        val options = createLiteModeMapOptions(cam)
        assertEquals(true, options.liteMode)
        assertEquals(cam, options.camera)
    }

    @Test
    fun tryApplyPreviewBounds_retriesAfterTransientIllegalStateException() {
        var attempts = 0
        val firstResult = tryApplyPreviewBounds {
            attempts++
            check(false) { "Map size is 0" }
        }
        assertFalse(firstResult)

        val secondResult = tryApplyPreviewBounds {
            attempts++
        }
        assertTrue(secondResult)
        assertEquals(2, attempts)
    }

    @Test
    fun pathPreviewMap_rendersSafelyInsideIntrinsicSizeMinWithoutSubcomposeCrash() {
        val track = sampleTrack()
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Row(
                        modifier = Modifier
                            .width(360.dp)
                            .height(IntrinsicSize.Min)
                    ) {
                        PathPreviewMap(
                            path = track,
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp)
                                .testTag(INTRINSIC_PREVIEW_TAG),
                            start = track.latLngs.first(),
                            end = track.latLngs.last(),
                            apex = track.latLngs[2]
                        )
                    }
                }
            }
        }

        val node = composeTestRule.onNodeWithTag(INTRINSIC_PREVIEW_TAG)
        node.assertIsDisplayed()
        val bounds = node.getBoundsInRoot()
        assertTrue("Expected positive preview height inside IntrinsicSize.Min", bounds.height.value > 100f)
        assertTrue("Expected positive preview width inside IntrinsicSize.Min", bounds.width.value > 100f)
    }

    @Test
    fun captureLightAndDarkPreviewPng_forRule85Inspection() {
        val track = sampleTrack()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                Row(
                    modifier = Modifier
                        .width((PREVIEW_WIDTH_DP * 2 + PREVIEW_PADDING_DP * 3).dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .padding(PREVIEW_PADDING_DP.dp)
                        .testTag(PREVIEW_HOST_TAG),
                    horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)
                ) {
                    listOf(false, true).forEach { isDark ->
                        ATrainingTrackerTheme(darkTheme = isDark) {
                            Surface(
                                color = MaterialTheme.colorScheme.background,
                                modifier = Modifier.width(PREVIEW_WIDTH_DP.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (isDark) "Dark PathPreviewMap" else "Light PathPreviewMap",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    PathPreviewMap(
                                        path = track,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(DEFAULT_PREVIEW_HEIGHT_DP.dp),
                                        start = track.latLngs.first(),
                                        end = track.latLngs.last(),
                                        apex = track.latLngs[2]
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val bitmap = composeTestRule.onNodeWithTag(PREVIEW_HOST_TAG)
            .captureToImage()
            .asAndroidBitmap()

        val outFile = resolvePreviewOutputFile()
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
        }
        assertTrue("Preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
