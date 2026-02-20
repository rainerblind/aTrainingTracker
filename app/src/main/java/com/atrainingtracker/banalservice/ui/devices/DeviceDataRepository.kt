package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.animation.core.copy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper
import com.atrainingtracker.banalservice.devices.BikePowerSensorsHelper
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
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

    // access to the databases
    private val devicesDatabaseManager by lazy { DevicesDatabaseManager.getInstance(application) }
    private val equipmentDbHelper by lazy { EquipmentDbHelper(application) }
    private val mapper by lazy {RawDeviceDataProvider(devicesDatabaseManager, equipmentDbHelper)}

    // access to the BANALService
    private var banalServiceComm: BANALService.BANALServiceComm? = null
    private var isBoundToBanalService = false


    private val _allDevices = MutableLiveData<List<DeviceUiData>>()
    val allDevices: LiveData<List<DeviceUiData>> = _allDevices

    init {
        // Automatically load all devices when the repository is first created
        repositoryScope.launch {
            loadAllDevices()
            bindToService()
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

    // Connection to BANALService and update mainValue and isAvailable...
    private val banalServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // This is called when the connection to the service has been established.
            // We get the BANALServiceComm binder instance.
            banalServiceComm = service as? BANALService.BANALServiceComm
            isBoundToBanalService = banalServiceComm != null
            if (isBoundToBanalService) {
                // Once connected, start observing the service for data
                startObservingServiceData()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // This is called when the service connection is lost unexpectedly
            isBoundToBanalService = false
            banalServiceComm = null
        }
    }

    // Methods to bind and unbind from the ViewModel
    fun bindToService() {
        if (!isBoundToBanalService) {
            val intent = Intent(application, BANALService::class.java)
            // BIND_AUTO_CREATE ensures the service is created if not already running
            application.bindService(intent, banalServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindFromService() {
        if (isBoundToBanalService) {
            application.unbindService(banalServiceConnection)
            isBoundToBanalService = false
            banalServiceComm = null
        }
    }

    // Observing the BANALService: This function will be called once the service is connected.
    private fun startObservingServiceData() {
        repositoryScope.launch {
            // Create a flow that emits every 1 second
            flow {
                while (true) {
                    emit(Unit)     // Emit a signal to fetch data
                    delay(1000) // Poll every second
                }
            }.collect {
                // Get the current list of devices from the LiveData in memory.
                val currentDevices = allDevices.value ?: emptyList()
                if (currentDevices.isEmpty()) {
                    // If the initial load from DB hasn't finished, wait for the next poll.
                    return@collect
                }

                val activeDevices = banalServiceComm?.activeRemoteDevices ?: emptyList()

                // --- Update allDevices based on the values we get from the BANALService
                val mergedList = currentDevices.map { device ->
                    val activeDevice = activeDevices.find { it.deviceId == device.id }

                    if (activeDevice != null) {
                        // Device is currently active and seen by the service
                        device.copy(
                            isAvailable = true, // Mark as available
                            lastSeen = application.getString(R.string.devices_now),
                            mainValue = activeDevice.mainSensorStringValue,

                        )
                    } else {
                        // Device is not active, reset its live data
                        device.copy(
                            isAvailable = false,
                            // lastSeen = device.lastSeen,  Note that we do not update lastSeen here.  When a device was seen and then is removed, we keep 'now'.  Otherwise, we would have to check the database again...
                            mainValue = null
                        )
                    }
                }

                // Post the final, merged list to the LiveData that the ViewModel observes
                _allDevices.postValue(mergedList)
            }
        }
    }


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


    /**
     * Updates a device's properties based on the final UI state.
     * This method translates the UI data into actions, like sending broadcasts to the BANALService.
     * This function should be called from a coroutine scope (e.g., viewModelScope).
     *
     * @param finalState The DeviceUiData object containing the desired final state.
     */
    suspend fun updateDevice(finalState: DeviceUiData) {
        // It's safer to run this logic on a background thread.
        withContext(Dispatchers.IO) {
            // We need to compare the finalState with the original state to see what changed.
            val originalState = getDeviceSnapshotById(finalState.id) ?: return@withContext

            if (originalState == finalState) {
                return@withContext
            }

            // update the device in the main device database
            val values = createContentValuesForUpdate(originalState, finalState)
            if (values.size() > 0) {
                devicesDatabaseManager.database.update(
                    DevicesDbHelper.DEVICES,
                    values,
                    "${DevicesDbHelper.C_ID} = ?",
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

            // Update power feature flags when necessary
            if (originalState.powerFeatures != finalState.powerFeatures) {
                var powerFeatureFlags = originalState.powerFeaturesFlags
                powerFeatureFlags = if (finalState.powerFeatures!!.doublePowerBalanceValues) {
                    powerFeatureFlags?.let { BikePowerSensorsHelper.addDoublePowerBalanceValues(it) }
                } else {
                    powerFeatureFlags?.let { BikePowerSensorsHelper.removeDoublePowerBalanceValues(it) }
                }
                if (finalState.powerFeatures!!.invertPowerBalanceValues) {
                    powerFeatureFlags = powerFeatureFlags?.let { BikePowerSensorsHelper.addInvertPowerBalanceValues(it) }
                }
                else {
                    powerFeatureFlags = powerFeatureFlags?.let {
                        BikePowerSensorsHelper.removeInvertPowerBalanceValues(it)
                    }
                }
                if (powerFeatureFlags != null) {
                    devicesDatabaseManager.putBikePowerSensorFlags(finalState.id, powerFeatureFlags)
                }
            }

            // Update the local LiveData to immediately reflect the change in the UI
            val currentList = _allDevices.value ?: emptyList()
            val updatedList = currentList.map { if (it.id == finalState.id) finalState else it }
            _allDevices.postValue(updatedList)

            // send broadcasts
            if (originalState.wheelCircumference != finalState.wheelCircumference) {
                sendCalibrationChangedBroadcast(finalState.id,
                    finalState.wheelCircumference?.div(1000.0)
                )
            }
            if (originalState.isPaired != finalState.isPaired) {
                sendPairingChangedBroadcast(finalState.id, finalState.isPaired)
            }
        }
    }

    /**
     * A helper function to create a ContentValues object containing only the fields that have changed.
     */
    private fun createContentValuesForUpdate(original: DeviceUiData, final: DeviceUiData): ContentValues {
        return ContentValues().apply {
            if (original.deviceName != final.deviceName) {
                put(DevicesDbHelper.NAME, final.deviceName)
            }
            if (original.isPaired != final.isPaired) {
                put(DevicesDbHelper.PAIRED, if (final.isPaired) 1 else 0)
            }

            // This handles both wheel circumference and run factor by converting them to the base calibration value
            val originalCalib = getAsCalibrationValue(original)
            val finalCalib = getAsCalibrationValue(final)
            if (originalCalib != finalCalib) {
                put(DevicesDbHelper.CALIBRATION_FACTOR, finalCalib)
            }

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
}