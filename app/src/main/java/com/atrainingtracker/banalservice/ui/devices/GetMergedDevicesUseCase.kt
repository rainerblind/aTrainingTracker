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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository

class GetMergedDevicesUseCase(
    private val dbRepo: DeviceDataRepository,
    private val serviceRepo: BANALServiceRepository,
    private val application: Application
) {
    // The single source of truth for the UI (List and Edit)
    val mergedDevices: LiveData<List<DeviceUiData>> = MediatorLiveData<List<DeviceUiData>>().apply {
        addSource(dbRepo.allDevices) { update() }
        addSource(serviceRepo.allActiveDevices) { update() }
        addSource(serviceRepo.newlyFoundDevicesIds.asLiveData()) { update() }
    }

    /**
     * Provides a live, merged object for the Edit View.
     */
    fun getMergedDeviceById(id: Long): LiveData<DeviceUiData?> {
        return mergedDevices.map { list -> list.find { it.id == id } }
    }

    private fun MediatorLiveData<List<DeviceUiData>>.update() {
        val dbList = dbRepo.allDevices.value ?: return
        val activeList = serviceRepo.allActiveDevices.value ?: emptyList()
        val foundIds = serviceRepo.newlyFoundDevicesIds.value

        value = dbList.map { knownDevice ->
            val activeDevice = activeList.find { it.deviceId == knownDevice.id }
            val isFound = foundIds.contains(knownDevice.id)

            when {
                activeDevice != null -> {
                    val mainSensorData = activeDevice.mainSensorData
                    val unit = application.getString(MyHelper.getUnitsId(mainSensorData.sensor))
                    knownDevice.copy(
                        isAvailable = true,
                        lastSeen = application.getString(R.string.devices_now),
                        mainValue = "${mainSensorData.value} $unit",
                        allValues = activeDevice.allSensorData.map {
                            "${it.sensor.getFullName(application)}: ${it.value}"
                        }
                    )
                }
                isFound -> knownDevice.copy(
                    isAvailable = true,
                    lastSeen = application.getString(R.string.devices_now)
                )
                else -> knownDevice.copy(isAvailable = false, mainValue = null)
            }
        }
    }
}