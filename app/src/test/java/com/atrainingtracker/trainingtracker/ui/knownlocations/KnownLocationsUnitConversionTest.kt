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

package com.atrainingtracker.trainingtracker.ui.knownlocations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit test suite verifying [KnownLocationsUnitConversions].
 *
 * Traceability:
 * - TST-UI-117.5: Metric (0.1m) and Imperial (integer feet) round-trip precision without drift.
 */
class KnownLocationsUnitConversionTest {

    @Test
    fun testMetricFormatting() {
        assertEquals("520 m", KnownLocationsUnitConversions.formatAltitude(520.0, isMetric = true))
        assertEquals("520.5 m", KnownLocationsUnitConversions.formatAltitude(520.5, isMetric = true))
        assertEquals("520.5 m", KnownLocationsUnitConversions.formatAltitude(520.48, isMetric = true))
        assertEquals("0 m", KnownLocationsUnitConversions.formatAltitude(0.0, isMetric = true))
    }

    @Test
    fun testImperialFormatting() {
        // 520m / 0.3048 = 1706.0367... -> 1706 ft
        assertEquals("1706 ft", KnownLocationsUnitConversions.formatAltitude(520.0, isMetric = false))
        // 1000m / 0.3048 = 3280.8398... -> 3281 ft
        assertEquals("3281 ft", KnownLocationsUnitConversions.formatAltitude(1000.0, isMetric = false))
    }

    @Test
    fun testImperialRoundTripPrecision_withoutDrift() {
        val testAltitudesInFeet = listOf(0L, 500L, 1000L, 1724L, 2500L, 5280L, 8848L)

        for (initialFeet in testAltitudesInFeet) {
            val meters = KnownLocationsUnitConversions.feetToMeters(initialFeet.toDouble())
            val roundTripFeet = KnownLocationsUnitConversions.metersToFeet(meters)
            assertEquals("Round-trip feet conversion must match initial feet exactly", initialFeet, roundTripFeet)
        }
    }

    @Test
    fun testMetricInputParsing() {
        val parsedComma = KnownLocationsUnitConversions.parseInputToMeters("520,5", isMetric = true)
        assertNotNull(parsedComma)
        assertEquals(520.5, parsedComma!!, 0.001)

        val parsedPeriod = KnownLocationsUnitConversions.parseInputToMeters("520.5", isMetric = true)
        assertNotNull(parsedPeriod)
        assertEquals(520.5, parsedPeriod!!, 0.001)

        val parsedInteger = KnownLocationsUnitConversions.parseInputToMeters("520", isMetric = true)
        assertNotNull(parsedInteger)
        assertEquals(520.0, parsedInteger!!, 0.001)

        val invalid = KnownLocationsUnitConversions.parseInputToMeters("invalid", isMetric = true)
        assertNull(invalid)
    }

    @Test
    fun testImperialInputParsing() {
        // 1000 ft input -> 304.8m
        val parsedFeet = KnownLocationsUnitConversions.parseInputToMeters("1000", isMetric = false)
        assertNotNull(parsedFeet)
        assertEquals(304.8, parsedFeet!!, 0.001)

        // Verify roundtrip back to 1000 ft
        val feetDisplay = KnownLocationsUnitConversions.metersToFeet(parsedFeet)
        assertEquals(1000L, feetDisplay)
    }
}
