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

package com.atrainingtracker.banalservice.filters

import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import java.util.Objects

open class FilteredSensorData<T> @JvmOverloads constructor(
    sensorType: SensorType,
    value: T?,
    stringValue: String?,
    deviceName: String?,
    @JvmField protected var mFilterType: FilterType,
    @JvmField protected var mFilterConstant: Double,
    @JvmField val deviceId: Long = -1L
) : SensorData<T>(sensorType, value, stringValue, deviceName) {

    open val filterType: FilterType
        get() = mFilterType

    open val filterConstant: Double
        get() = mFilterConstant

    open val filterData: FilterData
        get() = FilterData(deviceName, sensorType, filterType, filterConstant, deviceId)

    /**
     * Structural equality excluding volatile [SensorData.mTimestamp] so `StateFlow` conflates
     * unchanged 1-Hz snapshots cleanly, including `Double.NaN` parity (REQ-UI-2800.1).
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilteredSensorData<*>) return false
        return sensorType == other.sensorType &&
            Objects.equals(value, other.value) &&
            stringValue == other.stringValue &&
            deviceName == other.deviceName &&
            deviceId == other.deviceId &&
            mFilterType == other.mFilterType &&
            mFilterConstant.toBits() == other.mFilterConstant.toBits()
    }

    override fun hashCode(): Int {
        return Objects.hash(
            sensorType,
            value,
            stringValue,
            deviceName,
            deviceId,
            mFilterType,
            mFilterConstant.toBits()
        )
    }
}
