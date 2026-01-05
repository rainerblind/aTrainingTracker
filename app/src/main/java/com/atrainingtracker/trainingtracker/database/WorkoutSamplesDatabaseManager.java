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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public class WorkoutSamplesDatabaseManager {
    private static final String TAG = WorkoutSamplesDatabaseManager.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static WorkoutSamplesDatabaseManager cInstance;
    private static WorkoutSamplesDbHelper cWorkoutSamplesDbHelper;
    private int mOpenCounter;
    private SQLiteDatabase mDatabase;

    public static synchronized void initializeInstance(WorkoutSamplesDbHelper workoutSamplesDbHelper) {
        if (cInstance == null) {
            cInstance = new WorkoutSamplesDatabaseManager();
            cWorkoutSamplesDbHelper = workoutSamplesDbHelper;
        }
    }

    @NonNull
    public static synchronized WorkoutSamplesDatabaseManager getInstance() {
        if (cInstance == null) {
            throw new IllegalStateException(WorkoutSamplesDatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return cInstance;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level helper methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @Nullable
    public static Double calcExtremaValue(String baseFileName, @NonNull ExtremaType extremaType, @NonNull SensorType sensorType) {
        if (DEBUG)
            Log.i(TAG, "calcExtremaValue(" + baseFileName + ", " + extremaType.name() + ", " + sensorType.name() + ")");

        // first, a special case: when asked to calc the values for the pace,
        // we return the inverse of the corresponding extrema value of the speed.
        if (sensorType == SensorType.PACE_spm) {
            Double speed = calcExtremaValue(baseFileName, extremaType, SensorType.SPEED_mps);
            if (speed != null) {
                return 1 / speed;
            } else {
                return null;
            }
        }

        // next special case: average speed.
        // here, we simply use the time and distance to calc the average speed
        if ((extremaType == ExtremaType.AVG) & (sensorType == SensorType.SPEED_mps)) {
            if (DEBUG) Log.i(TAG, "calculating average speed based on distance and active time");

            WorkoutSummariesDatabaseManager.getInstance();
            Double distance = WorkoutSummariesDatabaseManager.getDouble(baseFileName, WorkoutSummaries.DISTANCE_TOTAL_m);
            WorkoutSummariesDatabaseManager.getInstance();
            Integer time = WorkoutSummariesDatabaseManager.getInt(baseFileName, WorkoutSummaries.TIME_ACTIVE_s);

            if (distance != null & time != null) {
                if (DEBUG)
                    Log.i(TAG, "calculating average speed: distance=" + distance + ", time=" + time + " => avg speed= " + distance / time + " m/s = " + distance / time * 3.6 + " km/h");
                // Toast.makeText(mContext, "calculating average speed: distance=" + distance + ", time=" + time + " => avg speed= " + distance/time, Toast.LENGTH_LONG).show();
                return distance / time;
            } else {
                return null;
            }
        }


        // in all other cases, we let sqlite do the job
        Double extremaValue = null;
        Cursor cursor = null;
        SQLiteDatabase db = getInstance().getOpenDatabase();

        // it might be possible that the corresponding sensor is not a column of the database, so we first check this
        if (existsColumnInTable(db, getTableName(baseFileName), sensorType.name())) {

            switch (extremaType) {
                case MAX:
                case AVG:
                case MIN:
                    String[] columns = new String[]{extremaType.name() + "(" + sensorType.name() + ")"};

                    cursor = db.query(getTableName(baseFileName),
                            columns,  // columns,
                            null, // selection, (here something like MAX(?))
                            null, // new String[] {sensorType.name()}, // selectionArgs,
                            null, null, null); // groupBy, having, orderBy)

                    cursor.moveToFirst();
                    extremaValue = cursor.getDouble(0);
                    break;

                case START:
                    cursor = db.query(getTableName(baseFileName),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);  // sorting?

                    while (cursor.moveToNext() && extremaValue == null) {
                        if (dataValid(cursor, sensorType.name())) {
                            if (DEBUG) Log.i(TAG, "got start value");
                            extremaValue = cursor.getDouble(cursor.getColumnIndex(sensorType.name()));
                        }
                    }
                    break;

                case END:
                    cursor = db.query(getTableName(baseFileName),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);  // sorting?

                    cursor.moveToLast();
                    while (cursor.moveToPrevious() && extremaValue == null) {
                        if (dataValid(cursor, sensorType.name())) {
                            if (DEBUG) Log.i(TAG, "got end location");
                            extremaValue = cursor.getDouble(cursor.getColumnIndex(sensorType.name()));
                        }
                    }
                    break;
            }
        }

        getInstance().closeDatabase(); // if (db.isOpen()) { db.close(); }
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }

        if (DEBUG) Log.i(TAG, "extremaValue: " + extremaValue);
        return extremaValue;
    }

    // since this method goes through all? samples, this might take long.
    public static double calcAverageAroundLocation(@NonNull LatLng center, double radius, @NonNull SensorType sensorType) {
        // based on http://stackoverflow.com/questions/3695224/sqlite-getting-nearest-locations-with-latitude-and-longitude

        Location centerLocation = new Location("center");
        centerLocation.setLatitude(center.latitude);
        centerLocation.setLongitude(center.longitude);

        Location testLocation = new Location("test");

        // PointF center = new PointF(x, y);
        final double mult = 1; // mult = 1.1; is more reliable
        LatLng p1 = calculateDerivedPosition(center, mult * radius, 0);
        LatLng p2 = calculateDerivedPosition(center, mult * radius, 90);
        LatLng p3 = calculateDerivedPosition(center, mult * radius, 180);
        LatLng p4 = calculateDerivedPosition(center, mult * radius, 270);

        // SQLiteDatabase db = getInstance().getOpenDatabase();
        // Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);  TODO: seems to be not that simple!

        double valueSum = 0.0;
        int counter = 0;
        double average = 0.0;

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
        Cursor summariesCursor = summariesDb.query(WorkoutSummaries.TABLE,
                new String[]{WorkoutSummaries.FILE_BASE_NAME},
                null, null,
                null, null, null);


        while (summariesCursor.moveToNext()) {
            String name = summariesCursor.getString(0);
            if (DEBUG) Log.i(TAG, "querying table: " + name);

            SQLiteDatabase samplesDb = getInstance().getOpenDatabase();
            Cursor samplesCursor = samplesDb.query(getTableName(name), // Table
                    new String[]{SensorType.LATITUDE.name(), SensorType.LONGITUDE.name(), sensorType.name()}, // columns
                    SensorType.LATITUDE.name() + " > ? AND " +
                            SensorType.LATITUDE.name() + " < ? AND " +
                            SensorType.LONGITUDE.name() + " > ? AND " +
                            SensorType.LONGITUDE.name() + " < ?", // selection
                    new String[]{Double.toString(p3.latitude), Double.toString(p1.latitude), Double.toString(p4.longitude), Double.toString(p2.longitude)},
                    null, null, null);

            if (DEBUG)
                Log.i(TAG, "got cursor with " + samplesCursor.getCount() + " entries for " + name);

            // cache the indexes
            int latIndex = samplesCursor.getColumnIndex(SensorType.LATITUDE.name());
            int lonIndex = samplesCursor.getColumnIndex(SensorType.LONGITUDE.name());
            int sensorIndex = samplesCursor.getColumnIndex(sensorType.name());

            while (samplesCursor.moveToNext()) {
                if (dataValid(samplesCursor, sensorType.name())
                        && dataValid(samplesCursor, SensorType.LATITUDE.name())
                        && dataValid(samplesCursor, SensorType.LONGITUDE.name())) {
                    testLocation.setLatitude(samplesCursor.getDouble(latIndex));
                    testLocation.setLongitude(samplesCursor.getDouble(lonIndex));

                    if (centerLocation.distanceTo(testLocation) <= radius) {
                        valueSum += samplesCursor.getDouble(sensorIndex);
                        counter++;
                    }
                }
            }
            samplesCursor.close();
        }
        summariesCursor.close();

        getInstance().closeDatabase();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase();

        if (counter != 0) {
            average = valueSum / counter;
        }

        return average;
    }

    /**
     * Calculates the end-point from a given source at a given range (meters)
     * and bearing (degrees). This methods uses simple geometry equations to
     * calculate the end-point.
     *
     * @param point   Point of origin
     * @param range   Range in meters
     * @param bearing Bearing in degrees
     * @return End-point from the source given the desired range and bearing.
     */
    @NonNull
    public static LatLng calculateDerivedPosition(@NonNull LatLng point,
                                                  double range, double bearing) {
        double EarthRadius = 6371000; // m

        double latA = Math.toRadians(point.latitude);
        double lonA = Math.toRadians(point.longitude);
        double angularDistance = range / EarthRadius;
        double trueCourse = Math.toRadians(bearing);

        double lat = Math.asin(
                Math.sin(latA) * Math.cos(angularDistance) +
                        Math.cos(latA) * Math.sin(angularDistance)
                                * Math.cos(trueCourse));

        double dlon = Math.atan2(
                Math.sin(trueCourse) * Math.sin(angularDistance)
                        * Math.cos(latA),
                Math.cos(angularDistance) - Math.sin(latA) * Math.sin(lat));

        double lon = ((lonA + dlon + Math.PI) % (Math.PI * 2)) - Math.PI;

        lat = Math.toDegrees(lat);
        lon = Math.toDegrees(lon);

        return new LatLng(lat, lon);
    }

    @Nullable
    public static LatLngValue getExtremaPosition(long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType) {
        if (DEBUG)
            Log.i(TAG, "getExtremaPosition for " + extremaType.name() + " " + sensorType.name());

        // TODO: obviously, this does not make sense for AVG

        LatLngValue result = null;

        // WorkoutSummariesDbHelper summariesDbHelper = new WorkoutSummariesDbHelper(mContext);
        String baseFileName = WorkoutSummariesDatabaseManager.getBaseFileName(workoutId);

        // first, get the extrema value
        Double extremaValue = WorkoutSummariesDatabaseManager.getExtremaValue(workoutId, sensorType, extremaType);
        if (DEBUG) Log.i(TAG, "got " + extremaValue);

        // if there is an extrema value, we look for its location
        if (extremaValue != null) {

            WorkoutSamplesDatabaseManager databaseManager = WorkoutSamplesDatabaseManager.getInstance();
            SQLiteDatabase samplesDb = databaseManager.getOpenDatabase();
            Cursor cursor = null;

            // depending on the extremaType, there are two alternative ways to find the corresponding row
            switch (extremaType) {
                case MIN:
                case MAX:
                    if (DEBUG)
                        Log.i(TAG, "special case: MIN or MAX => find by 'sorting' and only picking the first row");
                    // in previous versions, we used the 'default' code for this but it failed for max of line_distance
                    // probably due to problems with converting and storing doubles?

                    String orderBy = sensorType.name() + " " + ((extremaType == ExtremaType.MAX) ? "DESC" : "ASC");
                    cursor = samplesDb.query(WorkoutSamplesDatabaseManager.getTableName(baseFileName), // table
                            null, // columns
                            null, // selection
                            null, // selectionArgs
                            null, // groupBy
                            null, // having
                            orderBy, // orderBy
                            "1"); // limit: we only need the first one

                    break;
                default:  // neither MIN nor MAX
                    if (DEBUG)
                        Log.i(TAG, "default case: search for the extrema value which should be already within the summariesDb");

                    cursor = samplesDb.query(WorkoutSamplesDatabaseManager.getTableName(baseFileName), // table
                            null, // columns
                            sensorType.name() + "=?", // selection
                            new String[]{extremaValue.toString()}, // selectionArgs
                            null, // groupBy
                            null, // having
                            null, // orderBy
                            "1"); // limit  TODO: might be increased in the future?
            } // end of switch

            if (cursor.moveToFirst()
                    && !cursor.isNull(cursor.getColumnIndex(SensorType.LATITUDE.name()))
                    && !cursor.isNull(cursor.getColumnIndex(SensorType.LONGITUDE.name()))) {
                if (DEBUG)
                    Log.i(TAG, "got a valid location for " + extremaType.name() + " of " + sensorType.name());
                result = new LatLngValue(new LatLng(cursor.getDouble(cursor.getColumnIndex(SensorType.LATITUDE.name())),
                        cursor.getDouble(cursor.getColumnIndex(SensorType.LONGITUDE.name()))),
                        cursor.getDouble(cursor.getColumnIndex(sensorType.name())));
            } else {
                if (DEBUG)
                    Log.d(TAG, "did not get a valid location for " + extremaType.name() + " of " + sensorType.name());
            }

            // clean up
            cursor.close();
            databaseManager.closeDatabase(); // samplesDb.close();

        } else {
            if (DEBUG)
                Log.i(TAG, "there was no valid extrema value for " + extremaType.name() + " of " + sensorType.name());
        }

        // finally, return the result
        return result;
    }

    public static void createNewTable(String workoutName, @NonNull List<SensorType> sensorTypes) {
        if (DEBUG) Log.d(TAG, "createNewTable: " + WorkoutSamplesDbHelper.DB_NAME);

        SQLiteDatabase db = getInstance().getOpenDatabase();
        String table = getTableName(workoutName);

        // first of all, delete an existing db.
        // this is necessary when the user starts tracking twice within one minute (might be due to failures) (really necessary???)
        db.execSQL("drop table if exists " + table);

        String execSQL = "create table " + table + "(" + makeColumns(sensorTypes) + ")";
        if (DEBUG) Log.d(TAG, "execSQL: " + execSQL);

        db.execSQL(execSQL);
        getInstance().closeDatabase(); // db.close();
    }

    public static void deleteWorkout(String baseFileName) {
        SQLiteDatabase db = getInstance().getOpenDatabase();// getWritableDatabase();
        db.execSQL("drop table if exists " + getTableName(baseFileName));
        getInstance().closeDatabase(); // db.close();
    }

    // stolen from http://stackoverflow.com/questions/4719594/checking-if-a-column-exists-in-an-application-database-in-android
    private static boolean existsColumnInTable(@NonNull SQLiteDatabase inDatabase, String inTable, String columnToCheck) {
        Cursor mCursor = null;
        try {
            // Query 1 row
            mCursor = inDatabase.rawQuery("SELECT * FROM " + inTable + " LIMIT 0", null);

            // getColumnIndex() gives us the index (0 to ...) of the column - otherwise we get a -1
            return mCursor.getColumnIndex(columnToCheck) != -1;

        } catch (Exception Exp) {
            // Something went wrong. Missing the database? The table?
            Log.d("... - existsColumnInTable", "When checking whether a column exists in the table, an error occurred: " + Exp.getMessage());
            return false;
        } finally {
            if (mCursor != null) mCursor.close();
        }
    }

    /**
     * stolen from BaseExporter
     */
    protected static boolean dataValid(@NonNull Cursor cursor, String string) {
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

    @NonNull
    public static String getTableName(String workoutName) {
        return "\"" + workoutName + "\"";
    }

    @NonNull
    protected static String makeColumns(@NonNull List<SensorType> sensorTypes) {
        StringBuilder result = new StringBuilder(WorkoutSamplesDbHelper.BASE_COLUMNS);

        for (SensorType sensorType : sensorTypes) {
            switch (sensorType.getSensorValueType()) {
                case INTEGER:
                    result.append(", ").append(sensorType.name()).append(" int");
                    break;
                case DOUBLE:
                    result.append(", ").append(sensorType.name()).append(" real");
                    break;
                case STRING:
                    result.append(", ").append(sensorType.name()).append(" text");
                    break;
                default:
                    if (DEBUG) Log.d(TAG, "WTF: this should never ever happen: unknown type");
            }
        }

        return result.toString();
    }


    /**
     * helper method to check whether there is some data
     */

    public synchronized SQLiteDatabase getOpenDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = cWorkoutSamplesDbHelper.getWritableDatabase();
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

    public static class LatLngValue {
        public final LatLng latLng;
        public final Double value;

        public LatLngValue(LatLng latLng_, Double value_) {
            latLng = latLng_;
            value = value_;
        }
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The database itself
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public static class WorkoutSamplesDbHelper extends SQLiteOpenHelper {
        public static final int DB_VERSION = 1;
        public static final String DB_NAME = "WorkoutSamples.db";
        public static final String C_ID = BaseColumns._ID;
        public static final String TIME = "time";
        protected static final String BASE_COLUMNS = C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + TIME + " DATETIME DEFAULT CURRENT_TIMESTAMP";
        static final String TAG = "WorkoutSamplesDbHelper";
        static final boolean DEBUG = TrainingApplication.getDebug(true);
        protected final Context mContext;

        // Constructor
        public WorkoutSamplesDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
            if (DEBUG) Log.d(TAG, "WorkoutSamplesDbHelper: " + DB_NAME);

            mContext = context;
        }


        /**
         * Called only once, first time the DB is created
         **/
        @Override
        public void onCreate(SQLiteDatabase db) {

            if (DEBUG) Log.d(TAG, "onCreated sql");

            // db.execSQL(mCreateTable);

        }


        //Called whenever newVersion != oldVersion
        // since we create a new table for each workout, this should never be called.
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO: alter table instead of deleting!

            // db.execSQL("drop table if exists " + TABLE);
            // TODO: somehow, we should delete all tables?

            if (DEBUG) Log.d(TAG, "onUpgraded");
            onCreate(db);  // run onCreate to get new database
        }


    }
}
