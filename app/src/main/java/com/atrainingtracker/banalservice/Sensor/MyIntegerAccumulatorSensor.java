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

import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Devices.MyDevice;

public class MyIntegerAccumulatorSensor extends MyAccumulatorSensor<Integer> {
    private static final String TAG = "MyLongAccumulatorSensor";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    private Integer mZeroValue = 0;

    public MyIntegerAccumulatorSensor(MyDevice myDevice, SensorType sensorType) {
        super(myDevice, sensorType);
        mValue = mZeroValue;

        Object initialValue = BANALService.getInitialValue(sensorType);
        mInitialValue = initialValue == null ? 0 : (Integer) initialValue;

        reset();
    }

    public MyIntegerAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Integer zeroValue) {
        this(myDevice, sensorType);

        if (DEBUG) Log.d(TAG, "MyIntegerAccumulatorSensor: setting mValue to " + zeroValue);

        mZeroValue = zeroValue;
        mValue = mZeroValue;

        reset();
    }

    @Override
    public Integer getValue() {
        if (DEBUG)
            Log.d(TAG, "getValue for " + mSensorType + ": mValue=" + mValue + ", mInitialValue=" + mInitialValue);
        if (mActivated) {
            if (mValue == null) {
                Log.d(TAG, "mValue==null!");
            }
            if (mInitialValue == null) {
                Log.d(TAG, "mInitialValue==null!");
            }

            return mValue == null ? mInitialValue : mValue + mInitialValue;
        } else {
            return null;
        }
    }

    @Override
    public void reset() {
        if (DEBUG)
            Log.d(TAG, "reset " + mSensorType + ": mValue=" + mValue + ", mZeroValue=" + mZeroValue);

        setInitialValue(-mValue + mZeroValue);
    }
}
