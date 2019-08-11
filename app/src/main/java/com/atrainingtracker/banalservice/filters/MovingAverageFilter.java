package com.atrainingtracker.banalservice.filters;

import com.atrainingtracker.banalservice.Sensor.SensorType;

// moving average either over a fixed time horizon or a fixed number of measurements
public abstract class MovingAverageFilter extends MyFilter<Number> {
    public MovingAverageFilter(String deviceName, SensorType sensorType) {
        super(deviceName, sensorType);
    }

}
