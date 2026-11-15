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

import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.sensor.SensorType
import java.util.ArrayList

open class NumberedMovingAverageFilter(
    deviceName: String?,
    sensorType: SensorType,
    size: Int
) : MovingAverageFilter(deviceName, sensorType) {

    @JvmField
    protected var mSize: Int = size

    @JvmField
    protected var mIndex: Int = 0

    @JvmField
    protected var mValues: ArrayList<Number> = ArrayList()

    override val filterType: FilterType
        get() = FilterType.MOVING_AVERAGE_NUMBER

    override val filterConstant: Double
        get() = mSize.toDouble()

    @Synchronized
    override fun reset() {
        mValues.clear()
        mIndex = 0
    }

    @Synchronized
    override fun newValue(value: Number?) {
        if (value != null) {
            if (mValues.size < mSize) {
                mValues.add(value)
            } else {
                mValues[mIndex] = value
            }
            mIndex = (mIndex + 1) % mSize
        }
    }

    override val filteredValue: Number?
        @Synchronized get() {
            if (mValues.isEmpty()) {
                return null
            }

            var sum = 0.0
            var count = 0
            for (value in mValues) {
                sum += value.toDouble()
                count++
            }
            return if (count == 0) null else sum / count
        }
}
