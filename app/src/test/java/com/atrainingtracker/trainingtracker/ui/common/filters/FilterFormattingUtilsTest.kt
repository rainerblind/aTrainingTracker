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

package com.atrainingtracker.trainingtracker.ui.common.filters

import com.atrainingtracker.banalservice.BANALService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying [FilterFormattingUtils] unit conversions, rounding consistency,
 * athletic presets, and chip formatting strings (REQ-UI-508 / TST-UI-508).
 */
class FilterFormattingUtilsTest {

    // --- REQ-UI-508.1 / TST-UI-508.1: Distance Rounding & Formatting ---

    @Test
    fun testFormatDistanceValue_stripsTrailingZeroForIntegers() {
        val multiplierKm = FilterFormattingUtils.getDistanceMultiplier(isImperial = false)
        val multiplierMi = FilterFormattingUtils.getDistanceMultiplier(isImperial = true)

        // Metric integers: 10,000m -> 10km, 25,000m -> 25km
        assertEquals("10", FilterFormattingUtils.formatDistanceValue(10000.0, multiplierKm))
        assertEquals("25", FilterFormattingUtils.formatDistanceValue(25000.0, multiplierKm))

        // Imperial integers: 10 mi -> 16,093.44m, 25 mi -> 40,233.6m
        assertEquals("10", FilterFormattingUtils.formatDistanceValue(10.0 * BANALService.METER_PER_MILE, multiplierMi))
        assertEquals("25", FilterFormattingUtils.formatDistanceValue(25.0 * BANALService.METER_PER_MILE, multiplierMi))
    }

    @Test
    fun testFormatDistanceValue_roundsToMaxOneDecimalPlace() {
        val multiplierKm = FilterFormattingUtils.getDistanceMultiplier(isImperial = false)
        val multiplierMi = FilterFormattingUtils.getDistanceMultiplier(isImperial = true)

        // Metric decimals: 12.5km -> 12,500m
        assertEquals("12.5", FilterFormattingUtils.formatDistanceValue(12500.0, multiplierKm))
        // Metric decimals rounding: 12.54km -> 12.5, 12.56km -> 12.6
        assertEquals("12.5", FilterFormattingUtils.formatDistanceValue(12540.0, multiplierKm))
        assertEquals("12.6", FilterFormattingUtils.formatDistanceValue(12560.0, multiplierKm))

        // Imperial decimals: 5.5 mi -> 8,851.392m (AC-4)
        assertEquals("5.5", FilterFormattingUtils.formatDistanceValue(5.5 * BANALService.METER_PER_MILE, multiplierMi))
        // Precision artifact resilience: 9.999999999999998 mi -> 10
        assertEquals("10", FilterFormattingUtils.formatDistanceValue(9.999999999999998 * BANALService.METER_PER_MILE, multiplierMi))
    }

    @Test
    fun testFormatDistanceInputValue_handlesNullAndZero() {
        val multiplier = FilterFormattingUtils.getDistanceMultiplier(isImperial = false)
        assertEquals("", FilterFormattingUtils.formatDistanceInputValue(null, multiplier))
        assertEquals("0", FilterFormattingUtils.formatDistanceInputValue(0.0, multiplier))
    }

    // --- REQ-UI-508.1 / TST-UI-508.1: Elevation Rounding & Formatting ---

    @Test
    fun testFormatElevationValue_roundsToInteger() {
        val multiplierM = FilterFormattingUtils.getElevationMultiplier(isImperial = false)
        val multiplierFt = FilterFormattingUtils.getElevationMultiplier(isImperial = true)

        // Metric elevation
        assertEquals("100", FilterFormattingUtils.formatElevationValue(100.0, multiplierM))
        assertEquals("250", FilterFormattingUtils.formatElevationValue(250.4, multiplierM))
        assertEquals("251", FilterFormattingUtils.formatElevationValue(250.6, multiplierM))

        // Imperial elevation: 1,000 ft -> 304.8m (AC-2)
        assertEquals("1000", FilterFormattingUtils.formatElevationValue(304.8, multiplierFt))
        assertEquals("500", FilterFormattingUtils.formatElevationValue(500.0 * BANALService.METER_PER_FOOT, multiplierFt))
    }

