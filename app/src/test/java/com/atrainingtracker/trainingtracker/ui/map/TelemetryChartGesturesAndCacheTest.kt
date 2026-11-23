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
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import com.google.android.gms.maps.model.LatLng
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Unit and behavioral Robolectric Compose UI verification suite for `ATT-8134`
 * (`REQ-UI-2803.1`, `TST-UI-2803.1`):
 * - O(log N) floor/nearest binary search & trackless scrub parity.
 * - Viewport min-max downsampling and HRV artifact-gap preservation.
 * - `Modifier.drawWithCache` zero curve rebuild during 60-Hz scrubbing (`buildCount == 1`).
 * - Shared `Modifier.telemetryChartGestures` 2-finger pinch-zoom & double-tap reset parity across
 *   [TelemetryMetricGraph] and [ElevationProfile].
 * - Single-pass speed/pace display formatting without `UserUnit2mps` round-trips.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class TelemetryChartGesturesAndCacheTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        TrainingApplication.setUnitForTesting(null)
        unmockkAll()
    }

    private fun buildDensePath(count: Int = 200): List<PathPoint> =
        (0 until count).map { i ->
            PathPoint(
                distance = i * 50.0,
                latLng = LatLng(48.1 + i * 0.0002, 11.5 + i * 0.0002),
                altitude = 500.0 + (i % 40) * 5.0,
                timeSec = i * 5L,
                hr = 130 + (i % 50),
                speedMps = 6.0 + (i % 10) * 0.3,
                power = 180 + (i % 100)
            )
        }

    @Test
    fun testFindFloorIndex_exactParityWithLinearIndexOfLast_tstUi2803_1() {
        assertEquals(-1, TelemetryChartSearch.findFloorIndex(emptyList(), 50.0, isTimeDomain = false))

        val points = buildDensePath(300)
        val distanceProbes = listOf(-10.0, 0.0, 24.9, 50.0, 50.1, 2500.0, 14_925.0, 14_950.0, 99_999.0)
        for (target in distanceProbes) {
            val expected = points.indexOfLast { it.distance <= target }.coerceAtLeast(0)
            val actual = TelemetryChartSearch.findFloorIndex(points, target, isTimeDomain = false)
            assertEquals("Distance floor mismatch at $target", expected, actual)
        }

        val timeProbes = listOf(-5.0, 0.0, 4.9, 5.0, 127.3, 1495.0, 5000.0)
        for (target in timeProbes) {
            val expected = points.indexOfLast { it.timeSec.toDouble() <= target }.coerceAtLeast(0)
            val actual = TelemetryChartSearch.findFloorIndex(points, target, isTimeDomain = true)
            assertEquals("Time floor mismatch at $target", expected, actual)
        }
    }

    @Test
    fun testResolveScrubSelection_preservesFloorNearestAndTracklessSemantics_tstUi2803_1() {
        val points = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), timeSec = 0L, hr = 120),
            PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), timeSec = 20L, hr = 140),
            PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), timeSec = 40L, hr = 160)
        )

        // Floor picks index 1 (100m) for 180m; Nearest picks index 2 (200m) for 180m
        val floorSelection = TelemetryChartSearch.resolveScrubSelection(
            points = points,
            selectedDomainValue = 180.0,
            isTimeDomain = false,
            isTrackless = false,
            semantics = ChartScrubIndexSemantics.FLOOR
        )
        assertEquals(100.0, floorSelection.point?.distance)
        assertEquals(100.0, floorSelection.reportedDistance)

        val nearestSelection = TelemetryChartSearch.resolveScrubSelection(
            points = points,
            selectedDomainValue = 180.0,
            isTimeDomain = false,
            isTrackless = false,
            semantics = ChartScrubIndexSemantics.NEAREST
        )
        assertEquals(200.0, nearestSelection.point?.distance)
        assertEquals(180.0, nearestSelection.reportedDistance)

        // Trackless workout in time domain reports timeSec
        val tracklessPoints = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), timeSec = 0L, hr = 120),
            PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), timeSec = 60L, hr = 155)
        )
        val tracklessSelection = TelemetryChartSearch.resolveScrubSelection(
            points = tracklessPoints,
            selectedDomainValue = 52.0,
            isTimeDomain = true,
            isTrackless = true,
            semantics = ChartScrubIndexSemantics.NEAREST
        )
        assertEquals(60L, tracklessSelection.point?.timeSec)
        assertEquals(60.0, tracklessSelection.reportedDistance)
    }

    @Test
    fun testDownsampleSegmentMinMax_capsVerticesAndPreservesPeakSpikesAndAnchors_tstUi2803_1() {
        // 5,000 points spanning x in [-200f .. 1200f] with an extreme spike at x = 500f
        val segment = (0 until 5000).map { i ->
            val x = -200f + i * (1400f / 4999f)
            val y = if (i == 2500) 5f else 80f + (i % 7)
            TelemetryPlotPoint(Offset(x, y), value = y.toDouble())
        }

        val downsampled = downsampleSegmentMinMax(
            segment = segment,
            plotStartX = 0f,
            plotEndX = 400f,
            maxBuckets = 100
        )

        assertTrue("Downsampled count (${downsampled.size}) must be bounded <= 202", downsampled.size <= 202)
        assertTrue("Left viewport anchor must be retained", downsampled.first().offset.x <= 0f)
        assertTrue("Right viewport anchor must be retained", downsampled.last().offset.x >= 400f)

        // With spike inside viewport [0f .. 600f], min-Y peak must survive downsampling verbatim
        val withSpike = downsampleSegmentMinMax(
            segment = segment,
            plotStartX = 0f,
            plotEndX = 600f,
            maxBuckets = 80
        )
        assertTrue(
            "Min-max downsampling must retain extreme vertical spike (y = 5f)",
            withSpike.any { it.offset.y == 5f }
        )
    }

    @Test
    fun testBuildCachedTelemetryCurveGeometry_preservesHrvArtifactGapsAndCapsDensePoints_tstUi2803_1() {
        val hrvPoints = listOf(
            PathPoint(0.0, LatLng(0.0, 0.0), timeSec = 0L, hrvDfaA1 = 1.0, hrvArtifactRate = 1.0),
            PathPoint(100.0, LatLng(0.0, 0.0), timeSec = 30L, hrvDfaA1 = 0.85, hrvArtifactRate = 2.0),
            PathPoint(200.0, LatLng(0.0, 0.0), timeSec = 60L, hrvDfaA1 = 0.50, hrvArtifactRate = 12.0), // artifact gap!
            PathPoint(300.0, LatLng(0.0, 0.0), timeSec = 90L, hrvDfaA1 = 0.72, hrvArtifactRate = 1.5),
            PathPoint(400.0, LatLng(0.0, 0.0), timeSec = 120L, hrvDfaA1 = 0.65, hrvArtifactRate = 1.0)
        )
        val rawVals = hrvPoints.map { it.hrvDfaA1 }
        val hrvCache = buildCachedTelemetryCurveGeometry(
            pathPoints = hrvPoints,
            rawValues = rawVals,
            metricType = TelemetryMetricType.HRV_DFA_A1,
            isTimeDomain = true,
            startDist = 0.0,
            visibleSpan = 120.0,
            dataMin = 0.2,
            dataMax = 1.4,
            startPaddingPx = 50f,
            topPaddingPx = 10f,
            chartWidthPx = 300f,
            chartHeightPx = 100f,
            densityPxPerDp = 2f,
            accentColor = TTColor.Zone2,
            useZoneColoredCurve = false,
            zoneStrokeStops = emptyList(),
            zoneFillStops = emptyList(),
            effectiveHrThresholds = null,
            effectivePowerThresholds = null,
            dfaA1ThresholdT1 = 0.75f,
            dfaA1ThresholdT2 = 0.50f,
            isDarkSurface = false,
            canRenderFreshModelOverlay = false,
            effectiveShowFreshModel = false,
            freshHrTrajectory = emptyList(),
            activeOverlaySeries = emptyList(),
            allOverlaySeries = emptyList(),
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 2.5f
        )

        assertEquals("HRV artifact gap must split into 2 separate stroke paths", 2, hrvCache.strokePaths.size)
        assertEquals("HRV artifact gap interval must be preserved", 1, hrvCache.gapIntervals.size)

        // Dense 10,000-point HR ride must be downsampled well below raw point count
        val densePoints = buildDensePath(10_000)
        val denseRaw = densePoints.map { it.hr?.toDouble() }
        val denseCache = buildCachedTelemetryCurveGeometry(
            pathPoints = densePoints,
            rawValues = denseRaw,
            metricType = TelemetryMetricType.HEART_RATE,
            isTimeDomain = false,
            startDist = 0.0,
            visibleSpan = densePoints.last().distance,
            dataMin = 120.0,
            dataMax = 190.0,
            startPaddingPx = 50f,
            topPaddingPx = 10f,
            chartWidthPx = 300f,
            chartHeightPx = 100f,
            densityPxPerDp = 2f,
            accentColor = TTColor.Zone4,
            useZoneColoredCurve = false,
            zoneStrokeStops = emptyList(),
            zoneFillStops = emptyList(),
            effectiveHrThresholds = null,
            effectivePowerThresholds = null,
            dfaA1ThresholdT1 = 0.75f,
            dfaA1ThresholdT2 = 0.50f,
            isDarkSurface = false,
            canRenderFreshModelOverlay = false,
            effectiveShowFreshModel = false,
            freshHrTrajectory = emptyList(),
            activeOverlaySeries = emptyList(),
            allOverlaySeries = emptyList(),
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 2.5f
        )
        assertEquals(1, denseCache.strokePaths.size)
        assertTrue(
            "10,000 raw samples must downsample to <= 400 viewport points (got ${denseCache.plottedPointCount})",
            denseCache.plottedPointCount <= 400
        )
    }

    @Test
    fun testFormatValue_speedAndPaceAvoidRoundTripConversions_tstUi2803_1() {
        mockkStatic(MyHelper::class)
        val speedFormatter = SpeedFormatter()
        val paceFormatter = PaceFormatter()

        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        val metricSpeed = TelemetryMetricUtils.formatValue(
            value = 36.0,
            metricType = TelemetryMetricType.SPEED,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            locale = Locale.US
        )
        assertEquals("36.0 km/h", metricSpeed)

        val metricPace = TelemetryMetricUtils.formatValue(
            value = 4.5,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            locale = Locale.US
        )
        assertEquals("4:30 min/km", metricPace)

        TrainingApplication.setUnitForTesting(MyUnits.IMPERIAL)
        val imperialSpeed = TelemetryMetricUtils.formatValue(
            value = 22.4,
            metricType = TelemetryMetricType.SPEED,
            unit = MyUnits.IMPERIAL,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            locale = Locale.US
        )
        assertEquals("22.4 mile/h", imperialSpeed)

        val imperialPace = TelemetryMetricUtils.formatValue(
            value = 8.5,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.IMPERIAL,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter,
            locale = Locale.US
        )
        assertEquals("8:30 min/mile", imperialPace)

        // Single-pass formatting MUST NOT round-trip through UserUnit2mps or mps2userUnit
        verify(exactly = 0) { MyHelper.UserUnit2mps(any(), any()) }
        verify(exactly = 0) { MyHelper.mps2userUnit(any(), any()) }
    }

    @Test
    fun testTelemetryMetricGraph_supportsPinchZoomAndDoubleTapResetViaSharedGestures_tstUi2803_1() {
        val points = buildDensePath(120)
        var zoomScale by mutableFloatStateOf(1.0f)
        var startDist by mutableDoubleStateOf(0.0)
        var zoomChangeEvents = 0

        composeRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    TelemetryMetricGraph(
                        pathPoints = points,
                        metricType = TelemetryMetricType.HEART_RATE,
                        currentDistance = null,
                        onDistanceSelected = {},
                        zoomScale = zoomScale,
                        startDist = startDist,
                        onZoomChanged = { z, s ->
                            zoomScale = z
                            startDist = s
                            zoomChangeEvents++
                        },
                        modifier = Modifier.testTag("telemetry_hr_graph")
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 2-finger spread pinch zooms in on TelemetryMetricGraph
        composeRule.onNodeWithTag("telemetry_hr_graph").performTouchInput {
            val y = centerY
            pinch(
                start0 = Offset(centerX - 30f, y),
                end0 = Offset(centerX - 110f, y),
                start1 = Offset(centerX + 30f, y),
                end1 = Offset(centerX + 110f, y)
            )
        }
        composeRule.waitForIdle()

        assertTrue("2-finger pinch must zoom TelemetryMetricGraph > 1.0f (got $zoomScale)", zoomScale > 1.2f)
        assertTrue("onZoomChanged must be invoked during pinch", zoomChangeEvents > 0)

        // Double-tap resets zoom to (1.0f, 0.0)
        composeRule.onNodeWithTag("telemetry_hr_graph").performTouchInput {
            click(center)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("telemetry_hr_graph").performTouchInput {
            click(center)
        }
        composeRule.waitForIdle()

        assertEquals("Double-tap must reset zoomScale to 1.0f", 1.0f, zoomScale, 0.001f)
        assertEquals("Double-tap must reset startDist to 0.0", 0.0, startDist, 0.001)
    }

    @Test
    fun testDrawWithCache_60HzScrubbingRebuildsCurvePathsZeroExtraTimes_tstUi2803_1() {
        val points = buildDensePath(400)
        var currentDist by mutableStateOf<Double?>(null)
        var hrCacheBuilds = 0

        composeRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    CompositionLocalProvider(
                        LocalTelemetryCurveCacheBuildListener provides { hrCacheBuilds++ }
                    ) {
                        TelemetryMetricGraph(
                            pathPoints = points,
                            metricType = TelemetryMetricType.HEART_RATE,
                            currentDistance = currentDist,
                            onDistanceSelected = { currentDist = it },
                            modifier = Modifier.testTag("cached_hr_graph")
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cached_hr_graph").captureToImage()
        composeRule.waitForIdle()

        assertEquals("Initial draw must build TelemetryMetricGraph curve cache exactly once", 1, hrCacheBuilds)

        // Simulate 20 rapid scrub frames changing only currentDistance
        repeat(20) { frame ->
            composeRule.runOnIdle {
                currentDist = (frame + 1) * 500.0
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("cached_hr_graph").captureToImage()
            composeRule.waitForIdle()
        }

        assertEquals(
            "60-Hz currentDistance scrubbing MUST NOT rebuild TelemetryMetricGraph drawWithCache curves",
            1,
            hrCacheBuilds
        )
    }

    @Test
    fun testElevationProfileDrawWithCache_60HzScrubbingRebuildsCurvePathsZeroExtraTimes_tstUi2803_1() {
        val points = buildDensePath(400)
        var elevationCacheBuilds = 0
        var elevDist by mutableStateOf<Double?>(null)

        composeRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    CompositionLocalProvider(
                        LocalTelemetryCurveCacheBuildListener provides { elevationCacheBuilds++ }
                    ) {
                        ElevationProfile(
                            pathPoints = points,
                            currentDistance = elevDist,
                            showZoomControls = true,
                            smoothingSigma = 0.0,
                            modifier = Modifier.testTag("cached_elevation_profile")
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cached_elevation_profile").captureToImage()
        composeRule.waitForIdle()
        assertEquals("Initial draw must build ElevationProfile curve cache exactly once", 1, elevationCacheBuilds)

        repeat(10) { frame ->
            composeRule.runOnIdle { elevDist = (frame + 1) * 700.0 }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("cached_elevation_profile").captureToImage()
            composeRule.waitForIdle()
        }
        assertEquals(
            "60-Hz currentDistance scrubbing MUST NOT rebuild ElevationProfile drawWithCache curves",
            1,
            elevationCacheBuilds
        )
    }

    @Test
    fun testElevationProfile_pinchZoomAndDoubleTapResetViaSharedModifier_tstUi2803_1() {
        val points = buildDensePath(120)
        var zoomScale by mutableFloatStateOf(1.0f)
        var startDist by mutableDoubleStateOf(0.0)
        var selectedPoint: PathPoint? = null

        composeRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    ElevationProfile(
                        pathPoints = points,
                        currentDistance = null,
                        showZoomControls = true,
                        zoomScale = zoomScale,
                        startDist = startDist,
                        onZoomChanged = { z, s ->
                            zoomScale = z
                            startDist = s
                        },
                        onPointSelected = { selectedPoint = it },
                        smoothingSigma = 0.0,
                        modifier = Modifier.testTag("shared_elevation_profile")
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Single tap inspects a point
        composeRule.onNodeWithTag("shared_elevation_profile").performTouchInput {
            click(center)
        }
        composeRule.waitForIdle()
        assertNotNull("Single tap on ElevationProfile must select a PathPoint", selectedPoint)

        // 2-finger pinch zooms in
        composeRule.onNodeWithTag("shared_elevation_profile").performTouchInput {
            val y = centerY
            pinch(
                start0 = Offset(centerX - 30f, y),
                end0 = Offset(centerX - 110f, y),
                start1 = Offset(centerX + 30f, y),
                end1 = Offset(centerX + 110f, y)
            )
        }
        composeRule.waitForIdle()
        assertTrue("2-finger pinch must zoom ElevationProfile > 1.2f (got $zoomScale)", zoomScale > 1.2f)
    }

    @Test
    fun testTelemetryMetricGraph_doubleTapZoomResetWorksInPanMode_tstUi2803_1() {
        val points = buildDensePath(120)
        var zoomScale by mutableFloatStateOf(3.0f)
        var startDist by mutableDoubleStateOf(150.0)
        var scrubSelected: Double? = null

        composeRule.setContent {
            ATrainingTrackerTheme {
                Surface {
                    TelemetryMetricGraph(
                        metricType = TelemetryMetricType.HEART_RATE,
                        pathPoints = points,
                        currentDistance = null,
                        onDistanceSelected = { scrubSelected = it },
                        isPanMode = true,
                        zoomScale = zoomScale,
                        startDist = startDist,
                        onZoomChanged = { z, s ->
                            zoomScale = z
                            startDist = s
                        },
                        modifier = Modifier.testTag("pan_mode_double_tap_graph")
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // First tap in Pan Mode must NOT scrub, but must record lastTapMs
        composeRule.onNodeWithTag("pan_mode_double_tap_graph").performTouchInput {
            click(center)
        }
        composeRule.waitForIdle()
        assertNull("Single tap in Pan Mode must not dispatch scrub selection", scrubSelected)
        assertEquals("Single tap alone must not reset zoom", 3.0f, zoomScale, 0.01f)

        // Second tap within double-tap window (350ms) must reset zoom even while isPanMode == true
        composeRule.onNodeWithTag("pan_mode_double_tap_graph").performTouchInput {
            click(center)
        }
        composeRule.waitForIdle()
        assertEquals(
            "Double-tap in Pan Mode must reset zoom to MIN_ZOOM",
            ElevationProfileZoomMath.MIN_ZOOM,
            zoomScale,
            0.01f
        )
        assertEquals("Double-tap in Pan Mode must reset startDist to 0.0", 0.0, startDist, 0.01)
    }

    @Test
    fun testBuildCachedTelemetryCurveGeometry_preservesDuplicateStartCoordinateSamples_tstUi2803_1() {
        // 4 stationary samples at distance == 0.0 followed by moving samples
        val points = listOf(
            PathPoint(0.0, LatLng(48.1, 11.5), altitude = 500.0, timeSec = 0L, hr = 100),
            PathPoint(0.0, LatLng(48.1, 11.5), altitude = 500.0, timeSec = 1L, hr = 115),
            PathPoint(0.0, LatLng(48.1, 11.5), altitude = 500.0, timeSec = 2L, hr = 130),
            PathPoint(0.0, LatLng(48.1, 11.5), altitude = 500.0, timeSec = 3L, hr = 140),
            PathPoint(100.0, LatLng(48.1, 11.5), altitude = 505.0, timeSec = 10L, hr = 150),
            PathPoint(100.0, LatLng(48.1, 11.5), altitude = 505.0, timeSec = 11L, hr = 155),
            PathPoint(100.0, LatLng(48.1, 11.5), altitude = 505.0, timeSec = 12L, hr = 160),
            PathPoint(200.0, LatLng(48.1, 11.5), altitude = 510.0, timeSec = 20L, hr = 165)
        )
        assertEquals(0, TelemetryChartSearch.findLowerBoundIndex(points, 0.0, isTimeDomain = false))
        assertEquals(4, TelemetryChartSearch.findLowerBoundIndex(points, 100.0, isTimeDomain = false))

        val rawValues = points.map { it.hr?.toDouble() }
        val fullViewport = buildCachedTelemetryCurveGeometry(
            pathPoints = points,
            rawValues = rawValues,
            metricType = TelemetryMetricType.HEART_RATE,
            isTimeDomain = false,
            startDist = 0.0,
            visibleSpan = 200.0,
            dataMin = 90.0,
            dataMax = 180.0,
            startPaddingPx = 50f,
            topPaddingPx = 10f,
            chartWidthPx = 800f,
            chartHeightPx = 200f,
            densityPxPerDp = 2f,
            accentColor = TTColor.Zone4,
            useZoneColoredCurve = false,
            zoneStrokeStops = emptyList(),
            zoneFillStops = emptyList(),
            effectiveHrThresholds = null,
            effectivePowerThresholds = null,
            dfaA1ThresholdT1 = 0.75f,
            dfaA1ThresholdT2 = 0.50f,
            isDarkSurface = false,
            canRenderFreshModelOverlay = false,
            effectiveShowFreshModel = false,
            freshHrTrajectory = emptyList(),
            activeOverlaySeries = emptyList(),
            allOverlaySeries = emptyList(),
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 15.0f
        )
        assertEquals(
            "All 8 points (including all 4 stationary samples at distance == 0.0) must be retained",
            points.size,
            fullViewport.plottedPointCount
        )

        // Zoomed to startDist == 100.0: must include left anchor (idx 3) + all 3 samples at 100.0 + idx 7 = 5 points
        val zoomedViewport = buildCachedTelemetryCurveGeometry(
            pathPoints = points,
            rawValues = rawValues,
            metricType = TelemetryMetricType.HEART_RATE,
            isTimeDomain = false,
            startDist = 100.0,
            visibleSpan = 100.0,
            dataMin = 90.0,
            dataMax = 180.0,
            startPaddingPx = 50f,
            topPaddingPx = 10f,
            chartWidthPx = 800f,
            chartHeightPx = 200f,
            densityPxPerDp = 2f,
            accentColor = TTColor.Zone4,
            useZoneColoredCurve = false,
            zoneStrokeStops = emptyList(),
            zoneFillStops = emptyList(),
            effectiveHrThresholds = null,
            effectivePowerThresholds = null,
            dfaA1ThresholdT1 = 0.75f,
            dfaA1ThresholdT2 = 0.50f,
            isDarkSurface = false,
            canRenderFreshModelOverlay = false,
            effectiveShowFreshModel = false,
            freshHrTrajectory = emptyList(),
            activeOverlaySeries = emptyList(),
            allOverlaySeries = emptyList(),
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 15.0f
        )
        assertEquals(
            "Zoomed left edge at 100.0m must retain left anchor + all 3 duplicate 100.0m samples + end sample",
            5,
            zoomedViewport.plottedPointCount
        )
    }

    @Test
    fun testBuildCachedTelemetryCurveGeometry_paceOverlayWithTrailingStoppedSampleRetainsCurve_tstUi2803_1() {
        val points = listOf(
            PathPoint(0.0, LatLng(48.1, 11.5), altitude = 500.0, timeSec = 0L, speedMps = 3.5),
            PathPoint(100.0, LatLng(48.1, 11.5), altitude = 500.0, timeSec = 30L, speedMps = 3.2),
            PathPoint(200.0, LatLng(48.1, 11.5), altitude = 500.0, timeSec = 60L, speedMps = 3.0),
            PathPoint(250.0, LatLng(48.1, 11.5), altitude = 500.0, timeSec = 90L, speedMps = 0.0)
        )
        val paceOverlay = TelemetrySourceSeries(
            metricType = TelemetryMetricType.PACE,
            sourceName = "Footpod",
            columnName = "SPEED_mps_footpod",
            points = listOf(
                TelemetrySourcePoint(distance = 0.0, timeSec = 0L, value = 3.5),
                TelemetrySourcePoint(distance = 100.0, timeSec = 30L, value = 3.2),
                TelemetrySourcePoint(distance = 200.0, timeSec = 60L, value = 3.0),
                TelemetrySourcePoint(distance = 250.0, timeSec = 90L, value = 0.0)
            ),
            meanValue = 3.23
        )
        val rawPaceValues = points.map {
            TelemetryMetricUtils.convertSpeedMpsToPaceMinutes(it.speedMps ?: 0.0, MyUnits.METRIC, 15.0f)
        }
        val geometry = buildCachedTelemetryCurveGeometry(
            pathPoints = points,
            rawValues = rawPaceValues,
            metricType = TelemetryMetricType.PACE,
            isTimeDomain = false,
            startDist = 0.0,
            visibleSpan = 250.0,
            dataMin = 4.0,
            dataMax = 10.0,
            startPaddingPx = 50f,
            topPaddingPx = 10f,
            chartWidthPx = 800f,
            chartHeightPx = 200f,
            densityPxPerDp = 2f,
            accentColor = TTColor.Zone3,
            useZoneColoredCurve = false,
            zoneStrokeStops = emptyList(),
            zoneFillStops = emptyList(),
            effectiveHrThresholds = null,
            effectivePowerThresholds = null,
            dfaA1ThresholdT1 = 0.75f,
            dfaA1ThresholdT2 = 0.50f,
            isDarkSurface = false,
            canRenderFreshModelOverlay = false,
            effectiveShowFreshModel = false,
            freshHrTrajectory = emptyList(),
            activeOverlaySeries = listOf(paceOverlay),
            allOverlaySeries = listOf(paceOverlay),
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 15.0f
        )
        assertEquals(
            "PACE multi-sensor overlay ending with a stopped (0.0 m/s) sample MUST NOT be discarded",
            1,
            geometry.overlayCurves.size
        )
    }
}
