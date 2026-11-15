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

import android.util.Log
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication

open class MaxValueFilter(
    deviceName: String?,
    sensorType: SensorType
) : MyFilter<Number>(deviceName, sensorType) {

    @JvmField
    protected var mMaxValue: Number = 0

    @JvmField
    protected var mIsInitialized: Boolean = false

    override val filterConstant: Double
        get() = 1.0

    override val filterType: FilterType
        get() = FilterType.MAX_VALUE

    @Synchronized
    override fun reset() {
        mMaxValue = 0
        mIsInitialized = false
    }

    @Synchronized
    override fun newValue(value: Number?) {
        if (DEBUG) Log.i(TAG, "newValue: value=$value, maxValue=$mMaxValue")

        if (!TrainingApplication.isPaused()) {
            if (value != null) {
                if (!mIsInitialized) {
                    mMaxValue = value
                    mIsInitialized = true
                } else if (value.toDouble() > mMaxValue.toDouble()) {
                    mMaxValue = value
                }
            }
        }
    }

    override val filteredValue: Number
        @Synchronized get() {
            if (DEBUG) Log.i(TAG, "getFilteredValue: $mMaxValue")
            return mMaxValue
        }

    open val isInitialized: Boolean
        @Synchronized get() = mIsInitialized

    companion object {
        private val TAG: String = MaxValueFilter::class.java.canonicalName ?: "MaxValueFilter"
        private const val DEBUG: Boolean = false
    }
}
