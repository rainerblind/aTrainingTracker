/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.map

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for continuous telemetry metric graphs, presence verification,
 * dynamic metric extraction, and layout alignment invariants (TST-UI-160 / REQ-UI-206).
 */
class TelemetryMetricGraphTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val telemetryGraphFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt")
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    private val workoutSummaryFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt")
    }

    @Test
    fun testHasHeartRateData_validation() {
        val dummyLatLng = LatLng(48.0, 11.0)
        assertFalse(TelemetryMetricUtils.hasHeartRateData(null))
        assertFalse(TelemetryMetricUtils.hasHeartRateData(emptyList()))

        val pointsNoHr = listOf(
            PathPoint(distance = 100.0, latLng = dummyLatLng, altitude = 500.0, hr = null),
            PathPoint(distance = 200.0, latLng = dummyLatLng, altitude = 502.0, hr = 0)
        )
        assertFalse(TelemetryMetricUtils.hasHeartRateData(pointsNoHr))

        val pointsWithHr = listOf(
            PathPoint(distance = 100.0, latLng = dummyLatLng, altitude = 500.0, hr = null),
            PathPoint(distance = 200.0, latLng = dummyLatLng, altitude = 502.0, hr = 145)
        )
        assertTrue(TelemetryMetricUtils.hasHeartRateData(pointsWithHr))
    }

    @Test
    fun testHasSpeedData_validation() {
        val dummyLatLng = LatLng(48.0, 11.0)
        assertFalse(TelemetryMetricUtils.hasSpeedData(null))
        assertFalse(TelemetryMetricUtils.hasSpeedData(emptyList()))

        val pointsNoSpeed = listOf(
            PathPoint(distance = 100.0, latLng = dummyLatLng, altitude = 500.0, speedMps = null),
            PathPoint(distance = 200.0, latLng = dummyLatLng, altitude = 502.0, speedMps = 0.0)
        )
        assertFalse(TelemetryMetricUtils.hasSpeedData(pointsNoSpeed))

        val pointsWithSpeed = listOf(
            PathPoint(distance = 100.0, latLng = dummyLatLng, altitude = 500.0, speedMps = null),
            PathPoint(distance = 200.0, latLng = dummyLatLng, altitude = 502.0, speedMps = 6.5)
        )
        assertTrue(TelemetryMetricUtils.hasSpeedData(pointsWithSpeed))
    }

    @Test
    fun testHasPowerData_validation() {
        val dummyLatLng = LatLng(48.0, 11.0)
        assertFalse(TelemetryMetricUtils.hasPowerData(null))
        assertFalse(TelemetryMetricUtils.hasPowerData(emptyList()))

        val pointsNoPower = listOf(
            PathPoint(distance = 100.0, latLng = dummyLatLng, altitude = 500.0, power = null),
            PathPoint(distance = 200.0, latLng = dummyLatLng, altitude = 502.0, power = 0)
        )
        assertFalse(TelemetryMetricUtils.hasPowerData(pointsNoPower))

        val pointsWithPower = listOf(
            PathPoint(distance = 100.0, latLng = dummyLatLng, altitude = 500.0, power = null),
            PathPoint(distance = 200.0, latLng = dummyLatLng, altitude = 502.0, power = 250)
        )
        assertTrue(TelemetryMetricUtils.hasPowerData(pointsWithPower))
    }

    @Test
    fun testExtractMetricValue_heartRateAndPower() {
        val pt = PathPoint(
            distance = 500.0,
            latLng = LatLng(48.0, 11.0),
            altitude = 550.0,
            hr = 155,
            power = 280
        )

        val hrVal = TelemetryMetricUtils.extractMetricValue(pt, TelemetryMetricType.HEART_RATE, MyUnits.METRIC)
        assertEquals(155.0, hrVal!!, 0.001)

        val powerVal = TelemetryMetricUtils.extractMetricValue(pt, TelemetryMetricType.POWER, MyUnits.METRIC)
        assertEquals(280.0, powerVal!!, 0.001)

        val invalidPt = PathPoint(
            distance = 500.0,
            latLng = LatLng(48.0, 11.0),
            altitude = 550.0,
            hr = 0,
            power = null
        )
        assertNull(TelemetryMetricUtils.extractMetricValue(invalidPt, TelemetryMetricType.HEART_RATE, MyUnits.METRIC))
        assertNull(TelemetryMetricUtils.extractMetricValue(invalidPt, TelemetryMetricType.POWER, MyUnits.METRIC))
    }

    @Test
    fun testExtractMetricValue_speedAndPace() {
        // 10 m/s = 36.0 km/h
        val pt = PathPoint(
            distance = 1000.0,
            latLng = LatLng(48.0, 11.0),
            altitude = 550.0,
            speedMps = 10.0
        )

        val speedMetric = TelemetryMetricUtils.extractMetricValue(pt, TelemetryMetricType.SPEED, MyUnits.METRIC)
        assertEquals(36.0, speedMetric!!, 0.001)

        val speedImperial = TelemetryMetricUtils.extractMetricValue(pt, TelemetryMetricType.SPEED, MyUnits.IMPERIAL)
        assertEquals(22.369, speedImperial!!, 0.01)

        // For running pace at 10 m/s:
        // 1000m / 10 m/s = 100s = 1.6667 min/km
        val paceMetric = TelemetryMetricUtils.extractMetricValue(pt, TelemetryMetricType.PACE, MyUnits.METRIC)
        assertNotNull(paceMetric)
        assertEquals(100.0 / 60.0, paceMetric!!, 0.01)
    }

    @Test
    fun testLayoutInvariants_matchesElevationProfilePadding() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryGraphFile.exists())
        val content = telemetryGraphFile.readText()

        // Verify horizontal margin invariants
        assertTrue(
            "TelemetryMetricGraph must set startPadding = 50.dp to match ElevationProfile exactly",
            content.contains("val startPaddingPx = 50.dp.toPx()")
        )
        assertTrue(
            "TelemetryMetricGraph must set endPadding = 25.dp to match ElevationProfile exactly",
            content.contains("val endPaddingPx = 25.dp.toPx()")
        )
        assertTrue(
            "TelemetryMetricGraph must set bottomPadding = 24.dp to match ElevationProfile exactly",
            content.contains("val bottomPaddingPx = 24.dp.toPx()")
        )
    }

    @Test
    fun testMapDetailLayout_integrationContracts() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        // 1. Headings rendered when showZoomControls == true
        assertTrue(
            "MapDetailLayout must include graph_heading_elevation",
            content.contains("R.string.graph_heading_elevation")
        )
        assertTrue(
            "MapDetailLayout must include graph_heading_heart_rate",
            content.contains("R.string.graph_heading_heart_rate")
        )
        assertTrue(
            "MapDetailLayout must include graph_heading_speed and pace",
            content.contains("R.string.graph_heading_speed") && content.contains("R.string.graph_heading_pace")
        )
        assertTrue(
            "MapDetailLayout must include graph_heading_power",
            content.contains("R.string.graph_heading_power")
        )

        // 2. Conditional rendering of graphs
        assertTrue(
            "MapDetailLayout must conditionally check hasHeartRateData",
            content.contains("TelemetryMetricUtils.hasHeartRateData(path)")
        )
        assertTrue(
            "MapDetailLayout must conditionally check hasSpeedData",
            content.contains("TelemetryMetricUtils.hasSpeedData(path)")
        )
        assertTrue(
            "MapDetailLayout must conditionally check hasPowerData",
            content.contains("TelemetryMetricUtils.hasPowerData(path)")
        )

        // 3. Telemetry graphs receive selectedDistance and update selectedDistance
        assertTrue(
            "MapDetailLayout must forward selectedDistance to TelemetryMetricGraph",
            content.contains("currentDistance = selectedDistance")
        )
        assertTrue(
            "MapDetailLayout must synchronize touch events back to selectedDistance",
            content.contains("onDistanceSelected = { selectedDistance = it }")
        )
    }

    @Test
    fun testWorkoutSummary_telemetryGraphOrdering_speedPrecedesHeartRate() {
        assertTrue("WorkoutSummary.kt must exist", workoutSummaryFile.exists())
        val content = workoutSummaryFile.readText()

        val speedIndex = content.indexOf("TelemetryMetricUtils.hasSpeedData(telemetryPoints)")
        val hrIndex = content.indexOf("TelemetryMetricUtils.hasHeartRateData(telemetryPoints)")
        val powerIndex = content.indexOf("TelemetryMetricUtils.hasPowerData(telemetryPoints)")

        assertTrue("hasSpeedData check must exist in WorkoutSummary", speedIndex != -1)
        assertTrue("hasHeartRateData check must exist in WorkoutSummary", hrIndex != -1)
        assertTrue("hasPowerData check must exist in WorkoutSummary", powerIndex != -1)

        assertTrue(
            "Speed/Pace graph must precede Heart Rate graph in WorkoutSummary (REQ-UI-214, TST-UI-168.2)",
            speedIndex < hrIndex
        )
        assertTrue(
            "Heart Rate graph must precede Power graph in WorkoutSummary (REQ-UI-214, TST-UI-168.2)",
            hrIndex < powerIndex
        )
    }

    @Test
    fun testTelemetryMetricGraph_zoomedCoordinateMappingAndScrubbing() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryGraphFile.exists())
        val content = telemetryGraphFile.readText()

        // 1. Signature declaration check
        assertTrue(
            "TelemetryMetricGraph must declare zoomScale: Float = 1.0f (REQ-UI-215)",
            content.contains("zoomScale: Float = 1.0f")
        )
        assertTrue(
            "TelemetryMetricGraph must declare startDist: Double = 0.0 (REQ-UI-215)",
            content.contains("startDist: Double = 0.0")
        )

        // 2. Visible span calculation tests
        val totalDist = 10_000.0
        val visibleSpan1x = ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 1.0f)
        assertEquals(10_000.0, visibleSpan1x, 0.001)

        val visibleSpan2x = ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 2.0f)
        assertEquals(5_000.0, visibleSpan2x, 0.001)

        val visibleSpan5x = ElevationProfileZoomMath.calculateVisibleDistance(totalDist, 5.0f)
        assertEquals(2_000.0, visibleSpan5x, 0.001)

        // 3. Coordinate mapping and round-trip touch scrubbing tests at 2.0x zoom (startDist = 2,500m)
        val canvasWidth = 1000f
        val startDist = 2500.0
        val visibleSpan = visibleSpan2x // 5,000m -> window [2,500m .. 7,500m]

        val xAtStart = ElevationProfileZoomMath.distanceToCanvasX(2500.0, startDist, visibleSpan, canvasWidth)
        assertEquals(0f, xAtStart, 0.001f)

        val xAtMid = ElevationProfileZoomMath.distanceToCanvasX(5000.0, startDist, visibleSpan, canvasWidth)
        assertEquals(500f, xAtMid, 0.001f)

        val xAtEnd = ElevationProfileZoomMath.distanceToCanvasX(7500.0, startDist, visibleSpan, canvasWidth)
        assertEquals(1000f, xAtEnd, 0.001f)

        // Round-trip canvasX to distance
        val distFromMidX = ElevationProfileZoomMath.canvasXToDistance(500f, startDist, visibleSpan, canvasWidth, totalDist)
        assertEquals(5000.0, distFromMidX, 0.001)

        val distFromStartX = ElevationProfileZoomMath.canvasXToDistance(0f, startDist, visibleSpan, canvasWidth, totalDist)
        assertEquals(2500.0, distFromStartX, 0.001)

        val distFromEndX = ElevationProfileZoomMath.canvasXToDistance(1000f, startDist, visibleSpan, canvasWidth, totalDist)
        assertEquals(7500.0, distFromEndX, 0.001)

        // 4. Time domain mapping at 2.0x zoom (total 3600s, start 600s, visible 1800s -> window [600s .. 2400s])
        val totalTime = 3600.0
        val visibleTime = ElevationProfileZoomMath.calculateVisibleDistance(totalTime, 2.0f)
        val timeMidX = ElevationProfileZoomMath.distanceToCanvasX(1500.0, 600.0, visibleTime, canvasWidth)
        assertEquals(500f, timeMidX, 0.001f)

        val timeFromMidX = ElevationProfileZoomMath.canvasXToDistance(500f, 600.0, visibleTime, canvasWidth, totalTime)
        assertEquals(1500.0, timeFromMidX, 0.001)
    }

    @Test
    fun testFormatValue_paceFormattingMetricAndImperial() {
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getString(TrainingApplication.SP_UNITS, any()) } returns "METRIC"
        val field = TrainingApplication::class.java.getDeclaredField("cSharedPreferences")
        field.isAccessible = true
        val originalPrefs = field.get(null)
        field.set(null, mockPrefs)

        try {
            val paceFormatter = PaceFormatter()
            val speedFormatter = SpeedFormatter()

            // 4.0 min/km in metric -> "4:00 min/km" (REQ-UI-219 / ATT-1818)
            val formattedMetric = TelemetryMetricUtils.formatValue(4.0, TelemetryMetricType.PACE, MyUnits.METRIC, speedFormatter, paceFormatter)
            assertEquals("4:00 min/km", formattedMetric)
            assertFalse("Must not show 69:27 defect", formattedMetric.contains("69:27"))

            // Null value -> "--"
            assertEquals("--", TelemetryMetricUtils.formatValue(null, TelemetryMetricType.PACE, MyUnits.METRIC, speedFormatter, paceFormatter))

            // Imperial: 8.5 min/mile -> "8:30 min/mile"
            every { mockPrefs.getString(TrainingApplication.SP_UNITS, any()) } returns "IMPERIAL"
            val formattedImperial = TelemetryMetricUtils.formatValue(8.5, TelemetryMetricType.PACE, MyUnits.IMPERIAL, speedFormatter, paceFormatter)
            assertEquals("8:30 min/mile", formattedImperial)
        } finally {
            field.set(null, originalPrefs)
        }
    }

    @Test
    fun testFormatPaceMinutes_standardAndEdgeCases() {
        assertEquals("4:00", TelemetryMetricUtils.formatPaceMinutes(4.0))
        assertEquals("4:30", TelemetryMetricUtils.formatPaceMinutes(4.5))
        assertEquals("6:15", TelemetryMetricUtils.formatPaceMinutes(6.25))
        assertEquals("5:45", TelemetryMetricUtils.formatPaceMinutes(5.75))
        assertEquals("12:00", TelemetryMetricUtils.formatPaceMinutes(12.0))
        assertEquals("0:00", TelemetryMetricUtils.formatPaceMinutes(0.0))
    }

    @Test
    fun testExtractMetricValue_paceStoppedThreshold() {
        val dummyLatLng = LatLng(48.0, 11.0)
        // 4.1667 m/s (15.0 km/h) -> 4.0 min/km
        val fastPt = PathPoint(distance = 500.0, latLng = dummyLatLng, altitude = 500.0, speedMps = 4.166666666666667)
        val fastPace = TelemetryMetricUtils.extractMetricValue(fastPt, TelemetryMetricType.PACE, MyUnits.METRIC)
        assertNotNull(fastPace)
        assertEquals(4.0, fastPace!!, 0.01)

        // Stopped speeds (< 0.55 m/s) -> null (REQ-UI-219)
        val stoppedPt = PathPoint(distance = 500.0, latLng = dummyLatLng, altitude = 500.0, speedMps = 0.50)
        assertNull(TelemetryMetricUtils.extractMetricValue(stoppedPt, TelemetryMetricType.PACE, MyUnits.METRIC))

        val standingPt = PathPoint(distance = 500.0, latLng = dummyLatLng, altitude = 500.0, speedMps = 0.1)
        assertNull(TelemetryMetricUtils.extractMetricValue(standingPt, TelemetryMetricType.PACE, MyUnits.METRIC))

        val zeroPt = PathPoint(distance = 500.0, latLng = dummyLatLng, altitude = 500.0, speedMps = 0.0)
        assertNull(TelemetryMetricUtils.extractMetricValue(zeroPt, TelemetryMetricType.PACE, MyUnits.METRIC))
    }

    @Test
    fun testTelemetryMetricGraph_paceYAxisLabelsAreMmSs() {
        assertTrue("TelemetryMetricGraph.kt must exist", telemetryGraphFile.exists())
        val content = telemetryGraphFile.readText()

        assertTrue(
            "TelemetryMetricGraph must format maxLabel with formatPaceMinutes for PACE (REQ-UI-219)",
            content.contains("TelemetryMetricUtils.formatPaceMinutes(dataMin)")
        )
        assertTrue(
            "TelemetryMetricGraph must format minLabel with formatPaceMinutes for PACE (REQ-UI-219)",
            content.contains("TelemetryMetricUtils.formatPaceMinutes(dataMax)")
        )
        assertFalse(
            "TelemetryMetricGraph must not use raw decimal String.format(Locale.US, \"%.1f\", dataMin) for PACE",
            content.contains("String.format(Locale.US, \"%.1f\", dataMin)")
        )
    }
}
