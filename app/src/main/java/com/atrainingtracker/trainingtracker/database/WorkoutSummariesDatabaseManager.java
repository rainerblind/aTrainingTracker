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

/**
 * Orchestrates all persistent storage operations for high-level workout session metadata.
 *
 * This manager provides a thread-safe interface to the `WorkoutSummaries` and `ExtremumValues`
 * SQLite tables. It handles session identity, equipment links, analytical aggregates (extrema),
 * and spatial boundaries.
 *
 * Architectural Role: Primary data management layer for workout history summaries.
 * Threading: Methods should generally be called from background contexts to avoid UI block.
 */
public class WorkoutSummariesDatabaseManager {
    private static final String TAG = "WorkoutSummariesDatabaseManager";
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    private static WorkoutSummariesDbHelper cWorkoutSummariesDbHelper;
    private static volatile WorkoutSummariesDatabaseManager cInstance;
    private SQLiteDatabase mDatabase = null;

    /**
     * Private constructor to prevent direct instantiation and ensure singleton pattern.
     */
    private WorkoutSummariesDatabaseManager(Context context) {
        // The helper is instantiated with the application context, making it safe.
        cWorkoutSummariesDbHelper = new WorkoutSummariesDbHelper(context);
    }

    /**
     * Provides the thread-safe singleton instance of the manager.
     * Uses double-checked locking for lazy initialization.
     *
     * @param context The application context.
     * @return The authoritative manager instance.
     */
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
                cWorkoutSummariesDbHelper.close();
            }
            mDatabase = cWorkoutSummariesDbHelper.getWritableDatabase();
            return mDatabase;
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // some high level helper methods
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Persists all user-editable and analytical fields for a workout session.
     *
     * Implementation: Updates the primary session row with current metadata, including
     * sport identity, equipment links, descriptive text, and spatial bounding boxes.
     *
     * @param workoutData The exhaustive session data to persist.
     */
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

        // cluster association (SCRUM-191)
        values.put(WorkoutSummaries.CLUSTER_ID, workoutData.getClusterId());

        // spatial bounds (ATT-352)
        if (workoutData.getMinLat() != null) values.put(WorkoutSummaries.BOUND_MIN_LAT, workoutData.getMinLat());
        if (workoutData.getMinLng() != null) values.put(WorkoutSummaries.BOUND_MIN_LNG, workoutData.getMinLng());
        if (workoutData.getMaxLat() != null) values.put(WorkoutSummaries.BOUND_MAX_LAT, workoutData.getMaxLat());
        if (workoutData.getMaxLng() != null) values.put(WorkoutSummaries.BOUND_MAX_LNG, workoutData.getMaxLng());

        getDatabase().update(WorkoutSummaries.TABLE,
                values,
                WorkoutSummaries.C_ID + "=" + workoutData.getId(),
                null);
    }

    /**
     * Applies an inferred identity (Equipment, Strava) to a workout based on its SportType.
     * Use this during learning or cluster assignment (SCRUM-200).
     */
    public void applyInferredIdentity(long workoutId, EquipmentAndSportTypeDiscoveryManager.InferredIdentity identity) {
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.SPORT_ID, identity.getSportId());
        values.put(WorkoutSummaries.B_SPORT, identity.getBSportType().name());

        long equipmentId = identity.getEquipmentId();
        if (equipmentId == -1) {
            values.putNull(WorkoutSummaries.EQUIPMENT_ID);
        } else {
            values.put(WorkoutSummaries.EQUIPMENT_ID, equipmentId);
        }

        values.put(WorkoutSummaries.UPLOAD_TO_STRAVA, identity.getUploadToStrava());

        getDatabase().update(WorkoutSummaries.TABLE,
                values,
                WorkoutSummaries.C_ID + "=" + workoutId,
                null);
    }

    /**
     * Finds the most frequent sportId associated with a specific cluster.
     * Used to refine the "probable sport" for a route family.
     * It's safer to do it in WorkoutClusterEngine.migrateHistory
     */
    public long getMostFrequentSportIdForCluster(long clusterId) {
        if (clusterId == -1) return -1;
        String sql = "SELECT " + WorkoutSummaries.SPORT_ID + ", COUNT(*) as cnt FROM " + WorkoutSummaries.TABLE +
                " WHERE " + WorkoutSummaries.CLUSTER_ID + " = ?" +
                " GROUP BY " + WorkoutSummaries.SPORT_ID +
                " ORDER BY cnt DESC LIMIT 1";
        try (Cursor cursor = getDatabase().rawQuery(sql, new String[]{String.valueOf(clusterId)})) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.SPORT_ID));
            }
        }
        return -1;
    }

    /**
     * Clears the cluster ID for any workout linked to it (SCRUM-217).
     */
    public int clearClusterLink(long clusterId) {
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.CLUSTER_ID, -1L);
        return getDatabase().update(WorkoutSummaries.TABLE, values,
                WorkoutSummaries.CLUSTER_ID + "=?", new String[]{String.valueOf(clusterId)});
    }

    /**
     * Retrieves a cursor for a specific workout ID.
     */
    public Cursor getWorkoutCursor(long workoutId) {
        return getDatabase().query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null, null, null);
    }

    /**
     * Returns a cursor for all workouts sorted descending (newest first).
     */
    public Cursor getCursorForAllWorkouts() {
        return getDatabase().query(
                WorkoutSummaries.TABLE,
                null, null, null, null, null,
                WorkoutSummaries.TIME_START + " DESC"
        );
    }

    /**
     * Returns a cursor for all workouts sorted ascending (oldest first).
     */
    public Cursor getCursorForAllWorkoutsAsc() {
        return getDatabase().query(
                WorkoutSummaries.TABLE,
                null, null, null, null, null,
                WorkoutSummaries.TIME_START + " ASC"
        );
    }

    /**
     * Returns a cursor for all workouts within a specific time range.
     * Used for hierarchical periods (ATT-346) and analytical rollups.
     *
     * @param startTimeS Start of range in seconds.
     * @param endTimeS End of range in seconds.
     * @return A cursor sorted by start time ascending.
     */
    public Cursor getWorkoutsInRangeCursor(long startTimeS, long endTimeS) {
        String selection = "strftime('%s', " + WorkoutSummaries.TIME_START + ") >= ? AND " +
                "strftime('%s', " + WorkoutSummaries.TIME_START + ") <= ?";
        String[] selectionArgs = {String.valueOf(startTimeS), String.valueOf(endTimeS)};
        return getDatabase().query(
                WorkoutSummaries.TABLE,
                null, selection, selectionArgs, null, null,
                WorkoutSummaries.TIME_START + " ASC"
        );
    }


    @Nullable
    public String getBaseFileName(long workoutId) {
        return getBaseFileName(getDatabase(), workoutId);
    }

    @Nullable
    public String getBaseFileName(SQLiteDatabase db, long workoutId) {
        if (DEBUG) Log.i(TAG, "getBaseFileName for workoutId: " + workoutId);

        String baseFileName = null;

        try (Cursor cursor = db.query(WorkoutSummaries.TABLE,
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
                int index = cursor.getColumnIndexOrThrow(key);
                if (!cursor.isNull(index)) {
                    value = cursor.getDouble(index);
                }
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

    /**
     * Retrieves the peak value (Min/Mean/Max) for a specific sensor in a workout.
     */
    @Nullable
    public Double getExtremaValue(long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType) {
        return getExtremaValue(getDatabase(), workoutId, sensorType, extremaType);
    }

    /**
     * Retrieves the peak value using an explicit database connection.
     */
    @Nullable
    public Double getExtremaValue(SQLiteDatabase db, long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType) {
        Double extremaValue = null;

        try(Cursor cursor = db.query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
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

    /**
     * Retrieves the geographical position associated with a sensor peak.
     */
    @Nullable
    public LatLng getExtremaPosition(long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType) {
        LatLng position = null;

        try(Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE_EXTREMA_VALUES,
                new String[]{WorkoutSummaries.LATITUDE, WorkoutSummaries.LONGITUDE},
                WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=?",
                new String[]{Long.toString(workoutId), sensorType.name(), extremaType.name()},
                null, null, null)) {

            if (cursor.moveToFirst()) {
                int latIdx = cursor.getColumnIndex(WorkoutSummaries.LATITUDE);
                int lonIdx = cursor.getColumnIndex(WorkoutSummaries.LONGITUDE);
                if (!cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                    position = new LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx));
                }
            }
        }
        return position;
    }

    /**
     * DTO for batch extrema lookups (ATT-359).
     */
    public static class ExtremaRecord {
        public final long workoutId;
        public final SensorType sensorType;
        public final ExtremaType extremaType;
        public final double value;
        public final LatLng position;

        public ExtremaRecord(long workoutId, SensorType sensorType, ExtremaType extremaType, double value, LatLng position) {
            this.workoutId = workoutId;
            this.sensorType = sensorType;
            this.extremaType = extremaType;
            this.value = value;
            this.position = position;
        }
    }

    /**
     * Fetches all extrema records for a batch of workout IDs in a single query (ATT-359).
     * Reduces O(N) queries to O(1) for high-performance list loading.
     *
     * @param workoutIds A collection of IDs to fetch peaks for.
     * @return A list of [ExtremaRecord] objects.
     */
    public List<ExtremaRecord> getExtremaForWorkouts(java.util.Collection<Long> workoutIds) {
        List<ExtremaRecord> records = new ArrayList<>();
        if (workoutIds.isEmpty()) return records;

        StringBuilder inClause = new StringBuilder();
        for (Long id : workoutIds) {
            if (inClause.length() > 0) inClause.append(",");
            inClause.append(id);
        }

        String selection = WorkoutSummaries.WORKOUT_ID + " IN (" + inClause + ")";
        
        try (Cursor cursor = getDatabase().query(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, selection, null, null, null, null)) {
            int wIdIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.WORKOUT_ID);
            int sensorIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.SENSOR_TYPE);
            int typeIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.EXTREMA_TYPE);
            int valIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.VALUE);
            int latIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.LATITUDE);
            int lonIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.LONGITUDE);

            while (cursor.moveToNext()) {
                LatLng pos = null;
                if (!cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                    pos = new LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx));
                }
                records.add(new ExtremaRecord(
                        cursor.getLong(wIdIdx),
                        SensorType.valueOf(cursor.getString(sensorIdx)),
                        ExtremaType.valueOf(cursor.getString(typeIdx)),
                        cursor.getDouble(valIdx),
                        pos
                ));
            }
        }
        return records;
    }

    /**
     * Atomically updates or inserts a sensor peak value and its location.
     */
    public void updateExtremaValue(long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType, double value, @Nullable LatLng position) {
        updateExtremaValue(getDatabase(), workoutId, sensorType, extremaType, value, position);
    }

    /**
     * Implementation for extrema update using an explicit database handle.
     */
    public void updateExtremaValue(SQLiteDatabase db, long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType, double value, @Nullable LatLng position) {
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.WORKOUT_ID, workoutId);
        values.put(WorkoutSummaries.SENSOR_TYPE, sensorType.name());
        values.put(WorkoutSummaries.EXTREMA_TYPE, extremaType.name());
        values.put(WorkoutSummaries.VALUE, value);
        if (position != null) {
            values.put(WorkoutSummaries.LATITUDE, position.latitude);
            values.put(WorkoutSummaries.LONGITUDE, position.longitude);
        }

        String where = WorkoutSummaries.WORKOUT_ID + "=? AND " + WorkoutSummaries.SENSOR_TYPE + "=? AND " + WorkoutSummaries.EXTREMA_TYPE + "=?";
        String[] args = {String.valueOf(workoutId), sensorType.name(), extremaType.name()};

        if (db.update(WorkoutSummaries.TABLE_EXTREMA_VALUES, values, where, args) == 0) {
            db.insert(WorkoutSummaries.TABLE_EXTREMA_VALUES, null, values);
        }
    }

    /**
     * Updates the map polyline and analytical streams (Altitude/Distance) for a workout.
     *
     * Implementation: Also recalculates the spatial bounding box (N/S/E/W) based on the
     * provided polyline to ensure zero-latency map focus in the list view (ATT-352).
     */
    public void updateMapAndStreams(long workoutId, String polyline, String altitudeStream, String distanceStream) {
        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.MAP_POLYLINE, polyline);
        values.put(WorkoutSummaries.ALTITUDE_STREAM, altitudeStream);
        values.put(WorkoutSummaries.DISTANCE_STREAM, distanceStream);

        if (polyline != null && !polyline.isEmpty()) {
            List<LatLng> decoded = PolyUtil.decode(polyline);
            if (!decoded.isEmpty()) {
                double minLat = 90.0, maxLat = -90.0, minLng = 180.0, maxLng = -180.0;
                for (LatLng p : decoded) {
                    if (p.latitude < minLat) minLat = p.latitude;
                    if (p.latitude > maxLat) maxLat = p.latitude;
                    if (p.longitude < minLng) minLng = p.longitude;
                    if (p.longitude > maxLng) maxLng = p.longitude;
                }
                values.put(WorkoutSummaries.BOUND_MIN_LAT, minLat);
                values.put(WorkoutSummaries.BOUND_MIN_LNG, minLng);
                values.put(WorkoutSummaries.BOUND_MAX_LAT, maxLat);
                values.put(WorkoutSummaries.BOUND_MAX_LNG, maxLng);
            }
        }

        getDatabase().update(WorkoutSummaries.TABLE, values, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(workoutId)});
    }

    /**
     * Concatenates new data segments to existing map and data streams.
     * Use this during live tracking for incremental persistence.
     */
    public void appendToMapAndStreams(long workoutId, String polylineSuffix, String altitudeSuffix, String distanceSuffix) {
        String sql = "UPDATE " + WorkoutSummaries.TABLE + " SET "
                + WorkoutSummaries.MAP_POLYLINE + " = IFNULL(" + WorkoutSummaries.MAP_POLYLINE + ", '') || ?, "
                + WorkoutSummaries.DISTANCE_STREAM + " = IFNULL(" + WorkoutSummaries.DISTANCE_STREAM + ", '') || ?, "
                + WorkoutSummaries.ALTITUDE_STREAM + " = IFNULL(" + WorkoutSummaries.ALTITUDE_STREAM + ", '') || ? "
                + " WHERE " + WorkoutSummaries.C_ID + " = ?";
        getDatabase().execSQL(sql, new Object[]{polylineSuffix, distanceSuffix, altitudeSuffix, workoutId});
    }

    /**
     * ATT-38: Shifts all altitude-related summary data by the given offset.
     * This includes shifting existing extrema and re-encoding the altitude stream.
     */
    public void shiftAltitudeData(long workoutId, double offset) {
        SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            // 1. Shift Extrema Table
            String extremaSql = "UPDATE " + WorkoutSummaries.TABLE_EXTREMA_VALUES +
                    " SET " + WorkoutSummaries.VALUE + " = " + WorkoutSummaries.VALUE + " + ?" +
                    " WHERE " + WorkoutSummaries.WORKOUT_ID + " = ? AND " +
                    WorkoutSummaries.SENSOR_TYPE + " = ?";
            db.execSQL(extremaSql, new Object[]{offset, workoutId, SensorType.ALTITUDE.name()});

            // 2. Shift Altitude Stream
            String stream = getString(workoutId, WorkoutSummaries.ALTITUDE_STREAM);
            if (stream != null && !stream.isEmpty()) {
                List<Double> alts = NumericalEncodingUtils.INSTANCE.decodeDoubles(stream);
                List<Double> shiftedAlts = new ArrayList<>(alts.size());
                for (Double a : alts) shiftedAlts.add(a + offset);

                ContentValues streamValues = new ContentValues();
                streamValues.put(WorkoutSummaries.ALTITUDE_STREAM, NumericalEncodingUtils.INSTANCE.encodeDoubles(shiftedAlts));
                db.update(WorkoutSummaries.TABLE, streamValues, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(workoutId)});
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }


    /**
     * Persists the list of sensor types that were active during a workout.
     */
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

    /**
     * Retrieves the set of sensor types recorded for a specific workout.
     */
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


    /**
     * Returns a list of workout IDs that are older than the specified number of days.
     */
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


    /**
     * Retrieves the start timestamp of a workout session, formatted for a specific timezone.
     */
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


    /**
     * Surgically deletes a workout and its related metadata from the summary and extrema tables.
     * Note: This does NOT delete raw samples. Use [WorkoutDeletionHelper] for full cleanup.
     *
     * @return true if the deletion was successful.
     */
    boolean deleteWorkout(long workoutId) {
        if (DEBUG) Log.i(TAG, "deleteWorkout: workoutId=" + workoutId);

        SQLiteDatabase dbSummaries = getDatabase();
        dbSummaries.delete(WorkoutSummaries.TABLE, WorkoutSummaries.C_ID + "=?", new String[]{workoutId + ""});
        dbSummaries.delete(WorkoutSummaries.TABLE_ACCUMULATED_SENSORS, WorkoutSummaries.WORKOUT_ID + "=?", new String[]{workoutId + ""});
        dbSummaries.delete(WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.WORKOUT_ID + "=?", new String[]{workoutId + ""});

        return true;
    }




    /**
     * DTO for aggregated workout statistics (Equipment or Sport Type).
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
     * Internal helper to calculate total statistics for a specific column/ID pair.
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
     * Aggregates workout statistics for a specific sport type.
     */
    public Stats getSportTypeStats(long sportTypeId) {
        return getStatsForColumn(WorkoutSummaries.SPORT_ID, sportTypeId);
    }


    /**
     * Aggregates statistics for a specific category within a time range.
     *
     * @param column The database column to filter by (e.g., EQUIPMENT_ID).
     * @param id The value of the column (e.g., specific equipment ID).
     * @param startTimeS Start of range in seconds.
     * @param endTimeS End of range in seconds.
     * @return A [Stats] object containing the totals for the period.
     */
    public Stats getStatsForPeriod(String column, long id, long startTimeS, long endTimeS) {
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
                "strftime('%s', " + WorkoutSummaries.TIME_START + ") >= ? AND " +
                "strftime('%s', " + WorkoutSummaries.TIME_START + ") <= ?";

        String[] selectionArgs = {
                String.valueOf(id),
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
     * Aggregates statistics for a specific piece of equipment within a time range.
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
        public static final String CLUSTER_ID = "clusterId"; // added in Version 20
        public static final String BOUND_MIN_LAT = "boundMinLat"; // added in Version 21
        public static final String BOUND_MIN_LNG = "boundMinLng"; // added in Version 21
        public static final String BOUND_MAX_LAT = "boundMaxLat"; // added in Version 21
        public static final String BOUND_MAX_LNG = "boundMaxLng"; // added in Version 21
        // new entries in version 5 of the DB
        @Deprecated
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
        public static final String LATITUDE = "latitude";
        public static final String LONGITUDE = "longitude";

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
        // public static final int DB_VERSION = 17; // 08.05.2026: Bugfix: add eventually missing columns (altitude and distance stream)
        // public static final int DB_VERSION = 18; // 10.06.2026 Added lat/long to the extrema values
        public static final int DB_VERSION = 21; // 25.07.2026 Added bounding box for performance (ATT-352)
        


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
                + WorkoutSummaries.CLUSTER_ID + " int DEFAULT -1," // added in Version 20
                + WorkoutSummaries.EXTREMA_VALUES_CALCULATED + " int,"
                + WorkoutSummaries.BOUND_MIN_LAT + " real,"   // added in Version 21
                + WorkoutSummaries.BOUND_MIN_LNG + " real,"   // added in Version 21
                + WorkoutSummaries.BOUND_MAX_LAT + " real,"   // added in Version 21
                + WorkoutSummaries.BOUND_MAX_LNG + " real)";  // added in Version 21

        protected static final String CREATE_TABLE_EXTREMA_VALUES = "create table " + WorkoutSummaries.TABLE_EXTREMA_VALUES + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.WORKOUT_ID + " int,"
                + WorkoutSummaries.EXTREMA_TYPE + " text,"
                + WorkoutSummaries.SENSOR_TYPE + " text,"
                + WorkoutSummaries.VALUE + " real," // end of version 5
                + WorkoutSummaries.LATITUDE + " real,"
                + WorkoutSummaries.LONGITUDE + " real,"
                + WorkoutSummaries.SAMPLES_COLUMN_ID + " int)";

        protected static final String CREATE_TABLE_ACCUMULATED_SENSORS = "create table " + WorkoutSummaries.TABLE_ACCUMULATED_SENSORS + " ("
                + WorkoutSummaries.C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + WorkoutSummaries.WORKOUT_ID + " int,"
                + WorkoutSummaries.SENSOR_TYPE + " text)";

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
                addColumn(db, "WorkoutNamePatterns", "counter", "int", "0");
            } else if (oldVersion < 10) {
                Log.i(TAG, "upgrading to DB version 10");

                db.execSQL("create table WorkoutNamePatterns ("
                        + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "sportId int, "
                        + "startLocationName text, "
                        + "endLocationName text, "
                        + "fancyName text, "
                        + "addCounter int, "
                        + "counter int, "
                        + "addVia int)");
            }

            if (oldVersion < 11) {
                Log.i(TAG, "upgrading to DB version 11");
                addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.SPORT_ID, "int", "0");
                // addColumn(db, WorkoutSummaries.TABLE, WorkoutSummaries.B_SPORT,  "text");

                Cursor cursor = db.query(WorkoutSummaries.TABLE,
                        new String[]{WorkoutSummaries.C_ID, "sport"},
                        null, null,
                        null, null, null);
                ContentValues contentValues = new ContentValues();
                while (cursor.moveToNext()) {
                    contentValues.clear();
                    long id = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
                    String sport = cursor.getString(cursor.getColumnIndex("sport"));
                    contentValues.put(WorkoutSummaries.SPORT_ID, SportTypeDatabaseManager.getSportTypeIdFromTTSportTypeName(sport));
                    contentValues.put(WorkoutSummaries.B_SPORT, SportTypeDatabaseManager.getBSportType(sport).name());
                    db.update(WorkoutSummaries.TABLE, contentValues,
                            WorkoutSummaries.C_ID + "=?", new String[]{Long.toString(id)});
                }
                cursor.close();


                addColumn(db, "WorkoutNamePatterns", "sportId", "text", "???");

                cursor = db.query("WorkoutNamePatterns",
                        new String[]{WorkoutSummaries.C_ID, "sport"},
                        null, null,
                        null, null, null);
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.C_ID));
                    String sport = cursor.getString(cursor.getColumnIndex("sport"));
                    contentValues.put(WorkoutSummaries.SPORT_ID, SportTypeDatabaseManager.getSportTypeIdFromTTSportTypeName(sport));
                    db.update("WorkoutNamePatterns", contentValues,
                            WorkoutSummaries.C_ID + "=?", new String[]{Long.toString(id)});
                }
                cursor.close();
            }

            if (oldVersion < 12) {
                // do nothing.  The removed rows does not matter.
            }

            if (oldVersion < 13) {
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.MAP_POLYLINE, "text", "''");
            }

            if (oldVersion < 14) {
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.DISTANCE_STREAM, "text", "''");
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.ALTITUDE_STREAM, "text", "''");
            }

            if (oldVersion < 16) {
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.UPLOAD_TO_STRAVA, "int", "-1");
            }

            if (oldVersion < 17) {
                // Bugfix for version 14 error: ensure columns exist
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.DISTANCE_STREAM, "text", "''");
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.ALTITUDE_STREAM, "text", "''");

                // Combined migration to avoid multiple passes and ANRs.
                // If oldVersion < 15, we must recalculate everything due to step size change.
                migrateExistingWorkouts(db, oldVersion < 15);
            }

            // 18 failed due ot a bug
            if (oldVersion < 19) {
                Log.i(TAG, "upgrading to DB version 19");
                addColumnIfNotExists(db, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.LATITUDE, "real", null);
                addColumnIfNotExists(db, WorkoutSummaries.TABLE_EXTREMA_VALUES, WorkoutSummaries.LONGITUDE, "real", null);

                // Populate new columns for existing data
                migrateExtremaPositions(db);
            }

            if (oldVersion < 20) {
                Log.i(TAG, "upgrading to DB version 20");
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.CLUSTER_ID, "int", "-1");

                // Trigger re-learning to populate clusterIds
                // We'll run migration logic after upgrade finished in TrainingApplication or here?
                // It's safer to do it in RouteClusterEngine.migrateHistory
            }

            if (oldVersion < 21) {
                Log.i(TAG, "upgrading to DB version 21 (Adding spatial bounds)");
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.BOUND_MIN_LAT, "real", null);
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.BOUND_MIN_LNG, "real", null);
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.BOUND_MAX_LAT, "real", null);
                addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.BOUND_MAX_LNG, "real", null);

                migrateSpatialBounds(db);
            }
        }

        private void migrateSpatialBounds(SQLiteDatabase db) {
            Log.i(TAG, "Populating spatial bounds for existing workouts...");
            try (Cursor cursor = db.query(WorkoutSummaries.TABLE,
                    new String[]{WorkoutSummaries.C_ID, WorkoutSummaries.MAP_POLYLINE},
                    null, null, null, null, null)) {
                int idIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.C_ID);
                int polylineIdx = cursor.getColumnIndexOrThrow(WorkoutSummaries.MAP_POLYLINE);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIdx);
                    String polyline = cursor.getString(polylineIdx);
                    if (polyline != null && !polyline.isEmpty()) {
                        List<LatLng> decoded = PolyUtil.decode(polyline);
                        if (!decoded.isEmpty()) {
                            double minLat = 90.0, maxLat = -90.0, minLng = 180.0, maxLng = -180.0;
                            for (LatLng p : decoded) {
                                if (p.latitude < minLat) minLat = p.latitude;
                                if (p.latitude > maxLat) maxLat = p.latitude;
                                if (p.longitude < minLng) minLng = p.longitude;
                                if (p.longitude > maxLng) maxLng = p.longitude;
                            }
                            ContentValues cv = new ContentValues();
                            cv.put(WorkoutSummaries.BOUND_MIN_LAT, minLat);
                            cv.put(WorkoutSummaries.BOUND_MIN_LNG, minLng);
                            cv.put(WorkoutSummaries.BOUND_MAX_LAT, maxLat);
                            cv.put(WorkoutSummaries.BOUND_MAX_LNG, maxLng);
                            db.update(WorkoutSummaries.TABLE, cv, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(id)});
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error migrating spatial bounds", e);
            }
        }

        private void migrateExtremaPositions(SQLiteDatabase summariesDb) {
            Log.i(TAG, "Starting migration of extrema positions...");

            WorkoutSummariesDatabaseManager summariesManager = WorkoutSummariesDatabaseManager.getInstance(mContext);
            WorkoutSamplesDatabaseManager samplesManager = WorkoutSamplesDatabaseManager.getInstance(mContext);

            String selection = WorkoutSummaries.LATITUDE + " IS NULL AND " + WorkoutSummaries.EXTREMA_TYPE + " != ?";
            String[] selectionArgs = {ExtremaType.AVG.name()};
            String[] columns = {WorkoutSummaries.WORKOUT_ID, WorkoutSummaries.SENSOR_TYPE, WorkoutSummaries.EXTREMA_TYPE, WorkoutSummaries.VALUE};

            try (Cursor extremaCursor = summariesDb.query(WorkoutSummaries.TABLE_EXTREMA_VALUES, columns, selection, selectionArgs, null, null, null)) {
                int workoutIdIdx = extremaCursor.getColumnIndex(WorkoutSummaries.WORKOUT_ID);
                int sensorTypeIdx = extremaCursor.getColumnIndex(WorkoutSummaries.SENSOR_TYPE);
                int extremaTypeIdx = extremaCursor.getColumnIndex(WorkoutSummaries.EXTREMA_TYPE);
                int valueIdx = extremaCursor.getColumnIndex(WorkoutSummaries.VALUE);

                while (extremaCursor.moveToNext()) {
                    long workoutId = extremaCursor.getLong(workoutIdIdx);
                    String sensorTypeName = extremaCursor.getString(sensorTypeIdx);
                    String extremaTypeName = extremaCursor.getString(extremaTypeIdx);
                    double value = extremaCursor.getDouble(valueIdx);

                    try {
                        SensorType sensorType = SensorType.valueOf(sensorTypeName);
                        ExtremaType extremaType = ExtremaType.valueOf(extremaTypeName);

                        // Use the shared logic in WorkoutSamplesDatabaseManager to find the location
                        // Pass the database currently being upgraded to avoid recursion
                        WorkoutSamplesDatabaseManager.LatLngValue legacyPos = samplesManager.getExtremaPosition(summariesDb, summariesManager, workoutId, sensorType, extremaType);

                        if (legacyPos != null && legacyPos.latLng != null) {
                            // Use the new surgical update method to populate the coordinates
                            summariesManager.updateExtremaValue(summariesDb, workoutId, sensorType, extremaType, value, legacyPos.latLng);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Could not migrate position for " + sensorTypeName + " " + extremaTypeName, e);
                    }
                }
            }
        }

        /**
         * Combined migration for polylines and numerical streams.
         * Only one pass through the summaries table is performed.
         */
        private void migrateExistingWorkouts(SQLiteDatabase db, boolean forceRecalc) {
            Log.i(TAG, "Starting combined migration (forceRecalc=" + forceRecalc + ")...");

            String selection = null;
            if (!forceRecalc) {
                selection = "(" + WorkoutSummaries.MAP_POLYLINE + " = '' OR " +
                        WorkoutSummaries.DISTANCE_STREAM + " = '' OR " +
                        WorkoutSummaries.ALTITUDE_STREAM + " = '')";
            }

            String[] projection = {WorkoutSummaries.C_ID, WorkoutSummaries.FILE_BASE_NAME};

            try (Cursor summaryCursor = db.query(WorkoutSummaries.TABLE, projection, selection, null, null, null, null)) {
                int idIdx = summaryCursor.getColumnIndex(WorkoutSummaries.C_ID);
                int fileIdx = summaryCursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME);

                while (summaryCursor.moveToNext()) {
                    long workoutId = summaryCursor.getLong(idIdx);
                    String baseFileName = summaryCursor.getString(fileIdx);
                    if (baseFileName != null) {
                        migrateOneWorkout(db, workoutId, baseFileName);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Migration failed", e);
            }
        }

        private void migrateOneWorkout(SQLiteDatabase db, long workoutId, String baseFileName) {
            List<LatLng> latLngs = new ArrayList<>();
            List<Double> altitudes = new ArrayList<>();
            List<Double> distances = new ArrayList<>();

            SQLiteDatabase samplesDb = WorkoutSamplesDatabaseManager.getInstance(mContext).getDatabase();
            String tableName = WorkoutSamplesDatabaseManager.getTableName(baseFileName);

            try (Cursor cursor = samplesDb.query(tableName, null, null, null, null, null, null)) {
                if (cursor != null) {
                    int latIdx = cursor.getColumnIndex("LATITUDE");
                    int lonIdx = cursor.getColumnIndex("LONGITUDE");
                    int altIdx = cursor.getColumnIndex("ALTITUDE");
                    int distIdx = cursor.getColumnIndex("DISTANCE_m");

                    while (cursor.move(WorkoutSummaries.ENCODING_STEP_SIZE)) {
                        if (latIdx != -1 && lonIdx != -1 && !cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                            latLngs.add(new LatLng(cursor.getDouble(latIdx), cursor.getDouble(lonIdx)));
                        }
                        if (altIdx != -1 && !cursor.isNull(altIdx)) {
                            altitudes.add(cursor.getDouble(altIdx));
                        }
                        if (distIdx != -1 && !cursor.isNull(distIdx)) {
                            distances.add(cursor.getDouble(distIdx));
                        }
                    }
                }
            } catch (Exception e) {
                // Table might not exist or columns missing, skip
                return;
            }

            ContentValues values = new ContentValues();
            if (!latLngs.isEmpty()) {
                values.put(WorkoutSummaries.MAP_POLYLINE, PolyUtil.encode(latLngs));
            }
            if (!altitudes.isEmpty()) {
                values.put(WorkoutSummaries.ALTITUDE_STREAM, NumericalEncodingUtils.INSTANCE.encodeDoubles(altitudes));
                values.put(WorkoutSummaries.DISTANCE_STREAM, NumericalEncodingUtils.INSTANCE.encodeDoubles(distances));
            }

            if (values.size() > 0) {
                db.update(WorkoutSummaries.TABLE, values, WorkoutSummaries.C_ID + "=?", new String[]{String.valueOf(workoutId)});
            }
        }
    }
}
