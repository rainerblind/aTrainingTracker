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
 * Dr. Andrew Coggan's Normalized Power (NP), Intensity Factor (IF = NP / FTP),
 * Mechanical Work (kJ), and Training Stress Score (TSS) at 1Hz based on active cycling power,
 * athlete weight, and FTP (REQ-TRK-019, REQ-TRK-020, REQ-TRK-021, REQ-TRK-023, TST-TRK-011, TST-TRK-012, TST-TRK-013, TST-TRK-015).
 *
 * <p>State consistency, growing warm-up window (Rule 40), & pause handling:
 * NP and TSS are cumulative session metrics computed via Welford's online running mean of 4th powers.
 * During the initial warm-up phase (1 <= k < 30 active seconds) and after a long-pause window flush,
 * the rolling average is computed over the available samples (sum / window.size()) so live metrics are
 * emitted from the very first active second without a 30-second blank "--" delay.
 * Pauses gate metric accumulation. When an active pause exceeds 30 seconds, the 30-sample rolling buffer
 * is flushed to decouple physiological efforts and prevent blending pre-pause samples across long stops,
 * while retaining cumulative session accumulators (mean and active seconds) to maintain session continuity.</p>
 */
public class CyclingPowerMetricsDevice extends MyDevice {

    private static final String TAG = "CyclingPowerMetricsDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor();
    private MySensor<Double> mPowerToWeightSensor;
    private MySensor<Double> mNormalizedPowerSensor;
    private MySensor<Double> mIntensityFactorSensor;
    private MySensor<Double> mWorkKjSensor;
    private MySensor<Double> mTssSensor;

    public interface RecordingStateProvider {
        boolean isRecordingSuspended();
    }

    public interface AthleteWeightProvider {
        float getAthleteWeightKg(Context context);
    }

    public interface FtpProvider {
        Integer getFtp(Context context);
    }

    public interface TimeProvider {
        long currentTimeMillis();
    }

    private final RecordingStateProvider mRecordingStateProvider;
    private final AthleteWeightProvider mWeightProvider;
    private final FtpProvider mFtpProvider;
    private final TimeProvider mTimeProvider;

    // Internal state for Normalized Power calculation (Welford's online running mean)
    private final ArrayDeque<Double> mPowerWindow30s = new ArrayDeque<>(30);
    private double mMean4thPower = 0.0;
    private long mCount4thPower = 0L;
    private Double mLastCalculatedNp = null;

    // Internal state for Mechanical Work and TSS
    private double mWorkJoules = 0.0;
    private long mActivePowerDurationSec = 0L;

    // Pause tracking for long-pause window flushing (> 30s)
    private boolean mWasSuspended = false;
    private long mSuspendedStartMs = 0L;

