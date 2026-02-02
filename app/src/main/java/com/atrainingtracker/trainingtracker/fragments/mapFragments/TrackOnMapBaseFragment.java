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

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.segments.SegmentHelper;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;


public abstract class TrackOnMapBaseFragment
        extends BaseMapFragment {
    public static final String TAG = TrackOnMapBaseFragment.class.getName();
    protected static final String START_AND_FINISH_LINE_POINTS = 5 + "";
    protected static final double START_LINE_LENGTH = 15;                 // essentially only halve the length ;-)
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    protected long mWorkoutID = -1;

    private boolean mTrackOnMapLoaded = false;
    private final HashMap<Long, Boolean> mSegmentLoaded = new HashMap<>();


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // lifecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // onAttach()

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.i(TAG, "onCreate()");

        if (getArguments() != null && getArguments().containsKey(WorkoutSummaries.WORKOUT_ID)) {
            mWorkoutID = getArguments().getLong(WorkoutSummaries.WORKOUT_ID);
        }
    }

    // onActivityCreated(Bundle savedInstanceState)

    // onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.i(TAG, "onPause()");

        mTrackOnMapLoaded = false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // map methods
    ////////////////////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // helpers for map
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // was called public void createAndShowPolylineOptions()
    public void showTrackOnMap(boolean zoomToShowTrack) {
        if (DEBUG) Log.i(TAG, "showMainTrackOnMap: mWorkoutID=" + mWorkoutID);

        if (mTrackOnMapLoaded) {
            return;
        }

        ((TrainingApplication) getActivity().getApplication()).trackOnMapHelper.showTrackOnMap(getContext(), null, mMap, mWorkoutID, Roughness.ALL, TrackOnMapHelper.TrackType.BEST, zoomToShowTrack, true);
        mTrackOnMapLoaded = true;

    }


    /** helper method to check whether there is some data */
    protected boolean dataValid(@NonNull Cursor cursor, String string) {
        if (cursor.getColumnIndex(string) == -1) {
            if (DEBUG) Log.d(TAG, "dataValid: no such columnIndex!: " + string);
            return false;
        }
        if (cursor.isNull(cursor.getColumnIndex(string))) {
            if (DEBUG) Log.d(TAG, "dataValid: cursor.isNull = true for " + string);
            return false;
        }
        return true;
    }

    protected void addStartMarker(boolean zoomToStart) {
        LatLng latLngStart = getExtremaPosition(ExtremaType.START, true);
        addMarker(latLngStart, R.drawable.start_logo_map, getString(R.string.Start));

        if (zoomToStart && latLngStart != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngStart, 12));
        }
    }

    protected void addStopMarker() {
        LatLng latLngStop = getExtremaPosition(ExtremaType.END, false);
        addMarker(latLngStop, R.drawable.stop_logo_map, getString(R.string.Stop));
    }


    // helper method to get START and END position
    @Nullable
    LatLng getExtremaPosition(@NonNull ExtremaType extremaType, boolean calculateWhenNotInDb) {
        String baseFileName = WorkoutSummariesDatabaseManager.getBaseFileName(getContext(), mWorkoutID);

        Double lat = WorkoutSummariesDatabaseManager.getExtremaValue(getContext(), mWorkoutID, SensorType.LATITUDE, extremaType);
        if (lat == null && calculateWhenNotInDb) {
            lat = WorkoutSamplesDatabaseManager.calcExtremaValue(getContext(), baseFileName, extremaType, SensorType.LATITUDE);
        }
        Double lon = WorkoutSummariesDatabaseManager.getExtremaValue(getContext(), mWorkoutID, SensorType.LONGITUDE, extremaType);
        if (lon == null && calculateWhenNotInDb) {
            lon = WorkoutSamplesDatabaseManager.calcExtremaValue(getContext(), baseFileName, extremaType, SensorType.LONGITUDE);
        }

        return (lat != null & lon != null) ? new LatLng(lat, lon) : null;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // helpers for Segments
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void showStarredSegmentsOnMap(@NonNull SegmentHelper.SegmentType segmentType) {
        String selection = SegmentsDatabaseManager.Segments.ACTIVITY_TYPE + "=?";
        String[] selectionArgs = null;
        switch (segmentType) {
            case NONE:
                return; // do not search and show any segment
            case BIKE:
                selectionArgs = new String[]{SportTypeDatabaseManager.getStravaName(SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE))};
                break;
            case RUN:
                selectionArgs = new String[]{SportTypeDatabaseManager.getStravaName(SportTypeDatabaseManager.getSportTypeId(BSportType.RUN))};
                break;
            case ALL:
                // selectionArgs = null;
                selection = null;
                break;
        }

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();
        Cursor cursor = db.query(SegmentsDatabaseManager.Segments.TABLE_STARRED_SEGMENTS, null,
                selection, selectionArgs,
                null, null, null);

        while (cursor.moveToNext()) {
            showSegmentOnMap(cursor.getLong(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.SEGMENT_ID)), false);
            if (DEBUG)
                Log.i(TAG, "segmentId=" + cursor.getLong(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.SEGMENT_ID)) +
                        ", segment name=" + cursor.getString(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.SEGMENT_NAME)));
        }

        SegmentsDatabaseManager.getInstance().closeDatabase();
    }

    public void showSegmentOnMap(long segmentId, boolean zoomToShowTrack) {
        if (DEBUG) Log.i(TAG, "showSegmentOnMap: segmentId=" + segmentId);

        if (mSegmentLoaded.containsKey(segmentId) && mSegmentLoaded.get(segmentId)) {
            Log.i(TAG, "returning from showSegmentOnMap, segmentId=" + segmentId);
            return;
        }

        ((TrainingApplication) getActivity().getApplication()).segmentOnMapHelper.showSegmentOnMap(getContext(), null, mMap, segmentId, Roughness.ALL, zoomToShowTrack, true);
        mSegmentLoaded.put(segmentId, true);

        addSegmentDirectionMarkers(segmentId, true);
        addSegmentStartAndFinishLine(segmentId);
    }

    @Deprecated
    // use addSegmentDirectionMarkers instead
    protected void addSegmentStartAndFinishMarker(long segmentId, boolean zoomToStart) {
        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();
        Cursor cursor = db.query(SegmentsDatabaseManager.Segments.TABLE_STARRED_SEGMENTS, null,
                SegmentsDatabaseManager.Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""},
                null, null, null);
        if (cursor.moveToFirst()) {

            LatLng latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.START_LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.START_LONGITUDE)));

            addMarker(latLng, R.drawable.start_logo_map, getString(R.string.Start));
            if (zoomToStart) {
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12));
            }

            latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.END_LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.END_LONGITUDE)));

            addMarker(latLng, R.drawable.stop_logo_map, getString(R.string.Stop));

        }

        SegmentsDatabaseManager.getInstance().closeDatabase();
    }

    protected void addSegmentDirectionMarkers(long segmentId, boolean zoomToStart) {
        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();
        Cursor cursor = db.query(SegmentsDatabaseManager.Segments.TABLE_SEGMENT_STREAMS, null,
                SegmentsDatabaseManager.Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""},
                null, null, null);

        if (zoomToStart && cursor.moveToFirst()) {
            LatLng latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LATITUDE)), cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LONGITUDE)));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13));
        }

        while (cursor.move(2)) {
            Location startLocation = new Location("Start");
            startLocation.setLatitude(cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LATITUDE)));
            startLocation.setLongitude(cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LONGITUDE)));

            if (cursor.move(3)) {
                Location endLocation = new Location("End");
                endLocation.setLatitude(cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LATITUDE)));
                endLocation.setLongitude(cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LONGITUDE)));

                LatLng middle = new LatLng((startLocation.getLatitude() + endLocation.getLatitude()) / 2, (startLocation.getLongitude() + endLocation.getLongitude()) / 2);

                float bearing = startLocation.bearingTo(endLocation);

                Bitmap arrowhead = ((BitmapDrawable) ResourcesCompat.getDrawable(getResources(), R.drawable.arrowhead, null)).getBitmap();

                // mMap.addMarker(new MarkerOptions()
                //        .position(middle)
                //        .flat(true)
                //        .anchor((float) 0.5, (float) 0.5)
                //        .rotation(bearing)
                //        .icon(BitmapDescriptorFactory.fromBitmap(arrowheadScaled)));
                mMap.addGroundOverlay(new GroundOverlayOptions()
                        .position(middle, (float) START_LINE_LENGTH)
                        .anchor(0.5f, 0.5f)
                        .bearing(bearing)
                        .transparency(0.25f)
                        .zIndex(10)
                        // .image(BitmapDescriptorFactory.fromBitmap(arrowheadScaled)));
                        .image(BitmapDescriptorFactory.fromBitmap(arrowhead)));

                cursor.move(20);
            }
        }
    }

    protected void addSegmentStartAndFinishLine(long segmentId) {
        if (DEBUG) Log.i(TAG, "addSegmentStartAndFinishLine");

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(SegmentsDatabaseManager.Segments.TABLE_SEGMENT_STREAMS, null,
                SegmentsDatabaseManager.Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""},
                null, null,
                SegmentsDatabaseManager.Segments.DISTANCE + " ASC", START_AND_FINISH_LINE_POINTS);
        addOrthogonalLine(cursor);

        cursor = db.query(SegmentsDatabaseManager.Segments.TABLE_SEGMENT_STREAMS, null,
                SegmentsDatabaseManager.Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""},
                null, null,
                SegmentsDatabaseManager.Segments.C_ID + " DESC", START_AND_FINISH_LINE_POINTS);   // using DISTANCE does not work :-(
        addOrthogonalLine(cursor);

        SegmentsDatabaseManager.getInstance().closeDatabase();

    }

    protected void addOrthogonalLine(@NonNull Cursor cursor) {
        if (cursor.moveToFirst()) {
            if (DEBUG) Log.i(TAG, "moved to first :-)");

            double startLatitude = cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LATITUDE));
            double startLongitude = cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LONGITUDE));


            double latitudeDegreeInMeters = SegmentHelper.LatitudeDegreeInMeters(new LatLng(startLatitude, startLongitude));
            double longitudeDegreeInMeters = SegmentHelper.LongitudeDegreeInMeters(new LatLng(startLatitude, startLongitude));

            if (cursor.moveToLast()) {
                if (DEBUG) Log.i(TAG, "moved to last :-)");

                double deltaLatitude_m = (cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LATITUDE)) - startLatitude) * latitudeDegreeInMeters;
                double deltaLongitude_m = (cursor.getDouble(cursor.getColumnIndex(SegmentsDatabaseManager.Segments.LONGITUDE)) - startLongitude) * longitudeDegreeInMeters;

                double length = Math.sqrt(deltaLatitude_m * deltaLatitude_m + deltaLongitude_m * deltaLongitude_m);
                deltaLatitude_m = START_LINE_LENGTH * deltaLatitude_m / length;
                deltaLongitude_m = START_LINE_LENGTH * deltaLongitude_m / length;

                List<LatLng> latLngs = new LinkedList<>();
                latLngs.add(new LatLng(startLatitude + deltaLongitude_m / latitudeDegreeInMeters,
                        startLongitude - deltaLatitude_m / longitudeDegreeInMeters));
                latLngs.add(new LatLng(startLatitude, startLongitude));
                latLngs.add(new LatLng(startLatitude - deltaLongitude_m / latitudeDegreeInMeters,
                        startLongitude + deltaLatitude_m / longitudeDegreeInMeters));

                addPolyline(latLngs, getContext().getResources().getColor(R.color.strava));

            } else {
                Log.i(TAG, "could not move to last :-(");
            }
        } else {
            Log.i(TAG, "could not move to first :-(");
        }
    }

}
