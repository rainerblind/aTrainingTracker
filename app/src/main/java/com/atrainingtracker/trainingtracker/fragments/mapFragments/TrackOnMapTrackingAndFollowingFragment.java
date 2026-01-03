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

package com.atrainingtracker.trainingtracker.fragments.mapFragments;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;


public class TrackOnMapTrackingAndFollowingFragment
        extends TrackOnMapTrackingFragment {
    public static final String TAG = TrackOnMapTrackingAndFollowingFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    private static final float ZOOM_SUPER_CLOSE_THRESHOLD = 10f / 3.6f;
    private static final float ZOOM_CLOSE_THRESHOLD = 20f / 3.6f;
    private static final float ZOOM_NORMAL_THRESHOLD = 30f / 3.6f;
    private static final float ZOOM_AWAY_THRESHOLD = 40f / 3.6f;

    private static final int ZOOM_SUPER_CLOSE = 21;
    private static final int ZOOM_CLOSE = 20;
    private static final int ZOOM_NORMAL = 19;
    private static final int ZOOM_AWAY = 18;
    private static final int ZOOM_SUPER_AWAY = 17;

    private static final int ZOOM_INIT = 19;
    private static final float ZOOM_MAX = 21;
    private static final float ZOOM_GAIN = 3.6f / 20 * 1.25f;

    private FollowMeLocationSource mFollowMeLocationSource;

    // TODO: does this really make sense when we try to get the workoutId during onResume???
    @NonNull
    public static TrackOnMapTrackingAndFollowingFragment newInstance() {
        if (DEBUG) Log.i(TAG, "newInstance");

        return new TrackOnMapTrackingAndFollowingFragment();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // lifecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

        mFollowMeLocationSource = new FollowMeLocationSource();

    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        mFollowMeLocationSource.getBestAvailableProvider();

    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // map methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMapReady(final GoogleMap map) {
        if (DEBUG) Log.i(TAG, "onMapReady");
        super.onMapReady(map);

        // mMap.setMyLocationEnabled(true); already done in the parent method

        mMap.getUiSettings().setAllGesturesEnabled(false);

        centerMapOnMyLocation(ZOOM_INIT, 90);

        mMap.setLocationSource(mFollowMeLocationSource);
    }


    /* Our custom LocationSource.
     * We register this class to receive location updates from the Location Manager
     * and for that reason we need to also implement the LocationListener interface. */
    private class FollowMeLocationSource implements LocationSource, LocationListener {

        private final Criteria criteria = new Criteria();
        /* Updates are restricted to one every halve seconds, and only when
         * movement of more than 1 meters has been detected.*/
        private static final int minTime = 500;     // minimum time interval between location updates, in milliseconds
        private static final int minDistance = 1;    // minimum distance between location updates, in meters
        @Nullable
        private OnLocationChangedListener mListener;
        private final LocationManager locationManager;
        @Nullable
        private String bestAvailableProvider;
        // private double mLatitudeFiltered, mLongitudeFiltered;
        // private float mBearingFiltered, mSpeedFiltered;
        private final CameraPosition.Builder mCameraPositionBuilder = new CameraPosition.Builder();

        private FollowMeLocationSource() {
            // Get reference to Location Manager
            locationManager = (LocationManager) getActivity().getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

            // Specify Location Provider criteria
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            // criteria.setPowerRequirement(Criteria.POWER_LOW);
            criteria.setAltitudeRequired(true);
            criteria.setBearingRequired(true);
            criteria.setSpeedRequired(true);
            criteria.setCostAllowed(true);
        }

        private void getBestAvailableProvider() {
            /* The preferred way of specifying the location provider (e.g. GPS, NETWORK) to use
             * is to ask the Location Manager for the one that best satisfies our criteria.
             * By passing the 'true' boolean we ask for the best available (enabled) provider. */
            bestAvailableProvider = locationManager.getBestProvider(criteria, true);
            if (bestAvailableProvider == null) {
                bestAvailableProvider = LocationManager.GPS_PROVIDER;
            }
        }

        /* Activates this provider. This provider will notify the supplied listener
         * periodically, until you call deactivate().
         * This method is automatically invoked by enabling my-location layer. */
        @Override
        public void activate(OnLocationChangedListener listener) {
            // We need to keep a reference to my-location layer's listener so we can push forward
            // location updates to it when we receive them from Location Manager.
            mListener = listener;

            // Request location updates from Location Manager
            if (bestAvailableProvider != null) {
                locationManager.requestLocationUpdates(bestAvailableProvider, minTime, minDistance, this);
            } else {
                // (Display a message/dialog) No Location Providers currently available.
            }
        }

        /* Deactivates this provider.
         * This method is automatically invoked by disabling my-location layer. */
        @Override
        public void deactivate() {
            // Remove location updates from Location Manager
            locationManager.removeUpdates(this);

            mListener = null;
        }

        @Override
        public void onLocationChanged(@NonNull Location location) {
            /* Push location updates to the registered listener..
             * (this ensures that my-location layer will set the blue dot at the new/received location) */
            if (mListener != null) {
                mListener.onLocationChanged(location);
            }

            /* ..and Animate camera to center on that location !
             * (the reason for we created this custom Location Source !) */
            // mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));

            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(getCameraPosition(location)));

        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }

        @NonNull
        CameraPosition getCameraPosition(@NonNull Location location) {
            // mLatitudeFiltered  = (mLatitudeFiltered  == 0 ? location.getLatitude()  : (mLatitudeFiltered  + location.getLatitude())/2);
            // mLongitudeFiltered = (mLongitudeFiltered == 0 ? location.getLongitude() : (mLongitudeFiltered + location.getLongitude())/2);
            // mBearingFiltered   = (mBearingFiltered   + location.getBearing())/2;  unfortunately, this does not work when the one is close to 0 and the other close to 360 degree
            // mSpeedFiltered     = (mSpeedFiltered     + location.getSpeed())/2;

            return mCameraPositionBuilder
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .bearing(location.getBearing())
                    .tilt(90)  //  always the highest possible tilt // mMap.getCameraPosition().tilt)
                    .zoom(speed2zoom(location.getSpeed(), mMap.getCameraPosition().zoom))
                    .build();                   // Creates a CameraPosition from the builder
        }


        protected float speed2zoom(float speed, float currentZoom) {
            if (DEBUG) Log.i(TAG, "speed2zoom(" + speed + ")");

            if (!TrainingApplication.zoomDependsOnSpeed()) {
                return currentZoom;
            } else {
                return ZOOM_MAX - speed * ZOOM_GAIN;
            }

        /*
        if (speed <= ZOOM_SUPER_CLOSE_THRESHOLD) {
            if (DEBUG) Log.i(TAG, "returning" + ZOOM_SUPER_CLOSE);
            return ZOOM_SUPER_CLOSE;
        }
        else if (speed <= ZOOM_CLOSE_THRESHOLD) {
            if (DEBUG) Log.i(TAG, "returning " + ZOOM_CLOSE);
            return ZOOM_CLOSE;
        }
        else if (speed <= ZOOM_NORMAL_THRESHOLD) {
            if (DEBUG) Log.i(TAG, "returning " + ZOOM_NORMAL);
            return ZOOM_NORMAL;
        }
        else if (speed <= ZOOM_AWAY_THRESHOLD) {
            if (DEBUG) Log.i(TAG, "returning " + ZOOM_AWAY);
            return ZOOM_AWAY;
        }
        else {
            if (DEBUG) Log.i(TAG, "returning " + ZOOM_SUPER_AWAY);
            return ZOOM_SUPER_AWAY;
        }
        */
        }
    }

}
