package com.atrainingtracker.banalservice.ui.devices

import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.BikePowerSensorsHelper
import com.atrainingtracker.banalservice.devices.DeviceType


/**
 * A data class to hold all the necessary information prepared for display
 * in the EditDeviceDialogFragment.
 *
 * @param lastSeen A formatted string for the last seen timestamp.
 * @param batteryStatusIconRes The drawable resource ID for the battery icon.
 * @param manufacturer The manufacturer's name.
 * @param deviceName The user-editable name of the device.
 * @param isPaired The current pairing status.
 * @param calibrationData Data related to calibration, null if not applicable.
 * @param availableEquipment All possible equipment options to choose from.
 * @param selectedEquipmentIds The IDs of the equipment this device is assigned to.
 * @param powerFeatures Contains specific data for power meters, null otherwise.
 */
data class DeviceData(
    val id: Long,
    val protocol: Protocol,
    val deviceType: DeviceType,
    val lastSeen: String?,
    val batteryStatusIconRes: Int,
    val manufacturer: String,
    val deviceName: String,
    val isPaired: Boolean,
    val calibrationData: CalibrationData?,
    val availableEquipment: List<String>,
    val linkedEquipment: List<String>,
    val powerFeatures: BikePowerFeatures?
)


/**
 * A data class to hold data related to device calibration.
 *
 * @param titleRes The string resource for the field's hint (e.g., "Wheel Circumference" or "Calibration Factor").
 * @param value The current calibration value as a string.
 * @param showWheelSizeSpinner True if the wheel size presets spinner should be shown.
 * @param availableWheelSizes A list of preset wheel sizes (e.g., "700x25").
 * @param selectedWheelSizePosition The index of the currently selected preset.
 */
data class CalibrationData(
    val titleRes: Int,
    val value: String,
    val showWheelSizeSpinner: Boolean
)

/**
 * Represents the decoded features of a Bike Power sensor.
 * This provides a clean, readable API for the rest of the app
 * to query the capabilities of a power meter.
 */
data class BikePowerFeatures(
    // given by the device
    val pedalPowerBalanceSupported: Boolean = false,
    val wheelRevolutionDataSupported: Boolean = false,
    val crankRevolutionDataSupported: Boolean = false,
    val extremaMagnitudesSupported: Boolean = false,
    val extremaAnglesSupported: Boolean = false,
    val deadSpotAnglesSupported: Boolean = false,
    val accumulatedTorqueSupported: Boolean = false,
    val accumulatedEnergySupported: Boolean = false,
    val torqueDataSupported: Boolean = false,
    val wheelSpeedDataSupported: Boolean = false,
    val wheelDistanceDataSupported: Boolean = false,
    val pedalSmoothnessSupported: Boolean = false,
    val torqueEffectivenessSupported: Boolean = false,

    // editable by the user
    val doublePowerBalanceValues: Boolean = false,
    val invertPowerBalanceValues: Boolean = false
) {
    /**
     * A companion object to hold the logic for creating an instance
     * from the raw integer value stored in the database.
     */
    companion object {

        /**
         * Creates a [BikePowerFeatures] instance from a raw integer flag.
         * @param featureFlags The integer value from the database representing the feature bits.
         */
        fun fromFeatureFlags(featureFlags: Int): BikePowerFeatures {
            return BikePowerFeatures(
                pedalPowerBalanceSupported = BikePowerSensorsHelper.isPowerBalanceSupported(featureFlags),
                wheelRevolutionDataSupported = BikePowerSensorsHelper.isWheelRevolutionDataSupported(featureFlags),
                crankRevolutionDataSupported = BikePowerSensorsHelper.isCrankRevolutionDataSupported(featureFlags),
                extremaMagnitudesSupported = BikePowerSensorsHelper.isExtremeMagnitudesSupported(featureFlags),
                extremaAnglesSupported = BikePowerSensorsHelper.isExtremeAnglesSupported(featureFlags),
                deadSpotAnglesSupported = BikePowerSensorsHelper.isDeadSpotAnglesSupported(featureFlags),
                accumulatedTorqueSupported = BikePowerSensorsHelper.isAccumulatedTorqueSupported(featureFlags),
                accumulatedEnergySupported = BikePowerSensorsHelper.isAccumulatedEnergySupported(featureFlags),
                torqueDataSupported = BikePowerSensorsHelper.isTorqueDataSupported(featureFlags),
                wheelSpeedDataSupported = BikePowerSensorsHelper.isWheelSpeedDataSupported(featureFlags),
                wheelDistanceDataSupported = BikePowerSensorsHelper.isWheelDistanceDataSupported(featureFlags),
                pedalSmoothnessSupported = BikePowerSensorsHelper.isPedalSmoothnessSupported(featureFlags),
                torqueEffectivenessSupported = BikePowerSensorsHelper.isTorqueEffectivenessSupported(featureFlags),
                doublePowerBalanceValues = BikePowerSensorsHelper.doublePowerBalanceValues(featureFlags),
                invertPowerBalanceValues = BikePowerSensorsHelper.invertPowerBalanceValues(featureFlags)
            )
        }
    }
}

data class PowerFeatureDisplay(
    val name: String,
    val isDeemphasized: Boolean
)