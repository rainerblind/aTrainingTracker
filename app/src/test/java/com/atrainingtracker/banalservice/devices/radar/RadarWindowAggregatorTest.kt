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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying 1 Hz window latching, Rule 1.8 constructor parity,
 * and passing vehicle counting hysteresis in RadarWindowAggregator (TST-CON-028.3, REQ-CON-028.5).
 */
class RadarWindowAggregatorTest {

    private lateinit var aggregator: RadarWindowAggregator

    @Before
    fun setUp() {
        aggregator = RadarWindowAggregator()
    }

    @Test
    fun testRule18ConstructorStateSeedingParity() {
        assertFalse(aggregator.hasReceivedData)
        assertEquals(0, aggregator.getVehicleCount())

        val snapshot = aggregator.sampleAndResetWindow()
        assertEquals(0, snapshot.threatCount)
        assertNull(snapshot.closestDistanceMeters)
        assertEquals(0.0, snapshot.approachSpeedMps, 0.001)
        assertEquals(0, snapshot.vehicleCount)

        // After updating and resetting, state must match constructor baseline
        aggregator.onTargetsUpdated(listOf(RadarTarget(1, 30f, 6f, ThreatLevel.APPROACHING)))
        assertTrue(aggregator.hasReceivedData)

        aggregator.resetSession()
        assertFalse(aggregator.hasReceivedData)
        assertEquals(0, aggregator.getVehicleCount())
    }

    @Test
    fun testWindowAggregator_latchesPeakSpeedAndMinDistanceOverWindow() {
        // High-rate frame 1
        aggregator.onTargetsUpdated(listOf(
            RadarTarget(1, 70f, 5f, ThreatLevel.APPROACHING)
        ))

        // High-rate frame 2 (closer, faster)
        aggregator.onTargetsUpdated(listOf(
            RadarTarget(1, 45f, 9f, ThreatLevel.APPROACHING),
            RadarTarget(2, 85f, 4f, ThreatLevel.APPROACHING)
        ))

        // High-rate frame 3 (closer, slower)
        aggregator.onTargetsUpdated(listOf(
            RadarTarget(1, 30f, 7f, ThreatLevel.APPROACHING),
            RadarTarget(2, 60f, 5f, ThreatLevel.APPROACHING)
        ))

        // 1 Hz sample tick
        val tick1 = aggregator.sampleAndResetWindow()
        assertEquals(2, tick1.threatCount)
        assertEquals(30.0, tick1.closestDistanceMeters!!, 0.001)
        assertEquals(9.0, tick1.approachSpeedMps, 0.001)

        // Consecutive 1 Hz tick without incoming frames should reset window
        val tick2 = aggregator.sampleAndResetWindow()
        assertEquals(0, tick2.threatCount)
        assertNull(tick2.closestDistanceMeters)
        assertEquals(0.0, tick2.approachSpeedMps, 0.001)
    }

    @Test
    fun testVehicleCounter_incrementsOnPassingHysteresis() {
        // Vehicle 1 approaches from 50m to 12m (<= 18m threshold)
        aggregator.onTargetsUpdated(listOf(RadarTarget(1, 50f, 8f, ThreatLevel.APPROACHING)))
        aggregator.onTargetsUpdated(listOf(RadarTarget(1, 25f, 8f, ThreatLevel.APPROACHING)))
        aggregator.onTargetsUpdated(listOf(RadarTarget(1, 10f, 8f, ThreatLevel.APPROACHING)))

        assertEquals("Vehicle has not yet completed pass", 0, aggregator.getVehicleCount())

        // Vehicle 1 passes cyclist and disappears from active target list
        aggregator.onTargetsUpdated(emptyList())

        assertEquals("Vehicle 1 pass completed", 1, aggregator.getVehicleCount())

        // Vehicle 2 approaches to 40m but turns off / aborts (> 18m threshold)
        aggregator.onTargetsUpdated(listOf(RadarTarget(2, 60f, 5f, ThreatLevel.APPROACHING)))
        aggregator.onTargetsUpdated(listOf(RadarTarget(2, 40f, 4f, ThreatLevel.APPROACHING)))
        aggregator.onTargetsUpdated(emptyList())

        assertEquals("Aborted approach must not increment vehicle count", 1, aggregator.getVehicleCount())
    }

    @Test
    fun testPassingDistanceAndLargeVehicleCount_latchesLateralClearanceAndIncrementsLargeVehicleCount() {
        assertEquals(0, aggregator.getLargeVehicleCount())

        // 1. Large truck approaches and passes at 1.35m lateral clearance (y <= 3.0m)
        aggregator.onTargetsUpdated(listOf(
            RadarTarget(
                targetId = 1,
                rangeMeters = 50f,
                approachSpeedMps = 15f,
                threatLevel = ThreatLevel.APPROACHING,
                lateralOffsetMeters = -1.6f,
                vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
            )
        ))
        aggregator.onTargetsUpdated(listOf(
            RadarTarget(
                targetId = 1,
                rangeMeters = 2.5f,
                approachSpeedMps = 15f,
                threatLevel = ThreatLevel.APPROACHING,
                lateralOffsetMeters = -1.4f,
                vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
            )
        ))
        aggregator.onTargetsUpdated(listOf(
            RadarTarget(
                targetId = 1,
                rangeMeters = 1.5f,
                approachSpeedMps = 15f,
                threatLevel = ThreatLevel.APPROACHING,
                lateralOffsetMeters = -1.35f,
                vehicleSizeCategory = VehicleSizeCategory.TRUCK_OR_BUS
            )
        ))

        // 1 Hz sample tick must latch minimum passing clearance observed during pass (1.35m)
        val tick1 = aggregator.sampleAndResetWindow()
        assertEquals(1.35, tick1.passingDistanceMeters!!, 0.001)

        // Target finishes passing and exits radar tracking field
        aggregator.onTargetsUpdated(emptyList())
        assertEquals(1, aggregator.getVehicleCount())
        assertEquals(1, aggregator.getLargeVehicleCount())

        // 2. Standard car approaches and passes at 2.1m lateral clearance
        aggregator.onTargetsUpdated(listOf(
            RadarTarget(
                targetId = 2,
                rangeMeters = 2.0f,
                approachSpeedMps = 12f,
                threatLevel = ThreatLevel.APPROACHING,
                lateralOffsetMeters = -2.1f,
                vehicleSizeCategory = VehicleSizeCategory.CAR
            )
        ))
        val tick2 = aggregator.sampleAndResetWindow()
        assertEquals(2.1, tick2.passingDistanceMeters!!, 0.001)

        aggregator.onTargetsUpdated(emptyList())
        assertEquals(2, aggregator.getVehicleCount())
        assertEquals("Car must not increment large vehicle count", 1, aggregator.getLargeVehicleCount())
    }
}
