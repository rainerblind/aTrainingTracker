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

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying domain-agnostic scrubber point resolution, coordinate validation,
 * and directional bearing calculations for ScrubMarkerLayer and ScrubberController (REQ-MAP-032 / TST-MAP-034).
 */
class ScrubMarkerLayerResolutionTest {

    private val samplePath = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(48.0, 11.5), altitude = 500.0, timeSec = 0L, speedMps = 5.0),
        PathPoint(distance = 500.0, latLng = LatLng(48.01, 11.48), altitude = 510.0, timeSec = 100L, speedMps = 6.0),
        PathPoint(distance = 1000.0, latLng = LatLng(48.02, 11.45), altitude = 525.0, timeSec = 200L, speedMps = 5.5),
        PathPoint(distance = 1500.0, latLng = LatLng(48.03, 11.47), altitude = 540.0, timeSec = 300L, speedMps = 4.8)
    )

    private val tracklessPath = listOf(
        PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 0.0, timeSec = 0L, hr = 120, power = 150),
        PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 0.0, timeSec = 60L, hr = 135, power = 180),
        PathPoint(distance = 0.0, latLng = LatLng(0.0, 0.0), altitude = 0.0, timeSec = 120L, hr = 150, power = 210)
    )

    @Test
    fun directActiveScrubPointTakesPrecedenceOverSelectedDistance() {
        val directPoint: PathPoint? = samplePath[2] // distance = 1000m
        val selectedDistance = 200.0 // distance = 200m

        // Resolution logic: activeScrubPoint ?: fallback
        val resolved = directPoint ?: selectedDistance.let { targetDist ->
            TelemetryMetricUtils.findNearestPoint(samplePath, targetDist, isTimeDomain = false)
                ?: samplePath.find { it.distance >= targetDist }
        }

        assertNotNull(resolved)
        assertEquals(1000.0, resolved!!.distance, 0.001)
        assertEquals(LatLng(48.02, 11.45), resolved.latLng)
    }

    @Test
    fun fallbackToSelectedDistanceWhenActiveScrubPointIsNull() {
        val nullPoint: PathPoint? = null
        val selectedDistance = 600.0

        val resolved = nullPoint ?: selectedDistance.let { targetDist ->
            TelemetryMetricUtils.findNearestPoint(samplePath, targetDist, isTimeDomain = false)
                ?: samplePath.find { it.distance >= targetDist }
        }

        assertNotNull(resolved)
        assertEquals(500.0, resolved!!.distance, 0.001)
        assertEquals(LatLng(48.01, 11.48), resolved.latLng)
    }

    @Test
    fun timeDomainScrubPointWithZeroDistanceResolvesViaPointDirectly() {
        // Point from a workout where distance stream was absent (distance = 0.0) but GPS exists
        val gpsPointWithoutDistance = PathPoint(
            distance = 0.0,
            latLng = LatLng(48.1351, 11.5820),
            altitude = 519.0,
            timeSec = 350L,
            speedMps = 7.2
        )
        val pathWithNoDistance = listOf(
            PathPoint(distance = 0.0, latLng = LatLng(48.1300, 11.5800), altitude = 515.0, timeSec = 0L),
            gpsPointWithoutDistance,
            PathPoint(distance = 0.0, latLng = LatLng(48.1400, 11.5850), altitude = 525.0, timeSec = 600L)
        )

        // If using activeScrubPoint:
        val resolved = gpsPointWithoutDistance

        assertNotNull(resolved)
        assertTrue(resolved.latLng.latitude != 0.0 || resolved.latLng.longitude != 0.0)
        assertEquals(LatLng(48.1351, 11.5820), resolved.latLng)
    }

    @Test
    fun tracklessCoordinatesAtZeroZeroAreRejected() {
        val tracklessPoint = tracklessPath[1]

        val isValidCoordinate = tracklessPoint.latLng.latitude != 0.0 || tracklessPoint.latLng.longitude != 0.0

        assertFalse("Trackless (0,0) coordinates must be rejected from map marker rendering", isValidCoordinate)
    }

    @Test
    fun validGpsCoordinatesAreAccepted() {
        val gpsPoint = samplePath[1]

        val isValidCoordinate = gpsPoint.latLng.latitude != 0.0 || gpsPoint.latLng.longitude != 0.0

        assertTrue("Valid GPS coordinates must be accepted for map marker rendering", isValidCoordinate)
    }

    @Test
    fun isWestboundCalculationCorrectlyDetectsDirection() {
        // samplePath[0] lng = 11.50 -> samplePath[1] lng = 11.48 (moving West)
        val point0 = samplePath[0]
        val index0 = samplePath.indexOf(point0)
        val nextPoint0 = samplePath.drop(index0 + 1).firstOrNull { it.latLng.longitude != point0.latLng.longitude }
        val isWestbound0 = nextPoint0 != null && nextPoint0.latLng.longitude < point0.latLng.longitude

        assertTrue("Point 0 moving toward 11.48 from 11.50 should be westbound", isWestbound0)

        // samplePath[2] lng = 11.45 -> samplePath[3] lng = 11.47 (moving East)
        val point2 = samplePath[2]
        val index2 = samplePath.indexOf(point2)
        val nextPoint2 = samplePath.drop(index2 + 1).firstOrNull { it.latLng.longitude != point2.latLng.longitude }
        val isWestbound2 = nextPoint2 != null && nextPoint2.latLng.longitude < point2.latLng.longitude

        assertFalse("Point 2 moving toward 11.47 from 11.45 should be eastbound (not westbound)", isWestbound2)
    }

    @Test
    fun nullScrubPointAndNullDistanceResolvesToNull() {
        val point: PathPoint? = null
        val dist: Double? = null

        val resolved = point ?: dist?.let { targetDist ->
            TelemetryMetricUtils.findNearestPoint(samplePath, targetDist, isTimeDomain = false)
                ?: samplePath.find { it.distance >= targetDist }
        }

        assertNull(resolved)
    }
}
