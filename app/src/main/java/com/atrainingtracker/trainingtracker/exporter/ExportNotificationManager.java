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


public class ExportNotificationManager {
    private static final boolean DEBUG = true;
    private static final String TAG = "ExportNotificationManager";
    private static ExportNotificationManager sInstance;

    private final Context mContext;
    private final NotificationManagerCompat mNotificationManager;
    private final PendingIntent mPendingIntentStartWorkoutListActivity;
    private final boolean mShowOnlySummary = true;


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

        public synchronized int getJobsRunning() {
            return jobsStarted - jobsFinished;
        }
    }


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

        if (!mShowOnlySummary) {
            // configure the notification
            NotificationCompat.Builder notificationBuilder = getDefaultNotifictioBuilder(exportInfo, exporter)
                    .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_save_black_48dp))
                    .setOngoing(true)
                    .setGroup(groupKey)
                    .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY);
            mNotificationManager.notify(notificationId, notificationBuilder.build());
        }

        // configure the summary notification
        Notification summaryNotification = getDefaultSummaryNotifictioBuilder(exportInfo, exporter)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .setOngoing(true)
                .build();
        mNotificationManager.notify(summaryNotificationId, summaryNotification);
    }



    @SuppressLint("MissingPermission")
    public synchronized void updateNotification(ExportInfo exportInfo, BaseExporter exporter, boolean indeterminate, int max, int current) {
        if (isMissingPermission()) return;
        if (mShowOnlySummary) return;

        String groupID = generateGroupKey(exportInfo);
        int notificationId = generateNotificationId(exportInfo);

        // configure the notification
        NotificationCompat.Builder notificationBuilder = getDefaultNotifictioBuilder(exportInfo, exporter)
                .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_save_black_48dp))
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
            // remove what has to be removed
            mGroupStates.remove(groupKey);
            mActiveNotifications.remove(groupKey);
            mNotificationManager.cancel(summaryNotificationId);

            // show final summary notification
            showFinalSummary(exportInfo, exporter);

        } else {
            // other exports/uploads are still running -> update summaryNotification
            Notification summaryNotification = getDefaultSummaryNotifictioBuilder(exportInfo, exporter)
                    .setGroup(groupKey)
                    .setGroupSummary(true)
                    .setOngoing(true)
                    .build();
            mNotificationManager.notify(summaryNotificationId, summaryNotification);
        }
    }

    @SuppressLint("MissingPermission")
    private void showFinalSummary(ExportInfo exportInfo, BaseExporter exporter) {
        if (isMissingPermission()) return;

        // get the number of successes and failures from the repository
        ExportStatusRepository repository = ExportStatusRepository.getInstance(mContext);
        ExportGroupStats stats = repository.getStatsForExportGroup(exportInfo.getFileBaseName(), exportInfo.getExportType());
        int successCount = stats.successCount;
        int failedCount = stats.failureCount;
        int totalCount = stats.getTotalCount();

        int finalSummaryId = generateSummaryNotificationId(exportInfo);
        String title = exportInfo.getExportTitle(mContext, exporter);

        String action = mContext.getString(exporter.getAction().getIngId());
        String type = mContext.getString(exportInfo.getExportType().getUiId());

        String text;
        if (successCount == totalCount) {
            int export_string_id = R.plurals.exporting_success;
            text = mContext.getResources().getQuantityString(
                    export_string_id,
                    totalCount,
                    action,
                    type,
                    successCount,
                    totalCount);
        } else {
            int export_string_id = R.plurals.exporting_failed;
            text = mContext.getResources().getQuantityString(
                    export_string_id,
                    totalCount,
                    action,
                    type,
                    failedCount,
                    totalCount);
        }

        Notification finalSummaryNotification = getDefaultNotifictioBuilder(title, text)
                .setAutoCancel(true)
                .setOngoing(false)
                .build();

        mNotificationManager.notify(finalSummaryId, finalSummaryNotification);
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

    private NotificationCompat.Builder getDefaultNotifictioBuilder(ExportInfo exportInfo, BaseExporter exporter) {
        String text = getDefaultText(exportInfo, exporter);
        String title = getDefaultTitle(exportInfo, exporter);

        return getDefaultNotifictioBuilder(title, text);
    }

    private NotificationCompat.Builder getDefaultSummaryNotifictioBuilder(ExportInfo exportInfo, BaseExporter exporter) {
        String summaryText = getDefaultSummaryText(exportInfo, exporter);
        String summaryTitle = getDefaultSummaryTitle(exportInfo, exporter);

        return getDefaultNotifictioBuilder(summaryTitle, summaryText);
    }


    private String getDefaultText(ExportInfo exportInfo, BaseExporter exporter) {
        return exportInfo.getExportMessage(mContext, exporter);
    }

    private String getDefaultTitle(ExportInfo exportInfo, BaseExporter exporter) {
        return exportInfo.getExportTitle(mContext, exporter);
    }

    private String getDefaultSummaryText(ExportInfo exportInfo, BaseExporter exporter) {
        String action = mContext.getString(exporter.getAction().getIngId());
        String type = mContext.getString(exportInfo.getExportType().getUiId());
        int running = mGroupStates.get(generateGroupKey(exportInfo)).getJobsRunning();  // better pass the groupKey?

        return mContext.getResources().getQuantityString(
                R.plurals.notification_exporting,
                running,
                action,
                type,
                running);
    }

    private String getDefaultSummaryTitle(ExportInfo exportInfo, BaseExporter exporter) {
        return exportInfo.getExportTitle(mContext, exporter);
    }

    private NotificationCompat.Builder getDefaultNotifictioBuilder(String title, String text) {
        return new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(mPendingIntentStartWorkoutListActivity);
    }

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