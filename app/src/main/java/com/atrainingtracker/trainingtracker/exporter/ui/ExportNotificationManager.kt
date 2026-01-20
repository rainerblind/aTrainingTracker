package com.atrainingtracker.trainingtracker.exporter.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;
import com.atrainingtracker.trainingtracker.exporter.ExportInfo;
import com.atrainingtracker.trainingtracker.exporter.ExportType;
import com.atrainingtracker.trainingtracker.exporter.FileFormat;


import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class ExportNotificationManager {
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final String TAG = "ExportNotificationManager";
    private static ExportNotificationManager sInstance;

    private final Context mContext;
    private final NotificationManagerCompat mNotificationManager;
    private final PendingIntent mPendingIntentStartWorkoutListActivity;

    // workoutName -> ExportType -> FileFormat
    private final Map<String, Map<ExportType, Set<FileFormat>>> mActiveExports = new ConcurrentHashMap<>();

    private final ExportStatusUIDataProvider mUiDataProvider;

    // TODO: Stelle sicher, dass die `getInstance`-Methode thread-safe ist (z.B. mit `volatile` und double-checked locking)
    protected ExportNotificationManager(@NonNull Context context) {
        this.mContext = context.getApplicationContext();
        this.mNotificationManager = NotificationManagerCompat.from(mContext);
        this.mUiDataProvider = new ExportStatusUIDataProvider(mContext);
        mPendingIntentStartWorkoutListActivity = createPendingIntentStartWorkoutListActivity();
    }

    public static synchronized ExportNotificationManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new ExportNotificationManager(context.getApplicationContext());
        }
        return sInstance;
    }

    /**********************************************************************************************/
    /* public API: just one simple method to update the notification and one to get the active exports
    /**********************************************************************************************/

    public synchronized void updateNotification(ExportInfo exportInfo, Boolean isFinished) {
        if (DEBUG) Log.i(TAG, "\n-------------------------------------------------------------");
        if (DEBUG) Log.i(TAG, "updateNotification: " + exportInfo.toString() + " isFinished=" + isFinished);

        updateExportStatus(exportInfo, isFinished);
        showNotification(exportInfo);
    }

    public synchronized Map<String, Map<ExportType, Set<FileFormat>>> getActiveExports() {
        return mActiveExports;
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

    /***********************************************************************************************
     * Notification stuff
     **********************************************************************************************/

    @SuppressLint("MissingPermission")
    public void showNotification(ExportInfo exportInfo) {
        if (isMissingPermission()) return;

        String fileBaseName = exportInfo.getFileBaseName();
        String title = mContext.getString(R.string.export_notification__title, fileBaseName);

        boolean isStillRunning = false;
        Map<ExportType, Set<FileFormat>> exportTypeMap = mActiveExports.get(fileBaseName);
        if (exportTypeMap != null) {
            // check whether any export is still running
            isStillRunning = exportTypeMap.values().stream().anyMatch(set -> !set.isEmpty());
        }

        RemoteViews expandedView = new RemoteViews(mContext.getPackageName(), R.layout.export_notification__expanded);

        List<ExportType> orderedTypes = List.of(ExportType.FILE, ExportType.DROPBOX, ExportType.COMMUNITY);
        for (ExportType type : orderedTypes) {
            RemoteViews groupView = createGroupViewForExportType(fileBaseName, type);
            if (groupView != null) {
                expandedView.addView(R.id.notification_main_container, groupView);
            }
        }

        String contentText = getContentText(fileBaseName);

        Notification notification = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)       // Fallback für Wear OS etc.
                .setContentText(contentText)  // Fallback für Wear OS etc.
                // WICHTIG: Dieser Style sorgt dafür, dass Android den Standard-Header (App-Icon, Name, Zeit)
                // um unser Custom Layout herumzeichnet.
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(expandedView)
                .setOngoing(isStillRunning)
                .setAutoCancel(!isStillRunning)
                .setContentIntent(mPendingIntentStartWorkoutListActivity)
                .build();

        mNotificationManager.notify(generateNotificationId(exportInfo), notification);
    }

    /***********************************************************************************************
     * method to create one group
     **********************************************************************************************/

    private RemoteViews createGroupViewForExportType(String fileBaseName, ExportType exportType) {
        // get the data form the UI provider
        ExportStatusGroupData data = mUiDataProvider.createGroupData(fileBaseName, exportType);

        // when there is no data, we simply return null
        if (!data.getHasContent()) {
            return null;
        }

        // create the RemoteViews and fill it with the data
        RemoteViews groupView = new RemoteViews(mContext.getPackageName(), R.layout.export_notification__group);
        groupView.setTextViewText(R.id.group_title, data.getGroupTitle());

        updateRemoteViewLine(groupView, R.id.line_running, data.getRunningLine());
        updateRemoteViewLine(groupView, R.id.line_succeeded, data.getSucceededLine());
        updateRemoteViewLine(groupView, R.id.line_failed, data.getFailedLine());

        return groupView;
    }

    // simple helper to keep the code clean.
    private void updateRemoteViewLine(RemoteViews remoteViews, int viewId, @Nullable String text) {
        if (text != null) {
            remoteViews.setTextViewText(viewId, text);
            remoteViews.setViewVisibility(viewId, View.VISIBLE);
        } else {
            remoteViews.setViewVisibility(viewId, View.GONE);
        }
    }

    /**********************************************************************************************
    * short summary of the active Exports
     *********************************************************************************************/ 

    // creates the summary text when the notification is not extended
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


    /**********************************************************************************************/
    /* some more simple helpers
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