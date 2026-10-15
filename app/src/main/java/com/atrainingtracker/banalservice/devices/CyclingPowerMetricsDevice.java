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

import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Computed metrics device that calculates real-time power-to-weight ratio (W/kg),
 * Dr. Andrew Coggan's Normalized Power (NP), and Intensity Factor (IF = NP / FTP)
 * at 1Hz based on active cycling power, athlete weight, and FTP (REQ-TRK-019, REQ-TRK-020, TST-TRK-011, TST-TRK-012).
 */
public class CyclingPowerMetricsDevice extends MyDevice {

    private static final String TAG = "CyclingPowerMetricsDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor();
    private MySensor<Double> mPowerToWeightSensor;
    private MySensor<Double> mNormalizedPowerSensor;
    private MySensor<Double> mIntensityFactorSensor;

    public interface RecordingStateProvider {
        boolean isRecordingSuspended();
    }

    public interface AthleteWeightProvider {
        float getAthleteWeightKg(Context context);
    }

    public interface FtpProvider {
        Integer getFtp(Context context);
    }

    private final RecordingStateProvider mRecordingStateProvider;
    private final AthleteWeightProvider mWeightProvider;
    private final FtpProvider mFtpProvider;

    // Internal state for Normalized Power calculation
    private final ArrayDeque<Double> mPowerWindow30s = new ArrayDeque<>(30);
    private double mSum4thPower = 0.0;
    private long mCount4thPower = 0L;

    private static final FilterData cPowerFilter = new FilterData(null, SensorType.POWER, FilterType.MOVING_AVERAGE_TIME, 3);

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager) {
        this(context, mySensorManager, TrainingApplication::isRecordingSuspended, SettingsDataStoreJavaHelper::getAthleteWeightKg, SettingsDataStoreJavaHelper::getFtp);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider) {
        this(context, mySensorManager, recordingStateProvider, weightProvider, SettingsDataStoreJavaHelper::getFtp);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider,
                                     FtpProvider ftpProvider) {
        super(context, mySensorManager, DeviceType.CYCLING_POWER_METRICS);
        mRecordingStateProvider = recordingStateProvider != null ? recordingStateProvider : TrainingApplication::isRecordingSuspended;
        mWeightProvider = weightProvider != null ? weightProvider : SettingsDataStoreJavaHelper::getAthleteWeightKg;
        mFtpProvider = ftpProvider != null ? ftpProvider : SettingsDataStoreJavaHelper::getFtp;
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

        mNormalizedPowerSensor = new MySensor<>(this, SensorType.NORMALIZED_POWER);
        addSensor(mNormalizedPowerSensor);

        mIntensityFactorSensor = new MySensor<>(this, SensorType.INTENSITY_FACTOR);
        addSensor(mIntensityFactorSensor);
    }

    public synchronized MySensor<Double> getPowerToWeightSensor() {
        return mPowerToWeightSensor;
    }

    public synchronized MySensor<Double> getNormalizedPowerSensor() {
        return mNormalizedPowerSensor;
    }

    public synchronized MySensor<Double> getIntensityFactorSensor() {
        return mIntensityFactorSensor;
    }

    public synchronized void resetSession() {
        mPowerWindow30s.clear();
        mSum4thPower = 0.0;
        mCount4thPower = 0L;
        if (mPowerToWeightSensor != null) {
            mPowerToWeightSensor.newValue(null);
        }
        if (mNormalizedPowerSensor != null) {
            mNormalizedPowerSensor.newValue(null);
        }
        if (mIntensityFactorSensor != null) {
            mIntensityFactorSensor.newValue(null);
        }
    }

    /**
     * Periodic 1Hz computation of power-to-weight ratio (W/kg),
     * Normalized Power (NP), and Intensity Factor (IF).
     * Gated by active recording suspension (paused / auto-paused).
     */
    public synchronized void calculateMetrics() {
        if (mRecordingStateProvider.isRecordingSuspended()) {
            if (DEBUG) Log.i(TAG, "calculateMetrics(): recording suspended -> skip");
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

        // 1. Power-to-Weight Ratio Calculation (REQ-TRK-019)
        float athleteWeightKg = mWeightProvider.getAthleteWeightKg(mContext);
        if (athleteWeightKg <= 0.0f || power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0) {
            if (mPowerToWeightSensor != null) {
                mPowerToWeightSensor.newValue(null);
            }
        } else {
            double wPerKg = power / athleteWeightKg;
            if (mPowerToWeightSensor != null) {
                mPowerToWeightSensor.newValue(wPerKg);
            }
        }

        // 2. Normalized Power (NP) & Intensity Factor (IF) Calculations (REQ-TRK-020)
        if (power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0) {
            if (mNormalizedPowerSensor != null) {
                mNormalizedPowerSensor.newValue(null);
            }
            if (mIntensityFactorSensor != null) {
                mIntensityFactorSensor.newValue(null);
            }
            return;
        }

        // Ingest into 30-sample rolling FIFO window (coasting 0W is valid)
        if (mPowerWindow30s.size() == 30) {
            mPowerWindow30s.pollFirst();
        }
        mPowerWindow30s.addLast(power);

        if (mPowerWindow30s.size() < 30) {
            // Warm-up window: NP and IF are undefined until 30s have elapsed
            if (mNormalizedPowerSensor != null) {
                mNormalizedPowerSensor.newValue(null);
            }
            if (mIntensityFactorSensor != null) {
                mIntensityFactorSensor.newValue(null);
            }
            return;
        }

        // Calculate 30-second rolling average
        double sum30 = 0.0;
        for (double p : mPowerWindow30s) {
            sum30 += p;
        }
        double p30s = sum30 / 30.0;
        double p30sPow4 = Math.pow(p30s, 4.0);

        mSum4thPower += p30sPow4;
        mCount4thPower++;

        double np = Math.pow(mSum4thPower / (double) mCount4thPower, 0.25);
        if (mNormalizedPowerSensor != null) {
            mNormalizedPowerSensor.newValue(np);
        }

        // Calculate Intensity Factor (IF = NP / FTP)
        Integer ftp = mFtpProvider.getFtp(mContext);
        if (ftp != null && ftp > 0) {
            double ifVal = np / ftp.doubleValue();
            if (mIntensityFactorSensor != null) {
                mIntensityFactorSensor.newValue(ifVal);
            }
        } else {
            if (mIntensityFactorSensor != null) {
                mIntensityFactorSensor.newValue(null);
            }
        }
    }
}
