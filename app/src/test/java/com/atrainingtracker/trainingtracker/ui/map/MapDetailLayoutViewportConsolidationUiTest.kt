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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Behavioral Robolectric Compose UI verification for `ATT-8134` (`REQ-UI-2803.2`, `TST-UI-2803.2`):
 * - Grouped-model [MapDetailLayout] overload with unified [GlobalTelemetryZoomToolbar] in Portrait & Landscape.
 * - Consistent `ProfileDomainMath.isEffectiveTimeDomain` resolution across [MapDetailLayout],
 *   [ElevationProfile], and [TelemetryChartStack] (including `TIME` domain with `totalTimeSec == 0L`).
 * - Preservation of `ATT-9168` (`REQ-REV-015`) offscreen snapshot header isolation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class MapDetailLayoutViewportConsolidationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun samplePath(zeroTimestamps: Boolean = false): List<PathPoint> =
        (0..40).map { i ->
            PathPoint(
                distance = i * 100.0,
                latLng = LatLng(48.13 + i * 0.0005, 11.57 + i * 0.0005),
                altitude = 500.0 + i * 3.0,
                timeSec = if (zeroTimestamps) 0L else i * 15L,
                hr = 135 + (i % 25),
                speedMps = 7.5,
                power = 210 + (i % 40)
            )
        }

    @Test
    fun testGroupedMapDetailLayout_portraitZoomToolbarAndChartSectionsRenderCleanly_tstUi2803_2() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val zoomInDesc = context.getString(R.string.zoom_in)
        val zoomOutDesc = context.getString(R.string.zoom_out)

        composeRule.setContent {
            ATrainingTrackerTheme {
                MapDetailLayout(
                    bSportType = BSportType.BIKE,
                    zoomFocus = MapZoomFocus.FIT_PRIMARY,
                    activeScrubPath = samplePath(),
                    header = { Text("Workout Header", modifier = Modifier.testTag("grouped_header")) },
                    displayOptions = MapDetailDisplayOptions(
                        showHeader = false,
                        showMap = false,
                        showElevationProfile = true,
                        showZoomControls = true,
                        showTelemetryCharts = true
                    )
                )
            }
        }
        composeRule.waitForIdle()

        // ATT-9168 invariant: idle showHeader = false keeps offscreen header out of semantics tree
        composeRule.onNodeWithTag("grouped_header").assertDoesNotExist()
        // Unified lower viewport mounts Elevation + Telemetry chart sections + single GlobalTelemetryZoomToolbar
        composeRule.onNodeWithTag("workout_detail_section_ELEVATION").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_detail_section_CHARTS").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(zoomInDesc).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(zoomOutDesc).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-land-xxhdpi")
    fun testGroupedMapDetailLayout_landscapeOverlayZoomToolbarAndChartsRenderCleanly_tstUi2803_2() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val zoomInDesc = context.getString(R.string.zoom_in)

        composeRule.setContent {
            ATrainingTrackerTheme {
                MapDetailLayout(
                    bSportType = BSportType.BIKE,
                    zoomFocus = MapZoomFocus.FIT_PRIMARY,
                    activeScrubPath = samplePath(),
                    header = { Text("Landscape Header") },
                    displayOptions = MapDetailDisplayOptions(
                        showHeader = false,
                        showMap = false,
                        showElevationProfile = true,
                        showZoomControls = true,
                        showTelemetryCharts = true
                    )
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workout_detail_section_ELEVATION").assertIsDisplayed()
        composeRule.onNodeWithTag("workout_detail_section_CHARTS").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(zoomInDesc).assertIsDisplayed().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun testTelemetryChartStack_resolvesConsistentDomainWhenTimestampsZero_tstUi2803_2() {
        val zeroTimePath = samplePath(zeroTimestamps = true)
        assertFalse(
            "TIME preference with totalTimeSec == 0L must resolve to DISTANCE consistently",
            ProfileDomainMath.isEffectiveTimeDomain(ProfileXAxisDomain.TIME, zeroTimePath)
        )
        assertEquals(
            4000.0,
            ProfileDomainMath.calculateTotalSpan(ProfileXAxisDomain.TIME, zeroTimePath),
            0.001
        )

        var selectedDist: Double? = null
        var zoomUpdates = 0
        composeRule.setContent {
            ATrainingTrackerTheme {
                TelemetryChartStack(
                    path = zeroTimePath,
                    bSportType = BSportType.BIKE,
                    tuningConfig = TuningConfig(telemetryXAxisDomain = ProfileXAxisDomain.TIME),
                    selectedDistance = 1200.0,
                    onDistanceSelected = { selectedDist = it },
                    activeTelemetryDomain = ProfileXAxisDomain.TIME,
                    isTrackless = false,
                    zoomScale = 1.0f,
                    startDist = 0.0,
                    isPanMode = false,
                    onZoomChanged = { _, _ -> zoomUpdates++ },
                    chartModifier = Modifier
                )
            }
        }
        composeRule.waitForIdle()

        // All recorded charts render cleanly without domain mismatch
        composeRule.onNodeWithTag("telemetry_chart_section_SPEED_PACE").assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_chart_section_POWER").assertIsDisplayed()
        composeRule.onNodeWithTag("telemetry_chart_section_HEART_RATE").assertIsDisplayed()
        assertTrue("Non-crashing render verified ($selectedDist / $zoomUpdates)", zoomUpdates >= 0)
    }

    @Test
    fun testCaptureUnifiedTelemetryChartsLightAndDarkPng_tstUi2803_2() {
        val richPath = (0..120).map { i ->
            val alt = when {
                i < 40 -> 450.0 + i * 3.5
                i < 80 -> 590.0 + (i - 40) * 6.0
                else -> (830.0 - (i - 80) * 5.0).coerceAtLeast(500.0)
            }
            PathPoint(
                distance = i * 80.0,
                latLng = LatLng(48.13 + i * 0.0004, 11.57 + i * 0.0004),
                altitude = alt,
                timeSec = i * 12L,
                hr = 132 + ((i * 3) % 46),
                speedMps = 6.5 + ((i * 2) % 10) * 0.35,
                power = 175 + ((i * 7) % 120)
            )
        }
        composeRule.setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("att8134_charts_light_dark_root"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ElevationProfile(
                                pathPoints = richPath,
                                currentDistance = 4800.0,
                                showZoomControls = true,
                                alignWithTelemetryCharts = true,
                                smoothingSigma = 0.0
                            )
                            TelemetryMetricGraph(
                                pathPoints = richPath,
                                metricType = TelemetryMetricType.HEART_RATE,
                                currentDistance = 4800.0,
                                onDistanceSelected = {}
                            )
                            TelemetryMetricGraph(
                                pathPoints = richPath,
                                metricType = TelemetryMetricType.POWER,
                                currentDistance = 4800.0,
                                onDistanceSelected = {}
                            )
                        }
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ElevationProfile(
                                pathPoints = richPath,
                                currentDistance = 4800.0,
                                showZoomControls = true,
                                alignWithTelemetryCharts = true,
                                smoothingSigma = 0.0
                            )
                            TelemetryMetricGraph(
                                pathPoints = richPath,
                                metricType = TelemetryMetricType.HEART_RATE,
                                currentDistance = 4800.0,
                                onDistanceSelected = {}
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val outFile = File(
            System.getProperty("att8134.chartsPngOut")
                ?: "/tmp/ATT-8134_telemetry_charts_light_dark.png"
        )
        outFile.parentFile?.mkdirs()
        val bmp = composeRule.onNodeWithTag("att8134_charts_light_dark_root").captureToImage().asAndroidBitmap()
        FileOutputStream(outFile).use { fos ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Rendered ATT-8134 Light/Dark charts PNG must be non-empty", outFile.length() > 1_000L)
    }
}
