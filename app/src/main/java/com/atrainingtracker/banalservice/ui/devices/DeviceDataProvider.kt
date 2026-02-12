package com.atrainingtracker.banalservice.ui.devices

import android.database.Cursor
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper

class DeviceDataProvider(val devicesDatabaseManager: DevicesDatabaseManager, val equipmentDbHelper: EquipmentDbHelper) {
    fun getDeviceData(cursor: Cursor): DeviceData {
        val id = cursor.getLong(cursor.getColumnIndex(DevicesDbHelper.C_ID))
        val deviceType = DeviceType.valueOf(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.DEVICE_TYPE)))
        val protocol = Protocol.valueOf(cursor.getString(cursor.getColumnIndex(DevicesDbHelper.PROTOCOL)))
        val lastSeen = cursor.getString(cursor.getColumnIndex(DevicesDbHelper.LAST_ACTIVE))
        val batteryPercentage = cursor.getInt(cursor.getColumnIndex(DevicesDbHelper.LAST_BATTERY_PERCENTAGE))
        val manufacturer = cursor.getString(cursor.getColumnIndex(DevicesDbHelper.MANUFACTURER_NAME))
        val deviceName = cursor.getString(cursor.getColumnIndex(DevicesDbHelper.NAME))
        val isPaired = cursor.getInt(cursor.getColumnIndex(DevicesDbHelper.PAIRED)) == 1

        val batteryIconId = BatteryStatusHelper.getBatteryStatusImageId(batteryPercentage)

        // CalibrationData
        val calibrationTitleRes = when (deviceType) {
            DeviceType.BIKE_POWER, DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE -> R.string.Wheel_Circumference
            DeviceType.RUN_SPEED -> R.string.Calibration_Factor
            else -> -1
        }
        val calibrationValue = cursor.getDouble(cursor.getColumnIndex(DevicesDbHelper.CALIBRATION_FACTOR)).toString()
        val bikePowerFeatures = BikePowerFeatures.fromFeatureFlags(devicesDatabaseManager.getBikePowerSensorFlags(id))
        val showWheelSizeSpinner = when (deviceType) {
            DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE -> true
            DeviceType.BIKE_POWER -> bikePowerFeatures.wheelRevolutionDataSupported
                    || bikePowerFeatures.wheelDistanceDataSupported
                    || bikePowerFeatures.wheelSpeedDataSupported
            else -> false
        }

        val availableEquipment = equipmentDbHelper.getLinkedEquipment(id)
        val linkedEquipment = equipmentDbHelper.getLinkedEquipmentFromDeviceId(id)

        return DeviceData(
            id = id,
            protocol = protocol,
            deviceType = deviceType,
            lastSeen = lastSeen,
            batteryStatusIconRes = batteryIconId,
            manufacturer = manufacturer,
            deviceName = deviceName,
            isPaired = isPaired,
            calibrationData = CalibrationData(
                titleRes = calibrationTitleRes,
                value = calibrationValue,
                showWheelSizeSpinner = showWheelSizeSpinner),
            availableEquipment = availableEquipment,
            linkedEquipment = linkedEquipment,
            powerFeatures = bikePowerFeatures
        )
    }
}