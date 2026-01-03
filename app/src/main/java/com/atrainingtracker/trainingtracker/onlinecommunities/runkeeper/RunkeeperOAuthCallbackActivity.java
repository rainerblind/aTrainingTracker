package com.atrainingtracker.trainingtracker.onlinecommunities.runkeeper;

import android.net.Uri;
import android.util.Log;

import com.atrainingtracker.BuildConfig;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseOAuthCallbackActivity;
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaHelper;

import org.json.JSONObject;


public class RunkeeperOAuthCallbackActivity extends BaseOAuthCallbackActivity {
    public static final String HTTPS = "https";
    public static final String OAUTH = "oauth";
    public static final String TOKEN = "token";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    protected static final String STRAVA_AUTHORITY = "runkeeper.com";
    protected static final String MY_CLIENT_ID = BuildConfig.STRAVA_CLIENT_ID;
    protected static final String MY_CLIENT_SECRET = BuildConfig.STRAVA_CLIENT_SECRET;
    public static final String RunkeeperOAuthSuccess = "com.atrainingtracker.trainingtracker.onlinecommunities.strava.RunkeeperOAuthSuccess";

    @Override
    protected String getRedirectUri() {
        return "runkeeper://rainerblind.github.io";
    }

    @Override
    protected String getOAuthSuccessID() {
        return RunkeeperOAuthSuccess;
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

    // override onJsonResponse if you need to save refresh tokens etc
    protected void onJsonResponse(JSONObject jsonObject) {
        Log.i(getClass().getSimpleName(), "onJsonResponse: " + jsonObject);
    }

}
