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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager.Segments;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Created by rainer on 11.11.16.
 */

public class StravaSegmentsIntentService extends IntentService {
    public static final String REQUEST_TYPE = StravaSegmentsIntentService.class.getName() + ".REQUEST_TYPE";
    public static final String REQUEST_UPDATE_STARRED_SEGMENTS = StravaSegmentsIntentService.class.getName() + ".REQUEST_UPDATE_STARRED_SEGMENTS";
    public static final String SPORT_TYPE_ID = StravaSegmentsIntentService.class.getName() + ".SPORT_TYPE_ID";
    public static final String SEGMENT_ID = StravaSegmentsIntentService.class.getName() + ".SEGMENT_ID";
    public static final String SEGMENT_UPDATE_STARTED_INTENT = StravaSegmentsIntentService.class.getName() + ".SEGMENT_UPDATE_STARTED_INTENT";
    public static final String NEW_STARRED_SEGMENT_INTENT = StravaSegmentsIntentService.class.getName() + ".NEW_STARRED_SEGMENT_INTENT";       // send when there is a new or updated entry in the list of segments
    public static final String SEGMENTS_UPDATE_COMPLETE_INTENT = StravaSegmentsIntentService.class.getName() + ".SEGMENTS_UPDATE_COMPLETE_INTENT";  // send when all segment are handled
    public static final String RESULT_MESSAGE = StravaSegmentsIntentService.class.getName() + ".RESULT_MESSAGE";
    // protected static final String URL_STRAVA_STARRED_SEGMENTS = "https://www.strava.com/api/v3/segments/starred";
    protected static final String HTTPS = "https";
    protected static final String AUTHORITY_STRAVA = "www.strava.com";
    protected static final String API = "api";
    protected static final String V3 = "v3";
    protected static final String SEGMENTS = "segments";
    protected static final String PAGE = "page";

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // for streams:
    protected static final String STREAMS = "streams";
    protected static final String SERIES_TYPE = "series_type";
    protected static final String TIME = "time";
    protected static final String AUTHORIZATION = "Authorization";
    protected static final String BEARER = "Bearer";
    private static final String TAG = StravaSegmentsIntentService.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final String ID = "id";
    private static final String RESOURCE_STATE = "resource_state";
    private static final String NAME = "name";
    private static final String ACTIVITY_TYPE = "activity_type";
    private static final String DISTANCE = "distance";
    private static final String AVERAGE_GRADE = "average_grade";
    private static final String MAXIMUM_GRADE = "maximum_grade";
    private static final String ELEVATION_HIGH = "elevation_high";
    private static final String ELEVATION_LOW = "elevation_low";
    private static final String START_LATLNG = "start_latlng";
    private static final String END_LATLNG = "end_latlng";
    private static final String CLIMB_CATEGORY = "climb_category";
    private static final String CITY = "city";
    private static final String STATE = "state";
    private static final String COUNTRY = "country";
    private static final String PRIVATE = "private";
    private static final String STARRED = "starred";
    private static final String HAZARDOUS = "hazardous";
    private long mSportTypeId = -1;
    @Nullable
    private String mStravaSportName = null;

    public StravaSegmentsIntentService() {
        super("StravaSegmentsIntentService");
    }

