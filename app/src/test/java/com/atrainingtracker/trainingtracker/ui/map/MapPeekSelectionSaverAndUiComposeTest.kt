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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.elevation.ElevationSource
import com.atrainingtracker.trainingtracker.repositories.KnownLocationItem
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral unit and Robolectric Compose UI tests for [MapPeekSelection], [MapPeekSelection.Saver],
 * and [MapScreenWithTrack] (`REQ-UI-2790.2`, `TST-UI-2790.2`, `ATT-6040`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
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

        // Verify sheet-backed vs modal Climb distinction
        assertTrue(MapPeekSelection.Segment(1L).isSheetBacked)
        assertTrue(MapPeekSelection.Route(1L).isSheetBacked)
        assertTrue(MapPeekSelection.Cluster(1L).isSheetBacked)
        assertTrue(MapPeekSelection.Location(1L).isSheetBacked)
        assertFalse(MapPeekSelection.Climb(1L).isSheetBacked)

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
        assertFalse(currentSelection!!.isSheetBacked)

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
        val uiStateFlow = MutableStateFlow(
            MapFragmentUIState(
                knownLocations = listOf(homeLocation),
                bSportType = BSportType.BIKE
            )
        )
        val vm = mockk<MapFragmentWithTrackViewModel>(relaxed = true)
        every { vm.uiState } returns uiStateFlow
        every { vm.currentLocation } returns MutableStateFlow(null)
        every { vm.liveSegments } returns MutableStateFlow(emptyList())
        every { vm.allRoutes } returns MutableStateFlow(emptyList())
        every { vm.allClusters } returns MutableStateFlow(emptyList())
        every { vm.enabledLayers } returns MutableStateFlow(setOf(GeneralMapLayer.KNOWN_LOCATIONS))

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
}
