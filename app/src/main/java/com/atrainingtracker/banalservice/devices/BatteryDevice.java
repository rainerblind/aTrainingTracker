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

package com.atrainingtracker.banalservice.devices;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.sensor.formater.BatteryRemainingTimeFormatter;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Internal device for monitoring host smartphone battery percentage (PHONE_BATTERY)
 * and projecting remaining operating duration during active recording (BATTERY_REMAINING_TIME).
 */
public class BatteryDevice extends MyDevice {
    private static final String TAG = "BatteryDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    public static final int MIN_STABILIZATION_SECONDS = 300; // 5 minutes
    public static final int ROLLING_WINDOW_SECONDS = 1800;   // 30 minutes

    protected MySensor<Integer> mPhoneBatterySensor;
    protected MySensor<Integer> mBatteryRemainingTimeSensor;

    private int mBatteryLevel = -1;
    private boolean mIsCharging = false;
    private int mActiveRecordingSeconds = 0;

    static class BatterySample {
        final int activeSeconds;
        final int percent;

        BatterySample(int activeSeconds, int percent) {
            this.activeSeconds = activeSeconds;
            this.percent = percent;
        }
    }

    private final Deque<BatterySample> mSampleHistory = new ArrayDeque<>();

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                onBatteryChanged(level, scale, status);
            }
        }
    };

    private final BroadcastReceiver mTimeEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isTracking = false;
            boolean isPaused = false;
            try {
                isTracking = TrainingApplication.isTracking();
                isPaused = TrainingApplication.isPaused();
            } catch (Exception ignored) {
            }
            onTimeTick(isTracking, isPaused);
        }
    };

    public BatteryDevice(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.BATTERY);
        if (DEBUG) Log.i(TAG, "BatteryDevice initialized");

        registerSensors();

        try {
            Intent stickyIntent = ContextCompat.registerReceiver(
                    mContext,
                    mBatteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            if (stickyIntent != null) {
                int level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                onBatteryChanged(level, scale, status);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not register battery receiver: " + e.getMessage());
        }

        try {
            ContextCompat.registerReceiver(
                    mContext,
                    mTimeEventReceiver,
                    new IntentFilter(BANALService.NEW_TIME_EVENT_INTENT),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
        } catch (Exception e) {
            Log.w(TAG, "Could not register time event receiver: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return mContext.getString(R.string.phone_battery);
    }

    @Override
    protected void addSensors() {
        mPhoneBatterySensor = new MySensor<>(this, SensorType.PHONE_BATTERY);
        mBatteryRemainingTimeSensor = new MySensor<>(this, SensorType.BATTERY_REMAINING_TIME);

        addSensor(mPhoneBatterySensor);
        addSensor(mBatteryRemainingTimeSensor);
    }

    @Override
    protected void onAccumulatorsReset() {
        super.onAccumulatorsReset();
        resetDrainHistory();
    }

    public synchronized void resetDrainHistory() {
        mActiveRecordingSeconds = 0;
        mSampleHistory.clear();
        if (mBatteryLevel >= 0) {
            mSampleHistory.add(new BatterySample(0, mBatteryLevel));
        }
        updateRemainingTime();
    }

    public synchronized void onBatteryChanged(int level, int scale, int status) {
        if (level >= 0 && scale > 0) {
            int percent = Math.round((level * 100.0f) / scale);
            mBatteryLevel = Math.max(0, Math.min(100, percent));
            mPhoneBatterySensor.newValue(mBatteryLevel);
            setBatteryPercentage(mBatteryLevel);
        }
        mIsCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
        updateRemainingTime();
    }

    public synchronized void onTimeTick(boolean isTracking, boolean isPaused) {
        if (isTracking && !isPaused) {
            mActiveRecordingSeconds++;
            if (mBatteryLevel >= 0) {
                if (mSampleHistory.isEmpty()
                        || (mActiveRecordingSeconds - mSampleHistory.getLast().activeSeconds >= 30)
                        || (mSampleHistory.getLast().percent != mBatteryLevel)) {
                    mSampleHistory.addLast(new BatterySample(mActiveRecordingSeconds, mBatteryLevel));
                }
                int cutoff = mActiveRecordingSeconds - ROLLING_WINDOW_SECONDS;
                while (mSampleHistory.size() > 2 && mSampleHistory.getFirst().activeSeconds < cutoff) {
                    mSampleHistory.removeFirst();
                }
            }
        }
        updateRemainingTime();
    }

    public synchronized void onTimeTickExplicit(int activeSeconds, int batteryLevel, boolean isCharging) {
        mActiveRecordingSeconds = activeSeconds;
        mBatteryLevel = batteryLevel;
        mIsCharging = isCharging;
        mPhoneBatterySensor.newValue(batteryLevel);
        if (mSampleHistory.isEmpty()) {
            mSampleHistory.addLast(new BatterySample(0, batteryLevel));
        }
        if (mActiveRecordingSeconds > mSampleHistory.getLast().activeSeconds) {
            mSampleHistory.addLast(new BatterySample(mActiveRecordingSeconds, batteryLevel));
        }
        int cutoff = mActiveRecordingSeconds - ROLLING_WINDOW_SECONDS;
        while (mSampleHistory.size() > 2 && mSampleHistory.getFirst().activeSeconds < cutoff) {
            mSampleHistory.removeFirst();
        }
        updateRemainingTime();
    }

    private void updateRemainingTime() {
        if (mIsCharging) {
            mBatteryRemainingTimeSensor.newValue(BatteryRemainingTimeFormatter.CHARGING_STATUS_CODE);
            return;
        }

        if (mBatteryLevel < 0 || mActiveRecordingSeconds < MIN_STABILIZATION_SECONDS || mSampleHistory.size() < 2) {
            mBatteryRemainingTimeSensor.newValue(BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE);
            return;
        }

        BatterySample oldest = mSampleHistory.getFirst();
        BatterySample latest = mSampleHistory.getLast();
        int deltaSeconds = latest.activeSeconds - oldest.activeSeconds;
        int deltaPercent = oldest.percent - latest.percent;

        if (deltaSeconds < MIN_STABILIZATION_SECONDS || deltaPercent < 1) {
            mBatteryRemainingTimeSensor.newValue(BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE);
            return;
        }

        double drainRatePerHour = ((double) deltaPercent) / (deltaSeconds / 3600.0);
        if (drainRatePerHour <= 0.0) {
            mBatteryRemainingTimeSensor.newValue(BatteryRemainingTimeFormatter.STABILIZING_STATUS_CODE);
            return;
        }

        double remainingHours = mBatteryLevel / drainRatePerHour;
        int remainingSeconds = (int) Math.round(remainingHours * 3600.0);
        mBatteryRemainingTimeSensor.newValue(remainingSeconds);
    }

    public int getBatteryLevel() {
        return mBatteryLevel;
    }

    public boolean isCharging() {
        return mIsCharging;
    }

    public int getActiveRecordingSeconds() {
        return mActiveRecordingSeconds;
    }

    @Override
    public void shutDown() {
        super.shutDown();
        try {
            mContext.unregisterReceiver(mBatteryReceiver);
        } catch (Exception ignored) {
        }
        try {
            mContext.unregisterReceiver(mTimeEventReceiver);
        } catch (Exception ignored) {
        }
    }
}
