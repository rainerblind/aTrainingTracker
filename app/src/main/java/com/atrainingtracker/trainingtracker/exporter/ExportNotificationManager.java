package com.atrainingtracker.trainingtracker.exporter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;


public class ExportNotificationManager {
    private static final boolean DEBUG = true;
    private static final String TAG = "ExportNotificationManager";
    private static ExportNotificationManager sInstance;

    private final Context mContext;
    private final NotificationManagerCompat mNotificationManager;
    private final PendingIntent mPendingIntentStartWorkoutListActivity;


    private final Map<String, GroupState> mGroupStates = new ConcurrentHashMap<>();
    private final Map<String, Set<Integer>> mActiveNotifications = new ConcurrentHashMap<>();

    private static class GroupState {
        int jobsStarted = 0;
        int jobsFinished = 0;
        int jobsSucceeded = 0;

        public GroupState() {}

        public synchronized void logJobStarted() {
            jobsStarted++;
        }

        public synchronized void logJobFinished(boolean success) {
            jobsFinished++;
            if (success) {
                jobsSucceeded++;
            }
        }

        public synchronized boolean isFinished() {
            return jobsStarted > 0 && jobsSucceeded >= jobsStarted;
        }
    }


    protected ExportNotificationManager(@NonNull Context context) {
        this.mContext = context.getApplicationContext();
        this.mNotificationManager = NotificationManagerCompat.from(mContext);

        // create the mPendingIntentStartWorkoutListActivity
        Bundle bundle = new Bundle();
        bundle.putString(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST.name());
        Intent newIntent = new Intent(mContext, MainActivityWithNavigation.class);
        newIntent.putExtras(bundle);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        mPendingIntentStartWorkoutListActivity = PendingIntent.getActivity(mContext, 0, newIntent, PendingIntent.FLAG_IMMUTABLE);
    }

    public static synchronized ExportNotificationManager getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new ExportNotificationManager(context.getApplicationContext());
        }
        return sInstance;
    }


    @SuppressLint("MissingPermission")
    public synchronized void showInitialNotification(ExportInfo exportInfo, BaseExporter exporter) {  // TODO: rename
        if (isMissingPermission()) return;

        String groupKey = generateGroupKey(exportInfo);
        int notificationId = generateNotificationId(exportInfo);
        int summaryNotificationId = generateSummaryNotificationId(exportInfo);

        Set<Integer> activeIds = mActiveNotifications.computeIfAbsent(groupKey, k -> new HashSet<>());
        if (!activeIds.contains(notificationId)) {
            activeIds.add(notificationId);
            GroupState state = mGroupStates.computeIfAbsent(groupKey, k -> new GroupState());
            state.logJobStarted();
        }

        String title = exportInfo.getExportTitle(mContext, exporter);
        String text = exportInfo.getExportMessage(mContext, exporter);
        String summaryText = "Exporte für " + exportInfo.getExportType();  // TODO: better...


        // configure the notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_save_black_48dp))
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(mPendingIntentStartWorkoutListActivity)
                .setOngoing(true)
                .setGroup(groupKey);

        // configure the summary notification
        Notification summaryNotification = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(exportInfo.getExportTitle(mContext, exporter))
                .setContentText(summaryText)
//                .setStyle(new NotificationCompat.InboxStyle() // InboxStyle ist ideal für Zusammenfassungen
//                        .setSummaryText("Exporte für " + exportInfo.getFileBaseName())) // z.B. "Exporte für 2024-01-15..."
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setOngoing(true)
                .build();

        mNotificationManager.notify(notificationId, notificationBuilder.build());
        mNotificationManager.notify(summaryNotificationId, summaryNotification);
    }



    @SuppressLint("MissingPermission")
    public synchronized void updateNotification(ExportInfo exportInfo, BaseExporter exporter, boolean indeterminate, int max, int current) {
        if (isMissingPermission()) return;

        String groupID = generateGroupKey(exportInfo);
        int notificationId = generateNotificationId(exportInfo);

        // configure the notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_save_black_48dp))
                .setContentTitle(exportInfo.getExportTitle(mContext, exporter))
                .setContentText(exportInfo.getExportMessage(mContext, exporter))
                .setContentIntent(mPendingIntentStartWorkoutListActivity)
                .setOngoing(true)
                .setGroup(groupID)
                .setProgress(max, current, indeterminate);
        mNotificationManager.notify(notificationId, notificationBuilder.build());
    }


    @SuppressLint("MissingPermission")
    public synchronized void showFinalNotification(ExportInfo exportInfo, BaseExporter exporter, String text, boolean success) {
        if (isMissingPermission()) return;

        String groupKey = generateGroupKey(exportInfo);
        int notificationId = generateNotificationId(exportInfo);
        int summaryNotificationId = generateSummaryNotificationId(exportInfo);

        // simply cancel the notification since it is no longer necessary.
        mNotificationManager.cancel(notificationId);

        GroupState state = mGroupStates.get(groupKey);
        if (state == null) {
            return;
        }

        state.logJobFinished(success);

        if (state.isFinished()) {
            mGroupStates.remove(groupKey);
            mActiveNotifications.remove(groupKey);
            mNotificationManager.cancel(summaryNotificationId);

            showFinalSummary(exportInfo, exporter, state);

        } else {
            String summaryTitle = "Exporting/Uploading finished";  // TODO
            String summaryText = String.format("%d/%d fertig", state.jobsSucceeded, state.jobsStarted);

            Notification summaryNotification = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                    .setSmallIcon(R.drawable.logo)
                    .setContentTitle(summaryTitle)
                    .setContentText(summaryText)
                    .setGroup(groupKey)
                    .setGroupSummary(true)
                    .setOngoing(true)
                    .build();
            mNotificationManager.notify(summaryNotificationId, summaryNotification);
        }
    }

    @SuppressLint("MissingPermission")
    private void showFinalSummary(ExportInfo exportInfo, BaseExporter exporter, GroupState finalState) {
        if (isMissingPermission()) return;

        int finalSummaryId = generateSummaryNotificationId(exportInfo);
        String title = exportInfo.getExportTitle(mContext, exporter);

        String action = mContext.getString(exporter.getAction().getIngId());
        String type = mContext.getString(exportInfo.getExportType().getUiId());
        int export_string_id = (finalState.jobsSucceeded == finalState.jobsStarted)
                ? R.plurals.exporting_success
                : R.plurals.exporting_failed;

        String text = mContext.getResources().getQuantityString(
                export_string_id,
                finalState.jobsSucceeded,
                action,
                type,
                finalState.jobsSucceeded,
                finalState.jobsSucceeded);

        Notification finalSummaryNotification = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(mPendingIntentStartWorkoutListActivity)
                .setAutoCancel(true)
                .setOngoing(false)
                .build();

        mNotificationManager.notify(finalSummaryId, finalSummaryNotification);
    }


    /**********************************************************************************************/
    /* private helpers
    /**********************************************************************************************/

    private boolean isMissingPermission() {
        return ActivityCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED;
    }

    private String generateGroupKey(ExportInfo info) {
        return info.getFileBaseName() + "::" + info.getExportType().name();     // group by ExportType
        // return info.getFileBaseName() + "::" + info.getFileFormat().name();     // group by FileFormat
    }
    private int generateNotificationId(ExportInfo info) {
        String uniqueString = info.getFileBaseName() + info.getExportType().name() + info.getFileFormat().name();
        return uniqueString.hashCode();
    }
    private int generateSummaryNotificationId(ExportInfo info) {
        return generateGroupKey(info).hashCode();
    }

}