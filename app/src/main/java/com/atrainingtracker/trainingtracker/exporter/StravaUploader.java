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
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.database.EquipmentDbHelper;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager;
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StravaUploader extends BaseExporter {
    protected static final int MAX_REQUESTS = 10;
    protected static final String URL_STRAVA_UPLOAD = "https://www.strava.com/api/v3/uploads";
    protected static final String URL_STRAVA_ACTIVITY = "https://www.strava.com/api/v3/activities/";
    protected static final String ACCESS_TOKEN = "access_token";
    protected static final String ACTIVITY_TYPE = "activity_type";
    protected static final String DATA_TYPE = "data_type";
    protected static final String TCX = "tcx";
    protected static final String FILE = "file";
    protected static final long INITIAL_WAITING_TIME = 10 * 1000L;  // 10 seconds
    protected static final long WAITING_TIME_UPDATE = 1000L;  // 1 second
    private static final String TAG = "StravaUploader";
    private static final boolean DEBUG = true; //TrainingApplication.getDebug(false);

    //    {
//    	  "id": 16486788,
//    	  "external_id": "test.fit",
//    	  "error": null,
//    	  "status": "Your activity is still being processed.",
//    	  "activity_id": null
//    	}
    private static final String ID = "id";
    private static final String ACTIVITY_ID = "activity_id";
    // private static final String EXTERNAL_ID  = "external_id";
    private static final String ERROR = "error";
    private static final String STATUS = "status";

    private static final String STATUS_PROCESSING = "Your activity is still being processed.";
    private static final String STATUS_DELETED = "The created activity has been deleted.";
    private static final String STATUS_ERROR = "There was an error processing your activity.";
    private static final String STATUS_READY = "Your activity is ready.";

    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String GEAR_ID = "gear_id";
    private static final String DESCRIPTION = "description";
    private static final String PRIVATE = "private";
    private static final String COMMUTE = "commute";
    private static final String TRAINER = "trainer";
    private static final String TRUE = "true";

    public StravaUploader(Context context) {
        super(context);
    }

    @Override
    protected ExportResult doExport(ExportInfo exportInfo)
            throws IOException, JSONException, InterruptedException {
        if (DEBUG) Log.d(TAG, "doExport: " + exportInfo.getFileBaseName() + " ignoring as success, upload is broken.");

        if (ExportStatus.FINISHED_SUCCESS == cExportManager.getExportStatus(exportInfo)) {
            if (DEBUG) Log.d(TAG, "workout already successfully uploaded");
            return doUpdate(exportInfo);
            // return new ExportResult(false, "workout already successfully uploaded");  // TODO here, we should not change the answer?
        }

        File file = new File(getBaseDirFile(mContext), exportInfo.getShortPath());

        MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
        // multipartEntityBuilder.addTextBody(ACCESS_TOKEN, TrainingApplication.getStravaToken());
        multipartEntityBuilder.addTextBody(DATA_TYPE, TCX);
        // TODO: we could also set all other values at this point.  
        // However, changing the sport and gear at the same time was problematic some time ago. (Also asked in the forum about this problem.)
        // Maybe, this should be check again.

        // multipartEntityBuilder.addBinaryBody("file", file);
        multipartEntityBuilder.addBinaryBody(FILE, file, ContentType.MULTIPART_FORM_DATA, file.getName());

        HttpPost httpPost = new HttpPost(URL_STRAVA_UPLOAD);
        httpPost.addHeader("Authorization", "Bearer " + StravaHelper.getRefreshedAccessToken());

        httpPost.setEntity(multipartEntityBuilder.build());

        if (DEBUG) Log.d(TAG, "starting to upload to strava");
        HttpClient httpClient = new DefaultHttpClient();
        HttpResponse httpResponse = httpClient.execute(httpPost);
        String response = EntityUtils.toString(httpResponse.getEntity());
        if (DEBUG) Log.d(TAG, "uploadToStrava response: " + response);

        // check the response
        if (response == null || response.equals("")) {  // hm, there is no response
            if (DEBUG) Log.d(TAG, "no response");
            return new ExportResult(false, "no response");
        }

        if (DEBUG) Log.d(TAG, "ok, there is a response");

        // from strava API documentation: 
        // Upon a successful submission the request will return 201 Created. If there was an error the request will return 400 Bad Request
        // but also 500 Internal Server Error were observed :-(
        if (httpResponse.getStatusLine().getStatusCode() != 201) {
            if (DEBUG) Log.d(TAG, "bad response: " + response);
            // handle duplicate uploads like
            // response: {"id":130764101,"external_id":"2014-03-10_1114.tcx","error":"2014-03-10_1114.tcx duplicate of activity 119487747","status":"There was an error processing your activity.","activity_id":null}
            if (response.contains("duplicate of activity")) {
                if (DEBUG) Log.d(TAG, "duplicate activity");

                JSONObject responseJson = new JSONObject(response);
                if (responseJson.has(ERROR)) {
                    String id = responseJson.getString(ID);
                    String error = responseJson.getString(ERROR);
                    Pattern intsOnly = Pattern.compile("duplicate of activity (\\d+)");
                    Matcher makeMatch = intsOnly.matcher(error);
                    makeMatch.find();
                    String activity_id = makeMatch.group(1);
                    if (DEBUG) Log.i(TAG, "activity_id=" + activity_id);
                    (new StravaUploadDbHelper(mContext)).updateAll(exportInfo.getFileBaseName(), id, activity_id, error);
                    cExportManager.exportingFinished(exportInfo, true, getPositiveAnswer(exportInfo));
                    return doUpdate(exportInfo);
                }

            }

            // TODO: better way to inform the user
            return new ExportResult(false, "response");
        }

        // Ok, now there should be a valid result, so we can continue 
        if (DEBUG) Log.d(TAG, "Successfully uploaded to STRAVA, still have to check the result");
        notifyExportFinished(mContext.getString(R.string.strava_success_but_must_check));

        JSONObject uploadResponseJson = new JSONObject(response);

        if (uploadResponseJson.has(ERROR) && uploadResponseJson.getString(ERROR) != null && !"null".equals(uploadResponseJson.getString(ERROR))) {
            if (DEBUG) Log.d(TAG, "There was an error!");
            return new ExportResult(false, uploadResponseJson.getString(ERROR));
        } else if (uploadResponseJson.has(ID)) {
            String uploadId = uploadResponseJson.getString(ID);

            StravaUploadDbHelper stravaUploadDbHelper = new StravaUploadDbHelper(mContext);
            stravaUploadDbHelper.updateUploadId(exportInfo.getFileBaseName(), uploadId);
            stravaUploadDbHelper.updateStatus(exportInfo.getFileBaseName(), "Uploaded to STRAVA, checking result");
            notifyExportFinished(mContext.getString(R.string.strava_success_but_must_check));

            ExportResult exportResult = null;

            long waiting_time = INITIAL_WAITING_TIME;
            for (int attempt = 1; attempt <= MAX_REQUESTS && exportResult == null; attempt++) {
                // wait some time before we ask the server
                Thread.sleep(waiting_time);
                waiting_time *= 1.4;  // next time, we wait somewhat longer

                JSONObject uploadStatusJson = getStravaUploadStatus(uploadId);

                if (uploadStatusJson == null) {
                    exportResult = new ExportResult(false, "no correct response from Strava");
                }

                if (uploadStatusJson.has(ERROR) && uploadStatusJson.getString(ERROR) != null && !"null".equals(uploadStatusJson.getString(ERROR))) {
                    exportResult = new ExportResult(false, uploadStatusJson.getString(ERROR));
                } else if (uploadStatusJson.has(STATUS)) {
                    String status = uploadStatusJson.getString(STATUS);
                    if (DEBUG) Log.d(TAG, "strava response status: " + status);
                    stravaUploadDbHelper.updateStatus(exportInfo.getFileBaseName(), status);

                    if (STATUS_PROCESSING.equals(status)) {
                        // here, we do nothing (wait for the next iteration of the loop)
                    } else if (STATUS_DELETED.equals(status)) {
                        exportResult = new ExportResult(false, STATUS_DELETED);
                    } else if (STATUS_ERROR.equals(status)) {
                        // should have been already handled???
                        exportResult = new ExportResult(false, uploadStatusJson.getString(ERROR));
                    } else if (STATUS_READY.equals(status)) {
                        // all right
                        cExportManager.exportingFinished(exportInfo, true, getPositiveAnswer(exportInfo));
                        // but not everything is uploaded to strava, e.g., the gear data is missing.
                        // Thus, we update it
                        String activity_id = uploadStatusJson.getString(ACTIVITY_ID);
                        if (activity_id != null) {
                            stravaUploadDbHelper.updateActivityId(exportInfo.getFileBaseName(), activity_id);
                            exportResult = doUpdate(exportInfo);
                        } else {
                            if (DEBUG)
                                Log.d(TAG, "ERROR while uploading to Strava: could not get activity_id from response");
                        }

                    } else {
                        if (DEBUG) Log.d(TAG, "unknown response status: " + status);
                        exportResult = new ExportResult(false, "successfully uploaded but unknown status " + status);
                    }
                }
            }

            return exportResult;

        } else {
            if (DEBUG) Log.d(TAG, "strange error: neither ID, nor ERROR!");
            return new ExportResult(false, "Something strange went wrong");
        }
    }

    protected ExportResult doUpdate(ExportInfo exportInfo) {
        Log.e(TAG, "doUpdate: " + exportInfo.getFileBaseName());
        if (DEBUG) Log.d(TAG, "doUpdate");
        // Strava fields:
        //
        // name: 	    string optional
        // type: 	    string optional
        // private: 	boolean optional, defaults to false
        // commute: 	boolean optional, defaults to false
        // trainer: 	boolean optional, defaults to false
        // gear_id: 	string optional,  ‘none’ clears gear from activity
        // description: string optional

        String fileBaseName = exportInfo.getFileBaseName();
        String activityId = new StravaUploadDbHelper(mContext).getActivityId(fileBaseName);
        if (activityId == null) {
            return new ExportResult(false, "could not update " + fileBaseName + " there does not exist a stravaId");
        } else {
            if (DEBUG) Log.d(TAG, "got activityId: " + activityId);
        }

        WorkoutSummariesDatabaseManager databaseManager = WorkoutSummariesDatabaseManager.getInstance();
        SQLiteDatabase db = databaseManager.getOpenDatabase();
        Cursor cursor = db.query(WorkoutSummaries.TABLE,
                null,
                WorkoutSummaries.FILE_BASE_NAME + "=?",
                new String[]{fileBaseName},
                null,
                null,
                null);

        cursor.moveToFirst();

        long sportId;
        String name, sportName, gear_id, description;
        boolean isPrivate, trainer, commute;

        sportId = cursor.getLong(cursor.getColumnIndex(WorkoutSummaries.SPORT_ID));
        sportName = SportTypeDatabaseManager.getStravaName(sportId);

        if (!cursor.isNull(cursor.getColumnIndex(WorkoutSummaries.EQUIPMENT_ID))) {
            gear_id = new EquipmentDbHelper(mContext).getStravaIdFromId(cursor.getInt(cursor.getColumnIndex(WorkoutSummaries.EQUIPMENT_ID)));
        } else {
            gear_id = null;
        }
        name = myGetStringFromCursor(cursor, WorkoutSummaries.WORKOUT_NAME);
        description = myGetStringFromCursor(cursor, WorkoutSummaries.DESCRIPTION);
        isPrivate = myGetBooleanFromCursor(cursor, WorkoutSummaries.PRIVATE);
        trainer = myGetBooleanFromCursor(cursor, WorkoutSummaries.TRAINER);
        commute = myGetBooleanFromCursor(cursor, WorkoutSummaries.COMMUTE);

        cursor.close();
        databaseManager.closeDatabase();// db.close();


        // get an json description from Strava
        JSONObject activityJSON = getStravaActivity(activityId);
        if (activityJSON == null) {
            return new ExportResult(false, "updating Strava failed");
        }


        // first, make sure that the type is correct
        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
        nameValuePairs.add(new BasicNameValuePair(TYPE, sportName));
        updateStravaActivity(activityId, nameValuePairs);             // update
        int counter = 0;
        // TODO: better structuring of Exceptions!
        try {
            do {
                try {
                    Thread.sleep(WAITING_TIME_UPDATE);                   // wait
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                activityJSON = getStravaActivity(activityId);              // ...
                if (DEBUG) {
                    Log.i(TAG, "json:" + activityJSON);
                    Log.i(TAG, "sport type: " + sportName + "?=" + activityJSON.getString(TYPE));
                }
            } while ((!sportName.equalsIgnoreCase(activityJSON.getString(TYPE)))       // until type is updated
                    && (counter++ < MAX_REQUESTS));                       // or we give up
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // now, that the type is correct, we can update the equipment! (and the other fields)
        boolean update = false;
        nameValuePairs = new ArrayList<NameValuePair>(3);

        if (name != null) {
            nameValuePairs.add(new BasicNameValuePair(NAME, name));
            update = true;
        }

        if (gear_id != null) {
            nameValuePairs.add(new BasicNameValuePair(GEAR_ID, gear_id));
            update = true;
        }

        if (description != null) {
            nameValuePairs.add(new BasicNameValuePair(DESCRIPTION, description));
            update = true;
        }

        if (isPrivate) {
            nameValuePairs.add(new BasicNameValuePair(PRIVATE, TRUE));
            update = true;
        }

        if (trainer) {
            nameValuePairs.add(new BasicNameValuePair(TRAINER, TRUE));
            update = true;
        }

        if (commute) {
            nameValuePairs.add(new BasicNameValuePair(COMMUTE, TRUE));
            update = true;
        }

        if (update) {
            activityJSON = updateStravaActivity(activityId, nameValuePairs);
            if (DEBUG) {Log.i(TAG, "json: " + activityJSON); }
            // check result
            String errors = "errors:";
            boolean correctUpdate = true;

            for (NameValuePair nameValuePair : nameValuePairs) {
                String appValue = nameValuePair.getValue();
                String stravaValue = "";
                try {
                    stravaValue = activityJSON.getString(nameValuePair.getName());
                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    errors += " " + nameValuePair.getName();
                    correctUpdate = false;
                }
                if (!appValue.equals(stravaValue)) {
                    errors += " " + nameValuePair.getName();
                    correctUpdate = false;
                }
            }

            if (correctUpdate) {
                return new ExportResult(true, "successfully updated");
            } else {
                return new ExportResult(false, errors);
            }
        } else {
            return new ExportResult(true, "nothing to update");  // TODO: not correct when only the type was changed
        }

    }

    protected JSONObject updateStravaActivity(String stravaActivityId, List<NameValuePair> nameValuePairs) {
        if (DEBUG) Log.i(TAG, "updateStravaActivity(...)");
        Log.e(TAG, "updateStravaActivity: " + stravaActivityId);

        JSONObject responseJson = null;

        try {
            HttpClient httpClient = new DefaultHttpClient();
            HttpPut httpPut = new HttpPut(URL_STRAVA_ACTIVITY + stravaActivityId);
            httpPut.addHeader("Authorization", "Bearer " + StravaHelper.getRefreshedAccessToken());

            httpPut.setEntity(new UrlEncodedFormEntity(nameValuePairs, "UTF-8"));

            if (DEBUG) Log.d(TAG, "update request: " + httpPut.getRequestLine().getUri());

            HttpResponse httpResponse = httpClient.execute(httpPut);
            String response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "updateStravaActivity: got response from Strava: \n" + response);

            responseJson = new JSONObject(response);

        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return responseJson;
    }

    protected JSONObject getStravaActivity(String stravaActivityId) {
        JSONObject responseJson = null;
        Log.e(TAG, "getStravaUploadStatus: " + stravaActivityId);

        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(URL_STRAVA_ACTIVITY + stravaActivityId);
        httpGet.addHeader("Authorization", "Bearer " + StravaHelper.getRefreshedAccessToken());

        HttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpGet);
            String response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "getStravaActivity: got response:\n" + response);
            responseJson = new JSONObject(response);

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return responseJson;
    }


    protected JSONObject getStravaUploadStatus(String stravaUploadId) {
        JSONObject responseJson = null;
        Log.e(TAG, "getStravaUploadStatus: " + stravaUploadId);

        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(URL_STRAVA_UPLOAD + "/" + stravaUploadId);
        httpGet.addHeader("Authorization", "Bearer " + StravaHelper.getRefreshedAccessToken());

        HttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpGet);
            String response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "getStravaUploadStatus: got response:\n" + response);
            responseJson = new JSONObject(response);

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return responseJson;
    }

    @Override
    protected Action getAction() {
        return Action.UPLOAD;
    }

}
