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

package com.atrainingtracker.banalservice.devices.bluetooth_le

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for [BleHrmSilentConnectionWatchdog] state machine (`ATT-9155`, `REQ-CON-054.3`, `TST-CON-054.3`).
 */
class BleHrmSilentConnectionWatchdogTest {

    // REQ-CON-054.3 / TST-CON-054.3
    @Test
    fun testWatchdogEscalation_reenablesCccdThenDisconnectsAndStops_TST_CON_054_3() {
        val watchdog = BleHrmSilentConnectionWatchdog()
        assertEquals(12_000L, BleHrmSilentConnectionWatchdog.DEFAULT_WATCHDOG_TIMEOUT_MS)
        assertEquals(BleHrmSilentConnectionWatchdog.State.IDLE, watchdog.getState())
        assertEquals(BleHrmSilentConnectionWatchdog.Action.NONE, watchdog.onWatchdogElapsed())

        watchdog.armOnConnected()
        assertEquals(BleHrmSilentConnectionWatchdog.State.ARMED_INITIAL, watchdog.getState())

        // 1st silent window -> REENABLE_CCCD
        assertEquals(BleHrmSilentConnectionWatchdog.Action.REENABLE_CCCD, watchdog.onWatchdogElapsed())
        assertEquals(BleHrmSilentConnectionWatchdog.State.ARMED_AFTER_RESUBSCRIBE, watchdog.getState())

        // 2nd silent window -> DISCONNECT_AND_RECONNECT
        assertEquals(BleHrmSilentConnectionWatchdog.Action.DISCONNECT_AND_RECONNECT, watchdog.onWatchdogElapsed())
        assertEquals(BleHrmSilentConnectionWatchdog.State.RECOVERING, watchdog.getState())

        // Subsequent spurious ticks while recovering -> NONE
        assertEquals(BleHrmSilentConnectionWatchdog.Action.NONE, watchdog.onWatchdogElapsed())

        // Reset returns cleanly to IDLE and allows fresh arming
        watchdog.reset()
        assertEquals(BleHrmSilentConnectionWatchdog.State.IDLE, watchdog.getState())
    }

    // REQ-CON-054.3 / TST-CON-054.3
    @Test
    fun testOnHrPacketReceived_disarmsInitialAndPostResubscribeStates_TST_CON_054_3() {
        val watchdog = BleHrmSilentConnectionWatchdog()

        // Disarm during ARMED_INITIAL
        watchdog.armOnConnected()
        assertTrue(watchdog.onHrPacketReceived())
        assertEquals(BleHrmSilentConnectionWatchdog.State.DISARMED, watchdog.getState())
        assertFalse(watchdog.onHrPacketReceived())
        assertEquals(BleHrmSilentConnectionWatchdog.Action.NONE, watchdog.onWatchdogElapsed())

        // Disarm during ARMED_AFTER_RESUBSCRIBE
        watchdog.armOnConnected()
        assertEquals(BleHrmSilentConnectionWatchdog.Action.REENABLE_CCCD, watchdog.onWatchdogElapsed())
        assertTrue(watchdog.onHrPacketReceived())
        assertEquals(BleHrmSilentConnectionWatchdog.State.DISARMED, watchdog.getState())
        assertEquals(BleHrmSilentConnectionWatchdog.Action.NONE, watchdog.onWatchdogElapsed())
    }

    // REQ-CON-054.3 / TST-CON-054.3
    @Test
    fun testStaleGeneration_isRejectedAfterResetDisarmOrRearm_TST_CON_054_3() {
        val watchdog = BleHrmSilentConnectionWatchdog()
        val gen1 = watchdog.armOnConnected()
        assertEquals(gen1, watchdog.getGeneration())

        // Reset invalidates gen1 even after re-arming with gen2
        watchdog.reset()
        val gen2 = watchdog.armOnConnected()
        assertTrue("Re-arm must advance monotonic generation", gen2 > gen1)
        assertEquals(BleHrmSilentConnectionWatchdog.Action.NONE, watchdog.onWatchdogElapsed(gen1))
        assertEquals(BleHrmSilentConnectionWatchdog.State.ARMED_INITIAL, watchdog.getState())

        // Matching gen2 advances cleanly; HR packet advances generation and rejects gen2
        assertEquals(BleHrmSilentConnectionWatchdog.Action.REENABLE_CCCD, watchdog.onWatchdogElapsed(gen2))
        assertTrue(watchdog.onHrPacketReceived())
        assertTrue(watchdog.getGeneration() > gen2)
        assertEquals(BleHrmSilentConnectionWatchdog.Action.NONE, watchdog.onWatchdogElapsed(gen2))
    }
}
