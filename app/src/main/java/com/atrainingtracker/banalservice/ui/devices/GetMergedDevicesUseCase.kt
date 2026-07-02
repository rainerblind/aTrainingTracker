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

package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class GetMergedDevicesUseCase(
    private val dbRepo: DeviceDataRepository,
    private val serviceRepo: BANALServiceRepository,
    private val application: Application
) {
    // The single source of truth for the UI (List and Edit)
    val mergedDevices: Flow<List<DeviceUiData>> = combine(
        dbRepo.allDevices,
        serviceRepo.allActiveDevicesTelemetry,
        serviceRepo.newlyFoundDevicesIds
    ) { dbList, activeTelemetry, foundIds ->
        dbList.map { knownDevice ->
            val telemetry = activeTelemetry.find { it.deviceId == knownDevice.id }
            val isFound = foundIds.contains(knownDevice.id)

            when {
                telemetry != null -> {
                    val mainSensorData = telemetry.mainValue
                    if (mainSensorData != null) {
                        val unitId = MyHelper.getUnitsId(mainSensorData.sensor)
                        val unit = if (unitId > 0) application.getString(unitId) else ""
                        knownDevice.copy(
                            isConnected = true,
                            lastSeen = application.getString(R.string.devices_now),
                            mainValue = "${mainSensorData.value} $unit".trim(),
                            allValues = telemetry.allValues.map {
                                "${it.sensor.getShortName(application)}: ${it.value}"
                            }
                        )
                    } else {
                        knownDevice.copy(
                            isConnected = true,
                            lastSeen = application.getString(R.string.devices_now),
                            mainValue = null,
                            allValues = telemetry.allValues.map {
                                "${it.sensor.getShortName(application)}: ${it.value}"
                            }
                        )
                    }
                }

                isFound -> knownDevice.copy(
                    isConnected = true,
                    lastSeen = application.getString(R.string.devices_now)
                )

                else -> knownDevice.copy(isConnected = false, mainValue = null)
            }
        }
    }

    /**
     * Provides a live, merged object for the Edit View.
     */
    fun getMergedDeviceById(id: Long): Flow<DeviceUiData?> {
        return mergedDevices.map { list -> list.find { it.id == id } }
    }
}
