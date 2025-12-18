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

// simple filter for y[k+1] = \alpha m[k] + (1-\alpha) y[k]
public class ExponentialSmoothingFilter
        extends MyFilter<Number> {
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static final String TAG = ExponentialSmoothingFilter.class.getName();

    protected double mFilteredValue;
    protected double mAlpha;

    public ExponentialSmoothingFilter(String deviceName, SensorType sensorType, double alpha) {
        super(deviceName, sensorType);

        mAlpha = alpha;
    }

    @Override
    public double getFilterConstant() {
        return mAlpha;
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.EXPONENTIAL_SMOOTHING;
    }


    @Override
    public synchronized Number getFilteredValue() {
        return mFilteredValue;
    }

    @Override
    public synchronized void newValue(Number value) {
        mFilteredValue = mAlpha * value.doubleValue() + (1 - mAlpha) * mFilteredValue;
    }
}
