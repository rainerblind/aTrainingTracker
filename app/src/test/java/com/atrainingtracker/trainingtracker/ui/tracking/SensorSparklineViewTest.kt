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

package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.atrainingtracker.trainingtracker.ui.theme.TelemetryGraphColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SensorSparklineView] helper functions verifying relative time offset
 * formatting and container height resolution (TST-TRK-059.9b, REQ-TRK-059.9, ATT-6695).
 */
class SensorSparklineViewTest {

    @Test
    fun testFormatSparklineTimeOffset_subHour() {
        assertEquals("-0:00", formatSparklineTimeOffset(0))
        assertEquals("-0:00", formatSparklineTimeOffset(-5))
        assertEquals("-0:40", formatSparklineTimeOffset(40))
        assertEquals("-1:20", formatSparklineTimeOffset(80))
        assertEquals("-2:30", formatSparklineTimeOffset(150))
        assertEquals("-5:00", formatSparklineTimeOffset(300))
        assertEquals("-6:57", formatSparklineTimeOffset(417))
        assertEquals("-13:54", formatSparklineTimeOffset(834))
        assertEquals("-59:59", formatSparklineTimeOffset(3599))
    }

    @Test
    fun testFormatSparklineTimeOffset_multiHour() {
        assertEquals("-1:00:00", formatSparklineTimeOffset(3600))
        assertEquals("-1:00:01", formatSparklineTimeOffset(3601))
        assertEquals("-1:24:30", formatSparklineTimeOffset(5070))
        assertEquals("-2:00:00", formatSparklineTimeOffset(7200))
        assertEquals("-10:10:15", formatSparklineTimeOffset(36615))
    }

    @Test
    fun testResolveSparklineHeight() {
        assertEquals(26.dp, resolveSparklineHeight(ViewSize.XSMALL, isGraphOnly = false))
        assertEquals(26.dp, resolveSparklineHeight(ViewSize.SMALL, isGraphOnly = false))
        assertEquals(34.dp, resolveSparklineHeight(ViewSize.NORMAL, isGraphOnly = false))
        assertEquals(44.dp, resolveSparklineHeight(ViewSize.LARGE, isGraphOnly = false))
        assertEquals(52.dp, resolveSparklineHeight(ViewSize.XLARGE, isGraphOnly = false))

        assertEquals(24.dp, resolveSparklineHeight(ViewSize.SMALL, isGraphOnly = true))
        assertEquals(36.dp, resolveSparklineHeight(ViewSize.NORMAL, isGraphOnly = true))
        assertEquals(52.dp, resolveSparklineHeight(ViewSize.LARGE, isGraphOnly = true))
        assertEquals(68.dp, resolveSparklineHeight(ViewSize.XLARGE, isGraphOnly = true))
    }

    @Test
    fun testDefaultPreviewSparklineSamples_properties() {
        assertEquals("Representative curve must contain 24 samples", 24, DEFAULT_PREVIEW_SPARKLINE_SAMPLES.size)
        val min = DEFAULT_PREVIEW_SPARKLINE_SAMPLES.minOrNull() ?: 0f
        val max = DEFAULT_PREVIEW_SPARKLINE_SAMPLES.maxOrNull() ?: 0f
        assertTrue("Preview samples must vary to produce a visible curve", max > min)
    }

    @Test
    fun testResolveSparklineIsPlaceholder_contracts() {
        // Inactive sensor during live tracking must render placeholder curve (REQ-TRK-059.11, ATT-7522)
        assertTrue(
            "Inactive sensor during live tracking must trigger placeholder mode",
            resolveSparklineIsPlaceholder(hasLiveSamples = false, screenMode = ScreenMode.TRACKING)
        )

        // Inactive sensor in configuration mode must render standard preview, not placeholder
        assertFalse(
            "Inactive sensor in configuration mode must not trigger placeholder mode",
            resolveSparklineIsPlaceholder(hasLiveSamples = false, screenMode = ScreenMode.CONFIGURATION)
        )
        assertFalse(
            "Inactive sensor in preview mode must not trigger placeholder mode",
            resolveSparklineIsPlaceholder(hasLiveSamples = false, screenMode = ScreenMode.PREVIEW)
        )

        // Active sensor with live telemetry must never trigger placeholder mode
        assertFalse(
            "Active sensor during live tracking must not trigger placeholder mode",
            resolveSparklineIsPlaceholder(hasLiveSamples = true, screenMode = ScreenMode.TRACKING)
        )
        assertFalse(
            "Active sensor in configuration mode must not trigger placeholder mode",
            resolveSparklineIsPlaceholder(hasLiveSamples = true, screenMode = ScreenMode.CONFIGURATION)
        )
    }

