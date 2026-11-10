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

import androidx.annotation.VisibleForTesting;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.banalservice.filters.FilteredSensorData;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord;
import com.atrainingtracker.trainingtracker.database.CardioPowerTrendSample;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator;
import com.atrainingtracker.trainingtracker.physiology.SlidingWindowPowerAccumulator;
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper;

import java.util.ArrayDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Computed metrics device that calculates real-time power-to-weight ratio (W/kg),
 * Dr. Andrew Coggan's Normalized Power (NP), Intensity Factor (IF = NP / FTP),
 * Mechanical Work (kJ), Training Stress Score (TSS), and Skiba's Anaerobic Battery (W'bal in kJ)
 * at 1Hz based on active cycling power, athlete weight, FTP, CP, and W'
 * (REQ-TRK-019, REQ-TRK-020, REQ-TRK-021, REQ-TRK-023, REQ-UI-399).
 *
 * <p>State consistency, growing warm-up window (Rule 40), & pause handling:
 * NP and TSS are cumulative session metrics computed via Welford's online running mean of 4th powers.
 * During the initial warm-up phase (1 <= k < 30 active seconds) and after a long-pause window flush,
 * the rolling average is computed over the available samples (sum / window.size()) so live metrics are
 * emitted from the very first active second without a 30-second blank "--" delay.
 * Pauses gate NP/Work/TSS accumulation, while W'bal continues exponential reconstitution at P = 0 W
 * during pauses once an active effort has begun.</p>
 */
public class CyclingPowerMetricsDevice extends MyDevice {

    private static final String TAG = "CyclingPowerMetricsDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);

    /**
     * Default Critical Power (CP) fallback in Watts when neither CP nor FTP is configured.
     * Decouples the low-level BANAL device driver from the UI settings layer.
     */
    public static final int DEFAULT_FALLBACK_CP_WATTS = 200;

    private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor();
    private MySensor<Double> mPowerToWeightSensor;
    private MySensor<Double> mNormalizedPowerSensor;
    private MySensor<Double> mIntensityFactorSensor;
    private MySensor<Double> mWorkKjSensor;
    private MySensor<Double> mTssSensor;
    private MySensor<Double> mWPrimeBalSensor;
    private MySensor<Double> mPower5sSensor;
    private MySensor<Double> mPower1mSensor;
    private MySensor<Double> mPower5mSensor;
    private MySensor<Double> mPower20mSensor;
    private MySensor<Double> mPowerPeak5sSensor;
    private MySensor<Double> mPowerPeak1mSensor;
    private MySensor<Double> mPowerPeak5mSensor;
    private MySensor<Double> mPowerPeak20mSensor;
    private MySensor<Double> mLiveHrDriftSensor;

    // Sliding window accumulators for Mean Maximal Power (MMP) durations (REQ-TRK-058.1)
    private final SlidingWindowPowerAccumulator mAccumulator5s = new SlidingWindowPowerAccumulator(5);
    private final SlidingWindowPowerAccumulator mAccumulator1m = new SlidingWindowPowerAccumulator(60);
    private final SlidingWindowPowerAccumulator mAccumulator5m = new SlidingWindowPowerAccumulator(300);
    private final SlidingWindowPowerAccumulator mAccumulator20m = new SlidingWindowPowerAccumulator(1200);

    public static final double DEFAULT_FALLBACK_TAU = 40.0;
    public static final double DEFAULT_FALLBACK_HR0 = 90.0;
    public static final double DEFAULT_FALLBACK_K = 0.20;

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

    public interface CpProvider {
        Integer getCpWatts(Context context);
    }

    public interface WPrimeKjProvider {
        float getWPrimeKj(Context context);
    }

    public interface CardioPowerDynamicsEnabledProvider {
        boolean isCardioPowerDynamicsEnabled(Context context);
    }

    public interface CardioPowerBaselineProvider {
        DynamicalBaseline getBaseline(Context context);
    }

    public static class DynamicalBaseline {
        public final double tau;
        public final double hr0;
        public final double k;

        public DynamicalBaseline(double tau, double hr0, double k) {
            this.tau = tau;
            this.hr0 = hr0;
            this.k = k;
        }
    }

