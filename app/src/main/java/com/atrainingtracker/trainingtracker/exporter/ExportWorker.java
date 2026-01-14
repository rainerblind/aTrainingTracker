package com.atrainingtracker.trainingtracker.exporter;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
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

public class ExportWorker extends Worker implements IExportProgressListener {
    private static final String TAG = "UploadWorker";
    private static final boolean DEBUG = com.atrainingtracker.trainingtracker.TrainingApplication.getDebug(true);


    private NotificationManagerCompat mNotificationManager;
    private NotificationCompat.Builder mNotificationBuilder;
    private ExportInfo mExportInfo;
    private Context mContext;
    private BaseExporter mExporter;

    public ExportWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        mNotificationManager = NotificationManagerCompat.from(context);
        mContext = context;
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

        // the ExportInfo
        try {
            mExportInfo = ExportInfo.fromJson(exportInfoJson);
            Log.d(TAG, "Starting export for: " + mExportInfo.toString());
        } catch (JSONException e) {
            return Result.failure();
        }


        // now we can get the Exporter
        mExporter = ExportManager.getExporter(getApplicationContext(), mExportInfo);

        // inform the user that the export is started
        showInitialNotification();

        // and start the export
        BaseExporter.ExportResult result = mExporter.export(mExportInfo, this);

        if (result.success()) {
            if (DEBUG) Log.i(TAG, "Export successful: " + result.answer());
            showFinalNotification(getExportTitle(mExportInfo), getPositiveAnswer(mExportInfo));// TODO: Notification
            return Result.success();
        } else {
            if (DEBUG) Log.w(TAG, "Export failed. Reason: " + result.answer());
            if (result.shallRetry()) {
                showFinalNotification(getExportTitle(mExportInfo), result.answer());// TODO: Notification
                return Result.retry(); // WorkManager will retry based on backoff policy
            } else {
                showFinalNotification(getExportTitle(mExportInfo), result.answer());// TODO: Notification
                return Result.failure(); // A non-retriable failure
            }
        }
    }

    // Implementation of the callback
    @Override
    public void onProgress(int max, int current) {
        if ((current % (10 * 60)) == 0) {
            updateNotification(getExportMessage(mExportInfo), true, false, max, current);
        }
    }


    // helpers for the notifications

    PendingIntent getMainActivityPendingIntent() {
        Bundle bundle = new Bundle();
        bundle.putString(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST.name());
        Intent newIntent = new Intent(mContext, MainActivityWithNavigation.class);
        newIntent.putExtras(bundle);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(mContext, 0, newIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    @Nullable
    private void showInitialNotification() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // configure the notification
        mNotificationBuilder = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_save_black_48dp))
                .setContentTitle(mContext.getString(R.string.TrainingTracker))
                .setContentText(mContext.getString(R.string.exporting))
                .setContentIntent(getMainActivityPendingIntent())
                .setOngoing(true);
        mNotificationManager.notify(TrainingApplication.EXPORT_PROGRESS_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void updateNotification(String text, boolean ongoing, boolean indeterminate, int max, int current) {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        mNotificationBuilder.setContentText(text)
                .setOngoing(ongoing)
                .setProgress(max, current, indeterminate);
        mNotificationManager.notify(TrainingApplication.EXPORT_PROGRESS_NOTIFICATION_ID, mNotificationBuilder.build());
    }

    private void updateNotification(String text, boolean ongoing, boolean indeterminate) {
        updateNotification(text, ongoing, indeterminate, 0, 0);
    }

    private void showFinalNotification(String title, String text) {
        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Notification finalNotification = new NotificationCompat.Builder(getApplicationContext(), TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(getMainActivityPendingIntent())
                .setOngoing(false)
                .build();
        mNotificationManager.notify(TrainingApplication.EXPORT_PROGRESS_NOTIFICATION_ID, finalNotification);
    }

    @NonNull
    protected String getExportTitle(@NonNull ExportInfo exportInfo) {
        return mContext.getString(R.string.notification_title,
                mContext.getString(mExporter.getAction().getIngId()),
                mContext.getString(exportInfo.getExportType().getUiId()));
    }

    @NonNull
    protected String getExportMessage(@NonNull ExportInfo exportInfo) {
        String workoutName = exportInfo.getFileBaseName();
        FileFormat format = exportInfo.getFileFormat();
        ExportType type = exportInfo.getExportType();
        int notification_format_id = switch (type) {
            case FILE -> R.string.notification_export_file;
            case DROPBOX -> R.string.notification_export_dropbox;
            case COMMUNITY -> R.string.notification_export_community;
        };

        return mContext.getString(notification_format_id,
                mContext.getString(mExporter.getAction().getIngId()),
                mContext.getString(format.getUiNameId()),
                workoutName);
    }

    // copied code from getExportMessage
    @NonNull
    protected String getPositiveAnswer(@NonNull ExportInfo exportInfo) {
        String workoutName = exportInfo.getFileBaseName();
        FileFormat format = exportInfo.getFileFormat();
        ExportType type = exportInfo.getExportType();
        int notification_format_id = switch (type) {
            case FILE -> R.string.notification_finished_file;
            case DROPBOX -> R.string.notification_finished_dropbox;
            case COMMUNITY -> R.string.notification_finished_community;
        };

        return mContext.getString(notification_format_id,
                mContext.getString(mExporter.getAction().getPastId()),
                mContext.getString(format.getUiNameId()),
                workoutName);
    }

}
