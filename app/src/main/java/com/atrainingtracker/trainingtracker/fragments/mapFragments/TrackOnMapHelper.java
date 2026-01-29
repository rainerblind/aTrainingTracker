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
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.EnumMap;
import java.util.HashMap;

/**
 * Created by rainer on 29.03.16.
 */
public class TrackOnMapHelper {
    private static final String TAG = TrackOnMapHelper.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private final EnumMap<Roughness, EnumMap<TrackType, HashMap<Long, TrackData>>> mTrackCache = new EnumMap<>(Roughness.class);
    private final EnumMap<TrackType, HashMap<GoogleMap, Polyline>> mPolylines = new EnumMap<>(TrackType.class);

    @Nullable
    public static PolylineOptions getPolylineOptions(long workoutId, @NonNull Roughness roughness, @NonNull TrackType trackType) {
        String baseFileName = WorkoutSummariesDatabaseManager.getBaseFileName(workoutId);
        if (baseFileName == null) {
            return null;
        }

        PolylineOptions polylineOptions = new PolylineOptions()
                .color(trackType.color)
                .zIndex(5);

        WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSamplesDatabaseManager.getTableName(baseFileName),          // TODO: on some devices, an exception is thrown here
                null,
                null,
                null,
                null,
                null,
                null);  // sorting?

        LatLng latLng;

