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

package com.atrainingtracker.trainingtracker.onlinecommunities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

// import org.apache.http.HttpResponse;
// import org.apache.http.client.ClientProtocolException;
// import org.apache.http.client.HttpClient;
// import org.apache.http.client.methods.HttpPost;
// import org.apache.http.impl.client.DefaultHttpClient;
// import org.apache.http.util.EntityUtils;


public abstract class BaseGetAccessTokenActivity
        extends Activity {
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
    private static final String TAG = "BaseGetAccessTokenActivity";
    private static final boolean DEBUG = TrainingApplication.getDebug(true);

    protected abstract String getAuthorizationUrl();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        // Simply, launch the Browser.
        String authUrl = getAuthorizationUrl();
        if (DEBUG) Log.i(TAG, "Launching auth url: " + authUrl);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
        // No history for the browser step keeps the stack clean
        browserIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(browserIntent);

        finish();
    }
}
