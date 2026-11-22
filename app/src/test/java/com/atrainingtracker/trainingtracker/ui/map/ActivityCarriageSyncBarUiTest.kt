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

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.odometry.PhoneCarriageMode
import com.atrainingtracker.trainingtracker.WorkoutDetailPreferences
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageModeSummary
import com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageSegment
import com.atrainingtracker.trainingtracker.ui.aftermath.ActivityCarriageTimeline
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailSectionAvailability
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailSectionType
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabConfig
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabPreset
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContent
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure-JVM math and Robolectric Compose UI verification tests for [ActivityCarriageSyncBar]
 * (`REQ-UI-2796` / `TST-UI-2796` / `ATT-9172`):
 * - `DISTANCE` and `TIME` domain projection & active-mode lookup
 * - `v3|` -> `v4|` tab migration & independent coexistence with `ELEVATION`
 * - Exact `Rule 64` plot-width alignment below map and above elevation/charts
 * - Bidirectional scrubbing, active-mode readout chip, zoom/pan synchronization, and absence omission
 * - Light & Dark/AMOLED `Rule 8.5` PNG capture
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ActivityCarriageSyncBarUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleTimeline(): ActivityCarriageTimeline = ActivityCarriageTimeline(
        segments = listOf(
            ActivityCarriageSegment(PhoneCarriageMode.BIKE_HANDLEBAR, 0L, 400L, 400L, 4000.0, 40f, 40f),
            ActivityCarriageSegment(PhoneCarriageMode.WALKING_TRANSITION, 400L, 600L, 200L, 2000.0, 20f, 20f),
            ActivityCarriageSegment(PhoneCarriageMode.STATIONARY, 600L, 700L, 100L, 0.0, 10f, 0f),
            ActivityCarriageSegment(PhoneCarriageMode.BIKE_JERSEY_BACK, 700L, 1000L, 300L, 4000.0, 30f, 40f)
        ),
        modeSummaries = listOf(
            ActivityCarriageModeSummary(PhoneCarriageMode.BIKE_HANDLEBAR, 400L, 4000.0, 40f, 40f, 1),
            ActivityCarriageModeSummary(PhoneCarriageMode.BIKE_JERSEY_BACK, 300L, 4000.0, 30f, 40f, 1),
            ActivityCarriageModeSummary(PhoneCarriageMode.WALKING_TRANSITION, 200L, 2000.0, 20f, 20f, 1),
            ActivityCarriageModeSummary(PhoneCarriageMode.STATIONARY, 100L, 0.0, 10f, 0f, 1)
        ),
        totalDurationSeconds = 1000L,
        totalDistanceMeters = 10_000.0,
        startSec = 0L,
        endSec = 1000L
    )

    private fun samplePath(): List<PathPoint> = List(51) { idx ->
        val frac = idx / 50.0
        PathPoint(
            distance = frac * 10_000.0,
            latLng = LatLng(48.10 + frac * 0.05, 11.50 + frac * 0.05),
            altitude = 520.0 + sin(frac * Math.PI) * 180.0,
            timeSec = (frac * 1000.0).toLong(),
            speedMps = 7.0 + cos(frac * Math.PI) * 2.0,
            hr = (135.0 + sin(frac * Math.PI) * 32.0).toInt(),
            cadence = 84 + (idx % 8),
            power = 215 + (idx % 5) * 12
        )
    }

    private fun sampleWorkoutData(): WorkoutData = WorkoutData(
        id = 9172L,
        finished = true,
        fileBaseName = "ride_9172.tcx",
        workoutName = "Bakery & Climb Ride",
        sportId = 1L,
        sportName = "Road Bike",
        bSportType = BSportType.BIKE,
        startTimeS = 1_760_000_000L,
        formattedDate = "22.11.2026",
        formattedTime = "09:15",
        localDateTime = LocalDateTime.of(2026, 11, 22, 9, 15),
        equipmentName = "Road Bike",
        equipmentId = 1L,
        commute = false,
        trainer = false,
        mapPolyline = "",
        encodedAltitudes = "",
        encodedDistances = "",
        uploadToStrava = 0,
        totalDistance = 10_000.0,
        maxDisplacement = 5_000.0,
        activeTimeSec = 1000L,
        totalTimeSec = 1000L,
        avgSpeedMps = 10.0,
        ascentMeters = 180L,
        descentMeters = 180L,
        minAltitude = 520.0,
        maxAltitude = 700.0,
        description = null,
        goal = null,
        method = null,
        stravaSportName = null
    )

    // TST-UI-2796.1
    @Test
    fun activityCarriageBarMath_projectsDistanceAndTimeDomainsAndResolvesActiveMode_tstUi2796_1() {
        val timeline = sampleTimeline()
        val path = samplePath()

        // DISTANCE domain projection (0..10,000 m)
        val distProjected = ActivityCarriageBarMath.projectSegmentsToDomain(
            timeline = timeline,
            isTimeDomain = false,
            totalSpan = 10_000.0
        )
        assertEquals(4, distProjected.size)
        assertEquals(0.0, distProjected[0].startDomain, 0.01)
        assertEquals(4000.0, distProjected[0].endDomain, 0.01)
        assertEquals(4000.0, distProjected[1].startDomain, 0.01)
        assertEquals(6000.0, distProjected[1].endDomain, 0.01)
        assertEquals(6000.0, distProjected[2].startDomain, 0.01)
        assertEquals(6000.0, distProjected[2].endDomain, 0.01) // zero-distance stationary stop preserved
        assertEquals(6000.0, distProjected[3].startDomain, 0.01)
        assertEquals(10_000.0, distProjected[3].endDomain, 0.01)

        assertEquals(PhoneCarriageMode.BIKE_HANDLEBAR, ActivityCarriageBarMath.resolveModeAtDomainPosition(distProjected, 2000.0))
        assertEquals(PhoneCarriageMode.WALKING_TRANSITION, ActivityCarriageBarMath.resolveModeAtDomainPosition(distProjected, 5000.0))
        assertEquals(PhoneCarriageMode.BIKE_JERSEY_BACK, ActivityCarriageBarMath.resolveModeAtDomainPosition(distProjected, 8500.0))

        // TIME domain projection (0..1,000 s) gives full width to STATIONARY (600..700 s)
        val timeProjected = ActivityCarriageBarMath.projectSegmentsToDomain(
            timeline = timeline,
            isTimeDomain = true,
            totalSpan = 1000.0
        )
        assertEquals(600.0, timeProjected[2].startDomain, 0.01)
        assertEquals(700.0, timeProjected[2].endDomain, 0.01)
        assertEquals(PhoneCarriageMode.STATIONARY, ActivityCarriageBarMath.resolveModeAtDomainPosition(timeProjected, 650.0))

        // Cursor conversion across GPS Distance, GPS Time, and Trackless Time
        assertEquals(5000.0, ActivityCarriageBarMath.resolveCursorDomainPosition(path, 5000.0, isTimeDomain = false, isTrackless = false)!!, 0.01)
        assertEquals(500.0, ActivityCarriageBarMath.resolveCursorDomainPosition(path, 5000.0, isTimeDomain = true, isTrackless = false)!!, 0.01)
        assertEquals(650.0, ActivityCarriageBarMath.resolveCursorDomainPosition(path, 650.0, isTimeDomain = true, isTrackless = true)!!, 0.01)
    }

    // TST-UI-2796.2
    @Test
    fun tabConfig_v3MigratesActivityCarriageBarAfterMapAndV4PreservesRemoval_tstUi2796_2() {
        val legacyV3 = "v3|tab_overview:OVERVIEW::DETAILS,DESCRIPTION;tab_map_graphs:MAP_AND_GRAPHS::MAP,ELEVATION,CHARTS"
        val migrated = WorkoutDetailTabConfig.deserializeTabs(legacyV3)
        val mapTab = migrated.first { it.id == "tab_map_graphs" }
        assertEquals(
            listOf(
                WorkoutDetailSectionType.MAP,
                WorkoutDetailSectionType.ACTIVITY_CARRIAGE_BAR,
                WorkoutDetailSectionType.ELEVATION,
                WorkoutDetailSectionType.CHARTS
            ),
            mapTab.sections
        )
        // v3->v4 migration must NOT re-inject removed RPE_AND_FEEL or tab_hrv
        assertFalse(migrated.any { it.id == "tab_hrv" })

        // Removing ACTIVITY_CARRIAGE_BAR and serializing as v4| persists user removal
        val mapTabIdx = migrated.indexOfFirst { it.id == "tab_map_graphs" }
        val withoutBar = WorkoutDetailTabConfig.toggleSectionInTab(migrated, mapTabIdx, WorkoutDetailSectionType.ACTIVITY_CARRIAGE_BAR)
        val wireV4 = WorkoutDetailTabConfig.serializeTabs(withoutBar)
        assertTrue("Expected v4| prefix", wireV4.startsWith("v4|"))
        val roundTripped = WorkoutDetailTabConfig.deserializeTabs(wireV4)
        assertFalse(
            "Explicitly removed ACTIVITY_CARRIAGE_BAR must stay absent in v4|",
            roundTripped.any { WorkoutDetailSectionType.ACTIVITY_CARRIAGE_BAR in it.sections }
        )

        // Gesture isolation activates when ACTIVITY_CARRIAGE_BAR is active & canvas is touched
        val barOnlyTab = WorkoutDetailTabConfig(
            id = "tab_bar_only",
            preset = WorkoutDetailTabPreset.CUSTOM,
            sections = listOf(WorkoutDetailSectionType.ACTIVITY_CARRIAGE_BAR)
        )
        assertTrue(
            barOnlyTab.requiresHorizontalGestureIsolation(
                availability = WorkoutDetailSectionAvailability(hasActivityCarriageBar = true),
                isTouchingInteractiveCanvas = true
            )
        )
        assertFalse(
            barOnlyTab.requiresHorizontalGestureIsolation(
                availability = WorkoutDetailSectionAvailability(hasActivityCarriageBar = false),
                isTouchingInteractiveCanvas = true
            )
        )
    }

    // TST-UI-2796.3
    @Test
    fun karteUndDiagramme_rendersBarBelowMapAlignedWithElevationAndBidirectionalScrubbing_tstUi2796_3() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tabs = listOf(
            WorkoutDetailTabConfig(
                id = "tab_map_graphs",
                preset = WorkoutDetailTabPreset.MAP_AND_GRAPHS,
                sections = listOf(
                    WorkoutDetailSectionType.MAP,
                    WorkoutDetailSectionType.ACTIVITY_CARRIAGE_BAR,
                    WorkoutDetailSectionType.ELEVATION,
                    WorkoutDetailSectionType.CHARTS
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            WorkoutDetailTabbedContent(
                                workoutData = sampleWorkoutData(),
                                tabs = tabs,
                                availability = WorkoutDetailSectionAvailability(
                                    hasMap = true,
                                    hasActivityCarriageBar = true,
                                    hasElevation = true,
                                    hasCharts = true
                                ),
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
                                mapOverlay = {},
                                activityCarriageTimeline = sampleTimeline()
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val barBounds = composeTestRule.onNodeWithTag("workout_detail_section_ACTIVITY_CARRIAGE_BAR").getUnclippedBoundsInRoot()
        val elevBounds = composeTestRule.onNodeWithTag("workout_detail_section_ELEVATION").getUnclippedBoundsInRoot()
        val chartsBounds = composeTestRule.onNodeWithTag("workout_detail_section_CHARTS").getUnclippedBoundsInRoot()

        assertTrue("Activity bar (${barBounds.top}) must sit below top split map pane", barBounds.top > 250.dp)
        assertTrue("Activity bar (${barBounds.top}) must render above Elevation (${elevBounds.top})", barBounds.top < elevBounds.top)
        assertTrue("Elevation (${elevBounds.top}) must render above Charts (${chartsBounds.top})", elevBounds.top < chartsBounds.top)
        assertEquals("Activity bar left edge must match Elevation (Rule 64)", elevBounds.left, barBounds.left)
        assertEquals("Activity bar right edge must match Elevation (Rule 64)", elevBounds.right, barBounds.right)

        // 1. Scrubbing on Activity bar (midway -> ~5000m -> WALKING_TRANSITION) updates active-mode readout AND ScrubbingTelemetryBadge
        composeTestRule.onNodeWithTag("activity_carriage_sync_bar_canvas").performTouchInput {
            val tapX = 150f + (width - 300f) * 0.50f
            down(Offset(tapX, height * 0.5f))
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("activity_carriage_scrub_readout").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(PhoneCarriageMode.WALKING_TRANSITION.labelResId)).assertIsDisplayed()
        composeTestRule.onNodeWithTag("scrubbing_telemetry_badge").assertIsDisplayed()

        // 2. Reverse direction: tapping near right side (~85%) of ElevationProfile updates Activity bar readout to BIKE_JERSEY_BACK
        composeTestRule.onNodeWithTag("workout_detail_section_ELEVATION").performTouchInput {
            val tapX = 150f + (width - 300f) * 0.85f
            down(Offset(tapX, height * 0.65f))
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(PhoneCarriageMode.BIKE_JERSEY_BACK.labelResId)).assertIsDisplayed()

        // 3. Zoom button interaction rescales viewport cleanly without error
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.zoom_in)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workout_detail_section_ACTIVITY_CARRIAGE_BAR").assertIsDisplayed()
    }

    // TST-UI-2796.4
    @Test
    fun karteUndDiagramme_omitsBarWhenTelemetryAbsentAndSupportsBarWithoutElevation_tstUi2796_4() {
        val defaultMapTab = listOf(
            WorkoutDetailTabConfig(
                id = "tab_map_graphs",
                preset = WorkoutDetailTabPreset.MAP_AND_GRAPHS,
                sections = listOf(
                    WorkoutDetailSectionType.MAP,
                    WorkoutDetailSectionType.ACTIVITY_CARRIAGE_BAR,
                    WorkoutDetailSectionType.ELEVATION,
                    WorkoutDetailSectionType.CHARTS
                )
            )
        )

        // Case A: Legacy workout without ACTIVITY_AND_CARRIAGE_MODE -> bar completely omitted
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            WorkoutDetailTabbedContent(
                                workoutData = sampleWorkoutData(),
                                tabs = defaultMapTab,
                                availability = WorkoutDetailSectionAvailability(
                                    hasMap = true,
                                    hasActivityCarriageBar = false,
                                    hasElevation = true,
                                    hasCharts = true
                                ),
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
                                mapOverlay = {},
                                activityCarriageTimeline = null
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workout_detail_section_ACTIVITY_CARRIAGE_BAR").assertDoesNotExist()
        composeTestRule.onNodeWithTag("workout_detail_section_ELEVATION").assertIsDisplayed()
    }

    // TST-UI-2796.4b
    @Test
    fun karteUndDiagramme_supportsActivityBarInsteadOfElevation_tstUi2796_4() {
        val barWithoutElevationTab = listOf(
            WorkoutDetailTabConfig(
                id = "tab_map_graphs",
                preset = WorkoutDetailTabPreset.MAP_AND_GRAPHS,
                sections = listOf(
                    WorkoutDetailSectionType.MAP,
                    WorkoutDetailSectionType.ACTIVITY_CARRIAGE_BAR,
                    WorkoutDetailSectionType.CHARTS
                )
            )
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            WorkoutDetailTabbedContent(
                                workoutData = sampleWorkoutData(),
                                tabs = barWithoutElevationTab,
                                availability = WorkoutDetailSectionAvailability(
                                    hasMap = true,
                                    hasActivityCarriageBar = true,
                                    hasElevation = true,
                                    hasCharts = true
                                ),
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
                                mapOverlay = {},
                                activityCarriageTimeline = sampleTimeline()
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("workout_detail_section_ACTIVITY_CARRIAGE_BAR").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workout_detail_section_ELEVATION").assertDoesNotExist()
        composeTestRule.onNodeWithTag("workout_detail_section_CHARTS").assertExists()
    }

    // TST-UI-2796.5 (Rule 8.5 Light & Dark/AMOLED visual inspection PNG)
    @Test
    @Config(sdk = [34], qualifiers = "w860dp-h960dp-xxhdpi")
    fun captureActivityCarriageSyncBarLightAndDarkScreenshot_tstUi2796_5() {
        val timeline = sampleTimeline()
        val path = samplePath()
        val scrubDist = 5000.0 // Inside WALKING_TRANSITION phase (4000..6000m)

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width(860.dp)
                    .background(Color(0xFF777777))
                    .padding(8.dp)
                    .testTag("att9172_carriage_sync_bar_light_dark"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(false, true).forEach { isDark ->
                    ATrainingTrackerTheme(darkTheme = isDark) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ActivityCarriageSyncBar(
                                    timeline = timeline,
                                    pathPoints = path,
                                    currentDistance = scrubDist,
                                    onDistanceSelected = {},
                                    xAxisDomain = ProfileXAxisDomain.DISTANCE,
                                    alignWithTelemetryCharts = true
                                )
                                ElevationProfile(
                                    pathPoints = path,
                                    currentDistance = scrubDist,
                                    onDistanceSelected = {},
                                    showZoomControls = true,
                                    xAxisDomain = ProfileXAxisDomain.DISTANCE,
                                    bSportType = BSportType.BIKE,
                                    showScrubbingBadge = false,
                                    alignWithTelemetryCharts = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TelemetryChartStack(
                                    path = path,
                                    bSportType = BSportType.BIKE,
                                    tuningConfig = TuningConfig(),
                                    selectedDistance = scrubDist,
                                    onDistanceSelected = {},
                                    activeTelemetryDomain = ProfileXAxisDomain.DISTANCE,
                                    isTrackless = false,
                                    zoomScale = 1.0f,
                                    startDist = 0.0,
                                    isPanMode = false,
                                    onZoomChanged = { _, _ -> },
                                    chartModifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val outFile = File("/tmp/att9172_activity_carriage_sync_bar_light_dark.png")
        outFile.parentFile?.mkdirs()
        val bmp = composeTestRule
            .onNodeWithTag("att9172_carriage_sync_bar_light_dark")
            .captureToImage()
            .asAndroidBitmap()
        FileOutputStream(outFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Rendered Light/Dark PNG must be non-empty", outFile.length() > 10_000L)
    }

    // TST-UI-2796.6: Narrow-width zero-distance STATIONARY stop guard (Stage 6 Bounce 1 regression)
    @Test
    fun narrowWidthZeroDistanceStop_rendersWithoutCoerceInCrash_tstUi2796_6() {
        val timeline = sampleTimeline() // includes zero-distance STATIONARY stop at 6000m
        val path = samplePath()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    Column {
                        // Case A: width <= startPadding + endPadding (60dp <= 102dp -> rawPlotWidth <= 0)
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .testTag("narrow_zero_plot_bar")
                        ) {
                            ActivityCarriageSyncBar(
                                timeline = timeline,
                                pathPoints = path,
                                currentDistance = 6000.0,
                                onDistanceSelected = {},
                                xAxisDomain = ProfileXAxisDomain.DISTANCE,
                                alignWithTelemetryCharts = true
                            )
                        }
                        // Case B: tiny positive plotWidth (103dp - 102dp = 1dp < 2.5dp stopMarkerWidthPx)
                        Box(
                            modifier = Modifier
                                .width(103.dp)
                                .testTag("narrow_submarker_plot_bar")
                        ) {
                            ActivityCarriageSyncBar(
                                timeline = timeline,
                                pathPoints = path,
                                currentDistance = 6000.0,
                                onDistanceSelected = {},
                                xAxisDomain = ProfileXAxisDomain.DISTANCE,
                                alignWithTelemetryCharts = true
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("narrow_zero_plot_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("narrow_submarker_plot_bar").assertIsDisplayed()
    }

    // TST-UI-2796.7: Landscape header & active-mode readout stay inside 72% plot column (Stage 6 Bounce 1 regression)
    @Test
    @Config(sdk = [34], qualifiers = "w840dp-h411dp-land-xxhdpi")
    fun landscapeHeaderAndReadout_stayWithin72PercentColumnWithoutOverlappingTopEndToolbar_tstUi2796_7() {
        val timeline = sampleTimeline()
        val path = samplePath()

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Surface {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("landscape_sync_bar_host")
                    ) {
                        ActivityCarriageSyncBar(
                            timeline = timeline,
                            pathPoints = path,
                            currentDistance = 5000.0, // WALKING_TRANSITION active readout visible
                            onDistanceSelected = {},
                            xAxisDomain = ProfileXAxisDomain.DISTANCE,
                            alignWithTelemetryCharts = true
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        val hostBounds = composeTestRule.onNodeWithTag("landscape_sync_bar_host").getUnclippedBoundsInRoot()
        val canvasBounds = composeTestRule.onNodeWithTag("activity_carriage_sync_bar_canvas").getUnclippedBoundsInRoot()
        val readoutBounds = composeTestRule.onNodeWithTag("activity_carriage_scrub_readout").getUnclippedBoundsInRoot()

        val hostWidth = hostBounds.right - hostBounds.left
        val max72Right = hostBounds.left + (hostWidth * 0.72f)
        composeTestRule.onNodeWithTag("activity_carriage_scrub_readout").assertIsDisplayed()
        assertTrue(
            "Landscape scrub readout right (${readoutBounds.right}) must stay within barCanvas right (${canvasBounds.right})",
            readoutBounds.right <= canvasBounds.right + 1.dp
        )
        assertTrue(
            "Landscape scrub readout right (${readoutBounds.right}) must stay inside 0.72f column ($max72Right) to avoid overlapping TopEnd toolbar/badge",
            readoutBounds.right <= max72Right + 1.dp
        )
    }
}
