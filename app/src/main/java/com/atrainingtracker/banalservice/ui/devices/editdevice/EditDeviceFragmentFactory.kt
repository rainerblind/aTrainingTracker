/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.ui.devices.editdevice

import androidx.fragment.app.DialogFragment
import com.atrainingtracker.banalservice.devices.DeviceType

/**
 * A factory object responsible for creating the correct instance of an
 * edit device dialog based on the device's type.
 */
class EditDeviceFragmentFactory private constructor() {

    companion object  {
        /**
         * Creates and returns the appropriate DialogFragment for editing a device.
         * @param deviceId The ID of the device to edit.
         * @param deviceType The type of the device.
         * @return A DialogFragment instance ready to be shown.
         */
        @JvmStatic
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
}