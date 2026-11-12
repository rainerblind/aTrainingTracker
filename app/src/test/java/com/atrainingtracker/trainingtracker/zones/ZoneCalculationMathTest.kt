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

package com.atrainingtracker.trainingtracker.zones

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for [ZoneCalculationMath], verifying Coggan 7-zone power math,
 * Karvonen and % HRmax heart rate models, boundary clamping, and safety invariants.
 *
 * Covers [TST-UI-336.1] and [TST-UI-336.2].
 */
class ZoneCalculationMathTest {

    @Test
    fun calculateCogganPowerZones_250W_returnsExactCutoffs() {
        // FTP = 250W
        // Z1 (55%): 138W
        // Z2 (75%): 188W
        // Z3 (90%): 225W
        // Z4 (105%): 263W
        // Z5 (120%): 300W
        // Z6 (150%): 375W
        val thresholds = ZoneCalculationMath.calculateCogganPowerZones(250)

        assertEquals(138, thresholds.z1Max)
        assertEquals(188, thresholds.z2Max)
        assertEquals(225, thresholds.z3Max)
        assertEquals(263, thresholds.z4Max)
        assertEquals(300, thresholds.z5Max)
        assertEquals(375, thresholds.z6Max)

        // Strict monotonicity check: Zn >= Zn-1 + 1
        assertTrue(thresholds.z1Max > 0)
        assertTrue(thresholds.z2Max >= thresholds.z1Max + 1)
        assertTrue(thresholds.z3Max >= thresholds.z2Max + 1)
        assertTrue(thresholds.z4Max >= thresholds.z3Max + 1)
        assertTrue(thresholds.z5Max >= thresholds.z4Max + 1)
        assertTrue(thresholds.z6Max >= thresholds.z5Max + 1)
        assertTrue(thresholds.z6Max < ZoneCalculationMath.ZONE_7_POWER_CEILING)
    }

    @Test
    fun calculateCogganPowerZones_minFtp50W_maintainsStrictMonotonicity() {
        val thresholds = ZoneCalculationMath.calculateCogganPowerZones(50)

        assertTrue(thresholds.z1Max > 0)
        assertTrue(thresholds.z2Max >= thresholds.z1Max + 1)
        assertTrue(thresholds.z3Max >= thresholds.z2Max + 1)
        assertTrue(thresholds.z4Max >= thresholds.z3Max + 1)
        assertTrue(thresholds.z5Max >= thresholds.z4Max + 1)
        assertTrue(thresholds.z6Max >= thresholds.z5Max + 1)
    }

