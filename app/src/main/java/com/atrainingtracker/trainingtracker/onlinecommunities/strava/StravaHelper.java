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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.banalservice.BSportType;
import com.atrainingtracker.banalservice.database.SportTypeDatabaseManager;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.segments.SegmentsDatabaseManager;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Created by rainer on 31.08.16.
 */

public class StravaHelper {
    // TODO: define these constants somewhere better...
    public static final String ACCESS_TOKEN = "access_token";
    public static final String HTTPS = "https";
    public static final String TOKEN = "token";
    protected static final String AUTHORIZE = "authorize";
    public static final String OAUTH = "oauth";
    protected static final String MOBILE = "mobile";
    protected static final String CODE = "code";
    protected static final String AUTHORIZATION_CODE = "authorization_code";
    protected static final String ACCEPT_APPLICATION = "accept_application";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    protected static final String RESPONSE_TYPE = "response_type";
    protected static final String REDIRECT_URI = "redirect_uri";
    public static final String GRANT_TYPE = "grant_type";
    protected static final String TOKEN_TYPE = "token_type";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String EXPIRES_AT = "expires_at";
    protected static final String SCOPE = "scope";
    protected static final String WRITE = "write";
    protected static final String FILE_WRITE = "file:write";
    protected static final String ACTIVITY_WRITE = "activity:write";
    protected static final String ACTIVITY_READ_ALL = "activity:read_all";
    protected static final String PROFILE_READ_ALL = "profile:read_all";
    protected static final String READ = "read";
    protected static final String APPROVAL_PROMPT = "approval_prompt";
    protected static final String FORCE = "force";
    protected static final String AUTO = "auto";
    protected static final String APPS = "apps";
    protected static final String STRAVA_AUTHORITY = "www.strava.com";
    protected static final String MY_CLIENT_ID = BuildConfig.STRAVA_CLIENT_ID;
    protected static final String MY_CLIENT_SECRET = BuildConfig.STRAVA_CLIENT_SECRET;
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer";
    protected static final String ID = "id";
    private static final String TAG = StravaHelper.class.getSimpleName();
    private static final boolean DEBUG = true; // TrainingApplication.getDebug(true);

    public static String translateClimbCategory(int climbCategory) {
        switch (climbCategory) {
            case 1:
                return "cat. 4";

            case 2:
                return "cat. 3";

            case 3:
                return "cat. 2";

            case 4:
                return "cat. 1";

            case 5:
                return "HC";

            default:
                return "";
        }
    }

    protected static void storeJSONData(JSONObject jsonObject) {
        if (DEBUG) {
            Log.i(TAG, "string JSON response: " + jsonObject);
        }
        try {
            if (jsonObject.has("athlete")) {
                JSONObject athlete = jsonObject.getJSONObject("athlete");
                int athleteId = athlete.getInt("id");
                TrainingApplication.setStravaAthleteId(athleteId);
            }
            TrainingApplication.setStravaAccessToken(jsonObject.getString(ACCESS_TOKEN));
            TrainingApplication.setStravaRefreshToken(jsonObject.getString(REFRESH_TOKEN));
            TrainingApplication.setStravaTokenExpiresAt(jsonObject.getInt(EXPIRES_AT));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    protected static String getRedirectUri() {
        return "strava://rainerblind.github.io";
    }

    protected static String getAuthorizationUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(STRAVA_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(MOBILE)
                .appendPath(AUTHORIZE)
                .appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                .appendQueryParameter(REDIRECT_URI, getRedirectUri())
                .appendQueryParameter(RESPONSE_TYPE, CODE)
                .appendQueryParameter(APPROVAL_PROMPT, AUTO)
                .appendQueryParameter(SCOPE, READ + ',' + ACTIVITY_WRITE + ',' + ACTIVITY_READ_ALL + ',' + PROFILE_READ_ALL);
        return builder.build().toString();
    }


    public static void requestAccessToken(Context context) {
        if (DEBUG) Log.i(TAG, "requestAccessToken");

        // Simply, launch the Browser.
        String authUrl = getAuthorizationUrl();
        if (DEBUG) Log.i(TAG, "Launching auth url: " + authUrl);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
        // No history for the browser step keeps the stack clean
        browserIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(browserIntent);
    }

    protected static String getRefreshUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(STRAVA_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(TOKEN)
                .appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                .appendQueryParameter(CLIENT_SECRET, MY_CLIENT_SECRET)
                .appendQueryParameter(GRANT_TYPE, REFRESH_TOKEN)
                .appendQueryParameter(REFRESH_TOKEN, TrainingApplication.getStravaRefreshToken());
        return builder.build().toString();
    }

    public static String getRefreshedAccessToken() {
        if (DEBUG) Log.i(TAG, "getRefreshedAccessToken()");
        // first, check if we really need a new access token
        if (System.currentTimeMillis() / 1000 < TrainingApplication.getStravaTokenExpiresAt()) {
            return TrainingApplication.getStravaAccessToken();
        }

        String refreshUrl = getRefreshUrl();
        if (refreshUrl == null) {
            return null;
        }

        HttpPost httpPost = new HttpPost(refreshUrl);
        HttpClient httpClient = new DefaultHttpClient();
        try {
            HttpResponse httpResponse = httpClient.execute(httpPost);

            if (DEBUG) Log.i(TAG, "HTTP status: " + httpResponse.getStatusLine());

            String response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "response: " + response);
            // Uri uri = Uri.parse(response);
            JSONObject responseJson = new JSONObject(response);
            storeJSONData(responseJson);

            if (responseJson.has(ACCESS_TOKEN)) {
                // String tokenType   = responseJson.getString(TOKEN_TYPE);
                return responseJson.getString(ACCESS_TOKEN);
            }
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return null;
    }

    /* returns the athleteId stored in the shared preferences.  If this is not available (== 0), get the id from strava in the background and also get the segments
     * But this method still returns 0. */
    public int getAthleteId(Context context) {
        int athleteId = TrainingApplication.getStravaAthleteId();

        if (athleteId == 0) {
            new GetAthleteIdFromStravaThread(context).start();
        }

        return athleteId;
    }

    class GetAthleteIdFromStravaThread extends Thread {
        Context mContext;

        GetAthleteIdFromStravaThread(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            HttpClient httpClient = new DefaultHttpClient();
            HttpGet httpGet = new HttpGet("https://www.strava.com/api/v3/athlete");
            httpGet.addHeader(AUTHORIZATION, BEARER + " " + getRefreshedAccessToken());

            HttpResponse httpResponse;
            try {
                httpResponse = httpClient.execute(httpGet);

                String response = EntityUtils.toString(httpResponse.getEntity());
                if (DEBUG) Log.d(TAG, "getAthleteId response: " + response);

                JSONObject jsonObject = new JSONObject(response);

                if (jsonObject.has(ID)) {
                    if (DEBUG) Log.i(TAG, "got own athleteId: " + jsonObject.getInt(ID));
                    TrainingApplication.setStravaAthleteId(jsonObject.getInt(ID));

                    // ok, we got a new strava id, so we update the segments database
                    if (!SegmentsDatabaseManager.doesDatabaseExist(mContext)) {  // but there is not yet a database for the segments
                        StravaSegmentsHelper stravaSegmentsHelper = new StravaSegmentsHelper(mContext);
                        stravaSegmentsHelper.getStarredStravaSegments(SportTypeDatabaseManager.getSportTypeId(BSportType.BIKE));
                        stravaSegmentsHelper.getStarredStravaSegments(SportTypeDatabaseManager.getSportTypeId(BSportType.RUN));
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
