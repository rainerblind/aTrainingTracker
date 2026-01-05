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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;

public class LapsDatabaseManager {

    private static LapsDatabaseManager cInstance;
    private static LapsDbHelper cLapsDbHelper;
    private int mOpenCounter;
    private SQLiteDatabase mDatabase;

    public static synchronized void initializeInstance(LapsDbHelper lapsDbHelper) {
        if (cInstance == null) {
            cInstance = new LapsDatabaseManager();
            cLapsDbHelper = lapsDbHelper;
        }
    }

    @NonNull
    public static synchronized LapsDatabaseManager getInstance() {
        if (cInstance == null) {
            throw new IllegalStateException(LapsDatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return cInstance;
    }

    public synchronized SQLiteDatabase getOpenDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = cLapsDbHelper.getWritableDatabase();
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

    // the columns of the table
    public static final class Laps {
        public static final String TABLE = "Laps";

        public static final String C_ID = BaseColumns._ID;
        public static final String WORKOUT_ID = "workoutID";
        public static final String LAP_NR = "lapNr";
        public static final String TIME_START = "timeStart";
        public static final String TIME_TOTAL_s = "timeTotal_s";
        public static final String DISTANCE_TOTAL_m = "distanceTotal_m";
        public static final String SPEED_AVERAGE_mps = "speedAverage_mps";
    }

    public static class LapsDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "Laps.db";
        public static final int DB_VERSION = 1;
        protected static final String CREATE_TABLE = "create table " + Laps.TABLE + " ("
                + Laps.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + Laps.WORKOUT_ID + " int,"
                + Laps.LAP_NR + " int,"
                + Laps.TIME_START + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + Laps.TIME_TOTAL_s + " int,"
                + Laps.DISTANCE_TOTAL_m + " real,"
                + Laps.SPEED_AVERAGE_mps + " real)";
        private static final String TAG = "LapsDbHelper";
        private static final boolean DEBUG = false;

        // Constructor
        public LapsDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE);

            if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_TABLE);
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO: alter table instead of deleting!

            db.execSQL("drop table if exists " + Laps.TABLE);

            if (DEBUG) Log.d(TAG, "onUpgraded");
            onCreate(db);  // run onCreate to get new database
        }
    }

}
