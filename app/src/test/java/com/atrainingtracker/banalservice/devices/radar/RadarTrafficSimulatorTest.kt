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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying RadarTrafficSimulator scenarios, kinematics, hardware preemption,
 * and coroutine lifecycle safety (TST-CON-029.1–TST-CON-029.3, REQ-CON-029).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RadarTrafficSimulatorTest {

    @Before
    fun setUp() {
        RadarTrafficSimulator.stop()
        RadarTrafficSimulator.registerHardwareActiveProvider(null)
    }

    @After
    fun tearDown() {
        RadarTrafficSimulator.stop()
        RadarTrafficSimulator.registerHardwareActiveProvider(null)
    }

    @Test
    fun testSingleCarScenario_kinematicsAndVehicleOvertakeHysteresis() {
        val aggregator = RadarWindowAggregator()

        // At t = 0s: car at 140m, speed 12 m/s
        val frame0 = RadarTrafficSimulator.tick(RadarScenario.SINGLE_CAR, 0L, aggregator)
        assertEquals(RadarConnectionState.CONNECTED, frame0.connectionState)
        assertEquals(ThreatLevel.APPROACHING, frame0.overallThreatLevel)
        assertEquals(1, frame0.targets.size)
        assertEquals(140.0f, frame0.targets[0].rangeMeters, 0.01f)
        assertEquals(12.0f, frame0.targets[0].approachSpeedMps, 0.01f)

        // At t = 5s: car at 140 - 60 = 80m
        val frame5s = RadarTrafficSimulator.tick(RadarScenario.SINGLE_CAR, 5000L, aggregator)
        assertEquals(80.0f, frame5s.targets[0].rangeMeters, 0.01f)

        // At t = 11s: car at 140 - 132 = 8m (within PASSING_THRESHOLD_METERS <= 18m)
        val frame11s = RadarTrafficSimulator.tick(RadarScenario.SINGLE_CAR, 11000L, aggregator)
        assertEquals(8.0f, frame11s.targets[0].rangeMeters, 0.01f)
        // Vehicle has not disappeared yet, count still 0
        assertEquals(0, aggregator.getVehicleCount())

        // At t = 12s: car has passed (targets empty) -> hysteresis detects vehicle exit!
        val frame12s = RadarTrafficSimulator.tick(RadarScenario.SINGLE_CAR, 12000L, aggregator)
        assertTrue(frame12s.targets.isEmpty())
        assertEquals(ThreatLevel.NONE, frame12s.overallThreatLevel)
        assertEquals(1, aggregator.getVehicleCount())
    }

    @Test
    fun testConvoyScenario_generatesThreeSimultaneousTargets() {
        val frame = RadarTrafficSimulator.generateFrame(RadarScenario.CONVOY, 0L)
        assertEquals(RadarConnectionState.CONNECTED, frame.connectionState)
        assertEquals(ThreatLevel.APPROACHING, frame.overallThreatLevel)
        assertEquals(3, frame.targets.size)

        val targetIds = frame.targets.map { it.targetId }.toSet()
        assertEquals(setOf(1, 2, 3), targetIds)

        assertEquals(85.0f, frame.targets.first { it.targetId == 1 }.rangeMeters, 0.01f)
        assertEquals(115.0f, frame.targets.first { it.targetId == 2 }.rangeMeters, 0.01f)
        assertEquals(140.0f, frame.targets.first { it.targetId == 3 }.rangeMeters, 0.01f)
    }

    @Test
    fun testHighSpeedThreatScenario_triggersFastApproachingRedThreat() {
        val frame = RadarTrafficSimulator.generateFrame(RadarScenario.HIGH_SPEED, 0L)
        assertEquals(RadarConnectionState.CONNECTED, frame.connectionState)
        assertEquals(ThreatLevel.FAST_APPROACHING, frame.overallThreatLevel)
        assertEquals(1, frame.targets.size)
        assertEquals(25.0f, frame.targets[0].approachSpeedMps, 0.01f)
        assertEquals(ThreatLevel.FAST_APPROACHING, frame.targets[0].threatLevel)
    }

    @Test
    fun testDisconnectReconnectScenario_simulatesStarvationAndRecovery() {
        // Initial phase: Connected with approaching car
        val initial = RadarTrafficSimulator.generateFrame(RadarScenario.DISCONNECT_RECONNECT, 1000L)
        assertEquals(RadarConnectionState.CONNECTED, initial.connectionState)
        assertEquals(ThreatLevel.APPROACHING, initial.overallThreatLevel)
        assertEquals(1, initial.targets.size)

        // Mid-run starvation (3s..8s): DISCONNECTED_ERROR
        val disconnected = RadarTrafficSimulator.generateFrame(RadarScenario.DISCONNECT_RECONNECT, 5000L)
        assertEquals(RadarConnectionState.DISCONNECTED_ERROR, disconnected.connectionState)
        assertEquals(ThreatLevel.NONE, disconnected.overallThreatLevel)
        assertTrue(disconnected.targets.isEmpty())

        // Recovery (8s+): Connected again
        val recovered = RadarTrafficSimulator.generateFrame(RadarScenario.DISCONNECT_RECONNECT, 9000L)
        assertEquals(RadarConnectionState.CONNECTED, recovered.connectionState)
        assertEquals(ThreatLevel.APPROACHING, recovered.overallThreatLevel)
        assertEquals(1, recovered.targets.size)
    }

    @Test
    fun testCountryRoadLoopScenario_cyclesThroughQuietAndActivePhases() {
        // Phase 1 (0..10s): Quiet road
        val quiet = RadarTrafficSimulator.generateFrame(RadarScenario.COUNTRY_ROAD_LOOP, 2000L)
        assertEquals(RadarConnectionState.CONNECTED, quiet.connectionState)
        assertEquals(ThreatLevel.NONE, quiet.overallThreatLevel)
        assertTrue(quiet.targets.isEmpty())

        // Phase 2 (10..22s): Overtaking car
        val car = RadarTrafficSimulator.generateFrame(RadarScenario.COUNTRY_ROAD_LOOP, 15000L)
        assertEquals(ThreatLevel.APPROACHING, car.overallThreatLevel)
        assertEquals(1, car.targets.size)
        assertEquals(10, car.targets[0].targetId)

        // Phase 3 (22..25s): Quiet gap
        val gap = RadarTrafficSimulator.generateFrame(RadarScenario.COUNTRY_ROAD_LOOP, 23000L)
        assertEquals(ThreatLevel.NONE, gap.overallThreatLevel)
        assertTrue(gap.targets.isEmpty())

        // Phase 4 (25..30s): Fast threat
        val fast = RadarTrafficSimulator.generateFrame(RadarScenario.COUNTRY_ROAD_LOOP, 27000L)
        assertEquals(ThreatLevel.FAST_APPROACHING, fast.overallThreatLevel)
        assertEquals(1, fast.targets.size)
        assertEquals(20, fast.targets[0].targetId)

        // Phase 5 (30s+): Wraps around to quiet road
        val wrap = RadarTrafficSimulator.generateFrame(RadarScenario.COUNTRY_ROAD_LOOP, 32000L)
        assertEquals(ThreatLevel.NONE, wrap.overallThreatLevel)
        assertTrue(wrap.targets.isEmpty())
    }

    @Test
    fun testHardwarePreemption_haltsSimulationImmediately() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var hardwareStreaming = false
        RadarTrafficSimulator.registerHardwareActiveProvider { hardwareStreaming }

        RadarTrafficSimulator.start(
            scenario = RadarScenario.SINGLE_CAR,
            scope = testScope,
            tickIntervalMs = 125L
        )

        testScope.runCurrent()
        assertTrue(RadarTrafficSimulator.isSimulating)
        assertEquals(RadarScenario.SINGLE_CAR, RadarTrafficSimulator.activeScenario)

        // Advance one tick
        testScope.advanceTimeBy(125L)
        testScope.runCurrent()
        assertTrue(RadarTrafficSimulator.isSimulating)

        // Physical radar connects and streams data
        hardwareStreaming = true
        testScope.advanceTimeBy(125L)
        testScope.runCurrent()

        // Simulator must have halted
        assertFalse(RadarTrafficSimulator.isSimulating)
        assertNull(RadarTrafficSimulator.activeScenario)
        assertEquals(RadarConnectionState.DISCONNECTED, RadarStateRepository.getState().connectionState)
    }

    @Test
    fun testStop_cancelsJobAndResetsState() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        RadarTrafficSimulator.start(
            scenario = RadarScenario.CONVOY,
            scope = testScope,
            tickIntervalMs = 125L
        )

        testScope.runCurrent()
        assertTrue(RadarTrafficSimulator.isSimulating)

        RadarTrafficSimulator.stop()
        assertFalse(RadarTrafficSimulator.isSimulating)
        assertNull(RadarTrafficSimulator.activeScenario)
        assertEquals(RadarConnectionState.DISCONNECTED, RadarStateRepository.getState().connectionState)
    }
}
