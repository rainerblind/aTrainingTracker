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

import android.content.Context;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.filters.FilterData;
import com.atrainingtracker.banalservice.filters.FilterType;
import com.atrainingtracker.banalservice.filters.FilteredSensorData;
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;

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

    public VerticalSpeedAndSlopeDevice(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.VERTICAL_SPEED_AND_SLOPE);
        if (DEBUG) Log.i(TAG, "VerticalSpeedAndSlopeDevice");

        BANALService.createFilter(cAltitudeFilter);
        BANALService.createFilter(cAltitudeSuperFilter);
        BANALService.createFilter(cSpeedFilter);
        BANALService.createFilter(cAccuracyFilter);

        registerSensors();

        mScheduler.scheduleWithFixedDelay(this::calculateMetrics, 1, 1, TimeUnit.SECONDS);
    }

    @Override
    public String getName() {
        return "Vertical speed and slope";
    }

    @Override
    public void shutDown() {
        super.shutDown();

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

    private void calculateMetrics() {
        if (DEBUG) Log.i(TAG, "calculateMetrics()");

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
}
