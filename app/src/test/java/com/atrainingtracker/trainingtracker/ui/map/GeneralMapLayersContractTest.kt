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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.ui.map

import android.app.Application
import android.content.SharedPreferences
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Architectural contract test for General Map Layer Visibility Controls & Dynamic Decluttering Menu
 * (REQ-UI-322, TST-UI-282, ATT-2931).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GeneralMapLayersContractTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockApplication: Application
    private lateinit var mockKnownLocationsRepository: KnownLocationsRepository
    private lateinit var mockBanalRepository: BANALServiceRepository
    private lateinit var mockSegmentsRepository: SegmentsRepository
    private lateinit var mockRoutesRepository: RoutesRepository
    private lateinit var mockWorkoutClusterRepository: WorkoutClusterRepository
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mockk(relaxed = true)
        mockKnownLocationsRepository = mockk(relaxed = true)
        mockBanalRepository = mockk(relaxed = true)
        mockSegmentsRepository = mockk(relaxed = true)
        mockRoutesRepository = mockk(relaxed = true)
        mockWorkoutClusterRepository = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockKnownLocationsRepository.locationsFlow } returns MutableStateFlow(emptyList())
        every { mockBanalRepository.bSportType } returns MutableStateFlow(BSportType.UNKNOWN)
        every { mockBanalRepository.currentTrack } returns MutableStateFlow(emptyList())
        every { mockBanalRepository.currentLocation } returns MutableStateFlow(null)
        every { mockSegmentsRepository.allSegmentsWithPath } returns MutableStateFlow(emptyList())
        every { mockRoutesRepository.allRoutes } returns MutableStateFlow(emptyList())
        every { mockRoutesRepository.activeNavigatedRouteId } returns MutableStateFlow(null)
        every { mockWorkoutClusterRepository.allClusters } returns MutableStateFlow(emptyList())
        every { mockWorkoutClusterRepository.clusterStats } returns MutableStateFlow(emptyMap())

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putStringSet(any(), any()) } returns mockEditor
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    private fun createViewModel(prefs: SharedPreferences? = mockPrefs): MapFragmentWithTrackViewModel {
        return MapFragmentWithTrackViewModel(
            application = mockApplication,
            knownLocationsRepository = mockKnownLocationsRepository,
            banalRepo = mockBanalRepository,
            segmentsRepo = mockSegmentsRepository,
            routesRepo = mockRoutesRepository,
            workoutClusterRepo = mockWorkoutClusterRepository,
            prefs = prefs,
            defaultDispatcher = testDispatcher
        )
    }

    @Test
    fun testGeneralMapLayer_enumEntries() {
        val entries = GeneralMapLayer.entries
        assertEquals("GeneralMapLayer must have exactly 5 entries", 5, entries.size)
        assertTrue(entries.contains(GeneralMapLayer.ROUTES))
        assertTrue(entries.contains(GeneralMapLayer.SEGMENTS))
        assertTrue(entries.contains(GeneralMapLayer.KNOWN_LOCATIONS))
        assertTrue(entries.contains(GeneralMapLayer.TRACK))
        assertTrue(entries.contains(GeneralMapLayer.CLUSTERS))
    }

    @Test
    fun testGeneralMapLayer_defaultEnabledEntries() {
        val defaults = GeneralMapLayer.DEFAULT_ENABLED
        assertEquals("DEFAULT_ENABLED must have exactly 4 entries", 4, defaults.size)
        assertTrue(defaults.contains(GeneralMapLayer.ROUTES))
        assertTrue(defaults.contains(GeneralMapLayer.SEGMENTS))
        assertTrue(defaults.contains(GeneralMapLayer.KNOWN_LOCATIONS))
        assertTrue(defaults.contains(GeneralMapLayer.TRACK))
        assertFalse("DEFAULT_ENABLED must strictly exclude CLUSTERS (REQ-UI-322.1, ATT-5074)", defaults.contains(GeneralMapLayer.CLUSTERS))
    }

    @Test
    fun testDefaultEnabledLayers_excludesClustersWhenPrefsNull() = runTest {
        every { mockPrefs.getStringSet(MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS, null) } returns null

        val viewModel = createViewModel()

        val expected = GeneralMapLayer.DEFAULT_ENABLED
        assertEquals("Default enabledLayers must equal DEFAULT_ENABLED (excluding CLUSTERS)", expected, viewModel.enabledLayers.value)
        assertFalse("CLUSTERS must be disabled by default", GeneralMapLayer.CLUSTERS in viewModel.enabledLayers.value)
    }

    @Test
    fun testToggleLayer_persistsAndUpdatesFlow() = runTest {
        every { mockPrefs.getStringSet(MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS, null) } returns null

        val viewModel = createViewModel()
        assertTrue("Initially ROUTES is enabled", GeneralMapLayer.ROUTES in viewModel.enabledLayers.value)

        // Toggle ROUTES off
        viewModel.toggleLayer(GeneralMapLayer.ROUTES)
        assertFalse("ROUTES must be disabled after toggle", GeneralMapLayer.ROUTES in viewModel.enabledLayers.value)
        verify {
            mockEditor.putStringSet(
                MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS,
                match { set -> !set.contains("ROUTES") && set.contains("SEGMENTS") && set.contains("TRACK") && set.contains("KNOWN_LOCATIONS") && !set.contains("CLUSTERS") }
            )
            mockEditor.apply()
        }

        // Toggle ROUTES back on
        viewModel.toggleLayer(GeneralMapLayer.ROUTES)
        assertTrue("ROUTES must be re-enabled after toggle", GeneralMapLayer.ROUTES in viewModel.enabledLayers.value)
        verify {
            mockEditor.putStringSet(
                MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS,
                match { set -> set.contains("ROUTES") && set.contains("SEGMENTS") && set.contains("TRACK") && set.contains("KNOWN_LOCATIONS") && !set.contains("CLUSTERS") }
            )
        }
    }

    @Test
    fun testToggleClusterLayer_persistsAndUpdatesFlow() = runTest {
        every { mockPrefs.getStringSet(MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS, null) } returns null

        val viewModel = createViewModel()
        assertFalse("Initially CLUSTERS is disabled by default (REQ-UI-322.2, ATT-5074)", GeneralMapLayer.CLUSTERS in viewModel.enabledLayers.value)

        // Toggle CLUSTERS on
        viewModel.toggleLayer(GeneralMapLayer.CLUSTERS)
        assertTrue("CLUSTERS must be enabled after toggle", GeneralMapLayer.CLUSTERS in viewModel.enabledLayers.value)
        verify {
            mockEditor.putStringSet(
                MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS,
                match { set -> set.contains("CLUSTERS") && set.contains("ROUTES") && set.contains("SEGMENTS") && set.contains("TRACK") && set.contains("KNOWN_LOCATIONS") }
            )
            mockEditor.apply()
        }

        // Toggle CLUSTERS back off
        viewModel.toggleLayer(GeneralMapLayer.CLUSTERS)
        assertFalse("CLUSTERS must be disabled after second toggle", GeneralMapLayer.CLUSTERS in viewModel.enabledLayers.value)
        verify {
            mockEditor.putStringSet(
                MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS,
                match { set -> !set.contains("CLUSTERS") && set.contains("ROUTES") && set.contains("SEGMENTS") && set.contains("TRACK") && set.contains("KNOWN_LOCATIONS") }
            )
        }
    }

    @Test
    fun testMapClustersFlow_gatedByClusterLayer() = runTest {
        val testCluster = WorkoutCluster(
            id = 42L,
            name = "Morning Ridge",
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 9.0,
            endLat = 48.1,
            endLng = 9.1,
            maxDispLat = 48.2,
            maxDispLng = 9.2,
            refDistance = 5000.0,
            hitCount = 10,
            bSportType = BSportType.BIKE,
            routePolyline = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        )
        val clustersFlow = MutableStateFlow(listOf(testCluster))
        every { mockWorkoutClusterRepository.allClusters } returns clustersFlow
        every { mockPrefs.getStringSet(MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS, null) } returns null

        val viewModel = createViewModel()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        testDispatcher.scheduler.advanceUntilIdle()

        // Gated: By default CLUSTERS is disabled, so uiState.clusters must be empty
        assertEquals("Clusters must be empty when layer is disabled", emptyList<MapCluster>(), viewModel.uiState.value.clusters)

        // Toggle CLUSTERS on
        viewModel.toggleLayer(GeneralMapLayer.CLUSTERS)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Clusters must populate when layer is enabled", 1, viewModel.uiState.value.clusters.size)
        assertEquals("Morning Ridge", viewModel.uiState.value.clusters.first().name)
    }

    @Test
    fun testLoadEnabledLayers_fromExistingPreferences() = runTest {
        every {
            mockPrefs.getStringSet(MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS, null)
        } returns setOf("ROUTES", "TRACK")

        val viewModel = createViewModel()

        val expected = setOf(GeneralMapLayer.ROUTES, GeneralMapLayer.TRACK)
        assertEquals(expected, viewModel.enabledLayers.value)
    }

    @Test
    fun testLoadEnabledLayers_ignoresUnknownEntries() = runTest {
        every {
            mockPrefs.getStringSet(MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS, null)
        } returns setOf("ROUTES", "UNKNOWN_FUTURE_LAYER")

        val viewModel = createViewModel()

        val expected = setOf(GeneralMapLayer.ROUTES)
        assertEquals(expected, viewModel.enabledLayers.value)
    }

    @Test
    fun testMapScreenWithTrack_structuralVerification() {
        val file = findSourceFile("ui/map/MapScreenWithTrack.kt")
        assertTrue("MapScreenWithTrack.kt must exist", file.exists())
        val content = file.readText()

        assertTrue("Must reference GeneralMapLayer", content.contains("GeneralMapLayer"))
        assertTrue("Must reference Icons.Default.Layers", content.contains("Icons.Default.Layers"))
        assertTrue("Must apply statusBarsPadding()", content.contains("statusBarsPadding()"))
        assertTrue("Must render DropdownMenu", content.contains("DropdownMenu("))
        assertTrue("Must render DropdownMenuItem", content.contains("DropdownMenuItem("))
        assertTrue("Must render Checkbox", content.contains("Checkbox("))

        // Gating conditions in canvas
        assertTrue(
            "Must gate knownLocations with GeneralMapLayer.KNOWN_LOCATIONS in enabledLayers",
            content.contains("GeneralMapLayer.KNOWN_LOCATIONS in enabledLayers")
        )
        assertTrue(
            "Must gate segments with GeneralMapLayer.SEGMENTS in enabledLayers",
            content.contains("GeneralMapLayer.SEGMENTS in enabledLayers")
        )
        assertTrue(
            "Must gate routes with GeneralMapLayer.ROUTES in enabledLayers",
            content.contains("GeneralMapLayer.ROUTES in enabledLayers")
        )
        assertTrue(
            "Must gate track with GeneralMapLayer.TRACK in enabledLayers",
            content.contains("GeneralMapLayer.TRACK in enabledLayers")
        )
        assertTrue(
            "Must gate clusters with GeneralMapLayer.CLUSTERS in enabledLayers",
            content.contains("GeneralMapLayer.CLUSTERS in enabledLayers")
        )
    }

    @Test
    fun testClusterOnMapSheet_harmonizedLayoutAndColorTokens_TST_UI_282_5() {
        val file = findSourceFile("ui/map/MapScreenWithTrack.kt")
        assertTrue("MapScreenWithTrack.kt must exist", file.exists())
        val content = file.readText()

        // Peek height calibration for cluster with dynamic measurement (REQ-UI-322.12, ATT-5196)
        assertTrue(
            "Cluster peek height must use dynamic measuredClusterHeaderHeight fallback to BottomSheetDesign.PeekHeightWorkout",
            content.contains("measuredClusterHeaderHeight ?: BottomSheetDesign.PeekHeightWorkout")
        )

        // ClusterOnMapSheet composable contract (REQ-UI-322.11, ATT-5196)
        val sheetDefIndex = content.indexOf("fun ClusterOnMapSheet(")
        assertTrue("ClusterOnMapSheet composable must be defined", sheetDefIndex > 0)
        val sheetContent = content.substring(sheetDefIndex)

        // Must wrap in MapDetailLayout with useStatusBarsPadding = false
        assertTrue("Must use MapDetailLayout", sheetContent.contains("MapDetailLayout("))
        assertTrue("Must specify useStatusBarsPadding = false", sheetContent.contains("useStatusBarsPadding = false"))
        assertTrue("Must wire onHeaderHeightMeasured", sheetContent.contains("onHeaderHeightMeasured = onHeaderHeightMeasured"))

        // Must reuse canonical WorkoutClusterIdentityRow and WorkoutClusterMetadataBlock in header (Design Guidelines Section 5.15)
        assertTrue("Must use canonical WorkoutClusterIdentityRow", sheetContent.contains("WorkoutClusterIdentityRow("))
        assertTrue("Must use canonical WorkoutClusterMetadataBlock", sheetContent.contains("WorkoutClusterMetadataBlock("))

        // Must host WorkoutClusterDetailDashboard in metadataContent
        assertTrue("Must host WorkoutClusterDetailDashboard in metadataContent", sheetContent.contains("WorkoutClusterDetailDashboard("))

        // Must eliminate TTColor.Cluster text and icon tinting
        assertFalse("ClusterOnMapSheet must not use TTColor.Cluster", sheetContent.contains("TTColor.Cluster"))
    }

    @Test
    fun testClusterOnMapSheet_usesMapDetailLayoutAndCanonicalComponents_TST_UI_282_14() {
        // TST-UI-282.14 contract verification
        testClusterOnMapSheet_harmonizedLayoutAndColorTokens_TST_UI_282_5()
    }

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/$relativePath"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/$relativePath"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found in candidates for: $relativePath")
    }
}
