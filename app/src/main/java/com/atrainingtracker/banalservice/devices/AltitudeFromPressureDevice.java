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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager;
import com.atrainingtracker.trainingtracker.elevation.ElevationResult;
import com.atrainingtracker.trainingtracker.elevation.ElevationService;
import com.atrainingtracker.trainingtracker.elevation.ElevationSource;
import com.google.android.gms.maps.model.LatLng;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// TODO: use database or preferences to store whether or not the pressure sensor is available.  really necessary??

/**
 * Driver for the internal smartphone barometric pressure sensor, providing high-precision altitude.
 *
 * This device implements barometric formula logic to derive altitude from raw pressure. It
 * features an automated "Location Learning" loop that utilizes GPS-confirmed starting
 * locations to correct the pressure sensor's baseline drift.
 *
 * Architectural Role: Internal hardware sensor driver.
 */
public class AltitudeFromPressureDevice extends MyDevice
        implements SensorEventListener {
    public static final String ALTITUDE_CORRECTION_VALUE = "com.atrainingtracker.banalservice.Devices.AltitudeFromPressureDevice.ALTITUDE_CORRECTION_VALUE";
    public static final String ALTITUDE_CORRECTION_INTENT = "com.atrainingtracker.banalservice.Devices.AltitudeFromPressureDevice.ALTITUDE_CORRECTION_INTENT";
    protected static final double MY_PRESSURE_STANDARD_ATMOSPHERE = 1013.25;
    private static final boolean DEBUG = BANALService.getDebug(false);
    protected final IntentFilter mGPSProviderEnabledFilter = new IntentFilter(BANALService.LOCATION_AVAILABLE_INTENT);
    protected MySensor<Number> mAltitudeSensor;

    protected Sensor mPressureSensor;
    protected boolean mPressureSensorRegistered = false;
    private final String TAG = "AltitudeFromPressureDev";
    private double mAltitudeCorrection = 0;
    private double mLastRawAltitude = Double.NaN;
    private boolean mPressureSensorInitialized = false;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final BroadcastReceiver mGPSProviderEnabledReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            AltitudeFromPressureDevice.this.initPressureSensor();
        }
    };

    public AltitudeFromPressureDevice(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.ALTITUDE_FROM_PRESSURE);

        mDeviceId = mDevicesDatabaseManager.getSmartphoneDeviceId(DeviceType.ALTITUDE_FROM_PRESSURE);

        SensorManager sensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mPressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (mPressureSensor != null) {
            if (DEBUG) Log.d(TAG, "Woho, we have a pressure sensor!");
            sensorManager.registerListener(this, mPressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        ContextCompat.registerReceiver(context, mGPSProviderEnabledReceiver, mGPSProviderEnabledFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public String getName() {
        return mContext.getString(R.string.devices_altitude_from_pressure);
    }

    @Override
    protected void addSensors() {
        if (DEBUG) Log.d(TAG, "addSensors");

        mAltitudeSensor = new MySensor<Number>(this, SensorType.ALTITUDE);
        addSensor(mAltitudeSensor);
    }

    @Override
    public void shutDown() {
        super.shutDown();
        mExecutor.shutdown();
        ((SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE)).unregisterListener(this);

        ContextCompat.registerReceiver(mContext, mGPSProviderEnabledReceiver, mGPSProviderEnabledFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
        mContext.unregisterReceiver(mGPSProviderEnabledReceiver);
    }


    /**
     * assumes that GPS and Pressure Sensor are ready
     */
    private void initPressureSensor() {
        if (DEBUG) Log.d(TAG, "initPressureSensor");

        if (!Double.isNaN(mLastRawAltitude)
                && mMySensorManager.getSensor(SensorType.LATITUDE) != null
                && mMySensorManager.getSensor(SensorType.LONGITUDE) != null
                && mMySensorManager.getSensor(SensorType.LATITUDE).getValue() != null
                && mMySensorManager.getSensor(SensorType.LONGITUDE).getValue() != null
                && mAltitudeSensor != null) {

            mPressureSensorInitialized = true;

            double latitude = ((Number) mMySensorManager.getSensor(SensorType.LATITUDE).getValue()).doubleValue();
            double longitude = ((Number) mMySensorManager.getSensor(SensorType.LONGITUDE).getValue()).doubleValue();
            LatLng currentLatLng = new LatLng(latitude, longitude);
            KnownLocationsDatabaseManager knownLocationsDb = KnownLocationsDatabaseManager.getInstance(mContext);
            KnownLocationsDatabaseManager.MyLocation myLocation = knownLocationsDb.getMyLocation(currentLatLng);

            if (myLocation != null) {
                if (DEBUG) Log.i(TAG, "Location found: " + myLocation.name + " (Reference Alt: " + myLocation.altitude + "m, source: " + myLocation.source + ")");
                setAltitudeCorrection(myLocation.altitude);
                mAltitudeSensor.newValue(myLocation.altitude);

                if (myLocation.source == ElevationSource.LEGACY_RAW && !myLocation.isLocked) {
                    healLocationAsync(myLocation);
                    knownLocationsDb.healLegacyLocationsAsync();
                }

                // --- ATT-1366 / REQ-DAT-007: Hit Count Tracking ---
                // Record visit frequency without mutating the established reference altitude
                knownLocationsDb.learnLocation(currentLatLng, mLastRawAltitude, ExtremaType.START);
            } else {
                fetchDemOrFallbackAsync(latitude, longitude);
            }
        }
    }

    private void healLocationAsync(@NonNull KnownLocationsDatabaseManager.MyLocation location) {
        mExecutor.execute(() -> {
            ElevationResult result = ElevationService.getInstance().fetchElevation(location.latLng.latitude, location.latLng.longitude);
            if (result instanceof ElevationResult.Success success) {
                if (DEBUG) Log.i(TAG, "Async healed location " + location.name + " to DEM elevation: " + success.getElevationMeters() + "m");
                KnownLocationsDatabaseManager db = KnownLocationsDatabaseManager.getInstance(mContext);
                KnownLocationsDatabaseManager.MyLocation current = db.getMyLocation(location.id);
                if (current != null && !current.isLocked) {
                    current.altitude = success.getElevationMeters();
                    String locationName = current.name;
                    if (com.atrainingtracker.trainingtracker.location.LocationNameResolver.isPlaceholderName(locationName)) {
                        locationName = com.atrainingtracker.trainingtracker.location.LocationNameResolver.resolveLocationNameBlocking(mContext, current.latLng.latitude, current.latLng.longitude);
                    }
                    KnownLocationsDatabaseManager.MyLocation updated = new KnownLocationsDatabaseManager.MyLocation(
                            current.id, current.latLng.latitude, current.latLng.longitude,
                            locationName, success.getElevationMeters(), current.radius, current.hitCount,
                            false, ElevationSource.INTERNET_DEM);
                    db.updateMyLocation(current.id, updated);
                    setAltitudeCorrection(success.getElevationMeters());
                }
            }
        });
    }

    private void fetchDemOrFallbackAsync(double latitude, double longitude) {
        mExecutor.execute(() -> {
            ElevationResult result = ElevationService.getInstance().fetchElevation(latitude, longitude);
            if (result instanceof ElevationResult.Success success) {
                double demAlt = success.getElevationMeters();
                if (DEBUG) Log.i(TAG, "Fetched DEM elevation: " + demAlt + "m for (" + latitude + ", " + longitude + ")");
                KnownLocationsDatabaseManager db = KnownLocationsDatabaseManager.getInstance(mContext);
                String resolvedName = com.atrainingtracker.trainingtracker.location.LocationNameResolver.resolveLocationNameBlocking(mContext, latitude, longitude);
                db.upsertLocationByGeofence(new LatLng(latitude, longitude), demAlt, resolvedName,
                        ExtremaType.START, ElevationSource.INTERNET_DEM, false);
                setAltitudeCorrection(demAlt);
            } else {
                if (DEBUG) Log.d(TAG, "DEM lookup unsuccessful (" + result + "), maintaining default/GPS fallback.");
            }
        });
    }


    /**
     * set the field mAltitudeCorrection
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void setAltitudeCorrection(double correctAltitude) {
        if (DEBUG) Log.d(TAG, "setAltitudeCorrection");

        double currentAltitude;
        if (mAltitudeSensor != null && mAltitudeSensor.getValue() != null) {
            currentAltitude = mAltitudeSensor.getValue().doubleValue();
        } else if (!Double.isNaN(mLastRawAltitude)) {
            currentAltitude = mLastRawAltitude;
        } else {
            Log.w(TAG, "Cannot set altitude correction: neither current sensor value nor last raw altitude is available.");
            return;
        }

        mAltitudeCorrection = correctAltitude - currentAltitude;

        if (mAltitudeCorrection != 0.0) {
            // 	also send broadcast to inform the others (like a tracker) of this change such that they can update all previous samples accordingly!
            Intent intent = new Intent(ALTITUDE_CORRECTION_INTENT)
                    .setPackage(mContext.getPackageName())
                    .putExtra(ALTITUDE_CORRECTION_VALUE, mAltitudeCorrection);
            mContext.sendBroadcast(intent);
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    double getAltitudeCorrection() {
        return mAltitudeCorrection;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void setLastRawAltitude(double rawAltitude) {
        mLastRawAltitude = rawAltitude;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    double getLastRawAltitude() {
        return mLastRawAltitude;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    MySensor<Number> getAltitudeSensor() {
        return mAltitudeSensor;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    boolean isPressureSensorInitialized() {
        return mPressureSensorInitialized;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    void setPressureSensorInitialized(boolean initialized) {
        mPressureSensorInitialized = initialized;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.equals(mPressureSensor)) {
            if (DEBUG) Log.d(TAG, "Accuracy of pressure sensor changed");
            // TODO do something?
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (DEBUG) Log.d(TAG, "onSensorChanged");

        if (!mPressureSensorRegistered) {
            mPressureSensorRegistered = true;

            registerSensors();
        }

        handlePressureMeasurement(event.values[0]);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    void handlePressureMeasurement(float pressureHpa) {
        mLastRawAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa);
        if (!mPressureSensorInitialized) {
            initPressureSensor();
        }
        mAltitudeSensor.newValue(mLastRawAltitude + mAltitudeCorrection);
    }

//    protected double getAltitude(double p0, double p)
//    {
//        return 44330 * (1-Math.pow((p/p0), 1/5.255));
//    }

}