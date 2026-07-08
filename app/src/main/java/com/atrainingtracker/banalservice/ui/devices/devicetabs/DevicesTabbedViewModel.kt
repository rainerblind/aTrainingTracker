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

package com.atrainingtracker.banalservice.ui.devices.devicetabs

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * Sealed class to represent the UI state in a clean and type-safe way.
 */
sealed class UiState {
    object AwaitingDeviceTypeSelection : UiState()
    data class DisplayingTabs(val deviceType: DeviceType) : UiState()
}

/**
 * ViewModel for the DevicesTabbedContainerFragment.
 * It manages UI state and business logic, surviving configuration changes.
 */
class DevicesTabbedViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val deviceDBRepository = DeviceDataRepository.getInstance(application)

    private val deviceDiscoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BANALService.NEW_DEVICE_FOUND_INTENT) {
                // Tell the repository to refresh its data from the DB
                deviceDBRepository.handleNewDeviceFound()
            }
        }
    }

    private val banalServiceRepository: BANALServiceRepository = BANALServiceRepository.getInstance(application)
    val searchingFor: StateFlow<String?> = banalServiceRepository.searchingForDevice
    val isSearchingForNewDevices: StateFlow<Boolean> = banalServiceRepository.isSearchingForNewDevices

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    // Protocol is retrieved once from SavedStateHandle, which gets it from the fragment's arguments.
    val protocol: Protocol = Protocol.valueOf(savedStateHandle[BANALService.PROTOCOL]!!)

    private var isSearching = false

    init {
        // Register the deviceDiscoveryReceiver
        val filter = IntentFilter(BANALService.NEW_DEVICE_FOUND_INTENT)

        // In Android 14+, we must specify RECEIVER_NOT_EXPORTED or EXPORTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(deviceDiscoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            application.registerReceiver(deviceDiscoveryReceiver, filter)
        }

        // Check if deviceType was already saved (e.g., after process death)
        val savedDeviceType: DeviceType? = savedStateHandle.get<String>(BANALService.DEVICE_TYPE)?.let {
            DeviceType.valueOf(it)
        }

        if (savedDeviceType != null) {
            // If we have a device type, go directly to the tabs state
            _uiState.value = UiState.DisplayingTabs(savedDeviceType)
        } else {
            // Otherwise, we need to ask the user
            _uiState.value = UiState.AwaitingDeviceTypeSelection
        }

        banalServiceRepository.bindToBANALService()
    }

    /**
     * Called when the user selects a device type from the dialog.
     * Updates the state and saves it to the SavedStateHandle for process death resilience.
     */
    fun onDeviceTypeSelected(deviceType: DeviceType) {
        if (_uiState.value is UiState.AwaitingDeviceTypeSelection) {
            savedStateHandle[BANALService.DEVICE_TYPE] = deviceType.name
            _uiState.value = UiState.DisplayingTabs(deviceType)
        }
    }

    /**
     * Sends a broadcast to start searching for devices.
     */
    fun startSearching() {
        val currentState = _uiState.value
        if (isSearching || currentState !is UiState.DisplayingTabs) return

        banalServiceRepository.startSearchingForNewDevices(protocol, currentState.deviceType)

        isSearching = true
    }

    /**
     * Sends a broadcast to stop searching for devices.
     */
    fun stopSearching() {
        if (!isSearching) return

        banalServiceRepository.stopSearchingForNewDevices()

        isSearching = false
    }

    override fun onCleared() {
        super.onCleared()
        banalServiceRepository.unbindFromBANALService()
        try {
            getApplication<Application>().unregisterReceiver(deviceDiscoveryReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}
