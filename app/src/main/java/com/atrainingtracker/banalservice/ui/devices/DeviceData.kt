package com.atrainingtracker.banalservice.ui.devices

import androidx.compose.animation.core.copy
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.BikePowerSensorsHelper
import com.atrainingtracker.banalservice.devices.DeviceType


/**
 * Simple class to hold the data from the cursor of the database.
 */
data class DeviceRawData(
    val id: Long,
    val protocol: Protocol,
    val deviceType: DeviceType,
    val lastSeen: String?,
    val batteryPercentage: Int,
    val manufacturer: String,
    val deviceName: String,
    val isPaired: Boolean,
    val isAvailable: Boolean,
    val calibrationValue: Double?,
    val linkedEquipment: List<String>,
    val availableEquipment: List<String>,
    val powerFeaturesFlags: Int?,
    val mainValue: String
)

/**
 * Common class for the UI stuff
 */
data class DeviceUiData(
    // identical to the raw data
    val id: Long,
    val protocol: Protocol,
    val deviceType: DeviceType,
    val lastSeen: String?,
    val manufacturer: String,
    val deviceName: String,
    val isAvailable: Boolean,
    val isPaired: Boolean,
    val linkedEquipment: List<String>,
    val availableEquipment: List<String>,
    val powerFeaturesFlags: Int?,
    val mainValue: String,

    // derived from the raw data
    val deviceTypeIconRes: Int,
    val batteryStatusIconRes: Int,
    val onEquipmentResId: Int,

    // Specialized properties that can be null
    val wheelCircumference: Int?,
    val calibrationFactor: Double?,
    val powerFeatures: BikePowerFeatures?
)

// --- THE NEW, SIMPLIFIED FACTORY ---
fun raw2UiDeviceData(rawData: DeviceRawData): DeviceUiData {
    // Determine the specialized values first
    val wheelCircumference = when (rawData.deviceType) {
        DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE, DeviceType.BIKE_POWER ->
            rawData.calibrationValue?.let { (it * 1000).toInt() }
        else -> null
    }

    val calibrationFactor = when (rawData.deviceType) {
        DeviceType.RUN_SPEED -> rawData.calibrationValue
        else -> null
    }

    val powerFeatures = if (rawData.deviceType == DeviceType.BIKE_POWER) {
        BikePowerFeatures.fromFeatureFlags(rawData.powerFeaturesFlags)
    } else {
        null
    }

    val onEquipmentResId = when (rawData.deviceType) {
        DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE, DeviceType.BIKE_CADENCE, DeviceType.BIKE_POWER -> R.string.devices_on_bikes_text
        DeviceType.RUN_SPEED -> R.string.devices_on_shoes_text
        else -> R.string.devices_on_equipment_text
    }

    // Now, construct the single DeviceUiData object
    return DeviceUiData(
        id = rawData.id,
        protocol = rawData.protocol,
        deviceType = rawData.deviceType,
        lastSeen = rawData.lastSeen,
        manufacturer = rawData.manufacturer,
        deviceName = rawData.deviceName,
        isPaired = rawData.isPaired,
        isAvailable = rawData.isAvailable,
        linkedEquipment = rawData.linkedEquipment,
        availableEquipment = rawData.availableEquipment,
        powerFeaturesFlags = rawData.powerFeaturesFlags,
        mainValue = rawData.mainValue,

        deviceTypeIconRes = getIconId(rawData.deviceType, rawData.protocol),
        batteryStatusIconRes = getBatteryStatusIconRes(rawData.batteryPercentage),
        onEquipmentResId = onEquipmentResId,

        wheelCircumference = wheelCircumference,
        calibrationFactor = calibrationFactor,
        powerFeatures = powerFeatures
    )
}

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
        fun fromFeatureFlags(featureFlags: Int?): BikePowerFeatures {
            if (featureFlags == null) {
                return BikePowerFeatures()
            }
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

fun getBatteryStatusIconRes(batteryStatusPercentage: Int): Int {
    return when {
        batteryStatusPercentage >= 80 -> R.drawable.stat_sys_battery_80
        batteryStatusPercentage >= 60 -> R.drawable.stat_sys_battery_60
        batteryStatusPercentage >= 40 -> R.drawable.stat_sys_battery_40
        batteryStatusPercentage >= 20 -> R.drawable.stat_sys_battery_20
        batteryStatusPercentage > 0 -> R.drawable.stat_sys_battery_10
        else -> R.drawable.stat_sys_battery_unknown
    }
}


fun getIconId(deviceType: DeviceType, protocol: Protocol): Int {
    return when (protocol) {
        Protocol.ANT_PLUS -> {
            when (deviceType) {
                DeviceType.HRM -> R.drawable.hr
                DeviceType.BIKE_SPEED -> R.drawable.bike_spd
                DeviceType.BIKE_CADENCE -> R.drawable.bike_cad
                DeviceType.BIKE_SPEED_AND_CADENCE -> R.drawable.bike_speed_and_cadence
                DeviceType.BIKE_POWER -> R.drawable.bike_pwr
                DeviceType.RUN_SPEED -> R.drawable.run_spd
                DeviceType.ENVIRONMENT -> R.drawable.temp
                else -> -protocol.iconId
            }
        }

        Protocol.BLUETOOTH_LE -> when (deviceType) {
            DeviceType.HRM -> R.drawable.bt_hr
            DeviceType.BIKE_SPEED -> R.drawable.bt_bike_spd
            DeviceType.BIKE_CADENCE -> R.drawable.bt_bike_cad
            DeviceType.BIKE_SPEED_AND_CADENCE -> R.drawable.bt_bike_speed_and_cadence
            DeviceType.BIKE_POWER -> R.drawable.bt_bike_pwr
            DeviceType.RUN_SPEED -> R.drawable.bt_run
            else -> protocol.iconId
        }
        else -> protocol.iconId
    }
}