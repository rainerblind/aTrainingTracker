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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.Nullable;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.MyMapViewHolder;
import com.atrainingtracker.trainingtracker.fragments.mapFragments.Roughness;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.EnumMap;
import java.util.HashMap;

/**
 * Created by rainer on 29.03.16.
 */
public class SegmentOnMapHelper {
    private static final String TAG = SegmentOnMapHelper.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    //                                 segmentId
    private final EnumMap<Roughness, HashMap<Long, SegmentData>> mSegmentCache = new EnumMap<>(Roughness.class);

    public void showSegmentOnMap(Context context, MyMapViewHolder myMapViewHolder, long segmentId, Roughness roughness, boolean zoomToMap, boolean animateZoom) {
        if (DEBUG)
            Log.i(TAG, "showSegmentOnMap for segmentId=" + segmentId + ", roughness=" + roughness.name());

        Roughness roughness_tmp = roughness;

        SegmentData segmentData = getCachedSegmentData(segmentId, roughness_tmp);
        boolean calcSegmentData = true;
        if (segmentData != null) {
            calcSegmentData = false;
        } else {
            roughness_tmp = Roughness.MEDIUM;
            segmentData = getCachedSegmentData(segmentId, roughness_tmp);
            calcSegmentData = true;
        }

        if (segmentData != null) {
            plotSegmentOnMap(myMapViewHolder, segmentId, roughness_tmp, zoomToMap, animateZoom);
        }

        if (calcSegmentData) {
            new SegmentDataThread(context, myMapViewHolder, segmentId, roughness, zoomToMap, animateZoom).start();
        }
    }

    private void plotSegmentOnMap(final MyMapViewHolder myMapViewHolder, long segmentId, Roughness roughness, boolean zoomToMap, final boolean animateZoom) {
        if (DEBUG)
            Log.i(TAG, "plotSegmentOnMap for segmentId=" + segmentId + ", roughness=" + roughness.name());

        final SegmentData segmentData = getCachedSegmentData(segmentId, roughness);
        if (DEBUG) Log.i(TAG, "segmentData=" + segmentData);
        if (segmentData == null                                  // when there is no data
                & myMapViewHolder.mapView != null) {       // and it is 'only' an embedded MapView
            myMapViewHolder.mapView.setVisibility(View.GONE);  // we do not show the MapView
            return;
        } else if (segmentData == null) {
            // TODO: is this the right solution?
            return;
        }

        myMapViewHolder.map.addPolyline(segmentData.polylineOptions);

        if (zoomToMap) {
            myMapViewHolder.map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    if (animateZoom) {
                        myMapViewHolder.map.animateCamera(CameraUpdateFactory.newLatLngBounds(segmentData.latLngBounds, 50));
                    } else {
                        myMapViewHolder.map.moveCamera(CameraUpdateFactory.newLatLngBounds(segmentData.latLngBounds, 50));
                    }
                }
            });
        }

        // TODO: probably, not the right place! But necessary since we set map type to none when removing or recycling the map???
        myMapViewHolder.map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
    }

    @Nullable
    private SegmentData getCachedSegmentData(long segmentId, Roughness roughness) {
        if (DEBUG)
            Log.i(TAG, "getCachedTrackData for segmentId=" + segmentId + ", roughness=" + roughness.name());

        SegmentData segmentData = null;

        if (mSegmentCache.containsKey(roughness)
                && mSegmentCache.get(roughness).containsKey(segmentId)) {
            segmentData = mSegmentCache.get(roughness).get(segmentId);
        }

        if (DEBUG) Log.i(TAG, "segmentData=" + segmentData);

        return segmentData;
    }

    private void calcSegmentData(Context context, long segmentId, Roughness roughness) {
        if (DEBUG)
            Log.i(TAG, "calcSegmentData for segmentId=" + segmentId + ", roughness=" + roughness.name());

        PolylineOptions polylineOptions = new PolylineOptions().color(context.getResources().getColor(R.color.strava));

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();
        Cursor cursor = db.query(Segments.TABLE_SEGMENT_STREAMS, null,
                Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""},
                null, null, null);

        LatLng latLng;
        LatLngBounds.Builder latLngBoundsBuilder = new LatLngBounds.Builder();

        boolean havePoints = false;
        // while (cursor.moveToNext()) {
        while (cursor.move(roughness.stepSize)) {
            if (dataValid(cursor, Segments.LATITUDE) && dataValid(cursor, Segments.LONGITUDE)) {
                havePoints = true;

                // get latitude and longitude from the database and create new LatLng object
                latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex(Segments.LATITUDE)),
                        cursor.getDouble(cursor.getColumnIndex(Segments.LONGITUDE)));

                // add to polyline and builder
                polylineOptions.add(latLng);
                latLngBoundsBuilder.include(latLng);
            }
        }

        cursor.close();
        SegmentsDatabaseManager.getInstance().closeDatabase();

        if (havePoints) {
            if (!mSegmentCache.containsKey(roughness)) {
                mSegmentCache.put(roughness, new HashMap<>());
            }
            mSegmentCache.get(roughness).put(segmentId, new SegmentData(polylineOptions, latLngBoundsBuilder.build()));
        }

    }

    // TODO: this is stolen several times, so make it a static method of a DatabaseHelper Class
    protected boolean dataValid(Cursor cursor, String string) {
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

    private record SegmentData(PolylineOptions polylineOptions, LatLngBounds latLngBounds) {
    }

    private class SegmentDataThread extends Thread {
        final Context context;
        final MyMapViewHolder myMapViewHolder;
        final long segmentId;
        final Roughness roughness;
        final boolean zoomToMap;
        final boolean animateZoom;

        SegmentDataThread(Context context, MyMapViewHolder myMapViewHolder, long segmentId, Roughness roughness, boolean zoomToMap, boolean animateZoom) {
            this.context = context;
            this.myMapViewHolder = myMapViewHolder;
            this.segmentId = segmentId;
            this.roughness = roughness;
            this.zoomToMap = zoomToMap;
            this.animateZoom = animateZoom;
        }

        @Override
        public void run() {
            if (DEBUG) Log.i(TAG, "doInBackground for segmentId=" + segmentId);

            calcSegmentData(context, segmentId, roughness);

            new Handler(Looper.getMainLooper()).post(() -> {
                if (DEBUG) Log.i(TAG, "onPostExecute segmentId=" + segmentId);

                plotSegmentOnMap(myMapViewHolder, segmentId, roughness, zoomToMap, animateZoom);
            });
        }
    }
}
