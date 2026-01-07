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
import com.atrainingtracker.trainingtracker.TrainingApplication;

public abstract class MyAccumulatorSensor<N extends Number> extends MySensor<N> {
    private static final String TAG = "MyAccumulatorSensor";

    Boolean mRespectPause;
    protected N mInitialValue;

    @Override
    @Deprecated
    // Do not use the newValue method directly. Use the accumulate method instead.
    public void newValue(N value) {
        super.newValue(value);
    }

    protected abstract N add(N a, N b);

    public void increment(N incrementBy) {
        if (mRespectPause && TrainingApplication.isPaused()) {
            // do nothing
        } else {
            newValue(getValue() == null ? add(mInitialValue, incrementBy) : add(getValue(), incrementBy));
        }
    }

    @Override
    public N getValue() {
        if (mActivated) {
            return mValue == null ? mInitialValue : add(mInitialValue, mValue);
        } else {
            return null;
        }
    }

    /**
     * @param myDevice
     * @param sensorType
     * @param respectPause parameter to specify whether or not the value should be incremented when the app is paused.
     *    Unfortunately, this seems to be difficult for the ANT+ and Bluetooth LE devices.  These devices simply count the wheel revolutions or steps.  Thus, it is difficult to not increment the distance when paused.
     */
    public MyAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Boolean respectPause) {
        super(myDevice, sensorType);
        mRespectPause = respectPause;
    }

    public void setInitialValue(N value) {
        if (value != null) {
            mInitialValue = value;
        }
    }

    public abstract void reset();
}
