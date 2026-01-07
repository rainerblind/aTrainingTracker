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

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.devices.MyDevice;


public class MyDoubleAccumulatorSensor extends MyAccumulatorSensor<Double> {
    private static final String TAG = "MyDoubleAccumulatorSensor";

    private double mZeroValue = 0.0;

    public MyDoubleAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Boolean respectPause) {
        super(myDevice, sensorType, respectPause);
        mValue = mZeroValue;

        Double initialValue = (Double) BANALService.getInitialValue(sensorType);
        mInitialValue = initialValue == null ? 0.0 : initialValue;

        reset();
    }

    public MyDoubleAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Boolean respectPause, Double zeroValue) {
        this(myDevice, sensorType, respectPause);

        mZeroValue = zeroValue;
        mValue = mZeroValue;

        reset();
    }

    @Override
    // doing the most obvious stuff...
    protected Double add(Double a, Double b) {
        return a + b;
    }

    @Override
    public void reset() {
        setInitialValue(-mValue + mZeroValue);
    }
}
