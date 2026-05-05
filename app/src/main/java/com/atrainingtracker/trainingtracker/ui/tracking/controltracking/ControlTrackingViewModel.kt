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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.WorkoutNavigationEvents
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


// Data class to represent a remote device
data class RemoteDeviceUIData(
    val id: Long,
    val deviceType: DeviceType,
    val name: String,
    val iconRes: Int
)

// Sealed class for navigation destinations
sealed class ControlNavigation {
    data class ToPairing(val protocol: Protocol) : ControlNavigation()
    data class ToEditDevice(val deviceId: Long, val deviceType: DeviceType) : ControlNavigation()
}

class ControlTrackingViewModel(
    private val application: Application
) : ViewModel() {

    companion object {
        val DEBUG = true
        val TAG = "ControlTrackingViewModel"
    }

    val banalServiceRepository = BANALServiceRepository.getInstance(application)
    private val devicesRepository = DeviceDataRepository.getInstance(application)

    // A channel for one-time events (like clicking a button to navigate)
    private val _navigationEvent = MutableSharedFlow<ControlNavigation>(replay = 0)
    val navigationEvent = _navigationEvent.asSharedFlow()

    val devicesDatabaseManager = DevicesDatabaseManager.getInstance(application)

    val trackingMode = banalServiceRepository.trackingMode
    val activeSensors = banalServiceRepository.activeSensors


    /**************************************************
     * Control Sport Type
     */
    val bSportType = banalServiceRepository.bSportType
    fun setSport(bSportType: BSportType) {
        banalServiceRepository.setUserSelectedSportType(bSportType)
    }

    /*
     * Remote devices
     */
    // 1. Convert the Repository LiveData (Single Source of Truth) to a Flow
    private val allDevicesFromDb = devicesRepository.allDevices.asFlow()

    // 2. Combine the IDs from the BANALService with the Data from the Database
    val remoteDevices: StateFlow<List<RemoteDeviceUIData>> = banalServiceRepository.activeRemoteDevicesIds
        .combine(allDevicesFromDb) { ids, dbDevices ->
            ids.map { id ->
                // Look for the device in the DB list
                val dbDevice = dbDevices.find { it.id == id }

                if (dbDevice != null) {
                    // If found in DB, use the latest name and type
                    RemoteDeviceUIData(
                        id = dbDevice.id,
                        deviceType = dbDevice.deviceType,
                        name = dbDevice.deviceName ?: "Unknown Sensor",
                        iconRes = UIHelper.getIconId(dbDevice.deviceType, dbDevice.protocol)
                    )
                } else {
                    // Fallback if the device is found by service but not yet in DB
                    RemoteDeviceUIData(
                        id = id,
                        deviceType = DeviceType.DUMMY,
                        name = "New Sensor",
                        iconRes = R.drawable.research_icon
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onDeviceClicked(device: RemoteDeviceUIData) {
        viewModelScope.launch {
            _navigationEvent.emit(ControlNavigation.ToEditDevice(device.id, device.deviceType))
        }
    }



    /**************************************************
     * Searching
     */
    val searchingForDevice = banalServiceRepository.searchingForDevice

    fun onSearchClicked() {
        banalServiceRepository.startSearchingForPairedDevices()
    }

    fun isAntProperlyInstalled(): Boolean {
        return BANALService.areAllANTServicesInstalled(application)
    }

    fun isBluetoothSupported(): Boolean {
        return BANALService.isProtocolSupported(application, Protocol.BLUETOOTH_LE)
    }

    fun onPairingClicked(protocol: Protocol) {
        viewModelScope.launch {
            if (DEBUG) Log.i(TAG, "onPairingClicked")
            _navigationEvent.emit(ControlNavigation.ToPairing(protocol))
        }
    }


    /***********************************************************************************************
     * Control Tracking
     */
    // TODO: 1. create a tracking repository which sends these boradcasts.
    // TODO: 2. Replace this sending of Broadcasts.
    fun onStartTracking() {
        sendBroadcast(TrainingApplication.REQUEST_START_TRACKING)
    }

    fun onPauseTracking() {
        sendBroadcast(TrainingApplication.REQUEST_PAUSE_TRACKING)
    }

    fun onResumeTracking() {
        sendBroadcast(TrainingApplication.REQUEST_RESUME_FROM_PAUSED)
    }

    fun onStopTracking() {
        sendBroadcast(TrainingApplication.REQUEST_STOP_TRACKING)
    }

    /***********************************************************************************************
     * Simple helper to send a broadcast
     */
    fun sendBroadcast(action: String) {
        val intent = Intent(action).apply {
            `package` = application.packageName
        }
        application.sendBroadcast(intent)
    }

}