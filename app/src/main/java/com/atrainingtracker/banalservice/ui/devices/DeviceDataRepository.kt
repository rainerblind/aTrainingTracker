package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
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
    private val mapper by lazy {DeviceDataProvider(devicesDatabaseManager, equipmentDbHelper)}


    private val _allDevices = MutableLiveData<List<DeviceRawData>>()
    private val allDevices: LiveData<List<DeviceRawData>> = _allDevices

    init {
        // Automatically load all devices when the repository is first created
        repositoryScope.launch {
            loadAllDevices()
        }
    }

    fun getDeviceById(id: Long): LiveData<DeviceRawData?> {
        return allDevices.map { list ->
            list.find {it.id == id}
        }
    }


    /**
     * Refreshes the data for a single device by its ID. It loads the data
     * from the database and updates it in the main list.
     */
    suspend fun refreshDevice(id: Long) {
        withContext(Dispatchers.IO) {
            val refreshedDevice = devicesDatabaseManager.getDeviceCursor(id)?.use { cursor ->
                if (cursor.moveToFirst()) mapper.getDeviceData(cursor) else null
            }

            if (refreshedDevice != null) {
                val currentList = _allDevices.value ?: emptyList()
                val updatedList = currentList.map { if (it.id == id) refreshedDevice else it }
                _allDevices.postValue(updatedList)
            }
        }
    }

    /**
     * Loads or reloads all devices from the database and updates the LiveData.
     */
    suspend fun loadAllDevices() {
        withContext(Dispatchers.IO) {
            val deviceList = mutableListOf<DeviceRawData>()
            devicesDatabaseManager.getCursorForAllDevices()?.use { c ->
                if (c.moveToFirst()) {
                    do {
                        val data = mapper.getDeviceData(c)
                        deviceList.add(data)
                    } while (c.moveToNext())
                }
            }
            _allDevices.postValue(deviceList)
        }
    }
}