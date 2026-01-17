package com.atrainingtracker.trainingtracker.exporter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;


import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class ExportNotificationManager {
    private static final boolean DEBUG = true;
    private static final String TAG = "ExportNotificationManager";
    private static ExportNotificationManager sInstance;

    private final Context mContext;
    private final NotificationManagerCompat mNotificationManager;
    private final PendingIntent mPendingIntentStartWorkoutListActivity;

    // workoutName -> ExportType -> FileFormat
    private final Map<String, Map<ExportType, Set<FileFormat>>> mActiveExports = new ConcurrentHashMap<>();

    protected ExportNotificationManager(@NonNull Context context) {
        this.mContext = context.getApplicationContext();
        this.mNotificationManager = NotificationManagerCompat.from(mContext);

        mPendingIntentStartWorkoutListActivity = createPendingIntentStartWorkoutListActivity();
    }

    public static synchronized ExportNotificationManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new ExportNotificationManager(context.getApplicationContext());
        }
        return sInstance;
    }

    /**********************************************************************************************/
    /* Öffentliche API - wird jetzt an die neue Methode delegiert
    /**********************************************************************************************/

    // TODO: simplify the interface...

    public void showInitialNotification(ExportInfo exportInfo, BaseExporter exporter) {
        updateExportStatus(exportInfo, false);
        showNotification(exportInfo, exporter);
    }

    public void showFinalNotification(ExportInfo exportInfo, BaseExporter exporter, String text, boolean success) {
        updateExportStatus(exportInfo, true);
        showNotification(exportInfo, exporter);
    }


    public synchronized void updateExportStatus(ExportInfo exportInfo, boolean isFinished) {

        String fileBaseName = exportInfo.getFileBaseName();
        ExportType exportType = exportInfo.getExportType();
        FileFormat fileFormat = exportInfo.getFileFormat();

        // create the map and sets if not yet present
        Map<ExportType, Set<FileFormat>> exportTypeMap = mActiveExports.computeIfAbsent(fileBaseName, k -> new ConcurrentHashMap<>());
        Set<FileFormat> fileFormatSet = exportTypeMap.computeIfAbsent(exportType, k -> new HashSet<>());

        // remove or add
        if (isFinished) {
            fileFormatSet.remove(fileFormat);
            if (fileFormatSet.isEmpty()) {          // then the set is empty, it was the last entry for this type
                exportTypeMap.remove(exportType);   // -> also remove the type
            }
        } else {
            fileFormatSet.add(fileFormat);
        }
    }

    @SuppressLint("MissingPermission")
    private void showNotification(ExportInfo exportInfo, BaseExporter exporter) {
        if (isMissingPermission()) return;

        String fileBaseName = exportInfo.getFileBaseName();
        String title = mContext.getString(R.string.export_notification_title, fileBaseName);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(title);

        // add a line for each export type
        for (ExportType exportType : ExportType.values()) {
            inboxStyle.addLine(getLineText(fileBaseName, exportType, exporter));  // gives us "Exporting ... to ..." or "Succesfully uploaded ..." or "Failed to upload ..."
        }

        Notification notification = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText("TODO: short summary") // TODO: better text
                .setStyle(inboxStyle)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(mPendingIntentStartWorkoutListActivity)
                .build();

        mNotificationManager.notify(generateNotificationId(exportInfo), notification);
    }

    // returns a line for the notification
    private String getLineText(String fileBaseName, ExportType exportType, BaseExporter exporter) {

        Map<ExportType, Set<FileFormat>> exportTypeMap = mActiveExports.computeIfAbsent(fileBaseName, k -> new ConcurrentHashMap<>());
        Set<FileFormat> fileFormatSet = exportTypeMap.computeIfAbsent(exportType, k -> new HashSet<>());

        if (fileFormatSet.isEmpty()) {  // set is empty -> no running jobs -> get the final answer from the db...
            return getResultLine(fileBaseName, exportType, exporter);
        }
        else {
            return getRunningLine(exportType, exporter, fileFormatSet);
        }
    }

    // return list of running jobs as string
    private String getRunningLine(ExportType exportType, BaseExporter exporter, @NonNull Set<FileFormat> runningJobs) {
        String action = mContext.getString(exporter.getAction().getIngId());
        String type = mContext.getString(exportType.getUiId());

        String formatList;
        if (!runningJobs.isEmpty()) {
            formatList = runningJobs.stream()
                    .map(FileFormat::name)
                    .collect(Collectors.joining(", "));
        } else {
            // should never ever happen, but just in case...
            if (DEBUG) {
                Log.e(TAG, "getRunningLine: runningJobs is empty");
                throw new IllegalStateException("getRunningLine: runningJobs is empty");
            } else {
                formatList = "";
            }
        }

        return mContext.getString(R.string.notification_exporting, action, formatList, type);
    }

    // return the number of successes and failures from the repository as string
    private String getResultLine(String fileBaseName, ExportType exportType, BaseExporter exporter) {
        ExportStatusRepository repository = ExportStatusRepository.getInstance(mContext);
        ExportGroupStats stats = repository.getStatsForExportGroup(fileBaseName, exportType);
        int successCount = stats.successCount;
        int failedCount = stats.failureCount;
        int totalCount = stats.getTotalCount();

        String action = mContext.getString(exporter.getAction().getIngId());
        String type = mContext.getString(exportType.getUiId());

        String resultLine;
        if (successCount == totalCount) {
            int export_string_id = R.plurals.exporting_success;
            resultLine = mContext.getResources().getQuantityString(
                    export_string_id,
                    totalCount,
                    action,
                    type,
                    successCount,
                    totalCount);
        } else {
            int export_string_id = R.plurals.exporting_failed;
            resultLine = mContext.getResources().getQuantityString(
                    export_string_id,
                    totalCount,
                    action,
                    type,
                    failedCount,
                    totalCount);
        }

        return resultLine;
    }


    /**********************************************************************************************/
    /* private helpers
    /**********************************************************************************************/

    private PendingIntent createPendingIntentStartWorkoutListActivity() {
        Bundle bundle = new Bundle();
        bundle.putString(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST.name());
        Intent newIntent = new Intent(mContext, MainActivityWithNavigation.class);
        newIntent.putExtras(bundle);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return PendingIntent.getActivity(mContext, 0, newIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean isMissingPermission() {
        return ActivityCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private int generateNotificationId(ExportInfo exportInfo) {
        return exportInfo.getFileBaseName().hashCode();
    }
}