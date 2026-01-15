package com.atrainingtracker.trainingtracker.exporter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;

import org.json.JSONException;

public class ExportAndUploadWorker extends Worker implements IExportProgressListener {
    private static final String TAG = "UploadWorker";
    private static final boolean DEBUG = com.atrainingtracker.trainingtracker.TrainingApplication.getDebug(true);

    public static final String KEY_EXPORT_INFO = "export-info";
    private ExportInfo mExportInfo;
    private BaseExporter mExporter;

    private final ExportNotificationManager mExportNotificationManager;
    private final ExportStatusRepository repository = ExportStatusRepository.getInstance(getApplicationContext());
    private final Context mContext;

    public ExportAndUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mContext = context;
        mExportNotificationManager = ExportNotificationManager.getInstance(context);
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
            Log.d(TAG, "Starting export for: " + mExportInfo.toString());
        } catch (JSONException e) {
            return Result.failure();
        }

        // now we can get the Exporter
        mExporter = ExportManager.getExporter(getApplicationContext(), mExportInfo);

        // inform the user that the export is started and update the DB.
        mExportNotificationManager.showInitialNotification(mExportInfo, mExporter);
        updateStatus(ExportStatus.PROCESSING, "Export is being prepared...");  // TODO: Text?

        // and start the export
        BaseExporter.ExportResult result = mExporter.export(mExportInfo, this);

        if (result.success()) {
            // for the logging, we use the answer from the result
            if (DEBUG) Log.i(TAG, "Export successful: " + result.answer());

            // for the user, we use the standard positive answer
            String answer = mExportInfo.getPositiveAnswer(mContext, mExporter);
            updateStatus(ExportStatus.FINISHED_SUCCESS, answer);
            mExportNotificationManager.showFinalNotification(mExportInfo, mExporter, answer);

            return Result.success();
        } else {
            String answer = result.answer();

            if (DEBUG) Log.w(TAG, "Export failed. Reason: " + answer);

            updateStatus(ExportStatus.FINISHED_FAILED, answer);
            mExportNotificationManager.showFinalNotification(mExportInfo, mExporter, answer);

            return result.shallRetry() ? Result.retry() : Result.failure();
        }
    }

    // Implementation of the callback
    @SuppressLint("MissingPermission")
    @Override
    public void onProgress(int max, int current) {
        if ((current % (10 * 60)) == 0) {
            mExportNotificationManager.updateNotification(mExportInfo, mExporter, true, max, current);
        }
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
        values.put(ExportStatusRepository.ExportStatusDbHelper.EXPORT_STATUS, status.name());

        if (answer != null) {
            values.put(ExportStatusRepository.ExportStatusDbHelper.ANSWER, answer);
        }

        // delegate the database update to the repository.
        repository.updateExportStatus(values, mExportInfo);

        // Finally, send a broadcast to inform the UI that it needs to refresh.
        ExportStatusChangedBroadcaster.broadcastExportStatusChanged(getApplicationContext());
    }

}
