package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import android.content.Intent
import androidx.activity.result.launch
import androidx.compose.animation.core.copy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceFilterSpec
import com.atrainingtracker.banalservice.ui.devices.devicelist.DeviceFilterType
import com.atrainingtracker.trainingtracker.ui.util.Event


data class EditDeviceNavigationEvent(val deviceId: Long, val deviceType: DeviceType)

/**
 * ViewModel for the EditDeviceDialogFragment as well as the DeviceList.
 *
 * It provides the UI with device data and handles user actions like saving.
 * It survives configuration changes and acts as the bridge to the data layer (Repository).
 *
 * @param application The application context, needed to get the repository instance.
 */
class DevicesViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository = DeviceDataRepository.Companion.getInstance(application)

    // the single source of truth: the raw device list from the repository.
    private val allDevices: LiveData<List<DeviceUiData>> = repository.allDevices

    // The single source of truth for the UI. This holds the CURRENT state of the device being edited.
    private val _uiState = MutableLiveData<DeviceUiData?>()
    val uiState: LiveData<DeviceUiData?> = _uiState

    /**
     * Loads the initial device data from the repository and populates the initial UI state.
     * This should be called once when the edit dialog is created.
     */
    fun loadInitialDeviceData(deviceId: Long) {
        // No launch block is needed for this synchronous, main-safe call.
        _uiState.value = repository.getDeviceSnapshotById(deviceId)
    }

    private val _navigateToEditDevice = MutableLiveData<Event<EditDeviceNavigationEvent>>()
    val navigateToEditDevice: LiveData<Event<EditDeviceNavigationEvent>> = _navigateToEditDevice

    fun onDeviceSelected(deviceId: Long) {
        val deviceType = repository.getDeviceType(deviceId) // You'll need to create this simple method
        if (deviceType != null) {
            _navigateToEditDevice.postValue(Event(EditDeviceNavigationEvent(deviceId, deviceType)))
        }
    }

    /**
     * This is the key public method.
     * Fragments will call this to get a LiveData stream tailored to their specific needs.
     */
    fun getFilteredDevices(spec: DeviceFilterSpec): LiveData<List<DeviceUiData>> {
        // We apply another .map transformation to our already-transformed list.
        // This returns a new LiveData stream that will re-filter whenever allListDevices changes.
        return allDevices.map { devices ->
            // First, apply the main filter based on the filter type.
            val primaryFiltered = when (spec.filterType) {
                DeviceFilterType.PAIRED -> devices.filter { it.isPaired }
                DeviceFilterType.AVAILABLE -> devices.filter { it.isAvailable }
                DeviceFilterType.ALL_KNOWN -> devices // No primary filter, return the whole list
            }

            // Then, apply secondary filters for protocol and device type to the result.
            primaryFiltered.filter { device ->
                val protocolMatch = spec.protocol == Protocol.ALL || device.protocol == spec.protocol
                val deviceTypeMatch = spec.deviceType == DeviceType.ALL || device.deviceType == spec.deviceType
                protocolMatch && deviceTypeMatch
            }
        }
    }



    //--- dealing with wheel sizes
    val wheelSizeNames: List<String> by lazy {
        application.resources.getStringArray(R.array.wheel_size_names).toList()
    }
    // The corresponding integer values in millimeters (e.g., "2105")
    val wheelSizeValues: List<String> by lazy {
        application.resources.getStringArray(R.array.wheel_size_values).toList()
    }

    /**
     * Finds the index of a given circumference value in our master list.
     * @param circumference The circumference in mm (e.g., 2105).
     * @return The index in the list, or 0 if not found.
     */
    @Deprecated("Probably, we don't need this")
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
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__wheel_revolution_data),false))
        }

        if (features.wheelSpeedDataSupported && features.wheelDistanceDataSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__wheel_speed_and_distance_data),false))
        } else if (features.wheelSpeedDataSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__wheel_speed_data),false))
        } else if (features.wheelDistanceDataSupported) {
            displayList.add(PowerFeatureDisplay(application.getString(R.string.bike_power__wheel_distance_data),false))
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



    // --- Event Handlers from the UI ---

    fun onDeviceNameChanged(newName: String) {
        updateState { currentState ->
            // Create a new state object based on the old one, with the name changed.
            currentState.copyCommon(deviceName = newName)
        }
    }

    fun onPairedChanged(isPaired: Boolean) {
        updateState { currentState ->
            currentState.copyCommon(isPaired = isPaired)
        }
    }

    fun onEquipmentChanged(newEquipment: List<String>) {
        updateState { currentState ->
            currentState.copyCommon(linkedEquipment = newEquipment)
        }
    }


    fun onCalibrationFactorChanged(calibrationValue: Double) {
        // TODO: add validation before updating the state?
        updateState { currentState ->
            when (currentState) {
                is RunDeviceUiData -> currentState.copy(calibrationFactor = calibrationValue)
                else -> currentState // No change for other types
            }
        }
    }

    fun onWheelCircumferenceChanged(wheelCircumference: Int) {
        updateState { currentState ->
            when (currentState) {
                is SimpleBikeDeviceUiData -> currentState.copy(wheelCircumference = wheelCircumference)
                is BikePowerDeviceUiData -> currentState.copy(wheelCircumference = wheelCircumference)
                else -> currentState
            }
        }

    }

    fun onDoublePowerBalanceValuesChanged(isDouble: Boolean) {
        updateState { currentState ->
            if (currentState is BikePowerDeviceUiData) {
                currentState.copy(
                    powerFeatures = currentState.powerFeatures.copy(doublePowerBalanceValues = isDouble)
                )
            } else {
                currentState
            }
        }
    }

    fun onInvertPowerBalanceValuesChanged(isInverted: Boolean) {
        updateState { currentState ->
            if (currentState is BikePowerDeviceUiData) {
                currentState.copy(
                    powerFeatures = currentState.powerFeatures.copy(invertPowerBalanceValues = isInverted)
                )
            } else {
                currentState
            }
        }
    }

    /**
     * A generic helper function to safely update the state.
     * It ensures we always work with a non-null state and posts the result.
     */
    private fun updateState(updateAction: (currentState: DeviceUiData) -> DeviceUiData) {
        val currentState = _uiState.value
        if (currentState != null) {
            val newState = updateAction(currentState)

            // Only update the LiveData if the new state is actually different from the old one.
            // This avoids/breaks an infinite loop at its source.
            if (newState != currentState) {
                _uiState.value = newState
            }
        }
    }


    /**
     * Takes the final state from _uiState and passes it to the repository to be saved permanently.
     */

    fun saveChanges(deviceId: Long) {
        val finalState = _uiState.value ?: return

        // You would now map the finalState and specializedData to a format the repository can save.
        // TODO
        // viewModelScope.launch {
        //     repository.updateDevice(finalState, deviceId)
        // }
    }





    // TODO: move to the repository, where this stuff is stored...
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