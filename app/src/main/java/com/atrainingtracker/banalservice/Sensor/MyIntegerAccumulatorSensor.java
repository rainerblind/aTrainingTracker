package com.atrainingtracker.banalservice.Sensor;

import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.MyDevice;

public class MyIntegerAccumulatorSensor extends MyAccumulatorSensor<Integer> {
    private static final String TAG = "MyLongAccumulatorSensor";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    private Integer mZeroValue = 0;

    public MyIntegerAccumulatorSensor(MyDevice myDevice, SensorType sensorType) {
        super(myDevice, sensorType);
        mValue = mZeroValue;

        Object initialValue = BANALService.getInitialValue(sensorType);
        mInitialValue = initialValue == null ? 0 : (Integer) initialValue;

        reset();
    }

    public MyIntegerAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Integer zeroValue) {
        this(myDevice, sensorType);

        if (DEBUG) Log.d(TAG, "MyIntegerAccumulatorSensor: setting mValue to " + zeroValue);

        mZeroValue = zeroValue;
        mValue = mZeroValue;

        reset();
    }

    @Override
    public Integer getValue() {
        if (DEBUG)
            Log.d(TAG, "getValue for " + mSensorType + ": mValue=" + mValue + ", mInitialValue=" + mInitialValue);
        if (mActivated) {
            if (mValue == null) {
                Log.d(TAG, "mValue==null!");
            }
            if (mInitialValue == null) {
                Log.d(TAG, "mInitialValue==null!");
            }

            return mValue == null ? mInitialValue : mValue + mInitialValue;
        } else {
            return null;
        }
    }

    @Override
    public void reset() {
        if (DEBUG)
            Log.d(TAG, "reset " + mSensorType + ": mValue=" + mValue + ", mZeroValue=" + mZeroValue);

        setInitialValue(-mValue + mZeroValue);
    }
}
