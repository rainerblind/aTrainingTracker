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

package com.atrainingtracker.trainingtracker.ui.sportingjourney

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit verification suite for [SportingJourneyCalculator] (REQ-SPT-002, REQ-SPT-003, TST-SPT-002, TST-SPT-003).
 */
class SportingJourneyCalculatorTest {

    @Test
    fun testEquatorPercentage() {
        assertEquals(0.0, SportingJourneyCalculator.calculateEquatorPercentage(0.0), 0.001)
        assertEquals(0.0, SportingJourneyCalculator.calculateEquatorPercentage(-100.0), 0.001)
        assertEquals(100.0, SportingJourneyCalculator.calculateEquatorPercentage(40_075.0), 0.001)
        assertEquals(50.0, SportingJourneyCalculator.calculateEquatorPercentage(20_037.5), 0.001)
        // 20,529 km from prototype: 51.2%
        assertEquals(51.226, SportingJourneyCalculator.calculateEquatorPercentage(20_529.0), 0.01)
    }

    @Test
    fun testEverestMultiplier() {
        assertEquals(0.0, SportingJourneyCalculator.calculateEverestMultiplier(0.0), 0.001)
        assertEquals(1.0, SportingJourneyCalculator.calculateEverestMultiplier(8_848.0), 0.001)
        // 88,450 m climb -> ~10.0x Mt. Everest
        assertEquals(9.996, SportingJourneyCalculator.calculateEverestMultiplier(88_450.0), 0.01)
        // 15,207 m climb -> ~1.72x Mt. Everest
        assertEquals(1.719, SportingJourneyCalculator.calculateEverestMultiplier(15_207.0), 0.01)
    }

    @Test
    fun testNonstopDays() {
        assertEquals(0.0, SportingJourneyCalculator.calculateNonstopDays(0.0), 0.001)
        assertEquals(1.0, SportingJourneyCalculator.calculateNonstopDays(24.0), 0.001)
        // 1,322 hours -> ~55.08 days
        assertEquals(55.08, SportingJourneyCalculator.calculateNonstopDays(1322.0), 0.01)
    }

    @Test
    fun testPedalRevolutions() {
        assertEquals(0L, SportingJourneyCalculator.calculateRevolutions(0.0, 3600L))
        assertEquals(0L, SportingJourneyCalculator.calculateRevolutions(80.0, 0L))
        // 85 RPM for 1 hour (3600s) = 5100 revs
        assertEquals(5100L, SportingJourneyCalculator.calculateRevolutions(85.0, 3600L))
        // 90 RPM for 30 minutes (1800s) = 2700 revs
        assertEquals(2700L, SportingJourneyCalculator.calculateRevolutions(90.0, 1800L))
    }

    @Test
    fun testHeartbeats() {
        assertEquals(0L, SportingJourneyCalculator.calculateHeartbeats(0.0, 3600L))
        assertEquals(0L, SportingJourneyCalculator.calculateHeartbeats(140.0, 0L))
        // 140 BPM for 1 hour (3600s) = 8400 beats
        assertEquals(8400L, SportingJourneyCalculator.calculateHeartbeats(140.0, 3600L))
    }

    @Test
    fun testKwhFromKjAndPower() {
        assertEquals(0.0, SportingJourneyCalculator.calculateKwhFromKj(0.0), 0.001)
        // 3600 kJ = 1.0 kWh
        assertEquals(1.0, SportingJourneyCalculator.calculateKwhFromKj(3600.0), 0.001)
        // 243,207 kJ = 67.5575 kWh
        assertEquals(67.56, SportingJourneyCalculator.calculateKwhFromKj(243_207.0), 0.01)

        // 200 W for 1 hour (3600s) = 0.2 kWh
        assertEquals(0.2, SportingJourneyCalculator.calculateKwhFromPower(200.0, 3600L), 0.001)
    }

    @Test
    fun testPhysicalEquivalents() {
        val kwh = 67.6
        // E-bike range at 10 Wh/km: 67.6 kWh * 100 = 6,760 km
        assertEquals(6760.0, SportingJourneyCalculator.calculateEbikeKm(kwh), 0.1)

        // Espressos at 25 Wh: 67.6 / 0.025 = 2704 espressos
        assertEquals(2704, SportingJourneyCalculator.calculateEspressoCount(kwh))

        // Smartphone charges at 15 Wh: 67.6 / 0.015 = 4506.67 -> 4507 charges
        assertEquals(4507, SportingJourneyCalculator.calculateSmartphoneCharges(kwh))
    }

    @Test
    fun testRitualPercentage() {
        assertEquals(0, SportingJourneyCalculator.calculateRitualPercentage(0, 100))
        assertEquals(0, SportingJourneyCalculator.calculateRitualPercentage(50, 0))
        assertEquals(100, SportingJourneyCalculator.calculateRitualPercentage(100, 100))
        // 1606 ritual out of 1777 total = ~90%
        assertEquals(90, SportingJourneyCalculator.calculateRitualPercentage(1606, 1777))
    }
}
