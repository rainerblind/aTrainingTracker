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

package com.atrainingtracker.banalservice.devices.bluetooth_le;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
abstract public class BTLEBikeDevice extends MyBTLEDevice {
    protected static final int MAX_IDENTICAL = 4;
    private static final boolean DEBUG = BANALService.DEBUG & false;

    // some variables to calc the speed, distance, and cadence
    protected boolean mLastWheelRevolutionsValid = false;
    protected boolean mLastCrankRevolutionsValid = false;
    protected long mInitWheelRevolutions = 0;
    protected long mLastWheelRevolutions = 0;
    protected long mLastWheelEventTime = 0;
    protected long mLastCrankRevolutions = 0;
    protected long mLastCrankEventTime = 0;
    protected int mIdenticalWheelTime = 0;
    protected int mIdenticalCrankTime = 0;
    protected MySensor<Double> mCadenceSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    private final String TAG = "BTLEBikeDevice";


    /**
     * constructor
     **/
    public BTLEBikeDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType, long deviceID, String address) {
        super(context, mySensorManager, deviceType, deviceID, address);
    }

    protected void addCadenceSensor() {
        if (DEBUG) Log.i(TAG, "addCadenceSensor()");

        mCadenceSensor = new MySensor<Double>(this, SensorType.CADENCE);
        addSensor(mCadenceSensor);
    }

    protected void addSpeedAndDistanceSensors() {
        if (DEBUG) Log.i(TAG, "addSpeedAndDistanceSensors()");

        mSpeedSensor = new MySensor<Double>(this, SensorType.SPEED_mps);
        mPaceSensor = new MySensor<Double>(this, SensorType.PACE_spm);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP);

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

        boolean wheelRevolutionDataPresent = (flag & 0x01) == 0x01;
        boolean crankRevolutionDataPresent = (flag & 0x02) == 0x02;

        int crankDataOffset = 1;
        if (wheelRevolutionDataPresent) {
            if (DEBUG) Log.i(TAG, "wheelRevolutionDataPresent");

            crankDataOffset = 7;

            long cumulativeWheelRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1);
            long wheelEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 5);
            if (DEBUG)
                Log.i(TAG, "revolutions: " + cumulativeWheelRevolutions + ", time: " + wheelEventTime);
            // TODO: what to do when these values are negative?

            // calc speed and distance
            if (mLastWheelRevolutionsValid) {
                if (wheelEventTime > mLastWheelEventTime) {  // avoiding negative values
                    mIdenticalWheelTime = 0;

                    long revDiff = cumulativeWheelRevolutions - mLastWheelRevolutions;
                    long timeDiff = wheelEventTime - mLastWheelEventTime;
                    if (DEBUG) Log.i(TAG, "revDiff=" + revDiff + ", timeDiff=" + timeDiff);

                    double speed = mCalibrationFactor * revDiff * 1024 / timeDiff;
                    if (DEBUG) Log.i(TAG, "got new speed: " + speed);
                    mSpeedSensor.newValue(speed);
                    if (revDiff != 0) {
                        mPaceSensor.newValue(1 / speed);
                    }

                    double distance = mCalibrationFactor * (cumulativeWheelRevolutions - mInitWheelRevolutions);
                    if (DEBUG) Log.i(TAG, "got new distance: " + distance);
                    mDistanceSensor.newValue(distance);
                    mLapDistanceSensor.newValue(distance);
                } else {
                    mIdenticalWheelTime++;
                    if (mIdenticalWheelTime >= MAX_IDENTICAL) {
                        if (DEBUG)
                            Log.i(TAG, mIdenticalWheelTime + " identical wheel times => reset speed to zero");
                        mSpeedSensor.newValue(0.0);
                        mPaceSensor.newValue(null);
                    }
                }
            } else {
                mInitWheelRevolutions = cumulativeWheelRevolutions;
            }
            mLastWheelRevolutions = cumulativeWheelRevolutions;
            mLastWheelEventTime = wheelEventTime;
            mLastWheelRevolutionsValid = true;
        }

        if (crankRevolutionDataPresent) {
            if (DEBUG) Log.i(TAG, "crankRevolutionDataPresent");

            long cumulativeCrankRevolutions = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, crankDataOffset);
            long crankEventTime = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, crankDataOffset + 2);
            if (DEBUG)
                Log.i(TAG, "revolutions: " + cumulativeCrankRevolutions + ", time: " + crankEventTime);

            // calc cadence
            if (mLastCrankRevolutionsValid) {
                if (crankEventTime > mLastCrankEventTime) { // avoiding negative values
                    mIdenticalCrankTime = 0;

                    long revDiff = cumulativeCrankRevolutions - mLastCrankRevolutions;
                    long timeDiff = crankEventTime - mLastCrankEventTime;
                    if (DEBUG) Log.i(TAG, "revDiff=" + revDiff + ", timeDiff=" + timeDiff);

                    double cadence = 60 * revDiff * 1024 / timeDiff;
                    if (DEBUG) Log.i(TAG, "got new cadence: " + cadence);
                    mCadenceSensor.newValue(cadence);
                } else {
                    mIdenticalCrankTime++;
                    if (mIdenticalCrankTime >= MAX_IDENTICAL) {
                        if (DEBUG)
                            Log.i(TAG, mIdenticalCrankTime + " identical crank times => reset cadence to zero");
                        mCadenceSensor.newValue(0.0);
                    }
                }
            }
            mLastCrankRevolutions = cumulativeCrankRevolutions;
            mLastCrankEventTime = crankEventTime;
            mLastCrankRevolutionsValid = true;
        }
    }
}