    @Test
    fun testResolveSparklineEffectiveSamples_fallbackAndSupersedence() {
        // Empty live samples fall back to synthetic preview curve
        assertEquals(
            DEFAULT_PREVIEW_SPARKLINE_SAMPLES,
            resolveSparklineEffectiveSamples(emptyList())
        )

        // Genuine live telemetry supersedes preview curve
        val liveSamples = listOf(140f, 142f, 145f)
        assertEquals(
            liveSamples,
            resolveSparklineEffectiveSamples(liveSamples)
        )
    }

    @Test
    fun testResolveSparklineEffectiveSpanSeconds_fallbackAndSupersedence() {
        // Empty live samples use configured horizon seconds
        assertEquals(300, resolveSparklineEffectiveSpanSeconds(emptyList(), liveSpanSeconds = 0, graphHorizonSeconds = 300))
        assertEquals(60, resolveSparklineEffectiveSpanSeconds(emptyList(), liveSpanSeconds = 0, graphHorizonSeconds = 60))
        assertEquals(300, resolveSparklineEffectiveSpanSeconds(emptyList(), liveSpanSeconds = 0, graphHorizonSeconds = 0))

        // Genuine live telemetry uses actual elapsed span seconds
        val liveSamples = listOf(150f, 152f)
        assertEquals(42, resolveSparklineEffectiveSpanSeconds(liveSamples, liveSpanSeconds = 42, graphHorizonSeconds = 300))
    }

