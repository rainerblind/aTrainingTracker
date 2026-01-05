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

package com.atrainingtracker.trainingtracker.segments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.TrackOnMapBaseFragment;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Polyline;


public class SimpleSegmentOnMapFragment
        extends TrackOnMapBaseFragment {
    public static final String TAG = SimpleSegmentOnMapFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    protected Polyline mPolyline;

    protected long mSegmentID = -1;


    @NonNull
    public static SimpleSegmentOnMapFragment newInstance(long segmentId) {
        SimpleSegmentOnMapFragment simpleSegmentOnMapFragment = new SimpleSegmentOnMapFragment();

        Bundle args = new Bundle();
        args.putLong(SegmentsDatabaseManager.Segments.SEGMENT_ID, segmentId);
        simpleSegmentOnMapFragment.setArguments(args);

        return simpleSegmentOnMapFragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // lifecycle methods
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

    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // map methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMapReady(final GoogleMap map) {
        if (DEBUG) Log.i(TAG, "onMapReady");
        super.onMapReady(map);

        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(false);
        if (mSegmentID > 0) {
            showSegmentOnMap(mSegmentID, true);
        }


    }

}
