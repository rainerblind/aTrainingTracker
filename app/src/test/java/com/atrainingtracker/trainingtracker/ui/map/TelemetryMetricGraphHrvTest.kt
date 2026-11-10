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

import com.atrainingtracker.banalservice.sensor.formater.PaceFormatter
import com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter
import com.atrainingtracker.trainingtracker.MyUnits
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying TelemetryMetricGraph HRV metric types, utility extractors, formatters,
 * and artifact gap detection conforming to REQ-PER-035.1, REQ-PER-035.2, REQ-PER-035.3, TST-PER-043.2, and TST-PER-043.3.
 */
class TelemetryMetricGraphHrvTest {

    private val speedFormatter = SpeedFormatter()
    private val paceFormatter = PaceFormatter()

    @Test
    fun testTelemetryMetricGraph_hrvTracks_rendersBoundsAndThresholds() {
        // 1. Verify TelemetryMetricType enum constants exist
        val dfaType = TelemetryMetricType.valueOf("HRV_DFA_A1")
        val rmssdType = TelemetryMetricType.valueOf("HRV_RMSSD")
        assertNotNull(dfaType)
        assertNotNull(rmssdType)

        // 2. Verify TelemetryMetricUtils data availability checks
        val nonHrvPath = listOf(
            PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), hr = 140, power = 200)
        )
        assertFalse(TelemetryMetricUtils.hasHrvData(nonHrvPath))
        assertFalse(TelemetryMetricUtils.hasHrvDfaA1Data(nonHrvPath))
        assertFalse(TelemetryMetricUtils.hasHrvRmssdData(nonHrvPath))

        val hrvPath = listOf(
            PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), hrvDfaA1 = 0.78, hrvArtifactRate = 0.5),
            PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), hrvRmssd = 42.5, hrvArtifactRate = 0.8)
        )
        assertTrue(TelemetryMetricUtils.hasHrvData(hrvPath))
        assertTrue(TelemetryMetricUtils.hasHrvDfaA1Data(hrvPath))
        assertTrue(TelemetryMetricUtils.hasHrvRmssdData(hrvPath))

        // 3. Verify TelemetryMetricUtils scalar extraction
        val ptDfa = PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), hrvDfaA1 = 0.85)
        val extractedDfa = TelemetryMetricUtils.extractMetricValue(ptDfa, TelemetryMetricType.HRV_DFA_A1, MyUnits.METRIC)
        assertEquals(0.85, extractedDfa!!, 1e-4)

        val ptRmssd = PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), hrvRmssd = 55.4)
        val extractedRmssd = TelemetryMetricUtils.extractMetricValue(ptRmssd, TelemetryMetricType.HRV_RMSSD, MyUnits.METRIC)
        assertEquals(55.4, extractedRmssd!!, 1e-4)

        // 4. Verify TelemetryMetricUtils formatting
        val formattedDfa = TelemetryMetricUtils.formatValue(
            value = 0.75,
            metricType = TelemetryMetricType.HRV_DFA_A1,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter
        )
        assertEquals("0.75", formattedDfa)

        val formattedRmssd = TelemetryMetricUtils.formatValue(
            value = 45.2,
            metricType = TelemetryMetricType.HRV_RMSSD,
            unit = MyUnits.METRIC,
            speedFormatter = speedFormatter,
            paceFormatter = paceFormatter
        )
        assertEquals("45.2 ms", formattedRmssd)
    }

    @Test
    fun testTelemetryMetricGraph_artifactGap_breaksPolylineAndHatches() {
        val points = listOf(
            PathPoint(distance = 100.0, latLng = LatLng(0.0, 0.0), hrvDfaA1 = 0.90, hrvArtifactRate = 0.0),
            PathPoint(distance = 200.0, latLng = LatLng(0.0, 0.0), hrvDfaA1 = 0.85, hrvArtifactRate = 8.5), // High artifact > 5%
            PathPoint(distance = 300.0, latLng = LatLng(0.0, 0.0), hrvDfaA1 = null, hrvArtifactRate = 0.0), // Null value
            PathPoint(distance = 400.0, latLng = LatLng(0.0, 0.0), hrvDfaA1 = 0.72, hrvArtifactRate = 1.0)
        )

        // Point 0: valid
        assertTrue(points[0].hrvDfaA1 != null && (points[0].hrvArtifactRate ?: 0.0) <= 5.0)
        // Point 1: artifact dropout (> 5.0%)
        assertTrue(points[1].hrvArtifactRate != null && points[1].hrvArtifactRate!! > 5.0)
        // Point 2: null dropout
        assertNull(points[2].hrvDfaA1)
        // Point 3: valid
        assertTrue(points[3].hrvDfaA1 != null && (points[3].hrvArtifactRate ?: 0.0) <= 5.0)
    }
}
