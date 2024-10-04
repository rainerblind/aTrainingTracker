/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
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
import android.content.Intent;
import android.location.Location;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MyDoubleAccumulatorSensor;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;


public abstract class SpeedAndLocationDevice extends MyDevice {
    public static final double ACCURACY_THRESHOLD = 200;
    protected static final int SAMPLING_TIME = 1000;
    protected static final int MIN_DISTANCE = 0;
    private static final String TAG = "GPSSpeedAndLocationDevice";
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected boolean LocationAvailable = false;
    protected MySensor<Double> mLongitudeSensor;
    protected MySensor<Double> mLatitudeSensor;
    protected MySensor<Double> mAccuracySensor;
    protected MySensor<Number> mBearingSensor;
    protected MySensor<Double> mAltitudeSensor;
    protected MySensor<Double> mSpeedSensor;
    protected MySensor<Double> mLineDistanceSensor;
    protected MySensor<Double> mPaceSensor;
    protected MyDoubleAccumulatorSensor mDistanceSensor;
    protected MyDoubleAccumulatorSensor mLapDistanceSensor;
    protected Location mPrevLocation, mStartLocation = null;
    double mDistance, mSpeed;


    public SpeedAndLocationDevice(Context context, MySensorManager mySensorManager, DeviceType deviceType) {
        super(context, mySensorManager, deviceType);
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }
    }

    protected void LocationAvailable() {
        if (DEBUG) Log.d(TAG, "LocationAvailable()");

        if (!LocationAvailable) {
            LocationAvailable = true;
            registerSensors();
            mContext.sendBroadcast(new Intent(BANALService.LOCATION_AVAILABLE_INTENT)
                    .setPackage(mContext.getPackageName()));
        }
    }

    protected void LocationUnavailable() {
        if (DEBUG) Log.d(TAG, "LocationUnavailable()");

        if (LocationAvailable) {
            LocationAvailable = false;
            unregisterSensors();
            mContext.sendBroadcast(new Intent(BANALService.LOCATION_UNAVAILABLE_INTENT)
                    .setPackage(mContext.getPackageName()));
        }
    }

    @Override
    protected void addSensors() {
        mLongitudeSensor = new MySensor<Double>(this, SensorType.LONGITUDE);
        mLatitudeSensor = new MySensor<Double>(this, SensorType.LATITUDE);
        mAccuracySensor = new MySensor<Double>(this, SensorType.ACCURACY);
        mBearingSensor = new MySensor<Number>(this, SensorType.BEARING);
        mAltitudeSensor = new MySensor<Double>(this, SensorType.ALTITUDE);
        mSpeedSensor = new MySensor<Double>(this, SensorType.SPEED_mps);
        mPaceSensor = new MySensor<Double>(this, SensorType.PACE_spm);
        mLineDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.LINE_DISTANCE_m);
        mDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m);
        mLapDistanceSensor = new MyDoubleAccumulatorSensor(this, SensorType.DISTANCE_m_LAP);

        addSensor(mLongitudeSensor);
        addSensor(mLatitudeSensor);
        addSensor(mAltitudeSensor);
        addSensor(mAccuracySensor);
        addSensor(mAltitudeSensor);
        addSensor(mSpeedSensor);
        addSensor(mLineDistanceSensor);
        addSensor(mPaceSensor);
        addSensor(mDistanceSensor);
        addSensor(mLapDistanceSensor);

        mSpeedSensor.newValue(0.0);
        mPaceSensor.newValue(null);
    }

    @Override
    protected void newLap() {
        mLapDistanceSensor.reset();
    }


    public void onNewLocation(Location location) {
        if (DEBUG) Log.i(TAG, "onNewLocation()");

        if (location != null) {
            if (DEBUG)
                Log.d(TAG, "new location, provider: " + location.getProvider() + ", accuracy=" + location.getAccuracy() + ", threshold=" + ACCURACY_THRESHOLD);
            if (location.getAccuracy() <= ACCURACY_THRESHOLD) {
                LocationAvailable();

                // save the first location, i.e., the start location
                if (mStartLocation == null) {
                    mStartLocation = location;
                }

                mLongitudeSensor.newValue(location.getLongitude());
                mLatitudeSensor.newValue(location.getLatitude());
                mAccuracySensor.newValue(location.getAccuracy() + 0.0);
                mBearingSensor.newValue(location.getBearing());
                mAltitudeSensor.newValue(location.getAltitude());
                mLineDistanceSensor.newValue(location.distanceTo(mStartLocation) + 0.0);

                double speed = location.getSpeed();
                mSpeed = (mSpeed + speed) / 2;
                mSpeedSensor.newValue(mSpeed);
                mPaceSensor.newValue(1 / mSpeed);

                if (mPrevLocation != null) {
                    float delta_distance = mPrevLocation.distanceTo(location);
                    mDistance += delta_distance;

                    mDistanceSensor.newValue(mDistance);
                    mLapDistanceSensor.newValue(mDistance);
                }

                // finally, save the location
                mPrevLocation = location;

                // also broadcast that we have a new location
                Intent intent = new Intent(BANALService.NEW_LOCATION_INTENT)
                        .putExtra(BANALService.LATITUDE, location.getLatitude())
                        .putExtra(BANALService.LONGITUDE, location.getLongitude())
                        .putExtra(BANALService.LOCATION_PROVIDER, location.getProvider())
                        .setPackage(mContext.getPackageName());
                mContext.sendBroadcast(intent);
            }
        }
    }
}
