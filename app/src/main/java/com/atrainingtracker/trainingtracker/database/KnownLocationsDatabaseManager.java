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

package com.atrainingtracker.trainingtracker.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.BANALService;
import com.google.android.gms.maps.model.LatLng;

import java.util.LinkedList;
import java.util.List;

public class KnownLocationsDatabaseManager {
    public static final int DEFAULT_RADIUS = 200;
    private static final String TAG = KnownLocationsDatabaseManager.class.getName();
    private static final boolean DEBUG = BANALService.getDebug(false);

    // --- Modern Singleton Pattern ---
    private static volatile KnownLocationsDatabaseManager cInstance;
    private final KnownLocationsDbHelper cDbHelper;

    // Private constructor
    private KnownLocationsDatabaseManager(@NonNull Context context) {
        cDbHelper = new KnownLocationsDbHelper(context.getApplicationContext());
    }

    @NonNull
    public static KnownLocationsDatabaseManager getInstance(@NonNull Context context) {
        if (cInstance == null) {
            synchronized (KnownLocationsDatabaseManager.class) {
                if (cInstance == null) {
                    cInstance = new KnownLocationsDatabaseManager(context);
                }
            }
        }
        return cInstance;
    }

    /**
     * Returns a writable database instance, managed safely by the helper.
     */
    public SQLiteDatabase getDatabase() {
        return cDbHelper.getWritableDatabase();
    }
    // --- End of Singleton Pattern ---

    @Deprecated
    public void addStartAltitude(String name, int altitude, double latitude, double longitude) {
        if (DEBUG) Log.d(TAG, "addStartAltitude: " + name + " " + altitude + "m");

        ContentValues values = new ContentValues();

        values.put(KnownLocationsDbHelper.NAME, name);
        values.put(KnownLocationsDbHelper.ALTITUDE, altitude);
        values.put(KnownLocationsDbHelper.LONGITUDE, longitude);
        values.put(KnownLocationsDbHelper.LATITUDE, latitude);

        SQLiteDatabase db = getDatabase();
        try {
            db.insert(KnownLocationsDbHelper.TABLE, null, values);
        } catch (SQLException e) {
            Log.e(TAG, "Error while writing" + e);
        }
    }

    @Nullable
    public MyLocation addNewLocation(String name, int altitude, int radius, double latitude, double longitude) {
        if (DEBUG)
            Log.d(TAG, "addNewLocation: " + name + " " + altitude + " m" + ", radius=" + radius);

        MyLocation myLocation = null;

        ContentValues values = new ContentValues();

        values.put(KnownLocationsDbHelper.NAME, name);
        values.put(KnownLocationsDbHelper.ALTITUDE, altitude);
        values.put(KnownLocationsDbHelper.RADIUS, radius);
        values.put(KnownLocationsDbHelper.LONGITUDE, longitude);
        values.put(KnownLocationsDbHelper.LATITUDE, latitude);

        try {
            long id = getDatabase().insert(KnownLocationsDbHelper.TABLE, null, values);
            myLocation = new MyLocation(id, latitude, longitude, name, altitude, radius);
        } catch (SQLException e) {
            Log.e(TAG, "Error while writing" + e);
        }

        return myLocation;
    }

    // public static Integer getStartAltitude(Context context, double latitude, double longitude)
    @Nullable
    public MyLocation getMyLocation(@NonNull LatLng latLng) {
        MyLocation myLocation = null;

        Location currentLocation = new Location("");
        currentLocation.setLatitude(latLng.latitude);
        currentLocation.setLongitude(latLng.longitude);

        Cursor cursor = getDatabase().query(KnownLocationsDbHelper.TABLE,
                null,
                null,
                null,
                null,
                null,
                null);  // sorting

        Location startLocation = new Location("");
        double minDistance = Double.MAX_VALUE;
        while (cursor.moveToNext()) {
            int radius = cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.RADIUS));

