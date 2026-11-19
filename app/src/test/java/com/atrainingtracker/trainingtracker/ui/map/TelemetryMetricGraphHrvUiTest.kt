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

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor
import com.google.android.gms.maps.model.LatLng
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
import kotlin.math.abs

/**
 * Behavioral Compose UI & Rule 8.5 Light/Dark screenshot verification for DFA alpha 1
 * 3-zone graph coloring, 52.dp margin disjointness, and threshold filtering (TST-HRV-044.3).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w860dp-h720dp-xhdpi")
class TelemetryMetricGraphHrvUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val dfaSamplePoints = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), timeSec = 0L, hr = 125, hrvDfaA1 = 1.08, hrvArtifactRate = 0.5),
        PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), timeSec = 30L, hr = 132, hrvDfaA1 = 0.92, hrvArtifactRate = 1.0),
        PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), timeSec = 60L, hr = 148, hrvDfaA1 = 0.68, hrvArtifactRate = 1.5),
        PathPoint(distance = 300.0, latLng = LatLng(0.0, 0.0), timeSec = 90L, hr = 152, hrvDfaA1 = 0.62, hrvArtifactRate = 8.5), // artifact gap
        PathPoint(distance = 400.0, latLng = LatLng(0.0, 0.0), timeSec = 120L, hr = 162, hrvDfaA1 = 0.56, hrvArtifactRate = 1.2),
        PathPoint(distance = 500.0, latLng = LatLng(0.0, 0.0), timeSec = 150L, hr = 175, hrvDfaA1 = 0.36, hrvArtifactRate = 0.8),
        PathPoint(distance = 600.0, latLng = LatLng(0.0, 0.0), timeSec = 180L, hr = 168, hrvDfaA1 = 0.44, hrvArtifactRate = 0.4)
    )

    @Test
    fun testDfaA1Graph_rendersZoneColorsInLightAndDarkAndSavesPng() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Gray.copy(alpha = 0.2f))
                    .padding(8.dp)
                    .testTag("dfa_a1_light_dark_root")
            ) {
                ATrainingTrackerTheme(darkTheme = false, amoled = false, setWindowColors = false) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp)
                            .testTag("dfa_a1_light")
                    ) {
                        Text(
                            text = "Light — DFA α1 (0.75 / 0.50)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TelemetryMetricGraph(
                            pathPoints = dfaSamplePoints,
                            metricType = TelemetryMetricType.HRV_DFA_A1,
                            currentDistance = 500.0,
                            onDistanceSelected = {},
                            dfaA1ThresholdT1 = 0.75f,
                            dfaA1ThresholdT2 = 0.50f,
                            enableGestures = false
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                ATrainingTrackerTheme(darkTheme = true, amoled = false, setWindowColors = false) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(8.dp)
                            .testTag("dfa_a1_dark")
                    ) {
                        Text(
                            text = "Dark — DFA α1 (0.75 / 0.50)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TelemetryMetricGraph(
                            pathPoints = dfaSamplePoints,
                            metricType = TelemetryMetricType.HRV_DFA_A1,
                            currentDistance = 100.0,
                            onDistanceSelected = {},
                            dfaA1ThresholdT1 = 0.75f,
                            dfaA1ThresholdT2 = 0.50f,
                            enableGestures = false
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        val lightBmp = composeTestRule.onNodeWithTag("dfa_a1_light").captureToImage().asAndroidBitmap()
        val darkBmp = composeTestRule.onNodeWithTag("dfa_a1_dark").captureToImage().asAndroidBitmap()
        val compositeBmp = composeTestRule.onNodeWithTag("dfa_a1_light_dark_root").captureToImage().asAndroidBitmap()

        // Verify all 3 zone colors appear in both Light & Dark renderings
        assertTrue("Light DFA α1 graph must render Zone2 green", bitmapContainsColorNear(lightBmp, TTColor.Zone2))
        assertTrue("Light DFA α1 graph must render Zone3 orange", bitmapContainsColorNear(lightBmp, TTColor.Zone3))
        assertTrue("Light DFA α1 graph must render Zone4 red", bitmapContainsColorNear(lightBmp, TTColor.Zone4))
        assertTrue("Dark DFA α1 graph must render Zone2 green", bitmapContainsColorNear(darkBmp, TTColor.Zone2))
        assertTrue("Dark DFA α1 graph must render Zone3 orange", bitmapContainsColorNear(darkBmp, TTColor.Zone3))
        assertTrue("Dark DFA α1 graph must render Zone4 red", bitmapContainsColorNear(darkBmp, TTColor.Zone4))

        // Preserve historical ATT-8710_dfa_a1_zone_graph.png untouched; write inverted Rule 8.5 PNG to ATT-8953 asset (TST-HRV-046.3)
        val repoRoot = if (File("..", "docs/engineering/walkthroughs/img").exists()) File("..") else File(".")
        val outFile = File(repoRoot, "docs/engineering/walkthroughs/img/ATT-8953_dfa_a1_inverted_yaxis.png")
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            compositeBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Rule 8.5 PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }

    @Test
    fun testDfaA1Graph_invertedYAxisSpatialBandsPortraitAndLandscape_TST_HRV_046() {
        composeTestRule.setContent {
            val baseConfig = LocalConfiguration.current
            val portraitConfig = Configuration(baseConfig).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
            }
            val landscapeConfig = Configuration(baseConfig).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
            }
            ATrainingTrackerTheme(darkTheme = false, amoled = false, setWindowColors = false) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    CompositionLocalProvider(LocalConfiguration provides portraitConfig) {
                        TelemetryMetricGraph(
                            pathPoints = dfaSamplePoints,
                            metricType = TelemetryMetricType.HRV_DFA_A1,
                            currentDistance = 500.0,
                            onDistanceSelected = {},
                            dfaA1ThresholdT1 = 0.75f,
                            dfaA1ThresholdT2 = 0.50f,
                            enableGestures = false,
                            modifier = Modifier.testTag("dfa_a1_inverted_portrait")
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    CompositionLocalProvider(LocalConfiguration provides landscapeConfig) {
                        TelemetryMetricGraph(
                            pathPoints = dfaSamplePoints,
                            metricType = TelemetryMetricType.HRV_DFA_A1,
                            currentDistance = 100.0,
                            onDistanceSelected = {},
                            dfaA1ThresholdT1 = 0.75f,
                            dfaA1ThresholdT2 = 0.50f,
                            enableGestures = false,
                            modifier = Modifier.testTag("dfa_a1_inverted_landscape")
                        )
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        val portraitBmp = composeTestRule.onNodeWithTag("dfa_a1_inverted_portrait").captureToImage().asAndroidBitmap()
        val landscapeBmp = composeTestRule.onNodeWithTag("dfa_a1_inverted_landscape").captureToImage().asAndroidBitmap()

        // Portrait: top Anaerob band [0%..26%] has Zone4 red and NOT Zone2 green; bottom Aerob band [46%..80%] has Zone2 green and NOT Zone4 red
        val pTopEnd = (portraitBmp.height * 0.26f).toInt()
        val pBotStart = (portraitBmp.height * 0.46f).toInt()
        val pBotEnd = (portraitBmp.height * 0.80f).toInt()
        val pPlotStart = (portraitBmp.width * 0.10f).toInt()
        val pPlotEnd = (portraitBmp.width * 0.90f).toInt()
        val pHistStart = (portraitBmp.width * 0.945f).toInt()

        assertTrue("Portrait main plot top band must render Zone4 Anaerob red", bitmapRegionContainsColorNear(portraitBmp, TTColor.Zone4, pPlotStart, pPlotEnd, 0, pTopEnd))
        assertFalse("Portrait main plot top band must NOT render Zone2 Aerob green", bitmapRegionContainsColorNear(portraitBmp, TTColor.Zone2, pPlotStart, pPlotEnd, 0, pTopEnd))
        assertTrue("Portrait main plot bottom band must render Zone2 Aerob green", bitmapRegionContainsColorNear(portraitBmp, TTColor.Zone2, pPlotStart, pPlotEnd, pBotStart, pBotEnd))
        assertFalse("Portrait main plot bottom band must NOT render Zone4 Anaerob red", bitmapRegionContainsColorNear(portraitBmp, TTColor.Zone4, pPlotStart, pPlotEnd, pBotStart, pBotEnd))

        assertTrue("Portrait marginal histogram top band must render Zone4 red", bitmapRegionContainsColorNear(portraitBmp, TTColor.Zone4, pHistStart, portraitBmp.width, 0, pTopEnd))
        assertFalse("Portrait marginal histogram top band must NOT render Zone2 green", bitmapRegionContainsColorNear(portraitBmp, TTColor.Zone2, pHistStart, portraitBmp.width, 0, pTopEnd))
        assertTrue("Portrait marginal histogram bottom band must render Zone2 green", bitmapRegionContainsColorNear(portraitBmp, TTColor.Zone2, pHistStart, portraitBmp.width, pBotStart, pBotEnd))
        assertFalse("Portrait marginal histogram bottom band must NOT render Zone4 red", bitmapRegionContainsColorNear(portraitBmp, TTColor.Zone4, pHistStart, portraitBmp.width, pBotStart, pBotEnd))

        // Landscape: 72/28 split with AlignedMarginalLinearDistribution (bars drawn at 0.85f alpha -> tolerance 48)
        val lTopEnd = (landscapeBmp.height * 0.26f).toInt()
        val lBotStart = (landscapeBmp.height * 0.46f).toInt()
        val lBotEnd = (landscapeBmp.height * 0.80f).toInt()
        val lHistStart = (landscapeBmp.width * 0.74f).toInt()

        assertTrue("Landscape AlignedMarginalLinearDistribution top band must render Zone4 red", bitmapRegionContainsColorNear(landscapeBmp, TTColor.Zone4, lHistStart, landscapeBmp.width, 0, lTopEnd, tolerance = 48))
        assertFalse("Landscape AlignedMarginalLinearDistribution top band must NOT render Zone2 green", bitmapRegionContainsColorNear(landscapeBmp, TTColor.Zone2, lHistStart, landscapeBmp.width, 0, lTopEnd, tolerance = 48))
        assertTrue("Landscape AlignedMarginalLinearDistribution bottom band must render Zone2 green", bitmapRegionContainsColorNear(landscapeBmp, TTColor.Zone2, lHistStart, landscapeBmp.width, lBotStart, lBotEnd, tolerance = 48))
        assertFalse("Landscape AlignedMarginalLinearDistribution bottom band must NOT render Zone4 red", bitmapRegionContainsColorNear(landscapeBmp, TTColor.Zone4, lHistStart, landscapeBmp.width, lBotStart, lBotEnd, tolerance = 48))
    }

    @Test
    fun testDfaA1Graph_outOfRangeMaxT1_andHeartRateSolidInvariance() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false, amoled = false, setWindowColors = false) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                ) {
                    TelemetryMetricGraph(
                        pathPoints = dfaSamplePoints,
                        metricType = TelemetryMetricType.HRV_DFA_A1,
                        currentDistance = 100.0,
                        onDistanceSelected = {},
                        dfaA1ThresholdT1 = TuningPreferencesDefaults.MAX_DFA_A1_THRESHOLD,
                        dfaA1ThresholdT2 = 0.50f,
                        enableGestures = false,
                        modifier = Modifier.testTag("dfa_a1_max_t1")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TelemetryMetricGraph(
                        pathPoints = dfaSamplePoints,
                        metricType = TelemetryMetricType.HEART_RATE,
                        currentDistance = null,
                        onDistanceSelected = {},
                        enableGestures = false,
                        modifier = Modifier.testTag("hr_solid_invariance")
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val maxT1Bmp = composeTestRule.onNodeWithTag("dfa_a1_max_t1").captureToImage().asAndroidBitmap()
        // When T1 = 1.50f (> 1.4), no Aerob Zone2 pixels should appear
        assertFalse("T1=1.50f > 1.4 must not emit Zone2 Aerob curve/histogram pixels", bitmapContainsColorNear(maxT1Bmp, TTColor.Zone2))
        assertTrue("T1=1.50f, T2=0.50f must emit Zone3 Schwelle pixels", bitmapContainsColorNear(maxT1Bmp, TTColor.Zone3))
        assertTrue("T1=1.50f, T2=0.50f must emit Zone4 Anaerob pixels", bitmapContainsColorNear(maxT1Bmp, TTColor.Zone4))

        val hrBmp = composeTestRule.onNodeWithTag("hr_solid_invariance").captureToImage().asAndroidBitmap()
        assertTrue("HEART_RATE must preserve solid CRIMSON stroke", bitmapContainsColorNear(hrBmp, TelemetryGraphColor.CRIMSON.lightColor))
    }

    private fun bitmapContainsColorNear(bitmap: Bitmap, target: Color, tolerance: Int = 18): Boolean {
        return bitmapRegionContainsColorNear(bitmap, target, 0, bitmap.width, 0, bitmap.height, tolerance)
    }

    private fun bitmapRegionContainsColorNear(
        bitmap: Bitmap,
        target: Color,
        xStart: Int,
        xEnd: Int,
        yStart: Int,
        yEnd: Int,
        tolerance: Int = 18
    ): Boolean {
        val targetArgb = target.toArgb()
        val tr = android.graphics.Color.red(targetArgb)
        val tg = android.graphics.Color.green(targetArgb)
        val tb = android.graphics.Color.blue(targetArgb)

        val safeYStart = yStart.coerceIn(0, bitmap.height)
        val safeYEnd = yEnd.coerceIn(safeYStart, bitmap.height)
        val safeXStart = xStart.coerceIn(0, bitmap.width)
        val safeXEnd = xEnd.coerceIn(safeXStart, bitmap.width)

        for (y in safeYStart until safeYEnd) {
            for (x in safeXStart until safeXEnd) {
                val px = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(px) < 200) continue
                val dr = abs(android.graphics.Color.red(px) - tr)
                val dg = abs(android.graphics.Color.green(px) - tg)
                val db = abs(android.graphics.Color.blue(px) - tb)
                if (dr <= tolerance && dg <= tolerance && db <= tolerance) {
                    return true
                }
            }
        }
        return false
    }
}
