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

import android.net.Uri;

import androidx.annotation.NonNull;

import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseOAuthCallbackActivity;

import org.json.JSONObject;


public class StravaOAuthCallbackActivity extends BaseOAuthCallbackActivity {
    public static final String HTTPS = "https";
    public static final String OAUTH = "oauth";
    public static final String TOKEN = "token";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    protected static final String STRAVA_AUTHORITY = "www.strava.com";
    protected static final String MY_CLIENT_ID = BuildConfig.STRAVA_CLIENT_ID;
    protected static final String MY_CLIENT_SECRET = BuildConfig.STRAVA_CLIENT_SECRET;
    public static final String StravaOAuthSuccess = "com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaOAuthSuccess";

    @NonNull
    @Override
    protected String getRedirectUri() {
        return "strava://rainerblind.github.io";
    }

    @NonNull
    @Override
    protected String getOAuthSuccessID() {
        return StravaOAuthSuccess;
    }


    @NonNull
    @Override
    protected String getAccessUrl(String code) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(STRAVA_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(TOKEN)
                .appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                .appendQueryParameter(CLIENT_SECRET, MY_CLIENT_SECRET)
                .appendQueryParameter(CODE, code);
        return builder.build().toString();
    }

    // override onJsonResponse if you need to save refresh tokens etc
    protected void onJsonResponse(@NonNull JSONObject jsonObject) {
        StravaHelper.storeJSONData(jsonObject);
    }

}
