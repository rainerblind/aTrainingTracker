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

import android.app.Application
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.BikePowerSensorsHelper
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicedata.RawDeviceDataProvider
import com.atrainingtracker.util.EnumUtils
import com.atrainingtracker.trainingtracker.MyHelper
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import com.atrainingtracker.trainingtracker.repositories.EquipmentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pure domain representation of a device with battery percentage and threshold warning (REQ-UI-511.1).
 */
data class CriticalBatteryDevice(
    val name: String,
    val batteryPercentage: Int,
    val deviceId: Long
)

/**
 * Pure domain representation of a paired remote device for service coordination (REQ-TRK-066.3).
 */
data class PairedRemoteDeviceDto(
    val id: Long,
    val isPaired: Boolean,
    val deviceType: DeviceType?,
    val antDeviceNumber: Int,
    val btAddress: String?
)

/**
 * Pure domain representation of basic remote sensor identification (REQ-UI-511.1).
 */
data class SimpleSensorInfo(
    val id: Long,
    val name: String,
    val deviceType: DeviceType? = null
)

/**
 * A repository that acts as a single source of truth for workout data.
 * It abstracts the data source (database) from the ViewModels.
 */

class DeviceDataRepository @androidx.annotation.VisibleForTesting internal constructor(
    private val application: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = defaultMainDispatcher
) {
    companion object {
        private val TAG = DeviceDataRepository::class.java.simpleName
        private val DEBUG = BANALService.getDebug(true)

        private val defaultMainDispatcher: CoroutineDispatcher
            get() = try {
                val main = Dispatchers.Main
                main.isDispatchNeeded(kotlin.coroutines.EmptyCoroutineContext)
                main
            } catch (_: Throwable) {
                Dispatchers.Default
            }

        // The single, volatile instance of the repository.
        // @Volatile guarantees that writes to this field are immediately visible to other threads.
        @Volatile
        private var INSTANCE: DeviceDataRepository? = null

        /**
         * Gets the singleton instance of the DeviceDataRepository.
         *
         * @param application The application context, needed to create the instance for the first time.
         * @return The single instance of DeviceDataRepository.
         */
        fun getInstance(application: Application): DeviceDataRepository {
            return getInstance(application as Context)
        }

        @JvmStatic
        fun getInstance(context: Context): DeviceDataRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val safeContext = try {
                        context.applicationContext ?: context
                    } catch (_: Throwable) {
                        context
                    }
                    DeviceDataRepository(safeContext).also { INSTANCE = it }
                }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetForTesting(newInstance: DeviceDataRepository? = null) {
            INSTANCE?.repositoryScope?.cancel()
            INSTANCE = newInstance
        }

        private val _deviceBatteryUpdates = MutableSharedFlow<Pair<Long, Int>>(extraBufferCapacity = 64)
        val deviceBatteryUpdates: SharedFlow<Pair<Long, Int>> = _deviceBatteryUpdates.asSharedFlow()

        @JvmStatic
        fun notifyBatteryUpdated(deviceId: Long, percentage: Int) {
            _deviceBatteryUpdates.tryEmit(Pair(deviceId, percentage))
        }

        @JvmStatic
        fun isBikeSensor(deviceType: DeviceType?): Boolean = DevicesDatabaseManager.isBikeSensor(deviceType)

        @JvmStatic
        fun isRunSensor(deviceType: DeviceType?): Boolean = DevicesDatabaseManager.isRunSensor(deviceType)

        @JvmStatic
        fun isSharedSensor(deviceType: DeviceType?): Boolean = DevicesDatabaseManager.isSharedSensor(deviceType)
    }

    private val repositoryScope = CoroutineScope(mainDispatcher + SupervisorJob())

    @androidx.annotation.VisibleForTesting
    fun cancelScopeForTesting() {
        repositoryScope.cancel()
    }

    // access to the databases
    private val devicesDatabaseManager by lazy { DevicesDatabaseManager.getInstance(application) }
    private val equipmentDbHelper by lazy { EquipmentDbHelper.getInstance(application) }
    private val discoveryManager by lazy { EquipmentAndSportTypeDiscoveryManager.getInstance(application) }
    private val mapper by lazy { RawDeviceDataProvider(devicesDatabaseManager, equipmentDbHelper, discoveryManager) }

    private val _allDevices = MutableStateFlow<List<DeviceUiData>>(emptyList())
    val allDevices: StateFlow<List<DeviceUiData>> = _allDevices.asStateFlow()

    init {
        // Automatically load all devices when the repository is first created
        repositoryScope.launch {
            loadAllDevices()
        }
        // Reactively observe external equipment link modifications and invalidate cache (REQ-UI-257)
        repositoryScope.launch {
            EquipmentRepository.equipmentLinksChanged.collect { affectedDeviceId ->
                if (affectedDeviceId != null) {
                    refreshDeviceFromDb(affectedDeviceId)
                } else {
                    loadAllDevices()
                }
            }
        }
        // Reactively observe battery level updates and refresh cached device data (REQ-CON-007, ATT-3312)
        repositoryScope.launch {
            deviceBatteryUpdates.collect { (deviceId, percentage) ->
                _allDevices.value = _allDevices.value.map {
                    if (it.id == deviceId) it.copy(batteryPercentage = percentage) else it
                }
            }
        }
    }

    fun getDeviceById(id: Long): DeviceUiData? {
        return allDevices.value.find { it.id == id }
    }

    /**
     * Gets a single, non-live snapshot of a device by its ID from the currently loaded list.
     * This is useful for getting an initial value without establishing observation.
     * @param id The ID of the device to find.
     * @return The [DeviceUiData] object if found, otherwise null.
     */
    fun getDeviceSnapshotById(id: Long): DeviceUiData? {
        // Access the current value of the LiveData and find the item.
        // It's safe to do this on a background thread.
        return allDevices.value?.find { it.id == id }
    }

    fun getDeviceType(deviceId: Long): DeviceType? {
        return allDevices.value?.find { it.id == deviceId }?.deviceType
            ?: devicesDatabaseManager.getDeviceType(deviceId)
    }

    /**
     * Retrieves devices with battery levels at or below [threshold] (REQ-UI-507.5, REQ-UI-511.1).
     */
    fun getCriticalBatteryDevices(threshold: Int): List<CriticalBatteryDevice> {
        return devicesDatabaseManager.getCriticalBatteryDevices(threshold).map {
            CriticalBatteryDevice(it.name, it.batteryPercentage, it.deviceId)
        }
    }

    /**
     * Checks if a device is paired (REQ-UI-507.5).
     */
    fun isPaired(deviceId: Long): Boolean {
        return devicesDatabaseManager.isPaired(deviceId)
    }

    /**
     * Retrieves the custom or default name for a device (REQ-UI-507.5).
     */
    fun getDeviceName(deviceId: Long): String? {
        return devicesDatabaseManager.getDeviceName(deviceId)
    }

    /**
     * Checks if at least one remote sensor (ANT+ or BLE) is paired (REQ-UI-507.5).
     */
    fun hasPairedRemoteDevices(): Boolean {
        return devicesDatabaseManager.hasPairedRemoteDevices()
    }

    /**
     * Checks if at least one Bluetooth LE device is paired (REQ-UI-507.5).
     */
    fun hasPairedBluetoothDevices(): Boolean {
        return devicesDatabaseManager.hasPairedBluetoothDevices()
    }

    /**
     * Checks if at least one Heart Rate (HRM) device is paired (ATT-7532 / REQ-UI-525.2).
     */
    fun hasPairedHeartRateDevices(): Boolean {
        return devicesDatabaseManager.hasPairedHeartRateDevices()
    }

    /**
     * Retrieves summary information for all remote sensors (REQ-UI-507.5, REQ-UI-511.1).
     */
    val allRemoteSensors: List<SimpleSensorInfo>
        get() = devicesDatabaseManager.allRemoteSensors.map {
            SimpleSensorInfo(it.id, it.name, it.deviceType)
        }

    /**
     * Retrieves summary information for sensors compatible with [sportType] (REQ-UI-507.5, REQ-UI-511.1).
     */
    fun getSensorsForSportType(sportType: BSportType): List<SimpleSensorInfo> {
        return devicesDatabaseManager.getSensorsForSportType(sportType).map {
            SimpleSensorInfo(it.id, it.name, it.deviceType)
        }
    }

    /**
     * Refreshes the data for a single device by its ID. It loads the  raw data
     * from the database, translates it and updates it in the main list.
     */
    suspend fun refreshDeviceFromDb(id: Long) {
        withContext(ioDispatcher) {
            val refreshedRawDeviceData = devicesDatabaseManager.getDeviceCursor(id)?.use { cursor ->
                if (cursor.moveToFirst()) mapper.getDeviceData(cursor) else null
            }

            if (refreshedRawDeviceData != null) {
                val currentList = _allDevices.value
                val refreshedUiDeviceData = raw2UiDeviceData(refreshedRawDeviceData, application)
                val updatedList = if (currentList.any { it.id == id }) {
                    currentList.map { if (it.id == id) refreshedUiDeviceData else it }
                } else {
                    currentList + refreshedUiDeviceData
                }
                _allDevices.value = updatedList
            }
        }
    }

    /**
     * Loads or reloads all devices from the database and updates the LiveData.
     */
    suspend fun loadAllDevices() {
        withContext(ioDispatcher) {
            val uiDeviceDataList = mutableListOf<DeviceUiData>()
            devicesDatabaseManager.getCursorForAllDevices()?.use { c ->
                if (c.moveToFirst()) {
                    do {
                        try {
                            val rawData = mapper.getDeviceData(c)
                            uiDeviceDataList.add(raw2UiDeviceData(rawData, application))
                        } catch (e: Exception) {
                            Log.e("DeviceDataRepository", "Error parsing device from DB: ${e.message}")
                        }
                    } while (c.moveToNext())
                }
            }
            _allDevices.value = uiDeviceDataList
        }
    }

    /**
     * Called when a new device has been discovered and added to the database
     * by the BANALService. This refreshes the local LiveData list.
     */
    fun handleNewDeviceFound() {
        repositoryScope.launch {
            if (DEBUG) Log.d(TAG, "New device broadcast received - reloading all devices")
            loadAllDevices()
        }
    }


    /**
     * Updates a device's properties based on the final UI state.
     * This method translates the UI data into actions, like sending broadcasts to the BANALService.
     * This function should be called from a coroutine scope (e.g., viewModelScope).
     *
     * @param finalState The DeviceUiData object containing the desired final state.
     */
    suspend fun updateDevice(finalState: DeviceUiData) {
        // It's safer to run this logic on a background thread.
        withContext(ioDispatcher) {
            // We need to compare the finalState with the original state to see what changed.
            val originalState = getDeviceSnapshotById(finalState.id) ?: return@withContext

            if (originalState == finalState) {
                return@withContext
            }

            // update the device in the main device database
            val values = createContentValuesForUpdate(originalState, finalState)
            if (values.size() > 0) {
                devicesDatabaseManager.database.update(
                    DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                    values,
                    "${DevicesDatabaseManager.DevicesDbHelper.C_ID} = ?",
                    arrayOf(finalState.id.toString())
                )
            }

            // update linked equipment when necessary
            if (originalState.linkedEquipment != finalState.linkedEquipment) {
                equipmentDbHelper.setEquipmentLinks(
                    finalState.id.toInt(),
                    finalState.linkedEquipment
                )
            }

            // Update the local LiveData to immediately reflect the change in the UI
            val currentList = _allDevices.value
            val updatedList = currentList.map { if (it.id == finalState.id) finalState else it }
            _allDevices.value = updatedList

            // send broadcasts
            if (originalState.wheelCircumference != finalState.wheelCircumference) {
                sendCalibrationChangedBroadcast(
                    finalState.id,
                    finalState.wheelCircumference?.div(1000.0)
                )
            }
            if (originalState.isPaired != finalState.isPaired) {
                sendPairingChangedBroadcast(finalState.id, finalState.isPaired)
            }
        }
    }

    /**
     * Returns an instantaneous snapshot of currently paired devices (REQ-SET-083).
     */
    fun getPairedDevices(): List<DeviceUiData> {
        return _allDevices.value.filter { it.isPaired }
    }

    /**
     * Returns the count of currently paired devices (REQ-SET-083).
     */
    fun getPairedDevicesCount(): Int {
        return _allDevices.value.count { it.isPaired }
    }

    /**
     * Atomically swaps pairing state between an existing paired device and a newly requested candidate (REQ-SET-084).
     *
     * @param unpairDeviceId Device to be unpaired.
     * @param pairDeviceId Device to be paired.
     */
    suspend fun swapPairedDevice(unpairDeviceId: Long, pairDeviceId: Long) {
        withContext(ioDispatcher) {
            val unpairDevice = getDeviceSnapshotById(unpairDeviceId)
            val pairDevice = getDeviceSnapshotById(pairDeviceId)
            if (unpairDevice != null && unpairDevice.isPaired) {
                updateDevice(unpairDevice.copy(isPaired = false))
            }
            if (pairDevice != null && !pairDevice.isPaired) {
                updateDevice(pairDevice.copy(isPaired = true))
            }
        }
    }

    /**
     * Reconciles paired devices during subscription downgrade (REQ-SET-084.5).
     * Unpairs any currently paired device not contained in [keepDeviceIds],
     * preserving all other device metadata (calibration, equipment links, names).
     */
    suspend fun reconcilePairedDevices(keepDeviceIds: Set<Long>) {
        withContext(ioDispatcher) {
            val paired = getPairedDevices()
            for (device in paired) {
                if (device.id !in keepDeviceIds) {
                    updateDevice(device.copy(isPaired = false))
                }
            }
        }
    }

    /**
     * A helper function to create a ContentValues object containing only the fields that have changed.
     */
    private fun createContentValuesForUpdate(original: DeviceUiData, final: DeviceUiData): ContentValues {
        return ContentValues().apply {
            if (original.deviceName != final.deviceName) {
                put(DevicesDatabaseManager.DevicesDbHelper.NAME, final.deviceName)
            }
            if (original.isPaired != final.isPaired) {
                put(DevicesDatabaseManager.DevicesDbHelper.PAIRED, if (final.isPaired) 1 else 0)
            }

            // This handles both wheel circumference and run factor by converting them to the base calibration value
            val originalCalib = getAsCalibrationValue(original)
            val finalCalib = getAsCalibrationValue(final)
            if (originalCalib != finalCalib) {
                put(DevicesDatabaseManager.DevicesDbHelper.CALIBRATION_FACTOR, finalCalib)
            }

            if (original.baselineCircumference != final.baselineCircumference) {
                put(DevicesDatabaseManager.DevicesDbHelper.BASELINE_CIRCUMFERENCE, final.baselineCircumference ?: 0.0)
            }

        }
    }

    suspend fun resetTirePressureBaseline(deviceId: Long) {
        withContext(ioDispatcher) {
            devicesDatabaseManager.resetBaselineCircumference(deviceId)
            refreshDeviceFromDb(deviceId)
        }
    }

    /**
     * Helper to get the canonical calibration value (in meters or as a factor) from a DeviceUiData object.
     */
    private fun getAsCalibrationValue(data: DeviceUiData): Double? {
        return when (data.deviceType) {
            DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE, DeviceType.BIKE_POWER ->
                data.wheelCircumference?.div(1000.0)
            DeviceType.RUN_SPEED -> data.calibrationFactor
            else -> null
        }
    }

    fun deleteDevice(deviceId: Long) {
        repositoryScope.launch {
            sendPairingChangedBroadcast(deviceId, false)
            devicesDatabaseManager.deleteDevice(deviceId)
            loadAllDevices()
        }
    }


    fun sendPairingChangedBroadcast(deviceId: Long, paired: Boolean) {
        val intent = Intent(BANALService.PAIRING_CHANGED)
            .putExtra(BANALService.DEVICE_ID, deviceId)
            .putExtra(BANALService.PAIRED, paired)
            .setPackage(application.getPackageName())
        application.sendBroadcast(intent)
    }

    fun sendCalibrationChangedBroadcast(deviceId: Long, newCalibrationFactor: Double?) {
        if (newCalibrationFactor != null) {
            val intent = Intent(BANALService.CALIBRATION_FACTOR_CHANGED)
                .putExtra(BANALService.DEVICE_ID, deviceId)
                .putExtra(BANALService.CALIBRATION_FACTOR, newCalibrationFactor)
                .setPackage(application.getPackageName())
            application.sendBroadcast(intent)
        }
    }

    fun getActiveDeviceIdsForWorkout(workoutId: Long): List<Long> {
        return com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper.getInstance(application)
            .getDatabaseIdsOfActiveDevices(workoutId)
    }

    fun recordActiveDevice(workoutId: Long, deviceId: Long) {
        com.atrainingtracker.trainingtracker.database.ActiveDevicesDbHelper.getInstance(application)
            .addActiveDevice(workoutId, deviceId)
    }

    fun isANTDeviceInDB(deviceType: DeviceType, antDeviceNumber: Int): Boolean =
        devicesDatabaseManager.isANTDeviceInDB(deviceType, antDeviceNumber)

    fun insertNewAntDeviceIntoDB(
        deviceType: DeviceType,
        displayName: String,
        antDeviceNumber: Int,
        paired: Boolean,
        manufacturer: String?,
        batteryPercentage: Int
    ): Long = devicesDatabaseManager.insertNewAntDeviceIntoDB(
        deviceType, displayName, antDeviceNumber, paired, manufacturer, batteryPercentage
    )

    fun getDeviceId(deviceType: DeviceType, antDeviceNumber: Int): Long =
        devicesDatabaseManager.getDeviceId(deviceType, antDeviceNumber)

    fun getDeviceId(deviceType: DeviceType, btAddress: String): Long =
        devicesDatabaseManager.getDeviceId(deviceType, btAddress)

    fun setManufacturerName(deviceId: Long, manufacturer: String?) {
        devicesDatabaseManager.setManufacturerName(deviceId, manufacturer)
    }

    fun setBatteryPercentage(deviceId: Long, batteryPercentage: Int) {
        devicesDatabaseManager.setBatteryPercentage(deviceId, batteryPercentage)
    }

    fun isBluetoothDeviceInDB(deviceType: DeviceType, btAddress: String): Boolean =
        devicesDatabaseManager.isBluetoothDeviceInDB(deviceType, btAddress)

    fun insertNewBluetoothDeviceIntoDB(
        deviceType: DeviceType,
        btAddress: String,
        name: String,
        manufacturer: String?,
        batteryPercentage: Int,
        paired: Boolean
    ): Long = devicesDatabaseManager.insertNewBluetoothDeviceIntoDB(
        deviceType, btAddress, name, manufacturer, batteryPercentage, paired
    )

    fun ensureSmartphoneDeviceExists(deviceType: DeviceType, paired: Boolean, name: String) {
        devicesDatabaseManager.ensureSmartphoneDeviceExists(deviceType, name, paired)
    }

    fun getSmartphoneDeviceId(deviceType: DeviceType): Long =
        devicesDatabaseManager.getSmartphoneDeviceId(deviceType)

    fun getSpeedAndLocationGPSDeviceId(): Long =
        devicesDatabaseManager.speedAndLocationGPSDeviceId

    fun getSpeedAndLocationGoogleFusedDeviceId(): Long =
        devicesDatabaseManager.speedAndLocationGoogleFusedDeviceId

    fun getProtocol(deviceId: Long): Protocol? =
        devicesDatabaseManager.getProtocol(deviceId)

    fun getAntDeviceNumber(deviceId: Long): Int =
        devicesDatabaseManager.getAntDeviceNumber(deviceId)

    fun getBluetoothMACAddress(deviceId: Long): String? =
        devicesDatabaseManager.getBluetoothMACAddress(deviceId)

    fun getPairedRemoteDevices(protocol: Protocol): List<PairedRemoteDeviceDto> {
        val db = devicesDatabaseManager.database ?: return emptyList()
        val list = mutableListOf<PairedRemoteDeviceDto>()
        try {
            db.query(
                DevicesDatabaseManager.DevicesDbHelper.DEVICES,
                arrayOf(
                    DevicesDatabaseManager.DevicesDbHelper.C_ID,
                    DevicesDatabaseManager.DevicesDbHelper.PAIRED,
                    DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE,
                    DevicesDatabaseManager.DevicesDbHelper.ANT_DEVICE_NUMBER,
                    DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS
                ),
                "${DevicesDatabaseManager.DevicesDbHelper.PROTOCOL}=?",
                arrayOf(protocol.name),
                null, null, null
            ).use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DevicesDatabaseManager.DevicesDbHelper.C_ID)
                val pairedCol = cursor.getColumnIndexOrThrow(DevicesDatabaseManager.DevicesDbHelper.PAIRED)
                val typeCol = cursor.getColumnIndexOrThrow(DevicesDatabaseManager.DevicesDbHelper.DEVICE_TYPE)
                val antCol = cursor.getColumnIndexOrThrow(DevicesDatabaseManager.DevicesDbHelper.ANT_DEVICE_NUMBER)
                val btCol = cursor.getColumnIndexOrThrow(DevicesDatabaseManager.DevicesDbHelper.BT_ADDRESS)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val isPaired = cursor.getInt(pairedCol) > 0
                    val rawType = cursor.getString(typeCol)
                    val deviceType = EnumUtils.safeValueOfOrNull(
                        DeviceType::class.java, rawType, "DeviceDataRepository"
                    )
                    val antNum = cursor.getInt(antCol)
                    val btAddr = cursor.getString(btCol)
                    list.add(PairedRemoteDeviceDto(id, isPaired, deviceType, antNum, btAddr))
                }
            }
        } catch (e: Exception) {
            Log.e("DeviceDataRepository", "getPairedRemoteDevices failed for $protocol", e)
        }
        return list
    }
}