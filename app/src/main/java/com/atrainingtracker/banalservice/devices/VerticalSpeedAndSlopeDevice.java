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

import static java.lang.Math.abs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.banalservice.filters.FilteredSensorData;
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.ProxySensor;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.util.LifecycleUtils;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * a device to calculate the vertical speed, slope, ascent, and descent.
 */
public class VerticalSpeedAndSlopeDevice extends MyDevice {


    private static final String TAG = "VerticalSpeedAndSlopeDevice";
    private static final Boolean DEBUG = BANALService.getDebug(false);

    private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor();

    private MySensor<Integer> mVerticalSpeedSensor;
    private MySensor<Integer> mSlopeSensor;
    private MyDoubleAccumulatorSensor mAscentSensor;
    private MyDoubleAccumulatorSensor mDescentSensor;

    private Double mLastAltitude;
    private Double mLastAltitudeSuperFiltered;
    private MyDevice mLastAltitudeSourceDevice;

    // VAM linear regression sliding window (REQ-FIL-012, ATT-1621)
    public static final int VAM_WINDOW_SIZE = 15;
    private final double[] mAltitudeHistory = new double[VAM_WINDOW_SIZE];
    private int mAltitudeHistoryCount = 0;
    private int mAltitudeHistoryHead = 0;

    // Tuning parameters
    private static final FilterData cAltitudeFilter = new FilterData(null, SensorType.ALTITUDE, FilterType.MOVING_AVERAGE_TIME, 21);
    private static final FilterData cAltitudeSuperFilter = new FilterData(null, SensorType.ALTITUDE, FilterType.MOVING_AVERAGE_TIME, 5*60);
    private static final FilterData cSpeedFilter = new FilterData(null, SensorType.SPEED_mps, FilterType.MOVING_AVERAGE_TIME, 21);
    private static final FilterData cAccuracyFilter = new FilterData(null, SensorType.ACCURACY, FilterType.INSTANTANEOUS, 1);
    private static final double MIN_SPEED = 0.5;  // min speed to calculate slope

    // GPS Accuracy and Low-Speed Wander Gating Thresholds (REQ-FIL-012, TST-FIL-004, ATT-1738)
    public static final double VAM_MAX_GPS_ACCURACY_METERS = 20.0;
    public static final double VAM_LOW_SPEED_DRIFT_THRESHOLD_MPS = 1.2;

