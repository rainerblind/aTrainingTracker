package com.atrainingtracker.banalservice.Devices;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.banalservice.Sensor.MySensorManager;


public class SpeedAndLocationDevice_Network extends SpeedAndLocationDevice
        implements LocationListener {
    private static final String TAG = "SpeedAndLocationDevice_Network";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    LocationManager mLocationManager;

    public SpeedAndLocationDevice_Network(Context context, MySensorManager mySensorManager) {
        super(context, mySensorManager, DeviceType.SPEED_AND_LOCATION_NETWORK);
        if (DEBUG) {
            Log.d(TAG, "constructor");
        }

        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, SAMPLING_TIME, MIN_DISTANCE, this);
    }

    @Override
    public String getName() {
        return "network";   // here, we do not use R.string to be compatible with the old (pre 3.8) way
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
