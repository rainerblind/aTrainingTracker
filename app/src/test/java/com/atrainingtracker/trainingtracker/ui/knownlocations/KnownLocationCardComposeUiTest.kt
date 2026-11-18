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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
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

/**
 * Behavioral Compose UI verification suite for [KnownLocationsListContent] and [KnownLocationCard]
 * (REQ-UI-2785, TST-UI-2785.3, TST-UI-2785.4, REQ-PRO-049.1, ATT-8749).
 *
 * Uses zero app/src/main/ file reads and renders via LocalInspectionMode for offline deterministic bounds and PNG checks.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class KnownLocationCardComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleItem(
        id: Long = 1L,
        name: String = "Zuhause am Olympiapark",
        altitude: Double = 520.0,
        isHome: Boolean = true
    ) = KnownLocationItem(
        id = id,
        name = name,
        altitude = altitude,
        radius = 150,
        latLng = LatLng(48.137154, 11.576124),
        hitCount = 142,
        isLocked = true,
        source = ElevationSource.MANUAL_USER,
        isHome = isHome
    )

    private fun sampleCluster() = WorkoutCluster(
        id = 101L,
        name = "Hausrunde",
        probableSportId = 1L,
        startLat = 48.137,
        startLng = 11.576,
        endLat = 48.137,
        endLng = 11.576,
        maxDispLat = 48.140,
        maxDispLng = 11.580,
        refDistance = 10200.0,
        hitCount = 5
    )

    private fun sampleUiState(
        item: KnownLocationItem = sampleItem(),
        sportStarts: SportStartsCount = SportStartsCount(bike = 128, run = 345, other = 112)
    ) = KnownLocationsUiState(
        locations = listOf(item),
        filteredLocations = listOf(item),
        clustersByLocationId = mapOf(item.id to listOf(sampleCluster())),
        startsByLocationId = mapOf(item.id to sportStarts.total),
        sportStartsByLocationId = mapOf(item.id to sportStarts),
        isMetric = true
    )

    /**
     * TST-UI-2785.3 / REQ-UI-2785.1: Verifies that KnownLocationThumbnailMap renders at 175.dp x 120.dp.
     */
    @Test
    fun testPreviewMapBounds_are175x120Dp() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(modifier = Modifier.width(360.dp)) {
                        KnownLocationsListContent(
                            uiState = sampleUiState(),
                            onEdit = {},
                            onShowOnMap = {},
                            onDelete = {}
                        )
                    }
                }
            }
        }

        val previewNode = composeTestRule.onNodeWithTag("location_map_preview_1", useUnmergedTree = true)
        previewNode.assertIsDisplayed()
        val bounds = previewNode.getUnclippedBoundsInRoot()
        val widthDp = bounds.right.value - bounds.left.value
        val heightDp = bounds.bottom.value - bounds.top.value
        assertEquals("Preview width must be 175.dp", 175f, widthDp, 0.5f)
        assertEquals("Preview height must be 120.dp", 120f, heightDp, 0.5f)
    }

    /**
     * TST-UI-2785.3 / REQ-UI-2785.3: Verifies 360.dp narrow-viewport resilience at 130% font scale
     * with 3 multi-digit SportStartsItem counts and long altitude text without horizontal overlap or clipping.
     */
    @Test
    fun testNarrow360DpViewport_130PercentFontScale_noOverlapOrOverflow() {
        val highAltitudeItem = sampleItem(
            name = "Alpenvereinshütte Oberammergau Südgrat",
            altitude = 2962.4
        )
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                val currentDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    LocalDensity provides Density(density = currentDensity.density, fontScale = 1.3f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(360.dp)
                            .testTag("viewport_360dp")
                    ) {
                        KnownLocationsListContent(
                            uiState = sampleUiState(
                                item = highAltitudeItem,
                                sportStarts = SportStartsCount(bike = 128, run = 345, other = 112)
                            ),
                            onEdit = {},
                            onShowOnMap = {},
                            onDelete = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("location_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("location_home_icon_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("location_routes_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("location_starts_badge_1_bike", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("location_starts_badge_1_run", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("location_starts_badge_1_other", useUnmergedTree = true).assertIsDisplayed()

        val cardBounds = composeTestRule.onNodeWithTag("location_card_1").getUnclippedBoundsInRoot()
        val mapBounds = composeTestRule.onNodeWithTag("location_map_preview_1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val startsBounds = composeTestRule.onNodeWithTag("location_starts_badge_1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertTrue(
            "Starts column right (${startsBounds.right.value}) must stay left of 175dp map (${mapBounds.left.value})",
            startsBounds.right.value <= mapBounds.left.value + 1f
        )
        assertTrue(
            "Map preview right (${mapBounds.right.value}) must fit inside 360dp card (${cardBounds.right.value})",
            mapBounds.right.value <= cardBounds.right.value + 0.5f
        )
    }

    /**
     * TST-UI-2785.3 / REQ-UI-2785.1: Verifies tap callbacks for edit, map preview, and sport-specific starts.
     */
    @Test
    fun testCardCallbacks_editShowOnMapAndSportStartsDispatch() {
        var editClicks = 0
        var mapClicks = 0
        var lastSportClick: BSportType? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(modifier = Modifier.width(360.dp)) {
                        KnownLocationsListContent(
                            uiState = sampleUiState(),
                            onEdit = { editClicks++ },
                            onShowOnMap = { mapClicks++ },
                            onShowWorkoutsForSport = { _, sport -> lastSportClick = sport },
                            onDelete = {}
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("location_map_preview_1", useUnmergedTree = true).performClick()
        assertEquals(1, mapClicks)

        composeTestRule.onNodeWithTag("location_starts_badge_1_bike", useUnmergedTree = true).performClick()
        assertEquals(BSportType.BIKE, lastSportClick)

        composeTestRule.onNodeWithText("Zuhause am Olympiapark", useUnmergedTree = true).performClick()
        assertEquals(1, editClicks)
    }

    /**
     * TST-UI-2785.3 / Rule 8.5: Captures Light and Dark 360dp preview PNG of KnownLocationCard
     * at 175x120 dp for mandatory read_file visual self-inspection.
     */
    @Test
    fun testCaptureLightAndDarkPreviewPng() {
        val uiState = sampleUiState()
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(756.dp)
                    .background(Color(0xFF78909C))
                    .padding(12.dp)
                    .testTag("KnownLocationsLightDarkPreview"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.width(360.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                                KnownLocationsListContent(
                                    uiState = uiState,
                                    onEdit = {},
                                    onShowOnMap = {},
                                    onDelete = {}
                                )
                            }
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.width(360.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                                KnownLocationsListContent(
                                    uiState = uiState,
                                    onEdit = {},
                                    onShowOnMap = {},
                                    onDelete = {}
                                )
                            }
                        }
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("KnownLocationsLightDarkPreview")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8749_lieblingsorte_preview.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0)
    }
}
