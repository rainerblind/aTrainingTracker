// In a new file: UploadWorker.java
package com.atrainingtracker.trainingtracker.exporter;

import static com.atrainingtracker.trainingtracker.exporter.BaseExporter.cExportManager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONException;

import java.io.IOException;
import java.text.ParseException;

public class UploadWorker extends Worker {
    private static final String TAG = "UploadWorker";
    private static final boolean DEBUG = com.atrainingtracker.trainingtracker.TrainingApplication.getDebug(true);

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 1. Retrieve the parameters passed to the worker
        Data inputData = getInputData();
        String exportInfoJson = inputData.getString("EXPORT_INFO_JSON");

        if (exportInfoJson == null) {
            Log.e(TAG, "Cannot perform export: ExportInfo was not provided.");
            return Result.failure(); // A non-retriable failure
        }

        try {
            ExportInfo exportInfo = ExportInfo.fromJson(exportInfoJson);
            Log.d(TAG, "Starting export for: " + exportInfo.toString());

            try {

                BaseExporter exporter = ExportManager.getExporter(getApplicationContext(), exportInfo);
                ExportManager exportManager = new ExportManager(getApplicationContext(), TAG);

                // 4. Execute the actual export logic
                exportManager.exportingStarted(exportInfo);                                        // inform the manager
                BaseExporter.ExportResult result = exporter.doExport(exportInfo);

                if (result.success()) {
                    if (DEBUG) Log.i(TAG, "Export successful: " + result.answer());

                    exportManager.exportingFinished(exportInfo, true, result.answer());  // inform the manager
                    exportManager.onFinished(TAG);

                    return Result.success();
                } else {
                    if (DEBUG) Log.w(TAG, "Export failed, will retry later. Reason: " + result.answer());

                    exportManager.exportingFinished(exportInfo, false, result.answer());  // inform the manager
                    if (exportManager.getRetries(exportInfo) > 0) {                               // and aks him for one more chane
                        exportManager.onFinished(TAG);
                        return Result.retry(); // WorkManager will retry based on backoff policy
                    } else {
                        exportManager.onFinished(TAG);
                        return Result.failure(); // OK, we finally give up.
                    }
                }

            } catch (IOException | ParseException | InterruptedException | IllegalArgumentException e) {
                Log.e(TAG, "An exception occurred during export. Retrying.", e);

                ExportManager exportManager = new ExportManager(getApplicationContext(), TAG);
                exportManager.exportingFinished(exportInfo, false, e.getMessage());  // inform the manager
                if (exportManager.getRetries(exportInfo) > 0) {                              // and ask him for one more chance
                    exportManager.onFinished(TAG);
                    return Result.retry(); // WorkManager will retry based on backoff policy
                }
                else {
                    exportManager.onFinished(TAG);
                    return Result.failure(); // OK, we finally give up.
                }
            }
        } catch (JSONException e) {
            return Result.failure();
        }
    }
}
