package com.atrainingtracker.trainingtracker.fragments.mapFragments;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.Tracker.TrackerService;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.segments.SegmentHelper;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;


public class TrackOnMapTrackingFragment
        extends TrackOnMapBaseFragment {
    public static final String TAG = TrackOnMapTrackingFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    private final IntentFilter mNewLocationFilter = new IntentFilter(BANALService.NEW_LOCATION_INTENT);
    private final IntentFilter mTrackingStartedFilter = new IntentFilter(TrackerService.TRACKING_STARTED_INTENT);

    protected PolylineOptions mPolylineOptions = null;
    protected Polyline mPolyline = null;
    BroadcastReceiver mNewLocationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (LocationManager.GPS_PROVIDER.equals(intent.getStringExtra(BANALService.LOCATION_PROVIDER))
                    && intent.hasExtra(BANALService.LATITUDE)
                    && intent.hasExtra(BANALService.LONGITUDE)) {
                if (getActivity() != null
                        && ((TrainingApplication) getActivity().getApplication()).isTracking()) {  // only add the received location when we are tracking
                    addLatLng(intent.getDoubleExtra(BANALService.LATITUDE, 0.0),
                            intent.getDoubleExtra(BANALService.LONGITUDE, 0.0));
                }
            }
        }
    };

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // livecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    BroadcastReceiver mTrackingStartedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mWorkoutID = intent.getLongExtra(WorkoutSummaries.WORKOUT_ID, -1);
            showTrackOnMap();
        }
    };

    // TODO: does this really make sense when we try to get the workoutId during onResume???
    public static TrackOnMapTrackingFragment newInstance() {
        if (DEBUG) Log.i(TAG, "newInstance");
        TrackOnMapTrackingFragment trackOnMapTrackingFragment = new TrackOnMapTrackingFragment();

        return trackOnMapTrackingFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate");

    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // map methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.i(TAG, "onResume()");

        mWorkoutID = ((TrainingApplication) getActivity().getApplication()).getWorkoutID();

        getActivity().registerReceiver(mNewLocationReceiver, mNewLocationFilter);
        getActivity().registerReceiver(mTrackingStartedReceiver, mTrackingStartedFilter);

        if (mMap != null && mWorkoutID > 0) {
            showTrackOnMap();
            addMarkers();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        try {
            getActivity().unregisterReceiver(mNewLocationReceiver);
        } catch (Exception e) {
        }
        try {
            getActivity().unregisterReceiver(mTrackingStartedReceiver);
        } catch (Exception e) {
        }

        mPolylineOptions = null;
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        if (DEBUG) Log.i(TAG, "onMapReady");
        super.onMapReady(map);

        if (mWorkoutID > 0) {
            showTrackOnMap();
            addMarkers();
        }

        showStarredSegmentsOnMap(SegmentHelper.SegmentType.ALL);

        if (TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)
                || TrainingApplication.havePermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            mMap.setMyLocationEnabled(true);
        }
        centerMapOnMyLocation(15, 0);
    }

    // for long long workouts this will probably be problematic?
    public void addLatLng(double latitude, double longitude) {
        if (DEBUG) Log.i(TAG, "addLatLng: lat=" + latitude + ", lng=" + longitude);

        // mMap.getCameraPosition().target.

        if (mPolylineOptions != null && mPolyline != null) {
            // mPolylineOptions.add(new LatLng(latitude, longitude));
            List<LatLng> points = mPolyline.getPoints();
            points.add(new LatLng(latitude, longitude));
            mPolyline.setPoints(points);
        } else {
            showTrackOnMap();
        }

        // mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);  // TODO: probably not the best solution: only to keep the map up to date????
    }

    // @Override
    protected void addMarkers() {
        addStartMarker(false);
    }

    // TODO: parent class has a method with the same name but a boolean argument
    protected void showTrackOnMap() {
        if (DEBUG) Log.i(TAG, "showTackOnMap");

        if (mMap == null) {
            return;
        }

        mPolylineOptions = TrackOnMapHelper.getPolylineOptions(mWorkoutID, Roughness.ALL, TrackOnMapHelper.TrackType.BEST);  // BEST is not 100% correct since we plot the GPS data, but the users probably expect this
        if (mPolylineOptions != null) {
            mPolyline = mMap.addPolyline(mPolylineOptions);

            LatLngBounds latLngBounds = TrackOnMapHelper.getLatLngBounds(mPolylineOptions);
            // if (zoomToTrack && latLngBounds != null) {
            //    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 50));
            // }
        }
    }

}
