/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.ui.devices.devicedata

import android.database.Cursor
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper

class RawDeviceDataProvider(
    val devicesDatabaseManager: DevicesDatabaseManager,
    val equipmentDbHelper: EquipmentDbHelper,
    val discoveryManager: EquipmentAndSportTypeDiscoveryManager
) {
    fun getDeviceData(cursor: Cursor): DeviceRawData {
        val id = cursor.getLong(cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.C_ID))
        val deviceType = DeviceType.valueOf(cursor.getString(cursor.getColumnIndex(
            DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE)))
        val protocol = Protocol.valueOf(cursor.getString(cursor.getColumnIndex(
            DevicesDatabaseManager.DevicesDbHelper.PROTOCOL)))
        val lastSeen = cursor.getString(cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE))
        val batteryPercentage = cursor.getInt(cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE))
        val manufacturer = cursor.getString(cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME))
        val deviceName = cursor.getString(cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.NAME))
        val isPaired = cursor.getInt(cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.PAIRED)) == 1
        val calibrationValue = cursor.getDouble(cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.CALIBRATION_FACTOR))

        val powerFeaturesFlags = devicesDatabaseManager.getBikePowerSensorFlags(id)
        val linkedEquipment = equipmentDbHelper.getLinkedEquipmentFromDeviceId(id)
        val availableEquipment = equipmentDbHelper.getEquipment(deviceType.sportType)

        // Predict linked sport types based on equipment mapping
        val linkedSportTypes = discoveryManager.getLinkedSportTypeNames(setOf(id)).toList()

        return DeviceRawData(
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
            availableEquipment = availableEquipment,
            powerFeaturesFlags = powerFeaturesFlags,
            linkedSportTypes = linkedSportTypes
        )
    }
}