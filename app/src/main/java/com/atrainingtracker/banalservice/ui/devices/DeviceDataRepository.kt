package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.atrainingtracker.R
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

    val wheelSizeNames: List<String> by lazy {
        application.resources.getStringArray(R.array.wheel_size_names).toList()
    }
    // The corresponding integer values in millimeters (e.g., "2105")
    val wheelSizeValues: List<String> by lazy {
        application.resources.getStringArray(R.array.wheel_size_values).toList()
    }

    private val devicesDatabaseManager by lazy { DevicesDatabaseManager.getInstance(application) }
    private val equipmentDbHelper by lazy { EquipmentDbHelper(application) }
    private val mapper by lazy {DeviceDataProvider(devicesDatabaseManager, equipmentDbHelper)}


    private val _allDevices = MutableLiveData<List<DeviceData>>()
    private val allDevices: LiveData<List<DeviceData>> = _allDevices

    init {
        // Automatically load all devices when the repository is first created
        repositoryScope.launch {
            loadAllDevices()
        }
    }

    fun getDeviceById(id: Long): LiveData<DeviceData?> {
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
            val deviceList = mutableListOf<DeviceData>()
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

    /**
     * Finds the index of a given circumference value in our master list.
     * @param circumference The circumference in mm (e.g., 2105).
     * @return The index in the list, or 0 if not found.
     */
    fun getWheelSizePosition(circumference: Int?): Int {
        if (circumference == null) return 0
        val index = wheelSizeValues.indexOf(circumference.toString())
        return if (index != -1) index else 0
    }

    /**
     * Gets the circumference value in mm for a given spinner position.
     * @param position The selected index from the spinner.
     * @return The circumference as an Int, or null if invalid.
     */
    fun getWheelCircumferenceForPosition(position: Int): Int? {
        return wheelSizeValues.getOrNull(position)?.toIntOrNull()
    }

    /**
     * Converts a BikePowerFeatures object into a human-readable list of display-ready items,
     * including a flag to de-emphasize less common features. This keeps the conversion     * logic centralized in the repository.
     *
     * @param features The BikePowerFeatures object from the device data.
     * @return A list of [PowerFeatureDisplay] objects representing the supported features.
     */
    fun getPowerFeaturesForDisplay(features: BikePowerFeatures?): List<PowerFeatureDisplay> {
        if (features == null) {
            return emptyList()
        }

        val displayList = mutableListOf<PowerFeatureDisplay>()

        // --- Always add the default mandatory feature, not deemphasized ---
        displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__instantaneous_power),false))

        if (features.torqueDataSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__torque_data),false))
        }

        if (features.wheelRevolutionDataSupported) {
            displayList.add(PowerFeatureDisplay( application.getString(R.string.bike_power__wheel_revolution_data),false))
        }

        if (features.wheelSpeedDataSupported && features.wheelDistanceDataSupported) {
            displayList.add(PowerFeatureDisplay( application.getString(R.string.bike_power__wheel_speed_and_distance_data),false))
        } else if (features.wheelSpeedDataSupported) {
            displayList.add(PowerFeatureDisplay( application.getString(R.string.bike_power__wheel_speed_data),false))
        } else if (features.wheelDistanceDataSupported) {
            displayList.add(PowerFeatureDisplay( application.getString(R.string.bike_power__wheel_distance_data),false))
        }

        if (features.crankRevolutionDataSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__crank_revolution_data),false))
        }

        if (features.pedalPowerBalanceSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__pedal_power_balance),false))
        }

        if (features.extremaMagnitudesSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__extreme_magnitudes),true))
        }

        if (features.extremaAnglesSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__extreme_angles),true))
        }

        if (features.deadSpotAnglesSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__top_and_bottom_dead_sport_angles),true))
        }

        if (features.pedalSmoothnessSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__pedal_smoothness),false))
        }

        if (features.torqueEffectivenessSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__torque_effectiveness),false))
        }

        if (features.accumulatedTorqueSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__accumulated_torque),true))
        }

        if (features.accumulatedEnergySupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__accumulated_energy),true))
        }

        return displayList
    }
}