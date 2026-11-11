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

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central controller coordinating Garmin Varia BLE radar tail light modes (REQ-CON-031).
 *
 * Manages mode selection, StVZO capability filtering, and non-blocking GATT command
 * dispatch via [BTLERadarDevice.setLightMode]. Handles automated workout lighting
 * lifecycle transitions without duplicate BLE pairing or database storage.
 */
object RadarLightController {

    private const val TAG = "RadarLightController"

    private var activeRadarDevice: BTLERadarDevice? = null

    private val _currentMode = MutableStateFlow(RadarLightMode.OFF)
    @get:JvmStatic
    val currentMode: StateFlow<RadarLightMode> = _currentMode.asStateFlow()

    private val _isLightControlAvailable = MutableStateFlow(false)
    @get:JvmStatic
    val isLightControlAvailable: StateFlow<Boolean> = _isLightControlAvailable.asStateFlow()

    private val _isStvzo = MutableStateFlow(false)
    @get:JvmStatic
    val isStvzo: StateFlow<Boolean> = _isStvzo.asStateFlow()

    /**
     * Binds a connected [BTLERadarDevice] peripheral to the controller.
     */
    @JvmStatic
    @JvmOverloads
    fun registerRadarDevice(device: BTLERadarDevice, lightSupported: Boolean, isStvzo: Boolean = false) {
        synchronized(this) {
            activeRadarDevice = device
            _isLightControlAvailable.value = lightSupported
            _isStvzo.value = isStvzo
        }
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(isLightControlSupported = lightSupported))
        Log.i(TAG, "Registered radar device (lightSupported=$lightSupported, isStvzo=$isStvzo)")
    }

    /**
     * Unbinds a disconnected [BTLERadarDevice] peripheral.
     */
    @JvmStatic
    fun unregisterRadarDevice(device: BTLERadarDevice) {
        synchronized(this) {
            if (activeRadarDevice === device) {
                activeRadarDevice = null
                _isLightControlAvailable.value = false
                _currentMode.value = RadarLightMode.OFF
            }
        }
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(isLightControlSupported = false, lightMode = RadarLightMode.OFF))
        Log.i(TAG, "Unregistered radar device")
    }

    private var cachedAutoLightControl: Boolean = TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL
    private var cachedLightDefaultMode: RadarLightMode = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE

    @JvmStatic
    fun updateCachedPreferences(autoControl: Boolean, defaultMode: RadarLightMode) {
        synchronized(this) {
            cachedAutoLightControl = autoControl
            cachedLightDefaultMode = defaultMode
        }
    }

    /**
     * Updates active mode confirmed by GATT telemetry/read (REQ-CON-031.1).
     */
    @JvmStatic
    fun updateHardwareLightMode(mode: RadarLightMode) {
        synchronized(this) {
            _currentMode.value = mode
        }
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(lightMode = mode))
    }

    /**
     * Dispatches a light mode command to the active radar peripheral or simulator.
     *
     * Automatically prunes flashing modes under StVZO regulations to [RadarLightMode.SOLID].
     * Mutates [_currentMode] and [RadarStateRepository] strictly when command dispatch succeeds (REQ-CON-031.2).
     *
     * @param mode Target tail light operating mode.
     * @return `true` if command was dispatched or simulated successfully.
     */
    @JvmStatic
    fun setLightMode(mode: RadarLightMode): Boolean {
        val targetMode = if (_isStvzo.value && !mode.isStvzoCompliant) {
            Log.w(TAG, "Mode $mode is non-StVZO compliant; falling back to SOLID")
            RadarLightMode.SOLID
        } else {
            mode
        }

        val device = synchronized(this) { activeRadarDevice }
        var dispatched = false
        if (device != null && _isLightControlAvailable.value) {
            dispatched = device.setLightMode(targetMode)
        }

        if (RadarTrafficSimulator.isSimulating || (device == null && RadarStateRepository.getState().isLightControlSupported)) {
            RadarTrafficSimulator.setSimulatedLightMode(targetMode)
            dispatched = true
        }

        if (dispatched) {
            _currentMode.value = targetMode
            val current = RadarStateRepository.getState()
            RadarStateRepository.updateState(current.copy(lightMode = targetMode))
        }

        return dispatched
    }

    /**
     * Returns the list of permitted modes, pruning flashing modes when StVZO hardware is indicated.
     */
    @JvmStatic
    fun getAvailableModes(): List<RadarLightMode> {
        return RadarLightMode.getAvailableModes(_isStvzo.value)
    }

    private fun resolveActiveConfig(context: Context?): Pair<Boolean, RadarLightMode> {
        if (context != null && TuningPreferencesDataStore.hasInitializedInstance()) {
            try {
                val cfg = TuningPreferencesDataStore.getInstance(context).getTuningConfig()
                updateCachedPreferences(cfg.radarAutoLightControl, cfg.radarLightDefaultMode)
                return Pair(cfg.radarAutoLightControl, cfg.radarLightDefaultMode)
            } catch (_: Throwable) {
                // Ignore and use cached
            }
        }
        return synchronized(this) {
            Pair(cachedAutoLightControl, cachedLightDefaultMode)
        }
    }

    /**
     * Workout start hook (REQ-CON-031.4): turns on tail light if auto-switch is enabled.
     * Executes synchronously without unmanaged coroutines or filesDir inspections (Coding Standards §5.5).
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutStarted(context: Context? = null) {
        val (autoControl, defaultMode) = resolveActiveConfig(context)
        if (autoControl) {
            setLightMode(defaultMode)
        }
    }

    /**
     * Workout finish/stop hook (REQ-CON-031.4): reverts tail light to OFF if auto-switch is enabled.
     * Executes synchronously without unmanaged coroutines or filesDir inspections (Coding Standards §5.5).
     */
    @JvmStatic
    @JvmOverloads
    fun onWorkoutStopped(context: Context? = null) {
        val (autoControl, _) = resolveActiveConfig(context)
        if (autoControl) {
            setLightMode(RadarLightMode.OFF)
        }
    }

    /**
     * Resets controller state for unit test isolation.
     */
    @JvmStatic
    fun reset() {
        synchronized(this) {
            activeRadarDevice = null
            _isLightControlAvailable.value = false
            _isStvzo.value = false
            _currentMode.value = RadarLightMode.OFF
            cachedAutoLightControl = TuningPreferencesDefaults.RADAR_AUTO_LIGHT_CONTROL
            cachedLightDefaultMode = TuningPreferencesDefaults.RADAR_LIGHT_DEFAULT_MODE
        }
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(isLightControlSupported = false, lightMode = RadarLightMode.OFF))
    }
}
