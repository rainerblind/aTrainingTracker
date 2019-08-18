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

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.SensorType;

import java.util.ArrayList;

public class NumberedMovingAverageFilter
        extends MovingAverageFilter {
    private static final boolean DEBUG = BANALService.DEBUG & false;
    private static final String TAG = NumberedMovingAverageFilter.class.getName();

    protected int mSize;
    protected int mIndex;
    protected ArrayList<Number> mValues = new ArrayList<>();

    public NumberedMovingAverageFilter(String deviceName, SensorType sensorType, int size) {
        super(deviceName, sensorType);

        mSize = size;
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.MOVING_AVERAGE_NUMBER;
    }

    @Override
    public double getFilterConstant() {
        return mSize;
    }

    @Override
    public synchronized void newValue(Number value) {
        mValues.add(mIndex, value);
        mIndex = mIndex++ % mSize;
    }

    @Override
    public synchronized Number getFilteredValue() {
        if (mValues.size() == 0) {
            return null;
        }

        double sum = 0;
        for (Number value : mValues) {
            sum += value.doubleValue();
        }
        return sum / mValues.size();
    }
}