    @Test
    fun testFormatElevationInputValue_handlesNullAndZero() {
        val multiplier = FilterFormattingUtils.getElevationMultiplier(isImperial = false)
        assertEquals("", FilterFormattingUtils.formatElevationInputValue(null, multiplier))
        assertEquals("0", FilterFormattingUtils.formatElevationInputValue(0.0, multiplier))
    }

    // --- REQ-UI-508.2 / TST-UI-508.2: Chip Label Formatting ---

    @Test
    fun testFormatDistanceFilterString_metricAndImperial() {
        // Metric intervals
        assertEquals(
            "10 - 25 km",
            FilterFormattingUtils.formatDistanceFilterString(10000.0, 25000.0, isImperial = false)
        )
        assertEquals(
            "> 10 km",
            FilterFormattingUtils.formatDistanceFilterString(10000.0, null, isImperial = false)
        )
        assertEquals(
            "< 25 km",
            FilterFormattingUtils.formatDistanceFilterString(null, 25000.0, isImperial = false)
        )

        // Imperial intervals (AC-1)
        val tenMilesMeters = 10.0 * BANALService.METER_PER_MILE
        val twentyFiveMilesMeters = 25.0 * BANALService.METER_PER_MILE
        assertEquals(
            "10 - 25 mi",
            FilterFormattingUtils.formatDistanceFilterString(tenMilesMeters, twentyFiveMilesMeters, isImperial = true)
        )
        assertEquals(
            "> 10 mi",
            FilterFormattingUtils.formatDistanceFilterString(tenMilesMeters, null, isImperial = true)
        )
        assertEquals(
            "< 25 mi",
            FilterFormattingUtils.formatDistanceFilterString(null, twentyFiveMilesMeters, isImperial = true)
        )
    }

    @Test
    fun testFormatElevationFilterString_metricAndImperial() {
        // Metric elevation chips
        assertEquals(
            "100 - 250 m",
            FilterFormattingUtils.formatElevationFilterString(100.0, 250.0, isImperial = false)
        )
        assertEquals(
            "> 100 m",
            FilterFormattingUtils.formatElevationFilterString(100.0, null, isImperial = false)
        )
        assertEquals(
            "< 250 m",
            FilterFormattingUtils.formatElevationFilterString(null, 250.0, isImperial = false)
        )

        // Imperial elevation chips (AC-2)
        val fiveHundredFtM = 500.0 * BANALService.METER_PER_FOOT
        val oneThousandFtM = 1000.0 * BANALService.METER_PER_FOOT
        assertEquals(
            "500 - 1000 ft",
            FilterFormattingUtils.formatElevationFilterString(fiveHundredFtM, oneThousandFtM, isImperial = true)
        )
        assertEquals(
            "> 1000 ft",
            FilterFormattingUtils.formatElevationFilterString(oneThousandFtM, null, isImperial = true)
        )
        assertEquals(
            "< 500 ft",
            FilterFormattingUtils.formatElevationFilterString(null, fiveHundredFtM, isImperial = true)
        )
    }

    // --- REQ-UI-508.3 / TST-UI-508.3: Presets & Two-Way Meter Conversions ---

