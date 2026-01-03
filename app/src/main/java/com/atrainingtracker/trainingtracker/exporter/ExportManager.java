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

package com.atrainingtracker.trainingtracker.exporter;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import java.util.ArrayList;
import java.util.EnumMap;

public class ExportManager {
    public static final String EXPORT_STATUS_CHANGED_INTENT = "de.rainerblind.trainingtracker.EXPORT_STATUS_CHANGED_INTENT";
    private static final String TAG = "ExportManager";
    private static final boolean DEBUG = false;
    protected static SQLiteDatabase cExportStatusDb;
    protected static int cInstances = 0;
    private static final int DEFAULT_RETRIES_FILE = 1;
    private static final int DEFAULT_RETRIES_DROPBOX = 10;
    private static final int DEFAULT_RETRIES_COMMUNITY = 1;
    protected final Context mContext;
    // protected static HashMap<String, EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>>> cCash = new HashMap<String, EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>>>();

    public ExportManager(Context context, String caller) {
        cInstances++;

        if (DEBUG)
            Log.d(TAG, "constructor called by " + caller + ": now, we have " + cInstances + " instances");

        mContext = context;
        if (cExportStatusDb == null) {
            cExportStatusDb = (new ExportStatusDbHelper(context)).getWritableDatabase();
        }
    }

    public void onFinished(String caller) {
        cInstances--;

        if (DEBUG)
            Log.d(TAG, "onFinished called by " + caller + ": now, we have " + cInstances + " instances");

        if (cInstances == 0) {
            cExportStatusDb.close();
            cExportStatusDb = null;
        }
    }

    public synchronized void newWorkout(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "newWorkout: " + fileBaseName);

