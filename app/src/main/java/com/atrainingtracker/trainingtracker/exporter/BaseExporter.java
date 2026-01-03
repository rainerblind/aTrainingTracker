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


import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation;
import com.atrainingtracker.trainingtracker.MyHelper;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import org.json.JSONException;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

public abstract class BaseExporter {
    protected static final String PREFIX_NOT_FIRST = ",\n    ";
    protected static final String PREFIX_FIRST = "\n    ";
    private static final String TAG = "BaseExporter";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    protected static ExportManager cExportManager;
    private static NotificationCompat.Builder mNotificationBuilder;
    private static NotificationManagerCompat cNotificationManager;
    protected final Context mContext;

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
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, newIntent, PendingIntent.FLAG_IMMUTABLE);

        // configure the notification
        mNotificationBuilder = new NotificationCompat.Builder(mContext, TrainingApplication.NOTIFICATION_CHANNEL__EXPORT)
                .setSmallIcon(R.drawable.logo)
                .setLargeIcon(BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_save_black_48dp))
                .setContentTitle(mContext.getString(R.string.TrainingTracker))
                .setContentText(mContext.getString(R.string.exporting))
                .setContentIntent(pendingIntent)
                .setOngoing(true);
    }

    /**
     * Get the absolute path for the base directory where exported files are stored
     * @param context
     * @return the path
     */
    public static File getBaseDirFile(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // relative path
            return Environment.getExternalStoragePublicDirectory(getRelativePath()).getAbsoluteFile();
        } else {
            return context.getExternalFilesDir(null).getAbsoluteFile();
         }
    }

    /**
     * Get the relative "base path" to identify the file for >=Q
     * @return the path
     */
    private static String getRelativePath() {
        return Environment.DIRECTORY_DOCUMENTS + File.separator + "aTrainingTracker";
    }

    private static OutputStream getOutputStream(Context context, String shortPath, String mimeType) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final ContentValues contentValues = getContentValues(shortPath, mimeType);

            final ContentResolver resolver = context.getApplicationContext().getContentResolver();

            final Uri contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = resolver.insert(contentUri, contentValues);
            if (uri == null) {
                Log.w(TAG, "No uri: " + contentUri + " " + shortPath);
                return null;
            }
            return resolver.openOutputStream(uri);
        } else {
            String path = context.getExternalFilesDir(null).getAbsolutePath() + File.separator + shortPath;
            File file = new File(path);
            return new BufferedOutputStream(new FileOutputStream(file));
        }
    }

    @NonNull
    private static ContentValues getContentValues(String shortPath, String mimeType) {
        File shortFile = new File(shortPath);
        final String relativeLocation = getRelativePath() + File.separator + shortFile.getParent();

        final ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Files.FileColumns.DISPLAY_NAME, shortFile.getName());
        contentValues.put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativeLocation);
        // TODO int mediaType = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ? 0 : MediaStore.Files.FileColumns.MEDIA_TYPE_DOCUMENT;
        contentValues.put(MediaStore.Files.FileColumns.MEDIA_TYPE, 0);
        contentValues.put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType);
        return contentValues;
    }

    public static BufferedWriter getWriter(Context context, String shortPath, String mimeType) throws IOException {
        OutputStream outputStream = getOutputStream(context, shortPath, mimeType);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        return new BufferedWriter(outputStreamWriter);
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

        } catch (FileNotFoundException e) {
            Log.e(TAG, "FileNotFoundException: " + e.getMessage(), e);
            cExportManager.exportingFinished(exportInfo, false, "InterruptedException: " + e.getMessage());
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
        int index = cursor.getColumnIndex(string);
        if (index < 0) {
            if (DEBUG) Log.d(TAG, "dataValid: no such columnIndex!: " + string);
            return false;
        }
        if (cursor.isNull(index)) {
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
        if ((count % (10 * 60)) == 0  // TODO take sampling time into account?
                && cNotificationManager.areNotificationsEnabled()) {
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
        int index = cursor.getColumnIndex(key);
        if (index <= 0) {
            if (DEBUG) Log.d(TAG, "myGetStringFromCursor: no such columnIndex!: " + key);
            return null;
        }

        String result = null;
        if (!cursor.isNull(index)) {
            result = cursor.getString(index);
            if ("".equals(result)) {
                result = null;
            }
        }

        return result;
    }

    protected boolean myGetBooleanFromCursor(Cursor cursor, String key) {
        int index = cursor.getColumnIndex(key);
        if (index <= 0) {
            if (DEBUG) Log.d(TAG, "myGetBooleanFromCursor: no such columnIndex!: " + key);
            return false;
        }

        if (!cursor.isNull(index)) {
            return (cursor.getInt(index) > 0);
        } else {
            return false;
        }
    }

    protected enum Action {
        UPLOAD(R.string.uploading, R.string.uploaded),
        EXPORT(R.string.exporting, R.string.exported);

        private final int mIngId;
        private final int mPastId;

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

    protected static class ExportResult {
        private final boolean mSuccess;
        private final String mAnswer;

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
