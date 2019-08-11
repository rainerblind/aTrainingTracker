package com.atrainingtracker.banalservice.filters;

import com.atrainingtracker.banalservice.Sensor.SensorType;

public class AverageFilter extends MyFilter<Number> {
    protected double mAccumulatedValue = 0;
    protected long mNumberOfSamples = 0;

    public AverageFilter(String deviceName, SensorType sensorType) {
        super(deviceName, sensorType);
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.AVERAGE;
    }

    @Override
    public double getFilterConstant() {
        return 1;
    }

    @Override
    public synchronized void newValue(Number value) {
        mAccumulatedValue += value.doubleValue();
        mNumberOfSamples++;
    }

    @Override
    public synchronized Number getFilteredValue() {
        if (mNumberOfSamples == 0) {
            return null;
        } else {
            return mAccumulatedValue / mNumberOfSamples;
        }
    }
}
