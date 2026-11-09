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

package com.atrainingtracker.banalservice.filters;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.devices.DeviceManager;
import com.atrainingtracker.banalservice.devices.MyDevice;
import com.atrainingtracker.banalservice.sensor.MyAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.ProxySensor;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.core.content.ContextCompat;
import com.atrainingtracker.util.LifecycleUtils;

public class FilterManager {
    private static final String TAG = FilterType.class.getSimpleName();
    private static final boolean DEBUG = BANALService.getDebug(false);

    protected final Map<String, FilterData> mRequiredButNotAvailableFilters = new ConcurrentHashMap<>();
    protected final Map<String, FilterData> mAllFilterData = new ConcurrentHashMap<>();

    protected final Map<String, MyFilter> myFilterMap = new ConcurrentHashMap<>();
    protected final Map<MyFilter, MySensor> mMapFilter2Sensor = new ConcurrentHashMap<>();

    protected Context mContext;
    protected DeviceManager mDeviceManager;
    protected MySensorManager mMySensorManager;

    protected IntentFilter mSensorsChangedFilter = new IntentFilter(BANALService.SENSORS_CHANGED);
    protected BroadcastReceiver mSensorsChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            rebindExistingFilters();
            // try to create the filters that were not yet ready
            for (FilterData filterData : mRequiredButNotAvailableFilters.values()) {
                createFilter(filterData);
            }
        }
    };

    protected IntentFilter mResetAccumulatorsFilter = new IntentFilter(BANALService.RESET_ACCUMULATORS_INTENT);
    protected BroadcastReceiver mResetAccumulatorsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) Log.i(TAG, "mResetAccumulatorsReceiver.onReceive(): resetting all filters");
            resetAllFilters();
        }
    };

    public FilterManager(Context context, DeviceManager deviceManager, MySensorManager mySensorManager) {
        if (DEBUG) Log.i(TAG, "FilterManager()");

        mDeviceManager = deviceManager;
        mMySensorManager = mySensorManager;
        mContext = context;

        ContextCompat.registerReceiver(mContext, mSensorsChangedReceiver, mSensorsChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        ContextCompat.registerReceiver(mContext, mResetAccumulatorsReceiver, mResetAccumulatorsFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void createFilter(FilterData filterData) {
        if (DEBUG) Log.i(TAG, "createFilter: " + filterData.getHashKey());

        // When it is not possible to filter, we set the filter type to instantaneous
        // This is necessary to avoid NullPointerExceptions later on ...
        if (!filterData.sensorType.filteringPossible) {
            filterData.filterType = FilterType.INSTANTANEOUS;
        }

        String hashKey = filterData.getHashKey();
        mAllFilterData.put(hashKey, filterData);

        // if the requested filter is already there, check if its sensor changed and rebind (REQ-TRK-055.2)
        MyFilter existingFilter = myFilterMap.get(hashKey);
        if (existingFilter != null) {
            if (DEBUG) Log.i(TAG, "filter already exists, checking backing sensor re-binding");
            MySensor currentSensor = resolveSensor(filterData);
            if (currentSensor != null) {
                MySensor boundSensor = mMapFilter2Sensor.get(existingFilter);
                if (boundSensor != currentSensor) {
                    rebindFilter(existingFilter, boundSensor, currentSensor);
                }
            }
            return;
        }

        // first of all, we try to get the sensor
        MySensor mySensor = resolveSensor(filterData);

        if (mySensor == null) {  // sensor not (yet) available
            if (DEBUG) Log.i(TAG, "sensor not (yet) available");
            mRequiredButNotAvailableFilters.put(hashKey, filterData);   // save this request for later on..
            return;
        }


        // now, that we have valid sensor, we are ready to create the filter
        MyFilter myFilter;

        switch (filterData.filterType) {
            case INSTANTANEOUS:
                myFilter = new InstantaneousFilter(filterData.deviceName, filterData.sensorType);
                break;

            case AVERAGE:
                myFilter = new AverageFilter(filterData.deviceName, filterData.sensorType);
                break;

            case EXPONENTIAL_SMOOTHING:
                myFilter = new ExponentialSmoothingFilter(filterData.deviceName, filterData.sensorType, filterData.filterConstant);
                break;

            case MOVING_AVERAGE_NUMBER:
                myFilter = new NumberedMovingAverageFilter(filterData.deviceName, filterData.sensorType, (int) filterData.filterConstant);
                break;

            case MOVING_AVERAGE_TIME:
                myFilter = new TimedMovingAverageFilter(filterData.deviceName, filterData.sensorType, (long) filterData.filterConstant);
                break;

            case MAX_VALUE:
                if (DEBUG) Log.i(TAG, "case MAX_VALUE");
                myFilter = new MaxValueFilter(filterData.deviceName, filterData.sensorType);
                break;

            default:  // should never ever happen!
                myFilter = new InstantaneousFilter(filterData.deviceName, filterData.sensorType);
                break;
        }

        MyFilter existing = myFilterMap.putIfAbsent(hashKey, myFilter);
        if (existing == null) {
            mMapFilter2Sensor.put(myFilter, mySensor);
            mySensor.addSensorListener(myFilter);
            mRequiredButNotAvailableFilters.remove(hashKey);
            if (DEBUG) Log.i(TAG, "filter " + hashKey + " created");
        } else {
            MySensor boundSensor = mMapFilter2Sensor.get(existing);
            if (boundSensor != mySensor) {
                rebindFilter(existing, boundSensor, mySensor);
            }
        }
    }

    public MySensor resolveSensor(FilterData filterData) {
        if (filterData == null) return null;
        if (filterData.deviceName == null) {
            return mMySensorManager != null ? mMySensorManager.getSensor(filterData.sensorType) : null;
        } else {
            if (mDeviceManager != null) {
                MyDevice myDevice = mDeviceManager.getMyDeviceByName(filterData.deviceName);
                if (myDevice != null) {
                    return myDevice.getSensor(filterData.sensorType);
                }
            }
            return null;
        }
    }

    public synchronized void rebindFilter(MyFilter myFilter, MySensor oldSensor, MySensor newSensor) {
        if (myFilter == null) return;
        if (DEBUG) Log.i(TAG, "rebindFilter: oldSensor=" + oldSensor + ", newSensor=" + newSensor);
        if (oldSensor != null) {
            oldSensor.removeSensorListener(myFilter);
        }
        if (newSensor != null) {
            mMapFilter2Sensor.put(myFilter, newSensor);
            newSensor.addSensorListener(myFilter);
            Object currentValue = newSensor.getValue();
            if (currentValue != null) {
                myFilter.newValue(currentValue);
            }
        } else {
            mMapFilter2Sensor.remove(myFilter);
        }
    }

    public void rebindExistingFilters() {
        if (DEBUG) Log.i(TAG, "rebindExistingFilters()");
        for (FilterData filterData : mAllFilterData.values()) {
            String hashKey = filterData.getHashKey();
            MyFilter filter = myFilterMap.get(hashKey);
            if (filter != null) {
                MySensor currentSensor = resolveSensor(filterData);
                MySensor boundSensor = mMapFilter2Sensor.get(filter);
                if (currentSensor != null && boundSensor != currentSensor) {
                    rebindFilter(filter, boundSensor, currentSensor);
                }
            }
        }
    }

    public void shutDown() {
        for (MyFilter myFilter : myFilterMap.values()) {
            MySensor sensor = mMapFilter2Sensor.get(myFilter);
            if (sensor != null) {
                sensor.removeSensorListener(myFilter);
            }
        }
        myFilterMap.clear();
        mMapFilter2Sensor.clear();
        mRequiredButNotAvailableFilters.clear();
        mAllFilterData.clear();

        LifecycleUtils.safeUnregisterReceiver(mContext, mSensorsChangedReceiver, TAG);
        LifecycleUtils.safeUnregisterReceiver(mContext, mResetAccumulatorsReceiver, TAG);
    }

    /**
     * Resets all registered filters upon session start/reset (REQ-FIL-013, REQ-TRK-049.4).
     * Re-seeds accumulator filters with their baseline zero values so they display
     * 0:00:00 rather than --:--:-- in TrackingMode.READY (ATT-5080, ATT-5191).
     * Unwraps {@link ProxySensor} decorators and queries {@link MyAccumulatorSensor#getZeroValue()}
     * to eliminate non-deterministic broadcast receiver order dependencies.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public synchronized void resetAllFilters() {
        if (DEBUG) Log.i(TAG, "resetAllFilters()");
        for (MyFilter myFilter : myFilterMap.values()) {
            if (myFilter != null) {
                myFilter.reset();
                MySensor sensor = mMapFilter2Sensor.get(myFilter);
                while (sensor instanceof ProxySensor<?>) {
                    sensor = ((ProxySensor<?>) sensor).getSourceSensor();
                }
                if (sensor instanceof MyAccumulatorSensor<?>) {
                    Object resetVal = ((MyAccumulatorSensor<?>) sensor).getZeroValue();
                    if (resetVal == null) {
                        resetVal = sensor.getValue();
                    }
                    if (resetVal != null) {
                        myFilter.newValue(resetVal);
                    }
                }
            }
        }
    }


    public FilteredSensorData getFilteredSensorData(FilterData filterData) {
        String key = filterData.getHashKey();
        MyFilter filter = myFilterMap.get(key);
        return filter != null ? filter.getFilteredSensorData() : null;
    }


    public List<FilteredSensorData> getAllFilteredSensorData() {
        if (DEBUG) Log.i(TAG, "getAllFilteredSensorData()");

        LinkedList<FilteredSensorData> result = new LinkedList<>();

        for (MyFilter myFilter : myFilterMap.values()) {
            result.add(myFilter.getFilteredSensorData());
        }

        return result;
    }

    /**
     * Shifts buffered values in the specified filter uniformly by delta (REQ-FIL-012.6, ATT-5373).
     *
     * @param filterData the filter specification identifier.
     * @param delta the vertical offset to add to every sample.
     */
    public void shiftFilter(FilterData filterData, double delta) {
        if (filterData == null) return;
        MyFilter filter = myFilterMap.get(filterData.getHashKey());
        if (filter != null) {
            filter.shiftValues(delta);
        }
    }

    /**
     * Clears and reseeds the specified filter with a new baseline value (REQ-FIL-012.5, ATT-5373).
     *
     * @param filterData the filter specification identifier.
     * @param value the new authoritative baseline sample.
     */
    public void reseedFilter(FilterData filterData, Number value) {
        if (filterData == null) return;
        MyFilter filter = myFilterMap.get(filterData.getHashKey());
        if (filter != null) {
            filter.reseed(value);
        }
    }


    // TODO: method to remove a filter
}
