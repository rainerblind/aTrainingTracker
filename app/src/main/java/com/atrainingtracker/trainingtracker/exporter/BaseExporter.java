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
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

public abstract class BaseExporter {
    protected static final String PREFIX_NOT_FIRST = ",\n    ";
    protected static final String PREFIX_FIRST = "\n    ";
    private static final String TAG = "BaseExporter";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    @NonNull
    protected final Context mContext;


    /** method to be called by the ExportManager to start the export */
    public final ExportResult export(@NonNull ExportInfo exportInfo) {
        if (DEBUG) Log.d(TAG, "export: " + exportInfo.toString());
        try {
            ExportResult exportResult = doExport(exportInfo);
            onFinished(exportInfo);
            return exportResult;

        } catch (IOException | JSONException | ParseException e) {
            return new ExportResult(false, true, e.getMessage());
        }
    }

    /* method that must be overridden by the child classes to do the export */
    @Nullable
    abstract protected ExportResult doExport(ExportInfo exportInfo) throws IOException, JSONException, ParseException;

    abstract Action getAction();

    protected void onFinished(@NonNull ExportInfo exportInfo) {}


    public BaseExporter(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Get the absolute path for the base directory where exported files are stored
     * @param context
     * @return the path
     */
    @NonNull
    public static File getBaseDirFile(@NonNull Context context) {
        return context.getFilesDir();
    }

    /**
     * Get the relative "base path" to identify the file for >=Q
     * @return the path
     */
    @NonNull
    private static String getRelativePath() {
        return Environment.DIRECTORY_DOCUMENTS + File.separator + "aTrainingTracker";
    }


    /**
     * Gets a BufferedWriter for a file in the app's internal storage.
     * @param shortPath The relative path of the file (e.g., "gpx/workout.gpx").
     * @return A BufferedWriter for the specified file.
     * @throws IOException If the file cannot be created or opened.
     */
    @NonNull
    protected BufferedWriter getBufferedWriter(@NonNull String shortPath) throws IOException {
        File file = new File(getBaseDirFile(mContext), shortPath);

        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(TAG, "Could not create directory: " + parentDir.getAbsolutePath());
            }
        }

        OutputStream outputStream = new FileOutputStream(file);
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        return new BufferedWriter(outputStreamWriter);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    protected void copyFileToDownloads(@NonNull ExportInfo exportInfo) {
        String filename = exportInfo.getShortPath();
        File fileToCopy = new File(getBaseDirFile(mContext), filename);
        if (!fileToCopy.exists()) {
            return;
        }

        ContentResolver resolver = mContext.getContentResolver();

        deleteExistingFile(resolver, fileToCopy.getName());

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileToCopy.getName());
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

        Uri targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);

        try (InputStream in = new FileInputStream(fileToCopy);
             OutputStream out = resolver.openOutputStream(targetUri)) {

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }

        } catch (IOException e) {
            Log.e("SaveToDownloads", "Fehler beim Kopieren der Datei", e);
            resolver.delete(targetUri, null, null);
        }
    }

    /**
     * Finds and deletes a file by its name in the public Downloads directory. * @param resolver The ContentResolver.
     * @param fileName The name of the file to delete (e.g., "workout.gpx").
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void deleteExistingFile(ContentResolver resolver, String fileName) {

        // Define the query to find the file
        Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        String[] projection = new String[]{MediaStore.Files.FileColumns._ID};
        String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " = ?";
        String[] selectionArgs = new String[]{fileName};

        Uri fileUriToDelete = null;

        // Execute the query
        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                // File found, get its URI
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID));
                fileUriToDelete = Uri.withAppendedPath(collection, String.valueOf(id));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while querying for existing file.", e);
            // Don't block the export if query fails
        }

        // If a file was found, delete it
        if (fileUriToDelete != null) {
            try {
                int rowsDeleted = resolver.delete(fileUriToDelete, null, null);
                if (rowsDeleted > 0) {
                    if (DEBUG) Log.d(TAG, "Successfully deleted existing file: " + fileName);
                }
            } catch (Exception e) {
                // This can sometimes fail if the user moved the file, but it's usually safe to ignore.
                Log.w(TAG, "Could not delete existing file, a duplicate might be created.", e);
            }
        }
    }




    @NonNull
    private static ContentValues getContentValues(@NonNull String shortPath, String mimeType) {
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


    /**
     * helper method to check whether there is some data
     */
    protected boolean dataValid(@NonNull Cursor cursor, String string) {
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



    @NonNull
    protected String getSamplePrefix(boolean isFirst) {
        if (isFirst) {
            return PREFIX_FIRST;
        } else {
            return PREFIX_NOT_FIRST;
        }
    }

    @Nullable
    protected String myGetStringFromCursor(@NonNull Cursor cursor, String key) {
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

    protected boolean myGetBooleanFromCursor(@NonNull Cursor cursor, String key) {
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
        private final boolean mPleaseRetryWhenFailed;
        private final String mAnswer;

        public ExportResult(boolean success, boolean pleaseRetryWhenFailed, String answer) {
            mSuccess = success;
            mPleaseRetryWhenFailed = pleaseRetryWhenFailed;
            mAnswer = answer;
        }

        public boolean success() {
            return mSuccess;
        }

        public boolean shallRetry() {return mPleaseRetryWhenFailed;}

        public String answer() {
            return mAnswer;
        }
    }
}
