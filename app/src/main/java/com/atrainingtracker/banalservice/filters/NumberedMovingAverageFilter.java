package com.atrainingtracker.banalservice.filters;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Sensor.SensorType;

import java.util.ArrayList;

public class NumberedMovingAverageFilter
        extends MovingAverageFilter {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    private static final String TAG = NumberedMovingAverageFilter.class.getName();

    protected int mSize;
    protected int mIndex;
    protected ArrayList<Number> mValues = new ArrayList<>();

    public NumberedMovingAverageFilter(String deviceName, SensorType sensorType, int size) {
        super(deviceName, sensorType);

        mSize = size;
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.MOVING_AVERAGE_NUMBER;
    }

    @Override
    public double getFilterConstant() {
        return mSize;
    }

    @Override
    public synchronized void newValue(Number value) {
        mValues.add(mIndex, value);
        mIndex = mIndex++ % mSize;
    }

    @Override
    public synchronized Number getFilteredValue() {
        if (mValues.size() == 0) {
            return null;
        }

        double sum = 0;
        for (Number value : mValues) {
            sum += value.doubleValue();
        }
        return sum / mValues.size();
    }
}
