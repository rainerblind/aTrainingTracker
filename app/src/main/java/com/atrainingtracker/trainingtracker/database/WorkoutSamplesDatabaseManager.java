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
import com.atrainingtracker.database.BaseDatabaseManager;
import com.atrainingtracker.database.SqliteSchemaUtils;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager;
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager.DevicesDbHelper;
import com.atrainingtracker.banalservice.devices.DeviceType;
import com.atrainingtracker.banalservice.sensor.SensorType;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.domain.crop.CropWorkoutMath;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class WorkoutSamplesDatabaseManager extends BaseDatabaseManager<WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper> {
    private static final String TAG = WorkoutSamplesDatabaseManager.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(true);
    private static volatile WorkoutSamplesDatabaseManager cInstance;
    private final Context mContext;

    private WorkoutSamplesDatabaseManager(@NonNull Context context) {
        super(new WorkoutSamplesDbHelper(context));
        this.mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
    }

    @NonNull
    public static WorkoutSamplesDatabaseManager getInstance(@NonNull Context context) {
        if (cInstance == null) {
            synchronized (WorkoutSamplesDatabaseManager.class) {
                if (cInstance == null) {
                    cInstance = new WorkoutSamplesDatabaseManager(context);
                }
            }
        }
        return cInstance;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public static synchronized void resetForTesting(@Nullable WorkoutSamplesDatabaseManager newInstance) {
        if (cInstance != null) {
            try {
                cInstance.closeDatabase();
            } catch (Exception ignored) {
            }
        }
        cInstance = newInstance;
    }

    @Nullable
    public Double calcExtremaValue(WorkoutSummariesDatabaseManager workoutSummariesDatabaseManager, String baseFileName, @NonNull ExtremaType extremaType, @NonNull SensorType sensorType) {
        if (DEBUG)
            Log.i(TAG, "calcExtremaValue(" + baseFileName + ", " + extremaType.name() + ", " + sensorType.name() + ")");

        // first, a special case: when asked to calc the values for the pace,
        // we return the inverse of the corresponding extrema value of the speed.
        if (sensorType == SensorType.PACE_spm) {
            Double speed = calcExtremaValue(workoutSummariesDatabaseManager, baseFileName, extremaType, SensorType.SPEED_mps);
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

            Double distance = workoutSummariesDatabaseManager.getDouble(baseFileName, WorkoutSummaries.DISTANCE_TOTAL_m);
            Integer time = workoutSummariesDatabaseManager.getInt(baseFileName, WorkoutSummaries.TIME_ACTIVE_s);

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
        SQLiteDatabase db = getDatabase();

        // it might be possible that the corresponding sensor is not a column of the database, so we first check this
        if (existsColumnInTable(db, getTableName(baseFileName), sensorType.name())) {

            switch (extremaType) {
                case MAX:
                case AVG:
                case MIN:
                    String[] columns = new String[]{extremaType.name() + "(" + sensorType.name() + ")"};

                    try (Cursor cursor = db.query(getTableName(baseFileName),
                            columns,  // columns,
                            null, // selection, (here something like MAX(?))
                            null, // selectionArgs,
                            null, null, null)) {

                        if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                            extremaValue = cursor.getDouble(0);
                        }
                    }
                    break;

                case START:
                    try (Cursor cursor = db.query(getTableName(baseFileName),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null)) {

                        if (cursor != null) {
                            int colIdx = cursor.getColumnIndex(sensorType.name());
                            while (cursor.moveToNext() && extremaValue == null) {
                                if (dataValid(cursor, sensorType.name()) && colIdx != -1) {
                                    if (DEBUG) Log.i(TAG, "got start value");
                                    extremaValue = cursor.getDouble(colIdx);
                                }
                            }
                        }
                    }
                    break;

                case END:
                    try (Cursor cursor = db.query(getTableName(baseFileName),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null)) {

                        if (cursor != null && cursor.moveToLast()) {
                            int colIdx = cursor.getColumnIndex(sensorType.name());
                            do {
                                if (dataValid(cursor, sensorType.name()) && colIdx != -1) {
                                    if (DEBUG) Log.i(TAG, "got end location");
                                    extremaValue = cursor.getDouble(colIdx);
                                    break;
                                }
                            } while (cursor.moveToPrevious());
                        }
                    }
                    break;
            }
        }

        if (DEBUG) Log.i(TAG, "extremaValue: " + extremaValue);
        return extremaValue;
    }

    // since this method goes through all? samples, this might take long.
    public double calcAverageAroundLocation(WorkoutSummariesDatabaseManager workoutSummariesDatabaseManager, @NonNull LatLng center, double radius, @NonNull SensorType sensorType) {
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

        SQLiteDatabase summariesDb = workoutSummariesDatabaseManager.getDatabase();
        try (Cursor summariesCursor = summariesDb.query(WorkoutSummaries.TABLE,
                new String[]{WorkoutSummaries.FILE_BASE_NAME},
                null, null,
                null, null, null)) {

            if (summariesCursor != null) {
                while (summariesCursor.moveToNext()) {
                    String name = summariesCursor.getString(0);
                    if (DEBUG) Log.i(TAG, "querying table: " + name);

                    SQLiteDatabase samplesDb = getDatabase();
                    if (!existsTable(name) || !existsColumnInTable(samplesDb, getTableName(name), sensorType.name())) {
                        continue;
                    }

                    try (Cursor samplesCursor = samplesDb.query(getTableName(name), // Table
                            new String[]{SensorType.LATITUDE.name(), SensorType.LONGITUDE.name(), sensorType.name()}, // columns
                            SensorType.LATITUDE.name() + " > ? AND " +
                                    SensorType.LATITUDE.name() + " < ? AND " +
                                    SensorType.LONGITUDE.name() + " > ? AND " +
                                    SensorType.LONGITUDE.name() + " < ?", // selection
                            new String[]{Double.toString(p3.latitude), Double.toString(p1.latitude), Double.toString(p4.longitude), Double.toString(p2.longitude)},
                            null, null, null)) {

                        if (samplesCursor != null) {
                            if (DEBUG)
                                Log.i(TAG, "got cursor with " + samplesCursor.getCount() + " entries for " + name);

                            // cache the indexes
                            int latIndex = samplesCursor.getColumnIndex(SensorType.LATITUDE.name());
                            int lonIndex = samplesCursor.getColumnIndex(SensorType.LONGITUDE.name());
                            int sensorIndex = samplesCursor.getColumnIndex(sensorType.name());

                            while (samplesCursor.moveToNext()) {
                                if (dataValid(samplesCursor, sensorType.name())
                                        && dataValid(samplesCursor, SensorType.LATITUDE.name())
                                        && dataValid(samplesCursor, SensorType.LONGITUDE.name())
                                        && latIndex != -1 && lonIndex != -1 && sensorIndex != -1) {
                                    testLocation.setLatitude(samplesCursor.getDouble(latIndex));
                                    testLocation.setLongitude(samplesCursor.getDouble(lonIndex));

                                    // REQ-FIL-019.2: Exclude Null Island (0.0, 0.0) from location queries
                                    if (Math.abs(testLocation.getLatitude()) < 1e-6 && Math.abs(testLocation.getLongitude()) < 1e-6) {
                                        continue;
                                    }

                                    if (centerLocation.distanceTo(testLocation) <= radius) {
                                        valueSum += samplesCursor.getDouble(sensorIndex);
                                        counter++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

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
    public LatLngValue getExtremaPosition(WorkoutSummariesDatabaseManager workoutSummariesDatabaseManager, long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType) {
        return getExtremaPosition(workoutSummariesDatabaseManager.getDatabase(), workoutSummariesDatabaseManager, workoutId, sensorType, extremaType);
    }

    @Nullable
    public LatLngValue getExtremaPosition(SQLiteDatabase summariesDb, WorkoutSummariesDatabaseManager workoutSummariesDatabaseManager, long workoutId, @NonNull SensorType sensorType, @NonNull ExtremaType extremaType) {
        if (DEBUG)
            Log.i(TAG, "getExtremaPosition for " + extremaType.name() + " " + sensorType.name());

        // TODO: obviously, this does not make sense for AVG

        LatLngValue result = null;

        // WorkoutSummariesDbHelper summariesDbHelper = new WorkoutSummariesDbHelper(mContext);
        String baseFileName = workoutSummariesDatabaseManager.getBaseFileName(summariesDb, workoutId);

        // first, get the extrema value
        Double extremaValue = workoutSummariesDatabaseManager.getExtremaValue(summariesDb, workoutId, sensorType, extremaType);
        if (DEBUG) Log.i(TAG, "got " + extremaValue);

        // if there is an extrema value, we look for its location
        if (extremaValue != null) {

            SQLiteDatabase samplesDb = getDatabase();
            if (!existsTable(baseFileName) || !existsColumnInTable(samplesDb, getTableName(baseFileName), sensorType.name())) {
                return null;
            }

            String selection;
            String[] selectionArgs;
            String orderBy = null;

            // depending on the extremaType, there are two alternative ways to find the corresponding row
            switch (extremaType) {
                case MIN:
                case MAX:
                    if (DEBUG)
                        Log.i(TAG, "special case: MIN or MAX => find by 'sorting' and only picking the first row");
                    // in previous versions, we used the 'default' code for this but it failed for max of line_distance
                    // probably due to problems with converting and storing doubles?

                    orderBy = sensorType.name() + " " + ((extremaType == ExtremaType.MAX) ? "DESC" : "ASC");
                    selection = SensorType.LATITUDE.name() + " IS NOT NULL AND " + SensorType.LONGITUDE.name() + " IS NOT NULL";
                    selectionArgs = null;
                    break;
                default:  // neither MIN nor MAX
                    if (DEBUG)
                        Log.i(TAG, "default case: search for the extrema value which should be already within the summariesDb");

                    selection = sensorType.name() + "=?";
                    selectionArgs = new String[]{extremaValue.toString()};
                    break;
            } // end of switch

            try (Cursor cursor = samplesDb.query(WorkoutSamplesDatabaseManager.getTableName(baseFileName), // table
                    null, // columns
                    selection, // selection
                    selectionArgs, // selectionArgs
                    null, // groupBy
                    null, // having
                    orderBy, // orderBy
                    "1")) { // limit

                if (cursor != null && cursor.moveToFirst()) {
                    int latIdx = cursor.getColumnIndex(SensorType.LATITUDE.name());
                    int lonIdx = cursor.getColumnIndex(SensorType.LONGITUDE.name());
                    int sensorIdx = cursor.getColumnIndex(sensorType.name());

                    if (latIdx != -1 && lonIdx != -1 && sensorIdx != -1
                            && !cursor.isNull(latIdx)
                            && !cursor.isNull(lonIdx)) {
                        if (DEBUG)
                            Log.i(TAG, "got a valid location for " + extremaType.name() + " of " + sensorType.name());
                        result = new LatLngValue(new LatLng(cursor.getDouble(latIdx),
                                cursor.getDouble(lonIdx)),
                                cursor.getDouble(sensorIdx));
                    } else {
                        if (DEBUG)
                            Log.d(TAG, "did not get a valid location for " + extremaType.name() + " of " + sensorType.name());
                    }
                }
            }

        } else {
            if (DEBUG)
                Log.i(TAG, "there was no valid extrema value for " + extremaType.name() + " of " + sensorType.name());
        }

        // finally, return the result
        return result;
    }

    public void createNewTable(String workoutName, @NonNull List<SensorType> sensorTypes) {
        if (DEBUG) Log.d(TAG, "createNewTable: " + WorkoutSamplesDbHelper.DB_NAME);

        SQLiteDatabase db = getDatabase();
        String table = getTableName(workoutName);

        // first of all, delete an existing db.
        // this is necessary when the user starts tracking twice within one minute (might be due to failures) (really necessary???)
        db.execSQL("drop table if exists " + table);

        String execSQL = "create table " + table + "(" + makeColumns(sensorTypes) + ")";
        if (DEBUG) Log.d(TAG, "execSQL: " + execSQL);

        db.execSQL(execSQL);
    }

    public void deleteWorkout(String baseFileName) {
        getDatabase().execSQL("drop table if exists " + getTableName(baseFileName));
    }

    /**
     * Prunes unselected sample rows and updates cumulative and spatial fields
     * within a native SQLite transaction (ATT-3274 / REQ-UI-368).
     *
     * @param fileBaseName The workout table name.
     * @param startRowId The _id of the first retained sample row.
     * @param endRowId The _id of the last retained sample row.
     * @param expectedRetainedRows The expected number of rows after pruning.
     * @param lapOffset The lap number offset to subtract (original start lap - 1).
     * @param newStartLatLng The coordinate at startRowId for Haversine recalculation (null if indoor).
     * @return The number of rows deleted.
     */
    public int pruneAndShiftSamples(@NonNull String fileBaseName, long startRowId, long endRowId,
                                    int expectedRetainedRows, int lapOffset, @Nullable LatLng newStartLatLng) {
        SQLiteDatabase db = getDatabase();
        String table = getTableName(fileBaseName);
        db.beginTransaction();
        int deletedRows = 0;
        try {
            // 1. Delete rows outside [startRowId, endRowId]
            deletedRows = db.delete(table, "_id < ? OR _id > ?", new String[]{String.valueOf(startRowId), String.valueOf(endRowId)});

            // 2. Query baseline cumulative values at startRowId
            double baseDist = 0.0;
            long baseTimeTotal = 0L;
            long baseTimeActive = 0L;
            double baseAscent = 0.0;
            double baseDescent = 0.0;
            long baseCalories = 0L;

            boolean hasDist = existsColumnInTable(db, table, SensorType.DISTANCE_m.name());
            boolean hasTimeTotal = existsColumnInTable(db, table, SensorType.TIME_TOTAL.name());
            boolean hasTimeActive = existsColumnInTable(db, table, SensorType.TIME_ACTIVE.name());
            boolean hasAscent = existsColumnInTable(db, table, SensorType.ASCENT.name());
            boolean hasDescent = existsColumnInTable(db, table, SensorType.DESCENT.name());
            boolean hasCalories = existsColumnInTable(db, table, SensorType.CALORIES.name());
            boolean hasLineDist = existsColumnInTable(db, table, SensorType.LINE_DISTANCE_m.name());
            boolean hasLapNr = existsColumnInTable(db, table, SensorType.LAP_NR.name());
            boolean hasLat = existsColumnInTable(db, table, SensorType.LATITUDE.name());
            boolean hasLon = existsColumnInTable(db, table, SensorType.LONGITUDE.name());

            try (Cursor baselineCursor = db.query(table, null, "_id = ?", new String[]{String.valueOf(startRowId)}, null, null, null)) {
                if (baselineCursor != null && baselineCursor.moveToFirst()) {
                    if (hasDist) baseDist = baselineCursor.getDouble(baselineCursor.getColumnIndexOrThrow(SensorType.DISTANCE_m.name()));
                    if (hasTimeTotal) baseTimeTotal = baselineCursor.getLong(baselineCursor.getColumnIndexOrThrow(SensorType.TIME_TOTAL.name()));
                    if (hasTimeActive) baseTimeActive = baselineCursor.getLong(baselineCursor.getColumnIndexOrThrow(SensorType.TIME_ACTIVE.name()));
                    if (hasAscent) baseAscent = baselineCursor.getDouble(baselineCursor.getColumnIndexOrThrow(SensorType.ASCENT.name()));
                    if (hasDescent) baseDescent = baselineCursor.getDouble(baselineCursor.getColumnIndexOrThrow(SensorType.DESCENT.name()));
                    if (hasCalories) baseCalories = baselineCursor.getLong(baselineCursor.getColumnIndexOrThrow(SensorType.CALORIES.name()));
                }
            }

            // 3. Vectorized bulk update for retained rows:
            // Shift cumulative fields (DISTANCE_m, TIME_TOTAL, TIME_ACTIVE, ASCENT, DESCENT, CALORIES, LAP_NR) in a single atomic SQL statement
            List<String> setClauses = new ArrayList<>();
            if (hasDist) setClauses.add(SensorType.DISTANCE_m.name() + " = MAX(0.0, " + SensorType.DISTANCE_m.name() + " - " + baseDist + ")");
            if (hasTimeTotal) setClauses.add(SensorType.TIME_TOTAL.name() + " = MAX(0, " + SensorType.TIME_TOTAL.name() + " - " + baseTimeTotal + ")");
            if (hasTimeActive) setClauses.add(SensorType.TIME_ACTIVE.name() + " = MAX(0, " + SensorType.TIME_ACTIVE.name() + " - " + baseTimeActive + ")");
            if (hasAscent) setClauses.add(SensorType.ASCENT.name() + " = MAX(0.0, " + SensorType.ASCENT.name() + " - " + baseAscent + ")");
            if (hasDescent) setClauses.add(SensorType.DESCENT.name() + " = MAX(0.0, " + SensorType.DESCENT.name() + " - " + baseDescent + ")");
            if (hasCalories) setClauses.add(SensorType.CALORIES.name() + " = MAX(0, " + SensorType.CALORIES.name() + " - " + baseCalories + ")");
            if (hasLapNr && lapOffset > 0) setClauses.add(SensorType.LAP_NR.name() + " = MAX(1, " + SensorType.LAP_NR.name() + " - " + lapOffset + ")");

            if (!setClauses.isEmpty()) {
                String bulkUpdateSql = "UPDATE " + table + " SET " + TextUtils.join(", ", setClauses)
                        + " WHERE _id >= ? AND _id <= ?";
                db.execSQL(bulkUpdateSql, new Object[]{startRowId, endRowId});
            }

            // Recalculate LINE_DISTANCE_m: 0.0 at startRowId, and Haversine distance from newStartLatLng if GPS is available
            if (hasLineDist) {
                if (newStartLatLng != null && hasLat && hasLon) {
                    db.execSQL("UPDATE " + table + " SET " + SensorType.LINE_DISTANCE_m.name() + " = 0.0 WHERE _id = ?",
                            new Object[]{startRowId});
                    SQLiteStatement stmt = db.compileStatement(
                            "UPDATE " + table + " SET " + SensorType.LINE_DISTANCE_m.name() + " = ? WHERE _id = ?");
                    try (Cursor cursor = db.query(table, new String[]{"_id", SensorType.LATITUDE.name(), SensorType.LONGITUDE.name()},
                            "_id > ? AND _id <= ?", new String[]{String.valueOf(startRowId), String.valueOf(endRowId)},
                            null, null, "_id ASC")) {
                        if (cursor != null && cursor.moveToFirst()) {
                            int idIdx = cursor.getColumnIndexOrThrow("_id");
                            int latIdx = cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name());
                            int lonIdx = cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name());
                            do {
                                if (!cursor.isNull(latIdx) && !cursor.isNull(lonIdx)) {
                                    double rowLat = cursor.getDouble(latIdx);
                                    double rowLon = cursor.getDouble(lonIdx);
                                    double lineDist = CropWorkoutMath.INSTANCE.haversineDistanceMeters(newStartLatLng, new LatLng(rowLat, rowLon));
                                    stmt.bindDouble(1, lineDist);
                                    stmt.bindLong(2, cursor.getLong(idIdx));
                                    stmt.executeUpdateDelete();
                                }
                            } while (cursor.moveToNext());
                        }
                    } finally {
                        stmt.close();
                    }
                } else {
                    db.execSQL("UPDATE " + table + " SET " + SensorType.LINE_DISTANCE_m.name() + " = 0.0 WHERE _id >= ? AND _id <= ?",
                            new Object[]{startRowId, endRowId});
                }
            }

            // 4. Verify post-op checksum
            int remainingRows = 0;
            try (Cursor countCursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
                if (countCursor != null && countCursor.moveToFirst()) {
                    remainingRows = countCursor.getInt(0);
                }
            }

            if (remainingRows != expectedRetainedRows || remainingRows < 2) {
                throw new IllegalStateException("Crop checksum failure: expected " + expectedRetainedRows
                        + " remaining rows, but found " + remainingRows);
            }

            db.setTransactionSuccessful();
        } catch (SQLiteFullException e) {
            Log.e(TAG, "Disk full during pruneAndShiftSamples, rolling back cleanly: " + e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Transaction aborted during pruneAndShiftSamples, rolling back cleanly: " + e.getMessage(), e);
            throw e;
        } finally {
            db.endTransaction();
        }

        return deletedRows;
    }

    /**
     * Atomically increments LAP_NR by 1 for all samples at and after the specified splitRowId (REQ-UI-449.3 / ATT-907).
     *
     * @param fileBaseName The workout table base name.
     * @param splitRowId The _id threshold from which samples have their lap number incremented.
     * @return The number of rows affected.
     */
    public int incrementLapNumbersFromSample(@NonNull String fileBaseName, long splitRowId) {
        SQLiteDatabase db = getDatabase();
        String table = getTableName(fileBaseName);
        if (!existsTable(fileBaseName)) {
            Log.w(TAG, "Table does not exist for " + fileBaseName);
            return 0;
        }

        db.beginTransaction();
        try {
            boolean hasLapNr = existsColumnInTable(db, table, SensorType.LAP_NR.name());
            if (!hasLapNr) {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + SensorType.LAP_NR.name() + " INTEGER DEFAULT 1");
            }

            String sql = "UPDATE " + table + " SET " + SensorType.LAP_NR.name() + " = COALESCE(" + SensorType.LAP_NR.name() + ", 1) + 1 WHERE _id >= ?";
            SQLiteStatement stmt = db.compileStatement(sql);
            stmt.bindLong(1, splitRowId);
            int rowsAffected = stmt.executeUpdateDelete();

            db.setTransactionSuccessful();
            return rowsAffected;
        } catch (Exception e) {
            Log.e(TAG, "Error incrementing lap numbers from sample row " + splitRowId, e);
            throw e;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Atomically decrements LAP_NR by 1 for all samples with LAP_NR > boundaryLapNr (REQ-UI-454.3 / ATT-5482).
     * This effectively merges Lap (boundaryLapNr + 1) into Lap (boundaryLapNr) and shifts all subsequent laps down by 1.
     *
     * @param fileBaseName The workout table base name.
     * @param boundaryLapNr The boundary lap number above which all sample lap numbers are decremented.
     * @return The number of rows affected.
     */
    public int decrementLapNumbersAbove(@NonNull String fileBaseName, long boundaryLapNr) {
        SQLiteDatabase db = getDatabase();
        String table = getTableName(fileBaseName);
        if (!existsTable(fileBaseName)) {
            Log.w(TAG, "Table does not exist for " + fileBaseName);
            return 0;
        }

        db.beginTransaction();
        try {
            boolean hasLapNr = existsColumnInTable(db, table, SensorType.LAP_NR.name());
            if (!hasLapNr) {
                // If table has no LAP_NR column, all samples are implicitly Lap 1; nothing to decrement above boundary
                db.setTransactionSuccessful();
                return 0;
            }

            String sql = "UPDATE " + table + " SET " + SensorType.LAP_NR.name() + " = " + SensorType.LAP_NR.name() + " - 1 WHERE " + SensorType.LAP_NR.name() + " > ?";
            SQLiteStatement stmt = db.compileStatement(sql);
            stmt.bindLong(1, boundaryLapNr);
            int rowsAffected = stmt.executeUpdateDelete();

            db.setTransactionSuccessful();
            return rowsAffected;
        } catch (Exception e) {
            Log.e(TAG, "Error decrementing lap numbers above boundary lap " + boundaryLapNr, e);
            throw e;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Atomically updates LAP_NR to the specified newLapNr for all samples within [startRowId, endRowId] (inclusive) (REQ-UI-450.4 / ATT-908).
     *
     * @param fileBaseName The workout table base name.
     * @param startRowId The starting _id threshold (inclusive).
     * @param endRowId The ending _id threshold (inclusive).
     * @param newLapNr The new lap number to assign.
     * @return The number of rows affected.
     */
    public int updateLapNumberForSampleRange(@NonNull String fileBaseName, long startRowId, long endRowId, long newLapNr) {
        SQLiteDatabase db = getDatabase();
        String table = getTableName(fileBaseName);
        if (!existsTable(fileBaseName)) {
            Log.w(TAG, "Table does not exist for " + fileBaseName);
            return 0;
        }

        db.beginTransaction();
        try {
            boolean hasLapNr = existsColumnInTable(db, table, SensorType.LAP_NR.name());
            if (!hasLapNr) {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + SensorType.LAP_NR.name() + " INTEGER DEFAULT 1");
            }

            String sql = "UPDATE " + table + " SET " + SensorType.LAP_NR.name() + " = ? WHERE _id >= ? AND _id <= ?";
            SQLiteStatement stmt = db.compileStatement(sql);
            stmt.bindLong(1, newLapNr);
            stmt.bindLong(2, Math.min(startRowId, endRowId));
            stmt.bindLong(3, Math.max(startRowId, endRowId));
            int rowsAffected = stmt.executeUpdateDelete();

            db.setTransactionSuccessful();
            return rowsAffected;
        } catch (Exception e) {
            Log.e(TAG, "Error updating lap numbers for sample range [" + startRowId + ", " + endRowId + "] to " + newLapNr, e);
            throw e;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Reclaims SQLite storage via incremental vacuum if deleted rows exceed 20% of total rows
     * and the database is configured for INCREMENTAL auto-vacuum (mode 2).
     */
    public void vacuumIfFragmented(int deletedRows, int totalRows) {
        if (totalRows > 0 && ((double) deletedRows / (double) totalRows) > 0.20) {
            SQLiteDatabase db = getDatabase();
            try (Cursor cursor = db.rawQuery("PRAGMA auto_vacuum;", null)) {
                int autoVacuumMode = 0;
                if (cursor != null && cursor.moveToFirst()) {
                    autoVacuumMode = cursor.getInt(0);
                }
                // Mode 2 is INCREMENTAL auto-vacuum in SQLite
                if (autoVacuumMode == 2) {
                    db.execSQL("PRAGMA incremental_vacuum;");
                    if (DEBUG) Log.d(TAG, "Incremental vacuum executed successfully.");
                } else {
                    if (DEBUG) Log.d(TAG, "Incremental vacuum skipped: auto_vacuum mode is " + autoVacuumMode + " (requires mode 2 / INCREMENTAL).");
                }
            } catch (Exception e) {
                Log.w(TAG, "Incremental vacuum check/execution failed: " + e.getMessage());
            }
        }
    }


    public boolean existsTable(String baseFileName) {
        if (baseFileName == null || baseFileName.trim().isEmpty() || "null".equalsIgnoreCase(baseFileName.trim())) {
            return false;
        }
        return existsColumnInTable(getDatabase(), getTableName(baseFileName), WorkoutSamplesDbHelper.TIME);
    }

    /**
     * REQ-TRK-047.2, REQ-TRK-057.3 & REQ-FIL-018: Applies retroactive barometric altitude correction
     * to the workout samples table without epoch lower bound.
     *
     * @see #applyAltitudeDriftCorrection(String, double, long, long, long)
     */
    public int applyAltitudeDriftCorrection(@NonNull String samplesTable, double deltaOffset, long t0Ms, long t1Ms) {
        return applyAltitudeDriftCorrection(samplesTable, deltaOffset, t0Ms, t1Ms, -1L);
    }

    /**
     * REQ-FIL-018: Applies retroactive barometric altitude correction strictly to barometric pressure
     * columns and canonical ALTITUDE rows sourced from barometric pressure.
     * Case A (t0Ms &lt;= 0) is bounded to [floor(sessionEpochStartTimeMs/1000), t1Ms/1000.0] so
     * already-calibrated pre-crash samples and post-calibration samples are never shifted twice.
     *
     * @param samplesTable            Table name of the active workout samples.
     * @param deltaOffset             Altitude shift delta in meters.
     * @param t0Ms                    Previous calibration timestamp in milliseconds (-1 if uncalibrated in epoch).
     * @param t1Ms                    Current calibration timestamp in milliseconds.
     * @param sessionEpochStartTimeMs Current process/session epoch start timestamp in milliseconds (&lt;= 0 if unbounded).
     * @return Number of canonical ALTITUDE rows shifted (0 if canonical ALTITUDE was not barometric).
     */
    public int applyAltitudeDriftCorrection(
            @NonNull String samplesTable,
            double deltaOffset,
            long t0Ms,
            long t1Ms,
            long sessionEpochStartTimeMs) {
        if (Math.abs(deltaOffset) < 0.001 || samplesTable.trim().isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = getDatabase();

        Set<String> allowedBaroDeviceNames = resolveBarometricDeviceNames();
        String altPrefix = SensorType.ALTITUDE.name() + " (";
        boolean hasCanonicalAltitude = false;
        List<String> baroColumns = new ArrayList<>();

        try (Cursor cursor = db.rawQuery("SELECT * FROM " + samplesTable + " LIMIT 0", null)) {
            if (cursor != null) {
                String[] columnNames = cursor.getColumnNames();
                if (columnNames != null) {
                    for (String col : columnNames) {
                        if (SensorType.ALTITUDE.name().equals(col)) {
                            hasCanonicalAltitude = true;
                        } else if (col.startsWith(altPrefix) && col.endsWith(")")) {
                            String deviceName = col.substring(altPrefix.length(), col.length() - 1).trim();
                            if (allowedBaroDeviceNames.contains(deviceName)) {
                                baroColumns.add(col);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error inspecting columns in " + samplesTable + ": " + e.getMessage());
        }

        // REQ-FIL-018.1: Without a barometric column, never mutate DEM/GPS/Fused or canonical ALTITUDE
        if (baroColumns.isEmpty()) {
            return 0;
        }

        String canonicalMatchPredicate = buildCanonicalBarometricMatchPredicate(baroColumns);
        String timeExpr = "CAST(strftime('%s', " + WorkoutSamplesDbHelper.TIME + ") AS REAL)";
        int canonicalShifted = 0;

        db.beginTransaction();
        try {
            // Evaluate canonical ALTITUDE BEFORE mutating baroColumns so equality reflects pre-shift values
            if (hasCanonicalAltitude) {
                canonicalShifted = shiftAltitudeColumn(
                        db,
                        samplesTable,
                        SensorType.ALTITUDE.name(),
                        deltaOffset,
                        t0Ms,
                        t1Ms,
                        sessionEpochStartTimeMs,
                        timeExpr,
                        canonicalMatchPredicate);
            }
            for (String baroCol : baroColumns) {
                shiftAltitudeColumn(
                        db,
                        samplesTable,
                        baroCol,
                        deltaOffset,
                        t0Ms,
                        t1Ms,
                        sessionEpochStartTimeMs,
                        timeExpr,
                        null);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return canonicalShifted;
    }

    private int shiftAltitudeColumn(
            SQLiteDatabase db,
            String samplesTable,
            String columnName,
            double deltaOffset,
            long t0Ms,
            long t1Ms,
            long sessionEpochStartTimeMs,
            String timeExpr,
            @Nullable String extraPredicate) {
        String quotedCol = "\"" + columnName.replace("\"", "\"\"") + "\"";
        String extraWhere = extraPredicate != null ? " AND " + extraPredicate : "";
        String operator = deltaOffset >= 0 ? " + " : " - ";
        double absDelta = Math.abs(deltaOffset);

        if (t0Ms <= 0 || t1Ms <= t0Ms) {
            // Case A: Uncalibrated epoch -> uniform shift bounded to [sessionEpochStartTimeMs, t1Ms]
            StringBuilder timeBound = new StringBuilder();
            if (sessionEpochStartTimeMs > 0) {
                long epochStartSec = sessionEpochStartTimeMs / 1000L;
                timeBound.append(" AND ").append(timeExpr).append(" >= ").append(epochStartSec);
            }
            if (t1Ms > 0) {
                double t1Sec = t1Ms / 1000.0;
                timeBound.append(" AND ").append(timeExpr).append(" <= ").append(t1Sec);
            }
            return executeUpdateSql(db, "UPDATE " + samplesTable
                    + " SET " + quotedCol + " = " + quotedCol + operator + absDelta
                    + " WHERE " + quotedCol + " IS NOT NULL" + extraWhere + timeBound);
        } else {
            // Case B: Piecewise-linear drift interpolation between t0 and t1
            double t0Sec = t0Ms / 1000.0;
            double t1Sec = t1Ms / 1000.0;
            double duration = t1Sec - t0Sec;
            String epochBound = sessionEpochStartTimeMs > 0
                    ? " AND " + timeExpr + " >= " + (sessionEpochStartTimeMs / 1000L)
                    : "";

            int updated = executeUpdateSql(db, "UPDATE " + samplesTable
                    + " SET " + quotedCol + " = " + quotedCol
                    + " + (" + deltaOffset + " * (" + timeExpr + " - " + t0Sec + ") / " + duration + ")"
                    + " WHERE " + quotedCol + " IS NOT NULL"
                    + extraWhere
                    + epochBound
                    + " AND " + timeExpr + " >= " + t0Sec
                    + " AND " + timeExpr + " <= " + t1Sec);

            updated += executeUpdateSql(db, "UPDATE " + samplesTable
                    + " SET " + quotedCol + " = " + quotedCol + operator + absDelta
                    + " WHERE " + quotedCol + " IS NOT NULL"
                    + extraWhere
                    + epochBound
                    + " AND " + timeExpr + " > " + t1Sec);
            return updated;
        }
    }

    private int executeUpdateSql(SQLiteDatabase db, String sql) {
        try (SQLiteStatement stmt = db.compileStatement(sql)) {
            return stmt.executeUpdateDelete();
        }
    }

    private String buildCanonicalBarometricMatchPredicate(List<String> baroColumns) {
        String canonicalCol = "\"" + SensorType.ALTITUDE.name() + "\"";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < baroColumns.size(); i++) {
            if (i > 0) sb.append(" OR ");
            String quotedBaro = "\"" + baroColumns.get(i).replace("\"", "\"\"") + "\"";
            sb.append("(").append(quotedBaro).append(" IS NOT NULL AND ABS(")
                    .append(canonicalCol).append(" - ").append(quotedBaro).append(") < 0.01)");
        }
        sb.append(")");
        return sb.toString();
    }

    private Set<String> resolveBarometricDeviceNames() {
        Set<String> names = new HashSet<>(Arrays.asList(
                // 9-locale defaults for R.string.devices_altitude_from_pressure + ATT-5073 legacy names
                "Altitude from Pressure", "Höhe aus Luftdruck", "Altitud por presión",
                "Altitude par pression", "Altitudine da pressione", "気圧からの高度",
                "Hoogte uit luchtdruk", "Wysokość z ciśnienia", "Altitude por pressão",
                "Barometer",
                "Air Pressure", "Luftdruck", "Presión Aire", "Pression Air",
                "Pressione Aria", "気圧", "Luchtdruk", "Ciśnienie pow.", "Pressão do Ar"
        ));
        try {
            String localized = mContext.getString(R.string.devices_altitude_from_pressure);
            if (!localized.trim().isEmpty()) {
                names.add(localized.trim());
            }
        } catch (Throwable ignored) {
        }
        try {
            SQLiteDatabase devicesDb = DevicesDatabaseManager.getInstance(mContext).getDatabase();
            try (Cursor cursor = devicesDb.query(
                    DevicesDbHelper.DEVICES,
                    new String[]{DevicesDbHelper.NAME},
                    DevicesDbHelper.DEVICE_TYPE + "=?",
                    new String[]{DeviceType.ALTITUDE_FROM_PRESSURE.name()},
                    null, null, null)) {
                if (cursor != null) {
                    int nameIdx = cursor.getColumnIndex(DevicesDbHelper.NAME);
                    while (nameIdx >= 0 && cursor.moveToNext()) {
                        String dbName = cursor.getString(nameIdx);
                        if (dbName != null && !dbName.trim().isEmpty()) {
                            names.add(dbName.trim());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    /**
     * Checks whether the workout samples table has missing or unpopulated heart rate samples (ATT-7918, REQ-SET-105).
     */
    public boolean hasMissingHeartRateSamples(@Nullable String baseFileName) {
        if (baseFileName == null || !existsTable(baseFileName)) {
            return true;
        }
        SQLiteDatabase db = getDatabase();
        String table = getTableName(baseFileName);
        if (!existsColumnInTable(db, table, SensorType.HR.name())) {
            return true;
        }
        try (Cursor cursor = db.rawQuery(
                "SELECT COUNT(*), COUNT(CASE WHEN " + SensorType.HR.name() + " IS NOT NULL AND " + SensorType.HR.name() + " > 0 THEN 1 END) FROM " + table, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int totalCount = cursor.getInt(0);
                int validHrCount = cursor.getInt(1);
                return totalCount == 0 || validHrCount < totalCount;
            }
        } catch (Exception e) {
            if (DEBUG) Log.w(TAG, "Error checking missing HR samples: " + e.getMessage(), e);
            return true;
        }
        return false;
    }

    /**
     * Patches missing heart rate samples in the workout samples table using offline reconstructed HR data (ATT-7918, REQ-SET-105.3).
     * Preserves all existing valid HR samples, GPS, speed, cadence, and power data.
     *
     * @param baseFileName The workout file base name.
     * @param startTimeMs The workout start epoch ms.
     * @param hrSamples List of (timestampMs, hrBpm) pairs.
     * @return Number of patched sample rows.
     */
    public int patchMissingHeartRateSamples(@NonNull String baseFileName, long startTimeMs, @NonNull List<Pair<Long, Integer>> hrSamples) {
        if (baseFileName == null || !existsTable(baseFileName) || hrSamples.isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = getDatabase();
        String table = getTableName(baseFileName);

        // Ensure HR column exists
        if (!existsColumnInTable(db, table, SensorType.HR.name())) {
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + SensorType.HR.name() + " int");
            } catch (Exception e) {
                Log.w(TAG, "Failed adding HR column to " + table + ": " + e.getMessage());
            }
        }

        java.util.TreeMap<Long, Integer> hrMap = new java.util.TreeMap<>();
        for (Pair<Long, Integer> sample : hrSamples) {
            if (sample != null && sample.first != null && sample.second != null && sample.second > 0) {
                hrMap.put(sample.first, sample.second);
            }
        }
        if (hrMap.isEmpty()) {
            return 0;
        }

        int patchedCount = 0;
        db.beginTransaction();
        try {
            List<Pair<Long, Long>> missingRows = new java.util.ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT " + WorkoutSamplesDbHelper.C_ID + ", " + WorkoutSamplesDbHelper.TIME + ", CAST(strftime('%s', " + WorkoutSamplesDbHelper.TIME + ") AS INTEGER) FROM " + table + " WHERE " + SensorType.HR.name() + " IS NULL OR " + SensorType.HR.name() + " <= 0", null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long rowId = cursor.getLong(0);
                        String timeStr = cursor.getString(1);
                        long timeSec = cursor.getLong(2);
                        long tsMs = 0L;
                        if (timeSec > 0) {
                            tsMs = timeSec * 1000L;
                        } else if (timeStr != null) {
                            tsMs = parseTimestampMs(timeStr);
                        }
                        missingRows.add(new Pair<>(rowId, tsMs));
                    }
                }
            }

            if (!missingRows.isEmpty()) {
                SQLiteStatement stmt = db.compileStatement("UPDATE " + table + " SET " + SensorType.HR.name() + " = ? WHERE " + WorkoutSamplesDbHelper.C_ID + " = ?");
                for (Pair<Long, Long> row : missingRows) {
                    long rowId = row.first;
                    long tsMs = row.second > 0 ? row.second : startTimeMs;
                    java.util.Map.Entry<Long, Integer> floor = hrMap.floorEntry(tsMs);
                    java.util.Map.Entry<Long, Integer> ceiling = hrMap.ceilingEntry(tsMs);
                    Integer hr = null;
                    if (floor != null && ceiling != null) {
                        hr = Math.abs(tsMs - floor.getKey()) <= Math.abs(tsMs - ceiling.getKey()) ? floor.getValue() : ceiling.getValue();
                    } else if (floor != null) {
                        hr = floor.getValue();
                    } else if (ceiling != null) {
                        hr = ceiling.getValue();
                    }
                    if (hr != null && hr > 0) {
                        stmt.bindLong(1, hr);
                        stmt.bindLong(2, rowId);
                        stmt.executeUpdateDelete();
                        patchedCount++;
                    }
                }
            } else {
                int totalRows = 0;
                try (Cursor countCursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
                    if (countCursor != null && countCursor.moveToFirst()) {
                        totalRows = countCursor.getInt(0);
                    }
                }
                if (totalRows == 0) {
                    SQLiteStatement insertStmt = db.compileStatement("INSERT INTO " + table + " (" + WorkoutSamplesDbHelper.TIME + ", " + SensorType.HR.name() + ") VALUES (datetime(?, 'unixepoch'), ?)");
                    for (java.util.Map.Entry<Long, Integer> entry : hrMap.entrySet()) {
                        long sec = entry.getKey() / 1000L;
                        insertStmt.bindLong(1, sec);
                        insertStmt.bindLong(2, entry.getValue());
                        insertStmt.executeInsert();
                        patchedCount++;
                    }
                }
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return patchedCount;
    }

    /**
     * Valid positive heart-rate summary statistics computed directly from {@code WorkoutSamples.db}
     * ({@code ATT-7924}, {@code REQ-SET-109.2}).
     */
    public static class HrStats {
        public final double min;
        public final double max;
        public final double avg;
        public final int count;

        public HrStats(double min, double max, double avg, int count) {
            this.min = min;
            this.max = max;
            this.avg = avg;
            this.count = count;
        }
    }

    /**
     * Computes MIN, MAX, AVG, and sample count over valid positive {@code HR} rows in {@code WorkoutSamples.db}
     * ({@code ATT-7924}, {@code REQ-SET-109.2}). Returns {@code null} if no positive HR samples exist.
     */
    @Nullable
    public HrStats calcHeartRateStats(@Nullable String baseFileName) {
        if (baseFileName == null || !existsTable(baseFileName)) {
            return null;
        }
        SQLiteDatabase db = getDatabase();
        String table = getTableName(baseFileName);
        if (!existsColumnInTable(db, table, SensorType.HR.name())) {
            return null;
        }
        String hrCol = SensorType.HR.name();
        String sql = "SELECT MIN(" + hrCol + "), MAX(" + hrCol + "), AVG(" + hrCol + "), COUNT(" + hrCol + ") FROM " + table
                + " WHERE " + hrCol + " IS NOT NULL AND " + hrCol + " > 0";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int count = cursor.getInt(3);
                if (count > 0 && !cursor.isNull(0) && !cursor.isNull(1) && !cursor.isNull(2)) {
                    return new HrStats(cursor.getDouble(0), cursor.getDouble(1), cursor.getDouble(2), count);
                }
            }
        } catch (Exception e) {
            if (DEBUG) Log.w(TAG, "Error calculating HR stats for " + baseFileName + ": " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Backfills swim kinematics (cumulative DISTANCE_m, interval SPEED_mps, LAP_NR)
     * using detected turn timestamps (REQ-SET-111.2, ATT-8001).
     */
    public void patchSwimKinematics(
            @NonNull String baseFileName,
            long startTimeMs,
            long durationMs,
            @NonNull java.util.List<Long> turnTimestampsMs,
            double poolLengthM
    ) {
        java.util.List<SwimInterval> intervals = new java.util.ArrayList<>();
        java.util.List<Long> sortedTurns = new java.util.ArrayList<>();
        for (Long t : turnTimestampsMs) {
            if (t != null && t > startTimeMs) {
                sortedTurns.add(t);
            }
        }
        java.util.Collections.sort(sortedTurns);

        if (sortedTurns.isEmpty()) {
            long endMs = startTimeMs + Math.max(1000L, durationMs);
            intervals.add(new SwimInterval(startTimeMs, endMs, poolLengthM));
        } else {
            intervals.add(new SwimInterval(startTimeMs, sortedTurns.get(0), poolLengthM));
            for (int i = 0; i < sortedTurns.size() - 1; i++) {
                intervals.add(new SwimInterval(sortedTurns.get(i), sortedTurns.get(i + 1), poolLengthM));
            }
            long lastTurn = sortedTurns.get(sortedTurns.size() - 1);
            long endMs = Math.max(lastTurn + 1000L, startTimeMs + durationMs);
            intervals.add(new SwimInterval(lastTurn, endMs, poolLengthM));
        }

        applySwimKinematicsIntervals(baseFileName, startTimeMs, durationMs, intervals, poolLengthM);
    }

    /**
     * Backfills uniform swim kinematics across a specified number of lengths (REQ-SET-111.3, ATT-8001).
     */
    public void patchSwimKinematicsUniform(
            @NonNull String baseFileName,
            long startTimeMs,
            long durationMs,
            int lengthCount,
            double poolLengthM
    ) {
        int count = Math.max(1, lengthCount);
        long totalDur = Math.max(1000L, durationMs);
        long intervalDur = Math.max(500L, totalDur / count);

        java.util.List<SwimInterval> intervals = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            long s = startTimeMs + i * intervalDur;
            long e = (i == count - 1) ? (startTimeMs + totalDur) : (startTimeMs + (i + 1) * intervalDur);
            intervals.add(new SwimInterval(s, Math.max(s + 500L, e), poolLengthM));
        }

        applySwimKinematicsIntervals(baseFileName, startTimeMs, durationMs, intervals, poolLengthM);
    }

    private static class SwimInterval {
        final long startMs;
        final long endMs;
        final double speedMps;

        SwimInterval(long startMs, long endMs, double poolLengthM) {
            this.startMs = startMs;
            this.endMs = Math.max(startMs + 500L, endMs);
            double durSec = Math.max(0.5, (this.endMs - this.startMs) / 1000.0);
            this.speedMps = poolLengthM / durSec;
        }
    }

    private void applySwimKinematicsIntervals(
            @NonNull String baseFileName,
            long startTimeMs,
            long durationMs,
            @NonNull java.util.List<SwimInterval> intervals,
            double poolLengthM
    ) {
        if (baseFileName == null || !existsTable(baseFileName) || intervals.isEmpty()) return;
        SQLiteDatabase db = getDatabase();
        String table = getTableName(baseFileName);

        if (!existsColumnInTable(db, table, SensorType.DISTANCE_m.name())) {
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + SensorType.DISTANCE_m.name() + " REAL DEFAULT 0.0");
            } catch (Exception ignored) {}
        }
        if (!existsColumnInTable(db, table, SensorType.SPEED_mps.name())) {
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + SensorType.SPEED_mps.name() + " REAL DEFAULT 0.0");
            } catch (Exception ignored) {}
        }
        if (!existsColumnInTable(db, table, SensorType.LAP_NR.name())) {
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + SensorType.LAP_NR.name() + " INTEGER DEFAULT 1");
            } catch (Exception ignored) {}
        }

        db.beginTransaction();
        try {
            java.util.List<Pair<Long, Long>> rows = new java.util.ArrayList<>();
            try (Cursor cursor = db.rawQuery(
                    "SELECT " + WorkoutSamplesDbHelper.C_ID + ", " + WorkoutSamplesDbHelper.TIME +
                    ", CAST(strftime('%s', " + WorkoutSamplesDbHelper.TIME + ") AS INTEGER) FROM " + table +
                    " ORDER BY " + WorkoutSamplesDbHelper.C_ID + " ASC", null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long rowId = cursor.getLong(0);
                        String timeStr = cursor.getString(1);
                        long timeSec = cursor.getLong(2);
                        long tsMs = (timeSec > 0) ? timeSec * 1000L : parseTimestampMs(timeStr);
                        rows.add(new Pair<>(rowId, tsMs));
                    }
                }
            }

            int numIntervals = intervals.size();
            if (!rows.isEmpty()) {
                SQLiteStatement stmt = db.compileStatement(
                        "UPDATE " + table + " SET " +
                        SensorType.DISTANCE_m.name() + " = ?, " +
                        SensorType.SPEED_mps.name() + " = ?, " +
                        SensorType.LAP_NR.name() + " = ? WHERE " +
                        WorkoutSamplesDbHelper.C_ID + " = ?");

                for (Pair<Long, Long> row : rows) {
                    long rowId = row.first;
                    long tsMs = row.second > 0 ? row.second : startTimeMs;

                    int activeLap = 1;
                    double cumDist = 0.0;
                    double speed = intervals.get(0).speedMps;

                    if (tsMs <= intervals.get(0).startMs) {
                        activeLap = 1;
                        cumDist = 0.0;
                        speed = intervals.get(0).speedMps;
                    } else if (tsMs >= intervals.get(numIntervals - 1).endMs) {
                        activeLap = numIntervals;
                        cumDist = numIntervals * poolLengthM;
                        speed = 0.0;
                    } else {
                        for (int i = 0; i < numIntervals; i++) {
                            SwimInterval inv = intervals.get(i);
                            if (tsMs >= inv.startMs && tsMs <= inv.endMs) {
                                activeLap = i + 1;
                                double fraction = (inv.endMs > inv.startMs)
                                        ? Math.max(0.0, Math.min(1.0, (double) (tsMs - inv.startMs) / (double) (inv.endMs - inv.startMs)))
                                        : 1.0;
                                cumDist = (i * poolLengthM) + (fraction * poolLengthM);
                                speed = inv.speedMps;
                                break;
                            } else if (i < numIntervals - 1 && tsMs > inv.endMs && tsMs < intervals.get(i + 1).startMs) {
                                activeLap = i + 1;
                                cumDist = (i + 1) * poolLengthM;
                                speed = inv.speedMps;
                                break;
                            }
                        }
                    }

                    stmt.bindDouble(1, cumDist);
                    stmt.bindDouble(2, speed);
                    stmt.bindLong(3, activeLap);
                    stmt.bindLong(4, rowId);
                    stmt.executeUpdateDelete();
                }
            } else if (durationMs > 0) {
                long secCount = Math.max(1L, durationMs / 1000L);
                SQLiteStatement insertStmt = db.compileStatement(
                        "INSERT INTO " + table + " (" + WorkoutSamplesDbHelper.TIME + ", " +
                        SensorType.DISTANCE_m.name() + ", " + SensorType.SPEED_mps.name() + ", " + SensorType.LAP_NR.name() +
                        ") VALUES (datetime(?, 'unixepoch'), ?, ?, ?)");
                for (int s = 0; s < secCount; s++) {
                    long currentSec = (startTimeMs / 1000L) + s;
                    long tsMs = currentSec * 1000L;

                    int activeLap = 1;
                    double cumDist = 0.0;
                    double speed = intervals.get(0).speedMps;

                    if (tsMs <= intervals.get(0).startMs) {
                        activeLap = 1;
                        cumDist = 0.0;
                        speed = intervals.get(0).speedMps;
                    } else if (tsMs >= intervals.get(numIntervals - 1).endMs) {
                        activeLap = numIntervals;
                        cumDist = numIntervals * poolLengthM;
                        speed = 0.0;
                    } else {
                        for (int i = 0; i < numIntervals; i++) {
                            SwimInterval inv = intervals.get(i);
                            if (tsMs >= inv.startMs && tsMs <= inv.endMs) {
                                activeLap = i + 1;
                                double fraction = (inv.endMs > inv.startMs)
                                        ? Math.max(0.0, Math.min(1.0, (double) (tsMs - inv.startMs) / (double) (inv.endMs - inv.startMs)))
                                        : 1.0;
                                cumDist = (i * poolLengthM) + (fraction * poolLengthM);
                                speed = inv.speedMps;
                                break;
                            } else if (i < numIntervals - 1 && tsMs > inv.endMs && tsMs < intervals.get(i + 1).startMs) {
                                activeLap = i + 1;
                                cumDist = (i + 1) * poolLengthM;
                                speed = inv.speedMps;
                                break;
                            }
                        }
                    }

                    insertStmt.bindLong(1, currentSec);
                    insertStmt.bindDouble(2, cumDist);
                    insertStmt.bindDouble(3, speed);
                    insertStmt.bindLong(4, activeLap);
                    insertStmt.executeInsert();
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static long parseTimestampMs(@Nullable String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return 0L;
        String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd_HHmmss"
        };
        for (String pattern : formats) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                java.util.Date date = sdf.parse(timeStr);
                if (date != null) return date.getTime();
            } catch (Exception ignored) {
            }
        }
        return 0L;
    }

    private static boolean existsColumnInTable(@NonNull SQLiteDatabase inDatabase, String inTable, String columnToCheck) {
        return SqliteSchemaUtils.hasColumn(inDatabase, inTable, columnToCheck);
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

    /**
     * Resolves the quoted SQLite table identifier for the given workout {@code workoutName}.
     *
     * <p>Requirement {@code REQ-TRK-026.2} ({@code ATT-3980}): Strictly rejects {@code null}, empty/blank,
     * or literal {@code "null"} (case-insensitive) input to prevent silent creation or targeting of a
     * table named {@code "\"null\""} during deferred resume flows.
     *
     * @param workoutName the workout {@code FILE_BASE_NAME}
     * @return the quoted SQLite table name {@code "\"<workoutName>\""}
     * @throws IllegalArgumentException if {@code workoutName} is {@code null}, blank, or literal {@code "null"}
     */
    @NonNull
    public static String getTableName(String workoutName) {
        if (workoutName == null || workoutName.trim().isEmpty() || "null".equalsIgnoreCase(workoutName.trim())) {
            throw new IllegalArgumentException("Invalid workout baseFileName for sample table: " + workoutName);
        }
        String escaped = workoutName.replace("\"", "\"\"");
        if (escaped.length() > 64) {
            String msg = "Workout table name exceeds SQLite identifier limit (64 chars): " + workoutName;
            try {
                Context appContext = TrainingApplication.getAppContext();
                if (appContext != null) {
                    msg = appContext.getString(R.string.error_workout_table_name_too_long) + ": " + workoutName;
                }
            } catch (Throwable ignored) {
            }
            throw new IllegalArgumentException(msg);
        }
        return "\"" + escaped + "\"";
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
