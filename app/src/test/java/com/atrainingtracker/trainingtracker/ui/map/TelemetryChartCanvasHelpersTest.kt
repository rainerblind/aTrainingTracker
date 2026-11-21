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
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.settings.ProfileXAxisDomain
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.LinearHistogramBin
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.PowerZoneThresholds
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramBin
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneCardDisplayMode
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneDistributionData
import com.atrainingtracker.trainingtracker.ui.aftermath.zones.ZoneTimeEntry
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 * Behavioral unit and Robolectric Compose UI verification suite for [drawMarginalHistogramBars],
 * [drawXAxisTicks], [computeMarginalBarVerticalBounds], and [computeXAxisTickValues]
 * (ATT-6041, REQ-UI-2789.1, REQ-UI-2789.2, TST-UI-2789, TST-UI-2789.1, TST-UI-2789.3).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class TelemetryChartCanvasHelpersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private companion object {
        const val PREVIEW_HOST_TAG = "att_6041_charts_preview_host"
        const val HISTOGRAM_CANVAS_TAG = "att_6041_histogram_canvas"
        const val TICK_CANVAS_TAG = "att_6041_tick_canvas"
        const val PREVIEW_PNG_REL_PATH = "docs/engineering/walkthroughs/img/ATT-6041_charts_marginal_ticks_preview.png"
        const val PREVIEW_COL_WIDTH_DP = 411
        const val PREVIEW_GAP_DP = 12
        const val PREVIEW_PADDING_DP = 12
        const val PNG_QUALITY = 100
    }

    private fun resolvePreviewOutputFile(): File {
        val cwd = File(".").canonicalFile
        val repoRoot = if (cwd.name == "app") cwd.parentFile else cwd
        return File(repoRoot, PREVIEW_PNG_REL_PATH)
    }

    @Test
    fun computeMarginalBarVerticalBounds_filtersOutOfDomainAndClampsWithoutZeroHeightStacking() {
        // TST-UI-2789.1: Rule 70 domain filtering & clamping prior to minBarHeightPx
        val valueToY: (Double) -> Float = { v -> ((400.0 - v) / 400.0 * 100.0).toFloat() }

        // Out-of-domain below and above [0.0, 400.0] -> strictly null even with minBarHeightPx = 1f
        assertNull(computeMarginalBarVerticalBounds(-50.0, 0.0, 0.0, 400.0, valueToY, minBarHeightPx = 1f))
        assertNull(computeMarginalBarVerticalBounds(400.0, 800.0, 0.0, 400.0, valueToY, minBarHeightPx = 1f))
        assertNull(computeMarginalBarVerticalBounds(450.0, 500.0, 0.0, 400.0, valueToY, minBarHeightPx = 1f))

        // Straddling upper boundary [350, 500] clamps to [350, 400] -> y in [0f, 12.5f]
        val clamped = computeMarginalBarVerticalBounds(350.0, 500.0, 0.0, 400.0, valueToY)
        assertNotNull(clamped)
        assertEquals(0f, clamped!!.rectTop, 0.01f)
        assertEquals(12.5f, clamped.rectHeight, 0.01f)

        // Zero-height projected bin returns null BEFORE minBarHeightPx is applied
        val flatProjection: (Double) -> Float = { 50f }
        assertNull(computeMarginalBarVerticalBounds(100.0, 200.0, 0.0, 400.0, flatProjection, minBarHeightPx = 1f))
    }

    @Test
    fun computeMarginalBarVerticalBounds_appliesMinBarHeightOnlyToPositiveInDomainBins() {
        // TST-UI-2789.1: thin positive in-domain bin obeys minBarHeightPx cleanly
        val valueToY: (Double) -> Float = { v -> ((1000.0 - v) / 1000.0 * 40.0).toFloat() }
        val withoutMin = computeMarginalBarVerticalBounds(100.0, 110.0, 0.0, 1000.0, valueToY, minBarHeightPx = 0f)
        val withMin = computeMarginalBarVerticalBounds(100.0, 110.0, 0.0, 1000.0, valueToY, minBarHeightPx = 1f)

        assertNotNull(withoutMin)
        assertNotNull(withMin)
        assertEquals(0.4f, withoutMin!!.rectHeight, 0.01f)
        assertEquals(1.0f, withMin!!.rectHeight, 0.01f)
    }

    @Test
    fun computeXAxisTickValues_handlesValidStepAndGuardsInvalidInputs() {
        // TST-UI-2789.1: Guard non-positive/non-finite step & visibleSpan + exact interior stride
        assertEquals(emptyList<Double>(), computeXAxisTickValues(0.0, 1000.0, 0.0))
        assertEquals(emptyList<Double>(), computeXAxisTickValues(0.0, 1000.0, -50.0))
        assertEquals(emptyList<Double>(), computeXAxisTickValues(0.0, 0.0, 100.0))
        assertEquals(emptyList<Double>(), computeXAxisTickValues(0.0, Double.NaN, 100.0))
        assertEquals(emptyList<Double>(), computeXAxisTickValues(Double.POSITIVE_INFINITY, 1000.0, 100.0))

        // Strictly interior multiples in (0.0, 5000.0) with step 1000.0
        assertEquals(listOf(1000.0, 2000.0, 3000.0, 4000.0), computeXAxisTickValues(0.0, 5000.0, 1000.0))
        // Non-zero startValue 350.0, span 1000.0, step 250.0 -> (350, 1350) -> 500, 750, 1000, 1250
        assertEquals(listOf(500.0, 750.0, 1000.0, 1250.0), computeXAxisTickValues(350.0, 1000.0, 250.0))
    }

    @Test
    fun formatElevationDistanceTickLabel_formatsMetricAndImperialWithLocale() {
        // TST-UI-2789.1: ElevationProfile locale-aware formatting parity
        assertEquals("500m", formatElevationDistanceTickLabel(500.0, 1000.0, MyUnits.METRIC, Locale.US))
        assertEquals("2", formatElevationDistanceTickLabel(2000.0, 5000.0, MyUnits.METRIC, Locale.US))
        assertEquals("1.5", formatElevationDistanceTickLabel(1500.0, 5000.0, MyUnits.METRIC, Locale.US))
        assertEquals("1,5", formatElevationDistanceTickLabel(1500.0, 5000.0, MyUnits.METRIC, Locale.GERMAN))
        assertEquals("1", formatElevationDistanceTickLabel(1609.344, 5000.0, MyUnits.IMPERIAL, Locale.US))
    }

    @Test
    fun drawMarginalHistogramBars_rendersUnconditionalTrackAndGatesForegroundOnDuration() {
        // TST-UI-2789.1: Behavioral Canvas pixel verification of Rule 69 & Rule 70
        val drawnBins = mutableListOf<Int>()
        val bins = listOf(
            // Out-of-domain above dataMax=100 -> must NOT paint at y=0
            LinearHistogramBin(0, 120.0, 200.0, 100L, 100f, TTColor.Zone5),
            // Upper half [50, 100]: durationSec = 0L -> track (alpha 0.15f) only, no solid foreground
            LinearHistogramBin(1, 50.0, 100.0, 0L, 0f, TTColor.Zone4),
            // Lower half [0, 50]: durationSec = 60L -> full solid foreground bar across width
            LinearHistogramBin(2, 0.0, 50.0, 60L, 100f, TTColor.Zone2)
        )

        composeTestRule.setContent {
            Canvas(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.White)
                    .testTag(HISTOGRAM_CANVAS_TAG)
            ) {
                drawnBins.clear()
                drawMarginalHistogramBars(
                    bins = bins,
                    dataMin = 0.0,
                    dataMax = 100.0,
                    stripStartPx = 0f,
                    stripWidthPx = size.width,
                    valueToY = { v -> ((100.0 - v) / 100.0 * size.height).toFloat() },
                    rangeMinOf = { it.rangeMin },
                    rangeMaxOf = { it.rangeMax },
                    durationSecOf = { it.durationSec },
                    percentageOf = { it.percentage },
                    colorOf = { it.color },
                    onBinDrawn = { bin, _, _ -> drawnBins.add(bin.binIndex) }
                )
            }
        }

        val bitmap = composeTestRule.onNodeWithTag(HISTOGRAM_CANVAS_TAG).captureToImage().asAndroidBitmap()
        // Only in-domain bins 1 and 2 are drawn; out-of-domain bin 0 is skipped
        assertEquals(listOf(1, 2), drawnBins)

        // Upper half (duration=0) is tinted softly (not pure white, not full Zone4 red)
        val upperPixel = bitmap.getPixel(bitmap.width / 2, bitmap.height / 4)
        assertNotEquals(android.graphics.Color.WHITE, upperPixel)
        assertNotEquals(TTColor.Zone4.toArgb(), upperPixel)

        // Lower half (duration=60, 100%) is solid Zone2 green across the bar
        val lowerPixel = bitmap.getPixel(bitmap.width / 2, (bitmap.height * 3) / 4)
        assertEquals(TTColor.Zone2.toArgb(), lowerPixel)
    }

    @Test
    fun drawXAxisTicks_separatesGridlinesFromLabelCollisionGating() {
        // TST-UI-2789.1: Behavioral verification that onTickInBounds runs for all ticks while onLabelRendered obeys collision
        var inBoundsCount = 0
        val renderedLabels = mutableListOf<String>()
        val paint = Paint().apply { textSize = 24f }

        composeTestRule.setContent {
            Canvas(
                modifier = Modifier
                    .size(width = 200.dp, height = 60.dp)
                    .testTag(TICK_CANVAS_TAG)
            ) {
                inBoundsCount = 0
                renderedLabels.clear()
                drawXAxisTicks(
                    startValue = 0.0,
                    visibleSpan = 5000.0,
                    step = 1000.0,
                    plotStartX = 20f,
                    plotWidth = size.width - 40f,
                    paint = paint,
                    labelY = size.height - 4f,
                    initialLastDrawnRightX = 20f,
                    formatLabel = { "${(it / 1000.0).toInt()}k" },
                    shouldRenderLabel = { labelLeft, _, lastRight ->
                        // Force wide spacing so some in-bounds ticks skip labels
                        labelLeft >= lastRight + (size.width * 0.45f)
                    },
                    onTickInBounds = { inBoundsCount++ },
                    onLabelRendered = { _, label, _, _ -> renderedLabels.add(label) }
                )
            }
        }

        composeTestRule.onNodeWithTag(TICK_CANVAS_TAG).captureToImage()
        assertEquals("All 4 interior ticks must invoke onTickInBounds", 4, inBoundsCount)
        assertTrue(
            "Collision gating must render fewer labels than total ticks (rendered=$renderedLabels)",
            renderedLabels.isNotEmpty() && renderedLabels.size < inBoundsCount
        )
    }

    private fun samplePathPoints(): List<PathPoint> = (0..60).map { idx ->
        val dist = idx * 100.0
        val alt = 500.0 + kotlin.math.sin(idx / 8.0) * 65.0 + idx * 1.2
        val power = (180 + kotlin.math.sin(idx / 5.0) * 75 + if (idx in 40..44) 160 else 0).toInt()
        PathPoint(
            distance = dist,
            latLng = LatLng(48.1351 + idx * 0.0005, 11.5820 + idx * 0.0006),
            altitude = alt,
            timeSec = idx * 15L,
            hr = 140 + (idx % 25),
            power = power,
            speedMps = 7.5 + (idx % 5) * 0.4
        )
    }

    private fun sampleZoneDistribution(): ZoneDistributionData {
        val linearBins = listOf(
            TelemetryHistogramBin(0, 100, 140, 120L, 12f, 1, TTColor.Zone1),
            TelemetryHistogramBin(1, 140, 180, 260L, 26f, 2, TTColor.Zone2),
            TelemetryHistogramBin(2, 180, 220, 310L, 31f, 3, TTColor.Zone3),
            TelemetryHistogramBin(3, 220, 260, 180L, 18f, 4, TTColor.Zone4),
            TelemetryHistogramBin(4, 260, 300, 0L, 0f, 5, TTColor.Zone5),
            TelemetryHistogramBin(5, 300, 360, 90L, 9f, 6, TTColor.Zone6),
            // Out-of-domain sprint spike bin (Rule 70)
            TelemetryHistogramBin(6, 650, 750, 10L, 1f, 7, TTColor.Zone7)
        )
        return ZoneDistributionData(
            totalActiveTimeSec = 970L,
            entries = listOf(
                ZoneTimeEntry(1, 0, 120L, 12f, TTColor.Zone1),
                ZoneTimeEntry(2, 0, 260L, 26f, TTColor.Zone2),
                ZoneTimeEntry(3, 0, 310L, 31f, TTColor.Zone3),
                ZoneTimeEntry(4, 0, 180L, 18f, TTColor.Zone4),
                ZoneTimeEntry(5, 0, 100L, 10f, TTColor.Zone5)
            ),
            histogram = TelemetryHistogramData(
                binWidth = 40,
                dataMin = 100,
                dataMax = 750,
                totalActiveTimeSec = 970L,
                bins = linearBins
            )
        )
    }

    @Composable
    private fun ChartsPreviewColumn(isDark: Boolean) {
        val points = samplePathPoints()
        val zoneDist = sampleZoneDistribution()
        val linearBins = listOf(
            LinearHistogramBin(0, 480.0, 520.0, 180L, 20f, TTColor.Zone1),
            LinearHistogramBin(1, 520.0, 560.0, 360L, 40f, TTColor.Zone1),
            LinearHistogramBin(2, 560.0, 600.0, 0L, 0f, TTColor.Zone1),
            LinearHistogramBin(3, 600.0, 640.0, 270L, 30f, TTColor.Zone1)
        )
        val zoneBands = TelemetryZoneMath.calculatePowerZoneBands(
            thresholds = PowerZoneThresholds(149, 203, 243, 284, 324, 405),
            dataMin = 100.0,
            dataMax = 420.0
        )

        ATrainingTrackerTheme(darkTheme = isDark) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(PREVIEW_COL_WIDTH_DP.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isDark) "ATT-6041 Charts — Dark" else "ATT-6041 Charts — Light",
                        style = MaterialTheme.typography.titleSmall
                    )
                    ElevationProfile(
                        pathPoints = points,
                        currentDistance = null,
                        bSportType = BSportType.BIKE,
                        showZoomControls = true,
                        alignWithTelemetryCharts = true,
                        xAxisDomain = ProfileXAxisDomain.DISTANCE
                    )
                    TelemetryMetricGraph(
                        pathPoints = points,
                        metricType = TelemetryMetricType.POWER,
                        currentDistance = null,
                        onDistanceSelected = {},
                        xAxisDomain = ProfileXAxisDomain.TIME,
                        zoneDistribution = zoneDist,
                        zoneDisplayMode = ZoneCardDisplayMode.HISTOGRAM,
                        powerZoneThresholds = PowerZoneThresholds(149, 203, 243, 284, 324, 405)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f).height(110.dp)) {
                            AlignedMarginalLinearDistribution(
                                bins = linearBins,
                                dataMin = 480.0,
                                dataMax = 640.0,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(modifier = Modifier.weight(1f).height(110.dp)) {
                            AlignedMarginalZoneDistribution(
                                zoneDistribution = zoneDist,
                                zoneDisplayMode = ZoneCardDisplayMode.HISTOGRAM,
                                zoneBands = zoneBands,
                                dataMin = 100.0,
                                dataMax = 420.0,
                                metricType = TelemetryMetricType.POWER,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun captureChartsMarginalHistogramsAndTicksLightDarkPng_forRule85() {
        // TST-UI-2789.3: Rule 8.5 dedicated Light/Dark visual verification capture
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width((PREVIEW_COL_WIDTH_DP * 2 + PREVIEW_PADDING_DP * 3).dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .padding(PREVIEW_PADDING_DP.dp)
                    .testTag(PREVIEW_HOST_TAG),
                horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)
            ) {
                ChartsPreviewColumn(isDark = false)
                ChartsPreviewColumn(isDark = true)
            }
        }

        val bitmap = composeTestRule.onNodeWithTag(PREVIEW_HOST_TAG)
            .captureToImage()
            .asAndroidBitmap()

        val outFile = resolvePreviewOutputFile()
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
        }
        assertTrue("ATT-6041 charts PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
