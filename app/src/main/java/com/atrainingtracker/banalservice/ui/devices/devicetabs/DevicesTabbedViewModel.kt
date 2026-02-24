package com.atrainingtracker.banalservice.ui.devices.devicetabs

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType

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

    private val _uiState = MutableLiveData<UiState>()
    val uiState: LiveData<UiState> = _uiState

    // Protocol is retrieved once from SavedStateHandle, which gets it from the fragment's arguments.
    val protocol: Protocol = Protocol.valueOf(savedStateHandle[BANALService.PROTOCOL]!!)

    private var isSearching = false

    init {
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

        val intent = Intent(BANALService.START_SEARCHING_FOR_NEW_DEVICES_INTENT).apply {
            putExtra(BANALService.PROTOCOL, protocol.name)
            putExtra(BANALService.DEVICE_TYPE, currentState.deviceType.name)
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
        isSearching = true
    }

    /**
     * Sends a broadcast to stop searching for devices.
     */
    fun stopSearching() {
        if (!isSearching) return

        val intent = Intent(BANALService.STOP_SEARCHING_FOR_NEW_DEVICES_INTENT).apply {
            setPackage(getApplication<Application>().packageName)
        }
        getApplication<Application>().sendBroadcast(intent)
        isSearching = false
    }
}