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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0/>.
 */

package com.atrainingtracker.banalservice.devices;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MySensor;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.database.ElevationCacheDatabaseManager;
import com.atrainingtracker.trainingtracker.database.RoutesDatabaseManager;
import com.atrainingtracker.trainingtracker.elevation.ElevationResult;
import com.atrainingtracker.trainingtracker.elevation.ElevationService;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;
import com.atrainingtracker.util.LifecycleUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Driver for the pairable Altitude-From-Position device (REQ-SEN-025.1).
 *
 * Resolves continuous topographic elevation from geographical coordinates backed by
 * a dedicated DEM elevation cache database (ElevationCache.db). On cache misses,
 * queries Open-Meteo DEM asynchronously via [ElevationService].
 * Strictly decoupled from KnownLocations.db (Lieblingsorte) with zero queries or writes.
 *
 * Traceability: REQ-SEN-025.1, REQ-SEN-025.2, REQ-SEN-025.3, REQ-SEN-025.4.
 */
public class AltitudeFromPositionDevice extends MyDevice {

    private static final String TAG = "AltitudeFromPosDev";
    private static final boolean DEBUG = BANALService.getDebug(false);

    protected MySensor<Number> mAltitudeSensor;
    private final ExecutorService mExecutor;
    private final ElevationCacheDatabaseManager mElevationCacheDb;
    private final ElevationService mElevationService;

    private int mLastLatE5 = Integer.MIN_VALUE;
    private int mLastLonE5 = Integer.MIN_VALUE;
    private double mLastAltitude = Double.NaN;