    @Test
    fun calculateCogganPowerZones_maxFtp600W_withinPhysicalCeiling() {
        val thresholds = ZoneCalculationMath.calculateCogganPowerZones(600)

        assertEquals(330, thresholds.z1Max)
        assertEquals(450, thresholds.z2Max)
        assertEquals(540, thresholds.z3Max)
        assertEquals(630, thresholds.z4Max)
        assertEquals(720, thresholds.z5Max)
        assertEquals(900, thresholds.z6Max)
        assertTrue(thresholds.z6Max < ZoneCalculationMath.ZONE_7_POWER_CEILING)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateCogganPowerZones_ftpBelow50W_throws() {
        ZoneCalculationMath.calculateCogganPowerZones(49)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateCogganPowerZones_ftpAbove600W_throws() {
        ZoneCalculationMath.calculateCogganPowerZones(601)
    }

    @Test
    fun calculateHeartRateZonesMax_190Bpm_returnsExactCutoffs() {
        // HRmax = 190 bpm
        // Z1 (59%): 112 bpm
        // Z2 (69%): 131 bpm
        // Z3 (79%): 150 bpm
        // Z4 (89%): 169 bpm
        val thresholds = ZoneCalculationMath.calculateHeartRateZonesMax(190)

        assertEquals(112, thresholds.z1Max)
        assertEquals(131, thresholds.z2Max)
        assertEquals(150, thresholds.z3Max)
        assertEquals(169, thresholds.z4Max)

        assertTrue(thresholds.z1Max > 0)
        assertTrue(thresholds.z2Max >= thresholds.z1Max + 1)
        assertTrue(thresholds.z3Max >= thresholds.z2Max + 1)
        assertTrue(thresholds.z4Max >= thresholds.z3Max + 1)
        assertTrue(thresholds.z4Max < 190)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateHeartRateZonesMax_below80Bpm_throws() {
        ZoneCalculationMath.calculateHeartRateZonesMax(79)
    }

    @Test(expected = IllegalArgumentException::class)
    fun calculateHeartRateZonesMax_above240Bpm_throws() {
        ZoneCalculationMath.calculateHeartRateZonesMax(241)
    }

    @Test
    fun calculateKarvonenHeartRateZones_validBaselines_returnsCorrectThresholds() {
        // HRmax = 190 bpm, HRrest = 50 bpm -> HRR = 140 bpm
        // Z1 (50-59%): 50 + round(0.59 * 140) = 50 + 83 = 133 bpm
        // Z2 (60-69%): 50 + round(0.69 * 140) = 50 + 97 = 147 bpm
        // Z3 (70-79%): 50 + round(0.79 * 140) = 50 + 111 = 161 bpm
        // Z4 (80-89%): 50 + round(0.89 * 140) = 50 + 125 = 175 bpm
        val thresholds = ZoneCalculationMath.calculateKarvonenHeartRateZones(hrMax = 190, hrRest = 50)

        assertEquals(133, thresholds.z1Max)
        assertEquals(147, thresholds.z2Max)
        assertEquals(161, thresholds.z3Max)
        assertEquals(175, thresholds.z4Max)

        assertTrue(thresholds.z1Max > 50)
        assertTrue(thresholds.z2Max >= thresholds.z1Max + 1)
        assertTrue(thresholds.z3Max >= thresholds.z2Max + 1)
        assertTrue(thresholds.z4Max >= thresholds.z3Max + 1)
        assertTrue(thresholds.z4Max < 190)
    }

    @Test
    fun calculateKarvonenHeartRateZones_hrMaxLessOrEqualHrRest_throws() {
        try {
            ZoneCalculationMath.calculateKarvonenHeartRateZones(hrMax = 90, hrRest = 90)
            fail("Expected IllegalArgumentException for hrMax <= hrRest")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("greater") == true)
        }

        try {
            ZoneCalculationMath.calculateKarvonenHeartRateZones(hrMax = 85, hrRest = 90)
            fail("Expected IllegalArgumentException for hrMax < hrRest")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("greater") == true)
        }
    }

    @Test
    fun calculateKarvonenHeartRateZones_reserveBelow20Bpm_throws() {
        // HRmax = 100, HRrest = 85 -> HRR = 15 < 20
        try {
            ZoneCalculationMath.calculateKarvonenHeartRateZones(hrMax = 100, hrRest = 85)
            fail("Expected IllegalArgumentException for HRR < 20")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 20") == true)
        }
    }

    @Test
    fun calculateThresholdAnchoredHeartRateZones_withRestingHr_calculatesStrictAscendingBoundaries() {
        val thresholds = ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(
            hrvt1 = 145,
            hrvt2 = 172,
            hrMax = 195,
            hrRest = 50
        )

        // z1 = round(50 + 0.70 * (145 - 50)) = round(116.5) = 117
        assertEquals(117, thresholds.z1Max)
        // z2 = hrvt1
        assertEquals(145, thresholds.z2Max)
        // z3 = round((145 + 172) / 2.0) = 159
        assertEquals(159, thresholds.z3Max)
        // z4 = hrvt2
        assertEquals(172, thresholds.z4Max)

        assertTrue(thresholds.z1Max < thresholds.z2Max)
        assertTrue(thresholds.z2Max < thresholds.z3Max)
        assertTrue(thresholds.z3Max < thresholds.z4Max)
        assertTrue(thresholds.z4Max < 195)
    }

    @Test
    fun calculateThresholdAnchoredHeartRateZones_withoutRestingHr_usesPercentageFallback() {
        val thresholds = ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(
            hrvt1 = 140,
            hrvt2 = 168,
            hrMax = 190,
            hrRest = null
        )

        // z1 = round(0.85 * 140) = 119
        assertEquals(119, thresholds.z1Max)
        assertEquals(140, thresholds.z2Max)
        // z3 = round((140 + 168) / 2.0) = 154
        assertEquals(154, thresholds.z3Max)
        assertEquals(168, thresholds.z4Max)

        assertTrue(thresholds.z1Max < thresholds.z2Max)
        assertTrue(thresholds.z2Max < thresholds.z3Max)
        assertTrue(thresholds.z3Max < thresholds.z4Max)
        assertTrue(thresholds.z4Max < 190)
    }

    @Test
    fun calculateThresholdAnchoredHeartRateZones_invalidInputs_throws() {
        // hrvt2 <= hrvt1
        try {
            ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(hrvt1 = 150, hrvt2 = 150)
            fail("Expected IllegalArgumentException for hrvt2 <= hrvt1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly greater than HRVT1") == true)
        }

        // hrMax <= hrvt2
        try {
            ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(hrvt1 = 140, hrvt2 = 175, hrMax = 170)
            fail("Expected IllegalArgumentException for hrMax <= hrvt2")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly greater than HRVT2") == true)
        }

        // out of bounds hrvt1
        try {
            ZoneCalculationMath.calculateThresholdAnchoredHeartRateZones(hrvt1 = 50, hrvt2 = 160)
            fail("Expected IllegalArgumentException for hrvt1 < MIN_HR_MAX")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("HRVT1") == true)
        }
    }
}
