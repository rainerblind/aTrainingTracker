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
import com.atrainingtracker.trainingtracker.TrainingApplication;

public abstract class MyAccumulatorSensor<N extends Number> extends MySensor<N> {
    private static final String TAG = "MyAccumulatorSensor";

    Boolean mRespectPause;
    protected volatile N mInitialValue;  // necessary to manipulate the output that we get via getValue()...
    protected N mZeroValue; // In almost all cases, this will be 0.  In some special cases, like a lap sensor, this will be 1.
    protected volatile N mLastAbsoluteValue;

    @Override
    @Deprecated
    // Do not use the newValue method directly. Use the accumulate method instead.
    public void newValue(N value) {
        super.newValue(value);
    }

    protected abstract N add(N a, N b);

    protected abstract N sub(N a, N b);


    public void accumulate(N absoluteValue) {
        N incrementBy = null;
        synchronized (this) {
            if (mLastAbsoluteValue != null) {
                N diff = sub(absoluteValue, mLastAbsoluteValue);
                if (diff.doubleValue() >= 0) {
                    incrementBy = diff;
                } else {
                    android.util.Log.w(TAG, "Negative increment detected (" + diff + "). Sensor reset or wrap-around? Ignoring.");
                }
            }
            mLastAbsoluteValue = absoluteValue;
        }
        if (incrementBy != null) {
            increment(incrementBy);
        }
    }


    public void increment(N incrementBy) {
        if (mRespectPause && (!TrainingApplication.isTracking() || TrainingApplication.isPaused() || TrainingApplication.isAutoPaused())) {
            // do nothing
        } else {
            N currentTotal = null;
            boolean active;
            synchronized (this) {
                active = mActivated;
                if (active) {
                    mValue = add(mValue, incrementBy);
                    mLastUpdateTimestamp = System.currentTimeMillis();
                    currentTotal = add(mInitialValue, mValue);
                }
            }
            if (active) {
                for (SensorListener sensorListener : mSensorListeners) {
                    sensorListener.newValue(currentTotal);
                }
            }
        }
    }

    @Override
    public synchronized N getValue() {
        if (mActivated) {
            return add(mInitialValue, mValue);
        } else {
            return null;
        }
    }

    @Override
    public void addSensorListener(SensorListener sensorListener) {
        if (sensorListener == null) {
            return;
        }
        mSensorListeners.addIfAbsent(sensorListener);
        // Push composite total (mInitialValue + mValue), not raw incremental mValue, so rehydrated
        // or re-registered accumulators never notify bound ProxySensor/FilterManager listeners with 0.
        N currentTotal = getValue();
        if (currentTotal != null) {
            sensorListener.newValue(currentTotal);
        }
    }

    /**
     * @param myDevice
     * @param sensorType
     * @param respectPause parameter to specify whether or not the value should be incremented when the app is paused.
     *    Unfortunately, this seems to be difficult for the ANT+ and Bluetooth LE devices.  These devices simply count the wheel revolutions or steps.  Thus, it is difficult to not increment the distance when paused.
     */
    @SuppressWarnings("unchecked")
    public MyAccumulatorSensor(MyDevice myDevice, SensorType sensorType, Boolean respectPause, N zeroValue) {
        super(myDevice, sensorType);
        mRespectPause = respectPause;
        mZeroValue = zeroValue;
        N initialVal = zeroValue;
        if (myDevice != null && myDevice.getMySensorManager() != null) {
            Object registeredInitial = myDevice.getMySensorManager().getInitialValue(sensorType);
            if (registeredInitial instanceof Number) {
                if (zeroValue instanceof Double) {
                    initialVal = (N) Double.valueOf(((Number) registeredInitial).doubleValue());
                } else if (zeroValue instanceof Integer) {
                    initialVal = (N) Integer.valueOf(((Number) registeredInitial).intValue());
                } else if (zeroValue instanceof Long) {
                    initialVal = (N) Long.valueOf(((Number) registeredInitial).longValue());
                } else if (zeroValue instanceof Float) {
                    initialVal = (N) Float.valueOf(((Number) registeredInitial).floatValue());
                }
            }
        }
        mInitialValue = initialVal;
        newValue(zeroValue);
    }

    public final void setInitialValue(N value) {
        N currentTotal;
        boolean active;
        synchronized (this) {
            mInitialValue = value;
            currentTotal = add(mInitialValue, mValue);
            active = mActivated;
        }
        if (active) {
            for (SensorListener sensorListener : mSensorListeners) {
                sensorListener.newValue(currentTotal);
            }
        }
    }

    /**
     * Returns the baseline zero value defined for this accumulator sensor (e.g. 0 for time/distance/elevation, 1 for LAP_NR).
     *
     * @return the baseline zero value.
     */
    public N getZeroValue() {
        return mZeroValue;
    }

    public final void reset() {
        boolean active;
        synchronized (this) {
            mInitialValue = sub(mZeroValue, mValue);
            mLastAbsoluteValue = null;
            active = mActivated;
        }
        if (active) {
            for (SensorListener sensorListener : mSensorListeners) {
                sensorListener.newValue(mZeroValue);
            }
        }
    }

    /**
     * Retroactively re-anchors this accumulator's composite total ({@code mInitialValue + mValue})
     * to {@code targetValue} without altering {@code mValue}, {@code mLastAbsoluteValue}, or
     * {@code MySensorManager}'s global initial-value map ({@code REQ-FIL-031.2}, {@code ATT-8917}).
     *
     * <p>Listeners are notified outside {@code synchronized (this)} when activated.
     *
     * @param targetValue The exact composite value this accumulator shall immediately report.
     */
    public final void resetTo(N targetValue) {
        if (targetValue == null) {
            return;
        }
        boolean active;
        synchronized (this) {
            mInitialValue = sub(targetValue, mValue);
            active = mActivated;
        }
        if (active) {
            for (SensorListener sensorListener : mSensorListeners) {
                sensorListener.newValue(targetValue);
            }
        }
    }
}
