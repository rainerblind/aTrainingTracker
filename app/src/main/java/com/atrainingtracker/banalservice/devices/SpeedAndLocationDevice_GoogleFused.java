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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

public class SpeedAndLocationDevice_GoogleFused extends SpeedAndLocationDevice
        implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {
    private static final String TAG = "SpeedAndLocationDev_GF";
    private static final boolean DEBUG = BANALService.getDebug(false);
    private final Context mContext;

    protected GoogleApiClient mGoogleApiClient;

    protected LocationRequest mLocationRequest;

    protected FusedLocationProviderApi mFusedLocationApi = LocationServices.FusedLocationApi;

    private boolean mUpdatesRegistered = false;

    public SpeedAndLocationDevice_GoogleFused(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED);
        mContext = context;
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }

        // get the deviceId from the DevicesDatabaseManager
        DevicesDatabaseManager devicesDatabaseManager = DevicesDatabaseManager.getInstance(mContext);
        mDeviceId = devicesDatabaseManager.getSpeedAndLocationGoogleFusedDeviceId();

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(SAMPLING_TIME);
        mLocationRequest.setFastestInterval(SAMPLING_TIME);

        // Connect the client.
        mGoogleApiClient.connect();
    }

    /**
     * Returns whether location updates are currently registered with Google Play Services.
     *
     * @return true if location updates are active; false otherwise.
     */
    public synchronized boolean isLocationUpdatesRegistered() {
        return mUpdatesRegistered;
    }

    @Override
    public synchronized void onConnected(Bundle dataBundle) {
        if (DEBUG) Log.d(TAG, "onConnected()");

        if (mUpdatesRegistered) {
            if (DEBUG) Log.d(TAG, "onConnected: location updates already registered, skipping");
            return;
        }

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            mFusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            mUpdatesRegistered = true;
            // set last active
            setLastActive();
        } catch (Exception e) {
            Log.w(TAG, "Failed to request Google Fused location updates in onConnected: " + e.getMessage());
        }
    }


    @Override
    public synchronized void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection has been suspend");

        mUpdatesRegistered = false;
        LocationUnavailable();
    }

    @Override
    public synchronized void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient connection has failed");
        mUpdatesRegistered = false;
    }


    @Override
    public void onLocationChanged(Location location) {
        if (DEBUG) Log.d(TAG, "onLocationChanged");
        onNewLocation(location);
    }


    @Override
    public synchronized void checkOrReRegisterLocationUpdates() {
        if (mUpdatesRegistered) {
            if (DEBUG) Log.d(TAG, "checkOrReRegisterLocationUpdates: already registered, skipping");
            return;
        }
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    mFusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
                    mUpdatesRegistered = true;
                    setLastActive();
                    if (DEBUG) Log.i(TAG, "checkOrReRegisterLocationUpdates: Fused location updates registered successfully");
                } catch (Exception e) {
                    Log.w(TAG, "Failed to re-register Google Fused location updates: " + e.getMessage());
                }
            }
        } else if (mGoogleApiClient != null && !mGoogleApiClient.isConnecting()) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public synchronized void shutDown() {
        if (DEBUG) Log.d(TAG, "shutDown()");
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            try {
                mFusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                if (DEBUG) Log.i(TAG, "shutDown: Fused location updates removed successfully");
            } catch (Exception e) {
                Log.w(TAG, "Failed to remove Google Fused location updates: " + e.getMessage());
            }
        }
        mUpdatesRegistered = false;

        if (mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
        }

        super.shutDown();
    }
}