    private final RecordingStateProvider mRecordingStateProvider;
    private final AthleteWeightProvider mWeightProvider;
    private final FtpProvider mFtpProvider;
    private final TimeProvider mTimeProvider;
    private final CpProvider mCpProvider;
    private final WPrimeKjProvider mWPrimeKjProvider;
    private final CardioPowerDynamicsEnabledProvider mCardioPowerDynamicsEnabledProvider;
    private final CardioPowerBaselineProvider mCardioBaselineProvider;

    // Internal state for Normalized Power calculation (Welford's online running mean)
    private final ArrayDeque<Double> mPowerWindow30s = new ArrayDeque<>(30);
    private double mMean4thPower = 0.0;
    private long mCount4thPower = 0L;
    private Double mLastCalculatedNp = null;

    // Internal state for Mechanical Work, TSS, and Skiba W'bal (in Joules)
    private double mWorkJoules = 0.0;
    private long mActivePowerDurationSec = 0L;
    private Double mCurrentWPrimeBalJoules = null;

    // Internal state for Live HR Drift (REQ-REV-012 / ATT-3907)
    private Double mDriftStateX = null;
    private final ArrayDeque<Double> mDriftResidualWindow30s = new ArrayDeque<>(30);

    // Pause tracking for long-pause window flushing (> 30s)
    private boolean mWasSuspended = false;
    private long mSuspendedStartMs = 0L;

