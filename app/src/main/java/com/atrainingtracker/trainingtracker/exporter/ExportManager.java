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

import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.exporter.ExportStatusRepository.ExportStatusDbHelper;

import org.json.JSONException;

import java.util.EnumMap;
import java.util.List;

public class ExportManager {
    public static final String EXPORT_STATUS_CHANGED_INTENT = "de.rainerblind.trainingtracker.EXPORT_STATUS_CHANGED_INTENT";
    private static final String TAG = "ExportManager";
    private static final boolean DEBUG = true;
    @Nullable
    protected final Context mContext;
    // protected static HashMap<String, EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>>> cCash = new HashMap<String, EnumMap<ExportType, EnumMap<FileFormat, ExportStatus>>>();
    private final ExportStatusRepository mRepository;

    public ExportManager(Context context) {
        mContext = context;
        mRepository = ExportStatusRepository.getInstance(context);
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

                mRepository.addExportStatus(exportProgressValues);
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
                mRepository.updateExportStatus(WAITING, fileBaseName, ExportType.FILE, fileFormat);
            } else {
                mRepository.updateExportStatus(UNWANTED, fileBaseName, ExportType.FILE, fileFormat);
            }
        }

        // Dropbox
        if (TrainingApplication.uploadToDropbox()) {
            for (FileFormat fileFormat : ExportType.DROPBOX.getExportToFileFormats()) {
                if (TrainingApplication.exportToFile(fileFormat)) {
                    mRepository.updateExportStatus(WAITING, fileBaseName, ExportType.DROPBOX, fileFormat);
                } else {
                    mRepository.updateExportStatus(UNWANTED, fileBaseName, ExportType.DROPBOX, fileFormat);
                }
            }
        }

        // communities
        for (FileFormat fileFormat : ExportType.COMMUNITY.getExportToFileFormats()) {
            if (TrainingApplication.uploadToCommunity(fileFormat)) {
                mRepository.updateExportStatus(WAITING, fileBaseName, ExportType.COMMUNITY, fileFormat);
            } else {
                mRepository.updateExportStatus(UNWANTED, fileBaseName, ExportType.COMMUNITY, fileFormat);
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
        mRepository.updateExportStatus(values, fileBaseName, ExportType.FILE, fileFormat);

        // trigger the export
        startFileExport(fileBaseName, fileFormat);

        // dropbox: either waiting or unwanted (retries are not yet set)
        if (TrainingApplication.uploadToDropbox()) {
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());
        } else {
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.UNWANTED.name());
        }
        mRepository.updateExportStatus(values, fileBaseName, ExportType.DROPBOX, fileFormat);

        broadcastExportStatusChanged();
    }


    /***********************************************************************************************
     * non-public stuff
     **********************************************************************************************/


    /** method to inform the ExportManager that an export has started
     *
     * @param exportInfo
     */
    private synchronized void exportingStarted(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "exportingStarted: " + exportInfo);

        ContentValues values = new ContentValues();
        values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.PROCESSING.name());

        mRepository.updateExportStatus(values, exportInfo);

        broadcastExportStatusChanged();
    }

    /** method to inform the ExportManager that an export has been finished
     *
     * @param exportInfo
     * @param success: weather or not the export/upload was successfully.
     * @param answer: the text to be forwarded via a notification to the user.
     */
    private synchronized void exportingFinished(@NonNull ExportInfo exportInfo, boolean success, String answer) {
        if (DEBUG) Log.d(TAG, "exportingFinished: " + exportInfo + ": " + answer);

        // ' calculate' the remaining number of retries and the ExportStatus
        long retries = 0;
        ExportStatus exportStatus = ExportStatus.FINISHED_FAILED;

        if (success) {
            exportStatus = ExportStatus.FINISHED_SUCCESS;
        } else {
            exportStatus = ExportStatus.FINISHED_FAILED;
        }

        // now, update the DB accordingly
        ContentValues values = new ContentValues();

        values.put(ExportStatusDbHelper.EXPORT_STATUS, exportStatus.name());
        values.put(ExportStatusDbHelper.ANSWER, answer);

        mRepository.updateExportStatus(values, exportInfo);

        // note to ourself, that we have to continue with the upload to the cloud.
        if (exportInfo.getExportType() == ExportType.FILE && success) {
            exportingToFileFinishedStartCloudUploads(exportInfo);
        }

        broadcastExportStatusChanged();
    }


    /** simple helper method to start a file export */
    private void startFileExport(String fileBaseName, FileFormat fileFormat) {
        startExport(fileBaseName, fileFormat, ExportType.FILE);
    }


    /** helper method to start an export
     * the export will be done by a worker in the background.
     *
     * @param fileBaseName
     * @param fileFormat
     * @param exportType
     */
    private void startExport(String fileBaseName, FileFormat fileFormat, ExportType exportType) {

        ExportInfo exportInfo = new ExportInfo(fileBaseName, fileFormat, exportType);
        exportingStarted(exportInfo);

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

        // define constraints (when exporting to a file, we do not need a network connection; otherwise, we need one).
        Constraints.Builder constraintsBuilder = new Constraints.Builder();
        if (exportInfo.getExportType() != ExportType.FILE) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED);
        }
        Constraints constraints = constraintsBuilder.build();

        String workTag = exportInfo.toString(); // e.g., "COMMUNITY: TCX: 2024-01-12-10-00-00"
        workTag = workTag + "@" + System.currentTimeMillis();  // add a time-stamp

        // Build the request
        OneTimeWorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(ExportWorker.class)
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag(workTag)
                .build();

        // Enqueue the work
        WorkManager.getInstance(mContext).enqueue(uploadWorkRequest);

        // observe the work
        observeWorker(workTag, exportInfo);
    }


    /* method to do a scheduled export for uploading to the cloud */
    private void scheduleUpload(@NonNull ExportInfo exportInfo) {
        Log.d(TAG, "Scheduling upload for: " + exportInfo.toString());

    }

    private void observeWorker(String workTag, ExportInfo exportInfo) {
        WorkManager.getInstance(mContext)
                .getWorkInfosByTagLiveData(workTag)
                .observeForever(new Observer<List<WorkInfo>>() {
                    @Override
                    public void onChanged(List<WorkInfo> workInfos) {
                        if (workInfos == null || workInfos.isEmpty()) {
                            return;
                        }

                        // We are interested in the state of the first WorkInfo object
                        WorkInfo workInfo = workInfos.get(0);
                        Log.d(TAG, "Work status changed for " + exportInfo + ": " + workInfo.getState());
                        // TODO: we get more detailed information about the state.  Use this to update the db and notify the user.

                        if (workInfo.getState().isFinished()) {
                            // The job is finished, now we can update our state
                            handleWorkerFinished(workInfo, exportInfo);

                            // Remove the observer to prevent memory leaks
                            WorkManager.getInstance(mContext).getWorkInfosByTagLiveData(workTag).removeObserver(this);
                        }
                    }
                });
    }


    private void handleWorkerFinished(WorkInfo workInfo, ExportInfo exportInfo) {
        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
            // The worker reported success!
            Log.i(TAG, "WorkManager reported SUCCESS for: " + exportInfo);
            exportingFinished(exportInfo, true, "Upload successful.");

        } else if (workInfo.getState() == WorkInfo.State.FAILED) {
            // The worker reported a permanent failure.
            Log.w(TAG, "WorkManager reported FAILED for: " + exportInfo);
            exportingFinished(exportInfo, false, "Upload failed. No more retries.");

        } else if (workInfo.getState() == WorkInfo.State.CANCELLED) {
            // Handle cancellation if needed
            Log.w(TAG, "WorkManager reported CANCELLED for: " + exportInfo);
            exportingFinished(exportInfo, false, "Upload cancelled.");
        }
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
}
