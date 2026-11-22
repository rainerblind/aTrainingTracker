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

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneTimeEntry
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.atrainingtracker.trainingtracker.ui.map.TelemetryChartKind
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
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
 * Behavioral Robolectric Compose UI tests verifying that [WorkoutDetailSectionType.CHARTS]
 * and [WorkoutDetailSectionType.ELEVATION] respect the user-configured `tab.normalizedSections`
 * order in both [WorkoutDetailTabLayoutMode.MODE_A_CARD_SCROLL] and
 * [WorkoutDetailTabLayoutMode.MODE_B_SPLIT_MAP] while preserving `MAP` split-pane pinning,
 * synchronized scrubbing, chart visibility toggles, and `HorizontalPager` gesture isolation
 * (`REQ-UI-2795` / `TST-UI-2795` / `ATT-9508`).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class WorkoutDetailSectionOrderUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleWorkoutData(): WorkoutData = WorkoutData(
        id = 9508L,
        finished = true,
        fileBaseName = "ride_9508.tcx",
        workoutName = "Alpine Climb & Tempo",
        sportId = 1L,
        sportName = "Road Bike",
        bSportType = BSportType.BIKE,
        startTimeS = 1_760_000_000L,
        formattedDate = "22.11.2026",
        formattedTime = "08:00",
        localDateTime = LocalDateTime.of(2026, 11, 22, 8, 0),
        equipmentName = "Road Bike",
        equipmentId = 1L,
        commute = false,
        trainer = false,
        mapPolyline = "",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 24_500.0,
        maxDisplacement = 12_000.0,
        activeTimeSec = 3600L,
        totalTimeSec = 3660L,
        avgSpeedMps = 8.5,
        ascentMeters = 620L,
        descentMeters = 620L,
        minAltitude = 510.0,
        maxAltitude = 890.0,
        description = "Structured alpine tempo intervals.",
        goal = null,
        method = null,
        stravaSportName = null,
        normalizedPowerWatts = 242.0,
        intensityFactor = 0.85,
        tss = 74.0,
        variabilityIndex = 1.05
    )

    private fun samplePath(): List<PathPoint> = List(25) { idx ->
        val frac = idx / 24.0
        val altitude = 520.0 + kotlin.math.sin(frac * Math.PI) * 340.0
        val speed = 6.5 + kotlin.math.cos(frac * Math.PI) * 2.5
        val hr = (132.0 + kotlin.math.sin(frac * Math.PI) * 36.0).toInt()
        PathPoint(
            distance = idx * 1000.0,
            latLng = LatLng(47.60 + idx * 0.004, 11.30 + idx * 0.004),
            altitude = altitude,
            timeSec = idx * 120L,
            speedMps = speed,
            hr = hr,
            power = 210 + (idx % 5) * 18
        )
    }

    private fun sampleHrZones(): ZoneDistributionData = ZoneDistributionData(
        totalActiveTimeSec = 3600L,
        entries = listOf(
            ZoneTimeEntry(1, R.string.zone_1_label, 360L, 10f, Color(0xFF4CAF50)),
            ZoneTimeEntry(2, R.string.zone_2_label, 1080L, 30f, Color(0xFF2196F3)),
            ZoneTimeEntry(3, R.string.zone_3_label, 1260L, 35f, Color(0xFFFFC107)),
            ZoneTimeEntry(4, R.string.zone_4_label, 720L, 20f, Color(0xFFFF9800)),
            ZoneTimeEntry(5, R.string.zone_5_label, 180L, 5f, Color(0xFFF44336))
        )
    )

    private fun fullAvailability(hasMap: Boolean): WorkoutDetailSectionAvailability =
        WorkoutDetailSectionAvailability(
            hasDetails = true,
            hasDescription = true,
            hasMap = hasMap,
            hasElevation = true,
            hasCharts = true,
            hasZones = true
        )

    // TST-UI-2795.1
    @Test
    fun modeA_rendersDetailsElevationZonesChartsInConfiguredOrder_tstUi2795_1() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val customTabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_custom_mode_a",
                preset = WorkoutDetailTabPreset.CUSTOM,
                customTitle = "Custom",
                sections = listOf(
                    WorkoutDetailSectionType.DETAILS,
                    WorkoutDetailSectionType.ELEVATION,
                    WorkoutDetailSectionType.ZONES,
                    WorkoutDetailSectionType.CHARTS
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorkoutDetailTabbedContent(
                            workoutData = sampleWorkoutData(),
                            tabs = customTabs,
                            availability = fullAvailability(hasMap = false),
                            detailPreferences = WorkoutDetailPreferences(),
                            tuningConfig = TuningConfig(enableCogganPowerMetrics = true),
                            activeScrubPath = samplePath(),
                            hasAltitudeData = true,
                            splitChartData = null,
                            selectedLapNr = null,
                            onSelectLapNr = {},
                            hrZoneDistribution = sampleHrZones(),
                            powerZoneDistribution = null,
                            effectiveHrZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            effectivePowerZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            onHrZoneDisplayModeChange = {},
                            onPowerZoneDisplayModeChange = {},
                            useStatusBarsPadding = false,
                            onClusterClick = null,
                            onEditWorkout = null,
                            headerActions = {},
                            onOpenTabsConfig = {},
                            mapContent = {},
                            mapOverlay = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val detailsTop = composeTestRule
            .onNodeWithText(context.getString(R.string.normalized_power_short))
            .getUnclippedBoundsInRoot().top
        val elevationTop = composeTestRule
            .onNodeWithTag("workout_detail_section_ELEVATION")
            .getUnclippedBoundsInRoot().top
        val zonesTop = composeTestRule
            .onNodeWithText(context.getString(R.string.aftermath_hr_zones_title))
            .getUnclippedBoundsInRoot().top
        val chartsTop = composeTestRule
            .onNodeWithTag("workout_detail_section_CHARTS")
            .getUnclippedBoundsInRoot().top

        assertTrue("Expected DETAILS ($detailsTop) above ELEVATION ($elevationTop)", detailsTop < elevationTop)
        assertTrue("Expected ELEVATION ($elevationTop) above ZONES ($zonesTop)", elevationTop < zonesTop)
        assertTrue("Expected ZONES ($zonesTop) above CHARTS ($chartsTop)", zonesTop < chartsTop)
    }

    // TST-UI-2795.1
    @Test
    fun modeA_rendersChartsBeforeElevationWhenConfigured_tstUi2795_1() {
        val customTabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_charts_first",
                preset = WorkoutDetailTabPreset.CUSTOM,
                customTitle = "Charts First",
                sections = listOf(
                    WorkoutDetailSectionType.CHARTS,
                    WorkoutDetailSectionType.ELEVATION
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorkoutDetailTabbedContent(
                            workoutData = sampleWorkoutData(),
                            tabs = customTabs,
                            availability = fullAvailability(hasMap = false),
                            detailPreferences = WorkoutDetailPreferences(),
                            tuningConfig = TuningConfig(),
                            activeScrubPath = samplePath(),
                            hasAltitudeData = true,
                            splitChartData = null,
                            selectedLapNr = null,
                            onSelectLapNr = {},
                            hrZoneDistribution = null,
                            powerZoneDistribution = null,
                            effectiveHrZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            effectivePowerZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            onHrZoneDisplayModeChange = {},
                            onPowerZoneDisplayModeChange = {},
                            useStatusBarsPadding = false,
                            onClusterClick = null,
                            onEditWorkout = null,
                            headerActions = {},
                            onOpenTabsConfig = {},
                            mapContent = {},
                            mapOverlay = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val chartsTop = composeTestRule
            .onNodeWithTag("workout_detail_section_CHARTS")
            .getUnclippedBoundsInRoot().top
        val elevationTop = composeTestRule
            .onNodeWithTag("workout_detail_section_ELEVATION")
            .getUnclippedBoundsInRoot().top

        assertTrue("Expected CHARTS ($chartsTop) above ELEVATION ($elevationTop)", chartsTop < elevationTop)
    }

    // TST-UI-2795.2
    @Test
    fun modeB_splitMapKeepsMapPinnedAndOrdersDetailsChartsZonesElevation_tstUi2795_2() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Intentionally place MAP in the middle to confirm MAP remains pinned in the top split pane
        // while lower scrollable sections render in exact configured order: DETAILS -> CHARTS -> ZONES -> ELEVATION.
        val customTabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_split_custom",
                preset = WorkoutDetailTabPreset.MAP_AND_GRAPHS,
                sections = listOf(
                    WorkoutDetailSectionType.DETAILS,
                    WorkoutDetailSectionType.CHARTS,
                    WorkoutDetailSectionType.MAP,
                    WorkoutDetailSectionType.ZONES,
                    WorkoutDetailSectionType.ELEVATION
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorkoutDetailTabbedContent(
                            workoutData = sampleWorkoutData(),
                            tabs = customTabs,
                            availability = fullAvailability(hasMap = true),
                            detailPreferences = WorkoutDetailPreferences(),
                            tuningConfig = TuningConfig(enableCogganPowerMetrics = true),
                            activeScrubPath = samplePath(),
                            hasAltitudeData = true,
                            splitChartData = null,
                            selectedLapNr = null,
                            onSelectLapNr = {},
                            hrZoneDistribution = sampleHrZones(),
                            powerZoneDistribution = null,
                            effectiveHrZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            effectivePowerZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            onHrZoneDisplayModeChange = {},
                            onPowerZoneDisplayModeChange = {},
                            useStatusBarsPadding = false,
                            onClusterClick = null,
                            onEditWorkout = null,
                            headerActions = {},
                            onOpenTabsConfig = {},
                            mapContent = {},
                            mapOverlay = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val detailsTop = composeTestRule
            .onNodeWithText(context.getString(R.string.normalized_power_short))
            .getUnclippedBoundsInRoot().top
        val chartsTop = composeTestRule
            .onNodeWithTag("workout_detail_section_CHARTS")
            .getUnclippedBoundsInRoot().top
        val zonesTop = composeTestRule
            .onNodeWithText(context.getString(R.string.aftermath_hr_zones_title))
            .getUnclippedBoundsInRoot().top
        val elevationTop = composeTestRule
            .onNodeWithTag("workout_detail_section_ELEVATION")
            .getUnclippedBoundsInRoot().top

        assertTrue("Expected DETAILS ($detailsTop) below top split map pane", detailsTop > 250.dp)
        assertTrue("Expected DETAILS ($detailsTop) above CHARTS ($chartsTop)", detailsTop < chartsTop)
        assertTrue("Expected CHARTS ($chartsTop) above ZONES ($zonesTop)", chartsTop < zonesTop)
        assertTrue("Expected ZONES ($zonesTop) above ELEVATION ($elevationTop)", zonesTop < elevationTop)
    }

    // TST-UI-2795.3
    @Test
    fun customOrderedSections_preserveScrubbingVisibilityTogglesAndPagerIsolation_tstUi2795_3() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var hiddenCharts by mutableStateOf(emptySet<TelemetryChartKind>())
        val tabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_custom",
                preset = WorkoutDetailTabPreset.CUSTOM,
                customTitle = "Telemetry",
                sections = listOf(
                    WorkoutDetailSectionType.DETAILS,
                    WorkoutDetailSectionType.ELEVATION,
                    WorkoutDetailSectionType.CHARTS
                )
            ),
            WorkoutDetailTabConfig(
                id = "tab_zones",
                preset = WorkoutDetailTabPreset.ZONES,
                sections = listOf(WorkoutDetailSectionType.ZONES)
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        WorkoutDetailTabbedContent(
                            workoutData = sampleWorkoutData(),
                            tabs = tabs,
                            availability = fullAvailability(hasMap = false),
                            detailPreferences = WorkoutDetailPreferences(),
                            tuningConfig = TuningConfig(enableCogganPowerMetrics = true),
                            activeScrubPath = samplePath(),
                            hasAltitudeData = true,
                            splitChartData = null,
                            selectedLapNr = null,
                            onSelectLapNr = {},
                            hrZoneDistribution = sampleHrZones(),
                            powerZoneDistribution = null,
                            effectiveHrZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            effectivePowerZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                            onHrZoneDisplayModeChange = {},
                            onPowerZoneDisplayModeChange = {},
                            useStatusBarsPadding = false,
                            onClusterClick = null,
                            onEditWorkout = null,
                            headerActions = {},
                            onOpenTabsConfig = {},
                            mapContent = {},
                            mapOverlay = {},
                            hiddenCharts = hiddenCharts,
                            onToggleChartVisibility = { kind ->
                                hiddenCharts = if (kind in hiddenCharts) hiddenCharts - kind else hiddenCharts + kind
                            }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val pagerScrollEnabledMatcher = SemanticsMatcher("HorizontalPager ScrollBy enabled") { node ->
            node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null &&
                node.config.getOrNull(SemanticsActions.ScrollBy) != null &&
                node.config.getOrNull(SemanticsActions.PageRight) != null
        }

        // 1. Touching DETAILS card keeps HorizontalPager swipe enabled (not locked)
        composeTestRule.onNodeWithText(context.getString(R.string.normalized_power_short)).performTouchInput {
            down(center)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(pagerScrollEnabledMatcher).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.normalized_power_short)).performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        // 2. Touching & scrubbing inside ELEVATION plot locks HorizontalPager and triggers synchronized ScrubbingTelemetryBadge
        composeTestRule.onNodeWithTag("workout_detail_section_ELEVATION").performTouchInput {
            val midPlot = Offset(width * 0.5f, height * 0.65f)
            down(midPlot)
            moveTo(Offset(width * 0.62f, height * 0.65f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(pagerScrollEnabledMatcher).assertDoesNotExist()
        composeTestRule.onNodeWithTag("scrubbing_telemetry_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workout_detail_section_ELEVATION").performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(pagerScrollEnabledMatcher).assertExists()

        // 3. Chart visibility toggle menu still hides individual charts cleanly
        composeTestRule.onNodeWithTag("telemetry_chart_section_SPEED_PACE").assertExists()
        composeTestRule.onNodeWithTag("charts_visibility_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("charts_visibility_menu_item_SPEED_PACE").performClick()
        composeTestRule.waitForIdle()
        assertTrue(TelemetryChartKind.SPEED_PACE in hiddenCharts)
        composeTestRule.onNodeWithTag("telemetry_chart_section_SPEED_PACE").assertDoesNotExist()
        composeTestRule.onNodeWithTag("telemetry_chart_section_HEART_RATE").assertExists()
    }

    // TST-UI-2795.4 (Rule 8.5 Visual Self-Inspection Light & Dark/AMOLED PNG)
    @Test
    @Config(sdk = [34], qualifiers = "w860dp-h960dp-xxhdpi")
    fun captureCustomOrderedWorkoutDetailsLightAndDarkScreenshot_tstUi2795_4() {
        val customTabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_custom_order",
                preset = WorkoutDetailTabPreset.CUSTOM,
                customTitle = "Details → Elevation → Charts",
                sections = listOf(
                    WorkoutDetailSectionType.DETAILS,
                    WorkoutDetailSectionType.ELEVATION,
                    WorkoutDetailSectionType.CHARTS
                )
            ),
            WorkoutDetailTabConfig(
                id = "tab_zones",
                preset = WorkoutDetailTabPreset.ZONES,
                sections = listOf(WorkoutDetailSectionType.ZONES)
            )
        )
        val workoutData = sampleWorkoutData()
        val path = samplePath()
        val hrZones = sampleHrZones()

        composeTestRule.setContent {
            Row(modifier = Modifier.testTag("att9508_custom_order_light_dark_root")) {
                listOf(false, true).forEach { isDark ->
                    ATrainingTrackerTheme(darkTheme = isDark) {
                        Surface {
                            CompositionLocalProvider(LocalInspectionMode provides true) {
                                Box(modifier = Modifier.size(width = 412.dp, height = 915.dp)) {
                                    WorkoutDetailTabbedContent(
                                        workoutData = workoutData,
                                        tabs = customTabs,
                                        availability = fullAvailability(hasMap = false),
                                        detailPreferences = WorkoutDetailPreferences(),
                                        tuningConfig = TuningConfig(enableCogganPowerMetrics = true),
                                        activeScrubPath = path,
                                        hasAltitudeData = true,
                                        splitChartData = null,
                                        selectedLapNr = null,
                                        onSelectLapNr = {},
                                        hrZoneDistribution = hrZones,
                                        powerZoneDistribution = null,
                                        effectiveHrZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                                        effectivePowerZoneDisplayMode = ZoneCardDisplayMode.FIVE_ZONES,
                                        onHrZoneDisplayModeChange = {},
                                        onPowerZoneDisplayModeChange = {},
                                        useStatusBarsPadding = false,
                                        onClusterClick = null,
                                        onEditWorkout = null,
                                        headerActions = {},
                                        onOpenTabsConfig = {},
                                        mapContent = {},
                                        mapOverlay = {}
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val outFile = File(
            System.getProperty("att9508.customOrderPngOut")
                ?: "/tmp/att9508_custom_order_light_dark.png"
        )
        outFile.parentFile?.mkdirs()
        val bitmap = composeTestRule
            .onNodeWithTag("att9508_custom_order_light_dark_root")
            .captureToImage()
            .asAndroidBitmap()
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Rendered Light/Dark PNG must be non-empty", outFile.length() > 1_000L)
    }
}
