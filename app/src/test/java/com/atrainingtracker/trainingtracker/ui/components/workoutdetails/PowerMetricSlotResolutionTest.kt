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

package com.atrainingtracker.trainingtracker.ui.components.workoutdetails

import com.atrainingtracker.banalservice.BSportType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test suite for power metric slot resolution with granular model enablement
 * and workout summary visibility flags (`REQ-UI-424.3`, `TST-UI-388.1`).
 */
class PowerMetricSlotResolutionTest {

    private fun createBaseData(
        normalizedPowerWatts: Double? = 220.0,
        intensityFactor: Double? = 0.85,
        tss: Double? = 95.0,
        workKj: Double? = 1100.0,
        avgPowerToWeight: Double? = 3.1,
        variabilityIndex: Double? = 1.05
    ): WorkoutDetailsData {
        return WorkoutDetailsData(
            totalDistance = 42000.0,
            maxDisplacement = 18000.0,
            activeTimeSec = 5400,
            totalTimeSec = 5700,
            avgSpeedMps = 7.78,
            bSportType = BSportType.BIKE,
            ascentMeters = 500,
            descentMeters = 480,
            normalizedPowerWatts = normalizedPowerWatts,
            intensityFactor = intensityFactor,
            tss = tss,
            workKj = workKj,
            avgPowerToWeight = avgPowerToWeight,
            variabilityIndex = variabilityIndex
        )
    }

    @Test
    fun `TST-UI-388_1 Case 1 Full Coggan with summary enabled returns Coggan Trio`() {
        val data = createBaseData()
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = true,
            enableBasicPower = true,
            showBasicPower = true
        )
        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER,
                PowerMetricSlot.INTENSITY_FACTOR,
                PowerMetricSlot.TSS
            ),
            slots
        )
    }

    @Test
    fun `TST-UI-388_1 Case 2 Coggan summary disabled falls back to Basic Physics Work kJ and W per kg`() {
        val data = createBaseData()
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = false,
            enableBasicPower = true,
            showBasicPower = true
        )
        assertEquals(
            listOf(
                PowerMetricSlot.WORK_KJ,
                PowerMetricSlot.POWER_TO_WEIGHT
            ),
            slots
        )
    }

    @Test
    fun `TST-UI-388_1 Case 3 Coggan summary disabled and Basic Power disabled returns empty list`() {
        val data = createBaseData()
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = false,
            enableBasicPower = false
        )
        assertEquals(emptyList<PowerMetricSlot>(), slots)
    }

    @Test
    fun `TST-UI-388_1 Case 4 Both Coggan and Basic Power summary disabled returns empty list`() {
        val data = createBaseData()
        val slots = resolvePowerMetricSlots(
            data = data,
            showCogganPower = false,
            showBasicPower = false
        )
        assertEquals(emptyList<PowerMetricSlot>(), slots)
    }

    @Test
    fun `TST-UI-388_1 Case 5 Coggan enabled without FTP fills available slots from basic power when showBasicPower is true`() {
        val data = createBaseData(
            normalizedPowerWatts = 220.0,
            intensityFactor = null,
            tss = null,
            workKj = 1100.0,
            avgPowerToWeight = 3.1
        )
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = true,
            showBasicPower = true
        )
        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER,
                PowerMetricSlot.WORK_KJ,
                PowerMetricSlot.POWER_TO_WEIGHT
            ),
            slots
        )
    }

    @Test
    fun `TST-UI-388_1 Case 6 Coggan enabled without FTP does not fill from basic power when showBasicPower is false`() {
        val data = createBaseData(
            normalizedPowerWatts = 220.0,
            intensityFactor = null,
            tss = null,
            workKj = 1100.0,
            avgPowerToWeight = 3.1
        )
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = true,
            showBasicPower = false
        )
        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER
            ),
            slots
        )
    }
}