    @Test
    fun testGetDistancePresets_providesAthleticThresholds() {
        val metricPresets = FilterFormattingUtils.getDistancePresets(isImperial = false)
        assertEquals(5, metricPresets.size)
        assertEquals("< 10 km", metricPresets[0].first)
        assertNull(metricPresets[0].second)
        assertEquals(10000.0, metricPresets[0].third!!, 0.001)

        assertEquals("10 - 25 km", metricPresets[1].first)
        assertEquals(10000.0, metricPresets[1].second!!, 0.001)
        assertEquals(25000.0, metricPresets[1].third!!, 0.001)

        assertEquals("> 100 km", metricPresets[4].first)
        assertEquals(100000.0, metricPresets[4].second!!, 0.001)
        assertNull(metricPresets[4].third)

        val imperialPresets = FilterFormattingUtils.getDistancePresets(isImperial = true)
        assertEquals(5, imperialPresets.size)
        assertEquals("< 10 mi", imperialPresets[0].first)
        assertNull(imperialPresets[0].second)
        assertEquals(10.0 * BANALService.METER_PER_MILE, imperialPresets[0].third!!, 0.001)

        assertEquals("10 - 25 mi", imperialPresets[1].first)
        assertEquals(10.0 * BANALService.METER_PER_MILE, imperialPresets[1].second!!, 0.001)
        assertEquals(25.0 * BANALService.METER_PER_MILE, imperialPresets[1].third!!, 0.001)

        assertEquals("> 100 mi", imperialPresets[4].first)
        assertEquals(100.0 * BANALService.METER_PER_MILE, imperialPresets[4].second!!, 0.001)
        assertNull(imperialPresets[4].third)
    }

    @Test
    fun testGetElevationPresets_providesAthleticThresholds() {
        val metricPresets = FilterFormattingUtils.getElevationPresets(isImperial = false)
        assertEquals(5, metricPresets.size)
        assertEquals("< 100 m", metricPresets[0].first)
        assertNull(metricPresets[0].second)
        assertEquals(100.0, metricPresets[0].third!!, 0.001)

        assertEquals("> 1000 m", metricPresets[4].first)
        assertEquals(1000.0, metricPresets[4].second!!, 0.001)
        assertNull(metricPresets[4].third)

        val imperialPresets = FilterFormattingUtils.getElevationPresets(isImperial = true)
        assertEquals(5, imperialPresets.size)
        assertEquals("< 500 ft", imperialPresets[0].first)
        assertNull(imperialPresets[0].second)
        assertEquals(500.0 * BANALService.METER_PER_FOOT, imperialPresets[0].third!!, 0.001)

        assertEquals("500 - 1000 ft", imperialPresets[1].first)
        assertEquals(500.0 * BANALService.METER_PER_FOOT, imperialPresets[1].second!!, 0.001)
        assertEquals(1000.0 * BANALService.METER_PER_FOOT, imperialPresets[1].third!!, 0.001)

        assertEquals("> 4000 ft", imperialPresets[4].first)
        assertEquals(4000.0 * BANALService.METER_PER_FOOT, imperialPresets[4].second!!, 0.001)
        assertNull(imperialPresets[4].third)
    }

    @Test
    fun testInputToMeterConversionAndBack_preservesAccuracy() {
        val imperialDistMultiplier = FilterFormattingUtils.getDistanceMultiplier(isImperial = true)
        val enteredMiles = 5.5
        val metersStored = enteredMiles * imperialDistMultiplier
        assertEquals(8851.392, metersStored, 0.001)

        val formattedBack = FilterFormattingUtils.formatDistanceInputValue(metersStored, imperialDistMultiplier)
        assertEquals("5.5", formattedBack)

        val imperialElevMultiplier = FilterFormattingUtils.getElevationMultiplier(isImperial = true)
        val enteredFeet = 1000.0
        val elevationMetersStored = enteredFeet * imperialElevMultiplier
        assertEquals(304.8, elevationMetersStored, 0.001)

        val elevationFormattedBack = FilterFormattingUtils.formatElevationInputValue(elevationMetersStored, imperialElevMultiplier)
        assertEquals("1000", elevationFormattedBack)
    }

    @Test
    fun testUnitAbbreviations() {
        assertEquals("km", FilterFormattingUtils.getDistanceUnit(isImperial = false))
        assertEquals("mi", FilterFormattingUtils.getDistanceUnit(isImperial = true))
        assertEquals("m", FilterFormattingUtils.getElevationUnit(isImperial = false))
        assertEquals("ft", FilterFormattingUtils.getElevationUnit(isImperial = true))
    }
}
