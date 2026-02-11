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

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;

public class AverageFilter extends MyFilter<Number> {
    protected double mAccumulatedValue = 0;
    protected long mNumberOfSamples = 0;

    public AverageFilter(String deviceName, SensorType sensorType) {
        super(deviceName, sensorType);
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.AVERAGE;
    }

    @Override
    public double getFilterConstant() {
        return 1;
    }

    @Override
    public synchronized void newValue(Number value) {
        if (!TrainingApplication.isPaused()) {
            mAccumulatedValue += value.doubleValue();
            mNumberOfSamples++;
        }
    }

    @Override
    public synchronized Number getFilteredValue() {
        if (mNumberOfSamples == 0) {
            return null;
        } else {
            return mAccumulatedValue / mNumberOfSamples;
        }
    }
}
