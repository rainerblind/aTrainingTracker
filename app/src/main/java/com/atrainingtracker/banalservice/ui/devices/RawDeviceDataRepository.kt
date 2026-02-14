package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import android.content.ContentValues
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper
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

class RawDeviceDataRepository private constructor(private val application: Application) {
    companion object {
        private val TAG = RawDeviceDataRepository::class.java.simpleName
        private val DEBUG = BANALService.getDebug(true)

        // The single, volatile instance of the repository.
        // @Volatile guarantees that writes to this field are immediately visible to other threads.
        @Volatile
        private var INSTANCE: RawDeviceDataRepository? = null

        /**
         * Gets the singleton instance of the DeviceDataRepository.
         *
         * @param application The application context, needed to create the instance for the first time.
         * @return The single instance of DeviceDataRepository.
         */
        fun getInstance(application: Application): RawDeviceDataRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RawDeviceDataRepository(application).also { INSTANCE = it }
            }
        }
    }

    private val repositoryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())


    private val devicesDatabaseManager by lazy { DevicesDatabaseManager.getInstance(application) }
    private val equipmentDbHelper by lazy { EquipmentDbHelper(application) }
    private val mapper by lazy {RawDeviceDataProvider(devicesDatabaseManager, equipmentDbHelper)}


    private val _allDevices = MutableLiveData<List<RawDeviceData>>()
    val allDevices: LiveData<List<RawDeviceData>> = _allDevices

    init {
        // Automatically load all devices when the repository is first created
        repositoryScope.launch {
            loadAllDevices()
        }
    }

    fun getDeviceById(id: Long): LiveData<RawDeviceData?> {
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
            val deviceList = mutableListOf<RawDeviceData>()
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

    fun saveChanges(
        deviceId: Long,
        paired: Boolean,
        deviceName: String,
        newCalibrationFactor: Double?
    ) {
        // Launch a coroutine to perform the database operation on a background thread
        repositoryScope.launch {
            withContext(Dispatchers.IO) {
                val values = ContentValues().apply {
                    put(DevicesDbHelper.PAIRED, if (paired) 1 else 0) // Use 1/0 for boolean
                    put(DevicesDbHelper.NAME, deviceName)
                    if (newCalibrationFactor != null) {
                        put(DevicesDbHelper.CALIBRATION_FACTOR, newCalibrationFactor)
                    } else {
                        putNull(DevicesDbHelper.CALIBRATION_FACTOR)
                    }
                }

                devicesDatabaseManager.database.update(
                    DevicesDbHelper.DEVICES,
                    values,
                    "${DevicesDbHelper.C_ID} = ?",
                    arrayOf(deviceId.toString())
                )
            }

            // After the database update is complete, update the LiveData in-memory.
            // This runs on the main thread because repositoryScope is Dispatchers.Main.
            val currentList = _allDevices.value ?: return@launch
            val updatedList = currentList.map { device ->
                if (device.id == deviceId) {
                    // Create a new DeviceRawData object with the updated values
                    device.copy(
                        deviceName = deviceName,
                        isPaired = paired,
                        calibrationValue = newCalibrationFactor
                    )
                } else {
                    device
                }
            }
            _allDevices.value = updatedList
        }
    }
}