        ContentValues exportProgressValues = new ContentValues();
        for (FileFormat fileFormat : FileFormat.values()) {
            for (ExportType exportType : ExportType.values()) {
                exportProgressValues.clear();

                exportProgressValues.put(WorkoutSummaries.FILE_BASE_NAME, fileBaseName);
                exportProgressValues.put(ExportStatusDbHelper.FORMAT, fileFormat.name());
                exportProgressValues.put(ExportStatusDbHelper.TYPE, exportType.name());
                exportProgressValues.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.TRACKING.name());
                exportProgressValues.put(ExportStatusDbHelper.RETRIES, 0);        //  we do not want to export while tracking
                // exportProgressValues.put(ExportStatusDbHelper.ANSWER, "");

                try {
                    cExportStatusDb.insert(ExportStatusDbHelper.TABLE, null, exportProgressValues);
                } catch (SQLException e) {
                    Log.e(TAG, "Error while writing" + e + "to ExportStatusDbHelper.TABLE");
                }
            }
        }

        exportStatusChanged();
    }


    public synchronized void workoutFinished(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "workoutFinished: " + fileBaseName);

        ContentValues values = new ContentValues();

        values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.UNWANTED.name());
        cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                values,
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{fileBaseName});

        // FILE
        for (FileFormat fileFormat : ExportType.FILE.getExportToFileFormats()) {
            if (TrainingApplication.exportToFile(fileFormat) || TrainingApplication.exportViaEmail(fileFormat)) {
                values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());
                //values.put(ExportStatusDbHelper.RETRIES,  DEFAULT_RETRIES);
                cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                        values,
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                        new String[]{fileBaseName, ExportType.FILE.name(), fileFormat.name()});
            }
        }

        // Dropbox
        if (TrainingApplication.uploadToDropbox()) {
            for (FileFormat fileFormat : ExportType.DROPBOX.getExportToFileFormats()) {
                if (TrainingApplication.exportToFile(fileFormat)) {

                    values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());

                    cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                            values,
                            WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                            new String[]{fileBaseName, ExportType.DROPBOX.name(), fileFormat.name()});
                }
            }
        }

        // communities
        for (FileFormat fileFormat : ExportType.COMMUNITY.getExportToFileFormats()) {
            if (TrainingApplication.uploadToCommunity(fileFormat)) {

                values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());

                cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                        values,
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                        new String[]{fileBaseName, ExportType.COMMUNITY.name(), fileFormat.name()});
            }

        }

        exportStatusChanged();
    }


    public synchronized void exportWorkout(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "exportWorkout: " + fileBaseName);

        ContentValues values = new ContentValues();

        for (FileFormat fileFormat : FileFormat.values()) {
            if (TrainingApplication.exportToFile(fileFormat) || TrainingApplication.exportViaEmail(fileFormat)) {
                values.put(ExportStatusDbHelper.RETRIES, DEFAULT_RETRIES_FILE);

                int updates = cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                        values,
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                        new String[]{fileBaseName, ExportType.FILE.name(), fileFormat.name()});
                if (DEBUG) Log.d(TAG, fileFormat.name() + ": " + updates + " updates");
            }
        }
    }


    public synchronized void exportWorkoutTo(long workoutId, FileFormat fileFormat) {
        if (DEBUG) Log.d(TAG, "exportWorkoutTo " + workoutId + ", " + fileFormat.name());

        SQLiteDatabase db = WorkoutSummariesDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(WorkoutSummaries.TABLE,
                new String[]{WorkoutSummaries.FILE_BASE_NAME},
                WorkoutSummaries.C_ID + "=?",
                new String[]{Long.toString(workoutId)},
                null,
                null,
                null);
        cursor.moveToFirst();
        String fileBaseName = cursor.getString(0);
        if (fileBaseName == null) {
            if (DEBUG) Log.d(TAG, "could not find the fileBaseName of workout " + workoutId);
            return;
        } else {
            if (DEBUG) Log.d(TAG, "fileBaseName: " + fileBaseName);
        }
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // db.close();

        ContentValues values = new ContentValues();

        // user explicitly wants this.  So, we do not check whether we normally do this
        // if (TrainingApplication.exportToFile(fileFormat)) {
        values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());
        values.put(ExportStatusDbHelper.RETRIES, DEFAULT_RETRIES_FILE);
        values.put(ExportStatusDbHelper.ANSWER, "");
        cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                values,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                new String[]{fileBaseName, ExportType.FILE.name(), fileFormat.name()});
        // }

        // dropbox: either waiting or unwanted (retries are not yet set)
        if (TrainingApplication.uploadToDropbox()) {
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());
        } else {
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.UNWANTED.name());
        }
        cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                values,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                new String[]{fileBaseName, ExportType.DROPBOX.name(), fileFormat.name()});

        exportStatusChanged();
    }


    // TODO: queue changes when exporting to file finished (we have to upload to dropbox/community)
    public synchronized ArrayList<ExportInfo> getExportQueue() {
        if (DEBUG) Log.d(TAG, "getExportQueue");

        ArrayList<ExportInfo> result = new ArrayList<ExportInfo>();

        // 
        Cursor cursor = cExportStatusDb.query(ExportStatusDbHelper.TABLE,
                new String[]{ExportStatusDbHelper.RETRIES, WorkoutSummaries.FILE_BASE_NAME, ExportStatusDbHelper.TYPE, ExportStatusDbHelper.FORMAT},
                null, // maybe something like RETRIES > 0.  How to do this?  
                null,
                null,
                null,
                ExportStatusDbHelper.RETRIES + " DESC");

        while (cursor.moveToNext()) {
            int retries = cursor.getInt(cursor.getColumnIndex(ExportStatusDbHelper.RETRIES));
            if (retries > 0) {
                ExportInfo exportInfo = new ExportInfo(cursor.getString(cursor.getColumnIndex(WorkoutSummaries.FILE_BASE_NAME)),
                        FileFormat.valueOf(cursor.getString(cursor.getColumnIndex(ExportStatusDbHelper.FORMAT))),
                        ExportType.valueOf(cursor.getString(cursor.getColumnIndex(ExportStatusDbHelper.TYPE))));
                result.add(exportInfo);
                if (DEBUG) Log.d(TAG, "added " + exportInfo + " retries: " + retries);
            }
        }
        cursor.close();

        return result;
    }


    public synchronized void exportingStarted(ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "exportingStarted: " + exportInfo);

        ContentValues values = new ContentValues();

        values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.PROCESSING.name());

        cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                values,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()});
        // db.close();

        exportStatusChanged();
    }


    public synchronized void exportingFinished(ExportInfo exportInfo, boolean success, String answer) {
        if (DEBUG) Log.d(TAG, "exportingFinished: " + exportInfo + ": " + answer);

        long retries = 0;
        ExportStatus exportStatus = ExportStatus.FINISHED_FAILED;

        if (success) {
            retries = 0;
            exportStatus = ExportStatus.FINISHED_SUCCESS;
        } else {
            // get the number of retries from the db
            Cursor cursor = cExportStatusDb.query(ExportStatusDbHelper.TABLE,
                    new String[]{ExportStatusDbHelper.RETRIES},
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                    new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                    null,
                    null,
                    null);
            if (cursor.getCount() > 0) {
                cursor.moveToFirst();
                retries = cursor.getLong(cursor.getColumnIndex(ExportStatusDbHelper.RETRIES));
                retries--;
                if (retries == 0) {
                    exportStatus = ExportStatus.FINISHED_FAILED;
                } else {
                    exportStatus = ExportStatus.FINISHED_RETRY;
                }
            }
            cursor.close();
        }


        ContentValues values = new ContentValues();

        values.put(ExportStatusDbHelper.RETRIES, retries);
        values.put(ExportStatusDbHelper.EXPORT_STATUS, exportStatus.name());
        values.put(ExportStatusDbHelper.ANSWER, answer);

        cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                values,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()});
        // db.close();


        if (exportInfo.getExportType() == ExportType.FILE) {
            exportingToFileFinished(exportInfo);
        }

        exportStatusChanged();
    }


    public synchronized EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> getExportStatus(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> result = new EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>>(ExportType.class);

        Cursor cursor;

        for (ExportType exportType : ExportType.values()) {

            EnumMap<FileFormat, ExportStatus> enumMap = new EnumMap<FileFormat, ExportStatus>(FileFormat.class);
            for (FileFormat fileFormat : FileFormat.values()) {
                cursor = cExportStatusDb.query(ExportStatusDbHelper.TABLE,
                        new String[]{ExportStatusDbHelper.EXPORT_STATUS},
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                        new String[]{fileBaseName, exportType.name(), fileFormat.name()},
                        null,
                        null,
                        null);
                if (cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    enumMap.put(fileFormat, ExportStatus.valueOf(cursor.getString(cursor.getColumnIndex(ExportStatusDbHelper.EXPORT_STATUS))));
                }
                cursor.close();
            }
            result.put(exportType, enumMap);
        }

        if (DEBUG) Log.d(TAG, "getExportStatus finished");

        return result;
    }


    public synchronized String getExportAnswer(ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "getExportAnswer");

        String exportAnswer = null;

        Cursor cursor = cExportStatusDb.query(ExportStatusDbHelper.TABLE,
                new String[]{ExportStatusDbHelper.ANSWER},
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                null,
                null,
                null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            exportAnswer = cursor.getString(cursor.getColumnIndex(ExportStatusDbHelper.ANSWER));
        }
        cursor.close();

        return exportAnswer;
    }


    public synchronized ExportStatus getExportStatus(ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        ExportStatus exportStatus = null;

        Cursor cursor = cExportStatusDb.query(ExportStatusDbHelper.TABLE,
                new String[]{ExportStatusDbHelper.EXPORT_STATUS},
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()},
                null,
                null,
                null);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            exportStatus = ExportStatus.valueOf(cursor.getString(cursor.getColumnIndex(ExportStatusDbHelper.EXPORT_STATUS)));
        }
        cursor.close();

        return exportStatus;
    }


    protected synchronized void exportingToFileFinished(ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "exportingToFileFinished: " + exportInfo.toString());

        FileFormat fileFormat = exportInfo.getFileFormat();
        String fileBaseName = exportInfo.getFileBaseName();

        ContentValues values = new ContentValues();

        // TODO: switch fileFormat?  But this does not allow to do multiple actions, e.g. uploading to Dropbox and Strava!

        if (fileFormat == FileFormat.GC || fileFormat == FileFormat.TCX || fileFormat == FileFormat.GPX || fileFormat == FileFormat.CSV
            // || fileFormat == FileFormat.STRAVA            // only for debugging
            // || fileFormat == FileFormat.RUNKEEPER         // only for debugging
            // || fileFormat == FileFormat.TRAINING_PEAKS    // only for debugging
        ) {

            if (TrainingApplication.uploadToDropbox()) {
                values.put(ExportStatusDbHelper.RETRIES, DEFAULT_RETRIES_DROPBOX);
                cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                        values,
                        WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                        new String[]{fileBaseName, ExportType.DROPBOX.name(), fileFormat.name()});
            }
        }

        if (TrainingApplication.uploadToCommunity(fileFormat)) {
            values.put(ExportStatusDbHelper.RETRIES, DEFAULT_RETRIES_COMMUNITY);
            cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                    values,
                    WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                    new String[]{fileBaseName, ExportType.COMMUNITY.name(), fileFormat.name()});
        }
    }


    protected void exportStatusChanged() {
        if (DEBUG) Log.d(TAG, "exportStatusChanged");
        mContext.sendBroadcast(new Intent(EXPORT_STATUS_CHANGED_INTENT)
                .setPackage(mContext.getPackageName()));
    }

    public void deleteWorkout(String baseFileName) {
        cExportStatusDb.delete(ExportStatusDbHelper.TABLE, WorkoutSummaries.FILE_BASE_NAME + "=?", new String[]{baseFileName});
    }


    protected static class ExportStatusDbHelper extends SQLiteOpenHelper {
        public static final String DB_NAME = "ExportStatus.db";
        public static final int DB_VERSION = 1;
        static final String TAG = "ExportStatusDbHelper";
        static final String TABLE = "ExportManager";
        static final String C_ID = BaseColumns._ID;
        static final String FORMAT = "Format";
        static final String TYPE = "Type";
        static final String EXPORT_STATUS = "Progress"; // TODO: rename to ExportStatus
        static final String RETRIES = "Retries";
        static final String ANSWER = "Answer";
        protected static final String CREATE_TABLE = "create table " + TABLE + " ("
                + C_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "

                + WorkoutSummaries.FILE_BASE_NAME + " text, "
                + FORMAT + " text, "  // CSV, GPX, TCX, GC, Strava, RunKeeper, TrainingPeaks
                + TYPE + " text, "  // File, Dropbox, Community

                + EXPORT_STATUS + " text, "
                + RETRIES + " int, "
                + ANSWER + " text)";

        // Constructor
        public ExportStatusDbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        // Called only once, first time the DB is created
        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL(CREATE_TABLE);

            if (DEBUG) Log.d(TAG, "onCreated sql: " + TABLE);
        }

        //Called whenever newVersion != oldVersion
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // TODO: alter table instead of deleting!

            db.execSQL("drop table if exists " + TABLE);
            if (DEBUG) Log.d(TAG, "onUpgraded");
            onCreate(db);  // run onCreate to get new database
        }

    }

}
