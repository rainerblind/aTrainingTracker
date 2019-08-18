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

import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.SensorType;

public abstract class MyFilter<T>
        implements MySensor.SensorListener<T> {
    private static final String TAG = MyFilter.class.getCanonicalName();
    protected String mDeviceName;
    protected SensorType mSensorType;

    public MyFilter(String deviceName, SensorType sensorType) {
        mDeviceName = deviceName;
        mSensorType = sensorType;
    }

    abstract T getFilteredValue();

    abstract FilterType getFilterType();

    abstract double getFilterConstant();

    public FilteredSensorData getFilteredSensorData() {
        // Log.i(TAG, "getFilteredSensorData(): " + getFilterType() + " " + mDeviceName + " " + mSensorType + ": " + getFilteredValue());

        T filteredValue = getFilteredValue();
        return new FilteredSensorData(mSensorType, filteredValue, mSensorType.getMyFormatter().format(filteredValue), mDeviceName, getFilterType(), getFilterConstant());
    }
}
