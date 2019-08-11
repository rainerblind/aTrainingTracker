package com.atrainingtracker.banalservice.filters;

import com.atrainingtracker.banalservice.Sensor.SensorData;
import com.atrainingtracker.banalservice.Sensor.SensorType;

public class FilteredSensorData<T> extends SensorData<T> {

    protected FilterType mFilterType;
    protected double mFilterConstant;

    public FilteredSensorData(SensorType sensorType, T value, String stringValue, String deviceName, FilterType filterType, double filterConstant) {
        super(sensorType, value, stringValue, deviceName);

        mFilterType = filterType;
        mFilterConstant = filterConstant;
    }

    public FilterType getFilterType() {
        return mFilterType;
    }

    public double getFilterConstant() {
        return mFilterConstant;
    }

    public FilterData getFilterData() {
        return new FilterData(getDeviceName(), getSensorType(), getFilterType(), getFilterConstant());
    }

}
