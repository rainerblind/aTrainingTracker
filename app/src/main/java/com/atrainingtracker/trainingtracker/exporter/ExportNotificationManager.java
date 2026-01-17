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
    /* public API: just one simple method to update the notification
    /**********************************************************************************************/

    public synchronized void updateNotification(ExportInfo exportInfo, Boolean isFinished) {
        if (DEBUG) Log.i(TAG, "\n-------------------------------------------------------------");
        if (DEBUG) Log.i(TAG, "updateNotification: " + exportInfo.toString() + " isFinished=" + isFinished);

        updateExportStatus(exportInfo, isFinished);
        showNotification(exportInfo);
    }


    /***********************************************************************************************
     * private and protected stuff
     **********************************************************************************************/


    public synchronized void updateExportStatus(ExportInfo exportInfo, boolean isFinished) {
        // if (DEBUG) Log.i(TAG, "updateExportStatus: " + exportInfo.toString() + " isFinished=" + isFinished);
        if (DEBUG) logActiveExports(exportInfo.getFileBaseName());

        String fileBaseName = exportInfo.getFileBaseName();
        ExportType exportType = exportInfo.getExportType();
        FileFormat fileFormat = exportInfo.getFileFormat();

        // create the map and sets if not yet present
        Map<ExportType, Set<FileFormat>> exportTypeMap = mActiveExports.computeIfAbsent(fileBaseName, k -> new ConcurrentHashMap<>());
        Set<FileFormat> fileFormatSet = exportTypeMap.computeIfAbsent(exportType, k -> new HashSet<>());

        // remove or add
        if (isFinished) {
            Log.i(TAG, "updateExportStatus: removing fileFormat " + fileFormat);
            fileFormatSet.remove(fileFormat);
            // note that we must not remove the exportType when it is empty.
            // we keep this empty set to know that we did something for this export type.
        } else {
            fileFormatSet.add(fileFormat);
        }

        if (DEBUG) Log.i(TAG, ":");
        logActiveExports(fileBaseName);
    }

    @SuppressLint("MissingPermission")
    private void showNotification(ExportInfo exportInfo) {
        if (isMissingPermission()) return;

        String fileBaseName = exportInfo.getFileBaseName();
        String title = mContext.getString(R.string.export_notification__title, fileBaseName);
        String contentText = getContentText(fileBaseName);
        if (DEBUG) Log.i(TAG, "showNotification: " + title + " " + contentText);

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        inboxStyle.setBigContentTitle(title);

        // add a line for each export type
        Map<ExportType, Set<FileFormat>> exportTypeMap = mActiveExports.get(fileBaseName);
        if (exportTypeMap != null) {
            for (ExportType exportType : exportTypeMap.keySet()) {
                inboxStyle.addLine(getLineText(fileBaseName, exportType));
            }
        }

        Notification notification = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(contentText)
                .setStyle(inboxStyle)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(mPendingIntentStartWorkoutListActivity)
                .build();

        mNotificationManager.notify(generateNotificationId(exportInfo), notification);
    }

    private String getContentText(String fileBaseName) {
        if (DEBUG) Log.i(TAG, "getContentText: " + fileBaseName);

        if (mActiveExports.isEmpty()) {  // map is empty.  Should not happen but when in case of start
            return mContext.getString(R.string.export_notification__summary__started);
        } else {
            Map<ExportType, Set<FileFormat>> exportTypeMap = mActiveExports.get(fileBaseName);

            if (exportTypeMap != null && !exportTypeMap.isEmpty()) {
                String activeExportsList = exportTypeMap.entrySet().stream()
                        .filter(entry -> !entry.getValue().isEmpty())
                        .map(entry -> mContext.getString(entry.getKey().getUiId()))
                        .collect(Collectors.joining(", "));

                if (activeExportsList.isEmpty()) {
                    return mContext.getString(R.string.export_notification__summary__finished);
                } else {
                    return mContext.getString(R.string.export_notification__summary__in_progress_to, activeExportsList);
                }
            } else {
                return mContext.getString(R.string.export_notification__summary__started);
            }
        }
    }

    // returns a line for the notification
    private String getLineText(String fileBaseName, ExportType exportType) {
        if (DEBUG) Log.i(TAG, "getLineText: " + fileBaseName + " " + exportType);

        Map<ExportType, Set<FileFormat>> exportTypeMap = mActiveExports.get(fileBaseName);
        Set<FileFormat> fileFormatSet = exportTypeMap.get(exportType);

        if (fileFormatSet.isEmpty()) {  // set is empty -> no running jobs -> get the final answer from the db...

            return getResultLine(fileBaseName, exportType);
        }
        else {

            return getRunningLine(exportType, fileFormatSet, fileBaseName);
        }
    }

    // return list of running jobs as string
    private String getRunningLine(ExportType exportType, @NonNull Set<FileFormat> runningJobs, String fileBaseName) {
        if (DEBUG) Log.i(TAG, "getRunningLine: " + fileBaseName + " " + exportType);

        String action = mContext.getString(exportType.getAction().getIngId());
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
    private String getResultLine(String fileBaseName, ExportType exportType) {
        if (DEBUG) Log.i(TAG, "getResultLine: " + fileBaseName + " " + exportType);

        ExportStatusRepository repository = ExportStatusRepository.getInstance(mContext);
        ExportGroupStats stats = repository.getStatsForExportGroup(fileBaseName, exportType);
        int successCount = stats.successCount;
        int failedCount = stats.failureCount;
        int totalCount = stats.getTotalCount();

        String action = mContext.getString(exportType.getAction().getIngId());
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

    private void logActiveExports(String fileBaseName){
        Map<ExportType, Set<FileFormat>> exportTypeMap = mActiveExports.computeIfAbsent(fileBaseName, k -> new ConcurrentHashMap<>());

        Log.d(TAG, "vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv");
        Log.d(TAG, "Current state of mActiveExports:");
        if (mActiveExports.isEmpty()) {
            Log.d(TAG, "  -> Map is empty");
        } else {
            // Iteriere durch jeden Dateinamen (den äußeren Map-Key)
            for (Map.Entry<String, Map<ExportType, Set<FileFormat>>> fileEntry : mActiveExports.entrySet()) {
                Log.d(TAG, "  File: " + fileEntry.getKey());
                Map<ExportType, Set<FileFormat>> exportTypeMap2 = fileEntry.getValue();
                if (exportTypeMap.isEmpty()) {
                    Log.d(TAG, "    -> No active export types.");
                } else {
                    // Iteriere durch jeden Export-Typ (den inneren Map-Key)
                    for (Map.Entry<ExportType, Set<FileFormat>> typeEntry : exportTypeMap2.entrySet()) {
                        String formats = typeEntry.getValue().stream()
                                .map(FileFormat::name)
                                .collect(Collectors.joining(", "));
                        Log.d(TAG, "    - " + typeEntry.getKey().name() + ": [" + formats + "]");
                    }
                }
            }
        }
        Log.d(TAG, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
    }
}