package com.atrainingtracker.trainingtracker.fragments.mapFragments;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;


public class TrackOnMapTrackingAndFollowingFragment2
        extends TrackOnMapTrackingFragment {
    public static final String TAG = TrackOnMapTrackingAndFollowingFragment2.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

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
    private static final float ZOOM_GAIN = 3.6f / 20;


    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    private FollowMeLocationSource mFollowMeLocationSource;

    // TODO: does this really make sense when we try to get the workoutId during onResume???
    public static TrackOnMapTrackingAndFollowingFragment2 newInstance() {
        if (DEBUG) Log.i(TAG, "newInstance");
        TrackOnMapTrackingAndFollowingFragment2 trackOnMapTrackingFragment = new TrackOnMapTrackingAndFollowingFragment2();

        return trackOnMapTrackingFragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // livecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

        mFollowMeLocationSource = new FollowMeLocationSource();
        buildGoogleApiClient(getContext());
    }

    synchronized void buildGoogleApiClient(Context context) {
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(mFollowMeLocationSource)
                .addOnConnectionFailedListener(mFollowMeLocationSource)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

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

        centerMapAt(mLastLocation, ZOOM_INIT, 90);

        mMap.setLocationSource(mFollowMeLocationSource);
    }


    /* Our custom LocationSource.
     * We register this class to receive location updates from the Location Manager
     * and for that reason we need to also implement the LocationListener interface. */
    private class FollowMeLocationSource implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            LocationSource,
            LocationListener {

        private OnLocationChangedListener mListener;
        private LocationRequest mLocationRequest;
        private double mLatitudeFiltered, mLongitudeFiltered;
        private float mBearingFiltered, mSpeedFiltered;
        private CameraPosition.Builder mCameraPositionBuilder = new CameraPosition.Builder();


        private FollowMeLocationSource() {

            mLocationRequest = LocationRequest.create();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setInterval(500); // Update location every half second
        }

        @Override
        public void onConnected(Bundle bundle) {

            // LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);

            mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            centerMapAt(mLastLocation, ZOOM_INIT, 90);

            if (mListener != null) {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            }
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {

        }

        /* Activates this provider. This provider will notify the supplied listener
         * periodically, until you call deactivate().
         * This method is automatically invoked by enabling my-location layer. */
        @Override
        public void activate(OnLocationChangedListener listener) {
            // We need to keep a reference to my-location layer's listener so we can push forward
            // location updates to it when we receive them from Location Manager.
            mListener = listener;

            if (mLastLocation != null) {
                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            }
        }

        /* Deactivates this provider.
         * This method is automatically invoked by disabling my-location layer. */
        @Override
        public void deactivate() {
            // Remove location updates from Location Manager
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);

            mListener = null;
        }

        @Override
        public void onLocationChanged(Location location) {
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

        CameraPosition getCameraPosition(Location location) {
            mLatitudeFiltered = (mLatitudeFiltered == 0 ? location.getLatitude() : (mLatitudeFiltered + location.getLatitude()) / 2);
            mLongitudeFiltered = (mLongitudeFiltered == 0 ? location.getLongitude() : (mLongitudeFiltered + location.getLongitude()) / 2);
            mBearingFiltered = (mBearingFiltered + location.getBearing()) / 2;
            mSpeedFiltered = (mSpeedFiltered + location.getSpeed()) / 2;

            return mCameraPositionBuilder
                    .target(new LatLng(mLatitudeFiltered, mLongitudeFiltered))
                    .bearing(mBearingFiltered)
                    .tilt(90)  //  always the highest possible tilt // mMap.getCameraPosition().tilt)
                    .zoom(speed2zoom(mSpeedFiltered, mMap.getCameraPosition().zoom))
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
