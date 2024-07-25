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
    protected static final String MY_REDIRECT_URI = "https://rainer-blind.de";  // must not be changed because strava checks this uri
    private static final String TAG = "BaseGetAccessTokenActivity";
    private static final boolean DEBUG = TrainingApplication.getDebug(false);
    private ProgressDialog dialog;
    private boolean showDialog = true;  // TODO: bad name!

    protected abstract String getAuthorizationUrl();

    protected abstract String getAccessUrl(String code);

    protected UrlEncodedFormEntity getAccessUrlEncodedFormEntity(String code) {
        return null;
    }

    protected abstract String getAcceptApplicationUrl();

    protected abstract String getName();

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate");

        dialog = new ProgressDialog(this);
        dialog.setMessage(getString(R.string.connecting_to_string, getName()));
        dialog.show();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        final WebView webview = new WebView(this);

        webview.getSettings().setJavaScriptEnabled(true);
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (DEBUG) Log.d(TAG, "shouldOverrideUrlLoading: " + url);
                if (url.startsWith(MY_REDIRECT_URI)) {

                    dialog.setMessage(getString(R.string.please_wait));
                    if (!dialog.isShowing()) {
                        dialog.show();
                    }

                    Uri uri = Uri.parse(url);
                    String code = uri.getQueryParameter(CODE);
                    if (DEBUG) Log.d(TAG, "we got the code: " + code);

                    new GetAccessTokenThread(code).start();
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                if (DEBUG) Log.d(TAG, "onLoadResource: " + url);

                if (url.startsWith(getAcceptApplicationUrl())) {
                    dialog.setMessage(getString(R.string.please_wait));
                    if (!dialog.isShowing()) {
                        dialog.show();
                    }
                }
            }

        });

        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (showDialog && dialog.isShowing() && newProgress >= 50) {
                    dialog.dismiss();
                    showDialog = false;
                }
            }

        });

        if (DEBUG) Log.i(TAG, "authorization url = " + getAuthorizationUrl());

        webview.loadUrl(getAuthorizationUrl());

        setContentView(webview);
    }

    protected void onJsonResponse(JSONObject jsonObject) {
    }

    class GetAccessTokenThread extends Thread {
        final String code;
        public GetAccessTokenThread(String code) {
            this.code = code;
        }

        @Override
        public void run() {
            HttpPost httpPost = new HttpPost(getAccessUrl(code));

            httpPost.setHeader(HTTP.CONTENT_TYPE, "application/x-www-form-urlencoded");

            UrlEncodedFormEntity urlEncodedFormEntity = getAccessUrlEncodedFormEntity(code);
            if (urlEncodedFormEntity != null) {
                httpPost.setEntity(urlEncodedFormEntity);
            }

            String result = null;
            HttpClient httpClient = new DefaultHttpClient();
            try {
                HttpResponse httpResponse = httpClient.execute(httpPost);

                String response = EntityUtils.toString(httpResponse.getEntity());

                // Uri uri = Uri.parse(response);
                JSONObject responseJson = new JSONObject(response);

                onJsonResponse(responseJson);

                if (responseJson.has(ACCESS_TOKEN)) {
                    // String tokenType   = responseJson.getString(TOKEN_TYPE);
                    result = responseJson.getString(ACCESS_TOKEN);
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

            final String accessToken = result;
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent resultIntent = new Intent();
                if (accessToken == null) { // something went wrong
                    setResult(Activity.RESULT_CANCELED, resultIntent);
                } else {
                    resultIntent.putExtra(ACCESS_TOKEN, accessToken);
                    setResult(Activity.RESULT_OK, resultIntent);
                }

                if (dialog.isShowing()) {
                    dialog.dismiss();
                }

                finish();
            });
        }
    }
}
