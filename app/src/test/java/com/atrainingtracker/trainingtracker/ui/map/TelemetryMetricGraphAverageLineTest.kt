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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.HeartRateZoneThresholds
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.util.Locale

/**
 * Pure-JVM math, priority collision, narrow-canvas guard, and Robolectric Compose UI / Rule 8.5
 * Light & Dark/AMOLED visual verification for the dotted grey average reference line and `Ø` Y-axis
 * label on all [TelemetryMetricGraph] charts (`ATT-9170`, `REQ-UI-2797`, `TST-UI-2797`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w920dp-h1100dp-xhdpi")
class TelemetryMetricGraphAverageLineTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private companion object {
        val ORIGIN = LatLng(48.52, 9.05)
        const val SCREENSHOT_REL_PATH =
            "docs/engineering/test_evidence/tickets/ATT-9170/screenshots/att9170_telemetry_average_line_light_dark.png"
    }

    private fun resolveScreenshotFile(): File {
        val cwd = File(".").canonicalFile
        val repoRoot = if (cwd.name == "app") cwd.parentFile else cwd
        return File(repoRoot, SCREENSHOT_REL_PATH)
    }

    @Test
    fun computeWorkoutAverage_paceUsesMeanSpeedNotArithmeticMeanOfPointPaces() {
        // TST-UI-2797.1: 9 points at 3.3333 m/s (5:00/km) + 1 slow walking point at 0.6 m/s (27:47/km, clamped to 20:00)
        // + 1 standstill (0.0 m/s) + 1 null + 1 NaN.
        // Arithmetic mean of per-point paces would be (9*5.0 + 20.0)/10 = 6.50 min/km (6:30/km).
        // Speed-derived pace: meanSpeed = (9*(10/3) + 0.6)/10 = 3.06 m/s -> 1000/3.06/60 = 5.4466 min/km (5:27/km).
        val points = buildList {
            for (i in 0 until 9) {
                add(PathPoint(distance = i * 100.0, latLng = ORIGIN, timeSec = i * 30L, speedMps = 10.0 / 3.0))
            }
            add(PathPoint(distance = 900.0, latLng = ORIGIN, timeSec = 270L, speedMps = 0.6))
            add(PathPoint(distance = 900.0, latLng = ORIGIN, timeSec = 300L, speedMps = 0.0))
            add(PathPoint(distance = 900.0, latLng = ORIGIN, timeSec = 310L, speedMps = null))
            add(PathPoint(distance = 900.0, latLng = ORIGIN, timeSec = 320L, speedMps = Double.NaN))
        }

        val metricPace = TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.PACE, MyUnits.METRIC)
        assertNotNull(metricPace)
        val expectedMeanSpeedMps = 3.06
        val expectedMetricPace = (1000.0 / expectedMeanSpeedMps) / 60.0
        assertEquals(expectedMetricPace, metricPace!!, 1e-4)
        assertTrue("Speed-derived average pace must stay well below skewed arithmetic mean 6.50", metricPace < 5.6)
        assertEquals("Ø5:27", TelemetryMetricUtils.formatAverageYAxisLabel(metricPace, TelemetryMetricType.PACE, Locale.US))

        val imperialPace = TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.PACE, MyUnits.IMPERIAL)
        assertNotNull(imperialPace)
        val expectedImperialPace = (BANALService.METER_PER_MILE / expectedMeanSpeedMps) / 60.0
        assertEquals(expectedImperialPace, imperialPace!!, 1e-4)

        // Speed average in km/h must be the exact mathematical dual of meanSpeedMps
        val speedAvgKmh = TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.SPEED, MyUnits.METRIC)
        assertNotNull(speedAvgKmh)
        assertEquals(expectedMeanSpeedMps * 3.6, speedAvgKmh!!, 1e-4)
    }

    @Test
    fun computeWorkoutAverage_coversAllMetricsAndFiltersHrvArtifactsAndInvalidSamples() {
        // TST-UI-2797.1: Verify all supported TelemetryMetricType variants + HRV artifact filtering & fallback
        val points = listOf(
            PathPoint(
                distance = 0.0, latLng = ORIGIN, timeSec = 0L,
                hr = 140, power = 0, cadence = 80, speedMps = 5.0, slope = -3.0,
                wPrimeBalKj = 18.0, hrvDfaA1 = 0.90, hrvRmssd = 40.0, hrvArtifactRate = 1.0
            ),
            PathPoint(
                distance = 500.0, latLng = ORIGIN, timeSec = 100L,
                hr = 160, power = 200, cadence = 90, speedMps = 7.0, slope = 0.0,
                wPrimeBalKj = 14.0, hrvDfaA1 = 0.70, hrvRmssd = 30.0, hrvArtifactRate = 2.0
            ),
            PathPoint(
                distance = 1000.0, latLng = ORIGIN, timeSec = 200L,
                hr = 165, power = 280, cadence = 0, speedMps = 0.0, slope = 6.0,
                wPrimeBalKj = 10.0, hrvDfaA1 = 0.20, hrvRmssd = 190.0, hrvArtifactRate = 12.0 // high artifact (> 5.0%)
            ),
            PathPoint(
                distance = 1200.0, latLng = ORIGIN, timeSec = 240L,
                hr = 0, power = null, cadence = null, speedMps = Double.POSITIVE_INFINITY, slope = Double.NaN,
                wPrimeBalKj = Double.NaN, hrvDfaA1 = Double.NaN, hrvRmssd = Double.NEGATIVE_INFINITY, hrvArtifactRate = 0.0
            )
        )

        assertEquals(155.0, TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.HEART_RATE)!!, 1e-4)
        // Power includes 0 W coasting: (0 + 200 + 280) / 3 = 160 W
        assertEquals(160.0, TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.POWER)!!, 1e-4)
        // Cadence ignores 0 rpm: (80 + 90) / 2 = 85 rpm
        assertEquals(85.0, TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.CADENCE)!!, 1e-4)
        // Speed ignores 0.0 & +Inf: mean(5, 7) = 6 m/s = 21.6 km/h
        assertEquals(21.6, TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.SPEED, MyUnits.METRIC)!!, 1e-4)
        // Slope includes negative and 0%: (-3 + 0 + 6) / 3 = 1.0%
        assertEquals(1.0, TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.SLOPE)!!, 1e-4)
        // W'bal: (18 + 14 + 10) / 3 = 14.0 kJ
        assertEquals(14.0, TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.W_PRIME_BAL)!!, 1e-4)
        // HRV_DFA_A1 excludes artifact (> 5%): (0.90 + 0.70) / 2 = 0.80
        assertEquals(0.80, TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.HRV_DFA_A1)!!, 1e-4)
        // HRV_RMSSD excludes artifact (> 5%): (40.0 + 30.0) / 2 = 35.0 ms
        assertEquals(35.0, TelemetryMetricUtils.computeWorkoutAverage(points, TelemetryMetricType.HRV_RMSSD)!!, 1e-4)

        // All-artifact HRV fallback still returns finite average rather than silently disappearing
        val allArtifactHrv = listOf(
            PathPoint(distance = 0.0, latLng = ORIGIN, timeSec = 0L, hrvDfaA1 = 0.80, hrvRmssd = 25.0, hrvArtifactRate = 9.0),
            PathPoint(distance = 100.0, latLng = ORIGIN, timeSec = 30L, hrvDfaA1 = 0.60, hrvRmssd = 35.0, hrvArtifactRate = 8.0)
        )
        assertEquals(0.70, TelemetryMetricUtils.computeWorkoutAverage(allArtifactHrv, TelemetryMetricType.HRV_DFA_A1)!!, 1e-4)
        assertEquals(30.0, TelemetryMetricUtils.computeWorkoutAverage(allArtifactHrv, TelemetryMetricType.HRV_RMSSD)!!, 1e-4)

        // Empty / all-invalid returns null across every metric
        assertNull(TelemetryMetricUtils.computeWorkoutAverage(emptyList(), TelemetryMetricType.HEART_RATE))
        val invalidOnly = listOf(PathPoint(distance = 0.0, latLng = ORIGIN, timeSec = 0L, hr = 0, speedMps = 0.0, slope = Double.NaN))
        assertNull(TelemetryMetricUtils.computeWorkoutAverage(invalidOnly, TelemetryMetricType.HEART_RATE))
        assertNull(TelemetryMetricUtils.computeWorkoutAverage(invalidOnly, TelemetryMetricType.PACE))
        assertNull(TelemetryMetricUtils.computeWorkoutAverage(invalidOnly, TelemetryMetricType.SLOPE))
    }

    @Test
    fun formatAverageYAxisLabel_matchesMetricStandardFormatsAndLocales() {
        // TST-UI-2797.2: Ø-prefix formatting parity across metrics and locales
        assertEquals("Ø155", TelemetryMetricUtils.formatAverageYAxisLabel(154.8, TelemetryMetricType.HEART_RATE, Locale.GERMAN))
        assertEquals("Ø8:15", TelemetryMetricUtils.formatAverageYAxisLabel(8.25, TelemetryMetricType.PACE, Locale.US))
        assertEquals("Ø0,83", TelemetryMetricUtils.formatAverageYAxisLabel(0.83, TelemetryMetricType.HRV_DFA_A1, Locale.GERMAN))
        assertEquals("Ø0.83", TelemetryMetricUtils.formatAverageYAxisLabel(0.83, TelemetryMetricType.HRV_DFA_A1, Locale.US))
        assertEquals("Ø235", TelemetryMetricUtils.formatAverageYAxisLabel(235.2, TelemetryMetricType.POWER, Locale.US))
        assertEquals("Ø1%", TelemetryMetricUtils.formatAverageYAxisLabel(0.8, TelemetryMetricType.SLOPE, Locale.US))
        assertEquals("Ø0%", TelemetryMetricUtils.formatAverageYAxisLabel(-0.2, TelemetryMetricType.SLOPE, Locale.US))
        assertEquals("Ø-3%", TelemetryMetricUtils.formatAverageYAxisLabel(-2.7, TelemetryMetricType.SLOPE, Locale.US))
        assertEquals("Ø42", TelemetryMetricUtils.formatAverageYAxisLabel(42.0, TelemetryMetricType.HRV_RMSSD, Locale.US))
        assertEquals("Ø4,2", TelemetryMetricUtils.formatAverageYAxisLabel(4.2, TelemetryMetricType.HRV_RMSSD, Locale.GERMAN))
        assertEquals("", TelemetryMetricUtils.formatAverageYAxisLabel(Double.NaN, TelemetryMetricType.HEART_RATE, Locale.US))
    }

    @Test
    fun resolveVisibleAverageValue_hidesOffScaleAndNonFiniteValuesCleanly() {
        // TST-UI-2797.1: Constant anchor inside [dataMin, dataMax]; cleanly hidden when off-scale
        assertEquals(155.0, resolveVisibleAverageValue(155.0, 100.0, 190.0)!!, 1e-6)
        assertEquals(100.0, resolveVisibleAverageValue(100.0, 100.0, 190.0)!!, 1e-6)
        assertEquals(190.0, resolveVisibleAverageValue(190.0, 100.0, 190.0)!!, 1e-6)
        assertNull(resolveVisibleAverageValue(99.9, 100.0, 190.0))
        assertNull(resolveVisibleAverageValue(190.1, 100.0, 190.0))
        assertNull(resolveVisibleAverageValue(null, 100.0, 190.0))
        assertNull(resolveVisibleAverageValue(Double.NaN, 100.0, 190.0))
        assertNull(resolveVisibleAverageValue(150.0, 150.0, 150.0))
    }

    @Test
    fun resolveTelemetryYAxisLabels_enforcesAverageStrictTopPriorityOverExtremaThresholdsAndZones() {
        // TST-UI-2797.2: Ø average always wins over colliding min/max, DFA a1 thresholds, and ATT-9164 zone boundaries
        val ascent = -24f
        val descent = 6f
        val gap = 4f

        // Case A: Ø155 collides with simulated ATT-9164 HR zone boundary 157 (within 4px), while 130/143/170 stay clear
        val hrCandidates = listOf(
            TelemetryYAxisLabelCandidate("avg", "Ø155", baselineY = 92f, priority = Y_AXIS_PRIORITY_AVERAGE, alignment = TelemetryYAxisLabelAlignment.RIGHT_GUTTER),
            TelemetryYAxisLabelCandidate("zone_130", "130", baselineY = 175f, priority = Y_AXIS_PRIORITY_THRESHOLD),
            TelemetryYAxisLabelCandidate("zone_143", "143", baselineY = 135f, priority = Y_AXIS_PRIORITY_THRESHOLD),
            TelemetryYAxisLabelCandidate("zone_157", "157", baselineY = 88f, priority = Y_AXIS_PRIORITY_THRESHOLD),
            TelemetryYAxisLabelCandidate("zone_170", "170", baselineY = 45f, priority = Y_AXIS_PRIORITY_THRESHOLD),
            TelemetryYAxisLabelCandidate("top", "173", baselineY = 30f, priority = Y_AXIS_PRIORITY_EXTREMA),
            TelemetryYAxisLabelCandidate("bottom", "105", baselineY = 215f, priority = Y_AXIS_PRIORITY_EXTREMA)
        )
        val hrPlaced = resolveTelemetryYAxisLabels(hrCandidates, ascent = ascent, descent = descent, minVerticalGapPx = gap)
        val hrIds = hrPlaced.map { it.id }.toSet()
        assertTrue("Ø average must always be placed", "avg" in hrIds)
        assertFalse("Colliding zone boundary 157 near Ø155 must be suppressed", "zone_157" in hrIds)
        assertFalse("Colliding top extrema 173 near zone 170 must be suppressed", "top" in hrIds)
        assertTrue("Non-colliding zone 130 remains visible", "zone_130" in hrIds)
        assertTrue("Non-colliding zone 143 remains visible", "zone_143" in hrIds)
        assertTrue("Non-colliding zone 170 remains visible", "zone_170" in hrIds)
        assertTrue("Non-colliding bottom extrema remains visible", "bottom" in hrIds)
        assertNoVerticalOverlap(hrPlaced, gap)

        // Case B: Ø near top extrema suppresses top label
        val nearTop = resolveTelemetryYAxisLabels(
            candidates = listOf(
                TelemetryYAxisLabelCandidate("avg", "Ø170", baselineY = 34f, priority = Y_AXIS_PRIORITY_AVERAGE, alignment = TelemetryYAxisLabelAlignment.RIGHT_GUTTER),
                TelemetryYAxisLabelCandidate("top", "175", baselineY = 30f, priority = Y_AXIS_PRIORITY_EXTREMA),
                TelemetryYAxisLabelCandidate("bottom", "110", baselineY = 170f, priority = Y_AXIS_PRIORITY_EXTREMA)
            ),
            ascent = ascent,
            descent = descent,
            minVerticalGapPx = gap
        )
        assertEquals(setOf("avg", "bottom"), nearTop.map { it.id }.toSet())
        assertNoVerticalOverlap(nearTop, gap)

        // Case C: Ø near bottom extrema suppresses bottom label
        val nearBottom = resolveTelemetryYAxisLabels(
            candidates = listOf(
                TelemetryYAxisLabelCandidate("avg", "Ø0%", baselineY = 166f, priority = Y_AXIS_PRIORITY_AVERAGE, alignment = TelemetryYAxisLabelAlignment.RIGHT_GUTTER),
                TelemetryYAxisLabelCandidate("top", "12%", baselineY = 30f, priority = Y_AXIS_PRIORITY_EXTREMA),
                TelemetryYAxisLabelCandidate("bottom", "0%", baselineY = 170f, priority = Y_AXIS_PRIORITY_EXTREMA)
            ),
            ascent = ascent,
            descent = descent,
            minVerticalGapPx = gap
        )
        assertEquals(setOf("avg", "top"), nearBottom.map { it.id }.toSet())
        assertNoVerticalOverlap(nearBottom, gap)
    }

    @Test
    fun gutterAutoFitAndRightAlignment_preventOverflowIntoPlotArea() {
        // TST-UI-2797.2: Compact label stays at base size; wide label shrinks to fit inside 50.dp gutter
        assertEquals(28f, computeFittedGutterTextSize(28f, 60f, 88f), 1e-4f)
        val shrunk = computeFittedGutterTextSize(28f, 112f, 88f)
        assertEquals(22f, shrunk, 1e-4f)
        assertEquals(14f, computeFittedGutterTextSize(28f, 300f, 50f, minTextSizePx = 14f), 1e-4f)

        // Right-aligned X stays within [gutterLeftPx, gutterRightPx - width]
        assertEquals(32f, computeRightAlignedGutterX(textWidthPx = 60f, gutterLeftPx = 4f, gutterRightPx = 92f), 1e-4f)
        assertEquals(4f, computeRightAlignedGutterX(textWidthPx = 120f, gutterLeftPx = 4f, gutterRightPx = 92f), 1e-4f)
    }

    @Test
    fun drawAverageReferenceLine_guardsNarrowCanvasAndDrawsDistinctDottedPattern() {
        // TST-UI-2797.3: Guard plotWidth <= 0f (zero painted pixels) & verify dotted gaps on normal plot width
        composeTestRule.setContent {
            Row {
                Canvas(
                    modifier = Modifier
                        .size(width = 80.dp, height = 40.dp)
                        .background(Color.White)
                        .testTag("narrow_avg_canvas")
                ) {
                    drawAverageReferenceLine(
                        avgY = size.height / 2f,
                        plotStartX = 50.dp.toPx(),
                        plotWidth = 0f,
                        color = Color.DarkGray
                    )
                    drawAverageReferenceLine(
                        avgY = size.height / 2f,
                        plotStartX = 50.dp.toPx(),
                        plotWidth = -12f,
                        color = Color.DarkGray
                    )
                }
                Canvas(
                    modifier = Modifier
                        .size(width = 200.dp, height = 40.dp)
                        .background(Color.White)
                        .testTag("dotted_avg_canvas")
                ) {
                    drawAverageReferenceLine(
                        avgY = size.height / 2f,
                        plotStartX = 10.dp.toPx(),
                        plotWidth = size.width - 20.dp.toPx(),
                        color = Color.DarkGray
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val narrowBmp = composeTestRule.onNodeWithTag("narrow_avg_canvas").captureToImage().asAndroidBitmap()
        for (y in 0 until narrowBmp.height) {
            for (x in 0 until narrowBmp.width) {
                assertEquals(
                    "Narrow canvas (plotWidth <= 0f) must draw zero pixels",
                    android.graphics.Color.WHITE,
                    narrowBmp.getPixel(x, y)
                )
            }
        }

        val dottedBmp = composeTestRule.onNodeWithTag("dotted_avg_canvas").captureToImage().asAndroidBitmap()
        val midY = dottedBmp.height / 2
        val startX = (dottedBmp.width * 0.10f).toInt()
        val endX = (dottedBmp.width * 0.90f).toInt()
        var darkPixels = 0
        var whitePixels = 0
        var transitions = 0
        var prevDark = false
        for (x in startX until endX) {
            val isDark = dottedBmp.getPixel(x, midY) != android.graphics.Color.WHITE
            if (isDark) darkPixels++ else whitePixels++
            if (x > startX && isDark != prevDark) transitions++
            prevDark = isDark
        }
        assertTrue("Dotted average line must paint visible dot pixels ($darkPixels)", darkPixels > 10)
        assertTrue("Dotted average line must leave clear gap pixels ($whitePixels)", whitePixels > darkPixels)
        assertTrue("Dotted average line must alternate cleanly across plot ($transitions transitions)", transitions >= 20)
    }

    @Test
    fun renderTelemetryAverageLineGallery_lightAndDarkAmoled_andSaveVisualEvidencePng() {
        // TST-UI-2797.3 & Rule 8.5: Multi-metric Light & Dark/AMOLED visual evidence including HR, Pace, Slope,
        // DFA a1 collision suppression, and zoomed Workout Details constant-anchor verification.
        val hrPoints = listOf(
            PathPoint(distance = 0.0, latLng = ORIGIN, timeSec = 0L, hr = 128),
            PathPoint(distance = 400.0, latLng = ORIGIN, timeSec = 120L, hr = 148),
            PathPoint(distance = 800.0, latLng = ORIGIN, timeSec = 240L, hr = 166),
            PathPoint(distance = 1200.0, latLng = ORIGIN, timeSec = 360L, hr = 174),
            PathPoint(distance = 1600.0, latLng = ORIGIN, timeSec = 480L, hr = 158),
            PathPoint(distance = 2000.0, latLng = ORIGIN, timeSec = 600L, hr = 156)
        )
        val pacePoints = listOf(
            PathPoint(distance = 0.0, latLng = ORIGIN, timeSec = 0L, speedMps = 3.3333),
            PathPoint(distance = 400.0, latLng = ORIGIN, timeSec = 120L, speedMps = 3.45),
            PathPoint(distance = 800.0, latLng = ORIGIN, timeSec = 240L, speedMps = 0.60), // slow walking stop
            PathPoint(distance = 1200.0, latLng = ORIGIN, timeSec = 360L, speedMps = 3.3333),
            PathPoint(distance = 1600.0, latLng = ORIGIN, timeSec = 480L, speedMps = 3.55),
            PathPoint(distance = 2000.0, latLng = ORIGIN, timeSec = 600L, speedMps = 3.3333)
        )
        val slopePoints = listOf(
            PathPoint(distance = 0.0, latLng = ORIGIN, timeSec = 0L, slope = -4.0),
            PathPoint(distance = 400.0, latLng = ORIGIN, timeSec = 120L, slope = 0.0),
            PathPoint(distance = 800.0, latLng = ORIGIN, timeSec = 240L, slope = 5.5),
            PathPoint(distance = 1200.0, latLng = ORIGIN, timeSec = 360L, slope = 8.0),
            PathPoint(distance = 1600.0, latLng = ORIGIN, timeSec = 480L, slope = -2.5),
            PathPoint(distance = 2000.0, latLng = ORIGIN, timeSec = 600L, slope = 1.0)
        )
        // Average DFA a1 = (0.98 + 0.80 + 0.72 + 0.66 + 0.54) / 5 = 0.74 -> collides directly with T1 = 0.75!
        val dfaCollisionPoints = listOf(
            PathPoint(distance = 0.0, latLng = ORIGIN, timeSec = 0L, hrvDfaA1 = 0.98),
            PathPoint(distance = 500.0, latLng = ORIGIN, timeSec = 150L, hrvDfaA1 = 0.80),
            PathPoint(distance = 1000.0, latLng = ORIGIN, timeSec = 300L, hrvDfaA1 = 0.72),
            PathPoint(distance = 1500.0, latLng = ORIGIN, timeSec = 450L, hrvDfaA1 = 0.66),
            PathPoint(distance = 2000.0, latLng = ORIGIN, timeSec = 600L, hrvDfaA1 = 0.54)
        )
        val hrThresholds = HeartRateZoneThresholds(130, 143, 157, 170)

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF888888))
                    .padding(8.dp)
                    .testTag("att9170_gallery_root"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ATrainingTrackerTheme(darkTheme = false, amoled = false, setWindowColors = false) {
                    AverageLineShowcaseColumn(
                        title = "Light Mode — ATT-9170 Average Line & Ø Label",
                        hrPoints = hrPoints,
                        pacePoints = pacePoints,
                        slopePoints = slopePoints,
                        dfaCollisionPoints = dfaCollisionPoints,
                        hrThresholds = hrThresholds,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("att9170_light_column")
                    )
                }
                ATrainingTrackerTheme(darkTheme = true, amoled = true, setWindowColors = false) {
                    AverageLineShowcaseColumn(
                        title = "Dark / AMOLED — ATT-9170 Average Line & Ø Label",
                        hrPoints = hrPoints,
                        pacePoints = pacePoints,
                        slopePoints = slopePoints,
                        dfaCollisionPoints = dfaCollisionPoints,
                        hrThresholds = hrThresholds,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("att9170_dark_column")
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        val compositeBmp = composeTestRule.onNodeWithTag("att9170_gallery_root").captureToImage().asAndroidBitmap()
        val lightBmp = composeTestRule.onNodeWithTag("att9170_light_column").captureToImage().asAndroidBitmap()
        val darkBmp = composeTestRule.onNodeWithTag("att9170_dark_column").captureToImage().asAndroidBitmap()

        assertTrue("Light render must have positive dimensions", lightBmp.width > 200 && lightBmp.height > 400)
        assertTrue("Dark AMOLED render must have positive dimensions", darkBmp.width > 200 && darkBmp.height > 400)

        val outFile = resolveScreenshotFile()
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            compositeBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue("Rule 8.5 screenshot must exist and be non-empty", outFile.exists() && outFile.length() > 10_000L)
    }

    @Composable
    private fun AverageLineShowcaseColumn(
        title: String,
        hrPoints: List<PathPoint>,
        pacePoints: List<PathPoint>,
        slopePoints: List<PathPoint>,
        dfaCollisionPoints: List<PathPoint>,
        hrThresholds: HeartRateZoneThresholds,
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
                text = "Heart Rate (Zones + Ø155)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = hrPoints,
                metricType = TelemetryMetricType.HEART_RATE,
                currentDistance = null,
                onDistanceSelected = {},
                bSportType = BSportType.RUN,
                hrZoneThresholds = hrThresholds,
                enableGestures = false
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Pace (Speed-derived Ø vs Slow Walk)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = pacePoints,
                metricType = TelemetryMetricType.PACE,
                currentDistance = null,
                onDistanceSelected = {},
                bSportType = BSportType.RUN,
                enableGestures = false
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Slope (Dashed 0% vs Dotted Ø1%)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = slopePoints,
                metricType = TelemetryMetricType.SLOPE,
                currentDistance = null,
                onDistanceSelected = {},
                enableGestures = false
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "DFA α1 (Collision: Ø0.74 suppresses 0.75)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = dfaCollisionPoints,
                metricType = TelemetryMetricType.HRV_DFA_A1,
                currentDistance = null,
                onDistanceSelected = {},
                dfaA1ThresholdT1 = 0.75f,
                dfaA1ThresholdT2 = 0.50f,
                enableGestures = false
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Heart Rate — Zoomed 2.5x (Constant Ø155 Anchor)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TelemetryMetricGraph(
                pathPoints = hrPoints,
                metricType = TelemetryMetricType.HEART_RATE,
                currentDistance = 1200.0,
                onDistanceSelected = {},
                bSportType = BSportType.RUN,
                zoomScale = 2.5f,
                startDist = 800.0,
                hrZoneThresholds = hrThresholds,
                enableGestures = false
            )
        }
    }

    private fun assertNoVerticalOverlap(placed: List<PlacedTelemetryYAxisLabel>, minGap: Float) {
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                val a = placed[i]
                val b = placed[j]
                val separated = a.boxBottom + minGap <= b.boxTop + 1e-3f || b.boxBottom + minGap <= a.boxTop + 1e-3f
                assertTrue(
                    "Labels '${a.id}' [${a.boxTop}..${a.boxBottom}] and '${b.id}' [${b.boxTop}..${b.boxBottom}] must not overlap",
                    separated
                )
            }
        }
    }
}
