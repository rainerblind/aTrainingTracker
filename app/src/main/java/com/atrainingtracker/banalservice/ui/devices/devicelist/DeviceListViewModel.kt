package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.DeviceUiData
import com.atrainingtracker.trainingtracker.ui.util.Event
import kotlinx.coroutines.launch

data class EditDeviceNavigationEvent(val deviceId: Long, val deviceType: DeviceType)


class DeviceListViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository = DeviceDataRepository.Companion.getInstance(application)

    // the single source of truth: the raw device list from the repository.
    private val allDevices: LiveData<List<DeviceUiData>> = repository.allDevices

    private val _navigateToEditDevice = MutableLiveData<Event<EditDeviceNavigationEvent>>()
    val navigateToEditDevice: LiveData<Event<EditDeviceNavigationEvent>> = _navigateToEditDevice

    fun onDeviceSelected(deviceId: Long) {
        val deviceType = repository.getDeviceType(deviceId) // You'll need to create this simple method
        if (deviceType != null) {
            _navigateToEditDevice.postValue(Event(EditDeviceNavigationEvent(deviceId, deviceType)))
        }
    }

    /**
     * This is the key public method.
     * Fragments will call this to get a LiveData stream tailored to their specific needs.
     */
    fun getFilteredDevices(spec: DeviceFilterSpec): LiveData<List<DeviceUiData>> {
        // We apply another .map transformation to our already-transformed list.
        // This returns a new LiveData stream that will re-filter whenever allListDevices changes.
        return allDevices.map { devices ->
            // First, apply the main filter based on the filter type.
            val primaryFiltered = when (spec.filterType) {
                DeviceFilterType.PAIRED -> devices.filter { it.isPaired }
                DeviceFilterType.AVAILABLE -> devices.filter { it.isAvailable }
                DeviceFilterType.ALL_KNOWN -> devices // No primary filter, return the whole list
            }

            // Then, apply secondary filters for protocol and device type to the result.
            primaryFiltered.filter { device ->
                val protocolMatch = spec.protocol == Protocol.ALL || device.protocol == spec.protocol
                val deviceTypeMatch = spec.deviceType == DeviceType.ALL || device.deviceType == spec.deviceType
                protocolMatch && deviceTypeMatch
            }
        }
    }

    // called from the device list
    // -> immediately update repo.
    fun onPairedChanged(deviceId: Long, isPaired: Boolean) {
        viewModelScope.launch {
            // Find the current state of the device from the repository's cache
            val currentState = repository.getDeviceSnapshotById(deviceId) ?: return@launch

            // Create a new state with the isPaired property flipped
            val newState = currentState.copy(isPaired = !currentState.isPaired)

            // Tell the repository to save this new state. The repository will handle
            // the database update, sending the broadcast, and updating the LiveData.
            repository.updateDevice(newState)
        }
    }

    fun deleteDevice(deviceId: Long) {
        repository.deleteDevice(deviceId)
    }
}