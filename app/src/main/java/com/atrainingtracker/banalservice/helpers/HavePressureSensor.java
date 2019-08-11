package com.atrainingtracker.banalservice.helpers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;

public class HavePressureSensor {
    public static boolean havePressureSensor(Context context) {
        return ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_PRESSURE) != null;
    }
}
