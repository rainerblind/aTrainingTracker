package com.atrainingtracker.trainingtracker.onlinecommunities.strava;

import android.net.Uri;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseGetAccessTokenActivity;

import org.json.JSONException;
import org.json.JSONObject;


public class StravaGetAccessTokenActivity
        extends BaseGetAccessTokenActivity {
    protected static final String STRAVA_AUTHORITY = "www.strava.com";
    protected static final String MY_CLIENT_ID = "344";
    protected static final String MY_CLIENT_SECRET = "272b5ca4ba09a932e73ef2574162f04d7f41a643";
    private static final String TAG = "StravaGetAccesssTokenActivity";
    private static final boolean DEBUG = false;

    @Override
    protected String getAuthorizationUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(STRAVA_AUTHORITY)
                .appendPath(OAUTH)
                .appendPath(AUTHORIZE)
                .appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                .appendQueryParameter(REDIRECT_URI, MY_REDIRECT_URI)
                .appendQueryParameter(RESPONSE_TYPE, CODE)
                .appendQueryParameter(APPROVAL_PROMPT, FORCE)
                .appendQueryParameter(SCOPE, WRITE);
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
        try {
            JSONObject athlete = jsonObject.getJSONObject("athlete");
            int athleteId = athlete.getInt("id");
            TrainingApplication.setStravaAthleteId(athleteId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
