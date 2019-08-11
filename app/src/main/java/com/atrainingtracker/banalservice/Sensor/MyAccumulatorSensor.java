package com.atrainingtracker.banalservice.Sensor;

import com.atrainingtracker.banalservice.Devices.MyDevice;

public abstract class MyAccumulatorSensor<T> extends MySensor<T> {
    private static final String TAG = "MyAccumulatorSensor";

    protected T mInitialValue;

    public MyAccumulatorSensor(MyDevice myDevice, SensorType sensorType) {
        super(myDevice, sensorType);
    }

    public void setInitialValue(T value) {
        if (value != null) {
            mInitialValue = value;
        }
    }

    public abstract void reset();
}
