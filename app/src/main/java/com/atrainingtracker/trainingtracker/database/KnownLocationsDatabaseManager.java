/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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

/**
 * Orchestrates the persistent storage and retrieval of known geographical locations.
 *
 * This manager maintains a spatial database of "Known Locations" (Starting points). It
 * features an automated "Altitude Discovery" engine that refines altitude estimates
 * using a weighted moving average of raw barometric samples recorded at the start of
 * each workout.
 *
 * Architectural Role: Spatial knowledge base for sensor calibration.
 * Threading: Methods should generally be called from background contexts to avoid UI block.
 */
public class KnownLocationsDatabaseManager {
    public static final int DEFAULT_RADIUS = 200;
    private static final String TAG = KnownLocationsDatabaseManager.class.getName();
    private static final boolean DEBUG = BANALService.getDebug(false);

    // --- Modern Singleton Pattern ---
    private static volatile KnownLocationsDatabaseManager cInstance;
    private final KnownLocationsDbHelper cDbHelper;
    private SQLiteDatabase mDatabase = null;

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
     * Returns a writable database instance and ensures it remains open.
     * Re-opens if closed (e.g., by a backup process) to prevent IllegalStateException (ATT-289).
     */
    public SQLiteDatabase getDatabase() {
        if (mDatabase != null && mDatabase.isOpen()) {
            return mDatabase;
        }
        synchronized (this) {
            if (mDatabase != null && mDatabase.isOpen()) {
                return mDatabase;
            }
            // If the database was closed, ensure the helper clears its reference
            if (mDatabase != null) {
                cDbHelper.close();
            }
            mDatabase = cDbHelper.getWritableDatabase();
            return mDatabase;
        }
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
    public MyLocation addNewLocation(String name, int altitude, int radius, double latitude, double longitude, @NonNull ExtremaType type) {
        if (DEBUG)
            Log.d(TAG, "addNewLocation: " + name + " " + altitude + " m" + ", radius=" + radius + ", type=" + type);

        MyLocation myLocation = null;

        ContentValues values = new ContentValues();

        values.put(KnownLocationsDbHelper.NAME, name);
        values.put(KnownLocationsDbHelper.ALTITUDE, altitude);
        values.put(KnownLocationsDbHelper.RADIUS, radius);
        values.put(KnownLocationsDbHelper.LONGITUDE, longitude);
        values.put(KnownLocationsDbHelper.LATITUDE, latitude);
        values.put(KnownLocationsDbHelper.EXTREMA_TYPE, type.name());
        values.put(KnownLocationsDbHelper.HIT_COUNT, 1);

        try {
            long id = getDatabase().insert(KnownLocationsDbHelper.TABLE, null, values);
            myLocation = new MyLocation(id, latitude, longitude, name, altitude, radius, 1);
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
                            cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.ALTITUDE)),
                            cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.RADIUS)),
                            cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.HIT_COUNT)));
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
        contentValues.put(KnownLocationsDbHelper.HIT_COUNT, myLocation.hitCount);

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
                    cursor.getDouble(cursor.getColumnIndex(KnownLocationsDbHelper.ALTITUDE)),
                    cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.RADIUS)),
                    cursor.getInt(cursor.getColumnIndex(KnownLocationsDbHelper.HIT_COUNT)));

        }

        cursor.close();

        return myLocation;
    }

    /**
     * ATT-39: Automatically learns or refines a location's altitude.
     * Uses a weighted average to improve estimate over time.
     */
    public void learnLocation(@Nullable LatLng pos, @Nullable Double altitude, @NonNull ExtremaType type) {
        if (pos == null || altitude == null || altitude.isNaN()) return;

        synchronized (this) {
            MyLocation existing = getMyLocation(pos);
            if (existing != null) {
                // Weighted average refinement
                double refinedAlt = (existing.altitude * existing.hitCount + altitude) / (existing.hitCount + 1);
                ContentValues values = new ContentValues();
                values.put(KnownLocationsDbHelper.ALTITUDE, refinedAlt);
                values.put(KnownLocationsDbHelper.HIT_COUNT, existing.hitCount + 1);
                updateId(existing.id, values);
                if (DEBUG) Log.d(TAG, "Refined altitude for '" + existing.name + "': " + refinedAlt + "m (hits: " + (existing.hitCount + 1) + ")");
            } else {
                // New discovery
                String name = "Auto-learned " + type.name().toLowerCase();
                addNewLocation(name, (int) Math.round(altitude), DEFAULT_RADIUS, pos.latitude, pos.longitude, type);
                if (DEBUG) Log.d(TAG, "Discovered new location at " + pos + " with altitude " + altitude + "m");
            }
        }
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
        public double altitude;
        public int radius;
        public int hitCount;

        public MyLocation(long id, double lat, double lng, String name, double altitude, int radius, int hitCount) {
            this.id = id;
            latLng = new LatLng(lat, lng);
            this.name = name;
            this.altitude = altitude;
            this.radius = radius;
            this.hitCount = hitCount;
        }
    }

    public record NamedLatLng(LatLng latLng, String name) {
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    // finally, the database itself
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class KnownLocationsDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "StartLocation2Altitude.db";
        public static final int DB_VERSION = 4;
        public static final String TABLE = "StartLocation2Altitude";
        public static final String C_ID = BaseColumns._ID;
        public static final String NAME = "name";
        public static final String EXTREMA_TYPE = "extremumType";
        public static final String ALTITUDE = "altitude";
        public static final String LONGITUDE = "longitude";
        public static final String LATITUDE = "latitude";
        public static final String RADIUS = "radius";
        public static final String HIT_COUNT = "hitCount";
        protected static final String TAG = KnownLocationsDbHelper.class.getName();
        protected static final boolean DEBUG = BANALService.getDebug(false);
        protected static final String CREATE_TABLE_V4 = "create table " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + NAME + " text,"
                + EXTREMA_TYPE + " text,"
                + ALTITUDE + " real,"
                + LONGITUDE + " real,"
                + LATITUDE + " real,"
                + RADIUS + " int,"
                + HIT_COUNT + " int)";

        // Constructor
        public KnownLocationsDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {
            db.execSQL(CREATE_TABLE_V4);
            if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_TABLE_V4);
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

            if (oldVersion < 4) {
                addColumn(db, HIT_COUNT, "int");
                db.execSQL("ALTER TABLE " + TABLE + " RENAME TO tmp_" + TABLE + ";");
                db.execSQL(CREATE_TABLE_V4);
                db.execSQL("INSERT INTO " + TABLE + " (" + C_ID + ", " + NAME + ", " + EXTREMA_TYPE + ", " + ALTITUDE + ", " + LONGITUDE + ", " + LATITUDE + ", " + RADIUS + ", " + HIT_COUNT + ") " +
                        "SELECT " + C_ID + ", " + NAME + ", " + EXTREMA_TYPE + ", CAST(" + ALTITUDE + " AS REAL), " + LONGITUDE + ", " + LATITUDE + ", " + RADIUS + ", 1 FROM tmp_" + TABLE + ";");
                db.execSQL("DROP TABLE tmp_" + TABLE + ";");
            }
        }
    }


}
