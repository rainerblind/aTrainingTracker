/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
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
import com.atrainingtracker.banalservice.Devices.DeviceManager;
import com.atrainingtracker.banalservice.Devices.MyDevice;
import com.atrainingtracker.banalservice.Sensor.MySensor;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static com.atrainingtracker.trainingtracker.dialogs.EditFieldDialog.TRACKING_VIEW_CHANGED_INTENT;

public class FilterManager {
    private static final String TAG = FilterType.class.getSimpleName();
    private static final boolean DEBUG = BANALService.DEBUG & false;

    protected HashMap<String, FilterData> mRequiredButNotAvailableFilters = new HashMap<>();

    protected HashMap<String, MyFilter> myFilterMap = new HashMap<>();
    protected HashMap<MyFilter, MySensor> mMapFilter2Sensor = new HashMap<>();

    protected Context mContext;
    protected DeviceManager mDeviceManager;
    protected MySensorManager mMySensorManager;

    protected IntentFilter mSensorsChangedFilter = new IntentFilter(BANALService.SENSORS_CHANGED);
    protected IntentFilter mFiltersChangedFilter = new IntentFilter(TRACKING_VIEW_CHANGED_INTENT);
    protected BroadcastReceiver mSensorsChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // try to create the filters that were not yet ready
            for (FilterData filterData : mRequiredButNotAvailableFilters.values()) {
                createFilter(filterData);
            }
        }
    };
    protected BroadcastReceiver mFiltersChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            for (FilterData filterData : TrackingViewsDatabaseManager.getAllFilterData()) {
                createFilter(filterData);
            }
        }
    };


    public FilterManager(Context context, DeviceManager deviceManager, MySensorManager mySensorManager) {
        if (DEBUG) Log.i(TAG, "FilterManager()");

        mDeviceManager = deviceManager;
        mMySensorManager = mySensorManager;
        mContext = context;

        mContext.registerReceiver(mSensorsChangedReceiver, mSensorsChangedFilter);
        mContext.registerReceiver(mFiltersChangedReceiver, mFiltersChangedFilter);
    }

    public void createFilter(FilterData filterData) {
        if (DEBUG) Log.i(TAG, "createFilter: " + filterData.getHashKey());

        // if the requested filter is already there, we simply return
        if (myFilterMap.containsKey(filterData.getHashKey())) {
            if (DEBUG) Log.i(TAG, "filter already there");
            return;
        }


        // first of all, we try to get the sensor
        MySensor mySensor = null;

        // when deviceName is null, we try to get the best sensor
        if (filterData.deviceName == null) {
            mySensor = mMySensorManager.getSensor(filterData.sensorType);
        } else {
            MyDevice myDevice = mDeviceManager.getMyDeviceByName(filterData.deviceName);
            if (myDevice != null) {        // check if device exists
                mySensor = myDevice.getSensor(filterData.sensorType);
            }
        }

        if (mySensor == null) {  // sensor not (yet) available
            if (DEBUG) Log.i(TAG, "sensor not (yet) available");
            mRequiredButNotAvailableFilters.put(filterData.getHashKey(), filterData);   // save this request for later on..
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

        mySensor.addSensorListener(myFilter);

        myFilterMap.put(filterData.getHashKey(), myFilter);
        mMapFilter2Sensor.put(myFilter, mySensor);

        if (DEBUG) Log.i(TAG, "filter " + filterData.getHashKey() + "created");
    }

    public void shutDown() {
        for (MyFilter myFilter : myFilterMap.values()) {
            mMapFilter2Sensor.get(myFilter).removeSensorListener(myFilter);
        }

        mContext.unregisterReceiver(mSensorsChangedReceiver);
        mContext.unregisterReceiver(mFiltersChangedReceiver);
    }


    public FilteredSensorData getFilteredSensorData(FilterData filterData) {
        String key = filterData.getHashKey();
        if (myFilterMap.containsKey(key)) {
            return myFilterMap.get(key).getFilteredSensorData();
        } else {
            return null;
        }
    }


    public List<FilteredSensorData> getAllFilteredSensorData() {
        if (DEBUG) Log.i(TAG, "getAllFilteredSensorData()");

        LinkedList<FilteredSensorData> result = new LinkedList<>();

        for (MyFilter myFilter : myFilterMap.values()) {
            result.add(myFilter.getFilteredSensorData());
        }

        return result;
    }


    // TODO: method to remove a filter
}
