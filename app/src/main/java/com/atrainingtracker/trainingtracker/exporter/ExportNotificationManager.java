package com.atrainingtracker.trainingtracker.exporter;

import android.Manifest;
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
import androidx.work.ForegroundInfo;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Eine zentrale Klasse zur Verwaltung aller Benachrichtigungen, die während des Exportvorgangs angezeigt werden.
 * Sie kümmert sich um die Erstellung von Fortschritts-, Zusammenfassungs- und Abschlussbenachrichtigungen.
 */
public class ExportNotificationManager {
    private static final boolean DEBUG = true;
    private static final String TAG = "ExportNotificationManager";
    private static ExportNotificationManager sInstance;

    private final Context mContext;
    private final NotificationManagerCompat mNotificationManager;
    private final PendingIntent mPendingIntentStartWorkoutListActivity;

    private final Map<String, Set<Integer>> mActiveNotifications = new HashMap<>();



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


    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public synchronized void showInitialNotification(ExportInfo exportInfo, BaseExporter exporter) {  // TODO: rename
        if (isMissingPermission()) return;

        String groupKey = generateGroupKey(exportInfo);
        int notificationId = generateNotificationId(exportInfo);
        int summaryNotificationId = generateSummaryNotificationId(exportInfo);

        Set<Integer> notificationIdsInGroup = mActiveNotifications.get(groupKey);
        if (notificationIdsInGroup == null) {
            notificationIdsInGroup = new HashSet<>();
            mActiveNotifications.put(groupKey, notificationIdsInGroup);
        }

        String title = exportInfo.getExportTitle(mContext, exporter);
        String text = exportInfo.getExportMessage(mContext, exporter);
        String summaryText = "Exporte für " + exportInfo.getExportType();  // TODO: better...

        notificationIdsInGroup.add(notificationId);

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



    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
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


    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    public synchronized void showFinalNotification(ExportInfo exportInfo, BaseExporter exporter, String text) {
        if (isMissingPermission()) return;

        String groupKey = generateGroupKey(exportInfo);
        int notificationId = generateNotificationId(exportInfo);
        int summaryNotificationId = generateSummaryNotificationId(exportInfo);

        String title = exportInfo.getExportTitle(mContext, exporter);

        Set<Integer> notificationIdsInGroup = mActiveNotifications.get(groupKey);
        if (notificationIdsInGroup != null) {
            notificationIdsInGroup.remove(notificationId);

            if (notificationIdsInGroup.isEmpty()) {                  // this was the last ID in the group
                mActiveNotifications.remove(groupKey);               // remove the group from the map
                mNotificationManager.cancel(summaryNotificationId);  // remove the summary notification
                showFinalSummary(exportInfo, exporter);              // and show the final notification
                return;                                              // that's all folks
            }
        }
        Notification finalNotification = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(mPendingIntentStartWorkoutListActivity)
                .setGroup(groupKey)
                .setAutoCancel(true)
                .setOngoing(false)
                .build();
        mNotificationManager.notify(notificationId, finalNotification);
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private void showFinalSummary(ExportInfo exportInfo, BaseExporter exporter) {
        if (isMissingPermission()) return;

        int finalSummaryId = generateSummaryNotificationId(exportInfo);

        String title = "export finished";  // TODO

        exportInfo.getExportTitle(mContext, exporter);
        String text = exportInfo.getExportMessage(mContext, exporter);

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