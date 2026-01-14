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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.onlinecommunities.trainingpeaks.TrainingpeaksGetAccessTokenActivity;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class TrainingPeaksUploader extends BaseExporter {
    protected static final String MY_UPLOAD_URL = "https://api.trainingpeaks.com/v1/file";
    protected static final String MY_CONTENT_TYPE = "application/json";
    protected static final String CONTENT_TYPE = "Content-Type";
    protected static final String AUTHORIZATION = "Authorization";
    protected static final String BEARER = "Bearer";
    protected static final String UPLOAD_CLIENT = "UploadClient";
    protected static final String FILENAME = "Filename";
    protected static final String DATA = "Data";
    protected static final String SET_WORKOUT_PUBLIC = "SetWorkoutPublic";
    protected static final String TITLE = "Title";
    protected static final String COMMENT = "Comment";
    protected static final String TYPE = "Type";
    private static final String TAG = TrainingPeaksUploader.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);

    public TrainingPeaksUploader(@NonNull Context context) {
        super(context);
    }

    @NonNull
    @Override
    protected ExportResult doExport(@NonNull ExportInfo exportInfo, @Nullable IExportProgressListener progressListener) throws IOException, JSONException {
        if (DEBUG) Log.d(TAG, "doExport: " + exportInfo.getFileBaseName());

        TrainingpeaksGetAccessTokenActivity trainingPeaksGetAccessTokenActivity = new TrainingpeaksGetAccessTokenActivity();
        String accessToken = trainingPeaksGetAccessTokenActivity.getRefreshedAccessToken();
        if (DEBUG) Log.i(TAG, "accessToken=" + accessToken);

        if (accessToken == null) {
            // TODO: do some error handling?
            return new ExportResult(false, false,  "Could not get an up to date access token");
        }


        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSummariesDatabaseManager.WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{exportInfo.getFileBaseName()},
                null,
                null,
                null);

        cursor.moveToFirst();

        long sportId;
        String name, sportName, description;
        boolean isPrivate;

        sportId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.SPORT_ID));
        sportName = SportTypeDatabaseManager.getTrainingPeaksName(sportId);

        name = myGetStringFromCursor(cursor, WorkoutSummaries.WORKOUT_NAME);
        description = myGetStringFromCursor(cursor, WorkoutSummaries.DESCRIPTION);
        isPrivate = myGetBooleanFromCursor(cursor, WorkoutSummaries.PRIVATE);

        cursor.close();
        databaseManager.closeDatabase();// db.close();


        HttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost(MY_UPLOAD_URL);
        httpPost.addHeader(AUTHORIZATION, BEARER + " " + accessToken);
        httpPost.addHeader(CONTENT_TYPE, MY_CONTENT_TYPE);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put(UPLOAD_CLIENT, TrainingApplication.getAppName());
        // jsonObject.put(UPLOAD_CLIENT, TrainingpeaksGetAccessTokenActivity.MY_CLIENT_ID);
        jsonObject.put(FILENAME, exportInfo.getFileName());
        jsonObject.put(SET_WORKOUT_PUBLIC, !isPrivate);
        jsonObject.put(TITLE, name);
        jsonObject.put(COMMENT, description);
        jsonObject.put(TYPE, sportName);

        File file = new File(getBaseDirFile(mContext), exportInfo.getShortPath());
        InputStream inputStream = new FileInputStream(file.getAbsolutePath());
        byte[] buffer = new byte[8192];
        int bytesRead;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Base64OutputStream output64 = new Base64OutputStream(output, Base64.DEFAULT);
        try {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                output64.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        output64.close();
        jsonObject.put(DATA, output.toString());

        httpPost.setEntity(new StringEntity(jsonObject.toString()));

        // TODO: do this in background!
        if (DEBUG) Log.d(TAG, "starting to upload to TrainingPeaks");
        if (DEBUG) Log.i(TAG, "URI=" + httpPost.getURI());
        if (DEBUG) Log.i(TAG, "content:" + jsonObject);
        HttpResponse httpResponse = httpClient.execute(httpPost);
        String response = EntityUtils.toString(httpResponse.getEntity());
        if (DEBUG) Log.d(TAG, "status: " + httpResponse.getStatusLine());
        if (DEBUG) Log.d(TAG, "uploadToTrainingPeaks response: " + response);

        // TODO: the following lines seems to be not 100% correct

        if (response == null) {
            return new ExportResult(false, false, "no response");
        } else if (response.isEmpty()) {
            return new ExportResult(true, false, "successfully uploaded " + exportInfo.getFileBaseName() + " to TrainingPeaks");
        }
        return new ExportResult(true, false, response);
    }

    @NonNull
    @Override
    protected Action getAction() {
        return Action.UPLOAD;
    }

}
