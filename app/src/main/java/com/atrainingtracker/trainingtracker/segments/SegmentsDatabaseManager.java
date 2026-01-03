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
    private static SegmentsDatabaseManager cInstance;
    private static SegmentsDbHelper cSegmentsDbHelper;
    private int mOpenCounter;
    private SQLiteDatabase mDatabase;

    public static synchronized void initializeInstance(SegmentsDbHelper segmentsDbHelper) {
        if (cInstance == null) {
            cInstance = new SegmentsDatabaseManager();
            cSegmentsDbHelper = segmentsDbHelper;
        }
    }

    @NonNull
    public static synchronized SegmentsDatabaseManager getInstance() {
        if (cInstance == null) {
            throw new IllegalStateException(SegmentsDatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return cInstance;
    }

    public static boolean doesDatabaseExist(@NonNull Context context) {
        File dbFile = context.getDatabasePath(SegmentsDbHelper.DB_NAME);
        return dbFile.exists();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level helper methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static void deleteAllTables(@NonNull Context context) {

        SQLiteDatabase db = getInstance().getOpenDatabase();

        db.execSQL("drop table if exists " + Segments.TABLE_SEGMENT_LEADERBOARD);
        db.execSQL("drop table if exists " + Segments.TABLE_STARRED_SEGMENTS);
        db.execSQL("drop table if exists " + Segments.TABLE_EFFORT_STREAMS);
        db.execSQL("drop table if exists " + Segments.TABLE_SEGMENT_STREAMS);

        (new SegmentsDbHelper(null)).onCreate(db);  // run onCreate to get new database

        getInstance().closeDatabase();

        context.deleteDatabase(SegmentsDbHelper.DB_NAME);
    }

    public synchronized SQLiteDatabase getOpenDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = cSegmentsDbHelper.getWritableDatabase();
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

    public static final class Segments {
        public static final String TABLE_STARRED_SEGMENTS = "StarredSegmentsTable";
        public static final String TABLE_SEGMENT_LEADERBOARD = "SegmentLeaderboardTable";
        public static final String TABLE_SEGMENT_STREAMS = "SegmentStreams";
        public static final String TABLE_EFFORT_STREAMS = "EffortStreams";


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
        public static final String LAST_UPDATED = "LastUpdated";
        public static final String PR_TIME = "PRTime";
        public static final String PR_DATE = "PRDate";
        public static final String OWN_RANK = "OwnRank";
        public static final String LEADERBOARD_SIZE = "LeaderboardSize";


        // for TABLE_SEGMENT_LEADERBOARD
        public static final String ATHLETE_NAME = "AthleteName";    // "athlete_name": "Jim Whimpey",
        public static final String ATHLETE_ID = "AthleteID";      // "athlete_id": 123529,
        public static final String ATHLETE_GENDER = "AthleteGender";  // "athlete_gender": "M",
        public static final String AVERAGE_HR = "AverageHr";      // "average_hr": 190.5,
        public static final String AVERAGE_WATTS = "AverageWatts";   // "average_watts": 460.8,
        // public static final String DISTANCE         = "Distance";       // "distance": 2659.89,
        public static final String ELAPSED_TIME = "ElapsedTime";    // "elapsed_time": 360,
        public static final String MOVING_TIME = "MovingTime";     // "moving_time": 360,
        public static final String START_TIME = "StartTime";      // "start_date": "2013-03-29T13:49:35Z",
        public static final String START_TIME_LOCAL = "StartTimeLocal"; // "start_date_local": "2013-03-29T06:49:35Z",
        public static final String ACTIVITY_ID = "ActivityId";     // "activity_id": 46320211,
        public static final String EFFORT_ID = "EffortId";       // "effort_id": 801006623,
        public static final String RANK = "Rank";           // "rank": 1,
        public static final String ATHLETE_PROFILE_URL = "AthleteProfileURL"; // "athlete_profile": "http://pics.com/227615/large.jpg"

        // for TABLE_SEGMENT_STREAMS
        // SEGMENT_ID
        // DISTANCE
        public static final String ALTITUDE = "Altitude";
        public static final String LATITUDE = "Latitude";
        public static final String LONGITUDE = "Longitude";

        // for TABLE_EFFORT_STREAMS
        // public static final String EFFORT_ID = "EffortId";
        // LATITUDE
        // LONGITUDE
        // DISTANCE
        public static final String VELOCITY_SMOOTH = "VelocitySmooth";
        public static final String HEART_RATE = "HeartRate";
        public static final String CADENCE = "Cadence";
        public static final String WATTS = "Watts";
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The database itself
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static class SegmentsDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "Segments.db";
        // public static final int DB_VERSION = 1; // created  3.8.2016
        // public static final int DB_VERSION = 2; // updated 19.8.2016
        public static final int DB_VERSION = 3; // updated 26.9.2016
        protected static final String CREATE_TABLE_STARRED_SEGMENTS_V1 = "create table " + Segments.TABLE_STARRED_SEGMENTS + " ("
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

                + Segments.LAST_UPDATED + " datetime, "
                + Segments.PR_TIME + " int, "
                + Segments.PR_DATE + " datetime, "
                + Segments.OWN_RANK + " int, "
                + Segments.LEADERBOARD_SIZE + " int)";
        protected static final String CREATE_TABLE_SEGMENT_LEADERBOARD_V1 = "create table " + Segments.TABLE_SEGMENT_LEADERBOARD + " ("
                + Segments.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Segments.SEGMENT_ID + " int, "
                + Segments.ATHLETE_NAME + " text, "    // "athlete_name": "Jim Whimpey",
                + Segments.ATHLETE_ID + " int, "     // "athlete_id": 123529,
                + Segments.ATHLETE_GENDER + " text, "    // "athlete_gender": "M",
                + Segments.AVERAGE_HR + " real, "    // "average_hr": 190.5,
                + Segments.AVERAGE_WATTS + " real, "    // "average_watts": 460.8,
                + Segments.DISTANCE + " real, "    // "distance": 2659.89,
                + Segments.ELAPSED_TIME + " int, "     // "elapsed_time": 360,
                + Segments.MOVING_TIME + " int, "     // "moving_time": 360,
                + Segments.START_TIME + " text, "    // "start_date":       "2013-03-29T13:49:35Z",
                + Segments.START_TIME_LOCAL + " text, "    // "start_date_local": "2013-03-29T06:49:35Z",
                + Segments.ACTIVITY_ID + " int, "     // "activity_id": 46320211,
                + Segments.EFFORT_ID + " int, "     // "effort_id": 801006623,
                + Segments.RANK + " int, "     // "rank": 1,
                + Segments.ATHLETE_PROFILE_URL + " text)"; // "athlete_profile": "http://pics.com/227615/large.jpg"
        protected static final String CREATE_TABLE_SEGMENT_STREAMS_V1 = "create table " + Segments.TABLE_SEGMENT_STREAMS + " ("
                + Segments.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Segments.SEGMENT_ID + " int, "
                + Segments.DISTANCE + " real, "
                + Segments.ALTITUDE + " real, "
                + Segments.LATITUDE + " real, "
                + Segments.LONGITUDE + " real)";
        protected static final String CREATE_TABLE_EFFORT_STREAMS_V1 = "create table " + Segments.TABLE_EFFORT_STREAMS + " ("
                + Segments.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Segments.EFFORT_ID + " int, "
                + Segments.LATITUDE + " real, "
                + Segments.LONGITUDE + " real, "
                + Segments.DISTANCE + " real, "
                + Segments.VELOCITY_SMOOTH + " real, "
                + Segments.HEART_RATE + " int, "
                + Segments.CADENCE + " int, "
                + Segments.WATTS + " real)";
        private static final String TAG = SegmentsDbHelper.class.getName();
        private static final boolean DEBUG = TrainingApplication.getDebug(true);


        // Constructor
        public SegmentsDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);

        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE_STARRED_SEGMENTS_V1);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_STARRED_SEGMENTS_V1);

            db.execSQL(CREATE_TABLE_SEGMENT_LEADERBOARD_V1);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_SEGMENT_LEADERBOARD_V1);

            db.execSQL(CREATE_TABLE_SEGMENT_STREAMS_V1);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_SEGMENT_STREAMS_V1);

            db.execSQL(CREATE_TABLE_EFFORT_STREAMS_V1);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_EFFORT_STREAMS_V1);

        }

        private void addColumn(@NonNull SQLiteDatabase db, String table, String column, String type) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {

            if (newVersion <= 3) {  // simply clear everything

                db.execSQL("drop table if exists " + Segments.TABLE_SEGMENT_LEADERBOARD);
                db.execSQL("drop table if exists " + Segments.TABLE_STARRED_SEGMENTS);
                db.execSQL("drop table if exists " + Segments.TABLE_EFFORT_STREAMS);
                db.execSQL("drop table if exists " + Segments.TABLE_SEGMENT_STREAMS);

                onCreate(db);  // run onCreate to get new database
            }
        }
    }
}
