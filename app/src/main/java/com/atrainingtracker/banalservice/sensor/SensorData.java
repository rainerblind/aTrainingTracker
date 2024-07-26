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

package com.atrainingtracker.banalservice.sensor;

import com.atrainingtracker.banalservice.BANALService;

// public class SensorData implements Parcelable
public class SensorData<T> {
    private static final String TAG = "SensorData";
    private static final boolean DEBUG = BANALService.getDebug(false);
    public String mDeviceName;
    private final SensorType mSensorType;
    private final T mValue;
    private final String mStringValue;


    public SensorData(SensorType sensorType, T value, String stringValue, String deviceName) {
        mSensorType = sensorType;
        mValue = value;
        mStringValue = stringValue;
        mDeviceName = deviceName;
    }


    public SensorType getSensorType() {
        return mSensorType;
    }

    public T getValue() {
        return mValue;
    }

    public String getStringValue() {
        return mStringValue;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

}
