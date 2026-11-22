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

import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.SensorType

abstract class MyFilter<T>(
    @JvmField protected var mDeviceName: String?,
    @JvmField protected var mSensorType: SensorType
) : MySensor.SensorListener<T> {

    @JvmField
    protected var mDeviceId: Long = -1L

    fun updateDeviceIdentity(deviceId: Long, deviceName: String?) {
        mDeviceId = deviceId
        mDeviceName = deviceName
    }

    abstract val filteredValue: T?

    abstract val filterType: FilterType

    abstract val filterConstant: Double

    open val filteredSensorData: FilteredSensorData<T>
        get() {
            val fv = filteredValue
            val stringValue = mSensorType.myFormatter.format(fv)
            return FilteredSensorData(mSensorType, fv, stringValue, mDeviceName, filterType, filterConstant, mDeviceId)
        }

    /**
     * Resets filter internal state, sample buffers, and accumulators (REQ-FIL-013).
     * Default implementation is a no-op for stateless filters.
     */
    open fun reset() {
        // Default no-op for stateless filters (e.g. InstantaneousFilter)
    }

    /**
     * Shifts buffered values uniformly by delta (REQ-FIL-012.6, ATT-5373).
     * Default implementation is a no-op for filters that do not support translation.
     *
     * @param delta the offset to add to existing samples.
     */
    open fun shiftValues(delta: Double) {
        // Default no-op
    }

    /**
     * Reseeds filter state with a single baseline value (REQ-FIL-012.5, ATT-5373).
     * Default implementation calls reset() and newValue(value).
     *
     * @param value the new baseline sample.
     */
    @Suppress("UNCHECKED_CAST")
    open fun reseed(value: Number?) {
        reset()
        if (value != null) {
            newValue(value as T)
        }
    }
}
