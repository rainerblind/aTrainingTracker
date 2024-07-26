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

import com.atrainingtracker.banalservice.BANALService;
import com.google.android.gms.maps.model.LatLng;

import java.util.LinkedList;
import java.util.List;

public class KnownLocationsDatabaseManager {
    // private static final double maxDistanceDiff = 200;
    public static final int DEFAULT_RADIUS = 200;
    private static final String TAG = KnownLocationsDatabaseManager.class.getName();
    private static final boolean DEBUG = BANALService.getDebug(false);
    private static KnownLocationsDatabaseManager cInstance;
    private static KnownLocationsDbHelper cDbHelper;
    private int mOpenCounter;
    private SQLiteDatabase mDatabase;

    public static synchronized void initializeInstance(KnownLocationsDbHelper dbHelper) {
        if (cInstance == null) {
            cInstance = new KnownLocationsDatabaseManager();
            cDbHelper = dbHelper;
        }
    }

    public static synchronized KnownLocationsDatabaseManager getInstance() {
        if (cInstance == null) {
            throw new IllegalStateException(KnownLocationsDatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return cInstance;
    }

    @Deprecated
    public static void addStartAltitude(Context context, String name, int altitude, double latitude, double longitude) {
        if (DEBUG) Log.d(TAG, "addStartAltitude: " + name + " " + altitude + "m");

        ContentValues values = new ContentValues();

        values.put(KnownLocationsDbHelper.NAME, name);
        values.put(KnownLocationsDbHelper.ALTITUDE, altitude);
        values.put(KnownLocationsDbHelper.LONGITUDE, longitude);
        values.put(KnownLocationsDbHelper.LATITUDE, latitude);

        // StartLocation2AltitudeDbHelper dbHelper = new StartLocation2AltitudeDbHelper(context);
        // SQLiteDatabase db = dbHelper.getWritableDatabase();
        SQLiteDatabase db = getInstance().getOpenDatabase();
        try {
            db.insert(KnownLocationsDbHelper.TABLE, null, values);
        } catch (SQLException e) {
            Log.e(TAG, "Error while writing" + e);
        }
        getInstance().closeDatabase();
    }

    public static MyLocation addNewLocation(String name, int altitude, int radius, double latitude, double longitude) {
        if (DEBUG)
            Log.d(TAG, "addNewLocation: " + name + " " + altitude + " m" + ", radius=" + radius);

        MyLocation myLocation = null;

        ContentValues values = new ContentValues();

        values.put(KnownLocationsDbHelper.NAME, name);
        values.put(KnownLocationsDbHelper.ALTITUDE, altitude);
        values.put(KnownLocationsDbHelper.RADIUS, radius);
        values.put(KnownLocationsDbHelper.LONGITUDE, longitude);
        values.put(KnownLocationsDbHelper.LATITUDE, latitude);

        SQLiteDatabase db = getInstance().getOpenDatabase();
        try {
            long id = db.insert(KnownLocationsDbHelper.TABLE, null, values);
            myLocation = new MyLocation(id, latitude, longitude, name, altitude, radius);
        } catch (SQLException e) {
            Log.e(TAG, "Error while writing" + e);
        }
        getInstance().closeDatabase();

        return myLocation;
    }

    // public static Integer getStartAltitude(Context context, double latitude, double longitude)
    public static MyLocation getMyLocation(LatLng latLng) {
        MyLocation myLocation = null;

        Location currentLocation = new Location("");
        currentLocation.setLatitude(latLng.latitude);
        currentLocation.setLongitude(latLng.longitude);

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(KnownLocationsDbHelper.TABLE,
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
        getInstance().closeDatabase();

        return myLocation;
    }

    public static void deleteId(long id) {
        SQLiteDatabase db = getInstance().getOpenDatabase();

        db.delete(KnownLocationsDbHelper.TABLE,
                KnownLocationsDbHelper.C_ID + "=?",
                new String[]{id + ""});

        getInstance().closeDatabase();
    }

    public static void updateLocation(long id, LatLng latLng) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(KnownLocationsDbHelper.LATITUDE, latLng.latitude);
        contentValues.put(KnownLocationsDbHelper.LONGITUDE, latLng.longitude);

        updateId(id, contentValues);
    }

    public static void updateMyLocation(long id, MyLocation myLocation) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(KnownLocationsDbHelper.NAME, myLocation.name);
        contentValues.put(KnownLocationsDbHelper.ALTITUDE, myLocation.altitude);
        contentValues.put(KnownLocationsDbHelper.LATITUDE, myLocation.latLng.latitude);
        contentValues.put(KnownLocationsDbHelper.LONGITUDE, myLocation.latLng.longitude);
        contentValues.put(KnownLocationsDbHelper.RADIUS, myLocation.radius);

        updateId(id, contentValues);
    }

    // TODO: make this method private!
    public static void updateId(long id, ContentValues contentValues) {
        SQLiteDatabase db = getInstance().getOpenDatabase();

        db.update(KnownLocationsDbHelper.TABLE,
                contentValues,
                KnownLocationsDbHelper.C_ID + "=?",
                new String[]{id + ""});

        getInstance().closeDatabase();
    }

    public static MyLocation getMyLocation(long myLocationId) {
        MyLocation myLocation = null;

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(KnownLocationsDbHelper.TABLE,
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
        getInstance().closeDatabase();

        return myLocation;
    }

    public static List<String> getMyLocationNameList() {
        List<String> result = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(KnownLocationsDbHelper.TABLE,
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
        getInstance().closeDatabase();

        return result;
    }

    public static List<NamedLatLng> getLocationsList(ExtremaType extremaType) {
        List<NamedLatLng> startLocations = new LinkedList<>();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(KnownLocationsDbHelper.TABLE,
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
        getInstance().closeDatabase();

        return startLocations;
    }

    public synchronized SQLiteDatabase getOpenDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = cDbHelper.getWritableDatabase();
        }
        return mDatabase;
    }

    public synchronized void closeDatabase() {
        mOpenCounter--;
        if (mOpenCounter == 0) {
            // Closing database
            mDatabase.close();

        }
    }

    public static class MyLocation {
        public long id;
        public LatLng latLng;
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

    public static class NamedLatLng {
        public LatLng latLng;
        public String name;

        public NamedLatLng(LatLng latLng, String name) {
            this.latLng = latLng;
            this.name = name;
        }
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
        public void onCreate(SQLiteDatabase db) {

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

        private final void addColumn(SQLiteDatabase db, String column, String type) {
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN " + column + " " + type + ";");
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

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
