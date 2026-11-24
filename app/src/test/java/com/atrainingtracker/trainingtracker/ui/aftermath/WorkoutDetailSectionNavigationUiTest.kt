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

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummary
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneTimeEntry
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.util.NumericalEncodingUtils
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/**
 * Behavioral Robolectric Compose UI tests verifying summary-section-to-detail-tab navigation
 * (`REQ-UI-2805` / `TST-UI-2805.2` / `ATT-8721`):
 * 1. `WorkoutSummary` propagates tapped `WorkoutSectionType` via `onSectionClick`.
 * 2. `WorkoutDetailTabbedContent` resolves renamed & reordered tabs dynamically by section membership.
 * 3. Deleted-tab and unavailable-section targets fall back safely to tab `0`.
 * 4. Async DataStore tab config & aftermath telemetry loading resolves the target tab once ready.
 * 5. Single-page mode (`visibleTabs.size == 1`) and multi-section tabs scroll the target section into view.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WorkoutDetailSectionNavigationUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleWorkoutData(): WorkoutData = WorkoutData(
        id = 8721L,
        finished = true,
        fileBaseName = "ride_8721.tcx",
        workoutName = "Evening Endurance Ride",
        sportId = 1L,
        sportName = "Road Bike",
        bSportType = BSportType.BIKE,
        startTimeS = 1_760_000_000L,
        formattedDate = "23.11.2026",
        formattedTime = "18:00",
        localDateTime = LocalDateTime.of(2026, 11, 23, 18, 0),
        equipmentName = "Road Bike",
        equipmentId = 1L,
        commute = false,
        trainer = false,
        mapPolyline = "",
        encodedAltitudes = NumericalEncodingUtils.encodeDoubles(listOf(500.0, 540.0, 580.0)),
        encodedDistances = NumericalEncodingUtils.encodeDoubles(listOf(0.0, 5000.0, 10000.0)),
        uploadToStrava = 0,
        totalDistance = 28_400.0,
        maxDisplacement = 11_000.0,
        activeTimeSec = 3600L,
        totalTimeSec = 3660L,
        avgSpeedMps = 7.89,
        ascentMeters = 420L,
        descentMeters = 415L,
        minAltitude = 500.0,
        maxAltitude = 680.0,
        description = "Steady aerobic endurance loop.",
        goal = "Maintain Z2 power",
        method = "Continuous",
        stravaSportName = null,
        rpe = 6,
        feel = 4,
        extremaRows = listOf(
            ExtremaDataRow(
                sensorLabel = "Heart Rate",
                unitLabel = "bpm",
                minValue = "112",
                avgValue = "144",
                maxValue = "171"
            ),
            ExtremaDataRow(
                sensorLabel = "Power",
                unitLabel = "W",
                minValue = "95",
                avgValue = "218",
                maxValue = "410"
            )
        ),
        laps = listOf(
            LapData(
                id = 1L,
                workoutId = 8721L,
                lapNr = 1L,
                timeTotalS = 1800,
                distanceTotalM = 14200.0,
                speedAverageMps = 7.88
            ),
            LapData(
                id = 2L,
                workoutId = 8721L,
                lapNr = 2L,
                timeTotalS = 1800,
                distanceTotalM = 14200.0,
                speedAverageMps = 7.90
            )
        )
    )

    private fun sampleHrZones(): ZoneDistributionData = ZoneDistributionData(
        totalActiveTimeSec = 3600L,
        entries = listOf(
            ZoneTimeEntry(1, R.string.zone_1_label, 360L, 10f, Color(0xFF4CAF50)),
            ZoneTimeEntry(2, R.string.zone_2_label, 1440L, 40f, Color(0xFF2196F3)),
            ZoneTimeEntry(3, R.string.zone_3_label, 1080L, 30f, Color(0xFFFFC107)),
            ZoneTimeEntry(4, R.string.zone_4_label, 540L, 15f, Color(0xFFFF9800)),
            ZoneTimeEntry(5, R.string.zone_5_label, 180L, 5f, Color(0xFFF44336))
        )
    )

    private fun samplePath(): List<PathPoint> = List(15) { idx ->
        PathPoint(
            distance = idx * 1000.0,
            latLng = LatLng(47.60 + idx * 0.002, 11.30 + idx * 0.002),
            altitude = 500.0 + idx * 12.0,
            timeSec = idx * 120L,
            speedMps = 7.5,
            hr = 140 + idx,
            power = 210 + idx * 3
        )
    }

    @Test
    fun `TST-UI-2805_2 workoutSummary propagates tapped sectionType via onSectionClick`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val workout = sampleWorkoutData()
        val clickedSections = mutableListOf<WorkoutSectionType>()
        var headerClicks = 0

        val prefs = WorkoutCardSectionPreferences(
            showRpeAndFeel = true,
            showDetails = true,
            showDescription = true,
            showExtrema = true,
            showLaps = true,
            showElevationProfile = true,
            showMapPreview = false,
            showTelemetryCharts = false,
            showZoneAnalysis = false,
            showGradeDistribution = false,
            showStrava = false,
            showExportStatus = false,
            showPhotos = false
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Column(
                        modifier = Modifier
                            .width(412.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        WorkoutSummary(
                            workoutData = workout,
                            isPlayServiceAvailable = false,
                            onExport = {},
                            onSaveAsRoute = {},
                            onDeleteRequest = {},
                            onEditWorkout = {},
                            onMapClick = { headerClicks++ },
                            onSectionClick = { clickedSections.add(it) },
                            preferences = prefs,
                            workoutSectionsOrder = listOf(
                                WorkoutSectionType.RPE_AND_FEEL,
                                WorkoutSectionType.DETAILS,
                                WorkoutSectionType.DESCRIPTION,
                                WorkoutSectionType.EXTREMA,
                                WorkoutSectionType.LAPS,
                                WorkoutSectionType.ELEVATION
                            )
                        )
                    }
                }
            }
        }

        // Tapping specific sections invokes onSectionClick with matching WorkoutSectionType
        composeTestRule.onNodeWithTag("workout_summary_section_RPE_AND_FEEL").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("workout_summary_section_DETAILS").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("workout_summary_section_DESCRIPTION").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("workout_summary_section_EXTREMA").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("workout_summary_section_ELEVATION").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("workout_summary_section_LAPS").performScrollTo().performClick()

        assertEquals(
            listOf(
                WorkoutSectionType.RPE_AND_FEEL,
                WorkoutSectionType.DETAILS,
                WorkoutSectionType.DESCRIPTION,
                WorkoutSectionType.EXTREMA,
                WorkoutSectionType.ELEVATION,
                WorkoutSectionType.LAPS
            ),
            clickedSections
        )
        assertEquals(0, headerClicks)

        // Tapping the WorkoutHeader triggers onMapClick fallback (general tab 0 navigation)
        composeTestRule.onNodeWithText("Evening Endurance Ride").performScrollTo().performClick()
        assertEquals(1, headerClicks)
    }

    @Test
    fun `TST-UI-2805_2 renamed and reordered tabs open matching tab regardless of title or index`() {
        val workout = sampleWorkoutData()
        val reorderedRenamedTabs = listOf(
            WorkoutDetailTabConfig(
                id = "custom_laps_first",
                preset = WorkoutDetailTabPreset.CUSTOM,
                customTitle = "Splits First",
                sections = listOf(WorkoutDetailSectionType.LAPS)
            ),
            WorkoutDetailTabConfig(
                id = "custom_overview_second",
                preset = WorkoutDetailTabPreset.OVERVIEW,
                customTitle = "Renamed Info",
                sections = listOf(WorkoutDetailSectionType.DETAILS, WorkoutDetailSectionType.EXTREMA)
            ),
            WorkoutDetailTabConfig(
                id = "custom_zones_third",
                preset = WorkoutDetailTabPreset.ZONES,
                customTitle = "Renamed Effort",
                sections = listOf(WorkoutDetailSectionType.ZONES)
            )
        )
        val availability = WorkoutDetailSectionAvailability(
            hasDetails = true,
            hasExtrema = true,
            hasZones = true,
            hasLaps = true
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    WorkoutDetailTabbedContent(
                        workoutData = workout,
                        tabs = reorderedRenamedTabs,
                        availability = availability,
                        detailPreferences = WorkoutDetailPreferences(),
                        tuningConfig = TuningConfig(),
                        activeScrubPath = null,
                        hasAltitudeData = false,
                        splitChartData = null,
                        selectedLapNr = null,
                        onSelectLapNr = {},
                        hrZoneDistribution = sampleHrZones(),
                        powerZoneDistribution = null,
                        effectiveHrZoneDisplayMode = ZoneCardDisplayMode.COLUMNS,
                        effectivePowerZoneDisplayMode = ZoneCardDisplayMode.COLUMNS,
                        onHrZoneDisplayModeChange = {},
                        onPowerZoneDisplayModeChange = {},
                        useStatusBarsPadding = false,
                        onClusterClick = null,
                        onEditWorkout = null,
                        headerActions = {},
                        onOpenTabsConfig = {},
                        mapContent = {},
                        mapOverlay = {},
                        initialSection = WorkoutDetailSectionType.ZONES
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Tab 2 ("Renamed Effort") must be selected, NOT Tab 0 ("Splits First")
        composeTestRule.onNodeWithText("Renamed Effort").assertIsSelected()
        composeTestRule.onNodeWithText("Splits First").assertIsNotSelected()
        composeTestRule.onNodeWithTag("workout_detail_section_ZONES").assertIsDisplayed()
    }

    @Test
    fun `TST-UI-2805_2 deleted tab and unavailable section fall back safely to tab zero`() {
        val workout = sampleWorkoutData()
        // User deleted the Zones tab; only Laps (index 0) and Details (index 1) remain
        val tabsWithoutZones = listOf(
            WorkoutDetailTabConfig(
                id = "tab_laps",
                preset = WorkoutDetailTabPreset.LAPS_AND_SEGMENTS,
                customTitle = "Splits Only",
                sections = listOf(WorkoutDetailSectionType.LAPS)
            ),
            WorkoutDetailTabConfig(
                id = "tab_details",
                preset = WorkoutDetailTabPreset.OVERVIEW,
                customTitle = "Info Only",
                sections = listOf(WorkoutDetailSectionType.DETAILS, WorkoutDetailSectionType.EXTREMA)
            )
        )
        val availability = WorkoutDetailSectionAvailability(
            hasDetails = true,
            hasExtrema = false,
            hasZones = false,
            hasLaps = true
        )

        var targetSection by mutableStateOf<WorkoutDetailSectionType?>(WorkoutDetailSectionType.ZONES)

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    WorkoutDetailTabbedContent(
                        workoutData = workout,
                        tabs = tabsWithoutZones,
                        availability = availability,
                        detailPreferences = WorkoutDetailPreferences(),
                        tuningConfig = TuningConfig(),
                        activeScrubPath = null,
                        hasAltitudeData = false,
                        splitChartData = null,
                        selectedLapNr = null,
                        onSelectLapNr = {},
                        hrZoneDistribution = null,
                        powerZoneDistribution = null,
                        effectiveHrZoneDisplayMode = ZoneCardDisplayMode.COLUMNS,
                        effectivePowerZoneDisplayMode = ZoneCardDisplayMode.COLUMNS,
                        onHrZoneDisplayModeChange = {},
                        onPowerZoneDisplayModeChange = {},
                        useStatusBarsPadding = false,
                        onClusterClick = null,
                        onEditWorkout = null,
                        headerActions = {},
                        onOpenTabsConfig = {},
                        mapContent = {},
                        mapOverlay = {},
                        initialSection = targetSection
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Deleted Zones tab falls back cleanly to tab 0 ("Splits Only")
        composeTestRule.onNodeWithText("Splits Only").assertIsSelected()

        // Unavailable EXTREMA (hasExtrema = false) also falls back cleanly to tab 0
        composeTestRule.runOnUiThread {
            targetSection = WorkoutDetailSectionType.EXTREMA
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Splits Only").assertIsSelected()
    }

    @Test
    fun `TST-UI-2805_2 async tabConfig and sectionData loading resolves target tab once ready`() {
        val workout = sampleWorkoutData()
        var tabsState by mutableStateOf(WorkoutDetailTabConfig.DEFAULT_TABS)
        var availabilityState by mutableStateOf(WorkoutDetailSectionAvailability(hasDetails = true))
        var isTabsReady by mutableStateOf(false)
        var isDataLoading by mutableStateOf(true)
        var hrZonesState by mutableStateOf<ZoneDistributionData?>(null)

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    WorkoutDetailTabbedContent(
                        workoutData = workout,
                        tabs = tabsState,
                        availability = availabilityState,
                        detailPreferences = WorkoutDetailPreferences(),
                        tuningConfig = TuningConfig(),
                        activeScrubPath = null,
                        hasAltitudeData = false,
                        splitChartData = null,
                        selectedLapNr = null,
                        onSelectLapNr = {},
                        hrZoneDistribution = hrZonesState,
                        powerZoneDistribution = null,
                        effectiveHrZoneDisplayMode = ZoneCardDisplayMode.COLUMNS,
                        effectivePowerZoneDisplayMode = ZoneCardDisplayMode.COLUMNS,
                        onHrZoneDisplayModeChange = {},
                        onPowerZoneDisplayModeChange = {},
                        useStatusBarsPadding = false,
                        onClusterClick = null,
                        onEditWorkout = null,
                        headerActions = {},
                        onOpenTabsConfig = {},
                        mapContent = {},
                        mapOverlay = {},
                        initialSection = WorkoutDetailSectionType.ZONES,
                        isTabsConfigReady = isTabsReady,
                        isSectionDataLoading = isDataLoading
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Simulate async DataStore tab emission (reordered/renamed) + ViewModel telemetry completion
        composeTestRule.runOnUiThread {
            tabsState = listOf(
                WorkoutDetailTabConfig("t0", WorkoutDetailTabPreset.CUSTOM, "Alpha", listOf(WorkoutDetailSectionType.DETAILS)),
                WorkoutDetailTabConfig("t1", WorkoutDetailTabPreset.CUSTOM, "Beta", listOf(WorkoutDetailSectionType.LAPS)),
                WorkoutDetailTabConfig("t2", WorkoutDetailTabPreset.CUSTOM, "Custom Zones", listOf(WorkoutDetailSectionType.ZONES))
            )
            availabilityState = WorkoutDetailSectionAvailability(
                hasDetails = true,
                hasLaps = true,
                hasZones = true
            )
            hrZonesState = sampleHrZones()
            isTabsReady = true
            isDataLoading = false
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Custom Zones").assertIsSelected()
        composeTestRule.onNodeWithTag("workout_detail_section_ZONES").assertIsDisplayed()
    }

    @Test
    fun `TST-UI-2805_2 singlePageMode scrolls target section into view without tab bar`() {
        val workout = sampleWorkoutData()
        val singlePageTabs = listOf(
            WorkoutDetailTabConfig(
                id = "single_page",
                preset = WorkoutDetailTabPreset.CUSTOM,
                customTitle = "All Sections",
                sections = listOf(
                    WorkoutDetailSectionType.RPE_AND_FEEL,
                    WorkoutDetailSectionType.DETAILS,
                    WorkoutDetailSectionType.DESCRIPTION,
                    WorkoutDetailSectionType.EXTREMA,
                    WorkoutDetailSectionType.ZONES,
                    WorkoutDetailSectionType.LAPS
                )
            )
        )
        val availability = WorkoutDetailSectionAvailability(
            hasRpeAndFeel = true,
            hasDetails = true,
            hasDescription = true,
            hasExtrema = true,
            hasZones = true,
            hasLaps = true
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                // Constrained viewport height forces ZONES below the initial fold
                Box(modifier = Modifier.size(width = 412.dp, height = 360.dp)) {
                    WorkoutDetailTabbedContent(
                        workoutData = workout,
                        tabs = singlePageTabs,
                        availability = availability,
                        detailPreferences = WorkoutDetailPreferences(),
                        tuningConfig = TuningConfig(
                            enableCogganPowerMetrics = true,
                            enableAerobicEfficiencyModel = true,
                            enableCriticalPowerModel = true
                        ),
                        activeScrubPath = samplePath(),
                        hasAltitudeData = false,
                        splitChartData = null,
                        selectedLapNr = null,
                        onSelectLapNr = {},
                        hrZoneDistribution = sampleHrZones(),
                        powerZoneDistribution = null,
                        effectiveHrZoneDisplayMode = ZoneCardDisplayMode.COLUMNS,
                        effectivePowerZoneDisplayMode = ZoneCardDisplayMode.COLUMNS,
                        onHrZoneDisplayModeChange = {},
                        onPowerZoneDisplayModeChange = {},
                        useStatusBarsPadding = false,
                        onClusterClick = null,
                        onEditWorkout = null,
                        headerActions = {},
                        onOpenTabsConfig = {},
                        mapContent = {},
                        mapOverlay = {},
                        initialSection = WorkoutDetailSectionType.ZONES
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Tab bar is suppressed in single-page mode (visibleTabs.size == 1)
        composeTestRule.onNodeWithText("All Sections").assertDoesNotExist()
        // Single-page container automatically scrolled down to bring ZONES into view
        composeTestRule.onNodeWithTag("workout_detail_section_ZONES").assertIsDisplayed()
    }
}
