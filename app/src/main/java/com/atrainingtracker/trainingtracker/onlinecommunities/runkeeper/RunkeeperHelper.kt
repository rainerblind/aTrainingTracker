package com.atrainingtracker.trainingtracker.onlinecommunities.runkeeper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.trainingtracker.TrainingApplication


class RunkeeperHelper {

    companion object {
        val TAG = "RunkeeperHelper"
        val DEBUG = TrainingApplication.getDebug(true);

        val MY_CLIENT_ID = BuildConfig.RUNKEEPER_CLIENT_ID
        val REDIRECT_URI = "runkeeper://rainerblind.github.io"



        fun getAuthorizationUrl(): String {
            val uriBuilder = Uri.Builder()
            uriBuilder.scheme("https")
                .authority("runkeeper.com")
                .appendPath("apps")
                .appendPath("authorize")
                .appendQueryParameter("client_id", MY_CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
            return uriBuilder.build().toString()
        }


        fun requestAccessToken(context: Context) {
            if (DEBUG) Log.i(TAG, "requestAccessToken");

            // Simply, launch the Browser.
            val authUrl: String? = getAuthorizationUrl()
            if (DEBUG) Log.i(TAG, "Launching auth url: " + authUrl)
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))

            // No history for the browser step keeps the stack clean
            browserIntent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            context.startActivity(browserIntent)
        }
    }
}