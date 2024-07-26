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

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.google.android.gms.maps.model.LatLng;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class WorkoutSummariesDatabaseManager {
    private static final String TAG = WorkoutSummariesDatabaseManager.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    private static WorkoutSummariesDatabaseManager cInstance;
    private static WorkoutSummariesDbHelper cWorkoutSummariesDbHelper;
    private int mOpenCounter;
    private SQLiteDatabase mDatabase;

    public static synchronized void initializeInstance(WorkoutSummariesDbHelper workoutSummariesDbHelper) {
        if (cInstance == null) {
            cInstance = new WorkoutSummariesDatabaseManager();
            cWorkoutSummariesDbHelper = workoutSummariesDbHelper;
        }
    }

    public static synchronized WorkoutSummariesDatabaseManager getInstance() {
        if (cInstance == null) {
            throw new IllegalStateException(WorkoutSummariesDatabaseManager.class.getSimpleName() +
                    " is not initialized, call initializeInstance(..) method first.");
        }

        return cInstance;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level helper methods
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static String getBaseFileName(long workoutId) {
        if (DEBUG) Log.i(TAG, "getBaseFileName for workoutId: " + workoutId);

        String baseFileName = null;

        SQLiteDatabase summariesDb = getInstance().getOpenDatabase();

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null);
        if (cursor.moveToFirst()) {
            baseFileName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME));
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();

        return baseFileName;
    }

    public static Double getDouble(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getDouble for workoutId: " + workoutId + ", " + key);

        Double value = null;

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null);
        if (cursor.moveToFirst()) {
            try {
                value = cursor.getDouble(cursor.getColumnIndexOrThrow(key));
            } catch (Exception e) {
                Log.d(TAG, "in getDouble(): probably undefined key is used: " + key);
            }
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();

        return value;
    }

    public static Double getDouble(String baseFileName, String key) {
        if (DEBUG) Log.i(TAG, "getDouble for baseFileName: " + baseFileName + ", " + key);

        if (baseFileName == null) {
            Log.d(TAG, "WTF: baseFileName is null!");
            return null;
        }

        Double value = null;

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        // TODO: use method getCursor(...)
        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{baseFileName},
                null, null, null);
        if (cursor.moveToFirst()) {
            try {
                value = cursor.getDouble(cursor.getColumnIndexOrThrow(key));
            } catch (Exception e) {
                Log.d(TAG, "in getDouble(): probably undefined key is used: " + key);
            }
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();

        return value;
    }

    public static String getString(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getString(" + workoutId + ", " + key + ")");

        String value = null;

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
        Cursor cursor = db.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null,
                null,
                null);
        if (cursor.moveToFirst()) {
            value = cursor.getString(cursor.getColumnIndex(key));
        }

        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // db.close();

        return value;
    }

    public static Integer getInt(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getInt for workoutId: " + workoutId + ", " + key);

        Integer value = null;

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null);
        if (cursor.moveToFirst()) {
            try {
                value = cursor.getInt(cursor.getColumnIndexOrThrow(key));
            } catch (Exception e) {
                Log.d(TAG, "in getInt(): probably undefined key is used: " + key);
            }
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();

        return value;
    }

    public static Long getLong(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getLong for workoutId: " + workoutId + ", " + key);

        Long value = null;

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null);
        if (cursor.moveToFirst()) {
            try {
                value = cursor.getLong(cursor.getColumnIndexOrThrow(key));
            } catch (Exception e) {
                Log.d(TAG, "in getLong(): probably undefined key is used: " + key);
            }
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();

        return value;
    }

    public static Integer getInt(String baseFileName, String key) {
        if (DEBUG) Log.i(TAG, "getInt for baseFileName: " + baseFileName + ", " + key);

        Integer value = null;

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{baseFileName},
                null, null, null);
        if (cursor.moveToFirst()) {
            try {
                value = cursor.getInt(cursor.getColumnIndexOrThrow(key));
            } catch (Exception e) {
                Log.d(TAG, "in getInt(): probably undefined key is used: " + key);
            }
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();

        return value;
    }

    public static boolean getBoolean(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getBoolean for workoutId: " + workoutId + ", " + key);

        boolean result = false;

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null);

        if (DEBUG)
            Log.i(TAG, "cursor has dimension " + cursor.getColumnCount() + " x " + cursor.getCount() + "entries");

        if (cursor.moveToFirst()) {
            try {
                int columnIndex = cursor.getColumnIndexOrThrow(key);
                if (DEBUG) Log.i(TAG, "columnIndex=" + columnIndex);
                int value = cursor.getInt(columnIndex);
                if (DEBUG) Log.i(TAG, "got " + value);
                result = value > 0;
            } catch (Exception e) {
                Log.d(TAG, "in getBoolean(): probably undefined key is used: " + key);
                // when the key is not defined, we also return false;
            }
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();

        if (DEBUG) Log.i(TAG, "returning " + result);

        return result;
    }

    public static Double getExtremaValue(long workoutId, SensorType sensorType, ExtremaType extremaType) {
        Double extremaValue = null;

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = summariesDb.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                null,
                WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=?",
                new String[]{Long.toString(workoutId), sensorType.name(), extremaType.name()},
                null, null, null);
        if (cursor.moveToFirst()) {
            extremaValue = cursor.getDouble(cursor.getColumnIndex(WorkoutSummaries.VALUE));
            if (DEBUG)
                Log.i(TAG, "got " + extremaValue + " for " + extremaType.name() + " " + sensorType.name() + " of workout " + workoutId);
        } else {
            if (DEBUG)
                Log.d(TAG, "there seems to be no entry for " + extremaType.name() + " " + sensorType.name() + " in workout " + workoutId);
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();

        return extremaValue;
    }

    // TODO: make sport specific?
    public static List<LatLng> getExtremaTypeLocations(ExtremaType extremaType) {
        if (DEBUG) Log.i(TAG, "getAllStartLocations");

        List<LatLng> startLocations = new LinkedList<>();

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor latCursor = null;
        Cursor lonCursor = null;
        double latitude, longitude;

        Cursor cursor = db.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                null,
                null,
                null,
                null,
                null,
                null);

        while (cursor.moveToNext()) {

            long workoutId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.WORKOUT_ID));
            if (DEBUG) Log.i(TAG, "getAllStartLocations: checking workoutId=" + workoutId);

            latCursor = db.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                    null,
                    WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=?",
                    new String[]{Long.toString(workoutId), SensorType.LATITUDE.name(), extremaType.name()},
                    null, null, null);
            lonCursor = db.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                    null,
                    WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=?",
                    new String[]{Long.toString(workoutId), SensorType.LONGITUDE.name(), extremaType.name()},
                    null, null, null);

            if (latCursor.moveToFirst()
                    && lonCursor.moveToFirst()
                    && dataValid(latCursor, WorkoutSummaries.VALUE)
                    && dataValid(lonCursor, WorkoutSummaries.VALUE)) {
                latitude = latCursor.getDouble(latCursor.getColumnIndex(WorkoutSummaries.VALUE));
                longitude = lonCursor.getDouble(latCursor.getColumnIndex(WorkoutSummaries.VALUE));
                startLocations.add(new LatLng(latitude, longitude));
                if (DEBUG) Log.i(TAG, "added start location");
            } else if (DEBUG) {
                Log.i(TAG, "did not add start location");
            }

            latCursor.close();
            lonCursor.close();
        }

        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase();

        if (DEBUG) Log.i(TAG, "got " + startLocations.size() + " start locations");

        return startLocations;
    }

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

    public static void saveAccumulatedSensorTypes(long workoutId, Iterable<SensorType> sensorTypes) {
        if (DEBUG) Log.i(TAG, "saveAccumulatedSensors for workoutId: " + workoutId);

        SQLiteDatabase summariesDb = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.WORKOUT_ID, workoutId);

        for (SensorType sensorType : sensorTypes) {
            if (DEBUG) Log.i(TAG, "saving sensorType: " + sensorType.name());
            values.put(WorkoutSummaries.SENSOR_TYPE, sensorType.name());
            summariesDb.insert(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, null, values);
        }

        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // summariesDb.close();
        if (DEBUG) Log.i(TAG, "end of saveAccumulatedSensors");
    }

    public static Set<SensorType> getAccumulatedSensorTypes(long workoutId) {
        if (DEBUG) Log.i(TAG, "getAccumulatedSensorTypes for workoutId: " + workoutId);

        Set<SensorType> accumulatedSensorTypesSet = new HashSet<SensorType>();

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();
        Cursor cursor = db.query(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS,
                null, // columns
                WorkoutSummaries.WORKOUT_ID + "=?", // selection
                new String[]{Long.toString(workoutId)}, //selectionArgs,
                null, null, null); // groupBy, having, orderBy)
        while (cursor.moveToNext()) {
            String sensorTypeName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.SENSOR_TYPE));
            if (DEBUG) Log.i(TAG, "got sensor: " + sensorTypeName);
            accumulatedSensorTypesSet.add(SensorType.valueOf(sensorTypeName));
        }

        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // db.close();

        return accumulatedSensorTypesSet;
    }


    /**
     * helper method to check whether there is some data
     */

    public static List<Long> getWorkoutIds() {
        List<Long> workoutIds = new LinkedList<Long>();

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE,
                null, // columns,
                null, // selection
                null, null, null, null); // selectionArgs, groupBy, having, orderBy)
        while (cursor.moveToNext()) {
            long workoutId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
            workoutIds.add(workoutId);
        }

        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // db.close();

        return workoutIds;
    }

    public static List<Long> getOldWorkouts(int days) {
        if (DEBUG) Log.i(TAG, "getOldWorkouts(" + days + ")");

        List<Long> oldWorkoutIds = new LinkedList<Long>();

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE,
                null, // columns,
                WorkoutSummaries.TIME_START + " <= datetime('now', '-" + days + " day')", // selection
                null, null, null, null); // selectionArgs, groupBy, having, orderBy)
        while (cursor.moveToNext()) {
            long workoutId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
            if (DEBUG) Log.i(TAG, "adding " + workoutId + " to oldWorkoutId List (name="
                    + cursor.getString(cursor.getColumnIndex(WorkoutSummaries.WORKOUT_NAME)) + ", startTime="
                    + cursor.getString(cursor.getColumnIndex(WorkoutSummaries.TIME_START)) + ")");
            oldWorkoutIds.add(workoutId);
        }

        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // db.close();

        return oldWorkoutIds;
    }

    public static String getStartTime(long workoutId, String timeZone) {
        if (DEBUG) Log.i(TAG, "getStartTime: workoutId=" + workoutId);
        String startTime = null;

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE, // table
                new String[]{"datetime(" + WorkoutSummaries.TIME_START + ", '" + timeZone + "')"}, // columns
                WorkoutSummaries.C_ID + "=?",  // selection
                new String[]{Long.toString(workoutId)}, //selectionArgs,
                null, null, null); // groupBy, having, orderBy)
        cursor.moveToFirst();
        try {
            startTime = cursor.getString(0);
        } catch (Exception e) {
            // startTime remains null
        }

        WorkoutSummariesDatabaseManager.getInstance().closeDatabase();

        return startTime;
    }

    public static String getStartTime(String fileBaseName, String timeZone) {
        if (DEBUG) Log.i(TAG, "getStartTime: fileBaseName=" + fileBaseName);
        String startTime = null;

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE, // table
                new String[]{"datetime(" + WorkoutSummaries.TIME_START + ", '" + timeZone + "')"}, // columns
                WorkoutSummaries.FILE_BASE_NAME + "=?",  // selection
                new String[]{fileBaseName}, //selectionArgs,
                null, null, null); // groupBy, having, orderBy)
        cursor.moveToFirst();
        startTime = cursor.getString(0);

        WorkoutSummariesDatabaseManager.getInstance().closeDatabase();

        return startTime;
    }

    public static List<String> getFancyNameList() {
        List result = new LinkedList();

        SQLiteDatabase db = getInstance().getOpenDatabase();
        Cursor cursor = db.query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, // table
                null, null, null, null, null, null);

        while (cursor.moveToNext()) {
            result.add(cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FANCY_NAME)));
        }

        getInstance().closeDatabase();
        return result;
    }

    public static long getFancyNameId(String fancyName) {
        long fancyNameId = -1;

        SQLiteDatabase db = getInstance().getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS,
                null,
                WorkoutSummaries.FANCY_NAME + "=?",
                new String[]{fancyName},
                null, null, null);
        if (cursor.moveToFirst()) {
            fancyNameId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
        }

        getInstance().closeDatabase();

        return fancyNameId;
    }

    public static String getFancyNameAndIncrement(String fancyName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(fancyName);

        SQLiteDatabase db = getInstance().getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS,
                null,
                WorkoutSummaries.FANCY_NAME + "=?",
                new String[]{fancyName},
                null, null, null);

        if (cursor.moveToFirst()) {
            if (cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.ADD_COUNTER)) >= 1) {
                int counter = cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.COUNTER)) + 1;
                stringBuilder.append(" #");
                stringBuilder.append(counter);

                ContentValues contentValues = new ContentValues();
                contentValues.put(WorkoutSummaries.COUNTER, counter);
                db.update(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, contentValues,
                        WorkoutSummaries.FANCY_NAME + "=?",
                        new String[]{fancyName});
            }
        }

        getInstance().closeDatabase();

        return stringBuilder.toString();
    }

    public static String getFancyName(long sportTypeId,
                                      KnownLocationsDatabaseManager.MyLocation startLocation,
                                      KnownLocationsDatabaseManager.MyLocation maxLineDistanceLocation,
                                      KnownLocationsDatabaseManager.MyLocation endLocation) {
        if (startLocation != null & endLocation != null) {

            StringBuilder stringBuilder = new StringBuilder();

            SQLiteDatabase db = getInstance().getOpenDatabase();

            // get the first part, something like #bike2work, b2w, >> work ...
            Cursor cursor = db.query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, // table
                    null,  // columns,
                    WorkoutSummaries.SPORT_ID + "=? AND " + WorkoutSummaries.START_LOCATION_NAME + "=? AND " + WorkoutSummaries.END_LOCATION_NAME + "=?", // selection,
                    new String[]{Long.toString(sportTypeId), startLocation.name, endLocation.name}, // selectionArgs,
                    null, null, null);// groupBy, having, orderBy

            if (cursor.moveToFirst()) {
                stringBuilder.append(cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FANCY_NAME)));
            } else {
                stringBuilder.append(createDefaultFancyName(sportTypeId, startLocation, maxLineDistanceLocation, endLocation));
                cursor.requery();
            }

            if (cursor.moveToFirst()) {

                // optionally add counter like #42
                if (cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.ADD_COUNTER)) >= 1) {
                    int counter = cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.COUNTER)) + 1;
                    stringBuilder.append(" #");
                    stringBuilder.append(counter);

                    ContentValues contentValues = new ContentValues();
                    contentValues.put(WorkoutSummaries.COUNTER, counter);
                    db.update(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, contentValues,
                            WorkoutSummaries.SPORT_ID + "=? AND " + WorkoutSummaries.START_LOCATION_NAME + "=? AND " + WorkoutSummaries.END_LOCATION_NAME + "=?", // selection,
                            new String[]{Long.toString(sportTypeId), startLocation.name, endLocation.name}); // selectionArgs,
                }

                // optionally add via ...
                if (cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.ADD_VIA)) >= 1) {
                    if (maxLineDistanceLocation != null && maxLineDistanceLocation.id != endLocation.id) {
                        if (DEBUG) Log.i(TAG, "made a detour or a loop");

                        if (startLocation.id == endLocation.id) {  // loop around
                            stringBuilder.append(TrainingApplication.getAppContext().getString(R.string.loop_around_format, maxLineDistanceLocation.name));
                        } else {// detour on commute
                            stringBuilder.append(TrainingApplication.getAppContext().getString(R.string.via_format, maxLineDistanceLocation.name));
                        }
                    }
                }
            }

            // clean up
            cursor.close();
            getInstance().closeDatabase();

            return stringBuilder.toString();
        }

        return null;
    }

    protected static String createDefaultFancyName(long sportTypeId,
                                                   KnownLocationsDatabaseManager.MyLocation startLocation,
                                                   KnownLocationsDatabaseManager.MyLocation maxLineDistanceLocation,
                                                   KnownLocationsDatabaseManager.MyLocation endLocation) {

        if (startLocation != null & endLocation != null) {
            StringBuilder stringBuilder = new StringBuilder();

            if (startLocation.id != endLocation.id) { // probably a commute
                stringBuilder.append("#");
                stringBuilder.append(SportTypeDatabaseManager.getUIName(sportTypeId));
                stringBuilder.append("2");
                stringBuilder.append(endLocation.name);
            } else { // a loop
                stringBuilder.append(SportTypeDatabaseManager.getUIName(sportTypeId) + "@" + startLocation.name);
            }

            String baseName = stringBuilder.toString();

            ContentValues contentValues = new ContentValues();
            contentValues.put(WorkoutSummaries.SPORT_ID, sportTypeId);
            contentValues.put(WorkoutSummaries.START_LOCATION_NAME, startLocation.name);
            contentValues.put(WorkoutSummaries.END_LOCATION_NAME, endLocation.name);
            contentValues.put(WorkoutSummaries.FANCY_NAME, baseName);
            contentValues.put(WorkoutSummaries.ADD_COUNTER, 1);
            contentValues.put(WorkoutSummaries.COUNTER, 0);
            contentValues.put(WorkoutSummaries.ADD_VIA, 1);

            SQLiteDatabase db = getInstance().getOpenDatabase();
            db.insert(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, null, contentValues);
            getInstance().closeDatabase();

            return baseName;
        }

        return null;
    }

    public static void deleteFancyName(long id) {
        if (DEBUG) Log.i(TAG, "deleteFancyName: id=" + id);

        SQLiteDatabase db = getInstance().getOpenDatabase();
        db.delete(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS,
                WorkoutSummaries.C_ID + " =? ",
                new String[]{Long.toString(id)});
        getInstance().closeDatabase();
    }

    public synchronized SQLiteDatabase getOpenDatabase() {
        mOpenCounter++;
        if (mOpenCounter == 1) {
            // Opening new database
            mDatabase = cWorkoutSummariesDbHelper.getWritableDatabase();
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

    public static final class WorkoutSummaries {
        public static final String TABLE = "WorkoutSummaries";
        public static final String TABLE_EXTREMA_VALUES = "ExtremumValues";
        public static final String TABLE_ACCUMULATED_SENSORS = "AccumulatedSensors";
        // public static final String TABLE_WORKOUT_NAME_COUNTERS = "TODO:remove!";
        public static final String TABLE_WORKOUT_NAME_PATTERNS = "WorkoutNamePatterns";


        public static final String C_ID = BaseColumns._ID;

        public static final String WORKOUT_NAME = "exportName";
        public static final String FILE_BASE_NAME = "fileBaseName";
        public static final String ATHLETE_NAME = "athleteName";
        public static final String GOAL = "goal";
        public static final String METHOD = "method";
        public static final String EQUIPMENT_ID = "equipmentId";
        public static final String DESCRIPTION = "description";
        public static final String SAMPLING_TIME = "samplingTime";
        public static final String B_SPORT = "Sport";                 // intentionally the same name.  This avoids creating a new column and leaving the old one unused when upgrading
        public static final String SPORT_ID = "sportId";
        // use WorkoutSummariesDatabaseManager.getStartTime to access this field
        public static final String TIME_START = "timeStart";            // might be moved to the extrema (START) values?
        public static final String TIME_ACTIVE_s = "timeActive_s";
        public static final String TIME_TOTAL_s = "timeTotal_s";
        public static final String DISTANCE_TOTAL_m = "distanceTotal_m";
        public static final String SPEED_AVERAGE_mps = "speedAverage_mps";     // should be moved to the extrema (MEAN) values
        public static final String GC_DATA = "GCData";
        public static final String CALORIES = "calories";
        public static final String LAPS = "laps";
        public static final String FINISHED = "finished";
        // new entries in version 4 of the db
        public static final String PRIVATE = "private";
        public static final String COMMUTE = "commute";
        public static final String TRAINER = "trainer";
        public static final String ASCENDING = "ascending";
        public static final String DESCENDING = "descending";
        // new entries in version 5 of the DB
        public static final String EXTREMA_VALUES_CALCULATED = "extremumValuesCalculated";
        // new entries in version 6 of the DB
        public static final String SAMPLES_COLUMN_ID = "samplesColumnId";
        // columns of the EXTREMA table
        public static final String WORKOUT_ID = "workoutID";

        // public static final String ALTITUDE_MAX         = "altitudeMax";
        // public static final String CADENCE_MEAN         = "cadenceMean";
        // public static final String CADENCE_MAX          = "cadenceMax";
        // public static final String HR_MEAN              = "HRMean";
        // public static final String HR_MAX               = "HRMax";
        // public static final String PACE_spm_MEAN        = "paceMean";
        // public static final String PACE_spm_MAX         = "paceMax";    // the maximal pace would be the one with the smallest value
        // public static final String SPEED_mps_MEAN       = "speedMean";
        // public static final String SPEED_mps_MAX        = "speedMax";
        // public static final String POWER_MEAN           = "powerMean";
        // public static final String POWER_MAX            = "powerMax";
        // temperature? max, mean, min?
        public static final String EXTREMA_TYPE = "extremumType";
        public static final String SENSOR_TYPE = "sensorType";
        public static final String VALUE = "value";
        // columns of the WorkoutNamePattern table
        // public static final String SPORT // already defined
        public static final String START_LOCATION_NAME = "startLocationName";

        // columns of the WorkoutNameCounter table
        // public static final String WORKOUT_NAME_HASH_KEY = "workoutNameHashKey";
        // public static final String COUNTER               = "counter";
        public static final String END_LOCATION_NAME = "endLocationName";
        public static final String FANCY_NAME = "fancyName";
        public static final String ADD_COUNTER = "addCounter";
        public static final String COUNTER = "counter";
        public static final String ADD_VIA = "addVia";
        @Deprecated
        private static final String SPORT_OLD = "sport";

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The database itself
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static class WorkoutSummariesDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "WorkoutSummaries.db";
        // public static final int DB_VERSION  = 4; // upgrade to Version 4 around November 2015
        // public static final int DB_VERSION  = 5; // upgrade to Version 5 at 1. December 2015
        // public static final int DB_VERSION = 6; // upgrade to Version 6 at 7. December 2015
        // public static final int DB_VERSION = 7; // upgrade to Version 7 at 16. May 2016
        // public static final int DB_VERSION = 8; // upgrade to Version 8 at 1. June 2016
        // public static final int DB_VERSION = 9; // upgrade to Version 9 at 7. June 2016
        // public static final int DB_VERSION = 10; // upgrade to Version 10 at 8. June 2016
        public static final int DB_VERSION = 11; // upgrade to Version 11 at 19. 01. 2017
        protected static final String CREATE_TABLE_V11 = "create table " + WorkoutSummaries.TABLE + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.WORKOUT_NAME + " text,"
                + WorkoutSummaries.FILE_BASE_NAME + " text,"
                + WorkoutSummaries.ATHLETE_NAME + " text,"
                + WorkoutSummaries.DESCRIPTION + " text,"
                + WorkoutSummaries.GOAL + " text,"
                + WorkoutSummaries.METHOD + " text,"
                // + WorkoutSummaries.SPORT + " text,"
                + WorkoutSummaries.B_SPORT + " text,"
                + WorkoutSummaries.SPORT_ID + " int,"
                + WorkoutSummaries.EQUIPMENT_ID + " int,"
                + WorkoutSummaries.SAMPLING_TIME + " int,"
                + WorkoutSummaries.TIME_START + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + WorkoutSummaries.TIME_ACTIVE_s + " int,"
                + WorkoutSummaries.TIME_TOTAL_s + " int,"
                + WorkoutSummaries.DISTANCE_TOTAL_m + " real,"
                + WorkoutSummaries.SPEED_AVERAGE_mps + " real,"
                + WorkoutSummaries.GC_DATA + " text,"
                + WorkoutSummaries.CALORIES + " int,"
                + WorkoutSummaries.LAPS + " int,"
                + WorkoutSummaries.FINISHED + " int," // end of version 3
                + WorkoutSummaries.PRIVATE + " int,"
                + WorkoutSummaries.COMMUTE + " int,"
                + WorkoutSummaries.TRAINER + " int,"
                + WorkoutSummaries.ASCENDING + " int,"
                + WorkoutSummaries.DESCENDING + " int," // end of version 4
                + WorkoutSummaries.EXTREMA_VALUES_CALCULATED + " int)";
        protected static final String CREATE_TABLE_EXTREMA_VALUES_V6 = "create table " + WorkoutSummaries.TABLE_EXTREMA_VALUES + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.WORKOUT_ID + " int,"
                + WorkoutSummaries.EXTREMA_TYPE + " text,"
                + WorkoutSummaries.SENSOR_TYPE + " text,"
                + WorkoutSummaries.VALUE + " real," // end of version 5
                + WorkoutSummaries.SAMPLES_COLUMN_ID + " int)";
        protected static final String CREATE_TABLE_ACCUMULATED_SENSORS_V6 = "create table " + WorkoutSummaries.TABLE_ACCUMULATED_SENSORS + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.WORKOUT_ID + " int,"
                + WorkoutSummaries.SENSOR_TYPE + " text)";
        protected static final String CREATE_TABLE_WORKOUT_NAME_PATTERNS_V10
                = "create table " + WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.SPORT_OLD + " text, "
                + WorkoutSummaries.START_LOCATION_NAME + " text, "
                + WorkoutSummaries.END_LOCATION_NAME + " text, "
                + WorkoutSummaries.FANCY_NAME + " text, "
                + WorkoutSummaries.ADD_COUNTER + " int, "
                + WorkoutSummaries.COUNTER + " int, "
                + WorkoutSummaries.ADD_VIA + " int)";
        protected static final String CREATE_TABLE_WORKOUT_NAME_PATTERNS_V11
                = "create table " + WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.SPORT_ID + " int, "
                + WorkoutSummaries.START_LOCATION_NAME + " text, "
                + WorkoutSummaries.END_LOCATION_NAME + " text, "
                + WorkoutSummaries.FANCY_NAME + " text, "
                + WorkoutSummaries.ADD_COUNTER + " int, "
                + WorkoutSummaries.COUNTER + " int, "
                + WorkoutSummaries.ADD_VIA + " int)";
        private static final String TAG = "WorkoutSummariesDbHelper";
        private static final boolean DEBUG = TrainingApplication.DEBUG & true;
        private final Context mContext;

        // Constructor
        public WorkoutSummariesDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);

            mContext = context;
        }
        // TODO: add location (latitude and longitude) and add them when needed

        // Called only once, first time the DB is created
        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE_V11);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_V11);

            // new in version 4:
            db.execSQL(CREATE_TABLE_EXTREMA_VALUES_V6);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_EXTREMA_VALUES_V6);

            db.execSQL(CREATE_TABLE_ACCUMULATED_SENSORS_V6);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_ACCUMULATED_SENSORS_V6);

            db.execSQL(CREATE_TABLE_WORKOUT_NAME_PATTERNS_V11);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_WORKOUT_NAME_PATTERNS_V11);

        }

        // protected static final String CREATE_TABLE_WORKOUT_NAME_COUNTERS_V8 = "create table " + WorkoutSummaries.TABLE_WORKOUT_NAME_COUNTERS + " ("
        //         + WorkoutSummaries.C_ID                  + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        //         + WorkoutSummaries.WORKOUT_NAME_HASH_KEY + " text, "
        //         + WorkoutSummaries.COUNTER               + " int)";

        // protected static final String CREATE_TABLE_WORKOUT_NAME_PATTERNS_V9 = "create table " + WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS + " ("
        //         + WorkoutSummaries.C_ID                 + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        //         + WorkoutSummaries.SPORT                + " text, "
        //         + WorkoutSummaries.START_LOCATION_NAME  + " text, "
        //         + WorkoutSummaries.END_LOCATION_NAME    + " text, "
        //         + WorkoutSummaries.FANCY_NAME           + " text, "
        //         + WorkoutSummaries.ADD_COUNTER          + " int, "
        //         + WorkoutSummaries.ADD_VIA              + " int)";

        private final void addColumn(SQLiteDatabase db, String table, String column, String type) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 4) {
                Log.i(TAG, "upgrading to DB version 4");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.PRIVATE, "int");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.COMMUTE, "int");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.TRAINER, "int");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.ASCENDING, "int");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.DESCENDING, "int");

                db.execSQL(CREATE_TABLE_EXTREMA_VALUES_V6);
                db.execSQL(CREATE_TABLE_ACCUMULATED_SENSORS_V6);
            }

            if (oldVersion < 5) {  // this version of the database was never released.
                Log.i(TAG, "upgrading to DB version 5");

                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.EXTREMA_VALUES_CALCULATED, "int");
            }

            if (oldVersion < 6) {
                Log.i(TAG, "upgrading to DB version 6");

                // must not be executed because when upgrading from version 4, this column is already present!
                // addColumn(db, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.SAMPLES_COLUMN_ID, "int");
            }

            if (oldVersion < 7) {
                Log.i(TAG, "upgrading to DB version 7");

                // add MaxLineDistance Stuff but this did not work as expected
            }

            if (oldVersion == 9) {
                addColumn(db, WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, WorkoutSummaries.COUNTER, "int");
            } else if (oldVersion < 10) {
                Log.i(TAG, "upgrading to DB version 10");

                db.execSQL(CREATE_TABLE_WORKOUT_NAME_PATTERNS_V10);
            }

            if (oldVersion < 11) {
                Log.i(TAG, "upgrading to DB version 11");
                db.beginTransaction();
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.SPORT_ID, "int");
                // addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.B_SPORT,  "text");
                db.setTransactionSuccessful();
                db.endTransaction();

                Cursor cursor = db.query(WorkoutSummaries.TABLE,
                        new String[]{WorkoutSummaries.C_ID, WorkoutSummaries.SPORT_OLD},
                        null, null,
                        null, null, null);
                ContentValues contentValues = new ContentValues();
                while (cursor.moveToNext()) {
                    contentValues.clear();
                    long id = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
                    String sport = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.SPORT_OLD));
                    contentValues.put(WorkoutSummaries.SPORT_ID, SportTypeDatabaseManager.getSportTypeIdFromTTSportTypeName(sport));
                    contentValues.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getBSportType(sport).name());
                    db.update(WorkoutSummaries.TABLE, contentValues,
                            WorkoutSummaries.C_ID + "=?", new String[]{Long.toString(id)});
                }
                cursor.close();


                addColumn(db, WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, WorkoutSummaries.SPORT_ID, "text");

                cursor = db.query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS,
                        new String[]{WorkoutSummaries.C_ID, WorkoutSummaries.SPORT_OLD},
                        null, null,
                        null, null, null);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
                    String sport = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.SPORT_OLD));
                    contentValues.put(WorkoutSummaries.SPORT_ID, SportTypeDatabaseManager.getSportTypeIdFromTTSportTypeName(sport));
                    db.update(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, contentValues,
                            WorkoutSummaries.C_ID + "=?", new String[]{Long.toString(id)});
                }
                cursor.close();

            }

        }
    }
}
