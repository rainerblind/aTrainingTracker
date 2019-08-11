package com.atrainingtracker.banalservice.filters;

import android.util.Log;

import com.atrainingtracker.banalservice.Sensor.SensorType;

public class MaxValueFilter extends MyFilter<Number> {
    private static final String TAG = MaxValueFilter.class.getCanonicalName();
    private static final boolean DEBUG = false;

    protected Number mMaxValue = 0;

    public MaxValueFilter(String deviceName, SensorType sensorType) {
        super(deviceName, sensorType);
    }

    @Override
    public double getFilterConstant() {
        return 1;
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.MAX_VALUE;
    }

    @Override
    public synchronized void newValue(Number value) {
        if (DEBUG) Log.i(TAG, "newValue: value=" + value + ", maxValue=" + mMaxValue);

        if (value != null && value.doubleValue() > mMaxValue.doubleValue()) {
            mMaxValue = value;
        }
    }

    @Override
    public synchronized Number getFilteredValue() {
        if (DEBUG) Log.i(TAG, "getFilteredValue: " + mMaxValue);
        return mMaxValue;
    }
}
