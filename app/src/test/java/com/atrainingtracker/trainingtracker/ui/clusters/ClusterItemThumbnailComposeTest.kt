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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterStats
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
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
import kotlin.math.abs

/**
 * Behavioral Compose UI verification suite for enlarged 175x120 dp Lieblingsstrecken card
 * map thumbnails, narrow-screen metadata width guard, and Rule 8.5 Light/Dark preview capture
 * (ATT-8748 / REQ-UI-493.3 / TST-UI-493.3a / TST-UI-493.3c).
 *
 * Strictly adheres to REQ-PRO-049.1 (zero src/main file reads).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class ClusterItemThumbnailComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private companion object {
        const val WIDTH_411_DP = 411
        const val WIDTH_387_DP = 387
        const val WIDTH_360_DP = 360
        const val WIDTH_320_DP = 320
        const val EXPECTED_360_THUMBNAIL_DP = 148
        const val MIN_THUMBNAIL_DP = 110
        const val MIN_METADATA_DP = 160f
        const val EXPECTED_SPACER_DP = 12f
        const val DP_TOLERANCE = 1.5f
        const val MIN_PADDING_PX = 16
        const val MAX_PADDING_PX = 20
        const val PREVIEW_HOST_TAG = "att_8748_light_dark_preview"
        const val PREVIEW_PNG_REL_PATH = "docs/engineering/walkthroughs/img/ATT-8748_lieblingsstrecken_preview.png"
        const val SAMPLE_LAT_START = 48.1351
        const val SAMPLE_LNG_START = 11.5820
        const val SAMPLE_LAT_END = 48.1360
        const val SAMPLE_LNG_END = 11.5830
        const val SAMPLE_LAT_APEX = 48.1750
        const val SAMPLE_LNG_APEX = 11.6200
        const val SAMPLE_DISTANCE_M = 42500.0
        const val SAMPLE_WORKOUT_DISTANCE_M = 14200.0
        const val SAMPLE_BEST_SEC = 4825L
        const val SAMPLE_SPORT_ID = 1L
        const val SAMPLE_HIT_COUNT = 12
        const val SAMPLE_START_TIME_S = 1_760_000_000L
        const val PNG_QUALITY = 100
        const val PREVIEW_GAP_DP = 12
        const val PREVIEW_PADDING_DP = 12
        const val PREVIEW_WIDTH_DP = 411
        const val NARROW_THRESHOLD_DP = 360
        const val YEAR_2026 = 2026
        const val MONTH_NOV = 11
        const val DAY_18 = 18
        const val HOUR_8 = 8
        const val MINUTE_30 = 30
    }

    private fun createMockViewModel(clusterId: Long = 1L): WorkoutClustersViewModel {
        val statsFlow = MutableStateFlow(
            mapOf(
                clusterId to WorkoutClusterStats(
                    clusterId = clusterId,
                    workoutCount = SAMPLE_HIT_COUNT,
                    bestDurationSec = SAMPLE_BEST_SEC,
                    totalDistanceMeters = SAMPLE_DISTANCE_M * SAMPLE_HIT_COUNT
                )
            )
        )
        return mockk<WorkoutClustersViewModel>(relaxed = true).apply {
            every { getBSportType(any()) } returns BSportType.BIKE
            every { getSportName(any()) } returns "Road Bike"
            every { getLinkedEquipment(any()) } returns listOf("Canyon Aeroad CF SLX", "Garmin Vector 3")
            every { clusterStats } returns statsFlow
            every { getLocationName(SAMPLE_LAT_START, SAMPLE_LNG_START) } returns "Home"
            every { getLocationName(SAMPLE_LAT_END, SAMPLE_LNG_END) } returns "Home"
        }
    }

    private fun sampleCluster(id: Long = 1L) = WorkoutCluster(
        id = id,
        name = "Isar Valley Loop",
        probableSportId = SAMPLE_SPORT_ID,
        startLat = SAMPLE_LAT_START,
        startLng = SAMPLE_LNG_START,
        endLat = SAMPLE_LAT_END,
        endLng = SAMPLE_LNG_END,
        maxDispLat = SAMPLE_LAT_APEX,
        maxDispLng = SAMPLE_LNG_APEX,
        refDistance = SAMPLE_DISTANCE_M,
        hitCount = SAMPLE_HIT_COUNT,
        bSportType = BSportType.BIKE,
        routePolyline = "sample_route"
    )

    private fun sampleWorkout() = WorkoutData(
        id = 42L,
        finished = true,
        fileBaseName = "morning_ride.tcx",
        workoutName = "Morning Gravel Spin",
        sportId = SAMPLE_SPORT_ID,
        sportName = "Gravel Bike",
        bSportType = BSportType.BIKE,
        startTimeS = SAMPLE_START_TIME_S,
        formattedDate = "18.11.2026",
        formattedTime = "08:30",
        localDateTime = LocalDateTime.of(YEAR_2026, MONTH_NOV, DAY_18, HOUR_8, MINUTE_30),
        equipmentName = "Canyon Grail CF SL",
        equipmentId = 7L,
        commute = false,
        trainer = false,
        mapPolyline = "sample_workout",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = SAMPLE_WORKOUT_DISTANCE_M,
        maxDisplacement = null,
        activeTimeSec = SAMPLE_BEST_SEC,
        totalTimeSec = SAMPLE_BEST_SEC,
        avgSpeedMps = 7.5,
        ascentMeters = 240L,
        descentMeters = 240L,
        minAltitude = 510.0,
        maxAltitude = 580.0,
        startLatLng = LatLng(SAMPLE_LAT_START, SAMPLE_LNG_START),
        endLatLng = LatLng(SAMPLE_LAT_END, SAMPLE_LNG_END),
        maxDisplacementLatLng = LatLng(SAMPLE_LAT_APEX, SAMPLE_LNG_APEX),
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

    private fun resolvePreviewOutputFile(): File {
        val cwd = File(".").canonicalFile
        val repoRoot = if (cwd.name == "app") cwd.parentFile else cwd
        return File(repoRoot, PREVIEW_PNG_REL_PATH)
    }

    @Test
    fun resolveClusterThumbnailWidthDp_enforces175dpOn411dpAndGuardsNarrowScreens() {
        assertEquals(THUMBNAIL_WIDTH_DP, resolveClusterThumbnailWidthDp(WIDTH_411_DP))
        assertEquals(THUMBNAIL_WIDTH_DP, resolveClusterThumbnailWidthDp(WIDTH_387_DP))
        assertEquals(EXPECTED_360_THUMBNAIL_DP, resolveClusterThumbnailWidthDp(WIDTH_360_DP))
        assertEquals(MIN_THUMBNAIL_DP, resolveClusterThumbnailWidthDp(WIDTH_320_DP))
        assertEquals(175, THUMBNAIL_WIDTH_DP)
        assertEquals(120, THUMBNAIL_HEIGHT_DP)
        assertTrue(
            "Camera bounds padding must be within 16..20 px",
            THUMBNAIL_BOUNDS_PADDING_PX in MIN_PADDING_PX..MAX_PADDING_PX
        )
    }

    @Test
    fun clusterItem_renders175x120ThumbnailWith12dpGapAndHandlesClicksOn411dp() {
        val viewModel = createMockViewModel()
        var cardClicks = 0
        var hitCountClicks = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WithScreenWidth(WIDTH_411_DP) {
                    ClusterItem(
                        cluster = sampleCluster(),
                        viewModel = viewModel,
                        onClick = { cardClicks++ },
                        onDeleteRequest = {},
                        onHitCountClick = { hitCountClicks++ }
                    )
                }
            }
        }

        val thumbBounds = composeTestRule
            .onNodeWithTag(CLUSTER_THUMBNAIL_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()
        val metaBounds = composeTestRule
            .onNodeWithTag(CLUSTER_METADATA_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()

        assertEquals(THUMBNAIL_WIDTH_DP.toFloat(), thumbBounds.width.value, DP_TOLERANCE)
        assertEquals(THUMBNAIL_HEIGHT_DP.toFloat(), thumbBounds.height.value, DP_TOLERANCE)
        val gap = thumbBounds.left.value - metaBounds.right.value
        assertEquals(EXPECTED_SPACER_DP, gap, DP_TOLERANCE)
        assertTrue("Metadata width must be >= 160 dp", metaBounds.width.value >= MIN_METADATA_DP)

        composeTestRule.onNodeWithText("Isar Valley Loop").assertIsDisplayed()
        composeTestRule.onNodeWithTag(CLUSTER_THUMBNAIL_TEST_TAG, useUnmergedTree = true).performClick()
        assertEquals(1, cardClicks)
        assertEquals(0, hitCountClicks)
    }

    @Test
    fun unclusteredWorkoutItem_renders175x120ThumbnailWith12dpGapAndHandlesClickOn411dp() {
        val viewModel = createMockViewModel()
        var cardClicks = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WithScreenWidth(WIDTH_411_DP) {
                    UnclusteredWorkoutItem(
                        workout = sampleWorkout(),
                        viewModel = viewModel,
                        onClick = { cardClicks++ }
                    )
                }
            }
        }

        val thumbBounds = composeTestRule
            .onNodeWithTag(WORKOUT_THUMBNAIL_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()
        val metaBounds = composeTestRule
            .onNodeWithTag(WORKOUT_METADATA_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()

        assertEquals(THUMBNAIL_WIDTH_DP.toFloat(), thumbBounds.width.value, DP_TOLERANCE)
        assertEquals(THUMBNAIL_HEIGHT_DP.toFloat(), thumbBounds.height.value, DP_TOLERANCE)
        val gap = thumbBounds.left.value - metaBounds.right.value
        assertEquals(EXPECTED_SPACER_DP, gap, DP_TOLERANCE)

        composeTestRule.onNodeWithText("Morning Gravel Spin").assertIsDisplayed()
        composeTestRule.onNodeWithTag(WORKOUT_THUMBNAIL_TEST_TAG, useUnmergedTree = true).performClick()
        assertEquals(1, cardClicks)
    }

    @Test
    fun clusterAndWorkoutItems_preserveMetadataWidthOn360dpWithoutIntrinsicSizeException() {
        val viewModel = createMockViewModel()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WithScreenWidth(NARROW_THRESHOLD_DP) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ClusterItem(
                            cluster = sampleCluster(),
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

        val clusterThumb = composeTestRule
            .onNodeWithTag(CLUSTER_THUMBNAIL_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()
        val clusterMeta = composeTestRule
            .onNodeWithTag(CLUSTER_METADATA_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()
        val workoutThumb = composeTestRule
            .onNodeWithTag(WORKOUT_THUMBNAIL_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()
        val workoutMeta = composeTestRule
            .onNodeWithTag(WORKOUT_METADATA_TEST_TAG, useUnmergedTree = true)
            .getBoundsInRoot()

        assertTrue(
            "Cluster metadata width on 360dp must be >= 160dp, was ${clusterMeta.width.value}",
            clusterMeta.width.value >= MIN_METADATA_DP - DP_TOLERANCE
        )
        assertTrue(
            "Workout metadata width on 360dp must be >= 160dp, was ${workoutMeta.width.value}",
            workoutMeta.width.value >= MIN_METADATA_DP - DP_TOLERANCE
        )
        assertTrue(
            "Clamped cluster thumbnail width should match ~148dp, was ${clusterThumb.width.value}",
            abs(clusterThumb.width.value - EXPECTED_360_THUMBNAIL_DP.toFloat()) <= DP_TOLERANCE
        )
        assertEquals(THUMBNAIL_HEIGHT_DP.toFloat(), clusterThumb.height.value, DP_TOLERANCE)
        assertEquals(THUMBNAIL_HEIGHT_DP.toFloat(), workoutThumb.height.value, DP_TOLERANCE)
    }

    @Composable
    private fun PreviewThemeColumn(
        isDark: Boolean,
        viewModel: WorkoutClustersViewModel,
        cluster: WorkoutCluster,
        workout: WorkoutData
    ) {
        ATrainingTrackerTheme(darkTheme = isDark) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(PREVIEW_WIDTH_DP.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)
                ) {
                    Text(
                        text = if (isDark) "Dark (411 dp)" else "Light (411 dp)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    WithScreenWidth(WIDTH_411_DP) {
                        Column(verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)) {
                            ClusterItem(
                                cluster = cluster,
                                viewModel = viewModel,
                                onClick = {},
                                onDeleteRequest = {},
                                onHitCountClick = {}
                            )
                            UnclusteredWorkoutItem(
                                workout = workout,
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
    fun captureLightAndDarkPreviewPng_forRule85Inspection() {
        val viewModel = createMockViewModel()
        val cluster = sampleCluster()
        val workout = sampleWorkout()

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width((PREVIEW_WIDTH_DP * 2 + PREVIEW_PADDING_DP * 3).dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .padding(PREVIEW_PADDING_DP.dp)
                    .testTag(PREVIEW_HOST_TAG),
                horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)
            ) {
                PreviewThemeColumn(isDark = false, viewModel = viewModel, cluster = cluster, workout = workout)
                PreviewThemeColumn(isDark = true, viewModel = viewModel, cluster = cluster, workout = workout)
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
