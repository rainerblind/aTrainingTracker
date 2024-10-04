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

package com.atrainingtracker.banalservice.devices;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MyAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;


public abstract class MyDevice {
    private static final boolean DEBUG = BANALService.getDebug(false);
    private final IntentFilter resetAccumulatorsFilter = new IntentFilter(BANALService.RESET_ACCUMULATORS_INTENT);
    protected Context mContext;
    protected DeviceType mDeviceType;
    protected EnumMap<SensorType, MySensor> mSensorMap = new EnumMap<SensorType, MySensor>(SensorType.class);
    protected MySensorManager mMySensorManager;
    private final String TAG = "MyDevice";
    private boolean mSensorsRegistered = false;
    private final BroadcastReceiver resetAccumulatorsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            if (DEBUG) Log.d(TAG, "resetAccumulatorsReceiver.onReceive()");
            for (MySensor mySensor : getSensors()) {
                if (mySensor instanceof MyAccumulatorSensor) {
                    ((MyAccumulatorSensor) mySensor).reset();
                }

            }
        }
    };

    public MyDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType) {
        mContext = context;
        mMySensorManager = mySensorManager;
        mDeviceType = deviceType;

        ContextCompat.registerReceiver(mContext, resetAccumulatorsReceiver, resetAccumulatorsFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        addSensors();
    }

    public abstract String getName();

    protected abstract void addSensors();

    protected void addSensor(MySensor mySensor) {
        if (DEBUG) {
            Log.d(TAG, "addSensor(" + mySensor.getSensorType().name() + ")");
        }

        mSensorMap.put(mySensor.getSensorType(), mySensor);
    }

    protected void removeSensor(SensorType sensorType) {
        if (DEBUG) Log.d(TAG, "removeSensor(" + sensorType.name() + ")");

        mSensorMap.remove(sensorType);
    }

    protected void registerSensors() {
        if (DEBUG) {
            Log.d(TAG, "registerSensors()");
        }

        if (!mSensorsRegistered) {
            mMySensorManager.registerSensors(getSensors());
            mSensorsRegistered = true;
        }

        for (MySensor mySensor : getSensors()) {
            if (DEBUG) Log.d(TAG, "activating Sensor: " + mySensor.getSensorType().name());
            mySensor.activateSensor();
        }

        notifySensorsChanged();
    }

    protected void notifySensorsChanged() {
        mContext.sendBroadcast(new Intent(BANALService.SENSORS_CHANGED)
                .setPackage(mContext.getPackageName()));
    }

    protected void addAndRegisterSensor(MySensor mySensor) {
        // TODO; maybe, we should check whether this sensor is already added and registered???
        addSensor(mySensor);
        mMySensorManager.registerSensor(mySensor);
        mySensor.activateSensor();

        notifySensorsChanged();
    }

    // avoid calling notifySensorsChanged() several times...
    protected void addAndRegisterSensors(List<MySensor> mySensorList) {
        for (MySensor mySensor : mySensorList) {
            addSensor(mySensor);
            mMySensorManager.registerSensor(mySensor);
            mySensor.activateSensor();
        }

        notifySensorsChanged();
    }

    protected void unregisterSensors() {
        if (DEBUG) {
            Log.d(TAG, "unregisterSensors()");
        }

        // tell all sensors that the data is invalid
        for (MySensor mySensor : getSensors()) {
            mySensor.deactivateSensor();
        }

        if (mSensorsRegistered) {

            // then unregister all sensors
            mMySensorManager.unregisterSensors(getSensors());
            mSensorsRegistered = false;
        }

        notifySensorsChanged();
    }

    protected boolean sensorsRegistered() {
        return mSensorsRegistered;
    }

    public void shutDown() {
        if (DEBUG) Log.i(TAG, "shutDown(): " + mDeviceType.name());

        unregisterSensors();

        mContext.unregisterReceiver(resetAccumulatorsReceiver);
    }

    public final DeviceType getDeviceType() {
        return mDeviceType;
    }

    /**
     * returns the Sensors
     */
    public Collection<MySensor> getSensors() {
        return mSensorMap.values();
    }

    public int getNrOfSensors() {
        return mSensorMap.size();
    }

    /**
     * returns the corresponding Sensor when available, otherwise null
     */
    public MySensor getSensor(SensorType sensorType) {
        return mSensorMap.get(sensorType);
    }

    protected void newLap() {
        if (DEBUG) Log.d(TAG, "newLap()");
    }

}
