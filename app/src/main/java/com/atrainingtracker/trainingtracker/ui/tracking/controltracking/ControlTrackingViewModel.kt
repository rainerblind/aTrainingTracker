package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.content.Context
import android.content.Intent
import androidx.activity.result.launch
import androidx.compose.ui.input.key.type
import androidx.lifecycle.ViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val repository: BANALServiceRepository,
    private val context: Context
) : ViewModel() {


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
        val intent = Intent(TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES).apply {
            `package` = context.packageName
        }
        context.sendBroadcast(intent)
    }

    fun isAntProperlyInstalled(): Boolean {
        return BANALService.isANTProperlyInstalled(context)
    }

    fun isBluetoothSupported(): Boolean {
        return BANALService.isProtocolSupported(context, Protocol.BLUETOOTH_LE)
    }

    fun onPairingClicked(protocol: Protocol) {
        // TODO: pass to repository
    }


    /***********************************************************************************************
     * Control Tracking
     */
    fun onStartTracking() {
        // TODO: pass to repository
    }

    fun onPauseTracking() {
        // TODO: pass to repository
    }

    fun onResumeTracking() {
        // TODO: pass to repository
    }

    fun onStopTracking() {
        // TODO: pass to repository
    }

}