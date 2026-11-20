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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le

/**
 * Pure-JVM state machine guarding BLE Heart Rate connections against silent connected states
 * where ACL/GATT succeeds but zero `0x2A37` HR notification packets arrive (`ATT-9155`, `REQ-CON-054.3`).
 *
 * Shared between [BTLEHeartRateDevice] and `PolarOnlineStreamingBridge`:
 * 1. First silent window (`12 s` default) -> [Action.REENABLE_CCCD] (re-enable `0x2A37` / HR slot).
 * 2. Second silent window -> [Action.DISCONNECT_AND_RECONNECT] (clean teardown & reconnect cycle).
 * 3. Any received HR notification packet -> disarms cleanly ([State.DISARMED]).
 */
class BleHrmSilentConnectionWatchdog {

    /** Lifecycle state of the silent-HR watchdog. */
    enum class State {
        IDLE,
        ARMED_INITIAL,
        ARMED_AFTER_RESUBSCRIBE,
        DISARMED,
        RECOVERING
    }

    /** Recovery action returned when a watchdog window elapses without HR packets. */
    enum class Action {
        NONE,
        REENABLE_CCCD,
        DISCONNECT_AND_RECONNECT
    }

    private var state: State = State.IDLE
    private var generation: Long = 0L

    /** Arms the watchdog for the initial post-connect silent window and returns the new monotonic generation. */
    @Synchronized
    fun armOnConnected(): Long {
        state = State.ARMED_INITIAL
        generation++
        return generation
    }

    /**
     * Disarms the watchdog upon receiving any HR notification packet and advances the generation
     * token so any concurrent/stale watchdog tick is rejected.
     *
     * @return `true` if the watchdog was actively armed and should have its pending timer cancelled.
     */
    @Synchronized
    fun onHrPacketReceived(): Boolean {
        val wasArmed = state == State.ARMED_INITIAL || state == State.ARMED_AFTER_RESUBSCRIBE
        state = State.DISARMED
        generation++
        return wasArmed
    }

    /**
     * Advances the state machine when a watchdog timeout fires with zero HR packets received.
     */
    @Synchronized
    fun onWatchdogElapsed(): Action {
        return when (state) {
            State.ARMED_INITIAL -> {
                state = State.ARMED_AFTER_RESUBSCRIBE
                Action.REENABLE_CCCD
            }
            State.ARMED_AFTER_RESUBSCRIBE -> {
                state = State.RECOVERING
                Action.DISCONNECT_AND_RECONNECT
            }
            State.IDLE, State.DISARMED, State.RECOVERING -> Action.NONE
        }
    }

    /**
     * Generation-guarded overload returning [Action.NONE] when [expectedGeneration] no longer
     * matches the current [generation] token (stale timer after reset, disarm, or reconnect).
     */
    @Synchronized
    fun onWatchdogElapsed(expectedGeneration: Long): Action {
        if (expectedGeneration != generation) {
            return Action.NONE
        }
        return onWatchdogElapsed()
    }

    /** Resets the watchdog to [State.IDLE] and advances the generation token on disconnect, power-off, or shutdown. */
    @Synchronized
    fun reset() {
        state = State.IDLE
        generation++
    }

    /** Returns the current monotonic generation token for stale-timer rejection and testing. */
    @Synchronized
    fun getGeneration(): Long = generation

    /** Returns the current watchdog state for diagnostics and unit testing. */
    @Synchronized
    fun getState(): State = state

    companion object {
        /** Default bounded silence window before re-enabling HR notifications (`12 s`). */
        const val DEFAULT_WATCHDOG_TIMEOUT_MS: Long = 12_000L
    }
}
