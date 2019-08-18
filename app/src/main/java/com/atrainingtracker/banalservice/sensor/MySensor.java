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

import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.MyDevice;

import java.util.LinkedList;


public class MySensor<T> {
    private static final String TAG = "MySensor";
    private static final boolean DEBUG = BANALService.DEBUG && false;
    protected MyDevice mDevice;
    protected SensorType mSensorType;
    protected T mValue = null;
    protected boolean mActivated = true;
    protected LinkedList<SensorListener> mSensorListeners = new LinkedList<>();

    public MySensor(MyDevice myDevice, SensorType sensorType) {
        mDevice = myDevice;
        mSensorType = sensorType;
        mActivated = true;
    }

    public void activateSensor() {
        mActivated = true;
    }

    public void deactivateSensor() {
        mActivated = false;
    }

    public void addSensorListener(SensorListener sensorListener) {
        if (DEBUG) Log.i(TAG, "addSensorListener() to " + mDevice + ", " + mSensorType);

        mSensorListeners.add(sensorListener);
    }

    public void removeSensorListener(SensorListener sensorListener) {
        mSensorListeners.remove(sensorListener);
    }

    public void newValue(T value) {
        if (DEBUG) Log.i(TAG, "newValue for " + mDevice + ", " + mSensorType + ": " + value);
        if (mActivated) {
            mValue = value;

            // inform all the listeners
            for (SensorListener sensorListener : mSensorListeners) {
                if (DEBUG) Log.i(TAG, "informing sensorListener " + sensorListener);

                sensorListener.newValue(value);
            }
        }
    }

    public T getValue() {
        // Log.d(TAG, "getValue");
        if (mActivated) {
            return mValue;
        } else {
            return null;
        }
    }

    public String getStringValue() {
        return mSensorType.getMyFormatter().format(getValue());
    }

    public SensorType getSensorType() {
        return mSensorType;
    }

    public SensorValueType getSensorValueType() {
        return mSensorType.getSensorValueType();
    }

    public MyDevice getDevice() {
        return mDevice;
    }

    public String getDeviceName() {
        return getDevice().getName();
    }

    public SensorData<T> getSensorData() {
        return new SensorData<T>(getSensorType(), getValue(), getStringValue(), getDeviceName());
    }

    public interface SensorListener<T> {
        void newValue(T value);
    }

}   
