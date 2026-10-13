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

package com.atrainingtracker.trainingtracker.routes

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationAwareEtaCalculatorTest {

    @Test
    fun calcEta_flatTerrain_durationEqualsDistanceOverSpeed() {
        val distMeters = 10000.0 // 10 km
        val climbMeters = 0.0

        // Cycling default: 20 km/h = 10000m / (20/3.6) = 1800 seconds (30 mins)
        val result = ElevationAwareEtaCalculator.calculateEta(
            remainingDistanceMeters = distMeters,
            remainingClimbMeters = climbMeters,
            sportType = BSportType.BIKE
        )

        assertEquals(1800.0, result.durationSeconds, 0.5)
        assertEquals("~30 min", result.formattedDuration)
    }

    @Test
    fun calcEta_climbingTerrain_addsClimbPenaltyAccurately() {
        val distMeters = 5000.0 // 5 km
        val climbMeters = 300.0 // 300m climb

        // Flat time: 5000 / (20/3.6) = 900 seconds (15 mins)
        // Climb penalty at 500 m/h: 300 / (500/3600) = 2160 seconds (36 mins)
        // Total duration: 900 + 2160 = 3060 seconds (51 mins)
        val result = ElevationAwareEtaCalculator.calculateEta(
            remainingDistanceMeters = distMeters,
            remainingClimbMeters = climbMeters,
            sportType = BSportType.BIKE
        )

        assertEquals(3060.0, result.durationSeconds, 0.5)
        assertEquals("~51 min", result.formattedDuration)
    }

    @Test
    fun calcEta_withCurrentSpeed_usesSmoothedSpeed() {
        val distMeters = 6000.0
        val climbMeters = 0.0
        val speedMps = 6.0 // 21.6 km/h

        val result = ElevationAwareEtaCalculator.calculateEta(
            remainingDistanceMeters = distMeters,
            remainingClimbMeters = climbMeters,
            currentSpeedMps = speedMps,
            sportType = BSportType.BIKE
        )

        // 6000 / 6.0 = 1000 seconds (~17 mins)
        assertEquals(1000.0, result.durationSeconds, 0.5)
        assertEquals("~17 min", result.formattedDuration)
    }

    @Test
    fun calculateClimbAlongPath_sumsOnlyPositiveAscents() {
        val p1 = PathPoint(0.0, LatLng(48.0, 9.0), 300.0)
        val p2 = PathPoint(100.0, LatLng(48.01, 9.0), 350.0) // +50
        val p3 = PathPoint(200.0, LatLng(48.02, 9.0), 320.0) // -30 (descent ignored)
        val p4 = PathPoint(300.0, LatLng(48.03, 9.0), 380.0) // +60
        val path = listOf(p1, p2, p3, p4)

        val climb = ElevationAwareEtaCalculator.calculateClimbAlongPath(path, 0, 0.0)
        assertEquals(110.0, climb, 0.01) // 50 + 60 = 110m
    }

    @Test
    fun calculateReverseClimbAlongPath_sumsInvertedAscents() {
        val p1 = PathPoint(0.0, LatLng(48.0, 9.0), 300.0)
        val p2 = PathPoint(100.0, LatLng(48.01, 9.0), 350.0)
        val p3 = PathPoint(200.0, LatLng(48.02, 9.0), 320.0)
        val p4 = PathPoint(300.0, LatLng(48.03, 9.0), 380.0)
        val path = listOf(p1, p2, p3, p4)

        // Moving in reverse from p4 (380m) to p3 (320m): descent -60 (0 gain)
        // From p3 (320m) to p2 (350m): ascent +30m!
        // From p2 (350m) to p1 (300m): descent -50 (0 gain)
        // Total reverse climb: 30m
        val reverseClimb = ElevationAwareEtaCalculator.calculateReverseClimbAlongPath(path, 2, 0.0)
        assertEquals(30.0, reverseClimb, 0.01)
    }

    @Test
    fun formatDuration_variousThresholds() {
        assertEquals("< 1 min", ElevationAwareEtaCalculator.formatDuration(20.0))
        assertEquals("~5 min", ElevationAwareEtaCalculator.formatDuration(300.0))
        assertEquals("~1 h", ElevationAwareEtaCalculator.formatDuration(3600.0))
        assertEquals("~1 h 15 min", ElevationAwareEtaCalculator.formatDuration(4500.0))
    }
}
