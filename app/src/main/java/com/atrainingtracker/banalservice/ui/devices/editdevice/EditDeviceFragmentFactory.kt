package com.atrainingtracker.banalservice.ui.devices.editdevice

import androidx.fragment.app.DialogFragment
import com.atrainingtracker.banalservice.devices.DeviceType

/**
 * A factory object responsible for creating the correct instance of an
 * edit device dialog based on the device's type.
 */
object EditDeviceFragmentFactory {

    /**
     * Creates and returns the appropriate DialogFragment for editing a device.
     * @param deviceId The ID of the device to edit.
     * @param deviceType The type of the device.
     * @return A DialogFragment instance ready to be shown.
     */
    fun create(deviceId: Long, deviceType: DeviceType): DialogFragment {
        return when (deviceType) {

            DeviceType.RUN_SPEED -> EditRunDeviceFragment.newInstance(deviceId)
            DeviceType.BIKE_SPEED,
            DeviceType.BIKE_SPEED_AND_CADENCE -> EditSimpleBikeDeviceFragment.newInstance(deviceId)
            DeviceType.BIKE_POWER -> EditBikePowerDeviceFragment.newInstance(deviceId)
            DeviceType.BIKE_CADENCE -> EditGeneralDeviceFragment.newInstance(deviceId)

            else -> EditGeneralDeviceFragment.newInstance(deviceId)
        }
    }
}