package com.atrainingtracker.banalservice.Sensor;

import com.atrainingtracker.banalservice.BANALService;

// public class SensorData implements Parcelable
public class SensorData<T> {
    private static final String TAG = "SensorData";
    private static final boolean DEBUG = BANALService.DEBUG & false;
    public String mDeviceName;
    private SensorType mSensorType;
    private T mValue;
    private String mStringValue;


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