    private static void getStream(SQLiteDatabase db, @NonNull StreamType streamType, long id) {
        // first, check whether this stream is already in the database
        Cursor cursor = db.query(streamType.table,
                null,
                streamType.idName + "=?",
                new String[]{id + ""},
                null,
                null,
                null,
                "1");
        if (cursor.getCount() >= 1) {  // already in database => simply return
            cursor.close();
            return;
        }
        cursor.close();

        // "https://www.strava.com/api/v3/segment_efforts/:id/streams/:types";
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(AUTHORITY_STRAVA)
                .appendPath(API)
                .appendPath(V3)
                .appendPath(streamType.urlPart)
                .appendPath(id + "")
                .appendPath(STREAMS)
                .appendPath(streamType.requestStreamTypes)
                .appendQueryParameter(SERIES_TYPE, TIME);
        String stravaUrl = builder.build().toString();

        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(stravaUrl);
        httpGet.addHeader(AUTHORIZATION, BEARER + " " + StravaHelper.getRefreshedAccessToken());

        HttpResponse httpResponse;

        try {
            httpResponse = httpClient.execute(httpGet);

            String response;
            response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "getAndInsertStream response: " + response);

            JSONArray segmentEfforts = new JSONArray(response);

            // create and fill the different streams to an Array of ContentValues
            boolean haveTime = false;
            ContentValues[] effortStreams = null; // new ContentValues[segmentEfforts.length()];
            for (int i = 0; i < segmentEfforts.length(); i++) {
                JSONObject effort = segmentEfforts.getJSONObject(i);

                if (DEBUG) Log.i(TAG, "got new stream json Object: " + effort.toString());

                String type = effort.getString("type");
                if (DEBUG) Log.i(TAG, "type=" + type);
                boolean isLatLng = "latlng".equals(type);
                boolean isDouble = false;
                String myType = null;
                if ("time".equals(type)) {
                    myType = "time";
                    haveTime = true;
                }
                if ("latlng".equals(type)) {
                    myType = "latlng";
                } else if ("distance".equals(type)) {
                    myType = Segments.DISTANCE;
                    isDouble = true;
                } else if ("altitude".equals(type)) {
                    myType = Segments.ALTITUDE;
                    isDouble = true;
                }

                if (myType == null) {
                    Log.e(TAG, "WTF: unknown type:" + type);
                    continue;  // jump to the next iteration
                }

                JSONArray stream = effort.getJSONArray("data");
                JSONArray latlng;

                // when the array of ContentValues is not yet initialized, we have to initialize it.
                if (effortStreams == null) {
                    effortStreams = new ContentValues[stream.length()];
                    for (int j = 0; j < stream.length(); j++) {
                        effortStreams[j] = new ContentValues();
                    }
                }

                for (int j = 0; j < stream.length(); j++) {
                    if (isLatLng) {
                        latlng = stream.getJSONArray(j);
                        effortStreams[j].put(Segments.LATITUDE, latlng.getDouble(0));
                        effortStreams[j].put(Segments.LONGITUDE, latlng.getDouble(1));
                    } else {
                        if (isDouble) {
                            effortStreams[j].put(myType, stream.getDouble(j));
                        } else {
                            effortStreams[j].put(myType, stream.getInt(j));
                        }
                    }
                }
            }

            // now, insert the content values to the database
            // thereby make sure that each row is one second later
            if (haveTime) {
                int initialTime = effortStreams[0].getAsInteger("time");
                int prevTime = initialTime - 1;
                int curTime = initialTime - 1;
                for (ContentValues effortStream : effortStreams) {
                    prevTime = curTime;
                    curTime = effortStream.getAsInteger("time");
                    effortStream.remove("time");
                    effortStream.put(streamType.idName, id);
                    for (int delta = 1; delta <= curTime - prevTime; delta++) {
                        db.insert(streamType.table, null, effortStream);
                    }
                }
            } else {
                for (ContentValues effortStream : effortStreams) {
                    effortStream.put(streamType.idName, id);
                    db.insert(streamType.table, null, effortStream);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.i(TAG, "onCreate");
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.i(TAG, "onDestroy");

        if (mSportTypeId > -1) {
            notifyGetStarredSegmentsCompleted("background service destroyed");
        }
    }

    @Override
    protected void onHandleIntent(@NonNull Intent intent) {
        Bundle bundle = intent.getExtras();
        String requestType = bundle.getString(REQUEST_TYPE);

        if (REQUEST_UPDATE_STARRED_SEGMENTS.equals(requestType)) {
            mSportTypeId = bundle.getLong(SPORT_TYPE_ID);
            mStravaSportName = SportTypeDatabaseManager.getInstance(this).getStravaName(mSportTypeId);
            getStarredSegments();
            mSportTypeId = -1;
        } else {
            Log.i(TAG, "unknown request type:" + requestType);
        }
    }

    private void getStarredSegments() {
        if (DEBUG) Log.d(TAG, "getStarredStravaSegments: sportTypeId=" + mSportTypeId);

        notifySegmentUpdateStarred();

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance(this).getDatabase();

        // first, save the segment_ids contained in the database.
        HashSet<Long> segmentIdSet = new HashSet<>();
        HashSet<Long> newSegmentIdSet = new HashSet<>();

        Cursor cursor = db.query(Segments.TABLE_STARRED_SEGMENTS,
                new String[]{Segments.SEGMENT_ID},
                Segments.ACTIVITY_TYPE + "=?", new String[]{mStravaSportName},
                null, null, null);
        while (cursor.moveToNext()) {
            segmentIdSet.add(cursor.getLong(cursor.getColumnIndex(Segments.SEGMENT_ID)));
        }
        cursor.close();

        String updateError = null;

        boolean requestNewPage = true;
        int page = 0;

        while (requestNewPage) {
            requestNewPage = false;
            page++;
            if (DEBUG) Log.i(TAG, "requesting page " + page);

            // "https://www.strava.com/api/v3/segments/starred
            Uri.Builder builder = new Uri.Builder();
            builder.scheme(HTTPS)
                    .authority(AUTHORITY_STRAVA)
                    .appendPath(API)
                    .appendPath(V3)
                    .appendPath(SEGMENTS)
                    .appendPath(STARRED)
                    .appendQueryParameter(PAGE, page + "");
            String starredSegmentsUrl = builder.build().toString();

            HttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet(starredSegmentsUrl);
            httpGet.addHeader(AUTHORIZATION, BEARER + " " + StravaHelper.getRefreshedAccessToken());

            HttpResponse httpResponse;
            try {
                httpResponse = httpClient.execute(httpGet);

                String response;
                response = EntityUtils.toString(httpResponse.getEntity());
                if (DEBUG) Log.d(TAG, "getStarredStravaSegments response: " + response);

                JSONArray jsonArray = new JSONArray(response);
                if (DEBUG) Log.i(TAG, "got " + jsonArray.length() + " new starred segments");

                ContentValues contentValues = new ContentValues();

                for (int i = 0; i < jsonArray.length(); i++) {
                    requestNewPage = true;

                    try {
                        JSONObject segmentJsonObject = jsonArray.getJSONObject(i);
                        if (DEBUG)
                            Log.i(TAG, "got new segment json Object: " + segmentJsonObject.toString());

                        // ignore this segment, when the sportType / activityType is not the requested
                        if (!mStravaSportName.equalsIgnoreCase(segmentJsonObject.getString(ACTIVITY_TYPE))) {
                            if (DEBUG)
                                Log.i(TAG, "ignore this segment due to the wrong activityType");
                            continue;
                        }


                        long segmentId = segmentJsonObject.getInt(ID);
                        newSegmentIdSet.add(segmentId);

                        if (segmentIdSet.contains(segmentId) && false) {
                            // nothing to do?
                        } else {

                            contentValues.clear();
                            contentValues.put(Segments.SEGMENT_ID, segmentId);

                            JSONArray latLng = segmentJsonObject.getJSONArray(START_LATLNG);
                            contentValues.put(Segments.START_LATITUDE, latLng.getDouble(0));
                            contentValues.put(Segments.START_LONGITUDE, latLng.getDouble(1));

                            latLng = segmentJsonObject.getJSONArray(END_LATLNG);
                            contentValues.put(Segments.END_LATITUDE, latLng.getDouble(0));
                            contentValues.put(Segments.END_LONGITUDE, latLng.getDouble(1));

                            contentValues.put(Segments.RESOURCE_STATE, segmentJsonObject.getInt(RESOURCE_STATE));
                            contentValues.put(Segments.SEGMENT_NAME, segmentJsonObject.getString(NAME));
                            contentValues.put(Segments.ACTIVITY_TYPE, segmentJsonObject.getString(ACTIVITY_TYPE));
                            contentValues.put(Segments.DISTANCE, segmentJsonObject.getDouble(DISTANCE));
                            contentValues.put(Segments.AVERAGE_GRADE, segmentJsonObject.getDouble(AVERAGE_GRADE));
                            contentValues.put(Segments.MAXIMUM_GRADE, segmentJsonObject.getDouble(MAXIMUM_GRADE));
                            contentValues.put(Segments.ELEVATION_HIGH, segmentJsonObject.getDouble(ELEVATION_HIGH));
                            contentValues.put(Segments.ELEVATION_LOW, segmentJsonObject.getDouble(ELEVATION_LOW));
                            contentValues.put(Segments.CLIMB_CATEGORY, segmentJsonObject.getInt(CLIMB_CATEGORY));
                            contentValues.put(Segments.CITY, segmentJsonObject.getString(CITY));
                            contentValues.put(Segments.STATE, segmentJsonObject.getString(STATE));
                            contentValues.put(Segments.COUNTRY, segmentJsonObject.getString(COUNTRY));
                            contentValues.put(Segments.PRIVATE, segmentJsonObject.getBoolean(PRIVATE) ? 1 : 0);
                            contentValues.put(Segments.STARRED, segmentJsonObject.getBoolean(STARRED) ? 1 : 0);
                            contentValues.put(Segments.HAZARDOUS, segmentJsonObject.getBoolean(HAZARDOUS) ? 1 : 0);
                            if (segmentJsonObject.has(Segments.PR_TIME)) {
                                contentValues.put(Segments.PR_TIME, segmentJsonObject.getInt(Segments.PR_TIME));
                            }

                            db.insert(Segments.TABLE_STARRED_SEGMENTS, null, contentValues);
                            if (DEBUG) Log.i(TAG, "inserted segment " + segmentId);

                            getSegmentStream(segmentId);
                        }

                        notifyNewSegment(segmentId);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            } catch (ClientProtocolException e) {
                // TODO Auto-generated catch block
                Log.e(TAG, e.toString());
                e.printStackTrace();
                updateError = "ClientProtocolException";
            } catch (IOException e) {
                // TODO Auto-generated catch block
                Log.e(TAG, e.toString());
                e.printStackTrace();
                updateError = "IOException";
            } catch (JSONException e) {
                // TODO Auto-generated catch block
                Log.e(TAG, e.toString());
                e.printStackTrace();
                updateError = "JSONException";
            }
        }

        // remove no longer available segments from ALL databases!
        if (DEBUG)
            Log.i(TAG, "segmentIdSet=" + segmentIdSet + ", newSegmentIdSet=" + newSegmentIdSet);
        segmentIdSet.removeAll(newSegmentIdSet);
        deleteSegments(segmentIdSet);

        notifyGetStarredSegmentsCompleted(updateError);
    }

    private void notifyNewSegment(long segmentId) {
        if (DEBUG) Log.i(TAG, "notifyNewSegment Strava Name=" + mStravaSportName);

        // TODO: do something like 'flush' before we send the broadcast???
        Intent intent = new Intent(NEW_STARRED_SEGMENT_INTENT)
                .putExtra(SPORT_TYPE_ID, mSportTypeId)
                .putExtra(Segments.SEGMENT_ID, segmentId)
                .setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void notifySegmentUpdateStarred() {
        if (DEBUG) Log.i(TAG, "Segment update started");

        ((TrainingApplication) getApplicationContext()).setIsSegmentListUpdating(mSportTypeId, true);

        Intent intent = new Intent(SEGMENT_UPDATE_STARTED_INTENT)
                .putExtra(SPORT_TYPE_ID, mSportTypeId)
                .setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void notifyGetStarredSegmentsCompleted(String resultMessage) {
        if (DEBUG) Log.i(TAG, "updated Segment List complete: " + resultMessage);

        ((TrainingApplication) getApplicationContext()).setIsSegmentListUpdating(mSportTypeId, false);

        Intent intent = new Intent(SEGMENTS_UPDATE_COMPLETE_INTENT)
                .putExtra(SPORT_TYPE_ID, mSportTypeId)
                .putExtra(RESULT_MESSAGE, resultMessage)
                .setPackage(getPackageName());
        sendBroadcast(intent);
    }


    private void getSegmentStream(long segmentId) {
        if (DEBUG) Log.i(TAG, "getSegmentStream: segmentId=" + segmentId);

        getStream(SegmentsDatabaseManager.getInstance(this).getDatabase(), StreamType.SEGMENT, segmentId);
    }

    private void deleteSegments(@NonNull Set<Long> segmentIdSet) {
        for (long segmentId : segmentIdSet) {
            deleteSegment(segmentId);
        }
    }

    private void deleteSegment(long segmentId) {
        if (DEBUG) Log.i(TAG, "deleteSegment: segmentId=" + segmentId);

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance(this).getDatabase();

        db.delete(Segments.TABLE_SEGMENT_STREAMS, Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""});
        db.delete(Segments.TABLE_STARRED_SEGMENTS, Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""});
    }

}
