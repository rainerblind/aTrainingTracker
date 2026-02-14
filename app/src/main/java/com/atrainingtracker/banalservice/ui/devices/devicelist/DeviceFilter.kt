package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.os.Parcelable
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType

import kotlinx.parcelize.Parcelize

/**
 * An enum to define which subset of devices to display.
 */
enum class DeviceFilterType {
    AVAILABLE,   // Devices currently visible
    PAIRED,      // Paired devices
    ALL_KNOWN    // All devices in the database, regardless of pairing status
}

/**
 * A data class to hold all filtering specifications for the RemoteDevicesFragment.
 * Making it Parcelable allows it to be passed easily in fragment arguments.
 */
@Parcelize
data class DeviceFilterSpec(
    val filterType: DeviceFilterType,
    val protocol: Protocol?,
    val deviceType: DeviceType?
) : Parcelable