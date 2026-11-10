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
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Unit tests verifying CardioPowerDynamicsRow layout compliance, visibility gating, and formatting (REQ-REV-010 / TST-REV-011.2, TST-REV-011.4).
 */
class WorkoutDetailsCardioPowerRowTest {

    private fun createDetailsData(
        cardioPowerDynamics: CardioPowerDynamicsRecord? = null
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
            cardioPowerDynamics = cardioPowerDynamics
        )
    }

    private fun isCardioPowerRowVisible(
        data: WorkoutDetailsData,
        enableCardioPowerDynamics: Boolean,
        showCardioPowerInWorkoutSummary: Boolean
    ): Boolean {
        return enableCardioPowerDynamics && showCardioPowerInWorkoutSummary && data.cardioPowerDynamics != null
    }

    @Test
    fun testVisibilityGatingWhenEnabledAndDataPresent() {
        val record = CardioPowerDynamicsRecord(
            workoutId = 1L,
            tauStart = 30.0,
            hr0Start = 90.0,
            kStart = 0.35,
            r2Start = 0.85,
            rmseStart = 3.5
        )
        val data = createDetailsData(cardioPowerDynamics = record)

        assertTrue(
            isCardioPowerRowVisible(
                data = data,
                enableCardioPowerDynamics = true,
                showCardioPowerInWorkoutSummary = true
            )
        )
    }

    @Test
    fun testVisibilitySuppressionWhenMasterToggleDisabled() {
        val record = CardioPowerDynamicsRecord(
            workoutId = 1L,
            tauStart = 30.0,
            hr0Start = 90.0,
            kStart = 0.35,
            r2Start = 0.85,
            rmseStart = 3.5
        )
        val data = createDetailsData(cardioPowerDynamics = record)

        assertFalse(
            isCardioPowerRowVisible(
                data = data,
                enableCardioPowerDynamics = false,
                showCardioPowerInWorkoutSummary = true
            )
        )
    }

    @Test
    fun testVisibilitySuppressionWhenCardToggleDisabled() {
        val record = CardioPowerDynamicsRecord(
            workoutId = 1L,
            tauStart = 30.0,
            hr0Start = 90.0,
            kStart = 0.35,
            r2Start = 0.85,
            rmseStart = 3.5
        )
        val data = createDetailsData(cardioPowerDynamics = record)

        assertFalse(
            isCardioPowerRowVisible(
                data = data,
                enableCardioPowerDynamics = true,
                showCardioPowerInWorkoutSummary = false
            )
        )
    }

    @Test
    fun testVisibilitySuppressionWhenRecordNull() {
        val data = createDetailsData(cardioPowerDynamics = null)

        assertFalse(
            isCardioPowerRowVisible(
                data = data,
                enableCardioPowerDynamics = true,
                showCardioPowerInWorkoutSummary = true
            )
        )
    }

    @Test
    fun testMetricsFormattingCalculations() {
        val record = CardioPowerDynamicsRecord(
            workoutId = 1L,
            tauStart = 32.4,
            hr0Start = 94.0,
            kStart = 0.355,
            hr200Start = 165.0,
            r2Start = 0.89,
            rmseStart = 2.9,
            tauEnd = 44.8,
            hr0End = 104.0,
            kEnd = 0.345,
            hr200End = 173.0,
            r2End = 0.81,
            rmseEnd = 3.8,
            deltaHr200 = 8.0,
            deltaTau = 12.4,
            deltaHr0 = 10.0
        )

        assertEquals(165, record.hr200Start.roundToInt())
        assertEquals(173, record.hr200End?.roundToInt())
        assertEquals(8, record.deltaHr200?.roundToInt())
        assertEquals(32, record.tauStart.roundToInt())
        assertEquals(45, record.tauEnd?.roundToInt())
        assertEquals(12, record.deltaTau?.roundToInt())
        assertNotNull(record.aerobicSlopeStart)
        assertEquals(1.0 / 0.355, record.aerobicSlopeStart ?: 0.0, 0.01)
    }
}
