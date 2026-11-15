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

// simple filter for y[k+1] = \alpha m[k] + (1-\alpha) y[k]
open class ExponentialSmoothingFilter(
    deviceName: String?,
    sensorType: SensorType,
    alpha: Double
) : MyFilter<Number>(deviceName, sensorType) {

    @JvmField
    protected var mFilteredValue: Double = 0.0

    @JvmField
    protected val mAlpha: Double

    @JvmField
    protected var mIsInitialized: Boolean = false

    init {
        require(!alpha.isNaN() && !alpha.isInfinite() && alpha > 0.0 && alpha <= 1.0) {
            "Filter constant alpha must be in range (0.0, 1.0], was: $alpha"
        }
        mAlpha = alpha
    }

    override val filterConstant: Double
        get() = mAlpha

    override val filterType: FilterType
        get() = FilterType.EXPONENTIAL_SMOOTHING

    override val filteredValue: Number
        @Synchronized get() = mFilteredValue

    @Synchronized
    override fun reset() {
        mFilteredValue = 0.0
        mIsInitialized = false
    }

    @Synchronized
    override fun newValue(value: Number?) {
        if (value != null) {
            val d = value.toDouble()
            if (!mIsInitialized) {
                mFilteredValue = d
                mIsInitialized = true
            } else {
                mFilteredValue = mAlpha * d + (1 - mAlpha) * mFilteredValue
            }
        }
    }

    open val isInitialized: Boolean
        @Synchronized get() = mIsInitialized
}
