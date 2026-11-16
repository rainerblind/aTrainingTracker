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

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
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
import kotlin.math.sin

/**
 * Rule 8.5 Visual Compose Screenshot verification for ATT-8096 (TST-PER-050.5, REQ-PER-042).
 *
 * Renders Light & Dark themes side-by-side comparing:
 * - Left: Single-sensor Heart Rate chart (clean zero-clutter layout, no Layers button or mean legend)
 * - Right: Multi-sensor Heart Rate, Power, and Cadence charts with active dashed overlays,
 *   Layers icon toggle, Ø mean comparison legend, and synchronized scrub readout.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1320dp-h2400dp-xhdpi")
class MultiSensorChartsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testCaptureMultiSensorChartsComparisonScreenshot() {
        // TST-PER-050.5
        val path = buildSamplePath()
        val hrOverlays = buildHrOverlays()
        val powerOverlays = buildPowerOverlays()
        val cadenceOverlays = buildCadenceOverlays()
        val scrubDistance = 500.0

        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .width(1240.dp)
                    .background(Color(0xFF777777))
                    .padding(10.dp)
                    .testTag("multi_sensor_charts_comparison"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeComparisonPanel(
                            themeLabel = "Light Theme",
                            path = path,
                            hrOverlays = hrOverlays,
                            powerOverlays = powerOverlays,
                            cadenceOverlays = cadenceOverlays,
                            scrubDistance = scrubDistance
                        )
                    }
                }
                ATrainingTrackerTheme(darkTheme = true) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ThemeComparisonPanel(
                            themeLabel = "Dark Theme",
                            path = path,
                            hrOverlays = hrOverlays,
                            powerOverlays = powerOverlays,
                            cadenceOverlays = cadenceOverlays,
                            scrubDistance = scrubDistance
                        )
                    }
                }
            }
        }

        val bmp = composeTestRule.onNodeWithTag("multi_sensor_charts_comparison")
            .captureToImage()
            .asAndroidBitmap()

        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8096_multisensor_charts.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot must exist", outFile.exists())
        assertTrue("Screenshot must be non-empty", outFile.length() > 10_000L)
    }

    @Composable
    private fun ThemeComparisonPanel(
        themeLabel: String,
        path: List<PathPoint>,
        hrOverlays: List<TelemetrySourceSeries>,
        powerOverlays: List<TelemetrySourceSeries>,
        cadenceOverlays: List<TelemetrySourceSeries>,
        scrubDistance: Double
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ATT-8096 Multi-Sensor Charts Comparison ($themeLabel)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleSensorBaselineColumn(
                    path = path,
                    scrubDistance = scrubDistance,
                    modifier = Modifier.weight(1f)
                )
                MultiSensorOverlayColumn(
                    path = path,
                    hrOverlays = hrOverlays,
                    powerOverlays = powerOverlays,
                    cadenceOverlays = cadenceOverlays,
                    scrubDistance = scrubDistance,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    private fun SingleSensorBaselineColumn(
        path: List<PathPoint>,
        scrubDistance: Double,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier) {
            Text(
                text = "Single-Sensor Baseline (Zero Clutter)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTelemetryHeaderWithOverlays(
                    baseHeading = stringResource(R.string.graph_heading_heart_rate),
                    activePrimaryText = "149 bpm",
                    metricType = TelemetryMetricType.HEART_RATE,
                    activeOverlays = emptyList(),
                    overlayScrubTarget = scrubDistance,
                    isTimeDomain = false
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TelemetryMetricGraph(
                pathPoints = path,
                metricType = TelemetryMetricType.HEART_RATE,
                currentDistance = scrubDistance,
                onDistanceSelected = {},
                bSportType = BSportType.BIKE,
                overlaySeries = emptyList(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.graph_heading_cadence),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TelemetryMetricGraph(
                pathPoints = path,
                metricType = TelemetryMetricType.CADENCE,
                currentDistance = scrubDistance,
                onDistanceSelected = {},
                bSportType = BSportType.BIKE,
                overlaySeries = emptyList(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun MultiSensorOverlayColumn(
        path: List<PathPoint>,
        hrOverlays: List<TelemetrySourceSeries>,
        powerOverlays: List<TelemetrySourceSeries>,
        cadenceOverlays: List<TelemetrySourceSeries>,
        scrubDistance: Double,
        modifier: Modifier = Modifier
    ) {
        Column(modifier = modifier) {
            Text(
                text = "Multi-Sensor Overlay Active (Layers + Ø Mean + Scrub)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            OverlayPreviewChartItem(
                path = path,
                headingRes = R.string.graph_heading_heart_rate,
                activePrimaryText = "149 bpm",
                metricType = TelemetryMetricType.HEART_RATE,
                overlays = hrOverlays,
                scrubDistance = scrubDistance
            )
            Spacer(modifier = Modifier.height(6.dp))
            OverlayPreviewChartItem(
                path = path,
                headingRes = R.string.graph_heading_power,
                activePrimaryText = "245 W",
                metricType = TelemetryMetricType.POWER,
                overlays = powerOverlays,
                scrubDistance = scrubDistance
            )
            Spacer(modifier = Modifier.height(6.dp))
            OverlayPreviewChartItem(
                path = path,
                headingRes = R.string.graph_heading_cadence,
                activePrimaryText = "91 rpm",
                metricType = TelemetryMetricType.CADENCE,
                overlays = cadenceOverlays,
                scrubDistance = scrubDistance
            )
        }
    }

    @Composable
    private fun OverlayPreviewChartItem(
        path: List<PathPoint>,
        headingRes: Int,
        activePrimaryText: String,
        metricType: TelemetryMetricType,
        overlays: List<TelemetrySourceSeries>,
        scrubDistance: Double
    ) {
        Text(
            text = formatTelemetryHeaderWithOverlays(
                baseHeading = stringResource(headingRes),
                activePrimaryText = activePrimaryText,
                metricType = metricType,
                activeOverlays = overlays,
                overlayScrubTarget = scrubDistance,
                isTimeDomain = false
            ),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        TelemetryMetricGraph(
            pathPoints = path,
            metricType = metricType,
            currentDistance = scrubDistance,
            onDistanceSelected = {},
            bSportType = BSportType.BIKE,
            overlaySeries = overlays,
            enabledOverlayIds = overlays.map { it.columnName }.toSet(),
            onToggleOverlay = {},
            modifier = Modifier.fillMaxWidth()
        )
    }

    private fun buildSamplePath(): List<PathPoint> {
        val origin = LatLng(48.13, 11.57)
        return (0..40).map { i ->
            val distance = i * 25.0
            PathPoint(
                distance = distance,
                latLng = origin,
                timeSec = i * 5L,
                hr = (138 + (12 * sin(i * 0.25)).toInt()),
                power = (220 + (35 * sin(i * 0.2)).toInt()),
                cadence = (88 + (6 * sin(i * 0.3)).toInt())
            )
        }
    }

    private fun buildHrOverlays(): List<TelemetrySourceSeries> {
        val polarPts = (0..40).map { i ->
            TelemetrySourcePoint(i * 25.0, i * 5L, 138.0 + 12.0 * sin(i * 0.25))
        }
        val watchPts = (0..40).map { i ->
            TelemetrySourcePoint(i * 25.0, i * 5L, 134.0 + 10.0 * sin(i * 0.25 - 0.3))
        }
        return listOf(
            TelemetrySourceSeries(
                TelemetryMetricType.HEART_RATE,
                "Polar H10",
                "HR (Polar H10)",
                polarPts,
                141.2
            ),
            TelemetrySourceSeries(
                TelemetryMetricType.HEART_RATE,
                "Garmin Watch",
                "HR (Garmin Watch)",
                watchPts,
                136.4
            )
        )
    }

    private fun buildPowerOverlays(): List<TelemetrySourceSeries> {
        val kickrPts = (0..40).map { i ->
            TelemetrySourcePoint(i * 25.0, i * 5L, 220.0 + 35.0 * sin(i * 0.2))
        }
        val assiomaPts = (0..40).map { i ->
            TelemetrySourcePoint(i * 25.0, i * 5L, 228.0 + 36.0 * sin(i * 0.2))
        }
        return listOf(
            TelemetrySourceSeries(
                TelemetryMetricType.POWER,
                "KICKR v5.0",
                "POWER (KICKR v5.0)",
                kickrPts,
                227.5
            ),
            TelemetrySourceSeries(
                TelemetryMetricType.POWER,
                "Assioma Duo",
                "POWER (Assioma Duo)",
                assiomaPts,
                235.1
            )
        )
    }

    private fun buildCadenceOverlays(): List<TelemetrySourceSeries> {
        val assiomaPts = (0..40).map { i ->
            TelemetrySourcePoint(i * 25.0, i * 5L, 88.0 + 6.0 * sin(i * 0.3))
        }
        val kickrPts = (0..40).map { i ->
            TelemetrySourcePoint(i * 25.0, i * 5L, 86.0 + 5.0 * sin(i * 0.3 - 0.2))
        }
        return listOf(
            TelemetrySourceSeries(
                TelemetryMetricType.CADENCE,
                "Assioma",
                "CADENCE (Assioma)",
                assiomaPts,
                89.2
            ),
            TelemetrySourceSeries(
                TelemetryMetricType.CADENCE,
                "KICKR",
                "CADENCE (KICKR)",
                kickrPts,
                87.0
            )
        )
    }
}
