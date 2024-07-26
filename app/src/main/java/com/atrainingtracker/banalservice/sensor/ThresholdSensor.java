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

import com.atrainingtracker.banalservice.devices.MyDevice;


public class ThresholdSensor<T> extends MySensor<T> {
    private static final String TAG = "ThresholdSensor";

    private final int mThreshold;
    private int mNrZeros;

    public ThresholdSensor(MyDevice myDevice, SensorType sensorType, int threshold) {
        super(myDevice, sensorType);

        mThreshold = threshold;
    }


    public void newValue(T value) {
        if (value == null) {
            mNrZeros++;
            if (mNrZeros >= mThreshold) {
                super.newValue(null);
            }
            // else: keep the old value
        } else {
            mNrZeros = 0;
            super.newValue(value);
        }
    }

}   
