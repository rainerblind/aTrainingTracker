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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.trainingtracker.TrainingApplication;

public class LapsDatabaseManager {

    private static final String TAG = LapsDatabaseManager.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    // --- Singleton Pattern Implementation ---

    private static LapsDbHelper cLapsDbHelper;
    private static volatile LapsDatabaseManager cInstance;

    // Private constructor to prevent direct instantiation
    private LapsDatabaseManager(@NonNull Context context) {
        // The helper is instantiated with the application context to prevent leaks.
        cLapsDbHelper = new LapsDbHelper(context.getApplicationContext());
    }

    /**
     * Gets the single, thread-safe instance of the LapsDatabaseManager.
     *
     * @param context Any context, will be converted to application context.
     * @return The singleton instance.
     */
    @NonNull
    public static LapsDatabaseManager getInstance(@NonNull Context context) {
        // Use double-checked locking for thread-safe lazy initialization.
        if (cInstance == null) {
            synchronized (LapsDatabaseManager.class) {
                if (cInstance == null) {
                    cInstance = new LapsDatabaseManager(context);
                }
            }
        }
        return cInstance;
    }

    /**
     * Returns a writable database instance, managed by the helper.
     * This is the only method that should be used to get a database object.
     * It's thread-safe.
     * @return A thread-safe SQLiteDatabase instance.
     */
    public SQLiteDatabase getDatabase() {
        return cLapsDbHelper.getWritableDatabase();
    }

    // --- End of Singleton Pattern ---


    // --- High-level helper methods ---

    /**
     * Creates and saves a new lap entry in the database.
     * This is the preferred way to create a new lap.
     * @param workoutId The ID of the workout this lap belongs to.
     * @param lapNr The number of the lap.
     * @param lapTime The total time of the lap in seconds.
     * @param lapDistance The total distance of the lap in meters.
     * @param averageSpeed The average speed of the lap in m/s.
     */
    public void saveLap(long workoutId, long lapNr, int lapTime, double lapDistance, double averageSpeed) {
        if (DEBUG)
            Log.i(TAG, "saveLap: workoutId=" + workoutId + ", lapNr=" + lapNr + ", lapTime=" + lapTime + ", lapDistance=" + lapDistance);

        // Create and fill content values
        ContentValues values = new ContentValues();
        values.put(Laps.WORKOUT_ID, workoutId);
        values.put(Laps.LAP_NR, lapNr);
        // values.put(Laps.TIME_START, done automatically by DB trigger);
        values.put(Laps.TIME_TOTAL_s, lapTime);
        values.put(Laps.DISTANCE_TOTAL_m, lapDistance);
        values.put(Laps.SPEED_AVERAGE_mps, averageSpeed);

        try {
            getDatabase().insertOrThrow(Laps.TABLE, null, values);
        } catch (Exception e) {
            Log.e(TAG, "Error saving lap for workoutId: " + workoutId, e);
        }
    }

    /**
     * Deletes all lap data associated with a specific workoutId.
     * This is the preferred way to delete laps, as it encapsulates the logic.
     * @param workoutId The ID of the workout to delete laps for.
     */
    public void deleteWorkout(long workoutId) {
        SQLiteDatabase db = getDatabase();
        try {
            int rowsAffected = db.delete(Laps.TABLE, Laps.WORKOUT_ID + "=?", new String[]{String.valueOf(workoutId)});
            if (DEBUG) Log.d(TAG, "Deleted " + rowsAffected + " laps for workoutId: " + workoutId);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting laps for workoutId: " + workoutId, e);
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
