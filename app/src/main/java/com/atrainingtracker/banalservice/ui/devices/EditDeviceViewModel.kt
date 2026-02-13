package com.atrainingtracker.banalservice.ui.devices

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.helpers.BatteryStatusHelper
import com.atrainingtracker.banalservice.helpers.UIHelper

import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper

/**
 * ViewModel for the EditDeviceDialogFragment.
 *
 * It provides the UI with device data and handles user actions like saving.
 * It survives configuration changes and acts as the bridge to the data layer (Repository).
 *
 * @param application The application context, needed to get the repository instance.
 */
class EditDeviceViewModel(private val application: Application) : AndroidViewModel(application) {

    private val repository = DeviceDataRepository.getInstance(application)

    private val equipmentDbHelper by lazy { EquipmentDbHelper(application) }

    val wheelSizeNames: List<String> by lazy {
        application.resources.getStringArray(R.array.wheel_size_names).toList()
    }
    // The corresponding integer values in millimeters (e.g., "2105")
    val wheelSizeValues: List<String> by lazy {
        application.resources.getStringArray(R.array.wheel_size_values).toList()
    }
    /**
     * Gets the LiveData for a specific device from the repository and transforms it
     * into display-ready [DeviceEditViewData]. The UI (Fragment) will observe this
     * to get real-time updates.
     *
     * @param id The ID of the device to observe.
     * @return LiveData holding the DeviceEditViewData, or null if not found.
     */
    fun getDevice(id: Long): LiveData<DeviceEditViewData?> {
        val deviceRawData = repository.getDeviceById(id)

        // Use Transformations.map to convert the raw data from the repository
        // into the view data the fragment needs. This transformation will be
        // re-evaluated every time the source data changes.
        return deviceRawData.switchMap { rawData ->
            // If the source data is null, we return a LiveData instance that just holds null.
            if (rawData == null) {
                MutableLiveData<DeviceEditViewData?>(null)
            } else {
                // If the source data is NOT null, we return a LiveData instance
                // that holds the mapped DeviceEditViewData.
                MutableLiveData(
                    DeviceEditViewData(
                        // Direct mapping
                        id = rawData.id,
                        deviceName = rawData.deviceName,
                        manufacturer = rawData.manufacturer,
                        lastSeen = rawData.lastSeen,
                        isPaired = rawData.isPaired,

                        // get the icon id from the UI helper
                        deviceTypeIconRes = UIHelper.getIconId(rawData.deviceType, rawData.protocol),

                        // get the available equipment from the database
                        availableEquipment = equipmentDbHelper.getLinkedEquipment(id),
                        linkedEquipment = rawData.linkedEquipment,

                        // Transformed/Calculated data
                        batteryStatusIconRes = BatteryStatusHelper.getBatteryStatusImageId(rawData.batteryPercentage),

                        // Map nested data objects
                        calibrationData = mapCalibrationData(rawData),
                        powerFeatures = mapPowerFeatures(rawData)
                    )
                )
            }
        }
    }

    /**
     * Private helper to map raw data to the [CalibrationData] view object.
     */
    private fun mapCalibrationData(rawData: DeviceRawData): CalibrationData? {

        val bikePowerFeatures = BikePowerFeatures.fromFeatureFlags(rawData.powerFeaturesFlags)
        val value_in_mm = rawData.calibrationValue?.let { (it * 1000).toInt().toString() } ?: "???"

        return when (rawData.deviceType) {
            DeviceType.RUN_SPEED ->
                CalibrationData(
                    titleRes = R.string.Calibration_Factor,
                    value = value_in_mm,
                    showWheelSizeSpinner = false
                )
            DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE ->
                CalibrationData(
                    titleRes = R.string.Wheel_Circumference,
                    value = value_in_mm,
                    showWheelSizeSpinner = true,
                )
            DeviceType.BIKE_POWER ->
                CalibrationData(
                    titleRes = R.string.Wheel_Circumference,
                    value = value_in_mm,
                    showWheelSizeSpinner = bikePowerFeatures.wheelRevolutionDataSupported
                            || bikePowerFeatures.wheelDistanceDataSupported
                            || bikePowerFeatures.wheelSpeedDataSupported
                )
            else -> null
        }
    }

    /**
     * Private helper to map raw data to the [BikePowerFeatures] view object.
     */
    private fun mapPowerFeatures(rawData: DeviceRawData): BikePowerFeatures? {
        if (rawData.deviceType == DeviceType.BIKE_POWER) {
            return BikePowerFeatures.fromFeatureFlags(rawData.powerFeaturesFlags)
        }
        return null
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

}