    private static final FilterData cPowerFilter = new FilterData(null, SensorType.POWER, FilterType.MOVING_AVERAGE_TIME, 3);
    private static final FilterData cHeartRateFilter = new FilterData(null, SensorType.HR, FilterType.MOVING_AVERAGE_TIME, 3);

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager) {
        this(context, mySensorManager, TrainingApplication::isRecordingSuspended, SettingsDataStoreJavaHelper::getAthleteWeightKg, SettingsDataStoreJavaHelper::getFtp, System::currentTimeMillis, null, SettingsDataStoreJavaHelper::getEffectiveWPrimeKj);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider) {
        this(context, mySensorManager, recordingStateProvider, weightProvider, SettingsDataStoreJavaHelper::getFtp, System::currentTimeMillis, null, SettingsDataStoreJavaHelper::getEffectiveWPrimeKj);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider,
                                     FtpProvider ftpProvider) {
        this(context, mySensorManager, recordingStateProvider, weightProvider, ftpProvider, System::currentTimeMillis, null, SettingsDataStoreJavaHelper::getEffectiveWPrimeKj);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider,
                                     FtpProvider ftpProvider,
                                     TimeProvider timeProvider) {
        this(context, mySensorManager, recordingStateProvider, weightProvider, ftpProvider, timeProvider, null, SettingsDataStoreJavaHelper::getEffectiveWPrimeKj);
    }

    /**
     * Constructs a new {@link CyclingPowerMetricsDevice} telemetry provider.
     * Seeds initial session state and full W'bal anaerobic work capacity parity with {@link #resetSession()}
     * immediately upon registration (Rule 51 / REQ-TRK-038.1).
     *
     * @param context App context
     * @param mySensorManager Sensor manager hosting paired sensors
     * @param recordingStateProvider Provider for active pause/suspended state
     * @param weightProvider Provider for athlete body mass in kg
     * @param ftpProvider Provider for functional threshold power (FTP)
     * @param timeProvider Provider for current wall-clock epoch time
     * @param cpProvider Provider for critical power (CP)
     * @param wPrimeKjProvider Provider for configured W' anaerobic capacity in kJ
     */
    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider,
                                     FtpProvider ftpProvider,
                                     TimeProvider timeProvider,
                                     CpProvider cpProvider,
                                     WPrimeKjProvider wPrimeKjProvider) {
        this(context, mySensorManager, recordingStateProvider, weightProvider, ftpProvider, timeProvider, cpProvider, wPrimeKjProvider, null, null);
    }

    public CyclingPowerMetricsDevice(Context context, MySensorManager mySensorManager,
                                     RecordingStateProvider recordingStateProvider,
                                     AthleteWeightProvider weightProvider,
                                     FtpProvider ftpProvider,
                                     TimeProvider timeProvider,
                                     CpProvider cpProvider,
                                     WPrimeKjProvider wPrimeKjProvider,
                                     CardioPowerDynamicsEnabledProvider cardioPowerDynamicsEnabledProvider,
                                     CardioPowerBaselineProvider cardioBaselineProvider) {
        super(context, mySensorManager, DeviceType.CYCLING_POWER_METRICS);
        mRecordingStateProvider = recordingStateProvider != null ? recordingStateProvider : TrainingApplication::isRecordingSuspended;
        mWeightProvider = weightProvider != null ? weightProvider : SettingsDataStoreJavaHelper::getAthleteWeightKg;
        mFtpProvider = ftpProvider != null ? ftpProvider : SettingsDataStoreJavaHelper::getFtp;
        mTimeProvider = timeProvider != null ? timeProvider : System::currentTimeMillis;
        mCpProvider = cpProvider != null ? cpProvider : ctx -> {
            Integer cp = SettingsDataStoreJavaHelper.getCpWatts(ctx);
            if (cp != null && cp > 0) return cp;
            return mFtpProvider.getFtp(ctx);
        };
        mWPrimeKjProvider = wPrimeKjProvider != null ? wPrimeKjProvider : SettingsDataStoreJavaHelper::getEffectiveWPrimeKj;
        mCardioPowerDynamicsEnabledProvider = cardioPowerDynamicsEnabledProvider != null
                ? cardioPowerDynamicsEnabledProvider
                : ctx -> {
                    try {
                        return SettingsDataStoreJavaHelper.getTuningConfig(ctx).getEnableCardioPowerDynamics();
                    } catch (Throwable t) {
                        return false;
                    }
                };
        mCardioBaselineProvider = cardioBaselineProvider != null
                ? cardioBaselineProvider
                : ctx -> {
                    try {
                        WorkoutSummariesDatabaseManager db = WorkoutSummariesDatabaseManager.getInstance(ctx);
                        java.util.List<CardioPowerTrendSample> samples = db.getCardioPowerDynamicsFitnessTrendSamples(0.50);
                        if (samples != null && !samples.isEmpty()) {
                            CardioPowerTrendSample latest = samples.get(samples.size() - 1);
                            if (latest != null && latest.getRecord() != null) {
                                CardioPowerDynamicsRecord rec = latest.getRecord();
                                if (rec.getTauStart() > 0.0 && rec.getKStart() > 0.0 && rec.getHr0Start() > 0.0) {
                                    return new DynamicalBaseline(rec.getTauStart(), rec.getHr0Start(), rec.getKStart());
                                }
                            }
                        }
                    } catch (Throwable t) {
                        // ignore
                    }
                    return new DynamicalBaseline(DEFAULT_FALLBACK_TAU, DEFAULT_FALLBACK_HR0, DEFAULT_FALLBACK_K);
                };
        if (DEBUG) Log.i(TAG, "CyclingPowerMetricsDevice initialized");

        try {
            BANALService.createFilter(cPowerFilter);
            BANALService.createFilter(cHeartRateFilter);
        } catch (Throwable t) {
            // Graceful fallback for unit tests where FilterManager is not running
        }

        registerSensors();

        // Seed initial session state & W'bal capacity parity with resetSession() (Rule 51 / REQ-TRK-038.1)
        resetSession();

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

        mWPrimeBalSensor = new MySensor<>(this, SensorType.W_PRIME_BAL);
        addSensor(mWPrimeBalSensor);

        mPower5sSensor = new MySensor<>(this, SensorType.POWER_5S);
        addSensor(mPower5sSensor);

        mPower1mSensor = new MySensor<>(this, SensorType.POWER_1M);
        addSensor(mPower1mSensor);

        mPower5mSensor = new MySensor<>(this, SensorType.POWER_5M);
        addSensor(mPower5mSensor);

        mPower20mSensor = new MySensor<>(this, SensorType.POWER_20M);
        addSensor(mPower20mSensor);

        mPowerPeak5sSensor = new MySensor<>(this, SensorType.POWER_PEAK_5S);
        addSensor(mPowerPeak5sSensor);

        mPowerPeak1mSensor = new MySensor<>(this, SensorType.POWER_PEAK_1M);
        addSensor(mPowerPeak1mSensor);

        mPowerPeak5mSensor = new MySensor<>(this, SensorType.POWER_PEAK_5M);
        addSensor(mPowerPeak5mSensor);

        mPowerPeak20mSensor = new MySensor<>(this, SensorType.POWER_PEAK_20M);
        addSensor(mPowerPeak20mSensor);

        mLiveHrDriftSensor = new MySensor<>(this, SensorType.LIVE_HR_DRIFT);
        addSensor(mLiveHrDriftSensor);
    }

    public synchronized MySensor<Double> getLiveHrDriftSensor() {
        return mLiveHrDriftSensor;
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

    public synchronized MySensor<Double> getWPrimeBalSensor() {
        return mWPrimeBalSensor;
    }

    public synchronized MySensor<Double> getPower5sSensor() {
        return mPower5sSensor;
    }

    public synchronized MySensor<Double> getPower1mSensor() {
        return mPower1mSensor;
    }

    public synchronized MySensor<Double> getPower5mSensor() {
        return mPower5mSensor;
    }

    public synchronized MySensor<Double> getPower20mSensor() {
        return mPower20mSensor;
    }

    public synchronized MySensor<Double> getPowerPeak5sSensor() {
        return mPowerPeak5sSensor;
    }

    public synchronized MySensor<Double> getPowerPeak1mSensor() {
        return mPowerPeak1mSensor;
    }

    public synchronized MySensor<Double> getPowerPeak5mSensor() {
        return mPowerPeak5mSensor;
    }

    public synchronized MySensor<Double> getPowerPeak20mSensor() {
        return mPowerPeak20mSensor;
    }

    public SlidingWindowPowerAccumulator getAccumulator5s() {
        return mAccumulator5s;
    }

    public SlidingWindowPowerAccumulator getAccumulator1m() {
        return mAccumulator1m;
    }

    public SlidingWindowPowerAccumulator getAccumulator5m() {
        return mAccumulator5m;
    }

    public SlidingWindowPowerAccumulator getAccumulator20m() {
        return mAccumulator20m;
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
        Integer cp = resolveEffectiveCpWatts();
        float wPrimeKj = mWPrimeKjProvider.getWPrimeKj(mContext);
        if (cp != null && cp > 0 && wPrimeKj > 0.0f) {
            mCurrentWPrimeBalJoules = wPrimeKj * 1000.0;
        } else {
            mCurrentWPrimeBalJoules = null;
        }
        mAccumulator5s.reset();
        mAccumulator1m.reset();
        mAccumulator5m.reset();
        mAccumulator20m.reset();
        if (mPower5sSensor != null) {
            mPower5sSensor.newValue(null);
        }
        if (mPower1mSensor != null) {
            mPower1mSensor.newValue(null);
        }
        if (mPower5mSensor != null) {
            mPower5mSensor.newValue(null);
        }
        if (mPower20mSensor != null) {
            mPower20mSensor.newValue(null);
        }
        if (mPowerPeak5sSensor != null) {
            mPowerPeak5sSensor.newValue(null);
        }
        if (mPowerPeak1mSensor != null) {
            mPowerPeak1mSensor.newValue(null);
        }
        if (mPowerPeak5mSensor != null) {
            mPowerPeak5mSensor.newValue(null);
        }
        if (mPowerPeak20mSensor != null) {
            mPowerPeak20mSensor.newValue(null);
        }
        if (mWPrimeBalSensor != null) {
            mWPrimeBalSensor.newValue(null);
        }
        mDriftStateX = null;
        mDriftResidualWindow30s.clear();
        if (mLiveHrDriftSensor != null) {
            mLiveHrDriftSensor.newValue(null);
        }
    }

    /**
     * Periodic 1Hz computation of power-to-weight ratio (W/kg),
     * Normalized Power (NP), Intensity Factor (IF), Mechanical Work (kJ),
     * Training Stress Score (TSS), and Anaerobic Battery (W'bal in kJ).
     * Gated by active recording suspension (paused / auto-paused), while W'bal continues
     * exponential reconstitution at 0W during pauses if an active effort has started.
     */
    public synchronized void calculateMetrics() {
        boolean isSuspended = mRecordingStateProvider.isRecordingSuspended();
        long now = mTimeProvider.currentTimeMillis();

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

        // Query filtered HR (3-second moving average) or raw HR
        FilteredSensorData<Double> hrFiltered = null;
        try {
            hrFiltered = BANALService.getFilteredSensorData(cHeartRateFilter);
        } catch (Throwable t) {
            // ignore
        }
        Double hr = (hrFiltered != null) ? hrFiltered.getValue() : null;

        // Fallback to raw HR if filtered data is not yet available
        if (hr == null && mMySensorManager != null) {
            MySensor<?> rawHrSensor = mMySensorManager.getSensor(SensorType.HR);
            if (rawHrSensor != null && rawHrSensor.getSensorData() != null && rawHrSensor.getSensorData().getValue() != null) {
                Object val = rawHrSensor.getSensorData().getValue();
                if (val instanceof Number) {
                    double d = ((Number) val).doubleValue();
                    if (d > 30.0 && d < 240.0) {
                        hr = d;
                    }
                }
            }
        }

        boolean cardioEnabled = mCardioPowerDynamicsEnabledProvider.isCardioPowerDynamicsEnabled(mContext);
        boolean hasPowerSensor = (mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null);
        boolean hasHrSensor = (mMySensorManager != null && mMySensorManager.getSensor(SensorType.HR) != null);

        if (!cardioEnabled || !hasPowerSensor || !hasHrSensor) {
            if (mLiveHrDriftSensor != null) {
                mLiveHrDriftSensor.newValue(null);
            }
        }

        float athleteWeightKg = mWeightProvider.getAthleteWeightKg(mContext);

        if (isSuspended) {
            if (!mWasSuspended) {
                mWasSuspended = true;
                mSuspendedStartMs = now;
            }

            // Stationary & Paused W/kg Alignment (REQ-TRK-038.2)
            Double wPerKg = null;
            if (athleteWeightKg > 0.0f) {
                if (power != null && power >= 0.0) {
                    wPerKg = PowerPhysiologyCalculator.calculatePowerToWeight(power, athleteWeightKg);
                } else if (power == null && mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null) {
                    wPerKg = 0.0;
                }
            }
            if (mPowerToWeightSensor != null) {
                mPowerToWeightSensor.newValue(wPerKg);
            }

            if (mActivePowerDurationSec > 0L && mCurrentWPrimeBalJoules != null) {
                Integer cp = resolveEffectiveCpWatts();
                float wPrimeKj = mWPrimeKjProvider.getWPrimeKj(mContext);
                if (cp != null && cp > 0 && wPrimeKj > 0.0f) {
                    mCurrentWPrimeBalJoules = PowerPhysiologyCalculator.INSTANCE.stepWPrimeBalanceJoules(
                            mCurrentWPrimeBalJoules,
                            0.0,
                            cp.doubleValue(),
                            wPrimeKj * 1000.0,
                            1.0
                    );
                    if (mWPrimeBalSensor != null) {
                        mWPrimeBalSensor.newValue(mCurrentWPrimeBalJoules / 1000.0);
                    }
                }
            }
            if (mLiveHrDriftSensor != null) {
                mLiveHrDriftSensor.newValue(null);
            }
            if (DEBUG) Log.i(TAG, "calculateMetrics(): recording suspended -> skip");
            return;
        }

        // Resumed or actively tracking
        if (mWasSuspended) {
            long pauseDurationMs = (mSuspendedStartMs > 0L) ? (now - mSuspendedStartMs) : 0L;
            if (pauseDurationMs > 30000L) {
                mPowerWindow30s.clear();
                mDriftResidualWindow30s.clear();
                mDriftStateX = null;
            }
            mWasSuspended = false;
            mSuspendedStartMs = 0L;
        }

        // 1. Power-to-Weight Ratio Calculation (REQ-TRK-019, REQ-MIG-039.1, REQ-TRK-038.2)
        Double wPerKg = (power != null && power >= 0.0 && athleteWeightKg > 0.0f)
                ? PowerPhysiologyCalculator.calculatePowerToWeight(power, athleteWeightKg)
                : null;
        if (mPowerToWeightSensor != null) {
            mPowerToWeightSensor.newValue(wPerKg);
        }

        // 2. Power Validation for NP, IF, Mechanical Work, TSS & W'bal (REQ-TRK-038.3)
        if (power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0) {
            // Do NOT wipe sensors to null on transient 1-second dropouts.
            // Let TrackingViewModel's 3-second stale timer govern UI staleness gracefully.
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

        // 5. Calculate Intensity Factor (IF = NP / FTP) & Training Stress Score (TSS) (REQ-MIG-039.1)
        Integer ftp = mFtpProvider.getFtp(mContext);
        if (ftp != null && ftp > 0 && mLastCalculatedNp != null) {
            Double ifVal = PowerPhysiologyCalculator.calculateIntensityFactor(mLastCalculatedNp, ftp.doubleValue());
            if (mIntensityFactorSensor != null) {
                mIntensityFactorSensor.newValue(ifVal);
            }
            Double tss = (ifVal != null)
                    ? PowerPhysiologyCalculator.calculateTss((double) mActivePowerDurationSec, mLastCalculatedNp, ifVal, ftp.doubleValue())
                    : null;
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

        // 6. Calculate Skiba W'bal (REQ-UI-399.2)
        Integer cp = resolveEffectiveCpWatts();
        float wPrimeKj = mWPrimeKjProvider.getWPrimeKj(mContext);
        if (cp != null && cp > 0 && wPrimeKj > 0.0f) {
            double wPrimeJoules = wPrimeKj * 1000.0;
            if (mCurrentWPrimeBalJoules == null) {
                mCurrentWPrimeBalJoules = wPrimeJoules;
            }
            mCurrentWPrimeBalJoules = PowerPhysiologyCalculator.INSTANCE.stepWPrimeBalanceJoules(
                    mCurrentWPrimeBalJoules,
                    power,
                    cp.doubleValue(),
                    wPrimeJoules,
                    1.0
            );
            if (mWPrimeBalSensor != null) {
                mWPrimeBalSensor.newValue(mCurrentWPrimeBalJoules / 1000.0);
            }
        } else {
            if (mWPrimeBalSensor != null) {
                mWPrimeBalSensor.newValue(null);
            }
        }

        // 7. Update Mean Maximal Power (MMP) duration accumulators & session peaks (REQ-TRK-058)
        mAccumulator5s.addSample(power);
        mAccumulator1m.addSample(power);
        mAccumulator5m.addSample(power);
        mAccumulator20m.addSample(power);

        if (mPower5sSensor != null) {
            mPower5sSensor.newValue(mAccumulator5s.getAverage());
        }
        if (mPower1mSensor != null) {
            mPower1mSensor.newValue(mAccumulator1m.getAverage());
        }
        if (mPower5mSensor != null) {
            mPower5mSensor.newValue(mAccumulator5m.getAverage());
        }
        if (mPower20mSensor != null) {
            mPower20mSensor.newValue(mAccumulator20m.getAverage());
        }

        if (mPowerPeak5sSensor != null) {
            mPowerPeak5sSensor.newValue(mAccumulator5s.getPeak());
        }
        if (mPowerPeak1mSensor != null) {
            mPowerPeak1mSensor.newValue(mAccumulator1m.getPeak());
        }
        if (mPowerPeak5mSensor != null) {
            mPowerPeak5mSensor.newValue(mAccumulator5m.getPeak());
        }
        if (mPowerPeak20mSensor != null) {
            mPowerPeak20mSensor.newValue(mAccumulator20m.getPeak());
        }

        // 8. Calculate Live HR Drift (REQ-REV-012 / ATT-3907)
        if (cardioEnabled && hasPowerSensor && hasHrSensor && hr != null) {
            DynamicalBaseline baseline = mCardioBaselineProvider.getBaseline(mContext);
            double tau = (baseline != null && baseline.tau > 0.0) ? baseline.tau : DEFAULT_FALLBACK_TAU;
            double hr0 = (baseline != null && baseline.hr0 > 0.0) ? baseline.hr0 : DEFAULT_FALLBACK_HR0;
            double k = (baseline != null && baseline.k > 0.0) ? baseline.k : DEFAULT_FALLBACK_K;

            double dt = 1.0;
            double alpha = Math.exp(-dt / tau);
            if (mDriftStateX == null) {
                mDriftStateX = power;
            } else {
                mDriftStateX = alpha * mDriftStateX + (1.0 - alpha) * power;
            }
            double hrPred = hr0 + k * mDriftStateX;
            double residual = hr - hrPred;

            if (mDriftResidualWindow30s.size() == 30) {
                mDriftResidualWindow30s.pollFirst();
            }
            mDriftResidualWindow30s.addLast(residual);

            double sumResidual = 0.0;
            for (double r : mDriftResidualWindow30s) {
                sumResidual += r;
            }
            double smoothed = sumResidual / (double) mDriftResidualWindow30s.size();
            if (mLiveHrDriftSensor != null) {
                mLiveHrDriftSensor.newValue((double) Math.round(smoothed));
            }
        }
    }

    @VisibleForTesting
    Integer resolveEffectiveCpWatts() {
        Integer cp = mCpProvider != null ? mCpProvider.getCpWatts(mContext) : null;
        if (cp != null && cp > 0) {
            return cp;
        }
        Integer ftp = mFtpProvider != null ? mFtpProvider.getFtp(mContext) : null;
        if (ftp != null && ftp > 0) {
            return ftp;
        }
        return DEFAULT_FALLBACK_CP_WATTS;
    }
}
