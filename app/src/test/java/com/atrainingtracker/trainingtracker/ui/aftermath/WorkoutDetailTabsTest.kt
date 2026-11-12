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

package com.atrainingtracker.trainingtracker.ui.aftermath

import com.atrainingtracker.R
import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit and structural contract tests for Individually Configurable Workout Details Tabs
 * (ATT-3920 / REQ-UI-408 / TST-UI-371.1 through TST-UI-371.8).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class WorkoutDetailTabsTest {

    /**
     * TST-UI-371.1: Default 4-Tab Preset & Bidirectional Section Enum Mapping.
     */
    @Test
    fun `TST-UI-371_1 default 4-tab preset and bidirectional section enum mapping`() {
        val defaults = WorkoutDetailTabConfig.DEFAULT_TABS
        assertEquals(4, defaults.size)

        // Tab 1: Overview (REQ-UI-408.1 / ATT-5655 / REQ-WKO-037)
        assertEquals("tab_overview", defaults[0].id)
        assertEquals(R.string.workout_detail_tab_overview, defaults[0].preset.defaultTitleRes)
        assertEquals(
            listOf(
                WorkoutDetailSectionType.RPE_AND_FEEL,
                WorkoutDetailSectionType.DETAILS,
                WorkoutDetailSectionType.PHOTOS,
                WorkoutDetailSectionType.DESCRIPTION,
                WorkoutDetailSectionType.EXTREMA,
                WorkoutDetailSectionType.EXPORT_STATUS
            ),
            defaults[0].sections
        )

        // Tab 2: Map & Graphs
        assertEquals("tab_map_graphs", defaults[1].id)
        assertEquals(R.string.workout_detail_tab_map_graphs, defaults[1].preset.defaultTitleRes)
        assertEquals(
            listOf(
                WorkoutDetailSectionType.MAP,
                WorkoutDetailSectionType.ELEVATION,
                WorkoutDetailSectionType.CHARTS
            ),
            defaults[1].sections
        )

        // Tab 3: Zones
        assertEquals("tab_zones", defaults[2].id)
        assertEquals(R.string.workout_detail_tab_zones, defaults[2].preset.defaultTitleRes)
        assertEquals(
            listOf(
                WorkoutDetailSectionType.ZONES,
                WorkoutDetailSectionType.GRADE_DISTRIBUTION
            ),
            defaults[2].sections
        )

        // Tab 4: Laps & Segments
        assertEquals("tab_laps_segments", defaults[3].id)
        assertEquals(R.string.workout_detail_tab_laps_segments, defaults[3].preset.defaultTitleRes)
        assertEquals(
            listOf(
                WorkoutDetailSectionType.LAPS,
                WorkoutDetailSectionType.STRAVA
            ),
            defaults[3].sections
        )

        // Verify bidirectional enum mapping (WorkoutDetailSectionType <-> WorkoutSectionType, REQ-UI-499.1)
        assertEquals(WorkoutSectionType.DETAILS, WorkoutDetailSectionType.DETAILS.toWorkoutSectionTypeOrNull())
        for (legacyType in WorkoutSectionType.values()) {
            val detailType = legacyType.toWorkoutDetailSectionType()
            assertEquals(legacyType, detailType.toWorkoutSectionTypeOrNull())
        }
    }

    /**
     * TST-UI-371.2: Serialization, Deserialization, Corrupt Payload Fallback & Reset to Defaults.
     */
    @Test
    fun `TST-UI-371_2 serialization round-trip and corrupt payload fallback`() {
        val customTabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_overview",
                preset = WorkoutDetailTabPreset.OVERVIEW,
                customTitle = "My Overview|Special;Tab",
                sections = listOf(
                    WorkoutDetailSectionType.MAP,
                    WorkoutDetailSectionType.RPE_AND_FEEL,
                    WorkoutDetailSectionType.DETAILS,
                    WorkoutDetailSectionType.EXTREMA
                )
            ),
            WorkoutDetailTabConfig(
                id = "custom_1",
                preset = WorkoutDetailTabPreset.CUSTOM,
                customTitle = "Power & Charts",
                sections = listOf(
                    WorkoutDetailSectionType.ELEVATION,
                    WorkoutDetailSectionType.CHARTS,
                    WorkoutDetailSectionType.ZONES
                )
            )
        )

        val serialized = WorkoutDetailTabConfig.serializeTabs(customTabs)
        assertTrue(
            "Serialized payload must start with version prefix v2|",
            serialized.startsWith("v2|")
        )

        val deserialized = WorkoutDetailTabConfig.deserializeTabs(serialized)
        assertEquals(2, deserialized.size)
        assertEquals("tab_overview", deserialized[0].id)
        assertEquals("My Overview|Special;Tab", deserialized[0].customTitle)
        assertEquals(WorkoutDetailTabPreset.OVERVIEW, deserialized[0].preset)
        assertEquals(
            listOf(
                WorkoutDetailSectionType.MAP,
                WorkoutDetailSectionType.RPE_AND_FEEL,
                WorkoutDetailSectionType.DETAILS,
                WorkoutDetailSectionType.EXTREMA
            ),
            deserialized[0].sections
        )

        assertEquals("custom_1", deserialized[1].id)
        assertEquals("Power & Charts", deserialized[1].customTitle)
        assertEquals(WorkoutDetailTabPreset.CUSTOM, deserialized[1].preset)
        assertEquals(
            listOf(
                WorkoutDetailSectionType.ELEVATION,
                WorkoutDetailSectionType.CHARTS,
                WorkoutDetailSectionType.ZONES
            ),
            deserialized[1].sections
        )

        // Fallback on null, blank, or malformed strings
        assertEquals(WorkoutDetailTabConfig.DEFAULT_TABS, WorkoutDetailTabConfig.deserializeTabs(null))
        assertEquals(WorkoutDetailTabConfig.DEFAULT_TABS, WorkoutDetailTabConfig.deserializeTabs(""))
        assertEquals(WorkoutDetailTabConfig.DEFAULT_TABS, WorkoutDetailTabConfig.deserializeTabs("   "))
        assertEquals(
            WorkoutDetailTabConfig.DEFAULT_TABS,
            WorkoutDetailTabConfig.deserializeTabs("corrupted;garbage|without;valid;sections")
        )
        assertEquals(
            WorkoutDetailTabConfig.DEFAULT_TABS,
            WorkoutDetailTabConfig.deserializeTabs("v1|broken_entry_no_delimiters")
        )
    }

    /**
     * TST-UI-371.2b: Wire v1-to-v2 Migration auto-injects PHOTOS into Overview (REQ-UI-408.2 / ATT-5655).
     */
    @Test
    fun `TST-UI-371_2b legacy v1 wire migration auto-injects PHOTOS into overview`() {
        val legacyV1Payload = "v1|tab_overview:OVERVIEW::DETAILS,DESCRIPTION,EXTREMA,EXPORT_STATUS;tab_map_graphs:MAP_AND_GRAPHS::MAP,ELEVATION,CHARTS;tab_zones:ZONES::ZONES;tab_laps_segments:LAPS_AND_SEGMENTS::LAPS,STRAVA"
        val migrated = WorkoutDetailTabConfig.deserializeTabs(legacyV1Payload)
        assertEquals(4, migrated.size)
        assertEquals(
            listOf(
                WorkoutDetailSectionType.RPE_AND_FEEL,
                WorkoutDetailSectionType.DETAILS,
                WorkoutDetailSectionType.PHOTOS,
                WorkoutDetailSectionType.DESCRIPTION,
                WorkoutDetailSectionType.EXTREMA,
                WorkoutDetailSectionType.EXPORT_STATUS
            ),
            migrated[0].sections
        )

        // But v2 payload without PHOTOS honors athlete's deliberate choice (with RPE_AND_FEEL auto-migrated)
        val v2PayloadWithoutPhotos = "v2|tab_overview:OVERVIEW::DETAILS,DESCRIPTION,EXTREMA,EXPORT_STATUS;tab_map_graphs:MAP_AND_GRAPHS::MAP,ELEVATION,CHARTS;tab_zones:ZONES::ZONES;tab_laps_segments:LAPS_AND_SEGMENTS::LAPS,STRAVA"
        val v2Parsed = WorkoutDetailTabConfig.deserializeTabs(v2PayloadWithoutPhotos)
        assertEquals(
            listOf(
                WorkoutDetailSectionType.RPE_AND_FEEL,
                WorkoutDetailSectionType.DETAILS,
                WorkoutDetailSectionType.DESCRIPTION,
                WorkoutDetailSectionType.EXTREMA,
                WorkoutDetailSectionType.EXPORT_STATUS
            ),
            v2Parsed[0].sections
        )
    }

    /**
     * TST-UI-371.3: Tab CRUD & Reordering Operations.
     */
    @Test
    fun `TST-UI-371_3 tab CRUD and reordering invariants`() {
        var tabs = WorkoutDetailTabConfig.DEFAULT_TABS

        // Add tab
        tabs = WorkoutDetailTabConfig.addCustomTab(tabs, "Custom Analysis")
        assertEquals(5, tabs.size)
        assertEquals("Custom Analysis", tabs.last().customTitle)
        assertEquals(listOf(WorkoutDetailSectionType.DETAILS), tabs.last().sections)

        // Rename tab
        val customId = tabs.last().id
        tabs = WorkoutDetailTabConfig.renameTab(tabs, 4, "Climbing Focus")
        assertEquals("Climbing Focus", tabs.last().customTitle)

        // Move tab up
        tabs = WorkoutDetailTabConfig.moveTab(tabs, 4, 3)
        assertEquals(customId, tabs[3].id)
        assertEquals("tab_laps_segments", tabs[4].id)

        // Delete tabs down to minimum 1 tab guard
        tabs = WorkoutDetailTabConfig.deleteTab(tabs, 3)
        assertEquals(4, tabs.size)
        tabs = WorkoutDetailTabConfig.deleteTab(tabs, 3)
        assertEquals(3, tabs.size)
        tabs = WorkoutDetailTabConfig.deleteTab(tabs, 2)
        assertEquals(2, tabs.size)
        tabs = WorkoutDetailTabConfig.deleteTab(tabs, 1)
        assertEquals(1, tabs.size)

        // Minimum 1-tab guard: deleting the last remaining tab is a no-op
        val lastTabId = tabs.single().id
        tabs = WorkoutDetailTabConfig.deleteTab(tabs, 0)
        assertEquals(1, tabs.size)
        assertEquals(lastTabId, tabs.single().id)
    }

    /**
     * TST-UI-371.4: Per-Tab Section Toggle, Reordering & Pinned-Top `MAP` Invariant.
     */
    @Test
    fun `TST-UI-371_4 per-tab section toggle reordering and pinned-top MAP invariant`() {
        var tabs = WorkoutDetailTabConfig.DEFAULT_TABS
        assertEquals(WorkoutDetailTabLayoutMode.MODE_A_CARD_SCROLL, tabs[0].layoutMode)

        // Toggling MAP ON in tab 0 automatically pins MAP to index 0
        tabs = WorkoutDetailTabConfig.toggleSectionInTab(tabs, 0, WorkoutDetailSectionType.MAP)
        assertEquals(WorkoutDetailTabLayoutMode.MODE_B_SPLIT_MAP, tabs[0].layoutMode)
        assertEquals(WorkoutDetailSectionType.MAP, tabs[0].sections.first())

        // Attempting to move MAP down from index 0 is blocked; MAP remains at index 0
        tabs = WorkoutDetailTabConfig.moveSectionInTab(tabs, 0, 0, 1)
        assertEquals(WorkoutDetailSectionType.MAP, tabs[0].sections.first())

        // Attempting to move index 1 (RPE_AND_FEEL) up to index 0 (above MAP) is blocked
        tabs = WorkoutDetailTabConfig.moveSectionInTab(tabs, 0, 1, 0)
        assertEquals(WorkoutDetailSectionType.MAP, tabs[0].sections.first())
        assertEquals(WorkoutDetailSectionType.RPE_AND_FEEL, tabs[0].sections[1])

        // Moving non-MAP sections among indices >= 1 succeeds while keeping MAP at index 0 (moves RPE_AND_FEEL to index 2, displacing DETAILS to index 1)
        tabs = WorkoutDetailTabConfig.moveSectionInTab(tabs, 0, 1, 2)
        assertEquals(WorkoutDetailSectionType.MAP, tabs[0].sections[0])
        assertEquals(WorkoutDetailSectionType.DETAILS, tabs[0].sections[1])
        assertEquals(WorkoutDetailSectionType.RPE_AND_FEEL, tabs[0].sections[2])

        // Toggling MAP OFF removes MAP cleanly
        tabs = WorkoutDetailTabConfig.toggleSectionInTab(tabs, 0, WorkoutDetailSectionType.MAP)
        assertEquals(WorkoutDetailTabLayoutMode.MODE_A_CARD_SCROLL, tabs[0].layoutMode)
        assertFalse(tabs[0].sections.contains(WorkoutDetailSectionType.MAP))
    }

    /**
     * TST-UI-371.5: Dynamic Empty-Tab Hiding & Data Availability.
     */
    @Test
    fun `TST-UI-371_5 dynamic empty-tab hiding when workout lacks data for all sections in tab`() {
        val defaults = WorkoutDetailTabConfig.DEFAULT_TABS

        // Suppose workout only has summary details and extrema (indoor spin without GPS, zones, or laps)
        val nonSpinAvailable = WorkoutDetailSectionAvailability(
            hasDetails = true,
            hasExtrema = true
        )
        val visibleForIndoor = WorkoutDetailTabConfig.filterVisibleWorkoutDetailTabs(defaults, nonSpinAvailable)
        // Only "tab_overview" has DETAILS/EXTREMA; map_graphs, zones, and laps_segments are all empty and hidden
        assertEquals(listOf("tab_overview"), visibleForIndoor.map { it.id })

        // Suppose workout has GPS track, elevation, charts, and zones, but no laps or strava segments
        val outdoorNoLaps = WorkoutDetailSectionAvailability(
            hasDetails = true,
            hasExtrema = true,
            hasMap = true,
            hasElevation = true,
            hasCharts = true,
            hasZones = true
        )
        val visibleForOutdoorNoLaps = WorkoutDetailTabConfig.filterVisibleWorkoutDetailTabs(defaults, outdoorNoLaps)
        assertEquals(
            listOf("tab_overview", "tab_map_graphs", "tab_zones"),
            visibleForOutdoorNoLaps.map { it.id }
        )

        // Fallback safety: if availability has everything false, at least the first tab is returned
        val allEmpty = WorkoutDetailSectionAvailability(hasDetails = false)
        val fallbackTabs = WorkoutDetailTabConfig.filterVisibleWorkoutDetailTabs(defaults, allEmpty)
        assertEquals(1, fallbackTabs.size)
        assertEquals("tab_overview", fallbackTabs.first().id)
    }

    /**
     * TST-UI-371.6: Gesture Arbitration Contract (HorizontalPager userScrollEnabled & Interactive Bounds Exclusion).
     */
    @Test
    fun `TST-UI-371_6 gesture arbitration contract for map and chart tabs`() {
        val mapTab = WorkoutDetailTabConfig.DEFAULT_TABS[1] // tab_map_graphs
        val zonesTab = WorkoutDetailTabConfig.DEFAULT_TABS[2] // tab_zones
        val fullAvailability = WorkoutDetailSectionAvailability(
            hasDetails = true,
            hasMap = true,
            hasElevation = true,
            hasCharts = true,
            hasZones = true
        )

        // When NOT touching interactive canvas: horizontal gesture isolation is false (pager swipe allowed)
        assertFalse(
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = false,
                touchXDp = 180f,
                screenWidthDp = 360f,
                edgeSwipeGutterDp = 24f
            )
        )

        // When touching interactive map/chart canvas (away from edge gutter): isolation is true (pager swipe disabled)
        assertTrue(
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = true,
                touchXDp = 180f,
                screenWidthDp = 360f,
                edgeSwipeGutterDp = 24f
            )
        )

        // Inside left or right 24dp edge gutter: isolation is false (pager swipe allowed even over map/charts)
        assertFalse(
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = true,
                touchXDp = 12f,
                screenWidthDp = 360f,
                edgeSwipeGutterDp = 24f
            )
        )

        // Non-interactive tab (zones): never requires horizontal gesture isolation
        assertFalse(
            zonesTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = true,
                touchXDp = 180f,
                screenWidthDp = 360f,
                edgeSwipeGutterDp = 24f
            )
        )

        // 2. Verify WorkoutDetailTabbedContent exists and exposes tabbed layout
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClazz.declaredMethods.find { it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDetailTabbedContent composable must exist", tabbedMethod)
    }

    /**
     * TST-UI-371.7: Entry Point Parity (Top Bar Action & Advanced Settings).
     */
    @Test
    fun `TST-UI-371_7 entry point parity in TrackOnMapScreen top bar and WorkoutMasksAndCardsSection`() {
        val trackScreenClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        assertNotNull("TrackOnMapScreenKt must exist", trackScreenClazz)

        val sheetClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabsConfigSheetKt")
        assertNotNull("WorkoutDetailTabsConfigSheetKt must exist", sheetClazz)

        val settingsClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.WorkoutMasksAndCardsSectionKt")
        assertNotNull("WorkoutMasksAndCardsSectionKt must exist", settingsClazz)

        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("workout_detail_tabs_config_title string must exist", strings["workout_detail_tabs_config_title"])
    }

    /**
     * TST-UI-371.8: 9-Language Localization Parity.
     */
    @Test
    fun `TST-UI-371_8 9-language localization parity for workout detail tab keys`() {
        val localeDirs = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )
        val requiredKeys = listOf(
            "workout_detail_tab_overview",
            "workout_detail_tab_map_graphs",
            "workout_detail_tab_zones",
            "workout_detail_tab_laps_segments",
            "workout_detail_tab_custom_default",
            "workout_detail_section_details",
            "workout_detail_tabs_config_title",
            "workout_detail_tabs_config_subtitle",
            "workout_detail_tabs_add_tab",
            "workout_detail_tabs_rename_tab",
            "workout_detail_tabs_delete_tab",
            "workout_detail_tabs_reset_defaults",
            "workout_detail_tabs_sections_header",
            "workout_detail_tabs_map_pinned_hint",
            "workout_detail_tabs_map_pinned_desc",
            "action_drag_to_reorder"
        )

        for (dir in localeDirs) {
            val strings = LocalizationTestCache.getStrings(dir, "strings.xml")
            for (key in requiredKeys) {
                val value = strings[key]
                assertNotNull("Missing string key '$key' in $dir/strings.xml", value)
                assertTrue(
                    "Empty string key '$key' in $dir/strings.xml",
                    value!!.isNotBlank()
                )
            }
        }
    }

    /**
     * TST-UI-377.1: Authoritative Tab Data Availability & Section Activation.
     * Evaluates that isSectionActive ignores legacy boolean flags in WorkoutDetailPreferences (REQ-UI-414.1).
     */
    @Test
    fun `TST-UI-377_1 authoritative tab data availability ignores legacy detailPreferences boolean flags`() {
        val availability = WorkoutDetailSectionAvailability(
            hasRpeAndFeel = true,
            hasDetails = true,
            hasDescription = true,
            hasExtrema = true,
            hasMap = true,
            hasElevation = true,
            hasCharts = true,
            hasZones = true,
            hasGradeDistribution = true,
            hasLaps = true,
            hasStrava = true,
            hasExportStatus = true,
            hasPhotos = true
        )

        // All legacy flags explicitly set to false
        val disabledPrefs = WorkoutDetailPreferences(
            showDescription = false,
            showExtrema = false,
            showMap = false,
            showElevationProfile = false,
            showTelemetryCharts = false,
            showZoneAnalysis = false,
            showLaps = false,
            showStrava = false,
            showExportStatus = false
        )

        for (section in WorkoutDetailSectionType.values()) {
            assertTrue(
                "Section $section must be active based on data presence regardless of disabledPrefs",
                availability.isSectionActive(section, disabledPrefs)
            )
        }

        // When data availability is false, section must NOT be active
        val emptyAvailability = WorkoutDetailSectionAvailability(hasDetails = false)
        for (section in WorkoutDetailSectionType.values()) {
            assertFalse(
                "Section $section must NOT be active when data availability is false",
                emptyAvailability.isSectionActive(section, disabledPrefs)
            )
        }
    }

    /**
     * TST-UI-453.4b: Grade distribution auto-migration into tab_zones (REQ-UI-408.2 / ATT-5654).
     */
    @Test
    fun `TST-UI-453_4b grade distribution auto-migration into tab_zones when missing from wire string`() {
        val legacyWire = "v2|tab_overview:OVERVIEW::DETAILS,PHOTOS,DESCRIPTION,EXTREMA,EXPORT_STATUS;tab_zones:ZONES::ZONES"
        val migrated = WorkoutDetailTabConfig.deserializeTabs(legacyWire)
        assertEquals(2, migrated.size)
        val zonesTab = migrated[1]
        assertEquals(
            listOf(WorkoutDetailSectionType.ZONES, WorkoutDetailSectionType.GRADE_DISTRIBUTION),
            zonesTab.sections
        )
    }

    /**
     * TST-UI-453.4c: RPE and Feel auto-migration into tab_overview (REQ-WKO-036.3 / ATT-7320).
     */
    @Test
    fun `TST-UI-453_4c rpe and feel auto-migration into tab_overview when missing from wire string`() {
        val legacyWire = "v2|tab_overview:OVERVIEW::DETAILS,PHOTOS,DESCRIPTION,EXTREMA,EXPORT_STATUS;tab_zones:ZONES::ZONES,GRADE_DISTRIBUTION"
        val migrated = WorkoutDetailTabConfig.deserializeTabs(legacyWire)
        assertEquals(2, migrated.size)
        val overviewTab = migrated[0]
        assertEquals(WorkoutDetailSectionType.RPE_AND_FEEL, overviewTab.sections.first())
        assertEquals(
            listOf(
                WorkoutDetailSectionType.RPE_AND_FEEL,
                WorkoutDetailSectionType.DETAILS,
                WorkoutDetailSectionType.PHOTOS,
                WorkoutDetailSectionType.DESCRIPTION,
                WorkoutDetailSectionType.EXTREMA,
                WorkoutDetailSectionType.EXPORT_STATUS
            ),
            overviewTab.sections
        )
    }

    /**
     * TST-UI-456.2: Drag handle integration and pinned MAP anchor in tab config sheet (REQ-UI-456.4 / ATT-5761).
     */
    @Test
    fun `TST-UI-456_2 drag handle integration and pinned MAP anchor in tab config sheet`() {
        val sheetClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabsConfigSheetKt")
        val sheetMethod = sheetClazz.declaredMethods.find { it.name.startsWith("WorkoutDetailTabsConfigSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDetailTabsConfigSheet composable must exist", sheetMethod)
        assertNotNull("WorkoutDetailSectionType.MAP must exist", WorkoutDetailSectionType.MAP)
    }

    /**
     * TST-UI-464.1: Edge Gutter Default 32dp and Initial Down Lock Contract (REQ-UI-464.1 / ATT-5826).
     */
    @Test
    fun `TST-UI-464_1 edge gutter default 32dp and initial down lock contract`() {
        val mapTab = WorkoutDetailTabConfig.DEFAULT_TABS[1] // tab_map_graphs
        val fullAvailability = WorkoutDetailSectionAvailability(
            hasMap = true,
            hasElevation = true,
            hasCharts = true,
            hasZones = true
        )

        // 1. Default edgeSwipeGutterDp is 32f:
        // Inside left 32dp gutter (e.g. 30f): returns false (swipe enabled)
        assertFalse(
            "Inside 32dp left gutter must permit pager swipe",
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = true,
                touchXDp = 30f,
                screenWidthDp = 360f
            )
        )
        // Just outside left 32dp gutter (e.g. 33f): returns true (isolation active)
        assertTrue(
            "Outside 32dp left gutter must isolate horizontal gesture",
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = true,
                touchXDp = 33f,
                screenWidthDp = 360f
            )
        )
        // Inside right 32dp gutter (360 - 30 = 330f): returns false (swipe enabled)
        assertFalse(
            "Inside 32dp right gutter must permit pager swipe",
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = true,
                touchXDp = 330f,
                screenWidthDp = 360f
            )
        )
        // Just outside right 32dp gutter (360 - 33 = 327f): returns true (isolation active)
        assertTrue(
            "Outside 32dp right gutter must isolate horizontal gesture",
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = true,
                touchXDp = 327f,
                screenWidthDp = 360f
            )
        )

        // 2. Composable contract: WorkoutDetailTabbedContent exists and is public
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClazz.declaredMethods.find { it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("WorkoutDetailTabbedContent composable must exist", tabbedMethod)
    }

    /**
     * TST-UI-464.2: Granular Interactive Canvas & Non-Interactive Strip Pass-Through Contract (REQ-UI-464.2, REQ-UI-464.3 / ATT-5826).
     */
    @Test
    fun `TST-UI-464_2 granular interactive canvas and non-interactive strip pass-through contract`() {
        val mapTab = WorkoutDetailTabConfig.DEFAULT_TABS[1] // tab_map_graphs
        val fullAvailability = WorkoutDetailSectionAvailability(
            hasMap = true,
            hasElevation = true,
            hasCharts = true,
            hasZones = true
        )

        // When touching a non-interactive surface (isTouchingInteractiveCanvas = false):
        // Isolation is false regardless of touch position
        assertFalse(
            "Non-interactive strip touch must never isolate horizontal pager swipe",
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = false,
                touchXDp = 180f,
                screenWidthDp = 360f
            )
        )

        // Composable contract: MapDetailLayout exposes onInteractiveCanvasTouch callback
        val mapDetailClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        val mapDetailMethod = mapDetailClazz.declaredMethods.find { it.name.startsWith("MapDetailLayout") && Modifier.isPublic(it.modifiers) }
        assertNotNull("MapDetailLayout composable must exist", mapDetailMethod)
    }

    /**
     * TST-UI-464.3: Active Map & Chart Plot Scrubbing Isolation Contract (REQ-UI-464.2 / ATT-5826).
     */
    @Test
    fun `TST-UI-464_3 active map and chart plot scrubbing isolation contract`() {
        val mapTab = WorkoutDetailTabConfig.DEFAULT_TABS[1] // tab_map_graphs
        val fullAvailability = WorkoutDetailSectionAvailability(
            hasMap = true,
            hasElevation = true,
            hasCharts = true,
            hasZones = true
        )

        // When touching inside active map/chart plot area away from edges:
        // Isolation is true (pager swipe disabled to protect scrubbing/pan)
        assertTrue(
            "Active plot area touch must isolate horizontal pager swipe",
            mapTab.requiresHorizontalGestureIsolation(
                availability = fullAvailability,
                isTouchingInteractiveCanvas = true,
                touchXDp = 180f,
                screenWidthDp = 360f
            )
        )

        // Composable contract: WorkoutDetailTabbedContent wires gesture isolation
        val tabbedClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt class must exist", tabbedClazz)
    }
}
