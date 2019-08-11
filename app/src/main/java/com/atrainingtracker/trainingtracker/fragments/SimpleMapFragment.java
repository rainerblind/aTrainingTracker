package com.atrainingtracker.trainingtracker.fragments;

import android.util.Log;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.BaseMapFragment;
import com.google.android.gms.maps.GoogleMap;

/**
 * Created by rainer on 01.03.16.
 */
public class SimpleMapFragment
        extends BaseMapFragment {
    public static final String TAG = SimpleMapFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & true;

    @Override
    public void onMapReady(final GoogleMap map) {
        if (DEBUG) Log.i(TAG, "onMapReady");
        super.onMapReady(map);

        mMap.setMyLocationEnabled(true);
        centerMapOnMyLocation(14, 0);
    }
}
