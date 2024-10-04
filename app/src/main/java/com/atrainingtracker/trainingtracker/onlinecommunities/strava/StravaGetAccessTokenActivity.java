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

import android.net.Uri;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseGetAccessTokenActivity;

import org.json.JSONObject;


public class StravaGetAccessTokenActivity
        extends BaseGetAccessTokenActivity {
    protected static final String STRAVA_AUTHORITY = "www.strava.com";
    protected static final String MY_CLIENT_ID = "344";
    protected static final String MY_CLIENT_SECRET = "272b5ca4ba09a932e73ef2574162f04d7f41a643";
    private static final String TAG = "StravaGetAccessTokenActivity";
    private static final boolean DEBUG = true;

    @Override
    protected String getAuthorizationUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(STRAVA_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(MOBILE)
                .appendPath(AUTHORIZE)
                .appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                .appendQueryParameter(REDIRECT_URI, MY_REDIRECT_URI)
                .appendQueryParameter(RESPONSE_TYPE, CODE)
                .appendQueryParameter(APPROVAL_PROMPT, AUTO)
                .appendQueryParameter(SCOPE, READ + ',' + ACTIVITY_WRITE + ',' + ACTIVITY_READ_ALL + ',' + PROFILE_READ_ALL);
        return builder.build().toString();
    }

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

    @Override
    protected String getAcceptApplicationUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(STRAVA_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(ACCEPT_APPLICATION);
        return builder.build().toString();
    }

    @Override
    protected String getName() {
        return getString(R.string.Strava);
    }

    @Override
    protected void onJsonResponse(JSONObject jsonObject) {
        StravaHelper.storeJSONData(jsonObject);
    }

}