    private final BroadcastReceiver mLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (BANALService.NEW_LOCATION_INTENT.equals(action) || BANALService.LOCATION_AVAILABLE_INTENT.equals(action)) {
                double lat = intent.getDoubleExtra(BANALService.LATITUDE, Double.NaN);
                double lon = intent.getDoubleExtra(BANALService.LONGITUDE, Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    checkCurrentLocation();
                } else {
                    onNewLocation(lat, lon);
                }
            }
        }
    };

    public AltitudeFromPositionDevice(Context context, MySensorManager mySensorManager) {
        this(
                context,
                mySensorManager,
                ElevationCacheDatabaseManager.getInstance(context),
                ElevationService.getInstance(),
                Executors.newSingleThreadExecutor()
        );
        triggerBackgroundSeedingAsync();
    }

    @VisibleForTesting
    public AltitudeFromPositionDevice(
            @NonNull Context context,
            @NonNull MySensorManager mySensorManager,
            @NonNull ElevationCacheDatabaseManager elevationCacheDb,
            @NonNull ElevationService elevationService,
            @NonNull ExecutorService executor
    ) {
        super(context, mySensorManager, DeviceType.ALTITUDE_FROM_POSITION);

        mElevationCacheDb = elevationCacheDb;
        mElevationService = elevationService;
        mExecutor = executor;

        mDeviceId = mDevicesDatabaseManager.getAltitudeFromPositionDeviceId();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BANALService.LOCATION_AVAILABLE_INTENT);
        filter.addAction(BANALService.NEW_LOCATION_INTENT);
        ContextCompat.registerReceiver(mContext, mLocationReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        checkCurrentLocation();
    }

    @Override
    public String getName() {
        return mContext.getString(R.string.devices_altitude_from_position);
    }

    @Override
    protected void addSensors() {
        if (DEBUG) Log.d(TAG, "addSensors()");
        mAltitudeSensor = new MySensor<>(this, SensorType.ALTITUDE);
        addSensor(mAltitudeSensor);
    }

    @Override
    public void shutDown() {
        if (DEBUG) Log.i(TAG, "shutDown()");
        super.shutDown();
        mExecutor.shutdown();
        LifecycleUtils.safeUnregisterReceiver(mContext, mLocationReceiver, TAG);
        mLastLatE5 = Integer.MIN_VALUE;
        mLastLonE5 = Integer.MIN_VALUE;
        mLastAltitude = Double.NaN;
    }

    /**
     * Checks whether location sensors already have valid coordinates in MySensorManager.
     */
    public void checkCurrentLocation() {
        if (mMySensorManager == null) return;
        MySensor<?> latSensor = mMySensorManager.getSensor(SensorType.LATITUDE);
        MySensor<?> lonSensor = mMySensorManager.getSensor(SensorType.LONGITUDE);

        if (latSensor != null && lonSensor != null && latSensor.getValue() != null && lonSensor.getValue() != null) {
            try {
                double lat = ((Number) latSensor.getValue()).doubleValue();
                double lon = ((Number) lonSensor.getValue()).doubleValue();
                onNewLocation(lat, lon);
            } catch (Exception e) {
                if (DEBUG) Log.w(TAG, "Failed to read coordinates from sensor manager: " + e.getMessage());
            }
        }
    }

    /**
     * Handles incoming geographical coordinates, resolving elevation via ElevationCache.db or async DEM.
     */
    @VisibleForTesting
    public void onNewLocation(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return;
        }

        int latE5 = ElevationCacheDatabaseManager.toE5(latitude);
        int lonE5 = ElevationCacheDatabaseManager.toE5(longitude);

        if (latE5 == mLastLatE5 && lonE5 == mLastLonE5 && !Double.isNaN(mLastAltitude)) {
            // Coordinate has not shifted significantly in E5 (~1.1m) and altitude already resolved
            return;
        }

        Double cachedElevation = mElevationCacheDb.getElevation(latitude, longitude, 2);
        if (cachedElevation != null) {
            applyResolvedElevation(latE5, lonE5, cachedElevation);
        } else {
            // Cache miss: dispatch asynchronous DEM query
            fetchDemElevationAsync(latitude, longitude, latE5, lonE5);
        }
    }

    private void applyResolvedElevation(int latE5, int lonE5, double elevation) {
        mLastLatE5 = latE5;
        mLastLonE5 = lonE5;
        mLastAltitude = elevation;

        if (mAltitudeSensor != null) {
            mAltitudeSensor.newValue(elevation);
        }
        if (!sensorsRegistered()) {
            registerSensors();
        }
    }

    private void fetchDemElevationAsync(double latitude, double longitude, int latE5, int lonE5) {
        mExecutor.execute(() -> {
            try {
                ElevationResult result = mElevationService.fetchElevation(latitude, longitude);
                if (result instanceof ElevationResult.Success success) {
                    double demElevation = success.getElevationMeters();
                    if (DEBUG) Log.i(TAG, "DEM fetched: " + demElevation + "m for (" + latitude + ", " + longitude + ")");
                    mElevationCacheDb.putElevation(latitude, longitude, demElevation, "DEM_CACHE", System.currentTimeMillis());
                    applyResolvedElevation(latE5, lonE5, demElevation);
                } else {
                    if (DEBUG) Log.d(TAG, "DEM query unsuccessful: " + result);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error fetching DEM elevation for (" + latitude + ", " + longitude + "): " + e.getMessage());
            }
        });
    }

    /**
     * Asynchronously seeds ElevationCache.db from existing routes and segments in the background.
     */
    public void triggerBackgroundSeedingAsync() {
        mExecutor.execute(() -> {
            try {
                if (DEBUG) Log.i(TAG, "Starting background route & segment elevation seeding...");
                RoutesDatabaseManager routesDb = RoutesDatabaseManager.getInstance(mContext);
                int routeCount = mElevationCacheDb.seedFromRoutes(routesDb);

                SegmentsDatabaseManager segmentsDb = SegmentsDatabaseManager.getInstance(mContext);
                int segCount = mElevationCacheDb.seedFromSegments(segmentsDb);
                if (DEBUG) Log.i(TAG, "Background seeding completed: " + routeCount + " route points, " + segCount + " segment points.");
            } catch (Exception e) {
                if (DEBUG) Log.w(TAG, "Background seeding failed: " + e.getMessage());
            }
        });
    }

    @VisibleForTesting
    public MySensor<Number> getAltitudeSensor() {
        return mAltitudeSensor;
    }

    @VisibleForTesting
    public double getLastAltitude() {
        return mLastAltitude;
    }
}
