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
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.sensor.MySensorManager;


public class SpeedAndLocationDevice_Network extends SpeedAndLocationDevice
        implements LocationListener {
    private static final String TAG = "SpeedAndLocationDev_Net";
    private static final boolean DEBUG = BANALService.getDebug(false);

    LocationManager mLocationManager;

    public SpeedAndLocationDevice_Network(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.SPEED_AND_LOCATION_NETWORK);
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }

        // get the deviceId from the DevicesDatabaseManager
        DevicesDatabaseManager devicesDatabaseManager = DevicesDatabaseManager.getInstance(mContext);
        mDeviceId = devicesDatabaseManager.getSpeedAndLocationNetworkDeviceId();

        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, SAMPLING_TIME, MIN_DISTANCE, this);
    }


    @Override
    public void shutDown() {
        mLocationManager.removeUpdates(this);

        super.shutDown();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        if (DEBUG) Log.d(TAG, "onStatusChanged(" + provider + ", " + status + ")");
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (DEBUG) Log.d(TAG, "onProviderEnabled: " + provider);
        if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
            if (DEBUG) Log.d(TAG, "Network location provider enabled");
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, SAMPLING_TIME, MIN_DISTANCE, this);

            // set last active
            setLastActive();
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
            if (DEBUG) Log.d(TAG, "Network location provider disabled");
            mLocationManager.removeUpdates(this);
            LocationUnavailable();
        }
    }


    @Override
    public void onLocationChanged(Location location) {
        if (DEBUG) Log.i(TAG, "onLocationChanged");

        onNewLocation(location);
    }
}