            startLocation.setLatitude(cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LATITUDE)));
            startLocation.setLongitude(cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LONGITUDE)));
            float distance = currentLocation.distanceTo(startLocation);
            if (DEBUG) Log.d(TAG, "distance to current location:" + distance);

            if (distance < radius) { // acceptable start location
                if (distance < minDistance) {
                    minDistance = distance;

                    myLocation = new MyLocation(cursor.getLong(cursor.getColumnIndex(KnownLocationsDbHelper.C_ID)),
                            cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LATITUDE)),
                            cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LONGITUDE)),
                            cursor.getString(cursor.getColumnIndex(KnownLocationsDbHelper.NAME)),
                            cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.ALTITUDE)),
                            cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.RADIUS)));
                }
            }
        }

        cursor.close();

        return myLocation;
    }

    public void deleteId(long id) {
        getDatabase().delete(KnownLocationsDbHelper.TABLE,
                KnownLocationsDbHelper.C_ID + "=?",
                new String[]{id + ""});
    }

    public void updateLocation(long id, @NonNull LatLng latLng) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(KnownLocationsDbHelper.LATITUDE, latLng.latitude);
        contentValues.put(KnownLocationsDbHelper.LONGITUDE, latLng.longitude);

        updateId(id, contentValues);
    }

    public void updateMyLocation(long id, @NonNull MyLocation myLocation) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(KnownLocationsDbHelper.NAME, myLocation.name);
        contentValues.put(KnownLocationsDbHelper.ALTITUDE, myLocation.altitude);
        contentValues.put(KnownLocationsDbHelper.LATITUDE, myLocation.latLng.latitude);
        contentValues.put(KnownLocationsDbHelper.LONGITUDE, myLocation.latLng.longitude);
        contentValues.put(KnownLocationsDbHelper.RADIUS, myLocation.radius);

        updateId(id, contentValues);
    }

    private void updateId(long id, ContentValues contentValues) {
        getDatabase().update(KnownLocationsDbHelper.TABLE,
                contentValues,
                KnownLocationsDbHelper.C_ID + "=?",
                new String[]{id + ""});
    }

    @Nullable
    public MyLocation getMyLocation(long myLocationId) {
        MyLocation myLocation = null;

        Cursor cursor = getDatabase().query(KnownLocationsDbHelper.TABLE,
                null,
                KnownLocationsDbHelper.C_ID + "=?",
                new String[]{Long.toString(myLocationId)},
                null,
                null,
                null,
                null);  // sorting

        if (cursor.moveToFirst()) {
            myLocation = new MyLocation(myLocationId,
                    cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LONGITUDE)),
                    cursor.getString(cursor.getColumnIndex(KnownLocationsDbHelper.NAME)),
                    cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.ALTITUDE)),
                    cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.RADIUS)));

        }

        cursor.close();

        return myLocation;
    }

    @NonNull
    public List<String> getMyLocationNameList() {
        List<String> result = new LinkedList<>();

        Cursor cursor = getDatabase().query(KnownLocationsDbHelper.TABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        while (cursor.moveToNext()) {
            result.add(cursor.getString(cursor.getColumnIndex(KnownLocationsDbHelper.NAME)));
        }

        cursor.close();

        return result;
    }

    @NonNull
    public List<NamedLatLng> getLocationsList(@NonNull ExtremaType extremaType) {
        List<NamedLatLng> startLocations = new LinkedList<>();

        Cursor cursor = getDatabase().query(KnownLocationsDbHelper.TABLE,
                null,
                KnownLocationsDbHelper.EXTREMA_TYPE + "=?",
                new String[]{extremaType.name()},
                null,
                null,
                null,
                null);  // sorting

        while (cursor.moveToNext()) {
            double latitude = cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LATITUDE));
            double longitude = cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.LONGITUDE));
            String name = cursor.getString(cursor.getColumnIndex(KnownLocationsDbHelper.NAME));

            startLocations.add(new NamedLatLng(new LatLng(latitude, longitude), name));
        }

        cursor.close();

        return startLocations;
    }

    public static class MyLocation {
        public final long id;
        @NonNull
        public final LatLng latLng;
        public String name;
        public int altitude;
        public int radius;

        public MyLocation(long id, double lat, double lng, String name, int altitude, int radius) {
            this.id = id;
            latLng = new LatLng(lat, lng);
            this.name = name;
            this.altitude = altitude;
            this.radius = radius;
        }
    }

    public record NamedLatLng(LatLng latLng, String name) {
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // finally, the database itself
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class KnownLocationsDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "StartLocation2Altitude.db";
        public static final int DB_VERSION = 3;
        public static final String TABLE = "StartLocation2Altitude";
        public static final String C_ID = BaseColumns._ID;
        public static final String NAME = "name";
        public static final String EXTREMA_TYPE = "extremumType";
        public static final String ALTITUDE = "altitude";
        public static final String LONGITUDE = "longitude";
        public static final String LATITUDE = "latitude";
        public static final String RADIUS = "radius";
        protected static final String TAG = KnownLocationsDbHelper.class.getName();
        protected static final boolean DEBUG = BANALService.getDebug(false);
        protected static final String CREATE_TABLE_V1 = "create table " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + NAME + " text,"
                + ALTITUDE + " int,"
                + LONGITUDE + " real,"
                + LATITUDE + " real)";

        protected static final String CREATE_TABLE_V2 = "create table " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + NAME + " text,"
                + ALTITUDE + " int,"
                + LONGITUDE + " real,"
                + LATITUDE + " real,"
                + RADIUS + " int)";               // new in version 2

        protected static final String CREATE_TABLE_V3 = "create table " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + NAME + " text,"
                + EXTREMA_TYPE + " text,"          // new in version 3 but not yet used!
                + ALTITUDE + " int,"
                + LONGITUDE + " real,"
                + LATITUDE + " real,"
                + RADIUS + " int)";

        // Constructor
        public KnownLocationsDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {

            switch (DB_VERSION) {
                case 1:
                    db.execSQL(CREATE_TABLE_V1);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_TABLE_V1);
                    break;

                case 2:
                    db.execSQL(CREATE_TABLE_V2);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_TABLE_V2);
                    break;

                case 3:
                    db.execSQL(CREATE_TABLE_V3);
                    if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_TABLE_V3);
                    break;

            }
        }

        private void addColumn(@NonNull SQLiteDatabase db, String column, String type) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + column + " " + type + ";");
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {

            if (oldVersion < 2) {
                addColumn(db, RADIUS, "int");

                ContentValues contentValues = new ContentValues();
                contentValues.put(RADIUS, DEFAULT_RADIUS);
                db.update(TABLE, contentValues, null, null);
            }

            if (oldVersion < 3) {
                addColumn(db, EXTREMA_TYPE, "text");

                ContentValues contentValues = new ContentValues();
                contentValues.put(EXTREMA_TYPE, ExtremaType.START.name());
                db.update(TABLE, contentValues, null, null);
            }
        }
    }


}
