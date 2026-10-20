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

package com.atrainingtracker.banalservice.filters;

import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.LinkedList;

/**
 * Moving average filter that calculates the arithmetic mean of sample values
 * received within a sliding time window (specified in seconds).
 *
 * <p>Thread Safety: All state-mutating operations and value queries are synchronized
 * on the filter instance monitor to ensure concurrent safety between background sensor
 * ingestion threads and UI telemetry queries.</p>
 *
 * <p>Null Safety: Returns {@code null} when no samples exist or all buffered samples
 * expire past the time window threshold, avoiding division by zero ({@link Double#NaN})
 * per REQ-FIL-014.2 and Rule 50.</p>
 */
public class TimedMovingAverageFilter
        extends MovingAverageFilter {
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static final String TAG = TimedMovingAverageFilter.class.getName();
    protected long mSeconds;
    protected LinkedList<TimestampedValue> mTimestampedValues = new LinkedList<>();

    /**
     * Constructs a timed moving average filter for the given device and sensor type.
     *
     * @param deviceName the name of the sensor device, or null for best/proxy sensor.
     * @param sensorType the sensor telemetry type to filter.
     * @param seconds the duration of the rolling time window in seconds.
     */
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
    public synchronized void reset() {
        mTimestampedValues.clear();
    }

    @Override
    public synchronized void newValue(Number value) {
        if (value != null) {
            long currentTimeMillis = System.currentTimeMillis();
            mTimestampedValues.add(new TimestampedValue(currentTimeMillis, value));
            if (DEBUG)
                Log.i(TAG, "added a new value: timestamp=" + currentTimeMillis + ", value=" + value);

            trimValues();
        }
    }

    /**
     * Calculates the arithmetic mean of all non-expired samples within the time window.
     *
     * <p>Purges expired samples via {@link #trimValues()} first. If all samples have aged out
     * or the buffer is empty, returns {@code null} to prevent {@code 0.0 / 0 = Double.NaN}
     * division by zero (Rule 50 / REQ-FIL-014.2).</p>
     *
     * @return the arithmetic average of current values within the time window, or {@code null}
     *         if no valid unexpired samples exist.
     */
    @Override
    public synchronized Number getFilteredValue() {
        trimValues();

        if (mTimestampedValues.isEmpty()) {
            return null;
        }

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
