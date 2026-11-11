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

package com.atrainingtracker.trainingtracker.ui.aftermath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit verification tests for [RadarTrafficSummary] model derivation and calculations (REQ-RAD-005 / TST-RAD-005).
 */
class RadarTrafficSummaryTest {

    private val mapper = WorkoutDataMapper(
        context = io.mockk.mockk(relaxed = true),
        workoutSummariesDatabaseManager = io.mockk.mockk(relaxed = true),
        sportTypeDatabaseManager = io.mockk.mockk(relaxed = true),
        equipmentDbHelper = io.mockk.mockk(relaxed = true),
        stravaUploadDbHelper = io.mockk.mockk(relaxed = true)
    )

    @Test
    fun resolveRadarTrafficSummary_returnsNull_whenTotalVehiclesIsNull() {
        val result = mapper.resolveRadarTrafficSummary(
            totalVehiclesVal = null,
            maxApproachSpeedVal = 10.0,
            maxVehicleSpeedVal = null,
            highThreatPassVal = null,
            totalDistanceM = 25000.0,
            activeTimeSec = 3600
        )
        assertNull(result)
    }

    @Test
    fun resolveRadarTrafficSummary_returnsNull_whenTotalVehiclesIsZeroOrNegative() {
        assertNull(
            mapper.resolveRadarTrafficSummary(
                totalVehiclesVal = 0.0,
                maxApproachSpeedVal = 8.0,
                maxVehicleSpeedVal = null,
                highThreatPassVal = null,
                totalDistanceM = 10000.0,
                activeTimeSec = 1800
            )
        )
        assertNull(
            mapper.resolveRadarTrafficSummary(
                totalVehiclesVal = -5.0,
                maxApproachSpeedVal = 8.0,
                maxVehicleSpeedVal = null,
                highThreatPassVal = null,
                totalDistanceM = 10000.0,
                activeTimeSec = 1800
            )
        )
    }

    @Test
    fun resolveRadarTrafficSummary_calculatesDensityAndDerivedSpeedsAccurately() {
        // 20 vehicles passed over 40 km (40,000 m) in 2 hours (7200 s)
        // Rider max speed: 10 m/s (36 km/h), Max approach speed: 15 m/s (54 km/h) -> Max vehicle speed: 25 m/s (90 km/h)
        val result = mapper.resolveRadarTrafficSummary(
            totalVehiclesVal = 20.0,
            maxApproachSpeedVal = 15.0,
            maxVehicleSpeedVal = null,
            highThreatPassVal = 3.0,
            totalDistanceM = 40000.0,
            activeTimeSec = 7200,
            riderMaxSpeedMps = 10.0
        )

        assertNotNull(result)
        assertEquals(20, result!!.totalVehicles)
        assertEquals(0.5, result.vehiclesPerKm, 0.001) // 20 veh / 40 km = 0.5 veh/km
        assertEquals(10.0, result.vehiclesPerHour, 0.001) // 20 veh / 2 h = 10.0 veh/h
        assertEquals(15.0, result.maxApproachSpeedMps, 0.001)
        assertEquals(25.0, result.maxVehicleSpeedMps, 0.001) // 10 + 15 = 25 m/s
        assertEquals(3, result.highThreatPassCount)
    }

    @Test
    fun resolveRadarTrafficSummary_usesExplicitVehicleSpeed_whenProvided() {
        val result = mapper.resolveRadarTrafficSummary(
            totalVehiclesVal = 10.0,
            maxApproachSpeedVal = 12.0,
            maxVehicleSpeedVal = 28.5,
            highThreatPassVal = 1.0,
            totalDistanceM = 20000.0,
            activeTimeSec = 3600,
            riderMaxSpeedMps = 10.0
        )

        assertNotNull(result)
        assertEquals(28.5, result!!.maxVehicleSpeedMps, 0.001)
    }

    @Test
    fun resolveRadarTrafficSummary_handlesZeroDistanceAndDurationGracefully() {
        val result = mapper.resolveRadarTrafficSummary(
            totalVehiclesVal = 5.0,
            maxApproachSpeedVal = 10.0,
            maxVehicleSpeedVal = null,
            highThreatPassVal = 0.0,
            totalDistanceM = 0.0,
            activeTimeSec = 0
        )

        assertNotNull(result)
        assertEquals(5, result!!.totalVehicles)
        assertEquals(0.0, result.vehiclesPerKm, 0.001)
        assertEquals(0.0, result.vehiclesPerHour, 0.001)
        assertEquals(10.0, result.maxApproachSpeedMps, 0.001)
    }

    @Test
    fun resolveRadarTrafficSummary_mapsPassingClearanceAndHeavyVehiclesAccurately() {
        // TST-TRK-073.4 / TST-TRK-073.7: Min clearance, close passes, heavy vehicles
        val result = mapper.resolveRadarTrafficSummary(
            totalVehiclesVal = 15.0,
            maxApproachSpeedVal = 14.0,
            maxVehicleSpeedVal = null,
            highThreatPassVal = 2.0,
            totalDistanceM = 30000.0,
            activeTimeSec = 3600,
            riderMaxSpeedMps = 8.0,
            minPassingDistanceVal = 1.15,
            closePassCountVal = 2,
            heavyVehicleCountVal = 4.0
        )

        assertNotNull(result)
        assertEquals(15, result!!.totalVehicles)
        assertEquals(1.15, result.minPassingDistanceM!!, 0.001)
        assertEquals(2, result.closePassCount)
        assertEquals(4, result.heavyVehicleCount)
    }
}
