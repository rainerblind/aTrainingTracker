package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.atrainingtracker.banalservice.devices.DeviceType

/**
 * ViewModel for the EditDeviceDialogFragment.
 *
 * It provides the UI with device data and handles user actions like saving.
 * It survives configuration changes and acts as the bridge to the data layer (Repository).
 *
 * @param application The application context, needed to get the repository instance.
 */
class EditDeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceDataRepository.getInstance(application)

    val wheelSizeNames: List<String>
        get() = repository.wheelSizeNames

    fun getPowerFeaturesForDisplay(features: BikePowerFeatures?): List<PowerFeatureDisplay> {
        return repository.getPowerFeaturesForDisplay(features)
    }

    /**
     * Gets the LiveData for a specific device from the repository.
     * The UI (Fragment) will observe this to get real-time updates.
     *
     * @param id The ID of the device to observe.
     * @return LiveData holding the DeviceData, or null if not found.
     */
    fun getDevice(id: Long): LiveData<DeviceData?> {
        return repository.getDeviceById(id)
    }

    /**
     * Creates the appropriate configuration object for the dialog UI based on device type.
     * This logic lives in the ViewModel to keep the Fragment clean.
     *
     * @param deviceType The type of the device.
     * @return A [DeviceDialogConfig] object that defines the UI structure.
     */
    fun getDialogConfig(deviceType: DeviceType): DeviceDialogConfig {
        return when (deviceType) {
            DeviceType.BIKE_POWER -> DeviceDialogConfig.BikePower
            DeviceType.BIKE_SPEED -> DeviceDialogConfig.BikeSpeed
            DeviceType.BIKE_SPEED_AND_CADENCE -> DeviceDialogConfig.BikeSpeedAndCadence
            DeviceType.BIKE_CADENCE -> DeviceDialogConfig.BikeCadence
            DeviceType.RUN_SPEED -> DeviceDialogConfig.RunSpeed
            else -> DeviceDialogConfig.None
        }
    }

    /**
     * Saves the updated device data by delegating the call to the repository.
     * This runs within the ViewModel's own coroutine scope.
     *
     * @param unboundData The data object containing the user's edits from the dialog.
     */
    // fun saveDevice(unboundData: UnboundDeviceData) {
    //    viewModelScope.launch {
    //        repository.saveDeviceData(unboundData)
    //    }
    //}
}