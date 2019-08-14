/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.Sensor;

import com.atrainingtracker.banalservice.Devices.MyDevice;

/**
 * Created by rainer on 07.03.18.
 */

public class ProxySensor<T> extends MySensor<T> {
    protected MySensor<T> mSourceSensor;
    private String TAG = "ProxySensor";

    public ProxySensor(MyDevice myDevice, SensorType sensorType, MySensor<T> sourceSensor) {
        super(myDevice, sensorType);
        mSourceSensor = sourceSensor;
    }

    @Override
    public T getValue() {
        return mSourceSensor.getValue();
    }

    public void addSensorListener(SensorListener sensorListener) {
        super.addSensorListener(sensorListener);
        mSourceSensor.addSensorListener(sensorListener);   // also add this sensor listener to the source device
    }

    public void removeSensorListener(SensorListener sensorListener) {
        super.removeSensorListener(sensorListener);
        mSourceSensor.removeSensorListener(sensorListener);  // also remove this sensor listener from the source device
    }

    @Override
    public String getDeviceName() {
        return null;
    }

    public String getSourceDeviceName() {
        return getDevice().getName();
    }

    public void setSourceSensor(MySensor<T> sourceSensor) {
        // move all SensorListeners to the new sensor
        for (SensorListener sensorListener : mSensorListeners) {
            mSourceSensor.removeSensorListener(sensorListener);
            sourceSensor.addSensorListener(sensorListener);
        }

        mSourceSensor = sourceSensor;
    }
}
