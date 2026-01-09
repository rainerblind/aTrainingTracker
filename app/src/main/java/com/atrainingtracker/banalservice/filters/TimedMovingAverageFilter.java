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

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.LinkedList;

public class TimedMovingAverageFilter
        extends MovingAverageFilter {
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static final String TAG = TimedMovingAverageFilter.class.getName();
    protected long mSeconds;
    protected LinkedList<TimestampedValue> mTimestampedValues = new LinkedList<>();

    public TimedMovingAverageFilter(String deviceName, SensorType sensorType, long seconds) {
        super(deviceName, sensorType);

        mSeconds = seconds;
    }

    @Override
    public FilterType getFilterType() {
        return FilterType.MOVING_AVERAGE_TIME;
    }

    @Override
    public double getFilterConstant() {
        return mSeconds;
    }

    @Override
    public synchronized void newValue(Number value) {
        if (!TrainingApplication.isPaused()) {
            long currentTimeMillis = System.currentTimeMillis();
            mTimestampedValues.add(new TimestampedValue(currentTimeMillis, value));
            if (DEBUG)
                Log.i(TAG, "added a new value: timestamp=" + currentTimeMillis + ", value=" + value);

            trimValues();
        }
    }

    @Override
    public synchronized Number getFilteredValue() {
        if (mTimestampedValues.size() == 0) {
            return null;
        }

        trimValues();

        double sum = 0;
        for (TimestampedValue timestampedValue : mTimestampedValues) {
            sum += timestampedValue.value == null ? 0 : timestampedValue.value.doubleValue();
        }
        return sum / mTimestampedValues.size();
    }

    protected void trimValues() {
        long threshold = System.currentTimeMillis() - 1000 * mSeconds;
        if (DEBUG) Log.i(TAG, "trimValues: mSeconds=" + mSeconds + ", threshold=" + threshold);
        while (mTimestampedValues.peek() != null
                && mTimestampedValues.peek().timestamp < threshold) {
            if (DEBUG)
                Log.i(TAG, "removing an element with timestamp=" + mTimestampedValues.peek().timestamp);
            mTimestampedValues.poll();
        }
    }

    protected class TimestampedValue {
        public long timestamp;
        public Number value;

        public TimestampedValue(long timestamp, Number value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}
