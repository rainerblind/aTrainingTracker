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

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.climbs.Climb
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.components.core.BottomSheetDesign
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Behavioral unit and Robolectric Compose UI tests for [MapPeekSelection], [MapPeekSelection.Saver],
 * and [MapScreenWithTrack] (`REQ-UI-2790.2`, `TST-UI-2790.2`, `ATT-6040`; `REQ-UI-2794`, `TST-UI-2794`, `ATT-9553`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi", application = Application::class)
class MapPeekSelectionSaverAndUiComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun sampleClimb(id: Long = 505L): Climb {
        val points = (0..24).map { idx ->
            val dist = 1200.0 + idx * 200.0
            val alt = 620.0 + idx * (320.0 / 24.0)
            PathPoint(
                distance = dist,
                latLng = LatLng(47.620 + idx * 0.0012, 11.340 + idx * 0.0010),
                altitude = alt
            )
        }
        return Climb(
            id = id,
            name = "Kesselberg Süd",
            startLat = points.first().latLng.latitude,
            startLng = points.first().latLng.longitude,
            endLat = points.last().latLng.latitude,
            endLng = points.last().latLng.longitude,
            distanceMeters = 4800.0,
            elevationGainMeters = 320.0,
            avgGradePercent = 6.7,
            maxGradePercent = 11.2,
            category = ClimbCategory.CAT_2,
            pathPoints = points
        )
    }

    private fun mockMapViewModel(
        uiState: MapFragmentUIState,
        enabledLayers: Set<GeneralMapLayer> = GeneralMapLayer.entries.toSet()
    ): MapFragmentWithTrackViewModel {
        val vm = mockk<MapFragmentWithTrackViewModel>(relaxed = true)
        every { vm.uiState } returns MutableStateFlow(uiState)
        every { vm.currentLocation } returns MutableStateFlow(null)
        every { vm.liveSegments } returns MutableStateFlow(emptyList())
        every { vm.allRoutes } returns MutableStateFlow(emptyList())
        every { vm.allClusters } returns MutableStateFlow(emptyList())
        every { vm.enabledLayers } returns MutableStateFlow(enabledLayers)
        return vm
    }

    @Test
    fun testMapPeekSelectionSaver_roundTripsNullAndAllFiveVariants_tstUi2790_2() {
        val allowAll = SaverScope { true }
        val variants: List<MapPeekSelection?> = listOf(
            null,
            MapPeekSelection.Segment(101L),
            MapPeekSelection.Route(202L),
            MapPeekSelection.Cluster(303L),
            MapPeekSelection.Location(404L),
            MapPeekSelection.Climb(505L)
        )

        for (original in variants) {
            val saved = with(MapPeekSelection.Saver) { allowAll.save(original) }
            val restored = saved?.let { MapPeekSelection.Saver.restore(it) }
            assertEquals("Saver round-trip failed for $original", original, restored)
        }

        // All five map overlay selections are sheet-backed in BottomSheetScaffold (REQ-UI-2794 / ATT-9553)
        assertTrue(MapPeekSelection.Segment(1L).isSheetBacked)
        assertTrue(MapPeekSelection.Route(1L).isSheetBacked)
        assertTrue(MapPeekSelection.Cluster(1L).isSheetBacked)
        assertTrue(MapPeekSelection.Location(1L).isSheetBacked)
        assertTrue(MapPeekSelection.Climb(1L).isSheetBacked)
        assertEquals(192.dp, BottomSheetDesign.PeekHeightClimb)

        // Malformed saved payloads safely restore to null
        assertNull(MapPeekSelection.Saver.restore(listOf("unknown", 1L)))
        assertNull(MapPeekSelection.Saver.restore(listOf("segment")))
        assertNull(MapPeekSelection.Saver.restore("corrupt"))
    }

    @Test
    fun testMapPeekSelection_stateRestorationTester_survivesConfigurationChange_tstUi2790_2() {
        val restorationTester = StateRestorationTester(composeRule)
        var currentSelection: MapPeekSelection? = null
        var updateSelection: (MapPeekSelection?) -> Unit = {}

        restorationTester.setContent {
            var selection by rememberSaveable(stateSaver = MapPeekSelection.Saver) {
                mutableStateOf<MapPeekSelection?>(MapPeekSelection.Route(77L))
            }
            currentSelection = selection
            updateSelection = { selection = it }
        }
        composeRule.waitForIdle()
        assertEquals(MapPeekSelection.Route(77L), currentSelection)

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals(MapPeekSelection.Route(77L), currentSelection)

        // Switch through Climb and Cluster and restore across config changes
        composeRule.runOnUiThread { updateSelection(MapPeekSelection.Climb(88L)) }
        composeRule.waitForIdle()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals(MapPeekSelection.Climb(88L), currentSelection)
        assertTrue(currentSelection!!.isSheetBacked)

        composeRule.runOnUiThread { updateSelection(MapPeekSelection.Cluster(99L)) }
        composeRule.waitForIdle()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        assertEquals(MapPeekSelection.Cluster(99L), currentSelection)
        assertTrue(currentSelection!!.isSheetBacked)
    }

    @Test
    fun testMapScreenWithTrack_targetLocationRendersKnownLocationSheetAndZeroOverlap_tstUi2790_2() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        val homeLocation = KnownLocationItem(
            id = 42L,
            name = "Home Trailhead",
            latLng = LatLng(48.1371, 11.5754),
            altitude = 520.0,
            radius = 50,
            hitCount = 7,
            isLocked = false,
            source = ElevationSource.AUTO_LEARNED
        )
        val vm = mockMapViewModel(
            uiState = MapFragmentUIState(
                knownLocations = listOf(homeLocation),
                bSportType = BSportType.BIKE
            ),
            enabledLayers = setOf(GeneralMapLayer.KNOWN_LOCATIONS)
        )

        composeRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    MapScreenWithTrack(
                        viewModel = vm,
                        targetLocationId = 42L
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Selected location peek sheet is displayed
        composeRule.onNodeWithText("Home Trailhead").assertIsDisplayed()
        // No modal ClimbDetailSheet or unrelated route/segment peek content overlaps
        assertEquals(0, composeRule.onAllNodesWithText("Route").fetchSemanticsNodes().size)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testMapScreenWithTrack_selectedClimbPeeksCompactHeaderWithMeasuredHeight_tstUi2794_1() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        val climb = sampleClimb(505L)
        val vm = mockMapViewModel(
            uiState = MapFragmentUIState(
                climbs = listOf(climb),
                bSportType = BSportType.BIKE
            ),
            enabledLayers = setOf(GeneralMapLayer.CLIMBS)
        )

        var observedSelection: MapPeekSelection? = null
        var observedSheetValue: SheetValue? = null
        var observedMeasuredHeight: Dp? = null

        composeRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    MapScreenWithTrackContent(
                        viewModel = vm,
                        initialPeekSelection = MapPeekSelection.Climb(505L),
                        onStateObserved = { selection, sheetValue, measuredHeight ->
                            observedSelection = selection
                            observedSheetValue = sheetValue
                            observedMeasuredHeight = measuredHeight
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(MapPeekSelection.Climb(505L), observedSelection)
        assertEquals(
            "Selected Climb must open in PartiallyExpanded compact peek state, NOT Expanded fullscreen",
            SheetValue.PartiallyExpanded,
            observedSheetValue
        )
        assertNotNull("ClimbOnMapScreen must report measuredHeaderHeight", observedMeasuredHeight)
        assertTrue(
            "Measured Climb peek header height ($observedMeasuredHeight) must be a compact header (100..280dp)",
            observedMeasuredHeight!! in 100.dp..280.dp
        )

        // Compact header title & category chip render visibly in the bottom portion of the viewport (NOT fullscreen)
        val titleNode = composeRule.onNodeWithText("Kesselberg Süd")
        titleNode.assertIsDisplayed()
        val titleBounds = titleNode.getUnclippedBoundsInRoot()
        assertTrue(
            "Peeked Climb title must sit in the lower half of the viewport (top=${titleBounds.top}), not fullscreen top",
            titleBounds.top > 500.dp
        )

        composeRule.onNodeWithText(context.getString(R.string.climb_category_cat2)).assertIsDisplayed()

        // No fullscreen EntityDetailSheetScaffold modal close button should be rendered
        assertEquals(
            "MapScreenWithTrack must not open modal EntityDetailSheetScaffold close button for Climb peek",
            0,
            composeRule.onAllNodesWithContentDescription(context.getString(R.string.Cancel)).fetchSemanticsNodes().size
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testMapScreenWithTrack_selectedClimbExpandsToDetailsAndDismissClearsSelection_tstUi2794_2() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        val climb = sampleClimb(505L)
        val vm = mockMapViewModel(
            uiState = MapFragmentUIState(
                climbs = listOf(climb),
                bSportType = BSportType.BIKE
            ),
            enabledLayers = setOf(GeneralMapLayer.CLIMBS)
        )

        var observedSelection: MapPeekSelection? = null
        var observedSheetValue: SheetValue? = null
        lateinit var scaffoldState: BottomSheetScaffoldState
        lateinit var coroutineScope: CoroutineScope

        composeRule.setContent {
            ATrainingTrackerTheme {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    scaffoldState = rememberBottomSheetScaffoldState(
                        bottomSheetState = rememberStandardBottomSheetState(skipHiddenState = false)
                    )
                    coroutineScope = rememberCoroutineScope()
                    MapScreenWithTrackContent(
                        viewModel = vm,
                        initialPeekSelection = MapPeekSelection.Climb(505L),
                        scaffoldStateOverride = scaffoldState,
                        onStateObserved = { selection, sheetValue, _ ->
                            observedSelection = selection
                            observedSheetValue = sheetValue
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Starts compactly peeked
        assertEquals(SheetValue.PartiallyExpanded, observedSheetValue)
        val peekTop = composeRule.onNodeWithText("Kesselberg Süd").getUnclippedBoundsInRoot().top
        assertTrue("Initial Climb peek top ($peekTop) must be in bottom half", peekTop > 500.dp)

        // 2. Expanding reveals full ClimbOnMapScreen details (map + Share FAB + elevation profile)
        composeRule.runOnIdle {
            coroutineScope.launch { scaffoldState.bottomSheetState.expand() }
        }
        composeRule.waitForIdle()

        assertEquals(SheetValue.Expanded, observedSheetValue)
        val expandedTop = composeRule.onNodeWithText("Kesselberg Süd").getUnclippedBoundsInRoot().top
        assertTrue("Expanded Climb title top ($expandedTop) must rise to top of viewport", expandedTop < 120.dp)
        composeRule.onNodeWithText(context.getString(R.string.graph_heading_elevation)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.share)).assertIsDisplayed()

        // 3. Dismissing sheet to Hidden clears MapPeekSelection.Climb and unmounts ClimbOnMapScreen
        composeRule.runOnIdle {
            coroutineScope.launch { scaffoldState.bottomSheetState.hide() }
        }
        composeRule.waitForIdle()

        assertNull("Hiding the Climb bottom sheet must clear MapPeekSelection.Climb", observedSelection)
        assertEquals(
            "Climb header must be completely unmounted after dismissal",
            0,
            composeRule.onAllNodesWithText("Kesselberg Süd").fetchSemanticsNodes().size
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun testMapScreenWithTrack_backPressDismissesSelectedClimbPeek_tstUi2794_2() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        val climb = sampleClimb(505L)
        val vm = mockMapViewModel(
            uiState = MapFragmentUIState(
                climbs = listOf(climb),
                bSportType = BSportType.BIKE
            ),
            enabledLayers = setOf(GeneralMapLayer.CLIMBS)
        )

        var observedSelection: MapPeekSelection? = null
        val backDispatcher = androidx.activity.OnBackPressedDispatcher()

        composeRule.setContent {
            val currentLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
            val backOwner = object : androidx.activity.OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher: androidx.activity.OnBackPressedDispatcher = backDispatcher
                override val lifecycle: androidx.lifecycle.Lifecycle = currentLifecycle
            }
            ATrainingTrackerTheme {
                CompositionLocalProvider(
                    LocalInspectionMode provides true,
                    androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides backOwner
                ) {
                    MapScreenWithTrackContent(
                        viewModel = vm,
                        initialPeekSelection = MapPeekSelection.Climb(505L),
                        onStateObserved = { selection, _, _ ->
                            observedSelection = selection
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(MapPeekSelection.Climb(505L), observedSelection)
        composeRule.onNodeWithText("Kesselberg Süd").assertIsDisplayed()

        composeRule.runOnUiThread {
            backDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertNull("System back press must clear MapPeekSelection.Climb", observedSelection)
        assertEquals(
            "Climb header must be unmounted after system back press",
            0,
            composeRule.onAllNodesWithText("Kesselberg Süd").fetchSemanticsNodes().size
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    @Config(sdk = [34], qualifiers = "w860dp-h840dp-xhdpi", application = Application::class)
    fun testCaptureMapClimbPeekLightAndDarkScreenshot_tstUi2794_3() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.getUnit() } returns MyUnits.METRIC

        val climb = sampleClimb(505L)
        val vm = mockMapViewModel(
            uiState = MapFragmentUIState(
                climbs = listOf(climb),
                bSportType = BSportType.BIKE
            ),
            enabledLayers = setOf(GeneralMapLayer.CLIMBS)
        )

        composeRule.setContent {
            Row(
                modifier = Modifier
                    .size(width = 840.dp, height = 820.dp)
                    .testTag("map_climb_peek_light_dark_root"),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        Box(modifier = Modifier.size(width = 412.dp, height = 820.dp)) {
                            MapScreenWithTrackContent(
                                viewModel = vm,
                                initialPeekSelection = MapPeekSelection.Climb(505L),
                                onMenuClick = {},
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        Box(modifier = Modifier.size(width = 412.dp, height = 820.dp)) {
                            MapScreenWithTrackContent(
                                viewModel = vm,
                                initialPeekSelection = MapPeekSelection.Climb(505L),
                                onMenuClick = {},
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val outFile = File(System.getProperty("att9553.climbPeekPngOut") ?: "/tmp/att9553_climb_peek_light_dark.png")
        outFile.parentFile?.mkdirs()
        val bitmap = composeRule.onNodeWithTag("map_climb_peek_light_dark_root").captureToImage().asAndroidBitmap()
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Rendered Climb peek Light/Dark PNG must be non-empty", outFile.length() > 1_000L)
    }
}
