/*
* aTrainingTracker (ANT+ BTLE)
* Copyright (C) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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
    private static final Boolean DEBUG = true;

    private final ScheduledExecutorService mScheduler = Executors.newSingleThreadScheduledExecutor();

    private MySensor<Double> mVerticalSpeedSensor;
    private MySensor<Double> mSlopeSensor;
    private MySensor<Double> mAscentSensor;
    private MySensor<Double> mDescentSensor;

    private Double mLastAltitude;
    private Double mLastAltitudeSuperFiltered;

    // Tuning parameters
    private static final FilterData cAltitudeFilter = new FilterData(null, SensorType.ALTITUDE, FilterType.MOVING_AVERAGE_TIME, 30);
    private static final FilterData cAltitudeSuperFilter = new FilterData(null, SensorType.ALTITUDE, FilterType.MOVING_AVERAGE_TIME, 5*60);
    private static final FilterData cSpeedFilter = new FilterData(null, SensorType.SPEED_mps, FilterType.MOVING_AVERAGE_TIME, 30);
    private static final double MIN_SPEED = 0.01;  // min speed to calculate slope

    public VerticalSpeedAndSlopeDevice(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.VERTICAL_SPEED_AND_SLOPE);
        if (DEBUG) Log.i(TAG, "VerticalSpeedAndSlopeDevice");

        BANALService.createFilter(cAltitudeFilter);
        BANALService.createFilter(cAltitudeSuperFilter);
        BANALService.createFilter(cSpeedFilter);

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
        mAscentSensor = new MySensor<>(this, SensorType.ASCENT);
        mDescentSensor = new MySensor<>(this, SensorType.DESCENT);

        addSensor(mVerticalSpeedSensor);
        addSensor(mSlopeSensor);
        addSensor(mAscentSensor);
        addSensor(mDescentSensor);
    }

    private void calculateMetrics() {
        if (DEBUG) Log.i(TAG, "calculateMetrics()");

        // get current values
        FilteredSensorData<Double> altitudeFilteredSensorData = BANALService.getFilteredSensorData(cAltitudeFilter);
        FilteredSensorData<Double> speedFilteredSensorData = BANALService.getFilteredSensorData(cSpeedFilter);

        // check if we have filtered altitude values
        if (altitudeFilteredSensorData == null || altitudeFilteredSensorData.getValue() == null) {
            return;
        }

        // check if we already had a value for the altitude
        if (mLastAltitude == null) {
            mLastAltitude = altitudeFilteredSensorData.getValue();
            return;
        }

        // ok, now, we are ready to do all the calculations

        // first, get the difference in altitude
        double deltaAltitude_mps = altitudeFilteredSensorData.getValue() - mLastAltitude;
        // and store the current altitude for the next time
        mLastAltitude = altitudeFilteredSensorData.getValue();

        // now, convert m/s to m/h for the vertical speed
        mVerticalSpeedSensor.newValue(deltaAltitude_mps * 60 * 60);

        // calculate the slope
        // check if we have the filtered speed
        if (speedFilteredSensorData == null || speedFilteredSensorData.getValue() == null) {
            // do nothing
        } else {
            double speed = speedFilteredSensorData.getValue();
            if (abs(speed) > MIN_SPEED) {
                mSlopeSensor.newValue(deltaAltitude_mps / speed);  // TODO: make this mathematically more correct by using arctan
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
        deltaAltitude_mps = altitudeFilteredSensorData.getValue() - mLastAltitudeSuperFiltered;
        mLastAltitudeSuperFiltered = altitudeFilteredSensorData.getValue();

        // next, we can increment the ascent or descent
        if (deltaAltitude_mps > 0) {
            mAscentSensor.newValue(mAscentSensor.getValue() == null ? deltaAltitude_mps : mAscentSensor.getValue() + deltaAltitude_mps);
        } else if (deltaAltitude_mps < 0) {
            mDescentSensor.newValue(mDescentSensor.getValue() == null ? - deltaAltitude_mps : mDescentSensor.getValue() - deltaAltitude_mps);
        }
    }
}
