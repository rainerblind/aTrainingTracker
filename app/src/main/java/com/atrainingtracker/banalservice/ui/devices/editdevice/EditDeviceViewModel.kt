package com.atrainingtracker.banalservice.ui.devices.editdevice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.ui.devices.BikePowerFeatures
import com.atrainingtracker.banalservice.ui.devices.DeviceDataRepository
import com.atrainingtracker.banalservice.ui.devices.DeviceUiData
import com.atrainingtracker.banalservice.ui.devices.PowerFeatureDisplay
import kotlinx.coroutines.launch

/**
 * ViewModel for the EditDeviceDialogFragment
 *
 * It provides the UI with device data and handles user actions like saving.
 * It survives configuration changes and acts as the bridge to the data layer (Repository).
 *
 * @param application The application context, needed to get the repository instance.
 */
class EditDeviceViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository = DeviceDataRepository.Companion.getInstance(application)

    // The single source of truth for the UI. This holds the CURRENT state of the device being edited.
    private val _uiState = MutableLiveData<DeviceUiData?>()
    val uiState: LiveData<DeviceUiData?> = _uiState

    lateinit var deviceData : LiveData<DeviceUiData?>

    /**
     * Loads the initial device data from the repository and populates the initial UI state.
     * This should be called once when the edit dialog is created.
     */
    fun loadInitialDeviceData(deviceId: Long) {
        // No launch block is needed for this synchronous, main-safe call.
        _uiState.value = repository.getDeviceSnapshotById(deviceId)
        deviceData = repository.getDeviceById(deviceId)
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
     * @return A list of [com.atrainingtracker.banalservice.ui.devices.PowerFeatureDisplay] objects representing the supported features.
     */
    fun getPowerFeaturesForDisplay(features: BikePowerFeatures?): List<PowerFeatureDisplay> {
        if (features == null) {
            return emptyList()
        }

        val displayList = mutableListOf<PowerFeatureDisplay>()

        // --- Always add the default mandatory feature, not deemphasized ---
        displayList.add(
            PowerFeatureDisplay(
                application.getString(R.string.bike_power__instantaneous_power),
                false
            )
        )

        if (features.torqueDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__torque_data),
                    false
                )
            )
        }

        if (features.wheelRevolutionDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__wheel_revolution_data),
                    false
                )
            )
        }

        if (features.wheelSpeedDataSupported && features.wheelDistanceDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__wheel_speed_and_distance_data),
                    false
                )
            )
        } else if (features.wheelSpeedDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__wheel_speed_data),
                    false
                )
            )
        } else if (features.wheelDistanceDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__wheel_distance_data),
                    false
                )
            )
        }

        if (features.crankRevolutionDataSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__crank_revolution_data),
                    false
                )
            )
        }

        if (features.pedalPowerBalanceSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__pedal_power_balance),
                    false
                )
            )
        }

        if (features.extremaMagnitudesSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__extreme_magnitudes),
                    true
                )
            )
        }

        if (features.extremaAnglesSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__extreme_angles),
                    true
                )
            )
        }

        if (features.deadSpotAnglesSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__top_and_bottom_dead_sport_angles),
                    true
                )
            )
        }

        if (features.pedalSmoothnessSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__pedal_smoothness),
                    false
                )
            )
        }

        if (features.torqueEffectivenessSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__torque_effectiveness),
                    false
                )
            )
        }

        if (features.accumulatedTorqueSupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__accumulated_torque),
                    true
                )
            )
        }

        if (features.accumulatedEnergySupported) {
            displayList.add(
                PowerFeatureDisplay(
                    application.getString(R.string.bike_power__accumulated_energy),
                    true
                )
            )
        }

        return displayList
    }

    // --- Event Handlers from the UI ---


    // called from edit device dialog fragment
    // -> update repository only onSave
    fun onDeviceNameChanged(newName: String) {
        updateState { it.copy(deviceName = newName) }
    }

    fun onEquipmentChanged(newEquipment: List<String>) {
        updateState { it.copy(linkedEquipment = newEquipment) }
    }


    fun onCalibrationFactorChanged(calibrationValue: Double) {
        // TODO: add validation before updating the state?
        updateState { it.copy(calibrationFactor = calibrationValue) }
    }

    fun onWheelCircumferenceChanged(wheelCircumference: Int) {
        updateState { it.copy(wheelCircumference = wheelCircumference) }
    }

    fun onDoublePowerBalanceValuesChanged(isDouble: Boolean) {
        updateState { it.copy(powerFeatures = it.powerFeatures!!.copy(doublePowerBalanceValues = isDouble)) }
    }

    fun onInvertPowerBalanceValuesChanged(isInverted: Boolean) {
        updateState { it.copy(powerFeatures = it.powerFeatures!!.copy(invertPowerBalanceValues = isInverted)) }
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

    fun saveChanges() {
        val finalState = _uiState.value ?: return

        viewModelScope.launch {
            repository.updateDevice(finalState)
        }
    }

}