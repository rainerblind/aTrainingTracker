package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import androidx.compose.animation.core.copy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.MyDevice
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceUiData
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.ui.tracking.BANALServiceRepository

class GetMergedDevicesUseCase(
    private val dbRepo: DeviceDataRepository,
    private val serviceRepo: BANALServiceRepository,
    private val application: Application
) {
    // The single source of truth for the UI (List and Edit)
    val mergedDevices: LiveData<List<DeviceUiData>> = MediatorLiveData<List<DeviceUiData>>().apply {
        addSource(dbRepo.allDevices) { update() }
        addSource(serviceRepo.activeDevicesForUI) { update() }
        addSource(serviceRepo.foundDeviceIds.asLiveData()) { update() }
    }

    private fun MediatorLiveData<List<DeviceUiData>>.update() {
        val dbList = dbRepo.allDevices.value ?: return
        val activeList = serviceRepo.activeDevicesForUI.value ?: emptyList()
        val foundIds = serviceRepo.foundDeviceIds.value

        value = dbList.map { knownDevice ->
            val activeDevice = activeList.find { it.deviceId == knownDevice.id }
            val isFound = foundIds.contains(knownDevice.id)

            when {
                activeDevice != null -> {
                    val main = activeDevice.mainSensorData
                    val unit = application.getString(MyHelper.getUnitsId(main.sensor))
                    knownDevice.copy(
                        isAvailable = true,
                        lastSeen = application.getString(R.string.devices_now),
                        mainValue = "${main.value} $unit",
                        allValues = activeDevice.allSensorData.map {
                            "${application.getString(it.sensor.fullNameId)}: ${it.value}"
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