    @Test
    fun testSensorSparklineView_composableSignatureSupportsIsPlaceholder() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.SensorSparklineViewKt")
        val composableMethod = clazz.declaredMethods.find { it.name.startsWith("SensorSparklineView") }
        assertNotNull("SensorSparklineView composable must exist", composableMethod)
    }

    /**
     * TST-TRK-081.2: resolveSparklineLineColor verifies deterministic resolution for
     * DYNAMIC_ZONE, THEME_DEFAULT, GLOBAL_DEFAULT, and explicit palette tokens.
     */
    @Test
    fun testResolveSparklineLineColor_allTokens() {
        // TST-TRK-081
        val colorScheme = lightColorScheme(primary = Color.Blue)

        // 1. THEME_DEFAULT returns primary
        assertEquals(
            Color.Blue,
            resolveSparklineLineColor("THEME_DEFAULT", zoneColor = Color.Red, sensorType = SensorType.HR, colorScheme = colorScheme)
        )

        // 2. DYNAMIC_ZONE returns active zoneColor if valid, else falls back to primary
        assertEquals(
            Color.Green,
            resolveSparklineLineColor("DYNAMIC_ZONE", zoneColor = Color.Green, sensorType = SensorType.HR, colorScheme = colorScheme)
        )
        assertEquals(
            Color.Blue,
            resolveSparklineLineColor("DYNAMIC_ZONE", zoneColor = Color.Transparent, sensorType = SensorType.HR, colorScheme = colorScheme)
        )

        // 3. Explicit palette token returns resolved color
        val crimsonLight = TelemetryGraphColor.CRIMSON.lightColor
        assertEquals(
            crimsonLight,
            resolveSparklineLineColor("CRIMSON", zoneColor = Color.Transparent, sensorType = SensorType.HR, colorScheme = colorScheme)
        )

        // 4. Unknown/malformed token safely falls back to primary
        assertEquals(
            Color.Blue,
            resolveSparklineLineColor("NON_EXISTENT_COLOR", zoneColor = Color.Transparent, sensorType = SensorType.HR, colorScheme = colorScheme)
        )
    }

    /**
     * TST-TRK-082.1: Complete decoupling from text zone coloring (showTextColor removed),
     * ensuring GLOBAL_DEFAULT with sensorType == null returns primary rather than zoneColor.
     */
    @Test
    fun testResolveSparklineLineColor_decoupledFromTextZoneColor() {
        // TST-TRK-082
        val lightScheme = lightColorScheme(primary = Color.Blue, surface = Color.White)
        val darkScheme = darkColorScheme(primary = Color.Cyan, surface = Color.Black, onSurface = Color.White)

        // Even with an active zoneColor and sensorType == null, GLOBAL_DEFAULT never leaks zoneColor
        assertEquals(
            Color.Blue,
            resolveSparklineLineColor("GLOBAL_DEFAULT", zoneColor = Color.Red, sensorType = null, colorScheme = lightScheme)
        )
        // Explicit fixed palette token ignores zoneColor
        assertEquals(
            TelemetryGraphColor.VIOLET.lightColor,
            resolveSparklineLineColor("VIOLET", zoneColor = TTColor.Zone4, sensorType = SensorType.HR, colorScheme = lightScheme)
        )
        // DYNAMIC_ZONE substitutes onSurface for TTColor.Zone7 on dark/AMOLED surfaces
        assertEquals(
            Color.White,
            resolveSparklineLineColor("DYNAMIC_ZONE", zoneColor = TTColor.Zone7, sensorType = SensorType.POWER, colorScheme = darkScheme)
        )
        assertEquals(
            TTColor.Zone7,
            resolveSparklineLineColor("DYNAMIC_ZONE", zoneColor = TTColor.Zone7, sensorType = SensorType.POWER, colorScheme = lightScheme)
        )
    }

    /**
     * TST-TRK-082.2: HR (4 cutoffs -> Z1..Z5) and Power (6 cutoffs -> Z1..Z7) vertical gradient
     * stops are monotonically non-decreasing in [0f, 1f] and terminal dot matches latest sample zone.
     */
    @Test
    fun testBuildDynamicZoneVerticalGradientStops_hrAndPower() {
        // TST-TRK-082
        val lightScheme = lightColorScheme(primary = Color.Blue, surface = Color.White)
        val darkScheme = darkColorScheme(primary = Color.Cyan, surface = Color.Black, onSurface = Color.White)

        val hrThresholds = SparklineZoneThresholds(
            cutoffs = listOf(132f, 145f, 158f, 171f),
            zoneColors = listOf(TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4, TTColor.Zone5)
        )
        val hrStops = buildDynamicZoneVerticalGradientStops(120f, 180f, hrThresholds, lightScheme)
        assertNotNull(hrStops)
        assertEquals(0f, hrStops!!.first().first, 0.0001f)
        assertEquals(TTColor.Zone5, hrStops.first().second)
        assertEquals(1f, hrStops.last().first, 0.0001f)
        assertEquals(TTColor.Zone1, hrStops.last().second)
        for (i in 1 until hrStops.size) {
            assertTrue("Stops must be non-decreasing", hrStops[i].first >= hrStops[i - 1].first)
        }

        // Power 7-zone with dark/AMOLED Zone7 contrast substitution
        val pwrThresholds = SparklineZoneThresholds(
            cutoffs = listOf(149f, 203f, 243f, 284f, 324f, 405f),
            zoneColors = listOf(
                TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4,
                TTColor.Zone5, TTColor.Zone6, TTColor.Zone7
            )
        )
        val pwrDarkStops = buildDynamicZoneVerticalGradientStops(120f, 450f, pwrThresholds, darkScheme)
        assertNotNull(pwrDarkStops)
        assertEquals(TTColor.Zone7.let { Color.White }, pwrDarkStops!!.first().second)
        assertNotEquals(TTColor.Zone7, pwrDarkStops.first().second)
        assertEquals(TTColor.Zone1, pwrDarkStops.last().second)

        val pwrLightStops = buildDynamicZoneVerticalGradientStops(120f, 450f, pwrThresholds, lightScheme)
        assertEquals(TTColor.Zone7, pwrLightStops!!.first().second)

        // Terminal dot color resolves to latest sample's zone
        val styling = resolveSparklineDynamicStyling(
            samples = listOf(130f, 260f, 420f),
            isPlaceholder = false,
            zoneThresholds = pwrThresholds,
            colorScheme = darkScheme,
            fallbackLineColor = Color.Cyan
        )
        assertEquals(Color.White, styling.terminalDotColor)
    }

    /**
     * TST-TRK-082.2: HRV DFA alpha 1 inverted polarity maps alpha1 > t1 to Zone2 (green) at top
     * and alpha1 < t2 to Zone4 (red) at bottom.
     */
    @Test
    fun testBuildDynamicZoneVerticalGradientStops_dfaAlpha1InvertedPolarity() {
        // TST-TRK-082
        val lightScheme = lightColorScheme(primary = Color.Blue, surface = Color.White)
        val dfaThresholds = SparklineZoneThresholds(
            cutoffs = listOf(0.50f, 0.75f),
            zoneColors = listOf(TTColor.Zone4, TTColor.Zone3, TTColor.Zone2),
            invertedPolarity = true
        )

        val stops = buildDynamicZoneVerticalGradientStops(0.35f, 1.05f, dfaThresholds, lightScheme)
        assertNotNull(stops)
        // Top (maxVal = 1.05 > t1) -> Green (Zone2); Bottom (minVal = 0.35 < t2) -> Red (Zone4)
        assertEquals(TTColor.Zone2, stops!!.first().second)
        assertEquals(TTColor.Zone4, stops.last().second)

        // Boundary polarity: exactly t1 (0.75f) -> Zone3; exactly t2 (0.50f) -> Zone3; < t2 -> Zone4
        assertEquals(TTColor.Zone2, resolveSparklineZoneColorForValue(0.76f, dfaThresholds, lightScheme))
        assertEquals(TTColor.Zone3, resolveSparklineZoneColorForValue(0.75f, dfaThresholds, lightScheme))
        assertEquals(TTColor.Zone3, resolveSparklineZoneColorForValue(0.50f, dfaThresholds, lightScheme))
        assertEquals(TTColor.Zone4, resolveSparklineZoneColorForValue(0.49f, dfaThresholds, lightScheme))
    }

    /**
     * TST-TRK-082.2 & REQ-TRK-082.3: Edge-case fallbacks (single point, flat line, placeholder,
     * null/malformed thresholds) render cleanly with zero invalid stops or divide-by-zero errors.
     */
    @Test
    fun testDynamicZoneStyling_edgeCaseFallbacks() {
        // TST-TRK-082
        val lightScheme = lightColorScheme(primary = Color.Blue, surface = Color.White)
        val hrThresholds = SparklineZoneThresholds(
            cutoffs = listOf(132f, 145f, 158f, 171f),
            zoneColors = listOf(TTColor.Zone1, TTColor.Zone2, TTColor.Zone3, TTColor.Zone4, TTColor.Zone5)
        )

        // Placeholder suppresses gradient stops and uses fallbackLineColor
        val placeholder = resolveSparklineDynamicStyling(
            samples = DEFAULT_PREVIEW_SPARKLINE_SAMPLES,
            isPlaceholder = true,
            zoneThresholds = hrThresholds,
            colorScheme = lightScheme,
            fallbackLineColor = Color.Blue
        )
        assertNull(placeholder.gradientStops)
        assertEquals(Color.Blue, placeholder.solidColor)

        // Single sample suppresses gradient stops and resolves single sample zone color
        val singlePoint = resolveSparklineDynamicStyling(
            samples = listOf(160f),
            isPlaceholder = false,
            zoneThresholds = hrThresholds,
            colorScheme = lightScheme,
            fallbackLineColor = Color.Blue
        )
        assertNull(singlePoint.gradientStops)
        assertEquals(TTColor.Zone4, singlePoint.terminalDotColor)

        // Flat line (minVal == maxVal, range == 0f) suppresses gradient stops and resolves active zone
        val flatLine = resolveSparklineDynamicStyling(
            samples = listOf(150f, 150f, 150f),
            isPlaceholder = false,
            zoneThresholds = hrThresholds,
            colorScheme = lightScheme,
            fallbackLineColor = Color.Blue
        )
        assertNull(flatLine.gradientStops)
        assertEquals(TTColor.Zone3, flatLine.solidColor)
        assertEquals(TTColor.Zone3, flatLine.terminalDotColor)

        // Missing thresholds fall back to solid fallbackLineColor
        val missingThresholds = resolveSparklineDynamicStyling(
            samples = listOf(120f, 150f, 180f),
            isPlaceholder = false,
            zoneThresholds = null,
            colorScheme = lightScheme,
            fallbackLineColor = Color.Magenta
        )
        assertNull(missingThresholds.gradientStops)
        assertEquals(Color.Magenta, missingThresholds.solidColor)
        assertEquals(Color.Magenta, missingThresholds.terminalDotColor)

        // Zero-range buildDynamicZoneVerticalGradientStops returns null safely
        assertNull(buildDynamicZoneVerticalGradientStops(150f, 150f, hrThresholds, lightScheme))
    }
}
