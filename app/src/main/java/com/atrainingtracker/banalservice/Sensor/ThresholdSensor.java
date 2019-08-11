package com.atrainingtracker.banalservice.Sensor;

import com.atrainingtracker.banalservice.Devices.MyDevice;


public class ThresholdSensor<T> extends MySensor<T> {
    private static final String TAG = "ThresholdSensor";

    private int mThreshold;
    private int mNrZeros;

    public ThresholdSensor(MyDevice myDevice, SensorType sensorType, int threshold) {
        super(myDevice, sensorType);

        mThreshold = threshold;
    }


    public void newValue(T value) {
        if (value == null) {
            mNrZeros++;
            if (mNrZeros >= mThreshold) {
                super.newValue(null);
            }
            // else: keep the old value
        } else {
            mNrZeros = 0;
            super.newValue(value);
        }
    }

}   
