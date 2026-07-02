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

package com.atrainingtracker.banalservice.ui.devices.devicelist

import android.os.Parcelable
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.devices.DeviceType

import kotlinx.parcelize.Parcelize

/**
 * An enum to define which subset of devices to display.
 */
enum class DeviceFilterType {
    CONNECTED,   // Devices currently visible and transmitting
    PAIRED,      // Paired devices
    ALL_KNOWN    // All devices in the database, regardless of pairing status
}

/**
 * A data class to hold all filtering specifications for the RemoteDevicesFragment.
 * Making it Parcelable allows it to be passed easily in fragment arguments.
 */
@Parcelize
data class DeviceFilterSpec(
    val filterType: DeviceFilterType,
    val protocol: Protocol,
    val deviceType: DeviceType
) : Parcelable