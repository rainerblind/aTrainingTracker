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

package com.atrainingtracker.trainingtracker.ui.clusters

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterStats
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.map.PreviewPolyline
import com.atrainingtracker.trainingtracker.ui.map.estimateInitialCameraPosition
import com.atrainingtracker.trainingtracker.ui.map.resolvePreviewBounds
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.PolyUtil
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

/**
 * Behavioral verification suite for [PathPreviewMap] multi-polyline, padding, and long-click
 * consolidation across [ClusterItem] and [UnclusteredWorkoutItem]
 * (ATT-6041, REQ-UI-2789.3, TST-UI-2789, TST-UI-2789.2, TST-UI-2789.3).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class ClusterThumbnailPathPreviewDelegationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private companion object {
        const val WIDTH_411_DP = 411
        const val PREVIEW_HOST_TAG = "att_6041_cluster_thumbnails_host"
        const val PREVIEW_PNG_REL_PATH = "docs/engineering/walkthroughs/img/ATT-6041_cluster_thumbnails_preview.png"
        const val PREVIEW_GAP_DP = 12
        const val PREVIEW_PADDING_DP = 12
        const val PNG_QUALITY = 100
        val ROUTE_A = listOf(
            LatLng(48.1351, 11.5820),
            LatLng(48.1480, 11.5950),
            LatLng(48.1750, 11.6200),
            LatLng(48.1520, 11.6050),
            LatLng(48.1360, 11.5830)
        )
        val ROUTE_B = listOf(
            LatLng(48.1351, 11.5820),
            LatLng(48.1440, 11.5990),
            LatLng(48.1720, 11.6180),
            LatLng(48.1360, 11.5830)
        )
    }

    private fun resolvePreviewOutputFile(): File {
        val cwd = File(".").canonicalFile
        val repoRoot = if (cwd.name == "app") cwd.parentFile else cwd
        return File(repoRoot, PREVIEW_PNG_REL_PATH)
    }

    private fun createMockViewModel(clusterId: Long = 1L): WorkoutClustersViewModel {
        val statsFlow = MutableStateFlow(
            mapOf(
                clusterId to WorkoutClusterStats(
                    clusterId = clusterId,
                    workoutCount = 12,
                    bestDurationSec = 4825L,
                    totalDistanceMeters = 42500.0 * 12
                )
            )
        )
        return mockk<WorkoutClustersViewModel>(relaxed = true).apply {
            every { getBSportType(any()) } returns BSportType.BIKE
            every { getSportName(any()) } returns "Road Bike"
            every { getLinkedEquipment(any()) } returns listOf("Canyon Aeroad CF SLX")
            every { clusterStats } returns statsFlow
            every { getLocationName(48.1351, 11.5820) } returns "Home"
            every { getLocationName(48.1360, 11.5830) } returns "Home"
        }
    }

    private fun sampleCluster(withRealPolylines: Boolean = true) = WorkoutCluster(
        id = 1L,
        name = "Isar Valley Loop",
        probableSportId = 1L,
        startLat = 48.1351,
        startLng = 11.5820,
        endLat = 48.1360,
        endLng = 11.5830,
        maxDispLat = 48.1750,
        maxDispLng = 11.6200,
        refDistance = 42500.0,
        hitCount = 12,
        bSportType = BSportType.BIKE,
        previewPaths = if (withRealPolylines) listOf(PolyUtil.encode(ROUTE_A), PolyUtil.encode(ROUTE_B)) else emptyList(),
        routePolyline = if (withRealPolylines) PolyUtil.encode(ROUTE_A) else "sample_route"
    )

    private fun sampleWorkout() = WorkoutData(
        id = 42L,
        finished = true,
        fileBaseName = "morning_ride.tcx",
        workoutName = "Morning Gravel Spin",
        sportId = 1L,
        sportName = "Gravel Bike",
        bSportType = BSportType.BIKE,
        startTimeS = 1_760_000_000L,
        formattedDate = "21.11.2026",
        formattedTime = "08:30",
        localDateTime = LocalDateTime.of(2026, 11, 21, 8, 30),
        equipmentName = "Canyon Grail CF SL",
        equipmentId = 7L,
        commute = false,
        trainer = false,
        mapPolyline = PolyUtil.encode(ROUTE_B),
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 14200.0,
        maxDisplacement = null,
        activeTimeSec = 4825L,
        totalTimeSec = 4825L,
        avgSpeedMps = 7.5,
        ascentMeters = 240L,
        descentMeters = 240L,
        minAltitude = 510.0,
        maxAltitude = 580.0,
        startLatLng = LatLng(48.1351, 11.5820),
        endLatLng = LatLng(48.1360, 11.5830),
        maxDisplacementLatLng = LatLng(48.1720, 11.6180),
        description = null,
        goal = null,
        method = null,
        stravaSportName = null
    )

    @Composable
    private fun WithScreenWidth(screenWidthDp: Int, content: @Composable () -> Unit) {
        val baseConfig = LocalConfiguration.current
        val overriddenConfig = Configuration(baseConfig).apply {
            this.screenWidthDp = screenWidthDp
        }
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalConfiguration provides overriddenConfig
        ) {
            Box(modifier = Modifier.width(screenWidthDp.dp).padding(horizontal = 8.dp)) {
                content()
            }
        }
    }

    @Test
    fun buildClusterThumbnailPolylines_preservesHeatmapAlphaAndAuthoritativeZIndex() {
        // TST-UI-2789.2: Verify multi-polyline heatmap styling, authoritative route overlay & safe stub handling
        val primary = Color(0.1f, 0.4f, 0.8f, 1.0f)
        val encodedA = PolyUtil.encode(ROUTE_A)
        val encodedB = PolyUtil.encode(ROUTE_B)

        // Single preview path -> full primary alpha, width 4f, roundCaps
        val single = buildClusterThumbnailPolylines(listOf(encodedA), null, primary)
        assertEquals(1, single.size)
        assertEquals(primary, single[0].color)
        assertEquals(4f, single[0].widthPx, 0.01f)
        assertTrue(single[0].roundCaps)
        assertEquals(0f, single[0].zIndex, 0.01f)

        // Multi-polyline heatmap + authoritative route -> alpha 0.2f (width 6f) + RouteSelected (zIndex 10f)
        val multi = buildClusterThumbnailPolylines(listOf(encodedA, encodedB), encodedA, primary)
        assertEquals(3, multi.size)
        assertEquals(0.2f, multi[0].color.alpha, 0.01f)
        assertEquals(6f, multi[0].widthPx, 0.01f)
        assertEquals(0.2f, multi[1].color.alpha, 0.01f)
        assertEquals(TTColor.RouteSelected, multi[2].color)
        assertEquals(6f, multi[2].widthPx, 0.01f)
        assertEquals(10f, multi[2].zIndex, 0.01f)
        assertTrue(multi[2].roundCaps)

        // Non-decodable stub strings (empty or malformed) never throw
        val emptyOrStub = buildClusterThumbnailPolylines(listOf("", "sample_route"), "sample_workout", primary)
        assertTrue(emptyOrStub.isEmpty())
    }

    @Test
    fun pathPreviewMap_compositeBoundsIncludesAdditionalPathsAndPaddingAffectsCamera() {
        // TST-UI-2789.2: additionalPaths included in composite bounds when targetBounds == null + custom padding
        val extra = listOf(
            PreviewPolyline(
                points = listOf(LatLng(48.10, 11.50), LatLng(48.20, 11.70)),
                color = TTColor.RouteSelected,
                widthPx = 6f,
                roundCaps = true,
                zIndex = 10f
            )
        )
        val bounds = resolvePreviewBounds(
            targetBounds = null,
            allPaths = emptyList(),
            start = null,
            end = null,
            apex = null,
            additionalPaths = extra
        )
        assertNotNull(bounds)
        assertEquals(48.10, bounds!!.southwest.latitude, 1e-6)
        assertEquals(11.70, bounds.northeast.longitude, 1e-6)

        val camTight = estimateInitialCameraPosition(bounds, widthDp = 175f, heightDp = 120f, paddingPx = THUMBNAIL_BOUNDS_PADDING_PX, density = 2f)
        val camWide = estimateInitialCameraPosition(bounds, widthDp = 175f, heightDp = 120f, paddingPx = 40, density = 2f)
        assertTrue(
            "Tighter 18px thumbnail padding must produce higher zoom than legacy 40px padding (${camTight.zoom} vs ${camWide.zoom})",
            camTight.zoom > camWide.zoom
        )
    }

    @Test
    fun clusterItem_longClickOnPathPreviewMapThumbnailOpensContextMenu() {
        // TST-UI-2789.2: Long-click overlay parity on ClusterItem delegated to PathPreviewMap
        val viewModel = createMockViewModel()
        var mergeClicks = 0
        var deleteClicks = 0
        var cardClicks = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WithScreenWidth(WIDTH_411_DP) {
                    ClusterItem(
                        cluster = sampleCluster(withRealPolylines = true),
                        viewModel = viewModel,
                        onClick = { cardClicks++ },
                        onDeleteRequest = { deleteClicks++ },
                        onHitCountClick = {},
                        onMergeRequest = { mergeClicks++ }
                    )
                }
            }
        }

        // Long-click on the PathPreviewMap-backed thumbnail opens the Merge/Delete context menu
        composeTestRule.onNodeWithTag(CLUSTER_THUMBNAIL_TEST_TAG, useUnmergedTree = true)
            .performTouchInput { longClick() }

        composeTestRule.onNodeWithText("delete", ignoreCase = true).assertIsDisplayed().performClick()
        assertEquals(1, deleteClicks)
        assertEquals(0, cardClicks)
        assertEquals(0, mergeClicks)
    }

    @Composable
    private fun ClusterPreviewThemeColumn(isDark: Boolean, viewModel: WorkoutClustersViewModel) {
        ATrainingTrackerTheme(darkTheme = isDark) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(WIDTH_411_DP.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)
                ) {
                    Text(
                        text = if (isDark) "ATT-6041 Cluster PathPreviewMap — Dark" else "ATT-6041 Cluster PathPreviewMap — Light",
                        style = MaterialTheme.typography.titleSmall
                    )
                    WithScreenWidth(WIDTH_411_DP) {
                        Column(verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)) {
                            ClusterItem(
                                cluster = sampleCluster(withRealPolylines = true),
                                viewModel = viewModel,
                                onClick = {},
                                onDeleteRequest = {},
                                onHitCountClick = {}
                            )
                            UnclusteredWorkoutItem(
                                workout = sampleWorkout(),
                                viewModel = viewModel,
                                onClick = {}
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun captureClusterThumbnailsViaPathPreviewMapLightDarkPng_forRule85() {
        // TST-UI-2789.3: Rule 8.5 dedicated Light/Dark visual verification capture
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width((WIDTH_411_DP * 2 + PREVIEW_PADDING_DP * 3).dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .padding(PREVIEW_PADDING_DP.dp)
                    .testTag(PREVIEW_HOST_TAG),
                horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)
            ) {
                ClusterPreviewThemeColumn(isDark = false, viewModel = viewModel)
                ClusterPreviewThemeColumn(isDark = true, viewModel = viewModel)
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
        assertTrue("ATT-6041 cluster thumbnails PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
