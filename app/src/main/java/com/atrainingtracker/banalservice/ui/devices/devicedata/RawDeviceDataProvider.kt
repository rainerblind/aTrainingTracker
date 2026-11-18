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
import com.atrainingtracker.util.safeEnumValueOf
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.database.WheelPosition

class RawDeviceDataProvider(
    val devicesDatabaseManager: DevicesDatabaseManager,
    val equipmentDbHelper: EquipmentDbHelper,
    val discoveryManager: EquipmentAndSportTypeDiscoveryManager
) {
    fun getDeviceData(cursor: Cursor): DeviceRawData {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(DevicesDatabaseManager.DevicesDbHelper.C_ID))
        
        val typeString = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE)
        val deviceType = safeEnumValueOf(typeString, DeviceType.DUMMY, "RawDeviceDataProvider")

        val protocolString = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.PROTOCOL)
        val protocol = safeEnumValueOf(protocolString, Protocol.ALL, "RawDeviceDataProvider")

        val lastSeen = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.LAST_ACTIVE)
        val batteryPercentage = getSafeInt(cursor, DevicesDatabaseManager.DevicesDbHelper.LAST_BATTERY_PERCENTAGE)
        val manufacturer = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.MANUFACTURER_NAME)
        val deviceName = getSafeString(cursor, DevicesDatabaseManager.DevicesDbHelper.NAME)
        val isPaired = getSafeInt(cursor, DevicesDatabaseManager.DevicesDbHelper.PAIRED) == 1
        
        val calibIdx = cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.CALIBRATION_FACTOR)
        val calibrationValue = if (calibIdx != -1 && !cursor.isNull(calibIdx)) {
            cursor.getDouble(calibIdx)
        } else null

        val baselineIdx = cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.BASELINE_CIRCUMFERENCE)
        val baselineCircumference = if (baselineIdx != -1 && !cursor.isNull(baselineIdx)) {
            cursor.getDouble(baselineIdx)
        } else null

        val offlineRecIdx = cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.POLAR_H10_OFFLINE_RECORDING)
        val polarH10OfflineRecording = if (offlineRecIdx != -1 && !cursor.isNull(offlineRecIdx)) {
            cursor.getInt(offlineRecIdx) == 1
        } else true

        val liveImuIdx = cursor.getColumnIndex(DevicesDatabaseManager.DevicesDbHelper.POLAR_LIVE_IMU_ENABLED)
        val polarLiveImuEnabled = if (liveImuIdx != -1 && !cursor.isNull(liveImuIdx)) {
            cursor.getInt(liveImuIdx) == 1
        } else false

        val powerFeaturesFlags = devicesDatabaseManager.getBikePowerSensorFlags(id)
        val linkedEquipment = equipmentDbHelper.getLinkedEquipmentFromDeviceId(id)
        val availableEquipment = try {
            equipmentDbHelper.getEquipment(deviceType.sportType)
        } catch (e: Exception) {
            emptyList()
        }

        // Predict linked sport types based on equipment mapping
        val linkedSportTypes = discoveryManager.getLinkedSportTypeNames(setOf(id)).toList()

        val (linkedWheelPositions, wheelPositionConflicts) = if (isWheelSpeedSensor(deviceType)) {
            hydrateWheelPositionsAndConflicts(id, linkedEquipment, availableEquipment)
        } else {
            emptyMap<String, WheelPosition>() to emptyMap<String, Map<WheelPosition, String>>()
        }

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
            linkedSportTypes = linkedSportTypes,
            baselineCircumference = baselineCircumference,
            polarH10OfflineRecording = polarH10OfflineRecording,
            polarLiveImuEnabled = polarLiveImuEnabled,
            linkedWheelPositions = linkedWheelPositions,
            wheelPositionConflicts = wheelPositionConflicts
        )
    }

    /**
     * Resolves per-bike [WheelPosition] assignments for this sensor and any conflicting FRONT/REAR
     * assignments held by other sensors on each bike (`REQ-SEN-031.1`).
     */
    private fun hydrateWheelPositionsAndConflicts(
        sensorId: Long,
        linkedEquipment: List<String>,
        availableEquipment: List<String>
    ): Pair<Map<String, WheelPosition>, Map<String, Map<WheelPosition, String>>> {
        val positions = linkedEquipment.associateWith { eqName ->
            val eqId = runCatching { equipmentDbHelper.getEquipmentId(eqName) }.getOrDefault(-1L)
            if (eqId > 0L) {
                runCatching { equipmentDbHelper.getWheelPosition(eqId, sensorId) }
                    .getOrNull() ?: WheelPosition.UNSPECIFIED
            } else {
                WheelPosition.UNSPECIFIED
            }
        }
        val conflicts = mutableMapOf<String, Map<WheelPosition, String>>()
        for (eqName in (linkedEquipment + availableEquipment).distinct()) {
            val eqId = runCatching { equipmentDbHelper.getEquipmentId(eqName) }.getOrDefault(-1L)
            if (eqId <= 0L) continue
            val bikePositions = runCatching {
                equipmentDbHelper.getDeviceWheelPositionsForEquipment(eqId)
            }.getOrDefault(emptyMap())
            val occupiedByOthers = mutableMapOf<WheelPosition, String>()
            for ((otherSensorId, pos) in bikePositions) {
                if (otherSensorId != sensorId && (pos == WheelPosition.FRONT || pos == WheelPosition.REAR)) {
                    val otherName = runCatching {
                        devicesDatabaseManager.getDeviceName(otherSensorId)
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "#$otherSensorId"
                    occupiedByOthers[pos] = otherName
                }
            }
            if (occupiedByOthers.isNotEmpty()) {
                conflicts[eqName] = occupiedByOthers
            }
        }
        return positions to conflicts
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
