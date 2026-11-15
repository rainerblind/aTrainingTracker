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
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.sensor.SensorType
import java.util.LinkedList
import kotlin.math.abs

open class TimedMovingAverageFilter(
    deviceName: String?,
    sensorType: SensorType,
    seconds: Long
) : MovingAverageFilter(deviceName, sensorType) {

    @JvmField
    protected var mSeconds: Long = seconds

    @JvmField
    protected var mTimestampedValues: LinkedList<TimestampedValue> = LinkedList()

    override val filterType: FilterType
        get() = FilterType.MOVING_AVERAGE_TIME

    override val filterConstant: Double
        get() = mSeconds.toDouble()

    @Synchronized
    override fun reset() {
        mTimestampedValues.clear()
    }

    @Synchronized
    override fun shiftValues(delta: Double) {
        if (abs(delta) < MIN_SHIFT_DELTA) {
            return
        }
        for (tv in mTimestampedValues) {
            val v = tv.value
            if (v != null) {
                tv.value = v.toDouble() + delta
            }
        }
    }

    @Synchronized
    override fun reseed(value: Number?) {
        mTimestampedValues.clear()
        if (value != null) {
            val now = System.currentTimeMillis()
            mTimestampedValues.add(TimestampedValue(now, value))
        }
    }

    @Synchronized
    override fun newValue(value: Number?) {
        if (value != null) {
            val currentTimeMillis = System.currentTimeMillis()
            mTimestampedValues.add(TimestampedValue(currentTimeMillis, value))
            if (DEBUG) {
                Log.i(TAG, "added a new value: timestamp=$currentTimeMillis, value=$value")
            }
            trimValues()
        }
    }

    override val filteredValue: Number?
        @Synchronized get() {
            trimValues()

            if (mTimestampedValues.isEmpty()) {
                return null
            }

            var sum = 0.0
            for (timestampedValue in mTimestampedValues) {
                val v = timestampedValue.value
                sum += v?.toDouble() ?: 0.0
            }
            return sum / mTimestampedValues.size
        }

    protected open fun trimValues() {
        val threshold = System.currentTimeMillis() - MILLIS_PER_SECOND * mSeconds
        if (DEBUG) Log.i(TAG, "trimValues: mSeconds=$mSeconds, threshold=$threshold")
        while (mTimestampedValues.peek() != null && mTimestampedValues.peek()!!.timestamp < threshold) {
            if (DEBUG) {
                Log.i(TAG, "removing an element with timestamp=${mTimestampedValues.peek()!!.timestamp}")
            }
            mTimestampedValues.poll()
        }
    }

    open class TimestampedValue(
        @JvmField var timestamp: Long,
        @JvmField var value: Number?
    )

    companion object {
        private const val MIN_SHIFT_DELTA = 1e-4
        private const val MILLIS_PER_SECOND = 1000L

        @JvmStatic
        private val DEBUG: Boolean = BANALService.getDebug(false)
        private val TAG: String = TimedMovingAverageFilter::class.java.name
    }
}
