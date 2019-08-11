package com.atrainingtracker.banalservice.filters;

import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Sensor.SensorType;

import java.util.LinkedList;

public class TimedMovingAverageFilter
        extends MovingAverageFilter {
    private static final boolean DEBUG = BANALService.DEBUG & false;
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
        long currentTimeMillis = System.currentTimeMillis();
        mTimestampedValues.add(new TimestampedValue(currentTimeMillis, value));
        if (DEBUG)
            Log.i(TAG, "added a new value: timestamp=" + currentTimeMillis + ", value=" + value);

        trimValues();
    }

    @Override
    public synchronized Number getFilteredValue() {
        if (mTimestampedValues.size() == 0) {
            return null;
        }

        trimValues();

        double sum = 0;
        for (TimestampedValue timestampedValue : mTimestampedValues) {
            sum += timestampedValue.value.doubleValue();
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
