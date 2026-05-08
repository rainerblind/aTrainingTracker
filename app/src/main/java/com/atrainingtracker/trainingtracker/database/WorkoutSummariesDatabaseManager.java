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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData;
import com.atrainingtracker.trainingtracker.ui.utils.NumericalEncodingUtils;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class WorkoutSummariesDatabaseManager {
    private static final String TAG = "WorkoutSummariesDatabaseManager";
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    private static WorkoutSummariesDbHelper cWorkoutSummariesDbHelper;
    private static volatile WorkoutSummariesDatabaseManager cInstance;

    // Private constructor to prevent direct instantiation
    private WorkoutSummariesDatabaseManager(Context context) {
        // The helper is instantiated with the application context, making it safe.
        cWorkoutSummariesDbHelper = new WorkoutSummariesDbHelper(context);
    }

    @NonNull
    public static WorkoutSummariesDatabaseManager getInstance(@NonNull Context context) {
        // Use double-checked locking for thread-safe lazy initialization.
        if (cInstance == null) {
            synchronized (WorkoutSummariesDatabaseManager.class) {
                if (cInstance == null) {
                    // Pass the application context to avoid memory leaks.
                    cInstance = new WorkoutSummariesDatabaseManager(context.getApplicationContext());
                }
            }
        }
        return cInstance;
    }

    // Let the helper manage the database object. This is thread-safe.
    public SQLiteDatabase getDatabase() {
        return cWorkoutSummariesDbHelper.getWritableDatabase();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level helper methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public void updateWorkoutData(WorkoutData workoutData) {
        if (DEBUG) Log.i(TAG, "updateWorkoutData for workoutId: " + workoutData.getId());

        ContentValues values = new ContentValues();

        // workout name
        values.put(WorkoutSummaries.WORKOUT_NAME, workoutData.getWorkoutName());

        // sport and equipment
        values.put(WorkoutSummaries.SPORT_ID, workoutData.getSportId());
        values.put(WorkoutSummaries.B_SPORT, workoutData.getBSportType().name());
        long equipmentId = workoutData.getEquipmentId();
        if (equipmentId == -1) {  // when the equipmentId is -1, the link to the equipment is removed.
            values.putNull(WorkoutSummaries.EQUIPMENT_ID);
        }
        else {
            values.put(WorkoutSummaries.EQUIPMENT_ID, equipmentId);
        }

        // description, goal, and method
        values.put(WorkoutSummaries.DESCRIPTION, workoutData.getDescription());
        values.put(WorkoutSummaries.GOAL, workoutData.getGoal());
        values.put(WorkoutSummaries.METHOD, workoutData.getMethod());

        // commute and trainer
        values.put(WorkoutSummaries.COMMUTE, workoutData.getCommute());
        values.put(WorkoutSummaries.TRAINER, workoutData.getTrainer());

        // individual Strava upload
        values.put(WorkoutSummaries.UPLOAD_TO_STRAVA, workoutData.getUploadToStrava());

        getDatabase().update(WorkoutSummaries.TABLE,
                values,
                WorkoutSummaries.C_ID + "=" + workoutData.getId(),
                null);
    }

    public Cursor getWorkoutCursor(long workoutId) {
        return getDatabase().query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null);
    }


    public Cursor getCursorForAllWorkouts() {
        return getDatabase().query(
                WorkoutSummaries.TABLE,
                null, null, null, null, null,
                WorkoutSummaries.TIME_START + " DESC"
        );
    }


    @Nullable
    public String getBaseFileName(long workoutId) {
        if (DEBUG) Log.i(TAG, "getBaseFileName for workoutId: " + workoutId);

        String baseFileName = null;

        try (Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE,
                new String[]{WorkoutSummaries.FILE_BASE_NAME},
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null)) {

            if (cursor.moveToFirst()) {
                baseFileName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME));
            }
        } // Cursor is closed here, even if an exception occurs.

        return baseFileName;

    }

    @Nullable
    public Double getDouble(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getDouble for workoutId: " + workoutId + ", " + key);

        Double value = null;

        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE,
                new String[]{key},
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null)) {

            if (cursor.moveToFirst()) {
                value = cursor.getDouble(cursor.getColumnIndexOrThrow(key));
            }
        } // Cursor is closed here, even if an exception occurs.

        return value;
    }

    @Nullable
    public Double getDouble(@Nullable String baseFileName, String key) {
        if (DEBUG) Log.i(TAG, "getDouble for baseFileName: " + baseFileName + ", " + key);

        if (baseFileName == null) {
            Log.d(TAG, "WTF: baseFileName is null!");
            return null;
        }

        Double value = null;

        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE,
                new String[]{key},
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{baseFileName},
                null, null, null)) {

            if (cursor.moveToFirst()) {
                value = cursor.getDouble(cursor.getColumnIndexOrThrow(key));
            }
        }

        return value;
    }

    @Nullable
    public String getString(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getString(" + workoutId + ", " + key + ")");

        String value = null;

        try (Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE,
                new String[]{key},
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null,
                null,
                null)) {

            if (cursor.moveToFirst()) {
                value = cursor.getString(cursor.getColumnIndex(key));
            }
        }

        return value;
    }

    @Nullable
    public Integer getInt(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getInt for workoutId: " + workoutId + ", " + key);

        Integer value = null;

        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE,
                new String[]{key},
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null)) {

            if (cursor.moveToFirst()) {
                value = cursor.getInt(cursor.getColumnIndexOrThrow(key));
            }
        }

        return value;
    }

    @Nullable
    public Integer getInt(String baseFileName, String key) {
        if (DEBUG) Log.i(TAG, "getInt for baseFileName: " + baseFileName + ", " + key);

        Integer value = null;

        try (Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE,
                new String[]{key},
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{baseFileName},
                null, null, null)) {

            if (cursor.moveToFirst()) {
                value = cursor.getInt(cursor.getColumnIndexOrThrow(key));
            }
        }

        return value;
    }

    @Nullable
    public Long getLong(long workoutId, String key) {
        if (DEBUG) Log.i(TAG, "getLong for workoutId: " + workoutId + ", " + key);

        Long value = null;

        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE,
                new String[]{key},
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null)) {

            if (cursor.moveToFirst()) {
                value = cursor.getLong(cursor.getColumnIndexOrThrow(key));
            }
        }

        return value;
    }

    @Nullable
    public Double getExtremaValue(long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType) {
        Double extremaValue = null;

        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                new String[]{WorkoutSummaries.VALUE},
                WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=?",
                new String[]{Long.toString(workoutId), sensorType.name(), extremaType.name()},
                null, null, null)) {

            if (cursor.moveToFirst()) {
                extremaValue = cursor.getDouble(cursor.getColumnIndex(WorkoutSummaries.VALUE));
                if (DEBUG)
                    Log.i(TAG, "got " + extremaValue + " for " + extremaType.name() + " " + sensorType.name() + " of workout " + workoutId);
            } else {
                if (DEBUG)
                    Log.d(TAG, "there seems to be no entry for " + extremaType.name() + " " + sensorType.name() + " in workout " + workoutId);
            }
        }
        return extremaValue;
    }


    public void saveAccumulatedSensorTypes(long workoutId, @NonNull Iterable<SensorType> sensorTypes) {
        if (DEBUG) Log.i(TAG, "saveAccumulatedSensors for workoutId: " + workoutId);

        SQLiteDatabase summariesDb = getDatabase();
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.WORKOUT_ID, workoutId);

        for (SensorType sensorType : sensorTypes) {
            if (DEBUG) Log.i(TAG, "saving sensorType: " + sensorType.name());
            values.put(WorkoutSummaries.SENSOR_TYPE, sensorType.name());
            summariesDb.insert(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, null, values);
        }
    }

    @NonNull
    public Set<SensorType> getAccumulatedSensorTypes(long workoutId) {
        if (DEBUG) Log.i(TAG, "getAccumulatedSensorTypes for workoutId: " + workoutId);

        Set<SensorType> accumulatedSensorTypesSet = new HashSet<>();


        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS,
                new String[]{WorkoutSummaries.SENSOR_TYPE}, // columns
                WorkoutSummaries.WORKOUT_ID + "=?", // selection
                new String[]{Long.toString(workoutId)}, //selectionArgs,
                null, null, null)) { // groupBy, having, orderBy)

            while (cursor.moveToNext()) {
                String sensorTypeName = cursor.getString(cursor.getColumnIndex(WorkoutSummaries.SENSOR_TYPE));
                if (DEBUG) Log.i(TAG, "got sensor: " + sensorTypeName);
                accumulatedSensorTypesSet.add(SensorType.valueOf(sensorTypeName));
            }
        }

        return accumulatedSensorTypesSet;
    }


    @NonNull
    public List<Long> getOldWorkouts(int days) {
        if (DEBUG) Log.i(TAG, "getOldWorkouts(" + days + ")");

        List<Long> oldWorkoutIds = new LinkedList<>();

        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE,
                new String[]{WorkoutSummaries.C_ID}, // columns,
                WorkoutSummaries.TIME_START + " <= datetime('now', '-" + days + " day')", // selection
                null, null, null, null)) { // selectionArgs, groupBy, having, orderBy)

            while (cursor.moveToNext()) {
                long workoutId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
                if (DEBUG) Log.i(TAG, "adding " + workoutId + " to oldWorkoutId List");
                oldWorkoutIds.add(workoutId);
            }
        }

        return oldWorkoutIds;
    }


    public String getStartTime(String fileBaseName, String timeZone) {
        if (DEBUG) Log.i(TAG, "getStartTime: fileBaseName=" + fileBaseName);
        String startTime = null;

        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE, // table
                new String[]{"datetime(" + WorkoutSummaries.TIME_START + ", '" + timeZone + "')"}, // columns
                WorkoutSummaries.FILE_BASE_NAME + "=?",  // selection
                new String[]{fileBaseName}, //selectionArgs,
                null, null, null)) { // groupBy, having, orderBy)

            if (cursor.moveToFirst()) {
                startTime = cursor.getString(0);
            }
        }

        return startTime;
    }


    // TODO: here, we only delete the workout from this db.  Use WorkoutDeletionHelper to delete from all DBs.
    boolean deleteWorkout(long workoutId) {
        if (DEBUG) Log.i(TAG, "deleteWorkout: workoutId=" + workoutId);

        SQLiteDatabase dbSummaries = getDatabase();
        dbSummaries.delete(WorkoutSummaries.TABLE, WorkoutSummaries.C_ID + "=?", new String[]{workoutId + ""});
        dbSummaries.delete(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, WorkoutSummaries.WORKOUT_ID + "=?", new String[]{workoutId + ""});
        dbSummaries.delete(WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.WORKOUT_ID + "=?", new String[]{workoutId + ""});

        return true;
    }


    // -- Fancy / Auto Name

    @NonNull
    public List<String> getFancyNameList() {
        List result = new LinkedList();

        Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, // table
                null, null, null, null, null, null);

        while (cursor.moveToNext()) {
            result.add(cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FANCY_NAME)));
        }

        return result;
    }

    public long getFancyNameId(String fancyName) {
        long fancyNameId = -1;

        Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS,
                null,
                WorkoutSummaries.FANCY_NAME + "=?",
                new String[]{fancyName},
                null, null, null);
        if (cursor.moveToFirst()) {
            fancyNameId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
        }

        return fancyNameId;
    }

    @NonNull
    public String getFancyNameAndIncrement(String fancyName) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(fancyName);

        SQLiteDatabase db = getDatabase();
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

        return stringBuilder.toString();
    }

    // TODO: create helper class instead of passing database managers arround...
    @Nullable
    public String getFancyName(SportTypeDatabaseManager sportTypeDatabaseManager,
                               long sportTypeId,
                               @Nullable KnownLocationsDatabaseManager.MyLocation startLocation,
                               @Nullable KnownLocationsDatabaseManager.MyLocation maxLineDistanceLocation,
                               @Nullable KnownLocationsDatabaseManager.MyLocation endLocation) {
        if (startLocation != null & endLocation != null) {

            StringBuilder stringBuilder = new StringBuilder();

            SQLiteDatabase db = getDatabase();

            // get the first part, something like #bike2work, b2w, >> work ...
            Cursor cursor = db.query(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, // table
                    null,  // columns,
                    WorkoutSummaries.SPORT_ID + "=? AND " + WorkoutSummaries.START_LOCATION_NAME + "=? AND " + WorkoutSummaries.END_LOCATION_NAME + "=?", // selection,
                    new String[]{Long.toString(sportTypeId), startLocation.name, endLocation.name}, // selectionArgs,
                    null, null, null);// groupBy, having, orderBy

            if (cursor.moveToFirst()) {
                stringBuilder.append(cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FANCY_NAME)));
            } else {
                stringBuilder.append(createDefaultFancyName(sportTypeDatabaseManager, sportTypeId, startLocation, maxLineDistanceLocation, endLocation));
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

            return stringBuilder.toString();
        }

        return null;
    }

    // TODO: create extra helper instead of passing database managers arround...
    @Nullable
    protected String createDefaultFancyName(SportTypeDatabaseManager sportTypeDatabaseManager,
                                            long sportTypeId,
                                            @Nullable KnownLocationsDatabaseManager.MyLocation startLocation,
                                            KnownLocationsDatabaseManager.MyLocation maxLineDistanceLocation,
                                            @Nullable KnownLocationsDatabaseManager.MyLocation endLocation) {

        if (startLocation != null & endLocation != null) {
            StringBuilder stringBuilder = new StringBuilder();

            if (startLocation.id != endLocation.id) { // probably a commute
                stringBuilder.append("#");
                stringBuilder.append(sportTypeDatabaseManager.getUIName(sportTypeId));
                stringBuilder.append("2");
                stringBuilder.append(endLocation.name);
            } else { // a loop
                stringBuilder.append(sportTypeDatabaseManager.getUIName(sportTypeId)).append("@").append(startLocation.name);
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

            getDatabase().insert(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, null, contentValues);

            return baseName;
        }

        return null;
    }

    public void deleteFancyName(long id) {
        if (DEBUG) Log.i(TAG, "deleteFancyName: id=" + id);

        getDatabase().delete(WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS,
                WorkoutSummaries.C_ID + " =? ",
                new String[]{Long.toString(id)});
    }


    /**
     * Getting some stats for an equipment or a sport type
     */
    public static class Stats {
        public double totalDistanceM = 0;
        public long totalActiveTimeS = 0;
        public int totalAscentM = 0;
        public String firstUsage = null;
        public String lastUsage = null;
        public int count = 0;
    }

    /**
     * generic method to get the total Stats for a column=id
     */
    private Stats getStatsForColumn(String column, long id) {
        Stats stats = new Stats();
        SQLiteDatabase db = getDatabase();

        String[] columns = {
                "SUM(" + WorkoutSummaries.DISTANCE_TOTAL_m + ")",
                "SUM(" + WorkoutSummaries.TIME_ACTIVE_s + ")",
                "SUM(" + WorkoutSummaries.ASCENDING + ")",
                "MIN(" + WorkoutSummaries.TIME_START + ")",
                "MAX(" + WorkoutSummaries.TIME_START + ")",
                "COUNT(*)" // count of workouts
        };

        Cursor cursor = db.query(
                WorkoutSummaries.TABLE,
                columns,
                column + "=?",
                new String[]{String.valueOf(id)},
                null, null, null
        );

        if (cursor != null && cursor.moveToFirst()) {
            stats.totalDistanceM = cursor.getDouble(0);
            stats.totalActiveTimeS = cursor.getLong(1);
            stats.totalAscentM = cursor.getInt(2);
            stats.firstUsage = cursor.getString(3);
            stats.lastUsage = cursor.getString(4);
            stats.count = cursor.getInt(5); // Extract count
            cursor.close();
        }

        return stats;
    }

    /**
     * Aggregates workout statistics for a specific piece of equipment.
     */
    public Stats getEquipmentStats(long equipmentId) {
        return getStatsForColumn(WorkoutSummaries.EQUIPMENT_ID, equipmentId);
    }

    /**
     * Aggregates workout statistics for a specific sport type
     */
    public Stats getSportTypeStats(long sportTypeId) {
        return getStatsForColumn(WorkoutSummaries.SPORT_ID, sportTypeId);
    }


    /**
     * Generic method to get the Stats for a Period
     */
    public Stats getStatsForPeriod(String column, long equipmentId, long startTimeS, long endTimeS) {
        Stats stats = new Stats();
        SQLiteDatabase db = getDatabase();

        String[] columns = {
                "SUM(" + WorkoutSummaries.DISTANCE_TOTAL_m + ")",
                "SUM(" + WorkoutSummaries.TIME_ACTIVE_s + ")",
                "SUM(" + WorkoutSummaries.ASCENDING + ")",
                "MIN(" + WorkoutSummaries.TIME_START + ")",
                "MAX(" + WorkoutSummaries.TIME_START + ")",
                "COUNT(*)"
        };

        // Compare DATETIME column against numeric unix timestamps
        String selection = column + "=? AND " +
                WorkoutSummaries.TIME_START + " >= datetime(?, 'unixepoch') AND " +
                WorkoutSummaries.TIME_START + " <= datetime(?, 'unixepoch')";

        String[] selectionArgs = {
                String.valueOf(equipmentId),
                String.valueOf(startTimeS),
                String.valueOf(endTimeS)
        };

        try (Cursor cursor = db.query(WorkoutSummaries.TABLE, columns, selection, selectionArgs, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                stats.totalDistanceM = cursor.getDouble(0);
                stats.totalActiveTimeS = cursor.getLong(1);
                stats.totalAscentM = cursor.getInt(2);
                stats.firstUsage = cursor.getString(3);
                stats.lastUsage = cursor.getString(4);
                stats.count = cursor.getInt(5);
            }
        }
        return stats;
    }

    /**
     * Aggregates statistics for a specific equipment within a time range.
     */
    public Stats getEquipmentStatsForPeriod(long equipmentId, long startTimeS, long endTimeS) {
        return getStatsForPeriod(WorkoutSummaries.EQUIPMENT_ID, equipmentId, startTimeS, endTimeS);
    }

    /**
     * Aggregates statistics for a specific sport type within a time range.
     */
    public Stats getSportTypeStatsForPeriod(long sportTypeId, long startTimeS, long endTimeS) {
        return getStatsForPeriod(WorkoutSummaries.SPORT_ID, sportTypeId, startTimeS, endTimeS);
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
        // public static final String ATHLETE_NAME = "athleteName"; 2026-01: no longer supported/needed
        public static final String GOAL = "goal";
        public static final String METHOD = "method";
        public static final String EQUIPMENT_ID = "equipmentId";
        public static final String DESCRIPTION = "description";
        // public static final String SAMPLING_TIME = "samplingTime"; 2026-01: no longer supported/needed.  Will be always 1.
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
        // public static final String PRIVATE = "private";  2026-01 no longer supported / needed.
        public static final String COMMUTE = "commute";
        public static final String TRAINER = "trainer";
        public static final String UPLOAD_TO_STRAVA = "uploadToStrava";                             // added in Version 16 (08.05.2026)
        public static final String ASCENDING = "ascending";
        public static final String DESCENDING = "descending";
        public static final String MAP_POLYLINE = "mapPolyline"; // added in Version 13
        public static final String DISTANCE_STREAM = "distanceStream"; // added in Version 14
        public static final String ALTITUDE_STREAM = "altitudeStream"; // added in Version 14
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

        public final static int ENCODING_STEP_SIZE = 20;  // twenty seconds (Introduced in Version 15)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The database itself
    ////////////////////////////////////////////////////////////////////////////////////////////////
    public static class WorkoutSummariesDbHelper extends SQLiteOpenHelper {
        private final Context mContext;


        public static final String DB_NAME = "WorkoutSummaries.db";
        // public static final int DB_VERSION  = 4; // upgrade to Version 4 around November 2015
        // public static final int DB_VERSION  = 5; // upgrade to Version 5 at 1. December 2015
        // public static final int DB_VERSION = 6; // upgrade to Version 6 at 7. December 2015
        // public static final int DB_VERSION = 7; // upgrade to Version 7 at 16. May 2016
        // public static final int DB_VERSION = 8; // upgrade to Version 8 at 1. June 2016
        // public static final int DB_VERSION = 9; // upgrade to Version 9 at 7. June 2016
        // public static final int DB_VERSION = 10; // upgrade to Version 10 at 8. June 2016
        // public static final int DB_VERSION = 11; // upgrade to Version 11 at 19. 01. 2017
        // public static final int DB_VERSION = 12; // upgrade to Version 12 at 22.01.2026
        // public static final int DB_VERSION = 13; // upgrade to Version 13 at 05.05.2026
        // public static final int DB_VERSION = 14; // upgrade to Version 14 at 05.05.2026
        // public static final int DB_VERSION = 15; // upgrade to Version 15 at 06.05.2026: Unique step size for encoding map polyline, distance, and elevation: ENCODIN_STEP_SIZE
        // public static final int DB_VERSION = 16; // upgrade to Version 16 at 08.05.2026: Added uploadToStrava
        public static final int DB_VERSION = 17; // 08.05.2026: Bugfix: add eventually missing columns (altitude and distance stream)


        protected static final String CREATE_TABLE = "create table " + WorkoutSummaries.TABLE + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.WORKOUT_NAME + " text,"
                + WorkoutSummaries.FILE_BASE_NAME + " text,"
                // + WorkoutSummaries.ATHLETE_NAME + " text,"  // removed in verison 12
                + WorkoutSummaries.DESCRIPTION + " text,"
                + WorkoutSummaries.GOAL + " text,"
                + WorkoutSummaries.METHOD + " text,"
                // + WorkoutSummaries.SPORT + " text,"
                + WorkoutSummaries.B_SPORT + " text,"
                + WorkoutSummaries.SPORT_ID + " int,"
                + WorkoutSummaries.EQUIPMENT_ID + " int,"
                // + WorkoutSummaries.SAMPLING_TIME + " int,"  // removed in version 12.
                + WorkoutSummaries.TIME_START + " DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + WorkoutSummaries.TIME_ACTIVE_s + " int,"
                + WorkoutSummaries.TIME_TOTAL_s + " int,"
                + WorkoutSummaries.DISTANCE_TOTAL_m + " real,"
                + WorkoutSummaries.SPEED_AVERAGE_mps + " real,"
                + WorkoutSummaries.GC_DATA + " text,"
                + WorkoutSummaries.CALORIES + " int,"
                + WorkoutSummaries.LAPS + " int,"
                + WorkoutSummaries.FINISHED + " int," // end of version 3
                // + WorkoutSummaries.PRIVATE + " int,"  removed in version 12.
                + WorkoutSummaries.COMMUTE + " int,"
                + WorkoutSummaries.TRAINER + " int,"
                + WorkoutSummaries.UPLOAD_TO_STRAVA + " int DEFAULT -1," // added in Version 16 (-1: check preferences, 0: no, 1: yes)
                + WorkoutSummaries.ASCENDING + " int,"
                + WorkoutSummaries.DESCENDING + " int," // end of version 4
                + WorkoutSummaries.MAP_POLYLINE + " text,"    // added in Version 13
                + WorkoutSummaries.DISTANCE_STREAM + " text," // added in Version 14
                + WorkoutSummaries.ALTITUDE_STREAM + " text," // added in Version 14
                + WorkoutSummaries.EXTREMA_VALUES_CALCULATED + " int)";

        protected static final String CREATE_TABLE_EXTREMA_VALUES = "create table " + WorkoutSummaries.TABLE_EXTREMA_VALUES + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.WORKOUT_ID + " int,"
                + WorkoutSummaries.EXTREMA_TYPE + " text,"
                + WorkoutSummaries.SENSOR_TYPE + " text,"
                + WorkoutSummaries.VALUE + " real," // end of version 5
                + WorkoutSummaries.SAMPLES_COLUMN_ID + " int)";

        protected static final String CREATE_TABLE_ACCUMULATED_SENSORS = "create table " + WorkoutSummaries.TABLE_ACCUMULATED_SENSORS + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.WORKOUT_ID + " int,"
                + WorkoutSummaries.SENSOR_TYPE + " text)";

        protected static final String CREATE_TABLE_WORKOUT_NAME_PATTERNS
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
        private static final boolean DEBUG = TrainingApplication.getDebug(true);

        // Constructor
        public WorkoutSummariesDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);

            this.mContext = context.getApplicationContext();
        }
        // TODO: add location (latitude and longitude) and add them when needed

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE);

            // new in version 4:
            db.execSQL(CREATE_TABLE_EXTREMA_VALUES);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_EXTREMA_VALUES);

            db.execSQL(CREATE_TABLE_ACCUMULATED_SENSORS);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_ACCUMULATED_SENSORS);

            db.execSQL(CREATE_TABLE_WORKOUT_NAME_PATTERNS);
            if (DEBUG) Log.d(TAG, "onCreate sql: " + CREATE_TABLE_WORKOUT_NAME_PATTERNS);

        }

        private void addColumn(@NonNull SQLiteDatabase db, String table, String column, String type, String defaultValue) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + " DEFAULT " + defaultValue + ";");
        }

        /**
         * Safely adds a column to a table only if it does not already exist.
         */
        private void addColumnIfNotExists(SQLiteDatabase db, String tableName, String columnName, String columnType, String defaultValue) {
            try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null)) {
                boolean exists = false;
                while (cursor.moveToNext()) {
                    // The "name" column in PRAGMA table_info holds the column names
                    String existingColumn = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                    if (columnName.equalsIgnoreCase(existingColumn)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    String sql = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType;
                    if (defaultValue != null) {
                        sql += " DEFAULT " + defaultValue;
                    }
                    db.execSQL(sql);
                    Log.i(TAG, "Added missing column [" + columnName + "] to table [" + tableName + "]");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking/adding column: " + columnName, e);
            }
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 4) {
                Log.i(TAG, "upgrading to DB version 4");
                // addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.PRIVATE, "int");  // removed in version 12
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.COMMUTE, "int", "0");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.TRAINER, "int", "0");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.ASCENDING, "int", "0");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.DESCENDING, "int", "0");

                db.execSQL(CREATE_TABLE_EXTREMA_VALUES);
                db.execSQL(CREATE_TABLE_ACCUMULATED_SENSORS);
            }

            if (oldVersion < 5) {  // this version of the database was never released.
                Log.i(TAG, "upgrading to DB version 5");

                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.EXTREMA_VALUES_CALCULATED, "int", "0");
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
                addColumn(db, WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, WorkoutSummaries.COUNTER, "int", "0");
            } else if (oldVersion < 10) {
                Log.i(TAG, "upgrading to DB version 10");

                db.execSQL(CREATE_TABLE_WORKOUT_NAME_PATTERNS);
            }

            if (oldVersion < 11) {
                Log.i(TAG, "upgrading to DB version 11");
                db.beginTransaction();
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.SPORT_ID, "int", "0");
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


                addColumn(db, WorkoutSummaries.TABLE_WORKOUT_NAME_PATTERNS, WorkoutSummaries.SPORT_ID, "text", "???");

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

            if (oldVersion < 12) {
                // do nothing.  The removed rows does not matter.
            }

            if (oldVersion < 13) {

                // first, add the new column
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.MAP_POLYLINE, "text", "");

                // 2. Perform the migration
                migrateExistingWorkouts13(db);
            }

            if (oldVersion < 14) {
                // first, add the new columns
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.DISTANCE_STREAM, "text", "");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.ALTITUDE_STREAM, "text", "");

                // 2. Perform the migration
                migrateExistingWorkouts14(db);
            }

            if (oldVersion < 15) {
                // recalc the encoded strings with the unique step size.
                migrateExistingWorkouts13(db);
                migrateExistingWorkouts14(db);
            }

            if (oldVersion < 16) {
                // add the new column
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.UPLOAD_TO_STRAVA, "int", "-1");
            }

            if (oldVersion < 17) {
                // while upgrading to Version 14, we continued ot create the table for versin 13 :(
                // Thus, we add the forgotten columns
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.DISTANCE_STREAM, "text", "");
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.ALTITUDE_STREAM, "text", "");

                migrateExistingWorkouts13(db);
                migrateExistingWorkouts14(db);
            }
        }

        /**
         * Iterates through all existing workouts, fetches their points from the
         * samples database, encodes them into a polyline string, and saves it.
         */
        private void migrateExistingWorkouts13(SQLiteDatabase db) {
            Log.i(TAG, "Starting Migration: Encoding tracks to polylines...");

            // 1. Get IDs and BaseFileNames from the Summaries table (the one being upgraded)
            String[] projection = {WorkoutSummaries.C_ID, WorkoutSummaries.FILE_BASE_NAME};

            try (Cursor summaryCursor = db.query(WorkoutSummaries.TABLE, projection, null, null, null, null, null)) {
                if (summaryCursor != null) {
                    int idIdx = summaryCursor.getColumnIndex(WorkoutSummaries.C_ID);
                    int fileIdx = summaryCursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME);

                    while (summaryCursor.moveToNext()) {
                        long workoutId = summaryCursor.getLong(idIdx);
                        String baseFileName = summaryCursor.getString(fileIdx);

                        if (baseFileName != null) {
                            // 2. Derive the points
                            String polyline = getEncodedStringForWorkout(baseFileName);

                            if (polyline != null) {
                                // 3. Update the summary table
                                ContentValues values = new ContentValues();
                                values.put(WorkoutSummaries.MAP_POLYLINE, polyline);
                                db.update(WorkoutSummaries.TABLE, values, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(workoutId)});
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Migration failed", e);
            }
        }

        /**
         * Ported logic from WorkoutRepository.kt to fetch and encode points in Java
         */
        private String getEncodedStringForWorkout(String baseFileName) {
            List<LatLng> latLngs = new ArrayList<>();

            // Access the Samples database via its manager
            // Note: Ensure WorkoutSamplesDatabaseManager.getInstance(mContext) is available
            SQLiteDatabase samplesDb = WorkoutSamplesDatabaseManager.getInstance(mContext).getDatabase();
            String tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName);

            String latName = "LATITUDE";
            String lonName = "LONGITUDE";

            try (Cursor cursor = samplesDb.query(tableName, null, null, null, null, null, null)) {
                if (cursor != null) {
                    int latIdx = cursor.getColumnIndex(latName);
                    int lonIdx = cursor.getColumnIndex(lonName);

                    while (cursor.move(WorkoutSummaries.ENCODING_STEP_SIZE)) {
                        if (latIdx != -1 && lonIdx != -1 && !cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                            latLngs.add(new LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx)));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading samples for " + baseFileName, e);
                return null;
            }

            if (latLngs.isEmpty()) return null;

            // Use Google Maps Utility to encode
            return PolyUtil.encode(latLngs);
        }

        /**
         * Iterates through all existing workouts, fetches their points from the
         * samples database, encodes them into a polyline string, and saves it.
         */
        private void migrateExistingWorkouts14(SQLiteDatabase db) {
            Log.i(TAG, "Starting Migration 14: Encoding altitude and distance streams...");

            String[] projection = {WorkoutSummaries.C_ID, WorkoutSummaries.FILE_BASE_NAME};

            try (Cursor summaryCursor = db.query(WorkoutSummaries.TABLE, projection, null, null, null, null, null)) {
                if (summaryCursor != null) {
                    int idIdx = summaryCursor.getColumnIndex(WorkoutSummaries.C_ID);
                    int fileIdx = summaryCursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME);

                    while (summaryCursor.moveToNext()) {
                        long workoutId = summaryCursor.getLong(idIdx);
                        String baseFileName = summaryCursor.getString(fileIdx);

                        if (baseFileName != null) {
                            // Fetch and encode numerical streams
                            saveEncodedStreamsForWorkout(db, workoutId, baseFileName);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Migration 14 failed", e);
            }
        }

        private void saveEncodedStreamsForWorkout(SQLiteDatabase db, long workoutId, String baseFileName) {
            List<Double> altitudes = new ArrayList<>();
            List<Double> distances = new ArrayList<>();

            SQLiteDatabase samplesDb = WorkoutSamplesDatabaseManager.getInstance(mContext).getDatabase();
            String tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName);

            // Using standard column names from your database schema
            String altName = "ALTITUDE";
            String distName = "DISTANCE_m";

            try (Cursor cursor = samplesDb.query(tableName, new String[]{altName, distName}, null, null, null, null, null)) {
                if (cursor != null) {
                    int altIdx = cursor.getColumnIndex(altName);
                    int distIdx = cursor.getColumnIndex(distName);

                    // Step 5 for summaries creates a smooth profile and keeps the string short
                    while (cursor.move(WorkoutSummaries.ENCODING_STEP_SIZE)) {
                        if (altIdx != -1 && distIdx != -1) {
                            altitudes.add(cursor.getDouble(altIdx));
                            distances.add(cursor.getDouble(distIdx));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading samples for stream encoding: " + baseFileName);
                return;
            }

            if (!altitudes.isEmpty()) {
                // Use the NumericalEncodingUtils (Kotlin object)
                String encAlt = NumericalEncodingUtils.INSTANCE.encodeDoubles(altitudes);
                String encDist = NumericalEncodingUtils.INSTANCE.encodeDoubles(distances);

                ContentValues values = new ContentValues();
                values.put(WorkoutSummaries.ALTITUDE_STREAM, encAlt);
                values.put(WorkoutSummaries.DISTANCE_STREAM, encDist);

                db.update(WorkoutSummaries.TABLE, values, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(workoutId)});
            }
        }
    }
}
