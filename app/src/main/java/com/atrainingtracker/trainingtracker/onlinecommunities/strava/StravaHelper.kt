/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    protected static final String READ_ALL = "read_all";
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

    @NonNull
    public static String translateClimbCategory(int climbCategory) {
        return switch (climbCategory) {
            case 1 -> "cat. 4";
            case 2 -> "cat. 3";
            case 3 -> "cat. 2";
            case 4 -> "cat. 1";
            case 5 -> "HC";
            default -> "";
        };
    }

    protected static void storeJSONData(@NonNull JSONObject jsonObject) {
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


    @NonNull
    protected static String getRedirectUri() {
        return "strava://rainerblind.github.io";
    }

    @NonNull
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
                .appendQueryParameter(SCOPE, READ + "," + READ_ALL  + "," + ACTIVITY_WRITE + ",activity:read_all");
        return builder.build().toString();
    }


    public static void requestAccessToken(@NonNull Context context) {
        if (DEBUG) Log.i(TAG, "requestAccessToken");

        // Simply, launch the Browser.
        String authUrl = getAuthorizationUrl();
        if (DEBUG) Log.i(TAG, "Launching auth url: " + authUrl);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
        // No history for the browser step keeps the stack clean
        browserIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(browserIntent);
    }

    public static void openActivity(@NonNull Context context, long activityId) {
        if (DEBUG) Log.i(TAG, "openActivity: " + activityId);

        // Try to open in the Strava app first
        Uri intentUri = Uri.parse("strava://activities/" + activityId);
        Intent intent = new Intent(Intent.ACTION_VIEW, intentUri);

        // Check if there's an app to handle this intent
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            // Fallback: Open in browser
            intentUri = Uri.parse("https://www.strava.com/activities/" + activityId);
            intent = new Intent(Intent.ACTION_VIEW, intentUri);
            context.startActivity(intent);
        }
    }

    @NonNull
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

    @Nullable
    public static String getRefreshedAccessToken() {
        if (DEBUG) Log.i(TAG, "getRefreshedAccessToken()");
        // first, check if we really need a new access token
        if (System.currentTimeMillis() / 1000 < TrainingApplication.getStravaTokenExpiresAt()) {
            return TrainingApplication.getStravaAccessToken();
        }

        String refreshUrl = "https://www.strava.com/api/v3/oauth/token";
        
        okhttp3.RequestBody formBody = new okhttp3.FormBody.Builder()
                .add(CLIENT_ID, MY_CLIENT_ID)
                .add(CLIENT_SECRET, MY_CLIENT_SECRET)
                .add(GRANT_TYPE, REFRESH_TOKEN)
                .add(REFRESH_TOKEN, TrainingApplication.getStravaRefreshToken())
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(refreshUrl)
                .post(formBody)
                .build();

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        
        try (okhttp3.Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                if (DEBUG) Log.d(TAG, "Refresh Response: " + responseBody);
                JSONObject responseJson = new JSONObject(responseBody);
                storeJSONData(responseJson);
                return responseJson.optString(ACCESS_TOKEN, null);
            } else {
                Log.e(TAG, "Refresh failed: " + response.code() + " " + response.message());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing token", e);
        }

        return null;
    }
}
