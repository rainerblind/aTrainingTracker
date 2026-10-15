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

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.banalservice.filters.FilteredSensorData;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Computed metrics device that calculates real-time power-to-weight ratio (W/kg)
 * at 1Hz based on active cycling power and athlete weight (REQ-TRK-019, TST-TRK-011).
 */
public class CyclingPowerMetricsDevice extends MyDevice {

    private static final String TAG = "CyclingPowerMetricsDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor();
    private MySensor<Double> mPowerToWeightSensor;

    public interface RecordingStateProvider {
        boolean isRecordingSuspended();
    }

    public interface AthleteWeightProvider {
        float getAthleteWeightKg(Context context);
    }

    private final RecordingStateProvider mRecordingStateProvider;
    private final AthleteWeightProvider mWeightProvider;

    private static final FilterData cPowerFilter = new FilterData(null, SensorType.POWER, FilterType.MOVING_AVERAGE_TIME, 3);

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager) {
        this(context, mySensorManager, TrainingApplication::isRecordingSuspended, SettingsDataStoreJavaHelper::getAthleteWeightKg);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider) {
        super(context, mySensorManager, DeviceType.CYCLING_POWER_METRICS);
        mRecordingStateProvider = recordingStateProvider != null ? recordingStateProvider : TrainingApplication::isRecordingSuspended;
        mWeightProvider = weightProvider != null ? weightProvider : SettingsDataStoreJavaHelper::getAthleteWeightKg;
        if (DEBUG) Log.i(TAG, "CyclingPowerMetricsDevice initialized");

        try {
            BANALService.createFilter(cPowerFilter);
        } catch (Throwable t) {
            // Graceful fallback for unit tests where FilterManager is not running
        }

        registerSensors();

        mScheduler.scheduleWithFixedDelay(this::calculateMetrics, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public String getName() {
        return "Cycling power metrics";
    }

    @Override
    public synchronized void shutDown() {
        super.shutDown();
        if (mScheduler != null && !mScheduler.isShutdown()) {
            mScheduler.shutdownNow();
        }
    }

    @Override
    protected synchronized void addSensors() {
        if (DEBUG) Log.i(TAG, "addSensors()");
        mPowerToWeightSensor = new MySensor<>(this, SensorType.POWER_TO_WEIGHT);
        addSensor(mPowerToWeightSensor);
    }

    public synchronized MySensor<Double> getPowerToWeightSensor() {
        return mPowerToWeightSensor;
    }

    /**
     * Periodic 1Hz computation of power-to-weight ratio (W/kg).
     * Gated by active recording suspension (paused / auto-paused).
     */
    public synchronized void calculateMetrics() {
        if (mRecordingStateProvider.isRecordingSuspended()) {
            if (DEBUG) Log.i(TAG, "calculateMetrics(): recording suspended -> skip");
            return;
        }

        float athleteWeightKg = mWeightProvider.getAthleteWeightKg(mContext);
        if (athleteWeightKg <= 0.0f) {
            if (DEBUG) Log.i(TAG, "calculateMetrics(): athlete weight unconfigured");
            synchronized (this) {
                if (mPowerToWeightSensor != null) {
                    mPowerToWeightSensor.newValue(null);
                }
            }
            return;
        }

        // Query filtered power (3-second moving average)
        FilteredSensorData<Double> powerFiltered = null;
        try {
            powerFiltered = BANALService.getFilteredSensorData(cPowerFilter);
        } catch (Throwable t) {
            // ignore
        }
        Double power = (powerFiltered != null) ? powerFiltered.getValue() : null;

        // Fallback to raw power if filtered data is not yet available
        if (power == null && mMySensorManager != null) {
            MySensor<?> rawSensor = mMySensorManager.getSensor(SensorType.POWER);
            if (rawSensor != null && rawSensor.getSensorData() != null && rawSensor.getSensorData().getValue() != null) {
                Object val = rawSensor.getSensorData().getValue();
                if (val instanceof Number) {
                    power = ((Number) val).doubleValue();
                }
            }
        }

        if (power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0) {
            synchronized (this) {
                if (mPowerToWeightSensor != null) {
                    mPowerToWeightSensor.newValue(null);
                }
            }
            return;
        }

        double wPerKg = power / athleteWeightKg;
        synchronized (this) {
            if (mPowerToWeightSensor != null) {
                mPowerToWeightSensor.newValue(wPerKg);
            }
        }
    }
}
