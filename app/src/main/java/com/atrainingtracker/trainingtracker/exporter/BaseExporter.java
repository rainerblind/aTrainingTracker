/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.exporter;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

public abstract class BaseExporter {
    protected static final String PREFIX_NOT_FIRST = ",\n    ";
    protected static final String PREFIX_FIRST = "\n    ";
    private static final String TAG = "BaseExporter";
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;
    protected static ExportManager cExportManager;
    private static NotificationCompat.Builder mNotificationBuilder;
    private static NotificationManagerCompat cNotificationManager;
    protected Context mContext;

    public BaseExporter(Context context) {
        mContext = context;
        cExportManager = new ExportManager(context, TAG);

        cNotificationManager = NotificationManagerCompat.from(context);

        // configure the intent
        Bundle bundle = new Bundle();
        bundle.putString(MainActivityWithNavigation.SELECTED_FRAGMENT, MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST.name());
        Intent newIntent = new Intent(mContext, MainActivityWithNavigation.class);
        newIntent.putExtras(bundle);
        newIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        // newIntent.setAction("fooAction");
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, newIntent, 0);

        // configure the notification
        mNotificationBuilder = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_save_black_48dp))
                .setContentTitle(mContext.getString(R.string.TrainingTracker))
                .setContentText(mContext.getString(R.string.exporting))
                .setContentIntent(pendingIntent)
                .setOngoing(true);
    }

    protected static File getBaseDir() {
        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File(sdCard.getAbsolutePath() + "/Workouts");
        dir.mkdirs();
        return dir;
    }

    public static File getDir(String type) {
        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File(sdCard.getAbsolutePath() + "/Workouts/" + type);
        dir.mkdirs();
        return dir;
    }

    protected static boolean checkForSDStorage(Context context) {
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            return true;
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.SD_card_not_available)
                    .setCancelable(false)
                    .setPositiveButton(R.string.OK, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            // do nothing
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();

            return false;
        }
    }

    public void onFinished() {
        cExportManager.onFinished(TAG);
    }

    public final boolean export(ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "export: " + exportInfo.toString());

        boolean success = false;

        // ExportStatus oldExportStatus = cExportManager.getExportStatus(exportInfo);
        // String oldAnswer = cExportManager.getExportAnswer(exportInfo);

        cExportManager.exportingStarted(exportInfo);

        try {
            ExportResult result = doExport(exportInfo);
            success = result.success();
            if (!result.success() && !MyHelper.isOnline()) {
                // cExportManager.exportingFinishedRetry(exportInfo, "network loss while uploading, will retry later");
                cExportManager.exportingFinished(exportInfo, false, "network loss while uploading");
            } else {
                cExportManager.exportingFinished(exportInfo, result.success(), result.answer());
            }

        } catch (SQLException e) {
            Log.e(TAG, e.getMessage(), e);
            cExportManager.exportingFinished(exportInfo, false, "SQLException: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            cExportManager.exportingFinished(exportInfo, false, "SQLException:" + e.getMessage());
        } catch (JSONException e) {
            Log.e(TAG, "JSONException: " + e.getMessage(), e);
            cExportManager.exportingFinished(exportInfo, false, "JSONException: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException: " + e.getMessage(), e);
            cExportManager.exportingFinished(exportInfo, false, "IllegalArgumentException: " + e.getMessage());
        } catch (ParseException e) {
            Log.e(TAG, "ParseException: " + e.getMessage(), e);
            cExportManager.exportingFinished(exportInfo, false, "ParseException: " + e.getMessage());
        } catch (InterruptedException e) {
            Log.e(TAG, "ParseException: " + e.getMessage(), e);
            cExportManager.exportingFinished(exportInfo, false, "InterruptedException: " + e.getMessage());
        }

        return success;
    }

    abstract protected ExportResult doExport(ExportInfo exportInfo)
            throws IOException, IllegalArgumentException, JSONException, ParseException, InterruptedException;

    /**
     * helper method to check whether there is some data
     */
    protected boolean dataValid(Cursor cursor, String string) {
        if (cursor.getColumnIndex(string) == -1) {
            if (DEBUG) Log.d(TAG, "dataValid: no such columnIndex!: " + string);
            return false;
        }
        if (cursor.isNull(cursor.getColumnIndex(string))) {
            if (DEBUG) Log.d(TAG, "dataValid: cursor.isNull = true for " + string);
            return false;
        }
        return true;
    }

    /**
     * create a notification that informs the user about the progress
     **/
    public Notification getExportProgressNotification(ExportInfo exportInfo) {
        mNotificationBuilder.setProgress(0, 0, false)
                .setContentText(getExportMessage(exportInfo));

        return mNotificationBuilder.build();
    }

    public void notifyExportFinished(String message) {
        mNotificationBuilder.setProgress(0, 0, false)
                .setContentText(message);
    }

    protected String getExportTitle(ExportInfo exportInfo) {
        return mContext.getString(R.string.notification_title,
                mContext.getString(getAction().getIngId()),
                mContext.getString(exportInfo.getExportType().getUiId()));
    }

    protected String getExportMessage(ExportInfo exportInfo) {
        String workoutName = exportInfo.getFileBaseName();
        FileFormat format = exportInfo.getFileFormat();
        ExportType type = exportInfo.getExportType();
        int notification_format_id = R.string.notification_export_unknown;

        switch (type) {
            case FILE:
                notification_format_id = R.string.notification_export_file;
                break;
            case DROPBOX:
                notification_format_id = R.string.notification_export_dropbox;
                break;
            case COMMUNITY:
                notification_format_id = R.string.notification_export_community;
                break;
        }

        return mContext.getString(notification_format_id,
                mContext.getString(getAction().getIngId()),
                mContext.getString(format.getUiNameId()),
                workoutName);
    }

    // copied code from getExportMessage
    protected String getPositiveAnswer(ExportInfo exportInfo) {
        String workoutName = exportInfo.getFileBaseName();
        FileFormat format = exportInfo.getFileFormat();
        ExportType type = exportInfo.getExportType();
        int notification_format_id = R.string.notification_finished_unknown;

        switch (type) {
            case FILE:
                notification_format_id = R.string.notification_finished_file;
                break;
            case DROPBOX:
                notification_format_id = R.string.notification_finished_dropbox;
                break;
            case COMMUNITY:
                notification_format_id = R.string.notification_finished_community;
                break;
        }

        return mContext.getString(notification_format_id,
                mContext.getString(getAction().getPastId()),
                mContext.getString(format.getUiNameId()),
                workoutName);
    }

    protected abstract Action getAction();

    protected void notifyProgress(int max, int count) {
        if ((count % (10 * 60)) == 0) {  // TODO take sampling time into account?
            mNotificationBuilder.setProgress(max, count, false);
            cNotificationManager.notify(TrainingApplication.EXPORT_PROGRESS_NOTIFICATION_ID, mNotificationBuilder.build());
        }
    }

    protected String getSamplePrefix(boolean isFirst) {
        if (isFirst) {
            return PREFIX_FIRST;
        } else {
            return PREFIX_NOT_FIRST;
        }
    }

    protected String myGetStringFromCursor(Cursor cursor, String key) {
        String result = null;

        if (!cursor.isNull(cursor.getColumnIndex(key))) {
            result = cursor.getString(cursor.getColumnIndex(key));
            if ("".equals(result)) {
                result = null;
            }
        }

        return result;
    }

    protected boolean myGetBooleanFromCursor(Cursor cursor, String key) {

        if (!cursor.isNull(cursor.getColumnIndex(key))) {
            return (cursor.getInt(cursor.getColumnIndex(key)) > 0);
        } else {
            return false;
        }
    }

    protected enum Action {
        UPLOAD(R.string.uploading, R.string.uploaded),
        EXPORT(R.string.exporting, R.string.exported);

        private int mIngId;
        private int mPastId;

        Action(int ingId, int pastId) {
            mIngId = ingId;
            mPastId = pastId;
        }

        public int getIngId() {
            return mIngId;
        }

        public int getPastId() {
            return mPastId;
        }
    }

    protected class ExportResult {
        private boolean mSuccess;
        private String mAnswer;

        public ExportResult(boolean success, String answer) {
            mSuccess = success;
            mAnswer = answer;
        }

        public boolean success() {
            return mSuccess;
        }

        public String answer() {
            return mAnswer;
        }
    }
}
