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

import static com.atrainingtracker.trainingtracker.exporter.ExportStatusChangedBroadcaster.broadcastExportStatusChanged;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

import java.util.ArrayList;
import java.util.List;

public class ExportManager {
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

        broadcastExportStatusChanged(mContext);
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

        broadcastExportStatusChanged(mContext);
    }

    /** method to trigger the ExportManager to export a Workout to the various file formats and upload it to the cloud later on.
     *
     * @param fileBaseName
     */
    public synchronized void exportWorkout(String fileBaseName) {
        if (DEBUG) Log.d(TAG, "exportWorkout: " + fileBaseName);

        for (FileFormat fileFormat : FileFormat.values()) {
            if (TrainingApplication.exportToFile(fileFormat) || TrainingApplication.exportViaEmail(fileFormat)) {
                startFullExportProcess(fileBaseName, fileFormat);
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
        startFullExportProcess(fileBaseName, fileFormat);

        // TODO: Rethink from here on.
        // dropbox: either waiting or unwanted (retries are not yet set)
        if (TrainingApplication.uploadToDropbox()) {
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.WAITING.name());
        } else {
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.UNWANTED.name());
        }
        mRepository.updateExportStatus(values, fileBaseName, ExportType.DROPBOX, fileFormat);

        broadcastExportStatusChanged(mContext);
    }

    /***********************************************************************************************
     * non-public stuff
     **********************************************************************************************/

    private synchronized void startFullExportProcess(String fileBaseName, FileFormat fileFormat) {
        if (DEBUG) Log.d(TAG, "startFullExportProcess for " + fileBaseName + ", format: " + fileFormat);

        try {
            // work request for exporting to file
            ExportInfo fileExportInfo = new ExportInfo(fileBaseName, fileFormat, ExportType.FILE);
            OneTimeWorkRequest fileCreationWork = createWorkRequest(fileExportInfo);

            // update the export status
            updateStatus(fileExportInfo, ExportStatus.PROCESSING, null);

            // create a empty list for the upload requests
            List<OneTimeWorkRequest> uploadWorks = new ArrayList<>();

            // Dropbox-Upload (when requested)
            if (TrainingApplication.uploadToDropbox()) {
                ExportInfo dropboxExportInfo = new ExportInfo(fileBaseName, fileFormat, ExportType.DROPBOX);
                uploadWorks.add(createWorkRequest(dropboxExportInfo));
                updateStatus(dropboxExportInfo, ExportStatus.WAITING, null); // set state ot WAITING
            }

            // Community-Upload, (when requested)
            if (TrainingApplication.uploadToCommunity(fileFormat)) {
                ExportInfo communityExportInfo = new ExportInfo(fileBaseName, fileFormat, ExportType.COMMUNITY);
                uploadWorks.add(createWorkRequest(communityExportInfo));
                updateStatus(communityExportInfo, ExportStatus.WAITING, null); // set state ot WAITING
            }

            // create the queue and start.
            if (uploadWorks.isEmpty()) {
                // OK, no uploads just export to file
                WorkManager.getInstance(mContext)
                        .beginWith(fileCreationWork)
                        .enqueue();
            } else {
                // first: export to file, then do the uploads
                WorkManager.getInstance(mContext)
                        .beginWith(fileCreationWork)
                        .then(uploadWorks)
                        .enqueue();
            }

            // now, that everything is scheduled, we send an broadcast.
            broadcastExportStatusChanged(mContext);

        } catch (JSONException e) {
            Log.e(TAG, "Could not create WorkRequest due to JSONException", e);

            ContentValues values = new ContentValues();
            values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.FINISHED_FAILED.name());
            values.put(ExportStatusDbHelper.ANSWER, "Interner Fehler bei Job-Erstellung");  // TODO: Text
            mRepository.updateExportStatus(values, fileBaseName, null, fileFormat);   // note that exportType is set to null to update all.
            broadcastExportStatusChanged(mContext);
        }
    }

    private void updateStatus(ExportInfo info, ExportStatus status, String answer) {
        ContentValues values = new ContentValues();
        values.put(ExportStatusDbHelper.EXPORT_STATUS, status.name());
        if (answer != null) {
            values.put(ExportStatusDbHelper.ANSWER, answer);
        }
        mRepository.updateExportStatus(values, info);
    }

    private OneTimeWorkRequest createWorkRequest(ExportInfo exportInfo) throws JSONException {

        Data inputData = new Data.Builder()
                .putString(ExportAndUploadWorker.KEY_EXPORT_INFO, exportInfo.toJson())
                .build();


        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(exportInfo.getExportType() == ExportType.FILE ? NetworkType.NOT_REQUIRED : NetworkType.CONNECTED)
                .build();

        return new OneTimeWorkRequest.Builder(ExportAndUploadWorker.class)
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(exportInfo.toString())
                .build();
    }

    private synchronized void exportingFailed(@NonNull ExportInfo exportInfo, String answer) {
        //  update the DB accordingly
        ContentValues values = new ContentValues();

        values.put(ExportStatusDbHelper.EXPORT_STATUS, ExportStatus.FINISHED_FAILED.name());
        values.put(ExportStatusDbHelper.ANSWER, answer);

        mRepository.updateExportStatus(values, exportInfo);

        broadcastExportStatusChanged(mContext);
    }


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
