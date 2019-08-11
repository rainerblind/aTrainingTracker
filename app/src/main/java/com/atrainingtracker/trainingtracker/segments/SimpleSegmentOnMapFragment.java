package com.atrainingtracker.trainingtracker.segments;

import android.os.Bundle;
import android.util.Log;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapBaseFragment;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Polyline;


public class SimpleSegmentOnMapFragment
        extends TrackOnMapBaseFragment {
    public static final String TAG = SimpleSegmentOnMapFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    protected Polyline mPolyline;

    protected long mSegmentID = -1;

    private boolean mSegmentOnMapLoaded = false;


    public static SimpleSegmentOnMapFragment newInstance(long segmentId) {
        SimpleSegmentOnMapFragment simpleSegmentOnMapFragment = new SimpleSegmentOnMapFragment();

        Bundle args = new Bundle();
        args.putLong(SegmentsDatabaseManager.Segments.SEGMENT_ID, segmentId);
        simpleSegmentOnMapFragment.setArguments(args);

        return simpleSegmentOnMapFragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // livecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        setHasOptionsMenu(true);

        if (getArguments().containsKey(SegmentsDatabaseManager.Segments.SEGMENT_ID)) {
            mSegmentID = getArguments().getLong(SegmentsDatabaseManager.Segments.SEGMENT_ID);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        mSegmentOnMapLoaded = false;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // map methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMapReady(final GoogleMap map) {
        if (DEBUG) Log.i(TAG, "onMapReady");
        super.onMapReady(map);

        mMap.setMyLocationEnabled(false);
        if (mSegmentID > 0) {
            showSegmentOnMap(mSegmentID, true);
        }


    }

}
