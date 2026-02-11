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

package com.atrainingtracker.banalservice.filters;

import android.util.Log;

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;

public class MaxValueFilter extends MyFilter<Number> {
    private static final String TAG = MaxValueFilter.class.getCanonicalName();
    private static final boolean DEBUG = false;

    protected Number mMaxValue = 0;

    public MaxValueFilter(String deviceName, SensorType sensorType) {
        super(deviceName, sensorType);
    }

    @Override
    public double getFilterConstant() {
        return 1;
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.MAX_VALUE;
    }

    @Override
    public synchronized void newValue(Number value) {
        if (DEBUG) Log.i(TAG, "newValue: value=" + value + ", maxValue=" + mMaxValue);

        if (!TrainingApplication.isPaused()) {
            if (value != null && value.doubleValue() > mMaxValue.doubleValue()) {
                mMaxValue = value;
            }
        }
    }

    @Override
    public synchronized Number getFilteredValue() {
        if (DEBUG) Log.i(TAG, "getFilteredValue: " + mMaxValue);
        return mMaxValue;
    }
}
