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
 * and workout summary visibility flags (`REQ-UI-424.3`, `REQ-UI-415.1`, `TST-UI-388.1`, `TST-UI-378.1`).
 * Verifies Coggan-only resolution without basic power fallback.
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
            showCogganPower = true
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
    fun `TST-UI-388_1 Case 2 Missing FTP returns strictly Normalized Power without basic power fallback`() {
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
            showCogganPower = true
        )
        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER
            ),
            slots
        )
    }

    @Test
    fun `TST-UI-388_1 Case 3 Coggan summary disabled returns empty list`() {
        val data = createBaseData()
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = false
        )
        assertEquals(emptyList<PowerMetricSlot>(), slots)
    }

    @Test
    fun `TST-UI-388_1 Case 4 Coggan master disabled returns empty list`() {
        val data = createBaseData()
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = false,
            showCogganPower = true
        )
        assertEquals(emptyList<PowerMetricSlot>(), slots)
    }

    @Test
    fun `TST-UI-388_1 Case 5 Missing Normalized Power returns empty list`() {
        val data = createBaseData(
            normalizedPowerWatts = null,
            intensityFactor = 0.85,
            tss = 95.0
        )
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = true
        )
        assertEquals(emptyList<PowerMetricSlot>(), slots)
    }

    @Test
    fun `TST-UI-388_1 Case 6 Partial FTP with only IF present returns NP and IF`() {
        val data = createBaseData(
            normalizedPowerWatts = 220.0,
            intensityFactor = 0.82,
            tss = null
        )
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = true
        )
        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER,
                PowerMetricSlot.INTENSITY_FACTOR
            ),
            slots
        )
    }

    @Test
    fun `TST-UI-388_1 Case 7 Partial FTP with only TSS present returns NP and TSS`() {
        val data = createBaseData(
            normalizedPowerWatts = 220.0,
            intensityFactor = null,
            tss = 75.0
        )
        val slots = resolvePowerMetricSlots(
            data = data,
            enableCogganPower = true,
            showCogganPower = true
        )
        assertEquals(
            listOf(
                PowerMetricSlot.NORMALIZED_POWER,
                PowerMetricSlot.TSS
            ),
            slots
        )
    }
}
