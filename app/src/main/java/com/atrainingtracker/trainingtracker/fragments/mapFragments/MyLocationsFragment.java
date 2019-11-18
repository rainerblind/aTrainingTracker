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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.ExtremaType;
import com.atrainingtracker.trainingtracker.views.MultiSelectionSpinner;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.KnownLocationsDbHelper;
import com.atrainingtracker.trainingtracker.database.KnownLocationsDatabaseManager.MyLocation;
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.helpers.CalcExtremaValuesTask;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Created by rainer on 10.05.16.
 */
public class MyLocationsFragment
        extends Fragment {
    public static final String TAG = MyLocationsFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG & false;


    MapView mMapView;
    GoogleMap mMap;

    MultiSelectionSpinner mSportSpinner, mExtremaTypeSpinner;

    HashMap<Long, EnumMap<ExtremaType, List<Marker>>> mMarkerMap = new HashMap<Long, EnumMap<ExtremaType, List<Marker>>>();

    Map<Marker, Long> mMarker2WorkoutIdMap = new HashMap<>();
    Map<Marker, Long> mMarker2MyLocationsIdMap = new HashMap<>();
    Map<Long, Circle> mMarkerId2CircleMap = new HashMap<>();

    List<Long> mSportTypesIdList = SportTypeDatabaseManager.getSportTypesIdList();
    List<String> mSportTypesUiNamList = SportTypeDatabaseManager.getSportTypesUiNameList();

    /**
     * stolen from BaseExporter
     */
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // inflate the XML layout
        View v = inflater.inflate(R.layout.locations_manager, container, false);

        // get the views
        mSportSpinner = v.findViewById(R.id.spinnerSport);
        mExtremaTypeSpinner = v.findViewById(R.id.spinnerExtremaType);
        mMapView = v.findViewById(R.id.mapView);

        // now, configure the views
        mSportSpinner.setItems(mSportTypesUiNamList);
        // mSportSpinner.setSelection(new int[]{TTSportType.RUN.ordinal(), TTSportType.BIKE.ordinal(), TTSportType.MTB.ordinal()});
        mSportSpinner.setSelection(new int[]{});
        mSportSpinner.setmMSSOnitemClickedListener(new MultiSelectionSpinner.MSSOnItemClickedListener() {
            @Override
            public void onItemClicked(int position, boolean isChecked) {
                if (DEBUG)
                    Log.i(TAG, "sportSpinner: onItemClicked position=" + position + ", isChecked=" + isChecked);
                long sportTypeId = mSportTypesIdList.get(position);
                if (isChecked) {
                    addSportTypeLocations(sportTypeId);
                } else {
                    removeSportTypeLocations(sportTypeId);
                }
            }
        });

        mExtremaTypeSpinner.setItems(ExtremaType.getLocationNameList());
        // mExtremaTypeSpinner.setSelection(new int[]{0, 1, 2});
        mExtremaTypeSpinner.setSelection(new int[]{});
        mExtremaTypeSpinner.setmMSSOnitemClickedListener(new MultiSelectionSpinner.MSSOnItemClickedListener() {
            @Override
            public void onItemClicked(int position, boolean isChecked) {
                if (DEBUG)
                    Log.i(TAG, "extremaTypeSpinner: onItemClicked position=" + position + ", isChecked=" + isChecked);
                ExtremaType selectedExtremaType = ExtremaType.LOCATION_EXTREMA_TYPES[position];
                if (isChecked) {
                    addExtremaTypeLocations(selectedExtremaType);
                } else {
                    removeExtremaTypeLocations(selectedExtremaType);
                }
            }
        });


        // Gets the MapView from the XML layout and creates it
        mMapView.onCreate(savedInstanceState);
        // Gets to GoogleMap from the MapView and does initialization stuff
        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;

                if (getContext() != null) {
                    MapsInitializer.initialize(getContext());
                }
                mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                mMap.getUiSettings().setZoomControlsEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                mMap.setMyLocationEnabled(true);
                centerMapOnMyLocation(11);

                mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
                    @Override
                    public boolean onMarkerClick(Marker marker) {
                        if (mMarker2WorkoutIdMap.containsKey(marker)) {
                            long workoutId = mMarker2WorkoutIdMap.get(marker);

                            MyMapViewHolder myMapViewHolder = new MyMapViewHolder(mMap, mMapView);
                            ((TrainingApplication) getActivity().getApplication()).trackOnMapHelper.showTrackOnMap(myMapViewHolder, workoutId, Roughness.ALL, TrackOnMapHelper.TrackType.BEST, false, true);
                            // TODO: keep track but remove on second click?
                            return true;
                        } else if (mMarker2MyLocationsIdMap.containsKey(marker)) {
                            long myLocationId = mMarker2MyLocationsIdMap.get(marker);

                            showEditMyLocationsDialog(myLocationId);

                            return true;
                        } else {
                            return false;
                        }

                    }
                });

                mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
                    @Override
                    public void onMarkerDragStart(Marker marker) {

                    }

                    @Override
                    public void onMarkerDrag(Marker marker) {
                        // move circle
                        mMarkerId2CircleMap.get(mMarker2MyLocationsIdMap.get(marker)).setCenter(marker.getPosition());
                    }

                    @Override
                    public void onMarkerDragEnd(Marker marker) {
                        // update position in database
                        KnownLocationsDatabaseManager.updateLocation(mMarker2MyLocationsIdMap.get(marker), marker.getPosition());
                    }
                });

                mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(LatLng latLng) {
                        // create new MyLocation
                        new CreateNewMyLocation(getContext()).execute(latLng);
                    }
                });

                addMyLocationMarkers();
            }
        });

        // Updates the location and zoom of the MapView
        // TODO: zoom to the correct location?
        // CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(new LatLng(43.1, -87.9), 10);
        // map.animateCamera(cameraUpdate);

        return v;
    }

    @Override
    public void onResume() {
        mMapView.onResume();
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // FOO
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    protected void addMyLocationMarkers() {
        new AddMyLocationMarkers(getContext()).execute();
    }

    protected void addSportTypeLocations(long ttSportTypeId) {
        for (int selectedExtremaType : mExtremaTypeSpinner.getSelectedIndicies()) {
            ExtremaType extremaType = ExtremaType.LOCATION_EXTREMA_TYPES[selectedExtremaType];

            new ShowExtremaLocations(getContext(), ttSportTypeId, extremaType).execute();
        }
    }

    protected void addExtremaTypeLocations(ExtremaType extremaType) {
        for (int selectedSportTypeIndex : mSportSpinner.getSelectedIndicies()) {
            long sportTypeId = mSportTypesIdList.get(selectedSportTypeIndex);

            new ShowExtremaLocations(getContext(), sportTypeId, extremaType).execute();
        }
    }

    protected void removeSportTypeLocations(long sportTypeId) {
        if (DEBUG) Log.i(TAG, "removeSportTypeLocations: sportTypeId=" + sportTypeId);

        if (mMarkerMap.containsKey(sportTypeId)) {
            for (ExtremaType extremaType : mMarkerMap.get(sportTypeId).keySet()) {
                removeMarkers(sportTypeId, extremaType);
            }
        }
    }

    protected void removeExtremaTypeLocations(ExtremaType extremaType) {
        if (DEBUG) Log.i(TAG, "removeExtremaTypeLocations: ExtremaType=" + extremaType);

        for (long sportTypeId : mMarkerMap.keySet()) {
            if (mMarkerMap.get(sportTypeId).containsKey(extremaType)) {
                removeMarkers(sportTypeId, extremaType);
            }
        }
    }

    protected void removeMarkers(long sportTypeId, ExtremaType extremaType) {
        if (DEBUG)
            Log.i(TAG, "removeMarkers: sportTypeId=" + sportTypeId + ", ExtremaType=" + extremaType);

        for (Marker marker : mMarkerMap.get(sportTypeId).get(extremaType)) {
            mMarker2WorkoutIdMap.remove(marker);
            marker.remove();
        }

        mMarkerMap.get(sportTypeId).remove(extremaType);
    }

    protected void addMyLocationToMap(MyLocation myLocation) {

        Marker marker = mMap.addMarker(new MarkerOptions()
                .position(myLocation.latLng)
                .title(myLocation.name)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .draggable(true));  // TODO: much larger?
        mMarker2MyLocationsIdMap.put(marker, myLocation.id);

        Circle circle = mMap.addCircle(new CircleOptions()
                .center(myLocation.latLng)
                .radius(myLocation.radius)
                .fillColor(Color.argb(75, 0, 0, 255))
                .strokeColor(Color.BLUE));
        mMarkerId2CircleMap.put(myLocation.id, circle);
    }

    protected void showEditMyLocationsDialog(final long myLocationId) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.edit_my_location, null);

        final EditText etName = view.findViewById(R.id.etLocationName);
        final EditText etAltitude = view.findViewById(R.id.etLocationAltitude);
        final TextView tvRadius = view.findViewById(R.id.tvRadius);
        final SeekBar sbRadius = view.findViewById(R.id.sbRadius);

        final MyLocation myLocation = KnownLocationsDatabaseManager.getMyLocation(myLocationId);
        etName.setText(myLocation.name);
        etAltitude.setText(Integer.toString(myLocation.altitude));
        tvRadius.setText(getString(R.string.radius_format, getString(R.string.LocationRadius), myLocation.radius, getString(R.string.units_radius)));
        sbRadius.setProgress(myLocation.radius);
        sbRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mMarkerId2CircleMap.get(myLocationId).setRadius(progress);
                tvRadius.setText(getString(R.string.radius_format, getString(R.string.LocationRadius),
                        progress,
                        getString(R.string.units_radius)));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());
        alertDialogBuilder.setMessage(R.string.edit_my_location)
                .setView(view)
                // .setCancelable(false)
                .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        myLocation.name = etName.getText().toString();
                        try {
                            myLocation.altitude = Integer.parseInt(etAltitude.getText().toString());  // hopefully, this works
                        } catch (Exception e) {
                        }
                        myLocation.radius = sbRadius.getProgress();

                        KnownLocationsDatabaseManager.updateMyLocation(myLocationId, myLocation);
                    }
                });
        alertDialogBuilder.setNeutralButton(R.string.Cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mMarkerId2CircleMap.get(myLocationId).setRadius(myLocation.radius);
                dialog.cancel();
            }
        });
        alertDialogBuilder.setNegativeButton(R.string.delete_location, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteMyLocation(myLocation);
            }
        });
        AlertDialog alert = alertDialogBuilder.create();

        Window window = alert.getWindow();
        window.setGravity(Gravity.TOP);

        alert.show();
    }

    private void deleteMyLocation(final MyLocation myLocation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.delete_location)
                .setMessage(R.string.really_delete_location)
                .setIcon(android.R.drawable.ic_menu_delete)
                .setPositiveButton(R.string.delete_location, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        reallyDeleteLocation(myLocation);
                    }
                })
                .setNegativeButton(R.string.Cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.create().show();
    }

    private void reallyDeleteLocation(MyLocation myLocation) {
        // remove from database
        KnownLocationsDatabaseManager.deleteId(myLocation.id);

        // remove circle
        mMarkerId2CircleMap.get(myLocation.id).remove();

        // remove marker
        for (Marker marker : mMarker2MyLocationsIdMap.keySet()) {
            if (mMarker2MyLocationsIdMap.get(marker) == myLocation.id) {
                marker.remove();
            }
        }
    }

    // TODO: copied code from BaseMapFragment?
    // TODO: move these (and similar) methods to MyMapHelper
    protected Marker addScaledMarker(LatLng position, int drawableId, double scale) {
        if (DEBUG) Log.i(TAG, "addScaledMarker");
        if (position == null && DEBUG) {
            Log.i(TAG, "WTF: position == null");
            return null;
        }

        Bitmap marker = ((BitmapDrawable) getResources().getDrawable(drawableId)).getBitmap();
        Bitmap scaledMarker = Bitmap.createScaledBitmap(marker, (int) (marker.getWidth() * scale), (int) (marker.getHeight() * scale), false);

        return mMap.addMarker(new MarkerOptions()
                .position(position)
                .icon(BitmapDescriptorFactory.fromBitmap(scaledMarker)));
    }

    // again, copied code from BaseMapFragment
    protected void centerMapOnMyLocation(int zoomLevel) {
        // seems to work now properly

        long gpsTime = 1;
        long networkTime = 0;

        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

        Location locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (locationGPS != null) {
            if (DEBUG) Log.i(TAG, "locationGPS: time=" + locationGPS.getTime());
            gpsTime = locationGPS.getTime();
        }

        Location locationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (locationNetwork != null) {
            if (DEBUG) Log.i(TAG, "locationNetwork: time=" + locationNetwork.getTime());
            networkTime = locationNetwork.getTime();
        }

        // use the most resent location
        Location locationBest = (gpsTime > networkTime) ? locationGPS : locationNetwork;

        if (locationBest != null) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(locationBest.getLatitude(), locationBest.getLongitude()), zoomLevel));
            // mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(locationBest.getLatitude(), locationBest.getLongitude()), zoomLevel));
        }
    }

    private class LatLngId {
        LatLng latLng;
        long id;

        LatLngId(double lat, double lng, long id) {
            latLng = new LatLng(lat, lng);
            this.id = id;
        }
    }

    class ShowExtremaLocations extends AsyncTask<Integer, LatLngId, Float> {
        private long mSportTypeId;
        private ExtremaType mExtremaType;

        private ProgressDialog progressDialog;
        private Context context;

        private int mMarkerId = R.drawable.start_logo_map;

        ShowExtremaLocations(Context context, long sportTypeId, ExtremaType extremaType) {
            if (DEBUG)
                Log.i(TAG, "ShowExtremaLocations: sportTypeId=" + sportTypeId + ", extremaType=" + extremaType);

            this.context = context;
            mSportTypeId = sportTypeId;
            mExtremaType = extremaType;

            progressDialog = new ProgressDialog(context);
        }

        protected void onPreExecute() {
            // disable clicking
            mSportSpinner.setEnabled(false);
            mSportSpinner.setClickable(false);
            mExtremaTypeSpinner.setEnabled(false);
            mExtremaTypeSpinner.setClickable(false);

            // show a progress dialog
            progressDialog.setMessage(context.getString(R.string.get_extremaType_of_sportType_format, mExtremaType.toString(), SportTypeDatabaseManager.getUIName(mSportTypeId)));
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();


            switch (mExtremaType) {
                case START:
                    mMarkerId = R.drawable.start_logo_map;  // TODO: other color?
                    break;
                case END:
                    mMarkerId = R.drawable.stop_logo_map;   // TODO: other color?
                    break;
                case MAX_LINE_DISTANCE:
                    mMarkerId = R.drawable.max_line_distance_logo_map;  // TODO: other color?
                    break;
            }

            // make sure that mMarkerMap it correctly initialized
            if (!mMarkerMap.containsKey(mSportTypeId)) {
                mMarkerMap.put(mSportTypeId, new EnumMap<ExtremaType, List<Marker>>(ExtremaType.class));
            }
            if (!mMarkerMap.get(mSportTypeId).containsKey(mExtremaType)) {
                mMarkerMap.get(mSportTypeId).put(mExtremaType, new LinkedList<Marker>());
            }

        }


        @Override
        protected Float doInBackground(Integer... params) {

            // TODO: copied code from WorkoutSummariesDatabaseManager
            SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

            Cursor latCursor = null;
            Cursor lonCursor = null;
            double latitude, longitude;

            Cursor cursor = db.query(WorkoutSummaries.TABLE,
                    null,
                    WorkoutSummaries.SPORT_ID + "=?",
                    new String[]{Long.toString(mSportTypeId)},
                    null,
                    null,
                    null);

            boolean calculatedMaxLineDistance = false;

            while (cursor.moveToNext()) {
                long workoutId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
                if (DEBUG) Log.i(TAG, "checking workoutId=" + workoutId);

                latCursor = db.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                        null,
                        WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=?",
                        new String[]{Long.toString(workoutId), SensorType.LATITUDE.name(), mExtremaType.name()},
                        null, null, null);
                lonCursor = db.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                        null,
                        WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=?",
                        new String[]{Long.toString(workoutId), SensorType.LONGITUDE.name(), mExtremaType.name()},
                        null, null, null);

                if (latCursor.moveToFirst()
                        && lonCursor.moveToFirst()
                        && dataValid(latCursor, WorkoutSummaries.VALUE)
                        && dataValid(lonCursor, WorkoutSummaries.VALUE)) {
                    latitude = latCursor.getDouble(latCursor.getColumnIndex(WorkoutSummaries.VALUE));
                    longitude = lonCursor.getDouble(latCursor.getColumnIndex(WorkoutSummaries.VALUE));
                    publishProgress(new LatLngId(latitude, longitude, workoutId));
                } else if (!calculatedMaxLineDistance && mExtremaType == ExtremaType.MAX_LINE_DISTANCE) {
                    if (DEBUG)
                        Log.i(TAG, "try to calculate the max line distance of workoutId=" + workoutId);
                    CalcExtremaValuesTask.calcAndSaveMaxLineDistancePosition(workoutId);
                    cursor.moveToPrevious();
                    calculatedMaxLineDistance = true;

                }
                calculatedMaxLineDistance = false;
                if (DEBUG)
                    Log.i(TAG, "no valid location for ExtremaType=" + mExtremaType + ", mSportTypeId=" + mSportTypeId);

                latCursor.close();
                lonCursor.close();
            }

            cursor.close();
            WorkoutSummariesDatabaseManager.getInstance().closeDatabase();

            return null;
        }

        @Override
        protected void onProgressUpdate(LatLngId... values) {
            super.onProgressUpdate(values);
            if (DEBUG) Log.i(TAG, "add new extrema position.");

            Marker marker = addScaledMarker(values[0].latLng, mMarkerId, 0.666);
            mMarkerMap.get(mSportTypeId).get(mExtremaType).add(marker);
            mMarker2WorkoutIdMap.put(marker, values[0].id);
        }

        @Override
        protected void onPostExecute(Float param) {
            // hide progressView
            if (progressDialog.isShowing()) {
                try {
                    progressDialog.dismiss();
                    // sometimes this gives the following exception:
                    // java.lang.IllegalArgumentException: View not attached to window manager
                    // so we catch this exception
                } catch (IllegalArgumentException e) {
                    // and nothing
                    // http://stackoverflow.com/questions/2745061/java-lang-illegalargumentexception-view-not-attached-to-window-manager
                }
            } else {
                if (DEBUG) Log.d(TAG, "dialog no longer showing, so do nothing?");
            }

            mSportSpinner.setEnabled(true);
            mSportSpinner.setClickable(true);
            mExtremaTypeSpinner.setEnabled(true);
            mExtremaTypeSpinner.setClickable(true);

        }
    }

    /**
     * helper method to check whether there is some data
     */

    class AddMyLocationMarkers extends AsyncTask<Integer, MyLocation, Float> {
        private ProgressDialog progressDialog;
        private Context context;

        AddMyLocationMarkers(Context context) {
            this.context = context;

            progressDialog = new ProgressDialog(context);
        }

        protected void onPreExecute() {
            // show a progress dialog
            progressDialog.setMessage(context.getString(R.string.getting_my_locations));
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        }


        @Override
        protected Float doInBackground(Integer... params) {

            SQLiteDatabase db = KnownLocationsDatabaseManager.getInstance().getOpenDatabase();
            Cursor cursor = db.query(KnownLocationsDbHelper.TABLE,
                    null,
                    null, // KnownLocationsDbHelper.EXTREMA_TYPE + "=?",
                    null, // new String[] { extremaType.name() },
                    null,
                    null,
                    null,
                    null);  // sorting

            while (cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndex(KnownLocationsDbHelper.C_ID));
                double latitude = cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LONGITUDE));
                String name = cursor.getString(cursor.getColumnIndex(KnownLocationsDbHelper.NAME));
                int altitude = cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.ALTITUDE));
                int radius = cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.RADIUS));

                publishProgress(new MyLocation(id, latitude, longitude, name, altitude, radius));
            }

            cursor.close();
            KnownLocationsDatabaseManager.getInstance().closeDatabase();

            return null;
        }

        @Override
        protected void onProgressUpdate(MyLocation... values) {
            super.onProgressUpdate(values);
            if (DEBUG) Log.i(TAG, "add new myLocation.");

            addMyLocationToMap(values[0]);
        }

        @Override
        protected void onPostExecute(Float param) {
            // hide progressView
            if (progressDialog.isShowing()) {
                try {
                    progressDialog.dismiss();
                    // sometimes this gives the following exception:
                    // java.lang.IllegalArgumentException: View not attached to window manager
                    // so we catch this exception
                } catch (IllegalArgumentException e) {
                    // and nothing
                    // http://stackoverflow.com/questions/2745061/java-lang-illegalargumentexception-view-not-attached-to-window-manager
                }
            } else {
                if (DEBUG) Log.d(TAG, "dialog no longer showing, so do nothing?");
            }

        }
    }

    class CreateNewMyLocation extends AsyncTask<LatLng, Void, MyLocation> {
        private ProgressDialog progressDialog;
        private Context context;

        CreateNewMyLocation(Context context) {
            this.context = context;

            progressDialog = new ProgressDialog(context);
        }

        protected void onPreExecute() {
            // show a progress dialog
            progressDialog.setMessage(context.getString(R.string.create_new_my_location));
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.show();
        }


        @Override
        protected MyLocation doInBackground(LatLng... params) {
            Double altitude = WorkoutSamplesDatabaseManager.calcAverageAroundLocation(params[0], 100, SensorType.ALTITUDE);

            return KnownLocationsDatabaseManager.addNewLocation("", altitude.intValue(), KnownLocationsDatabaseManager.DEFAULT_RADIUS, params[0].latitude, params[0].longitude);

        }

        @Override
        protected void onPostExecute(MyLocation param) {
            addMyLocationToMap(param);
            showEditMyLocationsDialog(param.id);

            // hide progressView
            if (progressDialog.isShowing()) {
                try {
                    progressDialog.dismiss();
                    // sometimes this gives the following exception:
                    // java.lang.IllegalArgumentException: View not attached to window manager
                    // so we catch this exception
                } catch (IllegalArgumentException e) {
                    // and nothing
                    // http://stackoverflow.com/questions/2745061/java-lang-illegalargumentexception-view-not-attached-to-window-manager
                }
            } else {
                if (DEBUG) Log.d(TAG, "dialog no longer showing, so do nothing?");
            }

        }
    }

}
