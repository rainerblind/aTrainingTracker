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

package com.atrainingtracker.banalservice.odometry

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM unit tests for [SportSpeedPriors] and [SportSpeedBand] (`REQ-FIL-047.1`, `TST-FIL-047.1`, `ATT-9694`).
 */
// Linked test specs: TST-FIL-047
class SportSpeedPriorsTest {

    @Test
    fun fromBands_userConfiguredSportTypes_reflectsExactBoundedBands_tstFil047_1() {
        val bands = listOf(
            SportSpeedBand(BSportType.UNKNOWN, 0.0, 0.1, "", null, "Other"),
            SportSpeedBand(BSportType.RUN, 0.10, 2.00, "walk", "Walk", "Walking"), // Spaziergang
            SportSpeedBand(BSportType.RUN, 2.00, 4.00, "run", "Run", "Running"), // Laufen
            SportSpeedBand(BSportType.BIKE, 2.78, 5.56, "bike", "Ride", "Biking"), // Einkaufen
            SportSpeedBand(BSportType.BIKE, 4.00, 5.50, "mtb", "MountainBikeRide", "Biking"), // MTB
            SportSpeedBand(BSportType.BIKE, 5.50, 12.00, "bike", "Ride", "Biking") // Radeln
        )

        val priors = SportSpeedPriors.fromBands(bands)

        assertEquals(2.00, priors.maxWalkSpeedMps, 1e-6)
        assertEquals(2.78, priors.minBikeSpeedMps, 1e-6)
        assertEquals(4.00, priors.maxRunSpeedMps, 1e-6)
    }

    @Test
    fun fromBands_extremeAndInvalidRows_clampedToSafeBiomechanicalBounds_tstFil047_1() {
        val extremeHigh = listOf(
            SportSpeedBand(BSportType.RUN, 0.1, 8.0, "walk", "Walk", "Walking"),
            SportSpeedBand(BSportType.BIKE, 9.0, 15.0, "bike", "Ride", "Biking"),
            SportSpeedBand(BSportType.RUN, 2.0, 12.0, "run", "Run", "Running")
        )
        val clampedHigh = SportSpeedPriors.fromBands(extremeHigh)
        assertEquals(SportSpeedPriors.MAX_WALK_MAX_MPS, clampedHigh.maxWalkSpeedMps, 1e-6)
        assertEquals(SportSpeedPriors.MIN_BIKE_MAX_MPS, clampedHigh.minBikeSpeedMps, 1e-6)
        assertEquals(SportSpeedPriors.MAX_RUN_MAX_MPS, clampedHigh.maxRunSpeedMps, 1e-6)

        val extremeLow = listOf(
            SportSpeedBand(BSportType.RUN, 0.05, 0.9, "hike", "Hike", "Walking"),
            SportSpeedBand(BSportType.BIKE, 0.2, 2.0, "bike", "Ride", "Biking"),
            SportSpeedBand(BSportType.RUN, 1.0, 2.1, "run", "Run", "Running")
        )
        val clampedLow = SportSpeedPriors.fromBands(extremeLow)
        assertEquals(SportSpeedPriors.MAX_WALK_MIN_MPS, clampedLow.maxWalkSpeedMps, 1e-6)
        assertEquals(SportSpeedPriors.MIN_BIKE_MIN_MPS, clampedLow.minBikeSpeedMps, 1e-6)
        assertEquals(SportSpeedPriors.MAX_RUN_MIN_MPS, clampedLow.maxRunSpeedMps, 1e-6)

        val invalidAndIndoor = listOf(
            SportSpeedBand(BSportType.BIKE, 0.0, 0.0, "indoor", "VirtualRide", "Biking"),
            SportSpeedBand(BSportType.BIKE, Double.NaN, 5.0),
            SportSpeedBand(BSportType.BIKE, 6.0, 3.0), // inverted
            SportSpeedBand(BSportType.RUN, -1.0, 2.0),
            SportSpeedBand(BSportType.RUN, 0.1, Double.POSITIVE_INFINITY)
        )
        assertEquals(SportSpeedPriors.DEFAULT, SportSpeedPriors.fromBands(invalidAndIndoor))
        assertEquals(SportSpeedPriors.DEFAULT, SportSpeedPriors.fromBands(emptyList()))

        val nonFiniteDirect = SportSpeedPriors(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).clamped()
        assertEquals(SportSpeedPriors.DEFAULT, nonFiniteDirect)
    }

    @Test
    fun fromBands_customLocalizedWalkWithoutEnglishMetadata_recognizedBySpeedBand_tstFil047_1() {
        val bands = listOf(
            SportSpeedBand(BSportType.RUN, 0.1, 2.25, "", "", "Other"), // Custom "Wandern"
            SportSpeedBand(BSportType.RUN, 2.25, 5.80, "run", "Run", "Running")
        )
        val priors = SportSpeedPriors.fromBands(bands)
        assertEquals(2.25, priors.maxWalkSpeedMps, 1e-6)
        assertEquals(SportSpeedPriors.DEFAULT_MIN_BIKE_SPEED_MPS, priors.minBikeSpeedMps, 1e-6)
        assertEquals(5.80, priors.maxRunSpeedMps, 1e-6)
    }
}
