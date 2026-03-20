package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedContainerFragment
import com.atrainingtracker.banalservice.ui.devices.devicetabs.DevicesTabbedContainerFragment.Companion.newInstance
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


// Data class to represent a remote device
data class RemoteDeviceUIData(
    val id: String,
    val name: String,
    val iconRes: Int
)


class ControlTrackingViewModel(
    private val context: Context
) : ViewModel() {

    companion object {
        val DEBUG = true
        val TAG = "ControlTrackingViewModel"
    }

    val repository = BANALServiceRepository.getInstance(context)

    // A channel for one-time events (like clicking a button to navigate)
    private val _navigationEvent = MutableSharedFlow<Protocol>(replay = 0)
    val navigationEvent = _navigationEvent.asSharedFlow()

    val devicesDatabaseManager = DevicesDatabaseManager.getInstance(context)

    val trackingMode = repository.trackingMode
    val activeSensors = repository.activeSensors


    // A cache of device data from the database
    private var devicesCache: Map<Long, RemoteDeviceUIData> = emptyMap()

    init {
        // Load the database into the map once when the ViewModel starts
        loadDevicesCache()
    }

    private fun loadDevicesCache() {
        viewModelScope.launch(Dispatchers.IO) {
            // No Cursors here! Just a clean list from the Manager.
            val rawDevices = devicesDatabaseManager.allDevicesForCache

            devicesCache = rawDevices.associate { item ->
                item.id to RemoteDeviceUIData(
                    id = item.id.toString(),
                    name = item.name ?: "Unknown",
                    iconRes = UIHelper.getIconId(item.type, item.protocol)
                )
            }
        }
    }


    /**************************************************
     * Control Sport Type
     */
    val bSportType = repository.bSportType
    fun setSport(bSportType: BSportType) {
        // TODO: pass to repository
    }

    /*
     * Remote devices
     */
    // This Flow combines the Service IDs with the Database Names/Icons
    val remoteDevices: StateFlow<List<RemoteDeviceUIData>> = repository.foundDeviceIds
        .map { ids ->
            ids.map { id ->
                devicesCache[id] ?: RemoteDeviceUIData(
                    id = id.toString(),
                    name = "New Sensor",
                    iconRes = R.drawable.research_icon
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onDeviceClicked(device: RemoteDeviceUIData) {
        // TODO: pass to repository
    }



    /**************************************************
     * Searching
     */
    val searchingForDevice = repository.searchingForDevice

    fun onSearchClicked() {
        sendBroadcast(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES)
    }

    fun isAntProperlyInstalled(): Boolean {
        return BANALService.isANTProperlyInstalled(context)
    }

    fun isBluetoothSupported(): Boolean {
        return BANALService.isProtocolSupported(context, Protocol.BLUETOOTH_LE)
    }

    fun onPairingClicked(protocol: Protocol) {
        viewModelScope.launch {
            if (DEBUG) Log.i(TAG, "onPairingClicked")
            _navigationEvent.emit(protocol)
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
            `package` = context.packageName
        }
        context.sendBroadcast(intent)
    }





}