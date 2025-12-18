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

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class StravaDeauthorizationThread extends Thread {

    private static final String TAG = "StravaDeauthorizationThread";
    private static final boolean DEBUG = false;

    private final ProgressDialog progressDialog;
    private final Context mContext;

    public StravaDeauthorizationThread(Context context) {
        progressDialog = new ProgressDialog(context);
        mContext = context;
    }

    @Override
    public void run() {
        new Handler(Looper.getMainLooper()).post(() -> {
            progressDialog.setMessage(mContext.getString(R.string.deauthorization));
            if (!progressDialog.isShowing()) {
                progressDialog.show();
            }
        });

        HttpClient httpClient = new DefaultHttpClient();
        HttpPost httpPost = new HttpPost("https://www.strava.com/oauth/deauthorize");
        httpPost.addHeader("Authorization", "Bearer " + StravaHelper.getRefreshedAccessToken());

        HttpResponse httpResponse;
        try {
            httpResponse = httpClient.execute(httpPost);

            String response = EntityUtils.toString(httpResponse.getEntity());
            if (DEBUG) Log.d(TAG, "response: " + response);
            // Uri uri = Uri.parse(response);
            JSONObject responseJson = new JSONObject(response);

            if (responseJson.has("access_token")) {
                // String tokenType   = responseJson.getString(TOKEN_TYPE);
                String access_token = responseJson.getString("access_token");
                if (access_token.equals(StravaHelper.getRefreshedAccessToken())) {
                    if (DEBUG) Log.d(TAG, "all right!");
                }
            }
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        TrainingApplication.deleteStravaToken();

        new Handler(Looper.getMainLooper()).post(() -> {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }
}
