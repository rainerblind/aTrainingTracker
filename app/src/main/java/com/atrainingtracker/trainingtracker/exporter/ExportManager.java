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

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;

import org.json.JSONException;

import java.util.EnumMap;

public class ExportManager {
    public static final String EXPORT_STATUS_CHANGED_INTENT = "de.rainerblind.trainingtracker.EXPORT_STATUS_CHANGED_INTENT";
    private static final String TAG = "ExportManager";
    private static final boolean DEBUG = false;
    @Nullable
    protected static SQLiteDatabase cExportStatusDb;
    protected static int cInstances = 0;
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

    public static BaseExporter getExporter(@NonNull Context context, @NonNull ExportInfo exportInfo) {
        switch (exportInfo.getExportType()) {
            case FILE:
                return switch (exportInfo.getFileFormat()) {
                    case CSV -> new CSVFileExporter(context);
                    case GC -> new GCFileExporter(context);
                    case TCX -> new TCXFileExporter(context);
                    case GPX -> new GPXFileExporter(context);
                    case STRAVA -> new TCXFileExporter(context);
                    /* case RUNKEEPER:
                        return  new RunkeeperFileExporter(mContext);
                    /* case TRAINING_PEAKS:
                        return new TCXFileExporter(mContext);
                        return new TrainingPeaksFileExporter(mContext); */
                };
            case DROPBOX:
                return new DropboxUploader(context);
            case COMMUNITY:
                switch (exportInfo.getFileFormat()) {
                    case STRAVA -> new StravaUploader(context);
                                /* case RUNKEEPER:
                                    exporter = new RunkeeperUploader(mContext);
                                    break; */
                                /* case TRAINING_PEAKS:
                                    exporter = new TrainingPeaksUploader(mContext);
                                    break; */
                }
        }
        return null;
    }

    /** Method to inform the ExportManager that a new workout has been started.
     *
     * @param fileBaseName
     */
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
                // exportProgressValues.put(ExportStatusDbHelper.ANSWER, "");

