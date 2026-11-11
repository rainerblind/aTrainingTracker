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

package com.atrainingtracker.banalservice.devices.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Unit test suite verifying [SolarElevationCalculator] (TST-CON-034.2, REQ-CON-034.2).
 */
class SolarElevationCalculatorTest {

    @Test
    fun testCalculateSolarElevation_summerSolsticeNoonBerlin_highElevationDay() {
        // Berlin: 52.5200° N, 13.4050° E
        // Summer solstice solar noon approx 11:06 UTC (13:06 CEST) on 2026-06-21
        val dt = ZonedDateTime.of(2026, 6, 21, 11, 6, 0, 0, ZoneOffset.UTC)
        val epochMs = dt.toInstant().toEpochMilli()

        val elevation = SolarElevationCalculator.calculateSolarElevation(52.52, 13.405, epochMs)
        // Expected astronomical solar elevation at summer solstice noon in Berlin is approx 60.9°
        assertTrue("Elevation at noon should be > 55 deg, was: $elevation", elevation > 55.0)

        val profile = SolarElevationCalculator.getSolarProfile(52.52, 13.405, epochMs)
        assertEquals(SolarElevationCalculator.SolarProfile.DAY, profile)
    }

    @Test
    fun testCalculateSolarElevation_summerSolsticeMidnightBerlin_negativeElevationNight() {
        val dt = ZonedDateTime.of(2026, 6, 21, 23, 6, 0, 0, ZoneOffset.UTC)
        val epochMs = dt.toInstant().toEpochMilli()

        val elevation = SolarElevationCalculator.calculateSolarElevation(52.52, 13.405, epochMs)
        // Expected astronomical solar elevation at midnight in Berlin is approx -14°
        assertTrue("Elevation at midnight should be < -10 deg, was: $elevation", elevation < -10.0)

        val profile = SolarElevationCalculator.getSolarProfile(52.52, 13.405, epochMs)
        assertEquals(SolarElevationCalculator.SolarProfile.DUSK_NIGHT, profile)
    }

    @Test
    fun testSolarProfileThreshold_boundaryValidation() {
        // Mock a scenario where elevation is exactly around -3.0 deg threshold
        // Equinox sunset in London (51.5, -0.1) around 18:15 UTC on 2026-03-20
        val sunsetTime = ZonedDateTime.of(2026, 3, 20, 18, 15, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val elevationAtSunset = SolarElevationCalculator.calculateSolarElevation(51.5, -0.1, sunsetTime)
        // Sunset is close to 0 deg, well above -3 deg civil twilight boundary
        assertTrue("Sunset elevation should be near horizon (-2 to +2 deg), was: $elevationAtSunset", elevationAtSunset in -2.5..2.5)

        val nightTime = ZonedDateTime.of(2026, 3, 20, 20, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val elevationAtNight = SolarElevationCalculator.calculateSolarElevation(51.5, -0.1, nightTime)
        assertTrue("Elevation in full night should be < -10 deg, was: $elevationAtNight", elevationAtNight < -10.0)
        assertEquals(SolarElevationCalculator.SolarProfile.DUSK_NIGHT, SolarElevationCalculator.getSolarProfile(51.5, -0.1, nightTime))
    }
}
