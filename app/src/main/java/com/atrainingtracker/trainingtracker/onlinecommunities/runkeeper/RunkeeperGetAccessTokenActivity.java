package com.atrainingtracker.trainingtracker.onlinecommunities.runkeeper;


import android.net.Uri;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.onlinecommunities.BaseGetAccessTokenActivity;


public class RunkeeperGetAccessTokenActivity
        extends BaseGetAccessTokenActivity {
    protected static final String RUNKEEPER_AUTHORITY = "runkeeper.com";
    protected static final String MY_CLIENT_ID = "218435e7bc7f4ca1a43def3ab9589bda";
    protected static final String MY_CLIENT_SECRET = "199c2e26b25e457a97b048977f02a732";
    private static final String TAG = "RunkeeperGetAcesssTokenActivity";
    private static final boolean DEBUG = false;

    @Override
    protected String getAuthorizationUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(RUNKEEPER_AUTHORITY)
                .appendPath(APPS)
                .appendPath(AUTHORIZE)
                .appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                .appendQueryParameter(RESPONSE_TYPE, CODE)
                .appendQueryParameter(REDIRECT_URI, MY_REDIRECT_URI);
        return builder.build().toString();
    }

    @Override
    protected String getAccessUrl(String code) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(RUNKEEPER_AUTHORITY)
                .appendPath(APPS)
                .appendPath(TOKEN)
                .appendQueryParameter(GRANT_TYPE, AUTHORIZATION_CODE)
                .appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                .appendQueryParameter(CLIENT_SECRET, MY_CLIENT_SECRET)
                .appendQueryParameter(REDIRECT_URI, MY_REDIRECT_URI)
                .appendQueryParameter(CODE, code);
        return builder.build().toString();
    }

    @Override
    protected String getAcceptApplicationUrl() {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme(HTTPS)
                .authority(RUNKEEPER_AUTHORITY)  // TODO: was www.runkeeper.com
                .appendPath(OAUTH)
                .appendPath(ACCEPT_APPLICATION);
        return builder.build().toString();
        // return "https://www.runkeeper.com/oauth/accept_application";
    }

    @Override
    protected String getName() {
        return getString(R.string.Runkeeper);
    }

}
