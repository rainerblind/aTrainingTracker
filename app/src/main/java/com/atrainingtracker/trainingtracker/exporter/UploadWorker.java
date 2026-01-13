package com.atrainingtracker.trainingtracker.exporter;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;

public class UploadWorker extends Worker {
    private static final String TAG = "UploadWorker";
    private static final boolean DEBUG = com.atrainingtracker.trainingtracker.TrainingApplication.getDebug(true);

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Retrieve the parameters passed to the worker
        Data inputData = getInputData();
        String exportInfoJson = inputData.getString("EXPORT_INFO_JSON");

        if (exportInfoJson == null) {
            Log.e(TAG, "Cannot perform export: ExportInfo was not provided.");
            return Result.failure(); // A non-retriable failure
        }

        ExportInfo exportInfo;
        try {
            exportInfo = ExportInfo.fromJson(exportInfoJson);
            Log.d(TAG, "Starting export for: " + exportInfo.toString());
        } catch (JSONException e) {
            return Result.failure();
        }

        BaseExporter exporter = ExportManager.getExporter(getApplicationContext(), exportInfo);
        BaseExporter.ExportResult result = exporter.export(exportInfo);

        if (result.success()) {
            if (DEBUG) Log.i(TAG, "Export successful: " + result.answer());
            return Result.success();
        } else {
            if (DEBUG) Log.w(TAG, "Export failed. Reason: " + result.answer());
            if (result.shallRetry()) {
                return Result.retry(); // WorkManager will retry based on backoff policy
            } else {
                return Result.failure(); // A non-retriable failure
            }
        }
    }
}
