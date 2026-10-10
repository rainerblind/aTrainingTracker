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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Visual and structural contract verification for trackless aftermath rendering (REQ-UI-235 / TST-UI-194.3 / ATT-2006).
 * Verifies map suppression, full-screen vertical scrolling, time-domain telemetry routing,
 * and instantaneous header metric readouts.
 */
class TracklessAftermathVisualContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    private val trackOnMapScreenFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
    }

    @Test
    fun testTrackOnMapScreen_evaluatesHasGpsTrack_andSuppressesMap() {
        assertTrue("TrackOnMapScreen.kt must exist", trackOnMapScreenFile.exists())
        val content = trackOnMapScreenFile.readText()

        assertTrue(
            "TrackOnMapScreen must evaluate hasGpsTrack requiring non-zero coordinates",
            content.contains("tracks.any { track ->") &&
                    content.contains("track.path.isNotEmpty() && track.latLngs.any { it.latitude != 0.0 || it.longitude != 0.0 }")
        )

        assertTrue(
            "TrackOnMapScreen must bind activeScrubPath to telemetryPath when hasGpsTrack is false",
            content.contains("val activeScrubPath = remember(hasGpsTrack, bestTrack, tracks, telemetryPath)") &&
                    content.contains("telemetryPath.ifEmpty { tracks.firstOrNull { it.path.isNotEmpty() }?.path }")
        )

        assertTrue(
            "TrackOnMapScreen must gate showMap with hasGpsTrack",
            content.contains("showMap = showMap && hasGpsTrack")
        )

        assertTrue(
            "TrackOnMapScreen must evaluate hasTracklessAltitude for trackless workouts with altitude data (REQ-UI-332)",
            content.contains("val hasTracklessAltitude = !hasGpsTrack && (activeScrubPath?.any { it.altitude != 0.0 } == true)")
        )

        assertTrue(
            "TrackOnMapScreen must decouple showElevationProfile allowing display for trackless workouts with altitude data (REQ-UI-332)",
            content.contains("showElevationProfile = (hasGpsTrack || hasTracklessAltitude) && activeDetailPrefs.showElevationProfile && isElevationPostMap && hasAltitudeData")
        )
    }

    @Test
    fun testMapDetailLayout_suppressesMap_andAppliesVerticalScroll() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must conditionally render map Box only when showMap is true",
            content.contains("if (showMap) {") &&
                    content.contains("ATrainingTrackerMap(")
        )

        assertTrue(
            "MapDetailLayout must conditionally render SplitPaneDivider only when showMap is true",
            content.contains("if (showMap) {") &&
                    content.contains("SplitPaneDivider(")
        )

        assertTrue(
            "MapDetailLayout outer container must apply fillMaxSize when showMap || hasScrollableContent",
            content.contains("showMap || hasScrollableContent") &&
                    content.contains("Modifier.fillMaxSize()")
        )

        assertTrue(
            "MapDetailLayout lower column must apply weight(1f) and verticalScroll when !showMap && hasScrollableContent",
            content.contains("!showMap && hasScrollableContent") &&
                    content.contains(".weight(1f)") &&
                    content.contains(".verticalScroll(rememberScrollState())")
        )
    }

    @Test
    fun testMapDetailLayout_evaluatesHasTelemetryGraphs_fromActiveScrubPath() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must compute isTrackless when activeScrubPath has 0 distance and >0 time",
            content.contains("ProfileDomainMath.isTracklessWorkout(activeScrubPath)") ||
                    content.contains("val isTrackless = (activeScrubPath?.lastOrNull()?.distance ?: 0.0) == 0.0 && (activeScrubPath?.lastOrNull()?.timeSec ?: 0) > 0")
        )

        assertTrue(
            "MapDetailLayout must enforce ProfileXAxisDomain.TIME when isTrackless",
            content.contains("val activeTelemetryDomain = if (isTrackless) ProfileXAxisDomain.TIME else tuningConfig.telemetryXAxisDomain")
        )

        assertTrue(
            "MapDetailLayout must pass activeTelemetryDomain as xAxisDomain to TelemetryMetricGraphs",
            content.contains("xAxisDomain = activeTelemetryDomain")
        )
    }

    @Test
    fun testMapDetailLayout_rendersScrubbingReadoutInHeader() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must compute active instantaneous metric readout during scrubbing for Heart Rate",
            content.contains("val activeHrPoint = if (selectedDistance != null) {") &&
                    content.contains("TelemetryMetricUtils.findNearestPoint(") &&
                    content.contains("isTimeDomain = isTrackless")
        )

        assertTrue(
            "MapDetailLayout must compute active instantaneous metric readout during scrubbing for Power",
            content.contains("val activePowerPoint = if (selectedDistance != null) {") &&
                    content.contains("TelemetryMetricUtils.findNearestPoint(") &&
                    content.contains("isTimeDomain = isTrackless")
        )
    }

    @Test
    fun testMapDetailLayout_rendersTelemetryGraphs_evenWhenShowElevationProfileIsFalse() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout lowerColumn must render chart surface when showElevationProfile || hasTelemetryGraphs",
            content.contains("if (showElevationProfile || hasTelemetryGraphs) {")
        )

        assertTrue(
            "MapDetailLayout must guard ElevationProfile specifically with showElevationProfile",
            content.contains("if (showElevationProfile) {") &&
                    content.contains("ElevationProfile(")
        )

        assertTrue(
            "MapDetailLayout must allow TelemetryMetricGraph when showZoomControls without requiring showElevationProfile",
            content.contains("if (showZoomControls) {") &&
                    content.contains("TelemetryMetricType.HEART_RATE")
        )
    }
}

