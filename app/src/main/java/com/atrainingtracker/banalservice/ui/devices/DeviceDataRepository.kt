package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import android.content.ContentValues
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * A repository that acts as a single source of truth for workout data.
 * It abstracts the data source (database) from the ViewModels.
 */

class DeviceDataRepository private constructor(private val application: Application) {
    companion object {
        private val TAG = DeviceDataRepository::class.java.simpleName
        private val DEBUG = BANALService.getDebug(true)

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
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DeviceDataRepository(application).also { INSTANCE = it }
            }
        }
    }

    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())


    private val devicesDatabaseManager by lazy { DevicesDatabaseManager.getInstance(application) }
    private val equipmentDbHelper by lazy { EquipmentDbHelper(application) }
    private val mapper by lazy {RawDeviceDataProvider(devicesDatabaseManager, equipmentDbHelper)}


    private val _allDevices = MutableLiveData<List<DeviceUiData>>()
    val allDevices: LiveData<List<DeviceUiData>> = _allDevices

    init {
        // Automatically load all devices when the repository is first created
        repositoryScope.launch {
            loadAllDevices()
        }
    }

    fun getDeviceById(id: Long): LiveData<DeviceUiData?> {
        return allDevices.map { list ->
            list.find {it.id == id}
        }
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
    }

    // TODO: get connection to BANALService and update mainValue and isAvailable...


    /**
     * Refreshes the data for a single device by its ID. It loads the  raw data
     * from the database, translates it and updates it in the main list.
     */
    suspend fun refreshDeviceFromDb(id: Long) {
        withContext(Dispatchers.IO) {
            val refreshedRawDeviceData = devicesDatabaseManager.getDeviceCursor(id)?.use { cursor ->
                if (cursor.moveToFirst()) mapper.getDeviceData(cursor) else null
            }

            if (refreshedRawDeviceData != null) {
                val currentList = _allDevices.value ?: emptyList()
                val refreshedUiDeviceData = raw2UiDeviceData(refreshedRawDeviceData)
                val updatedList = currentList.map { if (it.id == id) refreshedUiDeviceData else it }
                _allDevices.postValue(updatedList)
            }
        }
    }

    /**
     * Loads or reloads all devices from the database and updates the LiveData.
     */
    suspend fun loadAllDevices() {
        withContext(Dispatchers.IO) {
            val uiDeviceDataList = mutableListOf<DeviceUiData>()
            devicesDatabaseManager.getCursorForAllDevices()?.use { c ->
                if (c.moveToFirst()) {
                    do {
                        val rawData = mapper.getDeviceData(c)
                        uiDeviceDataList.add(raw2UiDeviceData(rawData))
                    } while (c.moveToNext())
                }
            }
            _allDevices.postValue(uiDeviceDataList)
        }
    }

    fun saveChanges(
        deviceId: Long,
    ) {
        // Launch a coroutine to perform the database operation on a background thread
        repositoryScope.launch {
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    // TODO: fill values...
                }

                devicesDatabaseManager.database.update(
                    DevicesDbHelper.DEVICES,
                    values,
                    "${DevicesDbHelper.C_ID} = ?",
                    arrayOf(deviceId.toString())
                )
            }
        }
    }
}