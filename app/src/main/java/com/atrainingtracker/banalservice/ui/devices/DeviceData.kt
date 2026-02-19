package com.atrainingtracker.banalservice.ui.devices

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

// Data class holding all the common data for the UI
data class CommonDeviceUiData(
    val id: Long,
    val protocol: Protocol,
    val deviceType: DeviceType,
    val deviceTypeIconRes: Int,
    val lastSeen: String?,
    val batteryStatusIconRes: Int,
    val manufacturer: String,
    val deviceName: String,
    val isAvailable: Boolean,
    val isPaired: Boolean,
    val onEquipmentResId: Int,
    val linkedEquipment: List<String>,
    val availableEquipment: List<String>,
    val mainValue: String
)


/**
 * Base class for the device data for the UI
 */
sealed interface DeviceUiData {
    val common: CommonDeviceUiData

    // Delegate properties for easy access.
    val id: Long get() = common.id
    val protocol: Protocol get() = common.protocol
    val deviceType: DeviceType get() = common.deviceType
    val deviceTypeIconRes: Int get() = common.deviceTypeIconRes
    val lastSeen: String? get() = common.lastSeen
    val batteryStatusIconRes: Int get() = common.batteryStatusIconRes
    val manufacturer: String get() = common.manufacturer
    val deviceName: String get() = common.deviceName
    val isAvailable: Boolean get() = common.isAvailable
    val isPaired: Boolean get() = common.isPaired
    val onEquipmentResId: Int get() = common.onEquipmentResId
    val linkedEquipment: List<String> get() = common.linkedEquipment
    val availableEquipment: List<String> get() = common.availableEquipment
    val mainValue: String get() = common.mainValue
}

interface UiDeviceDataFactory<T: DeviceUiData> {
    fun fromRawData(rawData: DeviceRawData): T
}

// Helper to create the common data part from raw data.
fun createCommonUiData(rawData: DeviceRawData, onEquipmentResId: Int = R.string.devices_on_equipment_text): CommonDeviceUiData {
    return CommonDeviceUiData(
        id = rawData.id,
        protocol = rawData.protocol,
        deviceType = rawData.deviceType,
        deviceTypeIconRes = getIconId(rawData.deviceType, rawData.protocol),
        lastSeen = rawData.lastSeen,
        batteryStatusIconRes = getBatteryStatusIconRes(rawData.batteryPercentage),
        manufacturer = rawData.manufacturer,
        deviceName = rawData.deviceName,
        isPaired = rawData.isPaired,
        isAvailable = rawData.isAvailable,
        onEquipmentResId = onEquipmentResId,
        linkedEquipment = rawData.linkedEquipment,
        availableEquipment = rawData.availableEquipment,
        mainValue = rawData.mainValue
    )
}

fun raw2UiDeviceData(rawData: DeviceRawData): DeviceUiData {
    return when (rawData.deviceType) {
        DeviceType.RUN_SPEED -> RunDeviceUiData.fromRawData(rawData)
        DeviceType.BIKE_SPEED, DeviceType.BIKE_SPEED_AND_CADENCE -> BikeDeviceUiData.fromRawData(rawData)
        DeviceType.BIKE_POWER -> BikePowerDeviceUiData.fromRawData(rawData)
        DeviceType.BIKE_CADENCE -> GeneralDeviceUiData.fromRawData(rawData, R.string.devices_on_bikes_text)
        else -> GeneralDeviceUiData.fromRawData(rawData)
    }
}

data class GeneralDeviceUiData (
    override val common: CommonDeviceUiData
) : DeviceUiData {
    companion object: UiDeviceDataFactory<GeneralDeviceUiData> {
        override fun fromRawData(rawData: DeviceRawData): GeneralDeviceUiData {
            return GeneralDeviceUiData(createCommonUiData(rawData, R.string.devices_on_equipment_text)
            )
        }

        fun fromRawData(rawData: DeviceRawData, onEquipmentResId: Int): GeneralDeviceUiData {
            return GeneralDeviceUiData(createCommonUiData(rawData, onEquipmentResId)
            )
        }

    }
}

data class RunDeviceUiData(
    override val common: CommonDeviceUiData,
    val calibrationFactor: Double
) : DeviceUiData {
    companion object : UiDeviceDataFactory<RunDeviceUiData> {
        override fun fromRawData(rawData: DeviceRawData): RunDeviceUiData {
            return RunDeviceUiData(
                common = createCommonUiData(rawData, R.string.devices_on_shoes_text),
                calibrationFactor = rawData.calibrationValue!!
            )
        }
    }
}

data class BikeDeviceUiData(
    override val common: CommonDeviceUiData,
    val wheelCircumference: Int
) : DeviceUiData {
    companion object : UiDeviceDataFactory<BikeDeviceUiData> {
        override fun fromRawData(rawData: DeviceRawData): BikeDeviceUiData {
            return BikeDeviceUiData(
                common = createCommonUiData(rawData, R.string.devices_on_bikes_text),
                wheelCircumference = (rawData.calibrationValue!! * 1000).toInt()
            )
        }
    }
}

data class BikePowerDeviceUiData(
    override val common: CommonDeviceUiData,
    val wheelCircumference: Int,
    val powerFeatures: BikePowerFeatures
) : DeviceUiData {
    companion object : UiDeviceDataFactory<BikePowerDeviceUiData> {
        override fun fromRawData(rawData: DeviceRawData): BikePowerDeviceUiData {
            return BikePowerDeviceUiData(
                common = createCommonUiData(rawData, R.string.devices_on_bikes_text),
                wheelCircumference = (rawData.calibrationValue!! * 1000).toInt(),
                powerFeatures = BikePowerFeatures.fromFeatureFlags(rawData.powerFeaturesFlags)
            )
        }
    }
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