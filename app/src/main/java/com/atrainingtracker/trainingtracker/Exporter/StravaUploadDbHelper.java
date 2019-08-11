package com.atrainingtracker.trainingtracker.Exporter;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

public class StravaUploadDbHelper extends SQLiteOpenHelper {
    // The different tables
    public static final String TABLE = "StravaUploads";
    // colums
    public static final String C_ID = BaseColumns._ID;
    public static final String UPLOAD_ID = "UploadId";
    public static final String ACTIVITY_ID = "ActivityId";
    public static final String STATUS = "Status";
    static final String DB_NAME = "StravaUpload.db";
    static final int DB_VERSION = 3;
    private static final String TAG = "StravaUploadDbHelper";
    private static final boolean DEBUG = false;
    private static final String CREATE_TABLE = "create table " + TABLE + " ("
            + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + WorkoutSummaries.FILE_BASE_NAME + " text,"    // forms as a key
            + UPLOAD_ID + " text,"
            + ACTIVITY_ID + " text,"
            + STATUS + " text)";

    // Constructor
    public StravaUploadDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);

        if (DEBUG) Log.d(TAG, "onCreated sql: " + CREATE_TABLE);

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: alter table instead of deleting!

        db.execSQL("drop table if exists " + TABLE);  // drops the old database

        onCreate(db);  // run onCreate to get new database

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

    public void updateAll(String fileBaseName, String upload_id, String activity_id, String status) {
        if (DEBUG)
            Log.d(TAG, "updateStatus: " + fileBaseName + " upload_id: " + upload_id + " activity_id: " + activity_id + " status: " + status);

        ContentValues values = new ContentValues();
        values.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
        values.put(UPLOAD_ID, upload_id);
        values.put(ACTIVITY_ID, activity_id);
        values.put(STATUS, status);
        updateOrInsert(fileBaseName, values);
    }

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
}
