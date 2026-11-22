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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.AmoledDarkColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.DarkColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.LightColorScheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
 * Pure-JVM contrast, gradient-stop, round-tick fallback, multi-source overlay distinction, and
 * Robolectric Compose UI / Rule 8.5 Light & Dark/AMOLED visual verification for zone-colored
 * Heart Rate & Power curves and zone boundary Y-axis labels (`ATT-9164`, `REQ-UI-2798`, `TST-UI-2798`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w940dp-h1120dp-xhdpi")
class TelemetryMetricGraphZoneColorUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private companion object {
        val ORIGIN = LatLng(48.52, 9.05)
        const val SCREENSHOT_REL_PATH =
            "docs/engineering/test_evidence/tickets/ATT-9164/screenshots/att9164_zone_colored_graphs_light_dark.png"
    }

    private fun resolveScreenshotFile(): File {
        val cwd = File(".").canonicalFile
        val repoRoot = if (cwd.name == "app") cwd.parentFile else cwd
        return File(repoRoot, SCREENSHOT_REL_PATH)
    }

    @Test
    fun zoneStrokeColor_meetsWcagContrastAcrossLightDarkAndAmoledForAll7Zones() {
        // TST-UI-2798.1: Every Z1..Z7 stroke token achieves >= 3.0:1 non-text WCAG 2.1 AA contrast
        for (zone in 1..7) {
            val lightStroke = TTColor.zoneStrokeColor(zone, isDarkTheme = false)
            val darkStroke = TTColor.zoneStrokeColor(zone, isDarkTheme = true)

            val lightRatio = TTColor.calculateContrastRatio(lightStroke, LightColorScheme.surface)
            val darkRatio = TTColor.calculateContrastRatio(darkStroke, DarkColorScheme.surface)
            val amoledRatio = TTColor.calculateContrastRatio(darkStroke, AmoledDarkColorScheme.surface)

            assertTrue("Z$zone light stroke contrast ($lightRatio) must be >= 3.0:1", lightRatio >= 3.0)
            assertTrue("Z$zone dark stroke contrast ($darkRatio) must be >= 3.0:1", darkRatio >= 3.0)
            assertTrue("Z$zone AMOLED stroke contrast ($amoledRatio) must be >= 3.0:1", amoledRatio >= 3.0)
        }
    }

    @Test
    fun buildZoneBandsVerticalGradientStops_producesMonotonicHardSteppedStopsForHrAndPower() {
        // TST-UI-2798.1: Non-inverted vertical gradient maps dataMax -> 0f (top) and dataMin -> 1f (bottom)
        val hrThresholds = HeartRateZoneThresholds(130, 143, 157, 170)
        val hrBands = TelemetryZoneMath.calculateHeartRateZoneBands(hrThresholds, 100.0, 180.0)
        val hrStopsLight = TelemetryZoneMath.buildZoneBandsVerticalGradientStops(
            zoneBands = hrBands,
            dataMin = 100.0,
            dataMax = 180.0,
            isDarkSurface = false,
            topAlpha = 1f,
            bottomAlpha = 1f
        )

        // 5 bands => 1 top + 4*2 transitions + 1 bottom = 10 stops
        assertEquals(10, hrStopsLight.size)
        assertEquals(0f, hrStopsLight.first().first, 1e-4f)
        assertEquals(TTColor.Zone5StrokeLight, hrStopsLight.first().second)
        assertEquals(1f, hrStopsLight.last().first, 1e-4f)
        assertEquals(TTColor.Zone1StrokeLight, hrStopsLight.last().second)
        for (i in 1 until hrStopsLight.size) {
            assertTrue(
                "Gradient stops must be monotonically non-decreasing at index $i",
                hrStopsLight[i].first >= hrStopsLight[i - 1].first
            )
        }

        // First transition at 170 bpm -> (180 - 170) / 80 = 0.125f
        assertEquals(0.124f, hrStopsLight[1].first, 1e-3f)
        assertEquals(TTColor.Zone5StrokeLight, hrStopsLight[1].second)
        assertEquals(0.126f, hrStopsLight[2].first, 1e-3f)
        assertEquals(TTColor.Zone4StrokeLight, hrStopsLight[2].second)

        // 7-zone Power stops on Dark/AMOLED map Z7 top to high-contrast silver-white
        val pwrThresholds = PowerZoneThresholds(151, 206, 248, 289, 324, 405)
        val pwrBands = TelemetryZoneMath.calculatePowerZoneBands(pwrThresholds, 0.0, 500.0)
        val pwrStopsDark = TelemetryZoneMath.buildZoneBandsVerticalGradientStops(
            zoneBands = pwrBands,
            dataMin = 0.0,
            dataMax = 500.0,
            isDarkSurface = true,
            topAlpha = 1f,
            bottomAlpha = 1f
        )
        assertEquals(14, pwrStopsDark.size)
        assertEquals(TTColor.Zone7StrokeDark, pwrStopsDark.first().second)
        assertEquals(TTColor.Zone1StrokeDark, pwrStopsDark.last().second)

        // Single-zone ride still returns valid 2-stop gradient
        val singleBand = TelemetryZoneMath.calculateHeartRateZoneBands(hrThresholds, 132.0, 140.0)
        val singleStops = TelemetryZoneMath.buildZoneBandsVerticalGradientStops(singleBand, 132.0, 140.0, false)
        assertEquals(2, singleStops.size)
        assertEquals(TTColor.Zone2StrokeLight, singleStops[0].second)
        assertEquals(TTColor.Zone2StrokeLight, singleStops[1].second)

        // Invalid bounds return empty list
        assertTrue(TelemetryZoneMath.buildZoneBandsVerticalGradientStops(emptyList(), 0.0, 100.0, false).isEmpty())
        assertTrue(TelemetryZoneMath.buildZoneBandsVerticalGradientStops(hrBands, 100.0, 100.0, false).isEmpty())
    }

    @Test
    fun resolveMetricZoneStrokeColor_resolvesZoneColorsAndNullFallbackWhenUnzoned() {
        // TST-UI-2798.1: Single-point and scrub marker dot zone color resolution
        val hrThresholds = HeartRateZoneThresholds(130, 143, 157, 170)
        val pwrThresholds = PowerZoneThresholds(151, 206, 248, 289, 324, 405)

        assertEquals(
            TTColor.Zone1StrokeLight,
            TelemetryZoneMath.resolveMetricZoneStrokeColor(TelemetryMetricType.HEART_RATE, 120.0, hrThresholds, null, 0.75f, 0.50f, false)
        )
        assertEquals(
            TTColor.Zone5StrokeDark,
            TelemetryZoneMath.resolveMetricZoneStrokeColor(TelemetryMetricType.HEART_RATE, 175.0, hrThresholds, null, 0.75f, 0.50f, true)
        )
        assertEquals(
            TTColor.Zone7StrokeDark,
            TelemetryZoneMath.resolveMetricZoneStrokeColor(TelemetryMetricType.POWER, 460.0, null, pwrThresholds, 0.75f, 0.50f, true)
        )
        // Unconfigured / invalid thresholds return null (accent fallback)
        assertNull(
            TelemetryZoneMath.resolveMetricZoneStrokeColor(
                TelemetryMetricType.HEART_RATE, 150.0, HeartRateZoneThresholds(0, 0, 0, 0), null, 0.75f, 0.50f, false
            )
        )
        assertNull(
            TelemetryZoneMath.resolveMetricZoneStrokeColor(
                TelemetryMetricType.POWER, 250.0, null, PowerZoneThresholds(0, 0, 0, 0, 0, 0), 0.75f, 0.50f, false
            )
        )
        assertNull(
            TelemetryZoneMath.resolveMetricZoneStrokeColor(
                TelemetryMetricType.SPEED, 28.0, hrThresholds, pwrThresholds, 0.75f, 0.50f, false
            )
        )
    }

    @Test
    fun calculateNiceIntermediateTicks_selectsDynamicRoundStepsForUnzonedHrAndPower() {
        // TST-UI-2798.3: Dynamic round-number ticks when HR or Power zones are not configured
        assertEquals(
            listOf(100.0, 120.0, 140.0, 160.0),
            TelemetryZoneMath.calculateNiceIntermediateTicks(TelemetryMetricType.HEART_RATE, 95.0, 178.0, maxTickCount = 4)
        )
        assertEquals(
            listOf(130.0, 140.0, 150.0, 160.0),
            TelemetryZoneMath.calculateNiceIntermediateTicks(TelemetryMetricType.HEART_RATE, 125.0, 165.0, maxTickCount = 4)
        )
        assertEquals(
            listOf(100.0, 200.0, 300.0),
            TelemetryZoneMath.calculateNiceIntermediateTicks(TelemetryMetricType.POWER, 0.0, 330.0, maxTickCount = 4)
        )
        assertEquals(
            listOf(50.0, 100.0, 150.0),
            TelemetryZoneMath.calculateNiceIntermediateTicks(TelemetryMetricType.POWER, 0.0, 180.0, maxTickCount = 4)
        )
        assertEquals(
            listOf(200.0, 400.0, 600.0),
            TelemetryZoneMath.calculateNiceIntermediateTicks(TelemetryMetricType.POWER, 0.0, 650.0, maxTickCount = 4)
        )
        assertTrue(TelemetryZoneMath.calculateNiceIntermediateTicks(TelemetryMetricType.SPEED, 0.0, 40.0, 4).isEmpty())
        assertTrue(TelemetryZoneMath.calculateNiceIntermediateTicks(TelemetryMetricType.HEART_RATE, 150.0, 150.0, 4).isEmpty())
    }

    @Test
    fun overlayStrokeColor_avoidsZoneColorsWhenPrimaryCurveIsZoneColored() {
        // TST-UI-2798.2: Secondary multi-source overlay colors are strictly disjoint from Z1..Z7 stroke colors
        val lightZoneColors = (1..7).map { TTColor.zoneStrokeColor(it, isDarkTheme = false) }.toSet()
        val darkZoneColors = (1..7).map { TTColor.zoneStrokeColor(it, isDarkTheme = true) }.toSet()

        for (idx in 0 until 4) {
            val lightOverlay = overlayStrokeColor(idx, isDarkSurface = false, avoidZoneColors = true)
            val darkOverlay = overlayStrokeColor(idx, isDarkSurface = true, avoidZoneColors = true)
            assertFalse("Light overlay $idx must not match any zone stroke color", lightOverlay in lightZoneColors)
            assertFalse("Dark overlay $idx must not match any zone stroke color", darkOverlay in darkZoneColors)
            assertNotEquals("Light/Dark overlay $idx should adapt to surface", lightOverlay, darkOverlay)
        }
        assertEquals(TelemetryGraphColor.CYAN.lightColor, overlayStrokeColor(0, isDarkSurface = false, avoidZoneColors = true))
        assertEquals(TelemetryGraphColor.CYAN.darkColor, overlayStrokeColor(0, isDarkSurface = true, avoidZoneColors = true))
    }

    @Test
    fun renderZoneColoredGraphsGallery_lightAndDarkAmoled_verifiesPixelsAndSavesPng() {
        // TST-UI-2798.1, TST-UI-2798.2, TST-UI-2798.3 & Rule 8.5 Visual Self-Inspection PNG
        val hrThresholds = HeartRateZoneThresholds(130, 143, 157, 170)
        val pwrThresholds = PowerZoneThresholds(151, 206, 248, 289, 324, 405)

        // Spans Z1 (118) -> Z2 (138) -> Z3 (150) -> Z4 (164) -> Z5 (174), avg ~155 (near zone boundary 157!)
        val hrPoints = listOf(
            PathPoint(distance = 0.0, latLng = ORIGIN, timeSec = 0L, hr = 118),
            PathPoint(distance = 400.0, latLng = ORIGIN, timeSec = 120L, hr = 138),
            PathPoint(distance = 800.0, latLng = ORIGIN, timeSec = 240L, hr = 152),
            PathPoint(distance = 1200.0, latLng = ORIGIN, timeSec = 360L, hr = 166),
            PathPoint(distance = 1600.0, latLng = ORIGIN, timeSec = 480L, hr = 174),
            PathPoint(distance = 2000.0, latLng = ORIGIN, timeSec = 600L, hr = 168),
            PathPoint(distance = 2400.0, latLng = ORIGIN, timeSec = 720L, hr = 166)
        )

        // Spans all 7 Power zones Z1..Z7 (100 -> 180 -> 230 -> 270 -> 310 -> 370 -> 460 W)
        val pwrPoints = listOf(
            PathPoint(distance = 0.0, latLng = ORIGIN, timeSec = 0L, power = 100),
            PathPoint(distance = 400.0, latLng = ORIGIN, timeSec = 60L, power = 180),
            PathPoint(distance = 800.0, latLng = ORIGIN, timeSec = 120L, power = 230),
            PathPoint(distance = 1200.0, latLng = ORIGIN, timeSec = 180L, power = 270),
            PathPoint(distance = 1600.0, latLng = ORIGIN, timeSec = 240L, power = 310),
            PathPoint(distance = 2000.0, latLng = ORIGIN, timeSec = 300L, power = 370),
            PathPoint(distance = 2400.0, latLng = ORIGIN, timeSec = 360L, power = 460)
        )

        val secondaryHrOverlay = listOf(
            TelemetrySourceSeries(
                metricType = TelemetryMetricType.HEART_RATE,
                sourceName = "Polar H10",
                columnName = "hr_h10",
                points = hrPoints.map { TelemetrySourcePoint(it.distance, it.timeSec, (it.hr ?: 140).toDouble()) },
                meanValue = 155.0
            ),
            TelemetrySourceSeries(
                metricType = TelemetryMetricType.HEART_RATE,
                sourceName = "Polar Verity Sense",
                columnName = "hr_verity",
                points = hrPoints.map { TelemetrySourcePoint(it.distance, it.timeSec, ((it.hr ?: 140) - 6).toDouble()) },
                meanValue = 149.0
            )
        )

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("att9164_gallery_root"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false, amoled = false, setWindowColors = false) {
                    ZoneColorShowcaseColumn(
                        title = "Light Mode — ATT-9164 Zone Curves & Boundary Labels",
                        tagPrefix = "light",
                        hrPoints = hrPoints,
                        pwrPoints = pwrPoints,
                        hrThresholds = hrThresholds,
                        pwrThresholds = pwrThresholds,
                        secondaryHrOverlay = secondaryHrOverlay,
                        modifier = Modifier.weight(1f)
                    )
                }
                ATrainingTrackerTheme(darkTheme = true, amoled = true, setWindowColors = false) {
                    ZoneColorShowcaseColumn(
                        title = "Dark / AMOLED — ATT-9164 Zone Curves & Boundary Labels",
                        tagPrefix = "dark",
                        hrPoints = hrPoints,
                        pwrPoints = pwrPoints,
                        hrThresholds = hrThresholds,
                        pwrThresholds = pwrThresholds,
                        secondaryHrOverlay = secondaryHrOverlay,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verify HR multi-zone stroke colors in Light & Dark AMOLED
        val hrLightBmp = composeTestRule.onNodeWithTag("light_hr_zoned").captureToImage().asAndroidBitmap()
        val hrDarkBmp = composeTestRule.onNodeWithTag("dark_hr_zoned").captureToImage().asAndroidBitmap()
        assertTrue("Light zoned HR must contain Z1 stroke", bitmapContainsColorNear(hrLightBmp, TTColor.Zone1StrokeLight, tolerance = 28))
        assertTrue("Light zoned HR must contain Z2 stroke", bitmapContainsColorNear(hrLightBmp, TTColor.Zone2StrokeLight, tolerance = 28))
        assertTrue("Light zoned HR must contain Z3 stroke", bitmapContainsColorNear(hrLightBmp, TTColor.Zone3StrokeLight, tolerance = 28))
        assertTrue("Light zoned HR must contain Z4 stroke", bitmapContainsColorNear(hrLightBmp, TTColor.Zone4StrokeLight, tolerance = 28))
        assertTrue("Light zoned HR must contain Z5 stroke", bitmapContainsColorNear(hrLightBmp, TTColor.Zone5StrokeLight, tolerance = 28))

        assertTrue("Dark zoned HR must contain Z1 stroke", bitmapContainsColorNear(hrDarkBmp, TTColor.Zone1StrokeDark, tolerance = 28))
        assertTrue("Dark zoned HR must contain Z2 stroke", bitmapContainsColorNear(hrDarkBmp, TTColor.Zone2StrokeDark, tolerance = 28))
        assertTrue("Dark zoned HR must contain Z3 stroke", bitmapContainsColorNear(hrDarkBmp, TTColor.Zone3StrokeDark, tolerance = 28))
        assertTrue("Dark zoned HR must contain Z4 stroke", bitmapContainsColorNear(hrDarkBmp, TTColor.Zone4StrokeDark, tolerance = 28))
        assertTrue("Dark zoned HR must contain Z5 stroke", bitmapContainsColorNear(hrDarkBmp, TTColor.Zone5StrokeDark, tolerance = 28))

        // Verify Power 7-zone stroke colors incl. high-contrast Z7 on Dark AMOLED
        val pwrLightBmp = composeTestRule.onNodeWithTag("light_pwr_zoned").captureToImage().asAndroidBitmap()
        val pwrDarkBmp = composeTestRule.onNodeWithTag("dark_pwr_zoned").captureToImage().asAndroidBitmap()
        assertTrue("Light zoned Power must contain Z2 stroke", bitmapContainsColorNear(pwrLightBmp, TTColor.Zone2StrokeLight, tolerance = 28))
        assertTrue("Light zoned Power must contain Z5 stroke", bitmapContainsColorNear(pwrLightBmp, TTColor.Zone5StrokeLight, tolerance = 28))
        assertTrue("Dark zoned Power must contain Z7 silver-white stroke on AMOLED", bitmapContainsColorNear(pwrDarkBmp, TTColor.Zone7StrokeDark, tolerance = 28))

        // Verify Multi-Source HR preserves zone-colored primary + zone-orthogonal overlay stroke (index 1 -> THEME_DEFAULT blue)
        val multiLightBmp = composeTestRule.onNodeWithTag("light_hr_multi").captureToImage().asAndroidBitmap()
        val multiDarkBmp = composeTestRule.onNodeWithTag("dark_hr_multi").captureToImage().asAndroidBitmap()
        assertTrue("Multi-source Light HR must preserve primary Z4 stroke", bitmapContainsColorNear(multiLightBmp, TTColor.Zone4StrokeLight, tolerance = 28))
        assertTrue("Multi-source Light HR must render contrasting blue overlay", bitmapContainsColorNear(multiLightBmp, TelemetryGraphColor.THEME_DEFAULT.lightColor, tolerance = 32))
        assertTrue("Multi-source Dark HR must render contrasting blue overlay", bitmapContainsColorNear(multiDarkBmp, TelemetryGraphColor.THEME_DEFAULT.darkColor, tolerance = 32))

        // Verify Unzoned HR falls back to standard CRIMSON accent stroke
        val unzonedLightBmp = composeTestRule.onNodeWithTag("light_hr_unzoned").captureToImage().asAndroidBitmap()
        assertTrue("Unzoned Light HR must fall back to CRIMSON accent", bitmapContainsColorNear(unzonedLightBmp, TelemetryGraphColor.CRIMSON.lightColor, tolerance = 20))

        // Save Rule 8.5 PNG screenshot
        val compositeBmp = composeTestRule.onNodeWithTag("att9164_gallery_root").captureToImage().asAndroidBitmap()
        val outFile = resolveScreenshotFile()
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            compositeBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Rule 8.5 screenshot must exist and be non-empty", outFile.exists() && outFile.length() > 10_000L)
    }

    @Composable
    private fun ZoneColorShowcaseColumn(
        title: String,
        tagPrefix: String,
        hrPoints: List<PathPoint>,
        pwrPoints: List<PathPoint>,
        hrThresholds: HeartRateZoneThresholds,
        pwrThresholds: PowerZoneThresholds,
        secondaryHrOverlay: List<TelemetrySourceSeries>,
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Heart Rate — Zone Colors + Boundaries (130/143/157/170) + Ø155",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = hrPoints,
                metricType = TelemetryMetricType.HEART_RATE,
                currentDistance = 1200.0,
                onDistanceSelected = {},
                bSportType = BSportType.RUN,
                hrZoneThresholds = hrThresholds,
                enableGestures = false,
                modifier = Modifier.testTag("${tagPrefix}_hr_zoned")
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Power — 7-Zone Colors (Z1..Z7) + Zone Boundary Labels + Ø",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = pwrPoints,
                metricType = TelemetryMetricType.POWER,
                currentDistance = 2400.0,
                onDistanceSelected = {},
                bSportType = BSportType.BIKE,
                powerZoneThresholds = pwrThresholds,
                enableGestures = false,
                modifier = Modifier.testTag("${tagPrefix}_pwr_zoned")
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Multi-Source HR (Zone-Colored H10 + Contrasting Verity Sense)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = hrPoints,
                metricType = TelemetryMetricType.HEART_RATE,
                currentDistance = 1200.0,
                onDistanceSelected = {},
                bSportType = BSportType.RUN,
                hrZoneThresholds = hrThresholds,
                overlaySeries = secondaryHrOverlay,
                enabledOverlayIds = setOf("hr_verity"),
                onToggleOverlay = {},
                enableGestures = false,
                modifier = Modifier.testTag("${tagPrefix}_hr_multi")
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Heart Rate — No Zones Configured (Accent Fallback + Round Ticks)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = hrPoints,
                metricType = TelemetryMetricType.HEART_RATE,
                currentDistance = null,
                onDistanceSelected = {},
                bSportType = BSportType.RUN,
                hrZoneThresholds = HeartRateZoneThresholds(0, 0, 0, 0),
                enableGestures = false,
                modifier = Modifier.testTag("${tagPrefix}_hr_unzoned")
            )
        }
    }

    private fun bitmapContainsColorNear(bitmap: Bitmap, target: Color, tolerance: Int = 20): Boolean {
        val targetArgb = target.toArgb()
        val tr = android.graphics.Color.red(targetArgb)
        val tg = android.graphics.Color.green(targetArgb)
        val tb = android.graphics.Color.blue(targetArgb)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val px = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(px) < 180) continue
                if (abs(android.graphics.Color.red(px) - tr) <= tolerance &&
                    abs(android.graphics.Color.green(px) - tg) <= tolerance &&
                    abs(android.graphics.Color.blue(px) - tb) <= tolerance
                ) {
                    return true
                }
            }
        }
        return false
    }
}
