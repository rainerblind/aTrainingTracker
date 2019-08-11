package com.atrainingtracker.banalservice.Sensor;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.MyDevice;


public class MyDoubleAccumulatorSensor extends MyAccumulatorSensor<Double> {
    private static final String TAG = "MyDoubleAccumulatorSensor";

    private double mZeroValue = 0.0;

    public MyDoubleAccumulatorSensor(MyDevice myDevice, SensorType sensorType) {
        super(myDevice, sensorType);
        mValue = mZeroValue;

        Double initialValue = (Double) BANALService.getInitialValue(sensorType);
        mInitialValue = initialValue == null ? 0.0 : initialValue;

        reset();
    }

    public MyDoubleAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Double zeroValue) {
        this(myDevice, sensorType);

        mZeroValue = zeroValue;
        mValue = mZeroValue;

        reset();
    }

    @Override
    public Double getValue() {
        // Log.d(TAG, "getValue");
        if (mActivated) {
            return mValue == null ? mInitialValue : mValue + mInitialValue;
        } else {
            return null;
        }
    }

    @Override
    public void reset() {
        setInitialValue(-mValue + mZeroValue);
    }
}
