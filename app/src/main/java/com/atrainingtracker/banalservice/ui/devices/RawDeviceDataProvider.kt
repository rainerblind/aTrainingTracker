package com.atrainingtracker.banalservice.ui.devices

import android.database.Cursor
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper

class RawDeviceDataProvider(val devicesDatabaseManager: DevicesDatabaseManager, val equipmentDbHelper: EquipmentDbHelper) {
    fun getDeviceData(cursor: Cursor): RawDeviceData {
        val id = cursor.getLong(cursor.getColumnIndex(DevicesDbHelper.C_ID))
        val deviceType = DeviceType.valueOf(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.DEVICE_TYPE)))
        val protocol = Protocol.valueOf(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.PROTOCOL)))
        val lastSeen = cursor.getString(cursor.getColumnIndex(DevicesDbHelper.LAST_ACTIVE))
        val batteryPercentage = cursor.getInt(cursor.getColumnIndex(DevicesDbHelper.LAST_BATTERY_PERCENTAGE))
        val manufacturer = cursor.getString(cursor.getColumnIndex(DevicesDbHelper.MANUFACTURER_NAME))
        val deviceName = cursor.getString(cursor.getColumnIndex(DevicesDbHelper.NAME))
        val isPaired = cursor.getInt(cursor.getColumnIndex(DevicesDbHelper.PAIRED)) == 1
        val calibrationValue = cursor.getDouble(cursor.getColumnIndex(DevicesDbHelper.CALIBRATION_FACTOR))

        val powerFeaturesFlags = devicesDatabaseManager.getBikePowerSensorFlags(id)
        val linkedEquipment = equipmentDbHelper.getLinkedEquipmentFromDeviceId(id)

        return RawDeviceData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = lastSeen,
            batteryPercentage = batteryPercentage,
            manufacturer = manufacturer,
            deviceName = deviceName,
            isPaired = isPaired,
            calibrationValue = calibrationValue,
            linkedEquipment = linkedEquipment,
            powerFeaturesFlags = powerFeaturesFlags
        )
    }
}