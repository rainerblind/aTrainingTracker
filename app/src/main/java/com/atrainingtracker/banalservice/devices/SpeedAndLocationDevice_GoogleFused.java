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
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.sensor.MySensorManager;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
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

    public SpeedAndLocationDevice_GoogleFused(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED);
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(SAMPLING_TIME);

        // Connect the client.
        mGoogleApiClient.connect();
    }

    @Override
    public String getName() {
        return "google_fused";   // here, we do not use R.string to be compatible with the old (pre 3.8) way
    }


    @Override
    public void onConnected(Bundle dataBundle) {
        if (DEBUG) Log.d(TAG, "onConnected()");

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "GoogleApiClient connection has been suspend");

        LocationUnavailable();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient connection has failed");
    }


    @Override
    public void onLocationChanged(Location location) {
        if (DEBUG) Log.d(TAG, "onLocationChanged");
        onNewLocation(location);
    }


    @Override
    public void shutDown() {
        mGoogleApiClient.disconnect();

        super.shutDown();
    }

}