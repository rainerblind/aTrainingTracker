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

package com.atrainingtracker.trainingtracker.exporter.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

public class StravaUploadDbHelper extends SQLiteOpenHelper {
    // The different tables
    public static final String TABLE = "StravaUploads";
    // columns
    public static final String C_ID = BaseColumns._ID;
    public static final String UPLOAD_ID = "UploadId";
    public static final String ACTIVITY_ID = "ActivityId";
    public static final String STATUS = "Status";
    public static final String STRAVA_ACTIVITY_DATA = "StravaActivity";
    static final String DB_NAME = "StravaUpload.db";
    static final int DB_VERSION = 5;
    private static final String TAG = "StravaUploadDbHelper";
    private static final boolean DEBUG = false;
    private static final String CREATE_TABLE = "create table " + TABLE + " ("
            + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + WorkoutSummaries.FILE_BASE_NAME + " text,"    // forms as a key
            + UPLOAD_ID + " text,"
            + ACTIVITY_ID + " text,"
            + STATUS + " text,"
            + STRAVA_ACTIVITY_DATA + " text)";

    // Constructor
    // TODO: do we really need this??
    public StravaUploadDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);

        if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_TABLE);

    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            // Migrating from 3 to 4 added the original 'Feedback' column (now renamed)
            // But since we are at 5 now, we skip directly to the latest schema logic.
            db.execSQL("drop table if exists " + TABLE);
            onCreate(db);
        } else if (oldVersion == 4) {
            // Version 4 had 'Feedback', Version 5 uses 'StravaActivity'
            // We safely migrate the data by renaming the column
            try {
                db.beginTransaction();
                // 1. Rename existing column from Feedback to StravaActivity
                db.execSQL("ALTER TABLE " + TABLE + " RENAME COLUMN Feedback TO " + STRAVA_ACTIVITY_DATA);
                db.setTransactionSuccessful();
            } catch (SQLException e) {
                Log.e(TAG, "Failed to migrate Feedback column: " + e.getMessage());
                // Fallback: recreate if migration fails
                db.execSQL("drop table if exists " + TABLE);
                onCreate(db);
            } finally {
                db.endTransaction();
            }
        }
    }

    public void updateOrInsert(String fileBaseName, ContentValues values) {
        SQLiteDatabase db = getWritableDatabase();

        int updates = 0;
        try {
            updates = db.update(TABLE,
                    values,
                    WorkoutSummaries.FILE_BASE_NAME + "=?",
                    new String[]{fileBaseName});
            if (DEBUG) Log.d(TAG, "updated " + fileBaseName);
        } catch (SQLException e) {
            Log.e(TAG, e.getMessage());
            if (DEBUG) Log.d(TAG, "Exception! for " + fileBaseName);
        }
        if (updates < 1) {  // if nothing is updated, we create the entry
            db.insert(TABLE, null, values);
            if (DEBUG) Log.d(TAG, "added " + fileBaseName);
        }

        db.close();

    }

    public void updateStatus(String fileBaseName, String status) {
        if (DEBUG) Log.d(TAG, "updateStatus: " + fileBaseName + " status: " + status);

        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
        values.put(STATUS, status);
        updateOrInsert(fileBaseName, values);
    }

    public void updateUploadId(String fileBaseName, String id) {
        if (DEBUG) Log.d(TAG, "updateStatus: " + fileBaseName + " id: " + id);

        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
        values.put(UPLOAD_ID, id);
        updateOrInsert(fileBaseName, values);
    }

    public void updateActivityId(String fileBaseName, String activity_id) {
        if (DEBUG) Log.d(TAG, "updateStatus: " + fileBaseName + " activity_id: " + activity_id);

        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
        values.put(ACTIVITY_ID, activity_id);
        updateOrInsert(fileBaseName, values);
    }

    public void updateStravaActivityData(String fileBaseName, String activityData) {
        if (DEBUG) Log.d(TAG, "updateStravaActivityData: " + fileBaseName);

        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
        values.put(STRAVA_ACTIVITY_DATA, activityData);
        updateOrInsert(fileBaseName, values);
    }

    public void updateAll(String fileBaseName, String upload_id, String activity_id, String status, String activityData) {
        if (DEBUG)
            Log.d(TAG, "updateAll: " + fileBaseName + " upload_id: " + upload_id + " activity_id: " + activity_id + " status: " + status);

        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
        values.put(UPLOAD_ID, upload_id);
        values.put(ACTIVITY_ID, activity_id);
        values.put(STATUS, status);
        values.put(STRAVA_ACTIVITY_DATA, activityData);
        updateOrInsert(fileBaseName, values);
    }

    @Nullable
    public String getActivityId(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "getActivityId: " + fileBaseName);

        String activityId = null;

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE, null, WorkoutSummaries.FILE_BASE_NAME + "=?", new String[]{fileBaseName}, null, null, null);

        if (cursor.moveToFirst()) {
            if (!cursor.isNull(cursor.getColumnIndex(ACTIVITY_ID))) {
                activityId = cursor.getString(cursor.getColumnIndex(ACTIVITY_ID));
            }
        } else {
            Log.e(TAG, "in getActivityId: no entry or invalid entry for " + fileBaseName);
        }

        cursor.close();
        db.close();

        if (DEBUG) Log.d(TAG, "ActivityId: " + activityId);
        return activityId;
    }

    @Nullable
    public String getStravaActivityData(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "getStravaActivityData: " + fileBaseName);

        String activityData = null;

        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE, null, WorkoutSummaries.FILE_BASE_NAME + "=?", new String[]{fileBaseName}, null, null, null);

        if (cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(STRAVA_ACTIVITY_DATA);
            if (idx != -1 && !cursor.isNull(idx)) {
                activityData = cursor.getString(idx);
            }
        }

        cursor.close();

        return activityData;
    }
}
