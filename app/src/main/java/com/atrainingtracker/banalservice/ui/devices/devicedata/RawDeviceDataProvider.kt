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
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(DevicesDatabaseManager.DevicesDbHelper.C_ID))
        
        val typeString = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE)
        val deviceType = try {
            DeviceType.valueOf(typeString)
        } catch (e: Exception) {
            DeviceType.DUMMY
        }

        val protocolString = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.PROTOCOL)
        val protocol = try {
            Protocol.valueOf(protocolString)
        } catch (e: Exception) {
            Protocol.ALL
        }

        val lastSeen = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE)
        val batteryPercentage = getSafeInt(cursor, DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE)
        val manufacturer = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME)
        val deviceName = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.NAME)
        val isPaired = getSafeInt(cursor, DevicesDatabaseManager.DevicesDbHelper.PAIRED) == 1
        
        val calibIdx = cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.CALIBRATION_FACTOR)
        val calibrationValue = if (calibIdx != -1 && !cursor.isNull(calibIdx)) {
            cursor.getDouble(calibIdx)
        } else null

        val powerFeaturesFlags = devicesDatabaseManager.getBikePowerSensorFlags(id)
        val linkedEquipment = equipmentDbHelper.getLinkedEquipmentFromDeviceId(id)
        val availableEquipment = try {
            equipmentDbHelper.getEquipment(deviceType.sportType)
        } catch (e: Exception) {
            emptyList()
        }

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

    private fun getSafeString(cursor: Cursor, columnName: String): String {
        val idx = cursor.getColumnIndex(columnName)
        return if (idx != -1) cursor.getString(idx) ?: "" else ""
    }

    private fun getSafeInt(cursor: Cursor, columnName: String): Int {
        val idx = cursor.getColumnIndex(columnName)
        return if (idx != -1) cursor.getInt(idx) else -1
    }
}
