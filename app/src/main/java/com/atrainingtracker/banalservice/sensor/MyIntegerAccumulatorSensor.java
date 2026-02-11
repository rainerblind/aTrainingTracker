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

package com.atrainingtracker.banalservice.sensor;

import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.MyDevice;

public class MyIntegerAccumulatorSensor extends MyAccumulatorSensor<Integer> {
    private static final String TAG = "MyLongAccumulatorSensor";
    private static final boolean DEBUG = BANALService.getDebug(false);

    public MyIntegerAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Boolean respectPause, Integer zeroValue) {
        super(myDevice, sensorType, respectPause, zeroValue);
    }

    public MyIntegerAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Boolean respectPause) {
        super(myDevice, sensorType, respectPause, 0);  // when the zeroValue is not explicitly given in the constructor, it will be set to 0.
    }

    @Override
    // doing the most obvious stuff...
    protected Integer add(Integer a, Integer b) {
        return a + b;
    }

    protected Integer sub(Integer a, Integer b) {
        return a - b;
    }
}
