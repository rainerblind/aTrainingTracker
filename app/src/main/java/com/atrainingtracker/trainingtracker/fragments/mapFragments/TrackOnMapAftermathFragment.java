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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.helpers.CalcExtremaValuesTask;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.MarkerOptions;


public class TrackOnMapAftermathFragment
        extends TrackOnMapBaseFragment {
    public static final String TAG = TrackOnMapAftermathFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & false;

    private final IntentFilter mFinishedCalculatingExtremaValueFilter = new IntentFilter(CalcExtremaValuesTask.FINISHED_CALCULATING_EXTREMA_VALUE);

    // for these SensorTypes we want to add extrema markers
    protected SensorType[] mExtremaSensorTypes = {SensorType.ALTITUDE, SensorType.CADENCE, SensorType.HR, SensorType.LINE_DISTANCE_m, SensorType.POWER, SensorType.SPEED_mps, SensorType.TEMPERATURE, SensorType.TORQUE};
    BroadcastReceiver mFinishedCalculatingExtremaValueReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            SensorType sensorType = SensorType.valueOf(intent.getStringExtra(CalcExtremaValuesTask.SENSOR_TYPE));
            addExtremaMarker(sensorType);
        }
    };

    public static TrackOnMapAftermathFragment newInstance(long workoutId) {
        TrackOnMapAftermathFragment trackOnMapFragment = new TrackOnMapAftermathFragment();

        Bundle args = new Bundle();
        args.putLong(WorkoutSummaries.WORKOUT_ID, workoutId);
        trackOnMapFragment.setArguments(args);

        return trackOnMapFragment;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // lifecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        setHasOptionsMenu(true);
    }

    @Override
    public void onResume() {
        super.onResume();

        ContextCompat.registerReceiver(getActivity(), mFinishedCalculatingExtremaValueReceiver, mFinishedCalculatingExtremaValueFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // map methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();

        try {
            getActivity().unregisterReceiver(mFinishedCalculatingExtremaValueReceiver);
        } catch (Exception e) {
        }
    }

    @Override
    public void onMapReady(final GoogleMap map) {
        if (DEBUG) Log.i(TAG, "onMapReady");
        super.onMapReady(map);

        mMap.setMyLocationEnabled(false);
        if (mWorkoutID > 0) {
            showTrackOnMap(true);
            addMarkers();
        }
    }

    @Override
    public void showTrackOnMap(boolean zoomToShowMap) {
        // the super class will be called at the end in the hope that the 'best' location source is at the top (always visible)
        if (DEBUG) Log.i(TAG, "showMainTrackOnMap: mWorkoutID=" + mWorkoutID);

        if (TrainingApplication.showAllLocationSourcesOnMap()) {

            MyMapViewHolder myMapViewHolder = new MyMapViewHolder(mMap, null);

            TrackOnMapHelper trackOnMapHelper = ((TrainingApplication) getActivity().getApplication()).trackOnMapHelper;

            trackOnMapHelper.showTrackOnMap(myMapViewHolder, mWorkoutID, Roughness.ALL, TrackOnMapHelper.TrackType.GPS, false, false);
            trackOnMapHelper.showTrackOnMap(myMapViewHolder, mWorkoutID, Roughness.ALL, TrackOnMapHelper.TrackType.NETWORK, false, false);
            trackOnMapHelper.showTrackOnMap(myMapViewHolder, mWorkoutID, Roughness.ALL, TrackOnMapHelper.TrackType.FUSED, false, false);
        }

        // we always show the best track
        super.showTrackOnMap(zoomToShowMap);


        if (DEBUG) Log.i(TAG, "end of showTrackOnMap()");
    }

    // @Override
    protected void addMarkers() {
        addStartMarker(true);
        addStopMarker();

        for (SensorType sensorType : mExtremaSensorTypes) {
            addExtremaMarker(sensorType);
        }
    }

    protected void addExtremaMarker(SensorType sensorType) {
        switch (sensorType) {
            case ALTITUDE:
            case TEMPERATURE:
                addExtremaMarker(sensorType, ExtremaType.MIN, null);
                // no break because we also add the MAX value
            case CADENCE:
            case HR:
            case POWER:
            case SPEED_mps:
            case TORQUE:
                addExtremaMarker(sensorType, ExtremaType.MAX, null);
                break;

            case LINE_DISTANCE_m:
                addExtremaMarker(sensorType, ExtremaType.MAX, R.drawable.max_line_distance_logo_map);
                break;

        }
    }

    protected void addExtremaMarker(SensorType sensorType, ExtremaType extremaType, Integer drawableId) {
        if (DEBUG)
            Log.i(TAG, "addExtremaMarkerToMap for " + extremaType.name() + " " + sensorType.name());
        if (mMap == null) {
            Log.i(TAG, "mMap == null => can not add marker to map, thus returning ");
            return;
        }

        WorkoutSamplesDatabaseManager.LatLngValue latLngValue = WorkoutSamplesDatabaseManager.getExtremaPosition(mWorkoutID, sensorType, extremaType);

        if (latLngValue != null) {
            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLngValue.latLng)
                    .title(getString(R.string.location_extrema_format, extremaType.name(),
                            getString(sensorType.getFullNameId()),
                            sensorType.getMyFormatter().format(latLngValue.value),
                            getString(MyHelper.getShortUnitsId(sensorType))));
            if (drawableId != null) {
                Bitmap marker = ((BitmapDrawable) getResources().getDrawable(drawableId)).getBitmap();
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(marker));
            }
            mMap.addMarker(markerOptions);
        } else {
            if (DEBUG)
                Log.i(TAG, "unfortunately, there seems to be no " + extremaType.name() + " for " + sensorType.name() + " available.");
        }
    }


}
