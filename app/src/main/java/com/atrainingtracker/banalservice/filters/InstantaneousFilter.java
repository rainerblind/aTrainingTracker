package com.atrainingtracker.banalservice.filters;

import com.atrainingtracker.banalservice.Sensor.SensorType;

public class InstantaneousFilter<T> extends MyFilter<T> {
    protected T mValue = null;

    public InstantaneousFilter(String deviceName, SensorType sensorType) {
        super(deviceName, sensorType);
    }

    @Override
    public double getFilterConstant() {
        return 1;
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.INSTANTANEOUS;
    }

    @Override
    public synchronized void newValue(T value) {
        mValue = value;
    }

    @Override
    public synchronized T getFilteredValue() {
        return mValue;
    }
}
