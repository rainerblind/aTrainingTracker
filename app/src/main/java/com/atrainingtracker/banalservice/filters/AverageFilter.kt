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

import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication

open class AverageFilter(
    deviceName: String?,
    sensorType: SensorType
) : MyFilter<Number>(deviceName, sensorType) {

    @JvmField
    protected var mAccumulatedValue: Double = 0.0

    @JvmField
    protected var mNumberOfSamples: Long = 0L

    override val filterType: FilterType
        get() = FilterType.AVERAGE

    override val filterConstant: Double
        get() = 1.0

    @Synchronized
    override fun reset() {
        mAccumulatedValue = 0.0
        mNumberOfSamples = 0L
    }

    @Synchronized
    override fun newValue(value: Number?) {
        if (value != null && !TrainingApplication.isPaused()) {
            mAccumulatedValue += value.toDouble()
            mNumberOfSamples++
        }
    }

    override val filteredValue: Number?
        @Synchronized get() {
            return if (mNumberOfSamples == 0L) {
                null
            } else {
                mAccumulatedValue / mNumberOfSamples
            }
        }
}
