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
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.trainingtracker.TrainingApplication;

import java.io.File;

public class SegmentsDatabaseManager {
    private static final String TAG = SegmentsDatabaseManager.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    // --- Modern Singleton Pattern ---
    private static volatile SegmentsDatabaseManager cInstance;
    private final SegmentsDbHelper cSegmentsDbHelper;
    private final Context mContext;

    private SegmentsDatabaseManager(@NonNull Context context) {
        this.mContext = context.getApplicationContext();
        this.cSegmentsDbHelper = new SegmentsDbHelper(this.mContext);
    }

    @NonNull
    public static SegmentsDatabaseManager getInstance(@NonNull Context context) {
        if (cInstance == null) {
            synchronized (SegmentsDatabaseManager.class) {
                if (cInstance == null) {
                    cInstance = new SegmentsDatabaseManager(context);
                }
            }
        }
        return cInstance;
    }

    /**
     * Returns a writable database instance, managed by the helper.
     */
    // TODO: make private...
    public SQLiteDatabase getDatabase() {
        return cSegmentsDbHelper.getWritableDatabase();
    }
    // --- End of Singleton Pattern ---

    public static boolean doesDatabaseExist(@NonNull Context context) {
        File dbFile = context.getDatabasePath(SegmentsDbHelper.DB_NAME);
        return dbFile.exists();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level helper methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Deletes all tables and effectively resets the database.
     * Note: This is a destructive operation.
     */
    public void deleteAllTables() {
        try {
            SQLiteDatabase db = getDatabase();
            db.beginTransaction();
            try {
                db.execSQL("DROP TABLE IF EXISTS " + Segments.TABLE_STARRED_SEGMENTS);
                db.execSQL("DROP TABLE IF EXISTS " + Segments.TABLE_SEGMENT_STREAMS);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            // Recreate the tables
            cSegmentsDbHelper.onCreate(db);
            if (DEBUG) Log.d(TAG, "All segment tables deleted and recreated.");
        } catch (SQLException e) {
            Log.e(TAG, "Error deleting all tables in SegmentsDatabase", e);
        }
    }


    public static final class Segments {
        public static final String TABLE_STARRED_SEGMENTS = "StarredSegmentsTable";
        public static final String TABLE_SEGMENT_STREAMS = "SegmentStreams";


        // for TABLE_STARRED_SEGMENTS
        public static final String C_ID = BaseColumns._ID;
        public static final String SEGMENT_ID = "SegmentId";     // id: 	integer
        public static final String RESOURCE_STATE = "ResourceState"; // resource_state: 	integer // resource_state: 	integer
        public static final String SEGMENT_NAME = "SegmentName";   // name: 	string
        public static final String ACTIVITY_TYPE = "ActivityType";  // activity_type: 	string ‘Ride’ or ‘Run’
        public static final String DISTANCE = "Distance";      // distance: 	float meters
        public static final String AVERAGE_GRADE = "AverageGrade";  // average_grade: 	float percent
        public static final String MAXIMUM_GRADE = "MaximumGrade";  // maximum_grade: 	float percent
        public static final String ELEVATION_HIGH = "ElevationHigh"; // elevation_high: 	float meters
        public static final String ELEVATION_LOW = "ElevationLow";  // elevation_low: 	float meters
        public static final String START_LATITUDE = "StartLatitude";
        public static final String START_LONGITUDE = "StartLongitude";
        public static final String END_LATITUDE = "EndLatitude";
        public static final String END_LONGITUDE = "EndLongitude";
        public static final String CLIMB_CATEGORY = "ClimbCategory"; // climb_category: 	integer [0, 5], higher is harder ie. 5 is Hors catégorie, 0 is uncategorized
        public static final String CITY = "City";          // city: 	string
        public static final String STATE = "State";         // state: 	string
        public static final String COUNTRY = "Country";       // country: 	string
        public static final String PRIVATE = "Private";       // private: 	boolean
        public static final String STARRED = "Starred";       // starred: 	boolean
        public static final String HAZARDOUS = "Hazardous";     // hazardous: boolean
        public static final String PR_TIME = "pr_time";


        // for TABLE_SEGMENT_STREAMS
        // SEGMENT_ID
        // DISTANCE
        public static final String ALTITUDE = "Altitude";
        public static final String LATITUDE = "Latitude";
        public static final String LONGITUDE = "Longitude";
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The database itself
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static class SegmentsDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "Segments.db";
        // public static final int DB_VERSION = 1; // created  3.8.2016
        // public static final int DB_VERSION = 2; // updated 19.8.2016
        // public static final int DB_VERSION = 3; // updated 26.9.2016
        public static final int DB_VERSION = 5; // updated 11.01.2026: add PR_TIME
        protected static final String CREATE_TABLE_STARRED_SEGMENTS_V2 = "create table " + Segments.TABLE_STARRED_SEGMENTS + " ("
                + Segments.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Segments.SEGMENT_ID + " int, "
                + Segments.RESOURCE_STATE + " int, "
                + Segments.SEGMENT_NAME + " text, "
                + Segments.ACTIVITY_TYPE + " text, "  // activity_type: 	string ‘Ride’ or ‘Run’
                + Segments.DISTANCE + " real, "
                + Segments.AVERAGE_GRADE + " real, "
                + Segments.MAXIMUM_GRADE + " real, "
                + Segments.ELEVATION_HIGH + " real, "
                + Segments.ELEVATION_LOW + " real, "
                + Segments.START_LATITUDE + " real, "
                + Segments.START_LONGITUDE + " real, "
                + Segments.END_LATITUDE + " real, "
                + Segments.END_LONGITUDE + " real, "
                + Segments.CLIMB_CATEGORY + " int, "   // climb_category: 	integer [0, 5], higher is harder ie. 5 is Hors catégorie, 0 is uncategorized
                + Segments.CITY + " text, "
                + Segments.STATE + " text, "
                + Segments.COUNTRY + " text, "
                + Segments.PRIVATE + " int, "
                + Segments.STARRED + " int, "
                + Segments.HAZARDOUS + " int, "
                + Segments.PR_TIME + " int)";


        protected static final String CREATE_TABLE_SEGMENT_STREAMS_V1 = "create table " + Segments.TABLE_SEGMENT_STREAMS + " ("
                + Segments.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Segments.SEGMENT_ID + " int, "
                + Segments.DISTANCE + " real, "
                + Segments.ALTITUDE + " real, "
                + Segments.LATITUDE + " real, "
                + Segments.LONGITUDE + " real)";
        private static final String TAG = SegmentsDbHelper.class.getName();
        private static final boolean DEBUG = TrainingApplication.getDebug(true);


        // Constructor
        public SegmentsDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);

        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE_STARRED_SEGMENTS_V2);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_STARRED_SEGMENTS_V2);

            db.execSQL(CREATE_TABLE_SEGMENT_STREAMS_V1);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_SEGMENT_STREAMS_V1);

        }

        private void addColumn(@NonNull SQLiteDatabase db, String table, String column, String type) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {

            // since this database is only a cache for online data, its upgrade policy is
            // to simply to discard the data and start over

            db.execSQL("drop table if exists " + Segments.TABLE_STARRED_SEGMENTS);
            db.execSQL("drop table if exists " + Segments.TABLE_SEGMENT_STREAMS);

            onCreate(db);  // run onCreate to get new database
        }
    }
}
