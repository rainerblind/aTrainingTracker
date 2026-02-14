package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper
import com.atrainingtracker.banalservice.helpers.UIHelper
import com.atrainingtracker.banalservice.ui.devices.RawDeviceData
import com.atrainingtracker.banalservice.ui.devices.RawDeviceDataRepository

class ListDeviceViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository = RawDeviceDataRepository.Companion.getInstance(application)

    // the single source of truth: the raw device list from the repository.
    private val allRawDevices: LiveData<List<RawDeviceData>> = repository.allDevices

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
                val protocolMatch = spec.protocol == null || device.protocol == spec.protocol
                val deviceTypeMatch = spec.deviceType == null || device.deviceType == spec.deviceType
                protocolMatch && deviceTypeMatch
            }
        }
    }

    // TODO: updates of isAvailable and mainValue

    // TODO: functionality like change pairing and deleting, ...
}
