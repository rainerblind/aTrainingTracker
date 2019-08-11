package com.atrainingtracker.banalservice.filters;

import com.atrainingtracker.banalservice.Sensor.SensorType;

public class FilterData {

    public String deviceName;
    public SensorType sensorType;
    public FilterType filterType;
    public double filterConstant;

    public FilterData(String deviceName, SensorType sensorType, FilterType filterType, double filterConstant) {
        this.deviceName = deviceName;
        this.sensorType = sensorType;
        this.filterType = filterType;
        this.filterConstant = filterConstant;
    }

    public String getHashKey() {
        return deviceName + "-" + sensorType.name() + "-" + filterType.name() + "-" + filterConstant;
    }
}
