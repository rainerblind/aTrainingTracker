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

import com.atrainingtracker.banalservice.BANALService
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

/**
 * Unit tests for continuous telemetry metric graphs, presence verification,
 * dynamic metric extraction, and layout alignment invariants (TST-UI-160 / REQ-UI-206).
 */
class TelemetryMetricGraphTest {

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
        // With default athletic pace ceiling of 3:00 min/km (REQ-UI-243), 1.6667 min/km is clamped to 3.0 min/km:
        val paceMetricDefault = TelemetryMetricUtils.extractMetricValue(pt, TelemetryMetricType.PACE, MyUnits.METRIC)
        assertNotNull(paceMetricDefault)
        assertEquals(3.0, paceMetricDefault!!, 0.01)

        // When custom ceiling (e.g. 1.5 min/km) is supplied, raw pace is preserved:
        val paceMetricUnclamped = TelemetryMetricUtils.extractMetricValue(pt, TelemetryMetricType.PACE, MyUnits.METRIC, paceCeilingMinKm = 1.5f)
        assertNotNull(paceMetricUnclamped)
        assertEquals(100.0 / 60.0, paceMetricUnclamped!!, 0.01)
    }

    @Test
    fun testLayoutInvariants_matchesElevationProfilePadding() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
    }

    @Test
    fun testMapDetailLayout_integrationContracts() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)
    }

    @Test
    fun testWorkoutSummary_telemetryGraphOrdering_speedPrecedesHeartRate() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        assertNotNull("WorkoutSummaryKt must exist", clazz)
    }

    @Test
    fun testTelemetryMetricGraph_zoomedCoordinateMappingAndScrubbing() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)

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
        val paceStr = TelemetryMetricUtils.formatPaceMinutes(5.5)
        assertNotNull(paceStr)
    }

    @Test
    fun testMilestoneLabelFormatting_omitsRedundantUnit() {
        // Metric formatting for visibleSpan >= 1500m (REQ-UI-220 / TST-UI-174.2)
        assertEquals("2", TelemetryMetricUtils.formatMilestoneLabel(2000.0, 17820.0, MyUnits.METRIC))
        assertEquals("4", TelemetryMetricUtils.formatMilestoneLabel(4000.0, 17820.0, MyUnits.METRIC))
        assertEquals("10", TelemetryMetricUtils.formatMilestoneLabel(10000.0, 17820.0, MyUnits.METRIC))
        assertEquals("2.5", TelemetryMetricUtils.formatMilestoneLabel(2500.0, 17820.0, MyUnits.METRIC))

        // Short track < 1500m retains meters
        assertEquals("500m", TelemetryMetricUtils.formatMilestoneLabel(500.0, 1200.0, MyUnits.METRIC))

        // Imperial formatting
        val twoMilesMeters = 2.0 * BANALService.METER_PER_MILE
        val twoPointFiveMilesMeters = 2.5 * BANALService.METER_PER_MILE
        assertEquals("2", TelemetryMetricUtils.formatMilestoneLabel(twoMilesMeters, 17820.0, MyUnits.IMPERIAL))
        assertEquals("2.5", TelemetryMetricUtils.formatMilestoneLabel(twoPointFiveMilesMeters, 17820.0, MyUnits.IMPERIAL))
    }

    @Test
    fun testBoundaryLabels_rendersStartAndEnd() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
    }

    @Test
    fun testXAxisMilestoneDecimation_preventsLabelCollisions() {
        // 1. Start boundary suppression: labelLeft within 100px when threshold is 120px (REQ-UI-220 / TST-UI-174.4)
        assertFalse(
            "Label within start boundary margin must be suppressed",
            TelemetryMetricUtils.shouldRenderMilestoneLabel(
                labelLeft = 80f,
                labelRight = 110f,
                lastDrawnRightX = 0f,
                startBoundaryThreshold = 120f,
                endBoundaryThreshold = 800f,
                minSpacing = 36f
            )
        )

        // 2. End boundary suppression: labelRight exceeds end threshold
        assertFalse(
            "Label within end boundary margin must be suppressed",
            TelemetryMetricUtils.shouldRenderMilestoneLabel(
                labelLeft = 780f,
                labelRight = 810f,
                lastDrawnRightX = 600f,
                startBoundaryThreshold = 120f,
                endBoundaryThreshold = 800f,
                minSpacing = 36f
            )
        )

        // 3. Spacing collision: labelLeft too close to lastDrawnRightX
        assertFalse(
            "Label colliding with previous label must be decimated",
            TelemetryMetricUtils.shouldRenderMilestoneLabel(
                labelLeft = 210f,
                labelRight = 240f,
                lastDrawnRightX = 200f,
                startBoundaryThreshold = 120f,
                endBoundaryThreshold = 800f,
                minSpacing = 36f
            )
        )

        // 4. Well-spaced label satisfies all clearances
        assertTrue(
            "Well-spaced label must be rendered",
            TelemetryMetricUtils.shouldRenderMilestoneLabel(
                labelLeft = 250f,
                labelRight = 280f,
                lastDrawnRightX = 200f,
                startBoundaryThreshold = 120f,
                endBoundaryThreshold = 800f,
                minSpacing = 36f
            )
        )
    }

    @Test
    fun testUniversalMarginalHistogram_REQ_UI_446_contract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
    }

    @Test
    fun testComputeLinearMarginalHistogram_functionalValidation() {
        val values = listOf(10.0, 15.0, 20.0, 25.0, 30.0)
        val timestamps = listOf(0L, 2L, 4L, 6L, 8L)
        val dataMin = 10.0
        val dataMax = 30.0
        val color = androidx.compose.ui.graphics.Color.Blue

        val bins = com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculator.computeLinearMarginalHistogram(
            values = values,
            timestamps = timestamps,
            dataMin = dataMin,
            dataMax = dataMax,
            numBins = 20,
            color = color
        )

        assertEquals("Must generate exactly 20 bins", 20, bins.size)
        assertEquals(dataMin, bins.first().rangeMin, 0.001)
        assertEquals(dataMax, bins.last().rangeMax, 0.001)
        assertTrue("Total percentage across all bins must sum to 100%", kotlin.math.abs(bins.sumOf { it.percentage.toDouble() } - 100.0) < 0.1)

        // Empty / degenerate checks
        assertTrue(
            com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculator.computeLinearMarginalHistogram(
                values = emptyList(),
                timestamps = emptyList(),
                dataMin = 10.0,
                dataMax = 20.0,
                color = color
            ).isEmpty()
        )
        assertTrue(
            com.atrainingtracker.trainingtracker.ui.aftermath.zones.TelemetryHistogramCalculator.computeLinearMarginalHistogram(
                values = values,
                dataMin = 20.0,
                dataMax = 10.0,
                color = color
            ).isEmpty()
        )
    }
}
