package com.atrainingtracker.banalservice.ui.devices

import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType

/**
 * Simple class to hold the data from the cursor of the database.
  */
data class RawDeviceData(
    val id: Long,
    val protocol: Protocol,
    val deviceType: DeviceType,
    val lastSeen: String?,
    val batteryPercentage: Int,
    val manufacturer: String,
    val deviceName: String,
    val isPaired: Boolean,
    val calibrationValue: Double?,
    val linkedEquipment: List<String>,
    val powerFeaturesFlags: Int?
)


