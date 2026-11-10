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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val currentMode: StateFlow<RadarLightMode> = _currentMode.asStateFlow()

    private val _isLightControlAvailable = MutableStateFlow(false)
    val isLightControlAvailable: StateFlow<Boolean> = _isLightControlAvailable.asStateFlow()

    private val _isStvzo = MutableStateFlow(false)
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

    /**
     * Dispatches a light mode command to the active radar peripheral or simulator.
     *
     * Automatically prunes flashing modes under StVZO regulations to [RadarLightMode.SOLID].
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

        _currentMode.value = targetMode
        val current = RadarStateRepository.getState()
        RadarStateRepository.updateState(current.copy(lightMode = targetMode))

        val device = synchronized(this) { activeRadarDevice }
        var dispatched = false
        if (device != null && _isLightControlAvailable.value) {
            dispatched = device.setLightMode(targetMode)
        }

        if (RadarTrafficSimulator.isSimulating || (device == null && RadarStateRepository.getState().isLightControlSupported)) {
            RadarTrafficSimulator.setSimulatedLightMode(targetMode)
            dispatched = true
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

    private fun resolveSafeDataStoreContext(context: Context?): Context? {
        return try {
            val appCtx = context?.applicationContext ?: context ?: return null
            val dir = appCtx.filesDir ?: return null
            if (dir.path.isEmpty()) null else appCtx
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Workout start hook (REQ-CON-031.4): turns on tail light if auto-switch is enabled.
     *
     * Guards against unit test contexts with unmocked or stub `filesDir` to prevent coroutine crashes.
     */
    @JvmStatic
    fun onWorkoutStarted(context: Context?) {
        val appCtx = resolveSafeDataStoreContext(context) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStore = TuningPreferencesDataStore.getInstance(appCtx)
                val config = dataStore.tuningConfigFlow.first()
                if (config.radarAutoLightControl) {
                    val mode = config.radarLightDefaultMode
                    setLightMode(mode)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply auto workout light start mode", e)
            }
        }
    }

    /**
     * Workout finish/stop hook (REQ-CON-031.4): reverts tail light to OFF if auto-switch is enabled.
     *
     * Guards against unit test contexts with unmocked or stub `filesDir` to prevent coroutine crashes.
     */
    @JvmStatic
    fun onWorkoutStopped(context: Context?) {
        val appCtx = resolveSafeDataStoreContext(context) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStore = TuningPreferencesDataStore.getInstance(appCtx)
                val config = dataStore.tuningConfigFlow.first()
                if (config.radarAutoLightControl) {
                    setLightMode(RadarLightMode.OFF)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply auto workout light stop mode", e)
            }
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
        }
    }
}