        while (cursor.move(roughness.stepSize)) {
            if (dataValid(cursor, trackType.getLatitudeName()) && dataValid(cursor, trackType.getLongitudeName())) {
                // get latitude and longitude from the database and create new LatLng object
                latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex(trackType.getLatitudeName())),
                        cursor.getDouble(cursor.getColumnIndex(trackType.getLongitudeName())));
                polylineOptions.add(latLng);
            }
        }

        cursor.close();
        databaseManager.closeDatabase(); // db.close();

        return polylineOptions;
    }

    @Nullable
    public static LatLngBounds getLatLngBounds(@NonNull PolylineOptions polylineOptions) {
        if (DEBUG) Log.i(TAG, "getLatLngBounds");

        LatLngBounds.Builder latLngBoundsBuilder = new LatLngBounds.Builder();
        boolean havePoints = false;
        for (LatLng latLng : polylineOptions.getPoints()) {
            if (DEBUG) Log.i(TAG, latLng.toString());
            havePoints = true;
            latLngBoundsBuilder.include(latLng);
        }
        if (DEBUG) Log.i(TAG, "havePoints=" + havePoints);
        return havePoints ? latLngBoundsBuilder.build() : null;
    }

    // TODO: this is stolen several times, so make it a static method of a DatabaseHelper Class
    protected static boolean dataValid(@NonNull Cursor cursor, String string) {
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

    public void showTrackOnMap(MapView mapView, GoogleMap map, long workoutId, @NonNull Roughness roughness, @NonNull TrackType trackType, boolean zoomToMap, boolean animateZoom) {
        if (DEBUG)
            Log.i(TAG, "showTrackOnMap for workoutId=" + workoutId + ", roughness=" + roughness.name() + ", trackType=" + trackType.name());

        Roughness roughness_tmp = roughness;

        TrackData trackData = getCachedTrackData(workoutId, roughness_tmp, trackType);
        boolean calcTrackData = true;
        if (trackData != null) {
            calcTrackData = false;
        } else {
            roughness_tmp = Roughness.MEDIUM;
            trackData = getCachedTrackData(workoutId, roughness_tmp, trackType);
            calcTrackData = true;
        }

        if (trackData != null) {
            plotTrackOnMap(mapView, map, workoutId, roughness_tmp, trackType, zoomToMap, animateZoom);
        }

        if (calcTrackData) {
            new TrackOnMapThread(mapView, map, workoutId, roughness, trackType, zoomToMap, animateZoom).start();
        }
    }

    private void plotTrackOnMap(MapView mapView, @NonNull final GoogleMap map, long workoutId, @NonNull Roughness roughness, @NonNull TrackType trackType, boolean zoomToMap, final boolean animateZoom) {
        if (DEBUG)
            Log.i(TAG, "plotTrackOnMap for workoutId=" + workoutId + ", roughness=" + roughness.name() + ", trackType=" + trackType.name());

        if (mPolylines.containsKey(trackType) &&
                mPolylines.get(trackType).containsKey(map)) {
            mPolylines.get(trackType).get(map).remove();
        }

        final TrackData trackData = getCachedTrackData(workoutId, roughness, trackType);
        if (DEBUG) Log.i(TAG, "trackData=" + trackData);
        if (trackData == null                                  // when there is no data
                & mapView != null) {       // and it is 'only' an embedded MapView
            mapView.setVisibility(View.GONE);  // we do not show the MapView
            return;
        } else if (trackData == null) {
            // TODO: is this the right solution?
            return;
        }

        Polyline polyline = map.addPolyline(trackData.polylineOptions);

        if (!mPolylines.containsKey(trackType)) {
            mPolylines.put(trackType, new HashMap<>());
        }
        mPolylines.get(trackType).put(map, polyline);

        if (zoomToMap) {
            map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    if (animateZoom) {
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(trackData.latLngBounds, 50));
                    } else {
                        map.moveCamera(CameraUpdateFactory.newLatLngBounds(trackData.latLngBounds, 50));
                    }
                }
            });
        }

        map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
    }

    @Nullable
    private TrackData getCachedTrackData(long workoutId, @NonNull Roughness roughness, @NonNull TrackType trackType) {
        if (DEBUG)
            Log.i(TAG, "getCachedTrackData for workoutId=" + workoutId + ", roughness=" + roughness.name() + ", trackType=" + trackType.name());

        TrackData trackData = null;

        if (mTrackCache.containsKey(roughness)
                && mTrackCache.get(roughness).containsKey(trackType)
                && mTrackCache.get(roughness).get(trackType).containsKey(workoutId)) {
            trackData = mTrackCache.get(roughness).get(trackType).get(workoutId);
        }

        if (DEBUG) Log.i(TAG, "trackData=" + trackData);

        return trackData;
    }

    private void calcTrackData(long workoutId, @NonNull Roughness roughness, @NonNull TrackType trackType) {
        if (DEBUG)
            Log.i(TAG, "calcTrackData for workoutId=" + workoutId + ", roughness=" + roughness.name() + ", trackType=" + trackType.name());
        if (DEBUG) Log.i(TAG, "sensorTypeLatitude=" + trackType.getLatitudeName());

        PolylineOptions polylineOptions = getPolylineOptions(workoutId, roughness, trackType);

        if (polylineOptions != null && !polylineOptions.getPoints().isEmpty()) {
            if (!mTrackCache.containsKey(roughness)) {
                mTrackCache.put(roughness, new EnumMap<>(TrackType.class));
            }
            if (!mTrackCache.get(roughness).containsKey(trackType)) {
                mTrackCache.get(roughness).put(trackType, new HashMap<>());
            }

            mTrackCache.get(roughness).get(trackType).put(workoutId, new TrackData(polylineOptions, getLatLngBounds(polylineOptions)));
        }

    }

    public enum TrackType {
        BEST(Color.BLUE, null),
        GPS(Color.GREEN, "gps"),
        NETWORK(Color.MAGENTA, "network"),
        FUSED(Color.YELLOW, "google_fused");

        final int color;
        final String source;

        TrackType(int color, String source) {
            this.color = color;
            this.source = source;
        }

        @NonNull
        public String getLatitudeName() {
            String latitudeName = SensorType.LATITUDE.name();

            if (source != null) {
                latitudeName += "_" + source;
            }

            return latitudeName;
        }

        @NonNull
        public String getLongitudeName() {
            String longitudeName = SensorType.LONGITUDE.name();

            if (source != null) {
                longitudeName += "_" + source;
            }

            return longitudeName;
        }

    }

    private record TrackData(PolylineOptions polylineOptions, LatLngBounds latLngBounds) {
    }
    private class TrackOnMapThread extends Thread {
        final MapView mapView;
        final GoogleMap map;
        final long workoutId;
        final Roughness roughness;
        final TrackType trackType;
        final boolean zoomToMap;
        final boolean animateZoom;

        public TrackOnMapThread(MapView mapView, GoogleMap map, long workoutId, Roughness roughness, TrackType trackType, boolean zoomToMap, boolean animateZoom) {
            this.mapView = mapView;
            this.map = map;
            this.workoutId = workoutId;
            this.roughness = roughness;
            this.trackType = trackType;
            this.zoomToMap = zoomToMap;
            this.animateZoom = animateZoom;
        }

        @Override
        public void run()
        {
            if (DEBUG)
                Log.i(TAG, "doInBackground for workoutId=" + workoutId);

            calcTrackData(workoutId, roughness, trackType);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (DEBUG)
                    Log.i(TAG, "onPostExecute workoutId=" + workoutId);
                plotTrackOnMap(mapView, map, workoutId, roughness, trackType, zoomToMap, animateZoom);
            });
        }
    }
}
