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

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

// SimpleMapFragment: Just map,with location

// abstract TrackOnMapBaseFragment:
// TrackOnMapTrackingFragment: tracking map, with location and track and updates and start marker,listen to newWorkoutIdIntent...
// TrackOnMapAftermathFragment: aftermath map,without location,with track,without updates,with all markers

public abstract class BaseMapFragment
        extends SupportMapFragment
        implements OnMapReadyCallback {
    public static final String TAG = BaseMapFragment.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    // map related stuff
    protected GoogleMap mMap;

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // lifecycle methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // onAttach()

    // onCreate()

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onActivityCreated");

        this.getMapAsync(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreateView");
        setHasOptionsMenu(true);

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // map methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMapReady(final GoogleMap map) {
        if (DEBUG) Log.i(TAG, "onMapReady");

        if (mMap != null) {
            if (DEBUG) Log.i(TAG, "mMap !=null, returning");
            return;
        } // called second time???
        mMap = map;

        // if (getContext() != null) {
        // MapsInitializer.initialize(getContext());
        // }
        mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        mMap.getUiSettings().setZoomControlsEnabled(!TrainingApplication.zoomDependsOnSpeed());
    }


    protected void centerMapOnMyLocation(int zoomLevel, int tilt) {

        long gpsTime = 1;
        long networkTime = 0;

        Location locationGPS = null;
        Location locationNetwork = null;

        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);

        if (TrainingApplication.havePermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (locationGPS != null) {
                if (DEBUG) Log.i(TAG, "locationGPS: time=" + locationGPS.getTime());
                gpsTime = locationGPS.getTime();
            }
        }

        if (TrainingApplication.havePermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            locationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (locationNetwork != null) {
                if (DEBUG) Log.i(TAG, "locationNetwork: time=" + locationNetwork.getTime());
                networkTime = locationNetwork.getTime();
            }
        }

        // use the most resent location
        Location locationBest = (gpsTime > networkTime) ? locationGPS : locationNetwork;

        centerMapAt(locationBest, zoomLevel, tilt);
    }

    protected void centerMapAt(Location location, int zoomLevel, int tilt) {
        if (location != null) {
            // mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(locationBest.getLatitude(), locationBest.getLongitude()), zoomLevel));
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .tilt(tilt)                   // Sets the tilt of the camera to tilt degrees
                    .zoom(zoomLevel)
                    .build();                   // Creates a CameraPosition from the builder
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some helpers to add markers
    ////////////////////////////////////////////////////////////////////////////////////////////////
    protected Marker addMarker(LatLng position, int drawableId, String title) {
        if (position == null) return null;

        Bitmap marker = ((BitmapDrawable) getResources().getDrawable(drawableId)).getBitmap();

        return mMap.addMarker(new MarkerOptions()
                .position(position)
                .title(title)
                .icon(BitmapDescriptorFactory.fromBitmap(marker)));
    }

    protected Marker addScaledMarker(LatLng position, int drawableId, double scale) {
        if (DEBUG) Log.i(TAG, "addScaledMarker");
        if (position == null) {
            Log.i(TAG, "WTF: position == null");
            return null;
        }

        Bitmap marker = ((BitmapDrawable) getResources().getDrawable(drawableId)).getBitmap();
        Bitmap scaledMarker = Bitmap.createScaledBitmap(marker, (int) (marker.getWidth() * scale), (int) (marker.getHeight() * scale), false);

        return mMap.addMarker(new MarkerOptions()
                .position(position)
                .icon(BitmapDescriptorFactory.fromBitmap(scaledMarker)));
    }

    protected Polyline addPolyline(List<LatLng> latLngs, int color) {
        if (DEBUG) Log.i(TAG, "addPolyline");

        PolylineOptions polylineOptions = new PolylineOptions().color(color);
        polylineOptions.addAll(latLngs);

        return mMap.addPolyline(polylineOptions);
    }


}
