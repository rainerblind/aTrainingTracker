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

package com.atrainingtracker.banalservice.Devices.bluetooth_le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.DeviceType;
import com.atrainingtracker.banalservice.Sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.Sensor.MySensor;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;
import com.atrainingtracker.banalservice.Sensor.SensorType;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BTLERunSpeedDevice extends MyBTLEDevice {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    private final ScheduledExecutorService mScheduler = Executors.newScheduledThreadPool(1);
    protected MySensor<Integer> mCadenceSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;    // WTF: a distance sensor is not always present in bloutooth devices!
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    private String TAG = "BTLERunSpeedDevice";
    private boolean mDistancePresent;
    private double mDistance = 0;
    private double mSpeed;
    final Runnable distanceCalculator = new Runnable() {
        public void run() {
            if (!mDistancePresent) {
                mDistance += mSpeed;   // since the sampling time is 1 second, we just have to add the speed;

                if (DEBUG) Log.d(TAG, "had to calc distance (in meters): " + mDistance);

                mDistanceSensor.newValue(mDistance);
                mLapDistanceSensor.newValue(mDistance);
            }
        }
    };
    //TODO: there might be a stride length sensor
    private ScheduledFuture mDistanceCalculatorHandle;

    /**
     * constructor
     **/
    public BTLERunSpeedDevice(Context context, MySensorManager mySensorManager, long deviceID, String address) {
        super(context, mySensorManager, DeviceType.RUN_SPEED, deviceID, address);
        if (DEBUG) Log.d(TAG, "creating BT run speed device");

        mDistanceCalculatorHandle = mScheduler.scheduleAtFixedRate(distanceCalculator, 0, // initial delay
                1, // sampling time
                TimeUnit.SECONDS);
    }

    @Override
    protected void addSensors() {
        mCadenceSensor = new MySensor<Integer>(this, SensorType.CADENCE);
        mSpeedSensor = new MySensor<Double>(this, SensorType.SPEED_mps);
        mPaceSensor = new MySensor<Double>(this, SensorType.PACE_spm);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP);

        addSensor(mCadenceSensor);
        addSensor(mSpeedSensor);
        addSensor(mPaceSensor);
        addSensor(mDistanceSensor);
        addSensor(mLapDistanceSensor);
    }

    @Override
    protected void newLap() {
        if (mLapDistanceSensor != null) {
            mLapDistanceSensor.reset();
        }
    }

    @Override
    protected void measurementCharacteristicUpdate(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        int flag = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

        boolean stride_length_present = (flag & 0x01) != 0;
        if (DEBUG) Log.i(TAG, "stride length " + (stride_length_present ? "" : "not ") + "present");
        mDistancePresent = (flag & 0x02) != 0;
        // boolean walking                = (flag & 0x04) == 0;
        // boolean running                = (flag & 0x04) == 0;
        int distance_offset = stride_length_present ? 6 : 4;
        if (DEBUG) Log.i(TAG, "distance_offset=" + distance_offset);

        int speed = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 1); // Unit is in m/s with a resolution of 1/256 s
        mSpeed = mCalibrationFactor * speed / 256;
        mSpeedSensor.newValue(mSpeed);
        if (speed != 0) {
            mPaceSensor.newValue(1 / mSpeed);
        }

        mCadenceSensor.newValue(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 3));

        if (stride_length_present) {
            Log.i(TAG, "strideLength=" + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 4) + " cm");
        }

        if (mDistancePresent) {
            mDistance = mCalibrationFactor * characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, distance_offset) / 10;
            if (DEBUG) Log.d(TAG, "got distance (in meters): " + mDistance);
            mDistanceSensor.newValue(mDistance);
            mLapDistanceSensor.newValue(mDistance);
        }
    }

    @Override
    public void shutDown() {
        super.shutDown();

        if (mDistanceCalculatorHandle != null) {
            mDistanceCalculatorHandle.cancel(true);
        }
    }
}
