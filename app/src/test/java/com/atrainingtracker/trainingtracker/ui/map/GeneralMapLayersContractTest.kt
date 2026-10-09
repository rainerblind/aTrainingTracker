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
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockKnownLocationsRepository.locationsFlow } returns MutableStateFlow(emptyList())
        every { mockBanalRepository.bSportType } returns MutableStateFlow(BSportType.UNKNOWN)
        every { mockBanalRepository.currentTrack } returns MutableStateFlow(emptyList())
        every { mockBanalRepository.currentLocation } returns MutableStateFlow(null)
        every { mockSegmentsRepository.allSegmentsWithPath } returns MutableStateFlow(emptyList())
        every { mockRoutesRepository.allRoutes } returns MutableStateFlow(emptyList())

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
            prefs = prefs
        )
    }

    @Test
    fun testGeneralMapLayer_enumEntries() {
        val entries = GeneralMapLayer.entries
        assertEquals("GeneralMapLayer must have exactly 4 entries", 4, entries.size)
        assertTrue(entries.contains(GeneralMapLayer.ROUTES))
        assertTrue(entries.contains(GeneralMapLayer.SEGMENTS))
        assertTrue(entries.contains(GeneralMapLayer.KNOWN_LOCATIONS))
        assertTrue(entries.contains(GeneralMapLayer.TRACK))
    }

    @Test
    fun testDefaultEnabledLayers_containsAllLayersWhenPrefsNull() = runTest {
        every { mockPrefs.getStringSet(MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS, null) } returns null

        val viewModel = createViewModel()

        val expected = GeneralMapLayer.entries.toSet()
        assertEquals("Default enabledLayers must contain all entries", expected, viewModel.enabledLayers.value)
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
                match { set -> !set.contains("ROUTES") && set.contains("SEGMENTS") && set.contains("TRACK") && set.contains("KNOWN_LOCATIONS") }
            )
            mockEditor.apply()
        }

        // Toggle ROUTES back on
        viewModel.toggleLayer(GeneralMapLayer.ROUTES)
        assertTrue("ROUTES must be re-enabled after toggle", GeneralMapLayer.ROUTES in viewModel.enabledLayers.value)
        verify {
            mockEditor.putStringSet(
                MapFragmentWithTrackViewModel.PREF_GENERAL_MAP_ENABLED_LAYERS,
                match { set -> set.contains("ROUTES") && set.contains("SEGMENTS") && set.contains("TRACK") && set.contains("KNOWN_LOCATIONS") }
            )
        }
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
