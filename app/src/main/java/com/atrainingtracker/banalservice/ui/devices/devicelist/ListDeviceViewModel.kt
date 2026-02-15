package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.RawDeviceData
import com.atrainingtracker.banalservice.ui.devices.RawDeviceDataRepository
import com.atrainingtracker.trainingtracker.ui.util.Event

class ListDeviceViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository = RawDeviceDataRepository.Companion.getInstance(application)

    // the single source of truth: the raw device list from the repository.
    private val allRawDevices: LiveData<List<RawDeviceData>> = repository.allDevices

    // LiveData to trigger navigation. It holds the ID of the device to edit.
    private val _navigateToEditDevice = MutableLiveData<Event<Long>>()
    val navigateToEditDevice: LiveData<Event<Long>>
        get() = _navigateToEditDevice

    // A public function the fragment will call from the click listener.
    fun onDeviceSelected(deviceId: Long) {
        _navigateToEditDevice.value = Event(deviceId)
    }

    private val allListDevices: LiveData<List<ListDeviceData>> = allRawDevices.map{ rawDevices ->
        rawDevices.map { device ->
            ListDeviceData(
                id = device.id,
                protocol = device.protocol,
                deviceType = device.deviceType,
                deviceTypeIconRes = UIHelper.getIconId(device.deviceType, device.protocol),
                lastSeen = device.lastSeen,
                batteryStatusIconRes = BatteryStatusHelper.getBatteryStatusImageId(device.batteryPercentage),
                manufacturer = device.manufacturer,
                deviceName = device.deviceName,
                isPaired = device.isPaired,
                linkedEquipment = device.linkedEquipment.joinToString(", "),
                isAvailable = false,  // TODO: get somehow from BANALService
                mainValue = "TODO" // TODO: This should come from a live value in the raw data
            )
        }
    }

    /**
     * This is the key public method.
     * Fragments will call this to get a LiveData stream tailored to their specific needs.
     */
    fun getFilteredDevices(spec: DeviceFilterSpec): LiveData<List<ListDeviceData>> {
        // We apply another .map transformation to our already-transformed list.
        // This returns a new LiveData stream that will re-filter whenever allListDevices changes.
        return allListDevices.map { devices ->
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

    // TODO: updates of isAvailable and mainValue

    // TODO: functionality like change pairing and deleting, ...
}
