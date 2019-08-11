package com.atrainingtracker.banalservice.Devices;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Protocol;
import com.atrainingtracker.banalservice.Sensor.MySensor;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.helpers.UIHelper;

/**
 * base class for remote devices that connect via ANT+ or Bluetooth
 */
public abstract class MyRemoteDevice extends MyDevice {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    protected final IntentFilter mCalibrationFactorChangedFilter = new IntentFilter(BANALService.CALIBRATION_FACTOR_CHANGED);
    protected double mCalibrationFactor = 1;
    long mDeviceId = -1;  // the id of the device within the database
    private String TAG = "MyRemoteDevice";
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

        mCalibrationFactor = DevicesDatabaseManager.getCalibrationFactor(deviceId);
        context.registerReceiver(mCalibrationFactorChangedReceiver, mCalibrationFactorChangedFilter);
    }

    @Override
    public String getName() {
        return DevicesDatabaseManager.getDeviceName(getDeviceId());
    }

    abstract public Protocol getProtocol();

    public long getDeviceId() {
        return mDeviceId;
    }

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
        mContext.sendBroadcast(addSearchDetails(new Intent(BANALService.SEARCHING_STARTED_FOR_ONE_INTENT)));
    }

    protected void notifyStopSearching(boolean success) {
        myLog("notifyStopSearching(" + success + ")");
        mSearching = false;
        mContext.sendBroadcast(addSearchDetails(new Intent(BANALService.SEARCHING_STOPPED_FOR_ONE_INTENT))
                .putExtra(BANALService.SEARCHING_FINISHED_SUCCESS, success));
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
        return DevicesDatabaseManager.getManufacturerName(mDeviceId);
    }

    protected void setManufacturerName(String name) {
        if (DEBUG) Log.i(TAG, "woho, we have a manufactuer: " + name);
        DevicesDatabaseManager.setManufacturerName(mDeviceId, name);
    }

    public String getDeviceName() {
        return DevicesDatabaseManager.getDeviceName(mDeviceId);
    }

    public boolean isPaired() {
        return DevicesDatabaseManager.isPaired(mDeviceId);
    }

    protected void setLastActive() {
        DevicesDatabaseManager.setLastActive(mDeviceId);
    }

    protected void setBatteryPercentage(int percentage) {
        DevicesDatabaseManager.setBatteryPercentage(mDeviceId, percentage);
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
