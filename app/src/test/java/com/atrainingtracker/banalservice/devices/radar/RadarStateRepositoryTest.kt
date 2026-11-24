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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying multi-source radar arbitration, secondary timeout isolation,
 * seamless failover, and reset invariants in RadarStateRepository (TST-CON-041, REQ-CON-041).
 */
class RadarStateRepositoryTest {

    @Before
    fun setUp() {
        RadarStateRepository.reset()
    }

    @After
    fun tearDown() {
        RadarStateRepository.reset()
    }

    /**
     * TST-CON-041.1: Priority-Based Active Stream Arbitration.
     * Given an empty RadarStateRepository,
     * When ANT_PLUS connects, it becomes active.
     * When BLE connects, it preempts ANT_PLUS due to priority (BLE 3 > ANT_PLUS 2).
     * When subsequent ANT_PLUS frames arrive, BLE remains active and uncorrupted.
     */
    @Test
    fun testPriorityBasedActiveStreamArbitration() {
        val antTarget = RadarTarget(targetId = 1, rangeMeters = 50f, approachSpeedMps = 10f, threatLevel = ThreatLevel.APPROACHING)
        val antState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(antTarget),
            lastUpdateRealtimeMs = 1000L,
            source = RadarSource.ANT_PLUS
        )

        // 1. ANT+ connects and publishes
        RadarStateRepository.updateState(antState)
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getActiveSource())
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getState().source)
        assertEquals(ThreatLevel.APPROACHING, RadarStateRepository.getState().overallThreatLevel)
        assertEquals(1, RadarStateRepository.getState().targets.size)

        // 2. BLE connects and publishes high-threat target
        val bleTarget = RadarTarget(targetId = 2, rangeMeters = 20f, approachSpeedMps = 25f, threatLevel = ThreatLevel.FAST_APPROACHING)
        val bleState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(bleTarget),
            lastUpdateRealtimeMs = 1100L,
            source = RadarSource.BLE
        )
        RadarStateRepository.updateState(bleState)

        // BLE has higher priority (3 > 2), preempts ANT_PLUS as active stream
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())
        assertEquals(RadarSource.BLE, RadarStateRepository.getState().source)
        assertEquals(ThreatLevel.FAST_APPROACHING, RadarStateRepository.getState().overallThreatLevel)
        assertEquals(1, RadarStateRepository.getState().targets.size)
        assertEquals(20f, RadarStateRepository.getState().targets.first().rangeMeters, 0.01f)

        // 3. Subsequent ANT+ frame arrives with medium threat
        val subsequentAntState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(antTarget.copy(rangeMeters = 40f)),
            lastUpdateRealtimeMs = 1200L,
            source = RadarSource.ANT_PLUS
        )
        RadarStateRepository.updateState(subsequentAntState)

        // Active state must NOT be clobbered by lower-priority ANT+ stream
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())
        assertEquals(RadarSource.BLE, RadarStateRepository.getState().source)
        assertEquals(ThreatLevel.FAST_APPROACHING, RadarStateRepository.getState().overallThreatLevel)
        assertEquals(20f, RadarStateRepository.getState().targets.first().rangeMeters, 0.01f)

        // Isolated query returns updated ANT+ state
        assertEquals(40f, RadarStateRepository.getState(RadarSource.ANT_PLUS).targets.first().rangeMeters, 0.01f)
    }

    /**
     * TST-CON-041.2: Secondary Disconnection & Timeout Isolation.
     * Given BLE is actively streaming as primary,
     * When secondary ANT+ times out and emits DISCONNECTED_ERROR,
     * Then repository global state remains CONNECTED on BLE, while ANT+ isolated state is DISCONNECTED_ERROR.
     */
    @Test
    fun testSecondaryDisconnectionAndTimeoutIsolation() {
        val bleState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(RadarTarget(1, 30f, 12f, ThreatLevel.APPROACHING)),
            lastUpdateRealtimeMs = 1000L,
            source = RadarSource.BLE
        )
        val antState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(RadarTarget(1, 35f, 11f, ThreatLevel.APPROACHING)),
            lastUpdateRealtimeMs = 1000L,
            source = RadarSource.ANT_PLUS
        )

        RadarStateRepository.updateState(antState)
        RadarStateRepository.updateState(bleState)
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)

        // ANT+ secondary watchdog expires and publishes DISCONNECTED_ERROR
        val antTimeoutState = RadarState(
            connectionState = RadarConnectionState.DISCONNECTED_ERROR,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList(),
            lastUpdateRealtimeMs = 4500L,
            source = RadarSource.ANT_PLUS
        )
        RadarStateRepository.updateState(antTimeoutState)

        // Primary BLE stream remains CONNECTED and active
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)
        assertEquals(1, RadarStateRepository.getState().targets.size)

        // Isolated query reflects the secondary error state
        assertEquals(RadarConnectionState.DISCONNECTED_ERROR, RadarStateRepository.getState(RadarSource.ANT_PLUS).connectionState)
        assertTrue(RadarStateRepository.getState(RadarSource.ANT_PLUS).targets.isEmpty())
    }

    /**
     * TST-CON-041.3: Seamless Failover on Primary Disconnect.
     * Given both BLE and ANT+ are active with BLE arbitrated,
     * When BLE disconnects,
     * Then repository seamlessly fails over to ANT+ as active source.
     */
    @Test
    fun testSeamlessFailoverOnPrimaryDisconnect() {
        val bleState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(RadarTarget(1, 25f, 15f, ThreatLevel.APPROACHING)),
            lastUpdateRealtimeMs = 1000L,
            source = RadarSource.BLE
        )
        val antState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(RadarTarget(1, 27f, 14f, ThreatLevel.APPROACHING)),
            lastUpdateRealtimeMs = 1050L,
            source = RadarSource.ANT_PLUS
        )

        RadarStateRepository.updateState(antState)
        RadarStateRepository.updateState(bleState)
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())

        // BLE disconnects
        val bleDisconnectState = RadarState(
            connectionState = RadarConnectionState.DISCONNECTED,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList(),
            lastUpdateRealtimeMs = 2000L,
            source = RadarSource.BLE
        )
        RadarStateRepository.updateState(bleDisconnectState)

        // Seamless failover to ANT+ connected source
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getActiveSource())
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getState().source)
        assertEquals(1, RadarStateRepository.getState().targets.size)
        assertEquals(27f, RadarStateRepository.getState().targets.first().rangeMeters, 0.01f)
    }

    /**
     * TST-CON-041.5: Active CONNECTED stream not preempted by CONNECTING stream.
     * Given ANT_PLUS is CONNECTED and actively publishing targets,
     * When BLE begins CONNECTING (searching / connecting state with 0 targets),
     * Then ANT_PLUS remains the active source and active road targets are preserved (REQ-CON-041.2).
     * When BLE reaches CONNECTED, BLE then preempts ANT_PLUS.
     */
    @Test
    fun testConnectedSourceNotPreemptedByConnectingSource() {
        val antTarget = RadarTarget(targetId = 1, rangeMeters = 45f, approachSpeedMps = 15f, threatLevel = ThreatLevel.APPROACHING)
        val antConnectedState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.APPROACHING,
            targets = listOf(antTarget),
            lastUpdateRealtimeMs = 1000L,
            source = RadarSource.ANT_PLUS
        )
        RadarStateRepository.updateState(antConnectedState)

        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getActiveSource())
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)
        assertEquals(1, RadarStateRepository.getState().targets.size)
        assertEquals(45f, RadarStateRepository.getState().targets.first().rangeMeters, 0.01f)

        // BLE begins scanning / connecting (priority 3, but connectionState is CONNECTING)
        val bleConnectingState = RadarState(
            connectionState = RadarConnectionState.CONNECTING,
            overallThreatLevel = ThreatLevel.NONE,
            targets = emptyList(),
            lastUpdateRealtimeMs = 1100L,
            source = RadarSource.BLE
        )
        RadarStateRepository.updateState(bleConnectingState)

        // ANT_PLUS must remain the active source with its active targets!
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getActiveSource())
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getState().source)
        assertEquals(1, RadarStateRepository.getState().targets.size)
        assertEquals(45f, RadarStateRepository.getState().targets.first().rangeMeters, 0.01f)

        // Isolated BLE state shows CONNECTING
        assertEquals(RadarConnectionState.CONNECTING, RadarStateRepository.getState(RadarSource.BLE).connectionState)

        // Now BLE connects successfully
        val bleTarget = RadarTarget(targetId = 2, rangeMeters = 25f, approachSpeedMps = 20f, threatLevel = ThreatLevel.FAST_APPROACHING)
        val bleConnectedState = RadarState(
            connectionState = RadarConnectionState.CONNECTED,
            overallThreatLevel = ThreatLevel.FAST_APPROACHING,
            targets = listOf(bleTarget),
            lastUpdateRealtimeMs = 1500L,
            source = RadarSource.BLE
        )
        RadarStateRepository.updateState(bleConnectedState)

        // BLE now preempts ANT_PLUS because it reached CONNECTED
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())
        assertEquals(RadarConnectionState.CONNECTED, RadarStateRepository.getState().connectionState)
        assertEquals(RadarSource.BLE, RadarStateRepository.getState().source)
        assertEquals(1, RadarStateRepository.getState().targets.size)
        assertEquals(25f, RadarStateRepository.getState().targets.first().rangeMeters, 0.01f)
    }

    /**
     * TST-CON-041.4: Reset Invariant.
     * Given repository with multiple source states,
     * When reset() is invoked,
     * Then all source states and active source are reset to baseline disconnected state.
     */
    @Test
    fun testResetInvariant() {
        RadarStateRepository.updateState(
            RadarState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        RadarStateRepository.updateState(
            RadarState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.ANT_PLUS)
        )
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())

        RadarStateRepository.reset()

        assertEquals(RadarSource.NONE, RadarStateRepository.getActiveSource())
        assertEquals(RadarConnectionState.DISCONNECTED, RadarStateRepository.getState().connectionState)
        assertEquals(RadarSource.NONE, RadarStateRepository.getState().source)

        // Isolated source queries must also return default disconnected state
        assertEquals(RadarConnectionState.DISCONNECTED, RadarStateRepository.getState(RadarSource.BLE).connectionState)
        assertEquals(RadarSource.BLE, RadarStateRepository.getState(RadarSource.BLE).source)
        assertEquals(RadarConnectionState.DISCONNECTED, RadarStateRepository.getState(RadarSource.ANT_PLUS).connectionState)
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getState(RadarSource.ANT_PLUS).source)
    }

    /**
     * TST-CON-029 (ATT-8672): Verifies built-in RadarTrafficSimulator, RadarScenario,
     * SimulatedRadarDevice, and RadarSource.SIMULATOR are completely removed from classpath.
     */
    @Test
    fun testSimulatorRemoval_classpathAndRadarSourceEnum() {
        assertEquals(
            listOf(RadarSource.NONE, RadarSource.ANT_PLUS, RadarSource.BLE),
            RadarSource.values().toList()
        )
        val removedFqcns = listOf(
            "com.atrainingtracker.banalservice.devices.radar.RadarTrafficSimulator",
            "com.atrainingtracker.banalservice.devices.radar.RadarScenario",
            "com.atrainingtracker.banalservice.devices.radar.SimulatedRadarDevice"
        )
        for (fqcn in removedFqcns) {
            val missing = try {
                Class.forName(fqcn, false, javaClass.classLoader)
                false
            } catch (_: ClassNotFoundException) {
                true
            }
            assertTrue("Removed simulator class $fqcn must not exist on classpath", missing)
        }
    }

    /**
     * TST-CON-046.4 / REQ-CON-046.2, REQ-CON-046.3 (ATT-8455, updated ATT-8672):
     * Multi-source failover (BLE -> ANT_PLUS) preserves manual isAutoMode == false
     * and audioSnoozeState across sourceStates, while reset() cleanly restores default RadarState().
     */
    @Test
    fun testMultiSourceFailover_preservesIsAutoModeAndAudioSnooze_andResetIsolatesCleanly() {
        val activeSnooze = RadarAudioSnoozeState(isSnoozed = true, mode = RadarAudioSnoozeMode.SNOOZE_15M)

        // Connect ANT_PLUS and BLE initially in auto mode
        RadarStateRepository.updateState(
            RadarState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.ANT_PLUS, isAutoMode = true)
        )
        RadarStateRepository.updateState(
            RadarState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE, isAutoMode = true)
        )
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())

        // User enables manual light mode + audio snooze via updateControlState while BLE is active
        RadarStateRepository.updateControlState {
            it.copy(isAutoMode = false, lightMode = RadarLightMode.SOLID, audioSnoozeState = activeSnooze)
        }
        assertFalse(RadarStateRepository.getState().isAutoMode)
        assertFalse(RadarStateRepository.getState(RadarSource.ANT_PLUS).isAutoMode)

        // BLE disconnects -> fails over to ANT_PLUS
        RadarStateRepository.updateState(
            RadarState(
                connectionState = RadarConnectionState.DISCONNECTED,
                source = RadarSource.BLE,
                isAutoMode = false,
                audioSnoozeState = activeSnooze
            )
        )
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getActiveSource())
        assertFalse("Failover to ANT_PLUS must preserve isAutoMode=false", RadarStateRepository.getState().isAutoMode)
        assertTrue("Failover to ANT_PLUS must preserve audioSnoozeState", RadarStateRepository.getState().audioSnoozeState.isSnoozed)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_15M, RadarStateRepository.getState().audioSnoozeState.mode)

        // Explicit auto restoration propagates cleanly
        RadarStateRepository.updateControlState { it.copy(isAutoMode = true) }
        assertTrue(RadarStateRepository.getState().isAutoMode)

        // reset() isolates cleanly to default RadarState()
        RadarStateRepository.updateControlState { it.copy(isAutoMode = false, audioSnoozeState = activeSnooze) }
        RadarStateRepository.reset()
        assertEquals(RadarState(), RadarStateRepository.getState())
        assertTrue(RadarStateRepository.getState().isAutoMode)
        assertFalse(RadarStateRepository.getState().audioSnoozeState.isSnoozed)
    }

    /**
     * TST-CON-046.4 / REQ-CON-046.2 (ATT-8455 Bounce 1 Heal, updated ATT-8672):
     * Preemption by higher-priority BLE with default flags (isAutoMode=true, INACTIVE snooze) and
     * disconnect failover with stale default flags must NOT corrupt active manual isAutoMode=false
     * or active audioSnoozeState across any tracked sourceStates.
     */
    @Test
    fun testPreemptionAndFailoverWithDefaultSnapshots_preserveControlStateAcrossAllSources() {
        val activeSnooze = RadarAudioSnoozeState(isSnoozed = true, mode = RadarAudioSnoozeMode.SNOOZE_30M)

        // 1. ANT_PLUS connects, then user sets manual mode + snooze
        RadarStateRepository.updateState(
            RadarState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.ANT_PLUS)
        )
        RadarStateRepository.updateControlState {
            it.copy(isAutoMode = false, lightMode = RadarLightMode.SOLID, audioSnoozeState = activeSnooze)
        }

        // 2. Higher-priority BLE preempts ANT_PLUS with default isAutoMode=true & INACTIVE snooze
        RadarStateRepository.updateState(
            RadarState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        assertEquals(RadarSource.BLE, RadarStateRepository.getActiveSource())
        assertFalse("BLE preemption must preserve isAutoMode=false", RadarStateRepository.getState().isAutoMode)
        assertTrue("BLE preemption must preserve active snooze", RadarStateRepository.getState().audioSnoozeState.isSnoozed)
        assertFalse(RadarStateRepository.getState(RadarSource.BLE).isAutoMode)
        assertFalse(RadarStateRepository.getState(RadarSource.ANT_PLUS).isAutoMode)

        // 3. BLE disconnects carrying stale default isAutoMode=true & INACTIVE snooze -> fails over to ANT_PLUS
        RadarStateRepository.updateState(
            RadarState(connectionState = RadarConnectionState.DISCONNECTED, source = RadarSource.BLE)
        )
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getActiveSource())
        assertFalse("Failover to ANT_PLUS must preserve isAutoMode=false", RadarStateRepository.getState().isAutoMode)
        assertEquals(RadarAudioSnoozeMode.SNOOZE_30M, RadarStateRepository.getState().audioSnoozeState.mode)
        assertFalse("Disconnected BLE entry must remain synced at isAutoMode=false", RadarStateRepository.getState(RadarSource.BLE).isAutoMode)

        // 4. Conditional telemetry returning null leaves repository untouched
        val skipped = RadarStateRepository.updateTelemetryStateIf { null }
        assertEquals(null, skipped)
        assertFalse(RadarStateRepository.getState().isAutoMode)
    }

    /**
     * TST-CON-061.1 / REQ-CON-061.1 (ATT-8731):
     * Single-source-of-truth V2 bonding flag preservation across CONNECTED BLE frames,
     * zero resurrection after explicit clear, auto-clear + prompt-latch reset on
     * DISCONNECTED / DISCONNECTED_ERROR / CONNECTING, and clean BLE > ANT_PLUS arbitration.
     */
    @Test
    fun testV2BondingState_ssotPreservation_disconnectAutoClear_arbitrationAndOneShotLatch_tstCon061_1() {
        // 1. BLE connects and marks V2 bonding required with untrimmed MAC
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        RadarStateRepository.setV2BondingRequired(true, "  AA:BB:CC:DD:EE:FF  ")

        assertTrue(RadarStateRepository.getState().isV2BondingRequired)
        assertEquals("AA:BB:CC:DD:EE:FF", RadarStateRepository.getState().v2BondingDeviceAddress)
        assertEquals(RadarV2BondingStatus(true, "AA:BB:CC:DD:EE:FF"), RadarStateRepository.getV2BondingStatus())

        // 2. Subsequent 4-8 Hz BLE CONNECTED frame (default false) preserves SSoT true + MAC
        RadarStateRepository.updateThreatState(
            RadarThreatState(
                connectionState = RadarConnectionState.CONNECTED,
                overallThreatLevel = ThreatLevel.APPROACHING,
                targets = listOf(RadarTarget(1, 30f, 10f, ThreatLevel.APPROACHING)),
                source = RadarSource.BLE
            )
        )
        assertTrue(RadarStateRepository.getState().isV2BondingRequired)
        assertEquals("AA:BB:CC:DD:EE:FF", RadarStateRepository.getState().v2BondingDeviceAddress)

        // 3. One-shot prompt latch fires strictly once per session (case-insensitive)
        assertTrue(RadarStateRepository.consumeV2BondingPromptIfPending("aa:bb:cc:dd:ee:ff"))
        assertFalse(RadarStateRepository.consumeV2BondingPromptIfPending("AA:BB:CC:DD:EE:FF"))

        // 4. Explicit clear (ALREADY_BONDED) clears flag & resets latch; subsequent CONNECTED frame never resurrects it
        RadarStateRepository.setV2BondingRequired(false, null)
        assertFalse(RadarStateRepository.getState().isV2BondingRequired)
        assertEquals(null, RadarStateRepository.getState().v2BondingDeviceAddress)
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        assertFalse("Subsequent CONNECTED frame must not resurrect cleared bonding flag", RadarStateRepository.getState().isV2BondingRequired)

        // 5. BLE > ANT_PLUS arbitration + disconnect auto-clear & failover + prompt latch reset
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.ANT_PLUS)
        )
        RadarStateRepository.setV2BondingRequired(true, "AA:BB:CC:DD:EE:FF")
        assertTrue(RadarStateRepository.getState().isV2BondingRequired)
        assertTrue(RadarStateRepository.consumeV2BondingPromptIfPending("AA:BB:CC:DD:EE:FF"))

        // BLE disconnects -> auto-clears BLE bonding, resets latch, fails over to ANT_PLUS
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.DISCONNECTED, source = RadarSource.BLE)
        )
        assertEquals(RadarSource.ANT_PLUS, RadarStateRepository.getActiveSource())
        assertFalse(RadarStateRepository.getState().isV2BondingRequired)
        assertFalse(RadarStateRepository.getState(RadarSource.BLE).isV2BondingRequired)

        // Reconnect unbonded BLE -> prompt latch was reset on disconnect, so prompt can fire once again
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        RadarStateRepository.setV2BondingRequired(true, "AA:BB:CC:DD:EE:FF")
        assertTrue("Disconnect must reset one-shot prompt latch for next connection", RadarStateRepository.consumeV2BondingPromptIfPending("AA:BB:CC:DD:EE:FF"))

        // DISCONNECTED_ERROR and CONNECTING auto-clear bonding flag
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.DISCONNECTED_ERROR, source = RadarSource.BLE)
        )
        assertFalse(RadarStateRepository.getState(RadarSource.BLE).isV2BondingRequired)

        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        RadarStateRepository.setV2BondingRequired(true, "AA:BB:CC:DD:EE:FF")
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTING, source = RadarSource.BLE)
        )
        assertFalse(RadarStateRepository.getState(RadarSource.BLE).isV2BondingRequired)
    }

    /**
     * TST-CON-061.1 / REQ-CON-061.1 (ATT-8731 Bounce 1 Heal):
     * setV2BondingRequired(true, ...) MUST be ignored whenever BLE is absent or not CONNECTED
     * (DISCONNECTED, DISCONNECTED_ERROR, CONNECTING), preventing stale-disconnect races.
     */
    @Test
    fun testV2BondingState_setTrueIgnoredWhenBleNotConnected_tstCon061_1() {
        // 1. No BLE source state yet -> ignored
        RadarStateRepository.setV2BondingRequired(true, "AA:BB:CC:DD:EE:FF")
        assertFalse(RadarStateRepository.getState().isV2BondingRequired)
        assertEquals(null, RadarStateRepository.getState().v2BondingDeviceAddress)

        // 2. Only ANT_PLUS connected -> ignored
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.ANT_PLUS)
        )
        RadarStateRepository.setV2BondingRequired(true, "AA:BB:CC:DD:EE:FF")
        assertFalse(RadarStateRepository.getState().isV2BondingRequired)

        // 3. BLE DISCONNECTED / DISCONNECTED_ERROR / CONNECTING -> all ignored
        for (state in listOf(
            RadarConnectionState.DISCONNECTED,
            RadarConnectionState.DISCONNECTED_ERROR,
            RadarConnectionState.CONNECTING
        )) {
            RadarStateRepository.updateThreatState(
                RadarThreatState(connectionState = state, source = RadarSource.BLE)
            )
            RadarStateRepository.setV2BondingRequired(true, "AA:BB:CC:DD:EE:FF")
            assertFalse(
                "setV2BondingRequired(true) must be ignored when BLE is $state",
                RadarStateRepository.getState(RadarSource.BLE).isV2BondingRequired
            )
            assertEquals(null, RadarStateRepository.getState(RadarSource.BLE).v2BondingDeviceAddress)
        }
    }
}