    private final BroadcastReceiver mAltitudeCorrectionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            double delta = intent.getDoubleExtra(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE, 0.0);
            if (Math.abs(delta) < 0.1 || Math.abs(delta) > 500.0) return;
            synchronized (VerticalSpeedAndSlopeDevice.this) {
                if (mLastAltitudeSuperFiltered != null) {
                    mLastAltitudeSuperFiltered += delta;
                }
                if (mLastAltitude != null) {
                    mLastAltitude += delta;
                }
                for (int i = 0; i < mAltitudeHistoryCount; i++) {
                    mAltitudeHistory[i] += delta;
                }
                BANALService.shiftFilter(cAltitudeFilter, delta);
                BANALService.shiftFilter(cAltitudeSuperFilter, delta);
            }
        }
    };

    public VerticalSpeedAndSlopeDevice(Context context, MySensorManager mySensorManager) {
        this(context, mySensorManager, true);
    }

    @VisibleForTesting
    public VerticalSpeedAndSlopeDevice(Context context, MySensorManager mySensorManager, boolean autoStartScheduler) {
        super(context, mySensorManager, DeviceType.VERTICAL_SPEED_AND_SLOPE);
        if (DEBUG) Log.i(TAG, "VerticalSpeedAndSlopeDevice");

        BANALService.createFilter(cAltitudeFilter);
        BANALService.createFilter(cAltitudeSuperFilter);
        BANALService.createFilter(cSpeedFilter);
        BANALService.createFilter(cAccuracyFilter);

        ContextCompat.registerReceiver(mContext, mAltitudeCorrectionReceiver,
                new IntentFilter(AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        registerSensors();

        if (autoStartScheduler) {
            mScheduler.scheduleWithFixedDelay(this::calculateMetrics, 1, 1, TimeUnit.SECONDS);
        }
    }

    @Override
    public String getName() {
        return "Vertical speed and slope";
    }

    @Override
    public void shutDown() {
        super.shutDown();
        LifecycleUtils.safeUnregisterReceiver(mContext, mAltitudeCorrectionReceiver, TAG);

        if (mScheduler != null && !mScheduler.isShutdown()) {
            mScheduler.shutdown();
        }
    }

    @Override
    protected void addSensors() {
        if (DEBUG) Log.i(TAG, "addSensors()");

        mVerticalSpeedSensor = new MySensor<>(this, SensorType.VERTICAL_SPEED);
        mSlopeSensor = new MySensor<>(this, SensorType.SLOPE);
        mAscentSensor = new MyDoubleAccumulatorSensor(this, SensorType.ASCENT, true);
        mDescentSensor = new MyDoubleAccumulatorSensor(this, SensorType.DESCENT, true);

        addSensor(mVerticalSpeedSensor);
        addSensor(mSlopeSensor);
        addSensor(mAscentSensor);
        addSensor(mDescentSensor);
    }

    /**
     * Calculates the least-squares linear regression slope (in m/s) over an equidistant 1Hz altitude history.
     *
     * @param history Circular buffer of altitude samples.
     * @param count Number of valid samples in the buffer (up to VAM_WINDOW_SIZE).
     * @param headIndex Index where the next sample will be inserted.
     * @return Estimated rate of change in m/s (slope beta).
     */
    public static double calculateLinearRegressionSlope(double[] history, int count, int headIndex) {
        if (history == null || count < 2) {
            return 0.0;
        }

        int m = Math.min(count, history.length);
        // chronological order: oldest sample is at (headIndex - m + history.length) % history.length
        int startIndex = (headIndex - m + history.length) % history.length;

        double center = (m - 1) / 2.0;
        double sumNumerator = 0.0;

        for (int k = 0; k < m; k++) {
            int actualIndex = (startIndex + k) % history.length;
            double x_k = history[actualIndex];
            sumNumerator += (k - center) * x_k;
        }

        double denominator = (m * (m * m - 1.0)) / 12.0;
        if (denominator == 0.0) {
            return 0.0;
        }

        return sumNumerator / denominator;
    }

    /**
     * Backward-compatible overload resolving VAM with default acceptable accuracy (0.0m).
     */
    public static int calculateVam(double[] history, int count, int headIndex, double speedMps, double minSpeed) {
        return calculateVam(history, count, headIndex, speedMps, minSpeed, 0.0);
    }

    /**
     * Resolves the filtered, stabilized vertical speed in m/h with location accuracy gating,
     * low-speed drift damping, stationary suppression, noise deadband, and outlier boundary clamping
     * (REQ-FIL-012, TST-FIL-004, ATT-1621, ATT-1738).
     *
     * @param history Circular buffer of altitude samples.
     * @param count Number of valid samples in buffer.
     * @param headIndex Index of next insertion.
     * @param speedMps Current horizontal speed in m/s.
     * @param minSpeed Minimum horizontal speed threshold in m/s.
     * @param accuracyMeters Current GPS horizontal accuracy in meters (NaN or <= 20.0m considered acceptable).
     * @return Stabilized vertical speed in m/h.
     */
    public static int calculateVam(double[] history, int count, int headIndex, double speedMps, double minSpeed, double accuracyMeters) {
        // Location Accuracy Gating: if GPS accuracy is degraded (> 20.0m), suppress VAM noise (REQ-FIL-012, ATT-1738)
        if (!Double.isNaN(accuracyMeters) && accuracyMeters > VAM_MAX_GPS_ACCURACY_METERS) {
            return 0;
        }

        double slopeMps = calculateLinearRegressionSlope(history, count, headIndex);
        double rawVam = slopeMps * 3600.0;

        double verticalSpeed;
        if (Math.abs(speedMps) < minSpeed) {
            // Stationary athlete: suppress ambient barometric/GPS jitter (< 150 m/h)
            // but preserve genuine vertical movement (e.g. elevator, ski lift >= 150 m/h)
            if (Math.abs(rawVam) < 150.0) {
                verticalSpeed = 0.0;
            } else {
                verticalSpeed = rawVam;
            }
        } else if (Math.abs(speedMps) < VAM_LOW_SPEED_DRIFT_THRESHOLD_MPS) {
            // Low-speed GPS drift / indoor wander (0.5 <= speed < 1.2 m/s):
            // Enforce elevated deadband (< 150 m/h) to reject phantom speed jitter
            if (Math.abs(rawVam) < 150.0) {
                verticalSpeed = 0.0;
            } else {
                verticalSpeed = rawVam;
            }
        } else {
            // Moving athlete at athletic speed (>= 1.2 m/s): apply flat terrain noise deadband (< 35 m/h)
            if (Math.abs(rawVam) < 35.0) {
                verticalSpeed = 0.0;
            } else {
                verticalSpeed = rawVam;
            }
        }

        // Clamp to physical athletic bounds [-3000, 3000] m/h to reject GPS multipath step glitches
        verticalSpeed = Math.max(-3000.0, Math.min(3000.0, verticalSpeed));
        return (int) Math.round(verticalSpeed);
    }

    @VisibleForTesting
    void calculateMetrics() {
        if (DEBUG) Log.i(TAG, "calculateMetrics()");

        if (TrainingApplication.isPaused()) {
            return;
        }
        if (TrainingApplication.isAutoPaused()) {
            return;
        }

        // Check for active altitude source transition (REQ-FIL-012.5, ATT-5373)
        checkAltitudeSourceTransition();

        // get current values
        FilteredSensorData<Double> altitudeFilteredSensorData = BANALService.getFilteredSensorData(cAltitudeFilter);
        FilteredSensorData<Double> speedFilteredSensorData = BANALService.getFilteredSensorData(cSpeedFilter);
        FilteredSensorData<Double> accuracyFilteredSensorData = BANALService.getFilteredSensorData(cAccuracyFilter);

        // check if we have filtered altitude values
        if (altitudeFilteredSensorData == null || altitudeFilteredSensorData.getValue() == null) {
            if (DEBUG) Log.i(TAG, "calculateMetrics(): no filtered altitude -> return");
            return;
        }

        // update altitude history ring buffer
        double currentAltitude = altitudeFilteredSensorData.getValue();
        mAltitudeHistory[mAltitudeHistoryHead] = currentAltitude;
        mAltitudeHistoryHead = (mAltitudeHistoryHead + 1) % VAM_WINDOW_SIZE;
        if (mAltitudeHistoryCount < VAM_WINDOW_SIZE) {
            mAltitudeHistoryCount++;
        }

        double speed_mps = (speedFilteredSensorData != null && speedFilteredSensorData.getValue() != null)
                ? speedFilteredSensorData.getValue() : 0.0;
        double minSpeed = com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper.getSlopeMinSpeed(mContext);
        double accuracyMeters = (accuracyFilteredSensorData != null && accuracyFilteredSensorData.getValue() != null)
                ? accuracyFilteredSensorData.getValue() : 0.0;

        int vertical_speed = calculateVam(mAltitudeHistory, mAltitudeHistoryCount, mAltitudeHistoryHead, speed_mps, minSpeed, accuracyMeters);
        mVerticalSpeedSensor.newValue(vertical_speed);

        // calculate the slope using smoothed linear regression slope (m/s) if moving
        if (speedFilteredSensorData != null && speedFilteredSensorData.getValue() != null) {
            if (abs(speed_mps) > minSpeed) {
                double slopeMps = calculateLinearRegressionSlope(mAltitudeHistory, mAltitudeHistoryCount, mAltitudeHistoryHead);
                double slopePercentage = slopeMps / speed_mps * 100;
                if (DEBUG) Log.i(TAG, "calculateMetrics(): slopePercentage=" + slopePercentage);
                mSlopeSensor.newValue((int) Math.round(slopePercentage));
            } else {
                if (DEBUG) Log.i(TAG, "calculateMetrics(): speed is too low");
            }
        }

        // similar procedure for the ascent and descent but with the more filtered values
        altitudeFilteredSensorData = BANALService.getFilteredSensorData(cAltitudeSuperFilter);
        if (altitudeFilteredSensorData == null || altitudeFilteredSensorData.getValue() == null) {
            return;
        }

        // check if we already had a value for the filtered altitude
        if (mLastAltitudeSuperFiltered == null) {
            mLastAltitudeSuperFiltered = altitudeFilteredSensorData.getValue();
            return;
        }

        // calc the difference in altitude
        double deltaAltitude_mps = altitudeFilteredSensorData.getValue() - mLastAltitudeSuperFiltered;
        mLastAltitudeSuperFiltered = altitudeFilteredSensorData.getValue();

        // next, we can increment the ascent or descent
        if (deltaAltitude_mps > 0) {
            mAscentSensor.increment(deltaAltitude_mps);
        } else if (deltaAltitude_mps < 0) {
            mDescentSensor.increment(-deltaAltitude_mps);
        }
    }

    /**
     * Checks whether the active source device backing {@link SensorType#ALTITUDE} has transitioned
     * (e.g. from GPS ellipsoid to DEM elevation or barometric pressure) and re-seeds filters
     * to eliminate phantom historical decay and linear regression slope jumps (REQ-FIL-012.5, ATT-5373).
     */
    protected void checkAltitudeSourceTransition() {
        if (mMySensorManager == null) return;
        MySensor<?> altSensor = mMySensorManager.getSensor(SensorType.ALTITUDE);
        while (altSensor instanceof ProxySensor<?>) {
            altSensor = ((ProxySensor<?>) altSensor).getBestSensor();
        }
        if (altSensor == null) return;

        MyDevice currentDevice = altSensor.getDevice();
        if (currentDevice == null) return;

        if (mLastAltitudeSourceDevice != null && currentDevice != mLastAltitudeSourceDevice) {
            Object val = altSensor.getValue();
            if (val instanceof Number) {
                double rawAlt = ((Number) val).doubleValue();
                if (!Double.isNaN(rawAlt)) {
                    if (DEBUG) Log.i(TAG, "Altitude source transitioned from "
                            + mLastAltitudeSourceDevice.getName() + " to "
                            + currentDevice.getName() + ", rawAlt=" + rawAlt);
                    reseedAltitudeFilters(rawAlt);
                    mLastAltitudeSourceDevice = currentDevice;
                }
            }
        } else if (mLastAltitudeSourceDevice == null) {
            mLastAltitudeSourceDevice = currentDevice;
        }
    }

    /**
     * Re-seeds internal filters, baseline references, and history ring buffer with the new authoritative
     * raw altitude upon source device transition (REQ-FIL-012.5, ATT-5373).
     *
     * @param rawAlt the initial altitude sample emitted by the new active source device.
     */
    protected synchronized void reseedAltitudeFilters(double rawAlt) {
        BANALService.reseedFilter(cAltitudeFilter, rawAlt);
        BANALService.reseedFilter(cAltitudeSuperFilter, rawAlt);

        mLastAltitudeSuperFiltered = rawAlt;
        mLastAltitude = rawAlt;

        for (int i = 0; i < VAM_WINDOW_SIZE; i++) {
            mAltitudeHistory[i] = rawAlt;
        }
        mAltitudeHistoryHead = 0;
        mAltitudeHistoryCount = 0;
    }

    @VisibleForTesting
    BroadcastReceiver getAltitudeCorrectionReceiver() {
        return mAltitudeCorrectionReceiver;
    }

    @VisibleForTesting
    MySensor<Integer> getVerticalSpeedSensor() {
        return mVerticalSpeedSensor;
    }

    @VisibleForTesting
    MySensor<Integer> getSlopeSensor() {
        return mSlopeSensor;
    }

    @VisibleForTesting
    MyDoubleAccumulatorSensor getAscentSensor() {
        return mAscentSensor;
    }

    @VisibleForTesting
    MyDoubleAccumulatorSensor getDescentSensor() {
        return mDescentSensor;
    }

    @VisibleForTesting
    MyDevice getLastAltitudeSourceDevice() {
        return mLastAltitudeSourceDevice;
    }

    @VisibleForTesting
    void setLastAltitudeSourceDevice(MyDevice device) {
        mLastAltitudeSourceDevice = device;
    }

    @VisibleForTesting
    Double getLastAltitudeSuperFiltered() {
        return mLastAltitudeSuperFiltered;
    }

    @VisibleForTesting
    void setLastAltitudeSuperFiltered(Double alt) {
        mLastAltitudeSuperFiltered = alt;
    }
}
