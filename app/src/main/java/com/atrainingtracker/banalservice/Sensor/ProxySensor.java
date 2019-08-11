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
