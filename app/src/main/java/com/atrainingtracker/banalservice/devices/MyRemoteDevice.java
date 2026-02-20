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
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.helpers.UIHelper;

/**
 * base class for remote devices that connect via ANT+ or Bluetooth
 */
public abstract class MyRemoteDevice extends MyDevice {
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected final IntentFilter mCalibrationFactorChangedFilter = new IntentFilter(BANALService.CALIBRATION_FACTOR_CHANGED);
    protected double mCalibrationFactor = 1;
    private final String TAG = "MyRemoteDevice";
    private final BroadcastReceiver mCalibrationFactorChangedReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            long deviceId = intent.getLongExtra(BANALService.DEVICE_ID, -1);
            if (mDeviceId == deviceId) {
                mCalibrationFactor = intent.getDoubleExtra(BANALService.CALIBRATION_FACTOR, 1);
                if (DEBUG) Log.d(TAG, "got new calibrationFactor: " + mCalibrationFactor);
                onNewCalibrationFactor();
            }
        }
    };
    private boolean mSearching = false;

    public MyRemoteDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType, long deviceId) {
        super(context, mySensorManager, deviceType);
        if (DEBUG) Log.i(TAG, "MyRemoteDevice()");

        mDeviceId = deviceId;

        mCalibrationFactor = mDevicesDatabaseManager.getCalibrationFactor(deviceId);
        ContextCompat.registerReceiver(context, mCalibrationFactorChangedReceiver, mCalibrationFactorChangedFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public String getName() {
        return mDevicesDatabaseManager.getDeviceName(getDeviceId());
    }

    abstract public Protocol getProtocol();

    public String getMainSensorStringValue() {
        return getMainSensor().getStringValue();
    }

    protected MySensor getMainSensor() {
        return getSensor(getDeviceType().getMainSensorType());
    }

    public abstract void startSearching();

    protected void notifyStartSearching() {
        myLog("notifyStartSearching()");
        mSearching = true;
        mContext.sendBroadcast(addSearchDetails(
                new Intent(BANALService.SEARCHING_STARTED_FOR_ONE_INTENT)
                .setPackage(mContext.getPackageName())));
    }

    protected void notifyStopSearching(boolean success) {
        myLog("notifyStopSearching(" + success + ")");
        mSearching = false;
        mContext.sendBroadcast(addSearchDetails(new Intent(BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT))
                .putExtra(BANALService.SEARCHING_FINISHED_SUCCESS, success)
                .setPackage(mContext.getPackageName()));
        // .putExtra(BANALService.DEVICE_ID, getDeviceId()));
    }

    public boolean isSearching() {
        return mSearching;
    }

    private Intent addSearchDetails(Intent intent) {
        intent.putExtra(BANALService.PROTOCOL, getProtocol().name());
        intent.putExtra(BANALService.DEVICE_TYPE, getDeviceType().name());
        intent.putExtra(BANALService.DEVICE_ID, getDeviceId());
        intent.putExtra(BANALService.DEVICE_NAME, getDeviceName());
        return intent;
    }

    @Override
    public void shutDown() {
        if (DEBUG) Log.i(TAG, "shutDown()");

        mContext.unregisterReceiver(mCalibrationFactorChangedReceiver);
        super.shutDown();
    }

    abstract public boolean isReceivingData();


    protected void onNewCalibrationFactor() {
    }

    protected void onStartSearching() {
    }

    protected void onStopSearching() {
    }

    public String getManufacturerName() {
        return mDevicesDatabaseManager.getManufacturerName(mDeviceId);
    }

    protected void setManufacturerName(String name) {
        if (DEBUG) Log.i(TAG, "woho, we have a manufacturer: " + name);
        mDevicesDatabaseManager.setManufacturerName(mDeviceId, name);
    }

    public String getDeviceName() {
        return mDevicesDatabaseManager.getDeviceName(mDeviceId);
    }

    public boolean isPaired() {
        return mDevicesDatabaseManager.isPaired(mDeviceId);
    }


    @Override
    public String toString() {
        return mContext.getString(UIHelper.getNameId(getProtocol())) + " "
                + mContext.getString(UIHelper.getNameId(getDeviceType()));
    }

    protected void myLog(String logMessage) {
        if (DEBUG)
            Log.d(TAG, "(" + getClass().getName() + ", " + getDeviceType().name() + "): " + logMessage);
    }
}