    private static final FilterData cPowerFilter = new FilterData(null, SensorType.POWER, FilterType.MOVING_AVERAGE_TIME, 3);

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager) {
        this(context, mySensorManager, TrainingApplication::isRecordingSuspended, SettingsDataStoreJavaHelper::getAthleteWeightKg, SettingsDataStoreJavaHelper::getFtp, System::currentTimeMillis);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider) {
        this(context, mySensorManager, recordingStateProvider, weightProvider, SettingsDataStoreJavaHelper::getFtp, System::currentTimeMillis);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider,
                                     FtpProvider ftpProvider) {
        this(context, mySensorManager, recordingStateProvider, weightProvider, ftpProvider, System::currentTimeMillis);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider,
                                     FtpProvider ftpProvider,
                                     TimeProvider timeProvider) {
        super(context, mySensorManager, DeviceType.CYCLING_POWER_METRICS);
        mRecordingStateProvider = recordingStateProvider != null ? recordingStateProvider : TrainingApplication::isRecordingSuspended;
        mWeightProvider = weightProvider != null ? weightProvider : SettingsDataStoreJavaHelper::getAthleteWeightKg;
        mFtpProvider = ftpProvider != null ? ftpProvider : SettingsDataStoreJavaHelper::getFtp;
        mTimeProvider = timeProvider != null ? timeProvider : System::currentTimeMillis;
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

        mWorkKjSensor = new MySensor<>(this, SensorType.WORK_KJ);
        addSensor(mWorkKjSensor);

        mTssSensor = new MySensor<>(this, SensorType.TSS);
        addSensor(mTssSensor);
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

    public synchronized MySensor<Double> getWorkKjSensor() {
        return mWorkKjSensor;
    }

    public synchronized MySensor<Double> getTssSensor() {
        return mTssSensor;
    }

    public synchronized void resetSession() {
        mPowerWindow30s.clear();
        mMean4thPower = 0.0;
        mCount4thPower = 0L;
        mLastCalculatedNp = null;
        mWorkJoules = 0.0;
        mActivePowerDurationSec = 0L;
        mWasSuspended = false;
        mSuspendedStartMs = 0L;
        if (mPowerToWeightSensor != null) {
            mPowerToWeightSensor.newValue(null);
        }
        if (mNormalizedPowerSensor != null) {
            mNormalizedPowerSensor.newValue(null);
        }
        if (mIntensityFactorSensor != null) {
            mIntensityFactorSensor.newValue(null);
        }
        if (mWorkKjSensor != null) {
            mWorkKjSensor.newValue(null);
        }
        if (mTssSensor != null) {
            mTssSensor.newValue(null);
        }
    }

    /**
     * Periodic 1Hz computation of power-to-weight ratio (W/kg),
     * Normalized Power (NP), Intensity Factor (IF), Mechanical Work (kJ),
     * and Training Stress Score (TSS).
     * Gated by active recording suspension (paused / auto-paused).
     */
    public synchronized void calculateMetrics() {
        boolean isSuspended = mRecordingStateProvider.isRecordingSuspended();
        long now = mTimeProvider.currentTimeMillis();

        if (isSuspended) {
            if (!mWasSuspended) {
                mWasSuspended = true;
                mSuspendedStartMs = now;
            }
            if (DEBUG) Log.i(TAG, "calculateMetrics(): recording suspended -> skip");
            return;
        }

        // Resumed or actively tracking
        if (mWasSuspended) {
            long pauseDurationMs = (mSuspendedStartMs > 0L) ? (now - mSuspendedStartMs) : 0L;
            if (pauseDurationMs > 30000L) {
                mPowerWindow30s.clear();
            }
            mWasSuspended = false;
            mSuspendedStartMs = 0L;
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

        // 2. Power Validation for NP, IF, Mechanical Work & TSS
        if (power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0) {
            if (mNormalizedPowerSensor != null) {
                mNormalizedPowerSensor.newValue(null);
            }
            if (mIntensityFactorSensor != null) {
                mIntensityFactorSensor.newValue(null);
            }
            if (mTssSensor != null) {
                mTssSensor.newValue(null);
            }
            return;
        }

        // 3. Mechanical Work Integration (1Hz) & Active Duration Tracking (REQ-TRK-021)
        mWorkJoules += power * 1.0;
        mActivePowerDurationSec++;
        double workKj = mWorkJoules / 1000.0;
        if (mWorkKjSensor != null) {
            mWorkKjSensor.newValue(workKj);
        }

        // 4. Ingest into 30-sample rolling FIFO window (coasting 0W is valid)
        if (mPowerWindow30s.size() == 30) {
            mPowerWindow30s.pollFirst();
        }
        mPowerWindow30s.addLast(power);

        if (!mPowerWindow30s.isEmpty()) {
            // Calculate rolling average over the growing (1..29s) or full (30s) window (Rule 40 / REQ-TRK-023)
            double sum = 0.0;
            for (double p : mPowerWindow30s) {
                sum += p;
            }
            double pRolling = sum / (double) mPowerWindow30s.size();
            double pRollingPow4 = Math.pow(pRolling, 4.0);

            // Welford's online running mean of 4th powers (1 tick = 1 active second)
            mCount4thPower++;
            mMean4thPower += (pRollingPow4 - mMean4thPower) / (double) mCount4thPower;
            mLastCalculatedNp = Math.pow(mMean4thPower, 0.25);
        }

        if (mNormalizedPowerSensor != null) {
            mNormalizedPowerSensor.newValue(mLastCalculatedNp);
        }

        // 5. Calculate Intensity Factor (IF = NP / FTP) & Training Stress Score (TSS)
        Integer ftp = mFtpProvider.getFtp(mContext);
        if (ftp != null && ftp > 0 && mLastCalculatedNp != null) {
            double ifVal = mLastCalculatedNp / ftp.doubleValue();
            if (mIntensityFactorSensor != null) {
                mIntensityFactorSensor.newValue(ifVal);
            }
            double tss = (mActivePowerDurationSec * mLastCalculatedNp * ifVal) / (ftp.doubleValue() * 36.0);
            if (mTssSensor != null) {
                mTssSensor.newValue(tss);
            }
        } else {
            if (mIntensityFactorSensor != null) {
                mIntensityFactorSensor.newValue(null);
            }
            if (mTssSensor != null) {
                mTssSensor.newValue(null);
            }
        }
    }
}
