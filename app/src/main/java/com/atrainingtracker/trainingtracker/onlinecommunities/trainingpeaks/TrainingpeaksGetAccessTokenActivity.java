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

package com.atrainingtracker.trainingtracker.onlinecommunities.trainingpeaks;

import android.net.Uri;
import android.util.Log;

import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseGetAccessTokenActivity;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


public class TrainingpeaksGetAccessTokenActivity
        extends BaseGetAccessTokenActivity {
    public static final String MY_CLIENT_ID = BuildConfig.TRAININGPEAKS_CLIENT_ID;
    private static final String MY_CLIENT_SECRET = BuildConfig.TRAININGPEAKS_CLIENT_SECRET;
    private static final String TAG = TrainingpeaksGetAccessTokenActivity.class.getName();
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private static final String TRAININGPEAKS_AUTHORITY = "oauth.trainingpeaks.com";

    @Override
    protected String getAuthorizationUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(TRAININGPEAKS_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(AUTHORIZE)
                .appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                .appendQueryParameter(RESPONSE_TYPE, CODE)
                .appendQueryParameter(SCOPE, FILE_WRITE)
                .appendQueryParameter(REDIRECT_URI, MY_REDIRECT_URI);

        return builder.build().toString();
    }

    @Override
    protected String getAccessUrl(String code) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(TRAININGPEAKS_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(TOKEN);
        return builder.build().toString();
    }

    @Override
    protected UrlEncodedFormEntity getAccessUrlEncodedFormEntity(String code) {
        List<NameValuePair> nameValuePairs = new ArrayList<>();
        nameValuePairs.add(new BasicNameValuePair(GRANT_TYPE, AUTHORIZATION_CODE));
        nameValuePairs.add(new BasicNameValuePair(CLIENT_ID, MY_CLIENT_ID));
        nameValuePairs.add(new BasicNameValuePair(CLIENT_SECRET, MY_CLIENT_SECRET));
        nameValuePairs.add(new BasicNameValuePair(REDIRECT_URI, MY_REDIRECT_URI));
        nameValuePairs.add(new BasicNameValuePair(CODE, code));
        // .appendQueryParameter(CODE,          code.replaceAll("!", "%21"));
        try {
            return new UrlEncodedFormEntity(nameValuePairs);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected String getRefreshUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(TRAININGPEAKS_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(TOKEN);
        return builder.build().toString();
    }

    protected UrlEncodedFormEntity getRefreshUrlEncodedFormEntity() {
        List<NameValuePair> nameValuePairs = new ArrayList<>();
        nameValuePairs.add(new BasicNameValuePair(CLIENT_ID, MY_CLIENT_ID));
        nameValuePairs.add(new BasicNameValuePair(CLIENT_SECRET, MY_CLIENT_SECRET));
        nameValuePairs.add(new BasicNameValuePair(GRANT_TYPE, REFRESH_TOKEN));
        nameValuePairs.add(new BasicNameValuePair(REFRESH_TOKEN, TrainingApplication.getTrainingPeaksRefreshToken()));
        try {
            return new UrlEncodedFormEntity(nameValuePairs);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected String getAcceptApplicationUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(TRAININGPEAKS_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(ACCEPT_APPLICATION);
        return builder.build().toString();
    }

    @Override
    protected String getName() {
        return getString(R.string.TrainingPeaks);
    }

    @Override
    protected void onJsonResponse(JSONObject jsonObject) {
        try {
            if (jsonObject.has(REFRESH_TOKEN)) {
                if (DEBUG) Log.i(TAG, "FTW: we have the refresh token!");
                TrainingApplication.setTrainingPeaksRefreshToken(jsonObject.getString(REFRESH_TOKEN));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getRefreshedAccessToken() {
        if (DEBUG) Log.i(TAG, "getRefreshedAccessToken()");

        String refreshUrl = getRefreshUrl();
        if (refreshUrl == null) {
            return null;
        }

        HttpPost httpPost = new HttpPost(refreshUrl);

        httpPost.setHeader(HTTP.CONTENT_TYPE, "application/x-www-form-urlencoded");

        UrlEncodedFormEntity urlEncodedFormEntity = getRefreshUrlEncodedFormEntity();
        if (urlEncodedFormEntity != null) {
            httpPost.setEntity(urlEncodedFormEntity);
        }

        HttpClient httpClient = new DefaultHttpClient();
        try {
            HttpResponse httpResponse = httpClient.execute(httpPost);

            if (DEBUG) Log.i(TAG, "HTTP status: " + httpResponse.getStatusLine());

            String response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "response: " + response);
            // Uri uri = Uri.parse(response);
            JSONObject responseJson = new JSONObject(response);

            /// ???? onJsonResponse(responseJson);

            if (responseJson.has(REFRESH_TOKEN)) {
                TrainingApplication.setTrainingPeaksRefreshToken(responseJson.getString(REFRESH_TOKEN));
            }

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


}
