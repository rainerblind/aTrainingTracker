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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.exporter.ExportStatus;
import com.atrainingtracker.trainingtracker.exporter.ExportType;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;

import java.util.EnumMap;


/**
 * Repository zur Verwaltung des Export-Status in der Datenbank.
 * Dies ist ein Singleton und der EINZIGE Ort, an dem Datenbankzugriffe
 * für den Export-Status stattfinden. Alle Methoden sind Thread-sicher.
 */
public class ExportStatusDatabaseManager {

    private static final boolean DEBUG = true;
    private static final String TAG = "ExportStatusRepo";
    private static ExportStatusDatabaseManager sInstance;
    private final ExportStatusDbHelper mDbHelper;
    private SQLiteDatabase mDatabase = null;


    public static final String FORMAT = "Format";
    public static final String TYPE = "Type";
    public static final String EXPORT_STATUS = "Progress"; // TODO: rename to ExportStatus???
    public static final String ANSWER = "Answer";

    private ExportStatusDatabaseManager(Context context) {
        mDbHelper = ExportStatusDbHelper.getInstance(context);
    }

    public static synchronized ExportStatusDatabaseManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ExportStatusDatabaseManager(context.getApplicationContext());
        }
        return sInstance;
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
                mDbHelper.close();
            }
            mDatabase = mDbHelper.getWritableDatabase();
            return mDatabase;
        }
    }


    public void addExportStatus(ContentValues contentValues) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.insert(ExportStatusDbHelper.TABLE, null, contentValues);
    }


    /***********************************************************************************************
     * Updates the status of all 'TRACKING' exports for a given workout to 'WAITING'.
     * This is the most efficient way to perform this batch update.
     *
     * @param fileBaseName The unique identifier for the finished workout.
     * @return The number of rows affected.
     */
    public int workoutFinished(String fileBaseName) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(EXPORT_STATUS, ExportStatus.TRACKING_FINISHED.name());

        String whereClause = WorkoutSummaries.FILE_BASE_NAME + " = ? AND " + EXPORT_STATUS + " = ?";
        String[] whereArgs = { fileBaseName, ExportStatus.TRACKING.name() };

        return db.update(ExportStatusDbHelper.TABLE, values, whereClause, whereArgs);
    }

    public void updateExportStatus(ContentValues contentValues, String fileBaseName, ExportType exportType, FileFormat fileFormat) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot update status for " + fileBaseName + ", " + exportType + ", " + fileFormat);
            return;
        }
        db.update(ExportStatusDbHelper.TABLE,
                contentValues,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{fileBaseName, exportType.name(), fileFormat.name()});
    }

    public void updateExportStatus(ContentValues contentValues, ExportInfo exportInfo) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot update status for " + exportInfo);
            return;
        }

        db.update(ExportStatusDbHelper.TABLE,
                contentValues,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()});
    }



    public EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> getExportStatusMap(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + fileBaseName + " will return null");
            return null;
        }

        EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> result = new EnumMap<>(ExportType.class);
        Cursor cursor;

        for (ExportType exportType : ExportType.values()) {

            EnumMap<FileFormat, ExportStatus> enumMap = new EnumMap<>(FileFormat.class);
            for (FileFormat fileFormat : FileFormat.values()) {
                cursor = db.query(ExportStatusDbHelper.TABLE,
                        new String[]{EXPORT_STATUS},
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                        new String[]{fileBaseName, exportType.name(), fileFormat.name()},
                        null,
                        null,
                        null);
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    enumMap.put(fileFormat, ExportStatus.valueOf(cursor.getString(cursor.getColumnIndex(EXPORT_STATUS))));
                }
                cursor.close();
            }
            result.put(exportType, enumMap);
        }

        if (DEBUG) Log.d(TAG, "getExportStatus finished");

        return result;
    }

    public static class ExportRow {
        public final ExportType type;
        public final FileFormat format;
        public final ExportStatus status;
        public final String answer;

        public ExportRow(ExportType type, FileFormat format, ExportStatus status, String answer) {
            this.type = type;
            this.format = format;
            this.status = status;
            this.answer = answer;
        }
    }

    public java.util.List<ExportRow> getExportRows(String fileBaseName) {
        java.util.List<ExportRow> result = new java.util.ArrayList<>();
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        try (Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{TYPE, FORMAT, EXPORT_STATUS, ANSWER},
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{fileBaseName},
                null, null, null)) {
            while (cursor.moveToNext()) {
                try {
                    ExportType type = ExportType.valueOf(cursor.getString(0));
                    FileFormat format = FileFormat.valueOf(cursor.getString(1));
                    ExportStatus status = ExportStatus.valueOf(cursor.getString(2));
                    String answer = cursor.getString(3);
                    result.add(new ExportRow(type, format, status, answer));
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "Invalid enum value in DB", e);
                }
            }
        }
        return result;
    }

    public EnumMap<FileFormat, ExportStatus> getExportStatusMap(String fileBaseName, ExportType exportType) {
        if (DEBUG) Log.d(TAG, "getExportStatus " + fileBaseName + " " + exportType);

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + fileBaseName + " " + exportType + " will return null");
            return null;
        }

        EnumMap<FileFormat, ExportStatus> result = new EnumMap<>(FileFormat.class);
        Cursor cursor;

        for (FileFormat fileFormat : FileFormat.values()) {
            cursor = db.query(ExportStatusDbHelper.TABLE,
                    new String[]{EXPORT_STATUS},
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                    new String[]{fileBaseName, exportType.name(), fileFormat.name()},
                    null,
                    null,
                    null);
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                result.put(fileFormat, ExportStatus.valueOf(cursor.getString(cursor.getColumnIndex(EXPORT_STATUS))));
            }
            cursor.close();
        }

        if (DEBUG) Log.d(TAG, "getExportStatus finished");

        return result;
    }


    public ExportStatus getExportStatus(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export status for " + exportInfo + " will return null");
            return null;
        }

        ExportStatus exportStatus = null;

        Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{EXPORT_STATUS},
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                null,
                null,
                null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            exportStatus = ExportStatus.valueOf(cursor.getString(cursor.getColumnIndex(EXPORT_STATUS)));
        }
        cursor.close();

        return exportStatus;
    }


    public String getExportAnswer(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "getExportAnswer");

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot get the export answer for " + exportInfo + " will return null");
            return null;
        }

        String exportAnswer = null;

        Cursor cursor = db.query(ExportStatusDbHelper.TABLE,
                new String[]{ANSWER},
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + TYPE + "=? AND " + FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                null,
                null,
                null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            exportAnswer = cursor.getString(cursor.getColumnIndex(ANSWER));
        }
        cursor.close();

        return exportAnswer;
    }

    public void deleteWorkout(String baseFileName) {
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        if (db == null) {
            Log.e(TAG, "Database is null, cannot delete " + baseFileName);
        }

        db.delete(ExportStatusDbHelper.TABLE, WorkoutSummaries.FILE_BASE_NAME + "=?", new String[]{baseFileName});
    }


    protected static class ExportStatusDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "ExportStatus.db";
        public static final int DB_VERSION = 1;
        static final String TAG = "ExportStatusDbHelper";
        static final String TABLE = "ExportManager";
        static final String C_ID = BaseColumns._ID;
        static final String RETRIES = "Retries";  // shall not be used --> keep it here.
        protected static final String CREATE_TABLE = "create table " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "

                + WorkoutSummaries.FILE_BASE_NAME + " text, "
                + FORMAT + " text, "  // CSV, GPX, TCX, GC, Strava, RunKeeper, TrainingPeaks
                + TYPE + " text, "  // File, Dropbox, Community

                + EXPORT_STATUS + " text, "
                // + RETRIES + " int, "  // no longer necessary
                + ANSWER + " text)";

        private static ExportStatusDbHelper sInstance;

        /**
         * Ensure that there is only one instance of this DbHelper (Singleton-Pattern).
         */
        public static synchronized ExportStatusDbHelper getInstance(Context context) {
            if (sInstance == null) {
                sInstance = new ExportStatusDbHelper(context.getApplicationContext());
            }
            return sInstance;
        }

        // Constructor
        private ExportStatusDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(@NonNull SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE);

            if (DEBUG) Log.d(TAG, "onCreated sql: " + TABLE);
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO: alter table instead of deleting!

            db.execSQL("drop table if exists " + TABLE);
            if (DEBUG) Log.d(TAG, "onUpgraded");
            onCreate(db);  // run onCreate to get new database
        }

    }


}
