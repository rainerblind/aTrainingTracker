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

package com.atrainingtracker.trainingtracker.exporter;


import android.content.ContentValues;
import android.content.Context;
import android.util.Log;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.atrainingtracker.trainingtracker.exporter.db.ExportStatusDatabaseManager;
import com.atrainingtracker.trainingtracker.ui.components.export.ExportNotificationManager;

import org.json.JSONException;

public class ExportWorker extends Worker  {
    private static final String TAG = "ExportAndUploadWorker";
    private static final boolean DEBUG = com.atrainingtracker.trainingtracker.TrainingApplication.getDebug(true);

    public static final String KEY_EXPORT_INFO = "export-info";
    private ExportInfo mExportInfo;
    private BaseExporter mExporter;

    private final ExportNotificationManager mExportNotificationManager;
    private final ExportStatusDatabaseManager repository = ExportStatusDatabaseManager.getInstance(getApplicationContext());
    private final Context mContext;

    public ExportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mExportNotificationManager = ExportNotificationManager.Companion.getInstance(context);
    }

    @SuppressLint("MissingPermission")
    @NonNull
    @Override
    public Result doWork() {
        // Retrieve the parameters passed to the worker
        Data inputData = getInputData();
        String exportInfoJson = inputData.getString(KEY_EXPORT_INFO);

        if (exportInfoJson == null) {
            Log.e(TAG, "Cannot perform export: ExportInfo was not provided.");
            return Result.failure(); // A non-retriable failure
        }

        // the ExportInfo
        try {
            mExportInfo = ExportInfo.fromJson(exportInfoJson);
        } catch (JSONException e) {
            return Result.failure();
        }

        // now we can get the Exporter
        mExporter = ExportManager.getExporter(getApplicationContext(), mExportInfo);

        // inform others (to trigger notifications and update the export DB)
        informOthersStarted();

        // and start the export
        BaseExporter.ExportResult result = mExporter.export(mExportInfo);

        // done :)
        if (result.success()) {
            informOthersSuccess(result.answer());
            return Result.success();
        } else {
            informOthersFailed(result.answer());
            return result.shallRetry() ? Result.retry() : Result.failure();
        }
    }


    // some helpers to inform the ExportNotificationManager and the ExportStatusRepository
    private void informOthersStarted() {
        if (DEBUG) Log.i(TAG, "Export started: " + mExportInfo.toString());

        updateStatus(ExportStatus.PROCESSING, "Export is being prepared...");  // TODO: Text?
        mExportNotificationManager.updateNotification(mExportInfo, false);
    }

    private void informOthersSuccess(String answer) {
        if (DEBUG) Log.i(TAG, "Export successful: " + mExportInfo.toString() + " Answer: " + answer);

        updateStatus(ExportStatus.FINISHED_SUCCESS, answer);
        mExportNotificationManager.updateNotification(mExportInfo, true);
    }

    private void informOthersFailed(String answer) {
        if (DEBUG) Log.i(TAG, "Export failed: " + mExportInfo.toString() + " Answer: " + answer);

        updateStatus(ExportStatus.FINISHED_FAILED, answer);
        mExportNotificationManager.updateNotification(mExportInfo, true);
    }



    /**
     * Updates the status of the current export job in the database and notifies the UI.
     * This method is the single point of truth for state changes of this worker.
     *
     * @param status The new status of the export (e.g., PROCESSING, FINISHED_SUCCESS).
     * @param answer A descriptive message about the result (e.g., "Upload successful" or an error message). Can be null.
     */
    private void updateStatus(ExportStatus status, @Nullable String answer) {

        // create a ContentValues object to hold the new data.
        ContentValues values = new ContentValues();
        values.put(ExportStatusDatabaseManager.EXPORT_STATUS, status.name());

        if (answer != null) {
            values.put(ExportStatusDatabaseManager.ANSWER, answer);
        }

        // delegate the database update to the repository.
        repository.updateExportStatus(values, mExportInfo);

        // Finally, send a broadcast to inform the UI that it needs to refresh.
        ExportStatusChangedBroadcaster.broadcastExportStatusChanged(getApplicationContext());
    }

}
