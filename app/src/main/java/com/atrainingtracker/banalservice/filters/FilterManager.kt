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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.sensor.MyAccumulatorSensor
import com.atrainingtracker.banalservice.sensor.MySensor
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.atrainingtracker.banalservice.sensor.ProxySensor
import com.atrainingtracker.util.LifecycleUtils
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

open class FilterManager(
    context: Context,
    deviceManager: DeviceManager?,
    mySensorManager: MySensorManager?
) {
    @JvmField
    protected val mRequiredButNotAvailableFilters: ConcurrentHashMap<String, FilterData> = ConcurrentHashMap()

    @JvmField
    protected val mAllFilterData: ConcurrentHashMap<String, FilterData> = ConcurrentHashMap()

    @JvmField
    protected val myFilterMap: ConcurrentHashMap<String, MyFilter<*>> = ConcurrentHashMap()

    @JvmField
    protected val mMapFilter2Sensor: ConcurrentHashMap<MyFilter<*>, MySensor<*>> = ConcurrentHashMap()

    @JvmField
    protected val mAccumulatorFilters: MutableSet<MyFilter<*>> = ConcurrentHashMap.newKeySet()

    @JvmField
    protected var mContext: Context = context

    @JvmField
    protected var mDeviceManager: DeviceManager? = deviceManager

    @JvmField
    protected var mMySensorManager: MySensorManager? = mySensorManager

    @JvmField
    protected val mSensorsChangedFilter: IntentFilter = IntentFilter(BANALService.SENSORS_CHANGED)

    @JvmField
    protected val mSensorsChangedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            rebindExistingFilters()
            // try to create the filters that were not yet ready
            for (filterData in mRequiredButNotAvailableFilters.values) {
                createFilter(filterData)
            }
        }
    }

    @JvmField
    protected val mResetAccumulatorsFilter: IntentFilter = IntentFilter(BANALService.RESET_ACCUMULATORS_INTENT)

    @JvmField
    protected val mResetAccumulatorsReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (DEBUG) Log.i(TAG, "mResetAccumulatorsReceiver.onReceive(): resetting all filters")
            resetAllFilters()
        }
    }

    init {
        if (DEBUG) Log.i(TAG, "FilterManager()")

        ContextCompat.registerReceiver(
            mContext,
            mSensorsChangedReceiver,
            mSensorsChangedFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            mContext,
            mResetAccumulatorsReceiver,
            mResetAccumulatorsFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    open fun createFilter(filterData: FilterData) {
        if (DEBUG) Log.i(TAG, "createFilter: ${filterData.hashKey}")

        // When it is not possible to filter, we set the filter type to instantaneous
        // This is necessary to avoid NullPointerExceptions later on ...
        if (!filterData.sensorType.filteringPossible) {
            filterData.filterType = FilterType.INSTANTANEOUS
        }

        val hashKey = filterData.hashKey
        mAllFilterData[hashKey] = filterData

        // if the requested filter is already there, check if its sensor changed or disconnected (REQ-TRK-055.2, REQ-FIL-038.3)
        val existingFilter = myFilterMap[hashKey]
        if (existingFilter != null) {
            if (DEBUG) Log.i(TAG, "filter already exists, syncing backing sensor binding")
            syncFilterBinding(filterData, existingFilter)
            return
        }

        // first of all, we try to get the sensor
        val mySensor = resolveSensor(filterData)

        // Defer unavailable or inactive device-pinned sensors; never defer retained ProxySensor (REQ-TRK-055.1, REQ-FIL-038.3)
        if (mySensor == null || (mySensor !is ProxySensor<*> && !mySensor.isActivated)) {
            if (DEBUG) Log.i(TAG, "sensor not (yet) available or inactive")
            mRequiredButNotAvailableFilters[hashKey] = filterData // save this request for later on..
            return
        }

        // now, that we have valid sensor, we are ready to create the filter
        val myFilter: MyFilter<*> = instantiateFilter(filterData)
        syncFilterDeviceIdentity(filterData, myFilter, mySensor)

        val existing = myFilterMap.putIfAbsent(hashKey, myFilter)
        if (existing == null) {
            if (isAccumulatorSensor(mySensor)) {
                mAccumulatorFilters.add(myFilter)
            }
            mMapFilter2Sensor[myFilter] = mySensor
            @Suppress("UNCHECKED_CAST")
            (mySensor as MySensor<Any?>).addSensorListener(myFilter as MySensor.SensorListener<Any?>)
            mRequiredButNotAvailableFilters.remove(hashKey)
            if (DEBUG) Log.i(TAG, "filter $hashKey created")
        } else {
            syncFilterBinding(filterData, existing)
        }
    }

    protected open fun instantiateFilter(filterData: FilterData): MyFilter<*> = when (filterData.filterType) {
        FilterType.INSTANTANEOUS -> InstantaneousFilter<Any>(filterData.deviceName, filterData.sensorType)
        FilterType.AVERAGE -> AverageFilter(filterData.deviceName, filterData.sensorType)
        FilterType.EXPONENTIAL_SMOOTHING -> ExponentialSmoothingFilter(
            filterData.deviceName,
            filterData.sensorType,
            filterData.filterConstant
        )
        FilterType.MOVING_AVERAGE_NUMBER -> NumberedMovingAverageFilter(
            filterData.deviceName,
            filterData.sensorType,
            filterData.filterConstant.toInt()
        )
        FilterType.MOVING_AVERAGE_TIME -> TimedMovingAverageFilter(
            filterData.deviceName,
            filterData.sensorType,
            filterData.filterConstant.toLong()
        )
        FilterType.MAX_VALUE -> {
            if (DEBUG) Log.i(TAG, "case MAX_VALUE")
            MaxValueFilter(filterData.deviceName, filterData.sensorType)
        }
    }

    open fun resolveSensor(filterData: FilterData?): MySensor<*>? {
        if (filterData == null) return null
        if (filterData.deviceId > 0L) {
            val byId = mDeviceManager?.getMyDeviceById(filterData.deviceId)
            if (byId != null) {
                return byId.getSensor(filterData.sensorType)
            }
            if (filterData.deviceName != null) {
                return mDeviceManager?.getMyDeviceByName(filterData.deviceName)?.getSensor(filterData.sensorType)
            }
            return null
        }
        return if (filterData.deviceName == null) {
            mMySensorManager?.getSensor(filterData.sensorType)
        } else {
            mDeviceManager?.getMyDeviceByName(filterData.deviceName)?.getSensor(filterData.sensorType)
        }
    }

    private fun syncFilterDeviceIdentity(filterData: FilterData, filter: MyFilter<*>, sensor: MySensor<*>?) {
        val resolvedName = if (filterData.deviceId > 0L) {
            sensor?.device?.name ?: filterData.deviceName
        } else {
            filterData.deviceName
        }
        filter.updateDeviceIdentity(filterData.deviceId, resolvedName)
    }

    @Synchronized
    open fun rebindFilter(myFilter: MyFilter<*>?, oldSensor: MySensor<*>?, newSensor: MySensor<*>?) {
        if (myFilter == null) return
        if (DEBUG) Log.i(TAG, "rebindFilter: oldSensor=$oldSensor, newSensor=$newSensor")
        if (isAccumulatorSensor(oldSensor) || isAccumulatorSensor(newSensor)) {
            mAccumulatorFilters.add(myFilter)
        }
        if (oldSensor != null) {
            @Suppress("UNCHECKED_CAST")
            (oldSensor as MySensor<Any?>).removeSensorListener(myFilter as MySensor.SensorListener<Any?>)
        }
        if (newSensor != null) {
            mMapFilter2Sensor[myFilter] = newSensor
            // addSensorListener delivers the current value once when activated (REQ-UI-2800.3)
            @Suppress("UNCHECKED_CAST")
            (newSensor as MySensor<Any?>).addSensorListener(myFilter as MySensor.SensorListener<Any?>)
        } else {
            mMapFilter2Sensor.remove(myFilter)
        }
    }

    private fun isAccumulatorSensor(sensor: MySensor<*>?): Boolean {
        var current = sensor
        while (current is ProxySensor<*>) {
            if (current.isAccumulatorBacked) return true
            current = current.sourceSensor
        }
        return current is MyAccumulatorSensor<*>
    }

    private fun unwrapEffectiveSource(sensor: MySensor<*>?): MySensor<*>? {
        var current = sensor
        while (current is ProxySensor<*>) {
            current = current.sourceSensor
        }
        return current
    }

    private fun clearLiveFilterOnDisconnect(filter: MyFilter<*>, boundSensor: MySensor<*>?) {
        if (mAccumulatorFilters.contains(filter) || isAccumulatorSensor(boundSensor)) {
            mAccumulatorFilters.add(filter)
            return
        }
        when (filter) {
            is InstantaneousFilter<*> -> {
                @Suppress("UNCHECKED_CAST")
                (filter as InstantaneousFilter<Any?>).newValue(null)
            }
            is MovingAverageFilter -> filter.reset()
        }
    }

    private fun syncFilterBinding(filterData: FilterData, filter: MyFilter<*>) {
        val currentSensor = resolveSensor(filterData)
        syncFilterDeviceIdentity(filterData, filter, currentSensor)
        val boundSensor = mMapFilter2Sensor[filter]
        if (currentSensor is ProxySensor<*>) {
            if (boundSensor != currentSensor) {
                rebindFilter(filter, boundSensor, currentSensor)
            }
            if (isAccumulatorSensor(currentSensor)) {
                mAccumulatorFilters.add(filter)
            }
            val effectiveSource = unwrapEffectiveSource(currentSensor)
            if (effectiveSource == null || !effectiveSource.isActivated) {
                clearLiveFilterOnDisconnect(filter, currentSensor)
            }
        } else if (currentSensor == null || !currentSensor.isActivated) {
            if (boundSensor != null) {
                if (isAccumulatorSensor(boundSensor)) {
                    mAccumulatorFilters.add(filter)
                }
                rebindFilter(filter, boundSensor, null)
            }
            clearLiveFilterOnDisconnect(filter, boundSensor)
        } else if (boundSensor != currentSensor) {
            rebindFilter(filter, boundSensor, currentSensor)
        }
    }

    open fun rebindExistingFilters() {
        if (DEBUG) Log.i(TAG, "rebindExistingFilters()")
        for (filterData in mAllFilterData.values) {
            val filter = myFilterMap[filterData.hashKey]
            if (filter != null) {
                syncFilterBinding(filterData, filter)
            }
        }
    }

    open fun shutDown() {
        for ((myFilter, sensor) in mMapFilter2Sensor) {
            @Suppress("UNCHECKED_CAST")
            (sensor as MySensor<Any?>).removeSensorListener(myFilter as MySensor.SensorListener<Any?>)
        }
        myFilterMap.clear()
        mMapFilter2Sensor.clear()
        mAccumulatorFilters.clear()
        mRequiredButNotAvailableFilters.clear()
        mAllFilterData.clear()

        LifecycleUtils.safeUnregisterReceiver(mContext, mSensorsChangedReceiver, TAG)
        LifecycleUtils.safeUnregisterReceiver(mContext, mResetAccumulatorsReceiver, TAG)
    }

    /**
     * Resets all registered filters upon session start/reset (REQ-FIL-013, REQ-TRK-049.4).
     * Re-seeds accumulator filters with their baseline zero values so they display
     * 0:00:00 rather than --:--:-- in TrackingMode.READY (ATT-5080, ATT-5191).
     * Unwraps [ProxySensor] decorators and queries [MyAccumulatorSensor.getZeroValue]
     * to eliminate non-deterministic broadcast receiver order dependencies.
     */
    @Synchronized
    open fun resetAllFilters() {
        if (DEBUG) Log.i(TAG, "resetAllFilters()")
        for (myFilter in myFilterMap.values) {
            myFilter.reset()
            var sensor: MySensor<*>? = mMapFilter2Sensor[myFilter]
            while (sensor is ProxySensor<*>) {
                sensor = sensor.sourceSensor
            }
            if (sensor is MyAccumulatorSensor<*>) {
                var resetVal: Any? = sensor.zeroValue
                if (resetVal == null) {
                    resetVal = sensor.value
                }
                if (resetVal != null) {
                    @Suppress("UNCHECKED_CAST")
                    (myFilter as MyFilter<Any?>).newValue(resetVal)
                }
            }
        }
    }

    open fun getFilteredSensorData(filterData: FilterData): FilteredSensorData<*>? {
        val key = filterData.hashKey
        val filter = myFilterMap[key]
        return filter?.filteredSensorData
    }

    open fun getAllFilteredSensorData(): List<FilteredSensorData<*>> {
        if (DEBUG) Log.i(TAG, "getAllFilteredSensorData()")
        val result = LinkedList<FilteredSensorData<*>>()
        for (myFilter in myFilterMap.values) {
            result.add(myFilter.filteredSensorData)
        }
        return result
    }

    /**
     * Shifts buffered values in the specified filter uniformly by delta (REQ-FIL-012.6, ATT-5373).
     *
     * @param filterData the filter specification identifier.
     * @param delta the vertical offset to add to every sample.
     */
    open fun shiftFilter(filterData: FilterData?, delta: Double) {
        if (filterData == null) return
        val filter = myFilterMap[filterData.hashKey]
        filter?.shiftValues(delta)
    }

    /**
     * Clears and reseeds the specified filter with a new baseline value (REQ-FIL-012.5, ATT-5373).
     *
     * @param filterData the filter specification identifier.
     * @param value the new authoritative baseline sample.
     */
    open fun reseedFilter(filterData: FilterData?, value: Number?) {
        if (filterData == null) return
        val filter = myFilterMap[filterData.hashKey]
        filter?.reseed(value)
    }

    companion object {
        private val TAG: String = FilterType::class.java.simpleName
        @JvmStatic
        private val DEBUG: Boolean = BANALService.getDebug(false)
    }
}
