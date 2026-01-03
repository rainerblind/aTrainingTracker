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
    public static final String REQUEST_UPDATE_LEADERBOARD = StravaSegmentsIntentService.class.getName() + ".REQUEST_UPDATE_LEADERBOARD";
    public static final String SEGMENT_ID = StravaSegmentsIntentService.class.getName() + ".SEGMENT_ID";
    public static final String SEGMENT_UPDATE_STARTED_INTENT = StravaSegmentsIntentService.class.getName() + ".SEGMENT_UPDATE_STARTED_INTENT";
    public static final String NEW_STARRED_SEGMENT_INTENT = StravaSegmentsIntentService.class.getName() + ".NEW_STARRED_SEGMENT_INTENT";       // send when there is a new or updated entry in the list of segments
    public static final String SEGMENTS_UPDATE_COMPLETE_INTENT = StravaSegmentsIntentService.class.getName() + ".SEGMENTS_UPDATE_COMPLETE_INTENT";  // send when all segment are handled
    public static final String NEW_LEADERBOARD_ENTRY_INTENT = StravaSegmentsIntentService.class.getName() + ".NEW_LEADERBOARD_ENTRY_INTENT";     // send when there is a new entry in the leaderboard
    public static final String LEADERBOARD_UPDATE_COMPLETE_INTENT = StravaSegmentsIntentService.class.getName() + ".LEADERBOARD_UPDATE_COMPLETE_INTENT";        // send when the leaderboard is completely updated
    public static final String RESULT_MESSAGE = StravaSegmentsIntentService.class.getName() + ".RESULT_MESSAGE";
    // protected static final String URL_STRAVA_STARRED_SEGMENTS = "https://www.strava.com/api/v3/segments/starred";
    // protected static final String URL_STRAVA_SEGMENT_LEADERBOARD = "https://www.strava.com/api/v3/segments/%i/leaderboard";
    protected static final String HTTPS = "https";
    protected static final String AUTHORITY_STRAVA = "www.strava.com";
    protected static final String API = "api";
    protected static final String V3 = "v3";
    protected static final String SEGMENTS = "segments";
    protected static final String LEADERBOARD = "leaderboard";
    protected static final String PAGE = "page";
    protected static final String PER_PAGE = "per_page";
    protected static final int PER_PAGE_DEFAULT = 10;  // first 10

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // for streams:
    protected static final String STREAMS = "streams";
    protected static final String SERIES_TYPE = "series_type";
    protected static final String TIME = "time";
    protected static final String AUTHORIZATION = "Authorization";
    protected static final String BEARER = "Bearer";
    protected static final String CONTEXT_ENTRIES = "context_entries";
    protected static final int DEFAULT_CONTEXT_ENTRIES = 5;
    protected static final String ENTRY_COUNT = "entry_count";
    protected static final String ENTRIES = "entries";
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
    private String mStravaSportName = null;
    private long mSegmentId = -1;

    public StravaSegmentsIntentService() {
        super("StravaSegmentsIntentService");
    }

    private static void getStream(StreamType streamType, long id) {
        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();

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
        String leaderboardUrl = builder.build().toString();

        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(leaderboardUrl);
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
                }
                if ("velocity_smooth".equals(type)) {
                    myType = Segments.VELOCITY_SMOOTH;
                    isDouble = true;
                } else if ("distance".equals(type)) {
                    myType = Segments.DISTANCE;
                    isDouble = true;
                } else if ("altitude".equals(type)) {
                    myType = Segments.ALTITUDE;
                    isDouble = true;
                } else if ("heartrate".equals(type)) {
                    myType = Segments.HEART_RATE;
                } else if ("cadence".equals(type)) {
                    myType = Segments.CADENCE;
                } else if ("watts".equals(type)) {
                    myType = Segments.WATTS;
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
                for (int j = 0; j < effortStreams.length; j++) {
                    prevTime = curTime;
                    curTime = effortStreams[j].getAsInteger("time");
                    effortStreams[j].remove("time");
                    effortStreams[j].put(streamType.idName, id);
                    for (int delta = 1; delta <= curTime - prevTime; delta++) {
                        db.insert(streamType.table, null, effortStreams[j]);
                    }
                }
            } else {
                for (int j = 0; j < effortStreams.length; j++) {
                    effortStreams[j].put(streamType.idName, id);
                    db.insert(streamType.table, null, effortStreams[j]);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        SegmentsDatabaseManager.getInstance().closeDatabase();
    }

    protected static void deleteEffortStream(long effortId) {
        if (DEBUG) Log.i(TAG, "deleteEffortStream: effortId=" + effortId);

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();

        db.delete(Segments.TABLE_EFFORT_STREAMS,
                Segments.EFFORT_ID + "=?", new String[]{effortId + ""});

        SegmentsDatabaseManager.getInstance().closeDatabase();
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

        if (mSegmentId > -1) {
            notifyGetLeaderboardComplete("background service destroyed");
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle bundle = intent.getExtras();
        String requestType = bundle.getString(REQUEST_TYPE);

        if (REQUEST_UPDATE_STARRED_SEGMENTS.equals(requestType)) {
            mSportTypeId = bundle.getLong(SPORT_TYPE_ID);
            mStravaSportName = SportTypeDatabaseManager.getStravaName(mSportTypeId);
            getStarredSegments();
            mSportTypeId = -1;
        } else if (REQUEST_UPDATE_LEADERBOARD.equals(requestType)) {
            mSegmentId = bundle.getLong(SEGMENT_ID);
            getSegmentLeaderboard();
            mSegmentId = -1;
        } else {
            Log.i(TAG, "unknown request type:" + requestType);
        }
    }

    private void getStarredSegments() {
        if (DEBUG) Log.d(TAG, "getStarredStravaSegments: sportTypeId=" + mSportTypeId);

        notifySegmentUpdateStarred();

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();

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


                        mSegmentId = segmentJsonObject.getInt(ID);
                        newSegmentIdSet.add(mSegmentId);

                        if (segmentIdSet.contains(mSegmentId)) {
                            // nothing to do?
                        } else {

                            contentValues.clear();
                            contentValues.put(Segments.SEGMENT_ID, mSegmentId);

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

                            db.insert(Segments.TABLE_STARRED_SEGMENTS, null, contentValues);
                            if (DEBUG) Log.i(TAG, "inserted segment " + mSegmentId);

                            getSegmentStream();
                        }

                        getSegmentLeaderboard();

                        notifyNewSegment();

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

        // close the database
        SegmentsDatabaseManager.getInstance().closeDatabase();

        // remove no longer available segments from ALL databases!
        if (DEBUG)
            Log.i(TAG, "segmentIdSet=" + segmentIdSet + ", newSegmentIdSet=" + newSegmentIdSet);
        segmentIdSet.removeAll(newSegmentIdSet);
        deleteSegments(segmentIdSet);

        notifyGetStarredSegmentsCompleted(updateError);
    }

    private void notifyNewSegment() {
        if (DEBUG) Log.i(TAG, "notifyNewSegment Strava Name=" + mStravaSportName);

        // TODO: do something like 'flush' before we send the broadcast???
        Intent intent = new Intent(NEW_STARRED_SEGMENT_INTENT)
                .putExtra(SPORT_TYPE_ID, mSportTypeId)
                .putExtra(Segments.SEGMENT_ID, mSegmentId)
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

    private void getSegmentLeaderboard() {
        if (DEBUG) Log.i(TAG, "getSegmentLeaderboard: segmentId=" + mSegmentId);

        String resultMessage = null;

        ((TrainingApplication) getApplicationContext()).setIsLeaderboardUpdating(mSegmentId, true);

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();

        // delete current leaderboard
        db.delete(Segments.TABLE_SEGMENT_LEADERBOARD, Segments.SEGMENT_ID + "=?", new String[]{mSegmentId + ""});


        // "https://www.strava.com/api/v3/segments/%i/leaderboard";
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(AUTHORITY_STRAVA)
                .appendPath(API)
                .appendPath(V3)
                .appendPath(SEGMENTS)
                .appendPath(mSegmentId + "")
                .appendPath(LEADERBOARD)
                .appendQueryParameter(PER_PAGE, PER_PAGE_DEFAULT + "")
                .appendQueryParameter(CONTEXT_ENTRIES, DEFAULT_CONTEXT_ENTRIES + "");
        String leaderboardUrl = builder.build().toString();

        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(leaderboardUrl);
        httpGet.addHeader(AUTHORIZATION, BEARER + " " + StravaHelper.getRefreshedAccessToken());

        HttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpGet);

            String response;
            response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "getAndInsertSegmentLeaderboard response: " + response);


            JSONObject segmentLeaderboard = new JSONObject(response);

            ContentValues cvSegmentSummary = new ContentValues();
            cvSegmentSummary.put(Segments.LEADERBOARD_SIZE, segmentLeaderboard.getInt(ENTRY_COUNT));

            JSONArray leaderboardEntries = segmentLeaderboard.getJSONArray(ENTRIES);

            ContentValues contentValues = new ContentValues();

            boolean gapFound = false;
            for (int i = 0; i < leaderboardEntries.length(); i++) {
                JSONObject entry = leaderboardEntries.getJSONObject(i);

                int rank = entry.getInt("rank");

                if (!gapFound && rank > i + 1) {
                    if (DEBUG) Log.i(TAG, "gap found at i=" + i + ", rank=" + rank);
                    gapFound = true;

                    // insert an empty entry
                    contentValues.clear();
                    contentValues.put(Segments.SEGMENT_ID, mSegmentId);
                    contentValues.put(Segments.RANK, i + 1);
                    contentValues.put(Segments.ATHLETE_ID, -1);  // add an invalid athleteId
                    db.insert(Segments.TABLE_SEGMENT_LEADERBOARD, null, contentValues);
                }

                contentValues.clear();
                contentValues.put(Segments.SEGMENT_ID, mSegmentId);
                contentValues.put(Segments.ATHLETE_NAME, entry.getString("athlete_name"));
                contentValues.put(Segments.ATHLETE_ID, entry.getInt("athlete_id"));
                contentValues.put(Segments.ATHLETE_GENDER, entry.getString("athlete_gender"));

                if (entry.has("average_hr") && !entry.isNull("average_hr")) {
                    contentValues.put(Segments.AVERAGE_HR, entry.getDouble("average_hr"));
                }
                if (entry.has("average_watts") && !entry.isNull("average_watts")) {
                    contentValues.put(Segments.AVERAGE_WATTS, entry.getDouble("average_watts"));
                }

                contentValues.put(Segments.DISTANCE, entry.getDouble("distance"));
                contentValues.put(Segments.ELAPSED_TIME, entry.getInt("elapsed_time"));
                contentValues.put(Segments.MOVING_TIME, entry.getInt("moving_time"));
                contentValues.put(Segments.START_TIME, entry.getString("start_date"));
                contentValues.put(Segments.START_TIME_LOCAL, entry.getString("start_date_local"));
                contentValues.put(Segments.ACTIVITY_ID, entry.getInt("activity_id"));
                contentValues.put(Segments.EFFORT_ID, entry.getInt("effort_id"));

                contentValues.put(Segments.RANK, rank);
                contentValues.put(Segments.ATHLETE_PROFILE_URL, entry.getString("athlete_profile"));  // "http://pics.com/227615/large.jpg"
                db.insert(Segments.TABLE_SEGMENT_LEADERBOARD, null, contentValues);

                // check whether this is the current athlete
                if (entry.getInt("athlete_id") == new StravaHelper().getAthleteId(this)) {
                    if (DEBUG) Log.i(TAG, "update own athlete");
                    contentValues.clear();
                    contentValues.put(Segments.PR_DATE, entry.getString("start_date_local"));
                    contentValues.put(Segments.PR_TIME, entry.getInt("elapsed_time"));   // or moving time???
                    contentValues.put(Segments.OWN_RANK, entry.getInt("rank"));
                    db.update(Segments.TABLE_STARRED_SEGMENTS, contentValues, Segments.SEGMENT_ID + "=?", new String[]{mSegmentId + ""});
                }
                // get picture from URL and store it in some cache
                // Picasso.with(this).load(entry.getString("athlete_profile")).fetch();

                notifyNewLeaderboardEntry();

            }

            // finally, store the date of this update
            contentValues.clear();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            contentValues.put(Segments.LAST_UPDATED, sdf.format(new Date()));
            db.update(Segments.TABLE_STARRED_SEGMENTS, contentValues, Segments.SEGMENT_ID + "=?", new String[]{mSegmentId + ""});

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
            resultMessage = "ClientProtocolException";
        } catch (IOException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
            resultMessage = "IOException";
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
            resultMessage = "JSONException";
        }

        SegmentsDatabaseManager.getInstance().closeDatabase();

        notifyGetLeaderboardComplete(resultMessage);
    }

    private void notifyNewLeaderboardEntry() {
        if (DEBUG) Log.i(TAG, "notifyNewLeaderboardEntry");

        Intent intent = new Intent(NEW_LEADERBOARD_ENTRY_INTENT)
                .putExtra(Segments.SEGMENT_ID, mSegmentId)
                .setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void notifyGetLeaderboardComplete(String resultMessage) {
        if (DEBUG) Log.i(TAG, "notify Get Leaderboard complete: " + resultMessage);

        ((TrainingApplication) getApplicationContext()).setIsLeaderboardUpdating(mSegmentId, false);

        Intent intent = new Intent(LEADERBOARD_UPDATE_COMPLETE_INTENT)
                .putExtra(Segments.SEGMENT_ID, mSegmentId)
                .putExtra(RESULT_MESSAGE, resultMessage)
                .setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void getSegmentStream() {
        if (DEBUG) Log.i(TAG, "getSegmentStream: segmentId=" + mSegmentId);

        getStream(StreamType.SEGMENT, mSegmentId);
    }

    private void getEffortStream(long effortId) {
        if (DEBUG) Log.i(TAG, "getEffortStream: effortId=" + effortId);
        getStream(StreamType.SEGMENT_EFFORT, effortId);
    }

    private void deleteSegments(Set<Long> segmentIdSet) {
        for (long segmentId : segmentIdSet) {
            deleteSegment(segmentId);
        }
    }

    private void deleteSegment(long segmentId) {
        if (DEBUG) Log.i(TAG, "deleteSegment: segmentId=" + segmentId);

        SQLiteDatabase db = SegmentsDatabaseManager.getInstance().getOpenDatabase();

        Cursor cursor = db.query(Segments.TABLE_SEGMENT_LEADERBOARD, null,
                Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""},
                null, null, null);
        while (cursor.moveToNext()) {
            deleteEffortStream(cursor.getInt(cursor.getColumnIndex(Segments.EFFORT_ID)));
        }

        db.delete(Segments.TABLE_SEGMENT_STREAMS, Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""});
        db.delete(Segments.TABLE_SEGMENT_LEADERBOARD, Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""});
        db.delete(Segments.TABLE_STARRED_SEGMENTS, Segments.SEGMENT_ID + "=?", new String[]{segmentId + ""});

        SegmentsDatabaseManager.getInstance().closeDatabase();
    }

}