                try {
                    cExportStatusDb.insert(ExportStatusDbHelper.TABLE, null, exportProgressValues);
                } catch (SQLException e) {
                    Log.e(TAG, "Error while writing" + e + "to ExportStatusDbHelper.TABLE");
                }
            }
        }

        broadcastExportStatusChanged();
    }


    /** Method to inform the ExportManager that a workout has been finished.
     * Note that this does not trigger exports and uploads.
     *
     * @param fileBaseName
     */
    public synchronized void workoutFinished(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "workoutFinished: " + fileBaseName);

        // two 'constants' for waiting and unwanted
        ContentValues WAITING = new ContentValues();
        WAITING.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());
        ContentValues UNWANTED = new ContentValues();
        UNWANTED.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.UNWANTED.name());


        // FILE
        for (FileFormat fileFormat : ExportType.FILE.getExportToFileFormats()) {
            if (TrainingApplication.exportToFile(fileFormat) || TrainingApplication.exportViaEmail(fileFormat)) {
                updateExportStatusDb(WAITING, fileBaseName, ExportType.FILE, fileFormat);
            } else {
                updateExportStatusDb(UNWANTED, fileBaseName, ExportType.FILE, fileFormat);
            }
        }

        // Dropbox
        if (TrainingApplication.uploadToDropbox()) {
            for (FileFormat fileFormat : ExportType.DROPBOX.getExportToFileFormats()) {
                if (TrainingApplication.exportToFile(fileFormat)) {
                    updateExportStatusDb(WAITING, fileBaseName, ExportType.DROPBOX, fileFormat);
                } else {
                    updateExportStatusDb(UNWANTED, fileBaseName, ExportType.DROPBOX, fileFormat);
                }
            }
        }

        // communities
        for (FileFormat fileFormat : ExportType.COMMUNITY.getExportToFileFormats()) {
            if (TrainingApplication.uploadToCommunity(fileFormat)) {
                updateExportStatusDb(WAITING, fileBaseName, ExportType.COMMUNITY, fileFormat);
            } else {
                updateExportStatusDb(UNWANTED, fileBaseName, ExportType.COMMUNITY, fileFormat);
            }
        }

        broadcastExportStatusChanged();
    }

    /** method to trigger the ExportManager to export a Workout to the various file formats and upload it to the cloud later on.
     *
     * @param fileBaseName
     */
    public synchronized void exportWorkout(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "exportWorkout: " + fileBaseName);

        for (FileFormat fileFormat : FileFormat.values()) {
            if (TrainingApplication.exportToFile(fileFormat) || TrainingApplication.exportViaEmail(fileFormat)) {
                // simply, trigger the export
                startFileExport(fileBaseName, fileFormat);
            }
        }
    }

    /** method to trigger the ExportManager to export a specific workout and FileFormat
     *
     * @param workoutId: The workout ID
     * @param fileFormat: The specific FileFormat
     */
    public synchronized void exportWorkoutTo(long workoutId, @NonNull FileFormat fileFormat) {
        if (DEBUG) Log.d(TAG, "exportWorkoutTo " + workoutId + ", " + fileFormat.name());

        String fileBaseName = getFileBaseName(workoutId);

        if (fileBaseName == null) {
            if (DEBUG) Log.d(TAG, "could not find the fileBaseName of workout " + workoutId);
            return;
        } else {
            if (DEBUG) Log.d(TAG, "fileBaseName: " + fileBaseName);
        }


        ContentValues values = new ContentValues();

        // user explicitly wants this.  So, we do not check whether we normally do this.  I.e. no if (TrainingApplication.exportToFile(fileFormat))
        values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());
        values.put(ExportStatusDbHelper.ANSWER, "");
        updateExportStatusDb(values, fileBaseName, ExportType.FILE, fileFormat);

        // trigger the export
        startFileExport(fileBaseName, fileFormat);

        // dropbox: either waiting or unwanted (retries are not yet set)
        if (TrainingApplication.uploadToDropbox()) {
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());
        } else {
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.UNWANTED.name());
        }
        updateExportStatusDb(values, fileBaseName, ExportType.DROPBOX, fileFormat);

        broadcastExportStatusChanged();
    }


    /** method to inform the ExportManager that an export has started
     *
     * @param exportInfo
     */
    public synchronized void exportingStarted(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "exportingStarted: " + exportInfo);

        ContentValues values = new ContentValues();
        values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.PROCESSING.name());

        updateExportStatusDb(values, exportInfo);

        broadcastExportStatusChanged();
    }

    /** method to inform the ExportManager that an export has been finished
     *
     * @param exportInfo
     * @param success: weather or not the export/upload was successfully.
     * @param answer: the text to be forwarded via a notification to the user.
     */
    public synchronized void exportingFinished(@NonNull ExportInfo exportInfo, boolean success, String answer) {
        if (DEBUG) Log.d(TAG, "exportingFinished: " + exportInfo + ": " + answer);

        // ' calculate' the remaining number of retries and the ExportStatus
        long retries = 0;
        ExportStatus exportStatus = ExportStatus.FINISHED_FAILED;

        if (success) {
            exportStatus = ExportStatus.FINISHED_SUCCESS;
        } else {
            exportStatus = ExportStatus.FINISHED_FAILED;  // TODO: How can we fix this???
            exportStatus = ExportStatus.FINISHED_RETRY;
        }

        // now, update the DB accordingly
        ContentValues values = new ContentValues();

        values.put(ExportStatusDbHelper.EXPORT_STATUS, exportStatus.name());
        values.put(ExportStatusDbHelper.ANSWER, answer);

        updateExportStatusDb(values, exportInfo);

        // note to ourself, that we have to continue with the upload to the cloud.
        if (exportInfo.getExportType() == ExportType.FILE && success) {
            exportingToFileFinishedStartCloudUploads(exportInfo);
        }

        broadcastExportStatusChanged();
    }

    /***********************************************************************************************
     * non-public stuff
     **********************************************************************************************/


    private void updateExportStatusDb(ContentValues contentValues, String fileBaseName, ExportType exportType, FileFormat fileFormat) {
        cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                contentValues,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                new String[]{fileBaseName, exportType.name(), fileFormat.name()});
    }

    private void updateExportStatusDb(ContentValues contentValues, ExportInfo exportInfo) {
        cExportStatusDb.update(ExportStatusDbHelper.TABLE,
                contentValues,
                WorkoutSummaries.FILE_BASE_NAME + "=? AND " + ExportStatusDbHelper.TYPE + "=? AND " + ExportStatusDbHelper.FORMAT + "=?",
                new String[]{exportInfo.getFileBaseName(), exportInfo.getExportType().name(), exportInfo.getFileFormat().name()});
    }

    /** simple helper method to start a file export */
    private void startFileExport(String fileBaseName, FileFormat fileFormat) {
        startExport(fileBaseName, fileFormat, ExportType.FILE);
    }


    /** simple helper method to start an export */
    private void startExport(String fileBaseName, FileFormat fileFormat, ExportType exportType) {

        ExportInfo exportInfo = new ExportInfo(fileBaseName, fileFormat, exportType);
        BaseExporter exporter = getExporter(mContext, exportInfo);

        if (exportType == ExportType.FILE) {
            exporter.export(exportInfo);  // when exporting to a file, we can immediately start.
        } else {
            scheduleUpload(exportInfo);   // when uploading to the cloud, we use a scheduled upload.
        }
    }


    /* method to do a scheduled export for uploading to the cloud */
    private void scheduleUpload(@NonNull ExportInfo exportInfo) {
        Log.d(TAG, "Scheduling upload for: " + exportInfo.toString());

        Data inputData;
        try {
            // Create a Data object with the parameters
            inputData = new Data.Builder()
                    .putString("EXPORT_INFO_JSON", exportInfo.toJson())
                    .build();
        } catch (JSONException e) {
            Log.e(TAG, "JSONException: " + e.getMessage(), e);
            exportingFinished(exportInfo, false, "Exception: " + e.getMessage());
            return;
        }

        // Define constraints (must have network)
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Build the request
        OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        // Enqueue the work
        WorkManager.getInstance(mContext).enqueue(uploadWorkRequest);

        exportingStarted(exportInfo);
    }



    /** Inform the manager that the exporting to file finished.
     *  The manager will start the cloud uploads.
     */
    protected synchronized void exportingToFileFinishedStartCloudUploads(@NonNull ExportInfo exportInfo) {
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
                startExport(fileBaseName, fileFormat, ExportType.DROPBOX);
            }

            // TODO: check for e-Mail.
            // when all wanted file exports are finished, we could start to trigger the e-Mail sending...
        }

        if (TrainingApplication.uploadToCommunity(fileFormat)) {
            startExport(fileBaseName, fileFormat, ExportType.COMMUNITY);
        }
    }


    /***********************************************************************************************
     * simple helper to send a broadcast
     **********************************************************************************************/

    private void broadcastExportStatusChanged() {
        if (DEBUG) Log.d(TAG, "exportStatusChanged");
        mContext.sendBroadcast(new Intent(EXPORT_STATUS_CHANGED_INTENT)
                .setPackage(mContext.getPackageName()));
    }


    /***********************************************************************************************
     * helpers to deal with the database
     **********************************************************************************************/


    /** simple helper method to get the baseFileName from a given workoutId */
    private String getFileBaseName(long workoutId) {
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
        cursor.close();
        WorkoutSummariesDatabaseManager.getInstance().closeDatabase(); // db.close();

        return fileBaseName;
    }


    @NonNull
    public synchronized EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> getExportStatus(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "getExportStatus");

        EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>> result = new EnumMap<>(ExportType.class);

        Cursor cursor;

        for (ExportType exportType : ExportType.values()) {

            EnumMap<FileFormat, ExportStatus> enumMap = new EnumMap<>(FileFormat.class);
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


    @Nullable
    public synchronized String getExportAnswer(@NonNull ExportInfo exportInfo) {
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


    @Nullable
    public synchronized ExportStatus getExportStatus(@NonNull ExportInfo exportInfo) {
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
