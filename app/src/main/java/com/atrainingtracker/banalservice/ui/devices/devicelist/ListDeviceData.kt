package com.atrainingtracker.banalservice.ui.devices.devicelist

import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType

data class ListDeviceData (
    val id: Long,
    val protocol: Protocol,
    val deviceType: DeviceType,
    val deviceTypeIconRes: Int,
    val lastSeen: String?,
    val batteryStatusIconRes: Int,
    val manufacturer: String,
    val deviceName: String,
    val isPaired: Boolean,
    val linkedEquipment: String,
    val isAvailable: Boolean,
    val mainValue: String,
    )