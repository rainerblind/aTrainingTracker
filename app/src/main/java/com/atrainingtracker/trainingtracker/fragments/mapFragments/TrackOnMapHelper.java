package com.atrainingtracker.trainingtracker.fragments.mapFragments;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

import com.atrainingtracker.banalservice.Sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
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
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    private EnumMap<Roughness, Integer> foo;
    //                                                    workoutId
    private EnumMap<Roughness, EnumMap<TrackType, HashMap<Long, TrackData>>> mTrackCache = new EnumMap<Roughness, EnumMap<TrackType, HashMap<Long, TrackData>>>(Roughness.class);
    private EnumMap<TrackType, HashMap<GoogleMap, Polyline>> mPolylines = new EnumMap<TrackType, HashMap<GoogleMap, Polyline>>(TrackType.class);

    public static PolylineOptions getPolylineOptions(long workoutId, Roughness roughness, TrackType trackType) {
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
            if (dataValid(cursor, trackType.getLatitudeName()) && dataValid(cursor, trackType.getLongtudeName())) {
                // get latitude and longitude from the database and create new LatLng object
                latLng = new LatLng(cursor.getDouble(cursor.getColumnIndex(trackType.getLatitudeName())),
                        cursor.getDouble(cursor.getColumnIndex(trackType.getLongtudeName())));
                polylineOptions.add(latLng);
            }
        }

        cursor.close();
        databaseManager.closeDatabase(); // db.close();

        return polylineOptions;
    }

    public static LatLngBounds getLatLngBounds(PolylineOptions polylineOptions) {
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
    protected static boolean dataValid(Cursor cursor, String string) {
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

    public void showTrackOnMap(MyMapViewHolder myMapViewHolder, long workoutId, Roughness roughness, TrackType trackType, boolean zoomToMap, boolean animateZoom) {
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
            plotTrackOnMap(myMapViewHolder, workoutId, roughness_tmp, trackType, zoomToMap, animateZoom);
        }

        if (calcTrackData) {
            new FooAsyncTask().execute(new InputArguments(myMapViewHolder, workoutId, roughness, trackType, zoomToMap, animateZoom));
        }
    }

    private void plotTrackOnMap(final MyMapViewHolder myMapViewHolder, long workoutId, Roughness roughness, TrackType trackType, boolean zoomToMap, final boolean animateZoom) {
        if (DEBUG)
            Log.i(TAG, "plotTrackOnMap for workoutId=" + workoutId + ", roughness=" + roughness.name() + ", trackType=" + trackType.name());

        if (mPolylines.containsKey(trackType) &&
                mPolylines.get(trackType).containsKey(myMapViewHolder.map)) {
            mPolylines.get(trackType).get(myMapViewHolder.map).remove();
        }

        final TrackData trackData = getCachedTrackData(workoutId, roughness, trackType);
        if (DEBUG) Log.i(TAG, "trackData=" + trackData);
        if (trackData == null                                  // when there is no data
                & myMapViewHolder.mapView != null) {       // and it is 'only' an embedded MapView
            myMapViewHolder.mapView.setVisibility(View.GONE);  // we do not show the MapView
            return;
        } else if (trackData == null) {
            // TODO: is this the right solution?
            return;
        }

        Polyline polyline = myMapViewHolder.map.addPolyline(trackData.polylineOptions);

        if (!mPolylines.containsKey(trackType)) {
            mPolylines.put(trackType, new HashMap<GoogleMap, Polyline>());
        }
        mPolylines.get(trackType).put(myMapViewHolder.map, polyline);

        if (zoomToMap) {
            myMapViewHolder.map.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    if (animateZoom) {
                        myMapViewHolder.map.animateCamera(CameraUpdateFactory.newLatLngBounds(trackData.latLngBounds, 50));
                    } else {
                        myMapViewHolder.map.moveCamera(CameraUpdateFactory.newLatLngBounds(trackData.latLngBounds, 50));
                    }
                }
            });
        }

        myMapViewHolder.map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);

        return;
    }

    @Nullable
    private TrackData getCachedTrackData(long workoutId, Roughness roughness, TrackType trackType) {
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

    private boolean calcTrackData(long workoutId, Roughness roughness, TrackType trackType) {
        if (DEBUG)
            Log.i(TAG, "calcTrackData for workoutId=" + workoutId + ", roughness=" + roughness.name() + ", trackType=" + trackType.name());
        if (DEBUG) Log.i(TAG, "sensorTypeLatitude=" + trackType.getLatitudeName());

        PolylineOptions polylineOptions = getPolylineOptions(workoutId, roughness, trackType);

        if (polylineOptions != null && polylineOptions.getPoints().size() != 0) {
            if (!mTrackCache.containsKey(roughness)) {
                mTrackCache.put(roughness, new EnumMap<TrackType, HashMap<Long, TrackData>>(TrackType.class));
            }
            if (!mTrackCache.get(roughness).containsKey(trackType)) {
                mTrackCache.get(roughness).put(trackType, new HashMap<Long, TrackData>());
            }

            mTrackCache.get(roughness).get(trackType).put(workoutId, new TrackData(polylineOptions, getLatLngBounds(polylineOptions)));
        }

        return true;
    }

    public enum TrackType {
        BEST(Color.BLUE, null),
        GPS(Color.GREEN, "gps"),
        NETWORK(Color.MAGENTA, "network"),
        FUSED(Color.YELLOW, "google_fused");

        int color;
        String source;

        TrackType(int color, String source) {
            this.color = color;
            this.source = source;
        }

        public String getLatitudeName() {
            String latitudeName = SensorType.LATITUDE.name();

            if (source != null) {
                latitudeName += "_" + source;
            }

            return latitudeName;
        }

        public String getLongtudeName() {
            String longitudeName = SensorType.LONGITUDE.name();

            if (source != null) {
                longitudeName += "_" + source;
            }

            return longitudeName;
        }

    }

    private class TrackData {
        PolylineOptions polylineOptions;
        LatLngBounds latLngBounds;

        TrackData(PolylineOptions polylineOptions, LatLngBounds latLngBounds) {
            this.polylineOptions = polylineOptions;
            this.latLngBounds = latLngBounds;
        }
    }

    private class InputArguments {
        MyMapViewHolder myMapViewHolder;
        long workoutId;
        Roughness roughness;
        TrackType trackType;
        boolean zoomToMap;
        boolean animateZoom;

        public InputArguments(MyMapViewHolder myMapViewHolder, long workoutId, Roughness roughness, TrackType trackType, boolean zoomToMap, boolean animateZoom) {
            this.myMapViewHolder = myMapViewHolder;
            this.workoutId = workoutId;
            this.roughness = roughness;
            this.trackType = trackType;
            this.zoomToMap = zoomToMap;
            this.animateZoom = animateZoom;
        }
    }

    private class FooAsyncTask extends AsyncTask<InputArguments, Void, Void> {

        long workoutId;
        InputArguments inputArguments;

        @Override
        protected Void doInBackground(InputArguments... params) {
            inputArguments = params[0];
            workoutId = inputArguments.workoutId;
            if (DEBUG) Log.i(TAG, "doInBackground for workoutId=" + workoutId);

            calcTrackData(workoutId, inputArguments.roughness, inputArguments.trackType);

            return null;
        }

        @Override
        protected void onPostExecute(Void foo) {
            if (DEBUG) Log.i(TAG, "onPostExecute workoutId=" + workoutId);

            if (inputArguments.workoutId == workoutId) {  // is the workoutId still valid?
                plotTrackOnMap(inputArguments.myMapViewHolder, inputArguments.workoutId, inputArguments.roughness, inputArguments.trackType, inputArguments.zoomToMap, inputArguments.animateZoom);
            }
        }
    }

}
