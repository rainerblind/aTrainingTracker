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
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for pace telemetry metric clamping, GPS noise spikes mitigation,
 * unit parity, and bounds calculation with configurable pace ceiling (TST-UI-202.3 / REQ-UI-243).
 */
class TelemetryMetricGraphPaceCeilingTest {

    private fun createPointWithSpeed(speedMps: Double): PathPoint {
        return PathPoint(
            latLng = LatLng(48.137154, 11.576124),
            distance = 100.0,
            altitude = 500.0,
            speedMps = speedMps,
            hr = 150,
            power = 200,
            timeSec = 30L
        )
    }

    @Test
    fun extractMetricValue_gpsSpike_clampedToDefaultPaceCeiling() {
        // speed = 15.0 m/s -> 54 km/h -> raw pace = 66.67 sec/km = 1.11 min/km
        val spikePoint = createPointWithSpeed(15.0)

        val extracted = TelemetryMetricUtils.extractMetricValue(
            point = spikePoint,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.METRIC,
            paceCeilingMinKm = TuningPreferencesDefaults.DEFAULT_PACE_CEILING_MIN_KM
        )

        assertNotNull(extracted)
        // Must clamp to 3.0 min/km (3:00) and NEVER to old hardcoded 1.5 min/km (1:30)
        assertEquals(3.0, extracted!!, 0.001)
    }

    @Test
    fun extractMetricValue_gpsSpike_clampedToCustomPaceCeiling() {
        val spikePoint = createPointWithSpeed(15.0)

        val extracted25 = TelemetryMetricUtils.extractMetricValue(
            point = spikePoint,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 2.5f
        )
        assertNotNull(extracted25)
        assertEquals(2.5, extracted25!!, 0.001)

        val extracted40 = TelemetryMetricUtils.extractMetricValue(
            point = spikePoint,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 4.0f
        )
        assertNotNull(extracted40)
        assertEquals(4.0, extracted40!!, 0.001)
    }

    @Test
    fun extractMetricValue_normalRunningPace_unaffectedByCeiling() {
        // speed = 3.333333 m/s -> 12 km/h -> 5.0 min/km (5:00 min/km)
        val normalPoint = createPointWithSpeed(1000.0 / 300.0)

        val extracted = TelemetryMetricUtils.extractMetricValue(
            point = normalPoint,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 3.0f
        )

        assertNotNull(extracted)
        assertEquals(5.0, extracted!!, 0.001)
    }

    @Test
    fun extractMetricValue_stationarySpeed_filteredOut() {
        // speed < 0.55 m/s is treated as stopped/paused
        val stoppedPoint = createPointWithSpeed(0.4)

        val extracted = TelemetryMetricUtils.extractMetricValue(
            point = stoppedPoint,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 3.0f
        )

        assertNull(extracted)
    }

    @Test
    fun extractMetricValue_imperialParity_scalesCeilingCorrectly() {
        val spikePoint = createPointWithSpeed(15.0)

        val extractedImperial = TelemetryMetricUtils.extractMetricValue(
            point = spikePoint,
            metricType = TelemetryMetricType.PACE,
            unit = MyUnits.IMPERIAL,
            paceCeilingMinKm = 3.0f
        )

        val expectedImperialCeiling = 3.0 * (BANALService.METER_PER_MILE / 1000.0)
        assertNotNull(extractedImperial)
        assertEquals(expectedImperialCeiling, extractedImperial!!, 0.001)
    }

    @Test
    fun extractMetricValue_speedAndOtherMetrics_unaffectedByPaceCeiling() {
        val point = createPointWithSpeed(10.0)

        val speedMetric = TelemetryMetricUtils.extractMetricValue(
            point = point,
            metricType = TelemetryMetricType.SPEED,
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 3.0f
        )
        assertEquals(36.0, speedMetric!!, 0.001) // 10 m/s = 36 km/h

        val hrMetric = TelemetryMetricUtils.extractMetricValue(
            point = point,
            metricType = TelemetryMetricType.HEART_RATE,
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 3.0f
        )
        assertEquals(150.0, hrMetric!!, 0.001)

        val pwrMetric = TelemetryMetricUtils.extractMetricValue(
            point = point,
            metricType = TelemetryMetricType.POWER,
            unit = MyUnits.METRIC,
            paceCeilingMinKm = 3.0f
        )
        assertEquals(200.0, pwrMetric!!, 0.001)
    }
}
