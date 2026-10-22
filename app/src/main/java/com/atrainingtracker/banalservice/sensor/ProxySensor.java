/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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

package com.atrainingtracker.banalservice.sensor;

import com.atrainingtracker.banalservice.devices.MyDevice;

/**
 * Created by rainer on 07.03.18.
 */

public class ProxySensor<T> extends MySensor<T> {
    protected volatile MySensor<T> mSourceSensor;
    private final String TAG = "ProxySensor";

    public ProxySensor(MyDevice myDevice, SensorType sensorType, MySensor<T> sourceSensor) {
        super(myDevice, sensorType);
        mSourceSensor = sourceSensor;
    }

    @Override
    public T getValue() {
        final MySensor<T> source = mSourceSensor;
        return source != null ? source.getValue() : null;
    }

    /**
     * Returns the last update timestamp delegated from the underlying source sensor.
     *
     * @return the source sensor's last update timestamp in milliseconds, or {@code 0L} if no source sensor is bound.
     */
    @Override
    public long getLastUpdateTimestamp() {
        final MySensor<T> source = mSourceSensor;
        return source != null ? source.getLastUpdateTimestamp() : 0L;
    }

    /**
     * Returns a {@link SensorData} snapshot using a single atomic local reference to {@link #mSourceSensor},
     * delegating value and timestamp to the underlying source sensor while preserving {@code null} {@link #getDeviceName()}
     * so primary sample table columns remain unsuffixed.
     */
    @Override
    public SensorData<T> getSensorData() {
        final MySensor<T> source = mSourceSensor;
        final T value = source != null ? source.getValue() : null;
        final long timestamp = source != null ? source.getLastUpdateTimestamp() : 0L;
        final String stringValue = getSensorType() != null && getSensorType().getMyFormatter() != null
                ? getSensorType().getMyFormatter().format(value)
                : "";
        return new SensorData<>(getSensorType(), value, stringValue, getDeviceName(), timestamp);
    }

    private final Object mSourceLock = new Object();

    @Override
    public void addSensorListener(SensorListener sensorListener) {
        super.addSensorListener(sensorListener);
        final MySensor<T> source = mSourceSensor;
        if (source != null) {
            source.addSensorListener(sensorListener);   // also add this sensor listener to the source device
        }
    }

    @Override
    public void removeSensorListener(SensorListener sensorListener) {
        super.removeSensorListener(sensorListener);
        final MySensor<T> source = mSourceSensor;
        if (source != null) {
            source.removeSensorListener(sensorListener);  // also remove this sensor listener from the source device
        }
    }

    @Override
    public String getDeviceName() {
        return null;
    }

    public String getSourceDeviceName() {
        final MySensor<T> source = mSourceSensor;
        return (source != null && source.getDevice() != null) ? source.getDevice().getName() : null;
    }

    public long getSourceDeviceId() {
        final MySensor<T> source = mSourceSensor;
        return (source != null && source.getDevice() != null) ? source.getDevice().getDeviceId() : -1L;
    }

    public void setSourceSensor(MySensor<T> sourceSensor) {
        final MySensor<T> previousSource;
        final java.util.List<SensorListener> listenersSnapshot;

        synchronized (mSourceLock) {
            previousSource = mSourceSensor;
            if (previousSource == sourceSensor) {
                return;
            }
            mSourceSensor = sourceSensor;
            listenersSnapshot = new java.util.ArrayList<>(mSensorListeners);
        }

        // Migrate all SensorListeners to the new source sensor strictly outside mSourceLock
        // to prevent lock-order inversions during initial newValue callbacks (REQ-SEN-021.2).
        for (SensorListener sensorListener : listenersSnapshot) {
            if (previousSource != null) {
                previousSource.removeSensorListener(sensorListener);
            }
            if (sourceSensor != null) {
                sourceSensor.addSensorListener(sensorListener);
            }
        }
    }
}
