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

package com.atrainingtracker.trainingtracker.onlinecommunities.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.trainingtracker.TrainingApplication
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

object StravaHelper {
    const val ACCESS_TOKEN = "access_token"
    const val HTTPS = "https"
    const val TOKEN = "token"
    private const val AUTHORIZE = "authorize"
    const val OAUTH = "oauth"
    private const val MOBILE = "mobile"
    const val CODE = "code"
    const val CLIENT_ID = "client_id"
    const val CLIENT_SECRET = "client_secret"
    const val RESPONSE_TYPE = "response_type"
    const val REDIRECT_URI = "redirect_uri"
    const val GRANT_TYPE = "grant_type"
    const val REFRESH_TOKEN = "refresh_token"
    const val EXPIRES_AT = "expires_at"
    const val SCOPE = "scope"
    const val PROFILE_READ_ALL = "profile:read_all"
    private const val APPROVAL_PROMPT = "approval_prompt"
    private const val AUTO = "auto"
    private const val STRAVA_AUTHORITY = "www.strava.com"
    private val MY_CLIENT_ID = BuildConfig.STRAVA_CLIENT_ID
    private val MY_CLIENT_SECRET = BuildConfig.STRAVA_CLIENT_SECRET
    const val AUTHORIZATION = "Authorization"
    const val BEARER = "Bearer"
    
    private val TAG = StravaHelper::class.java.simpleName
    private const val DEBUG = true

    @JvmStatic
    fun translateClimbCategory(climbCategory: Int): String {
        return when (climbCategory) {
            1 -> "cat. 4"
            2 -> "cat. 3"
            3 -> "cat. 2"
            4 -> "cat. 1"
            5 -> "HC"
            else -> ""
        }
    }

    @JvmStatic
    fun storeJSONData(jsonObject: JSONObject) {
        if (DEBUG) {
            Log.i(TAG, "storeJSONData: $jsonObject")
        }
        try {
            if (jsonObject.has("athlete")) {
                val athlete = jsonObject.getJSONObject("athlete")
                val athleteId = athlete.getInt("id")
                TrainingApplication.setStravaAthleteId(athleteId)
            }
            TrainingApplication.setStravaAccessToken(jsonObject.getString(ACCESS_TOKEN))
            TrainingApplication.setStravaRefreshToken(jsonObject.getString(REFRESH_TOKEN))
            TrainingApplication.setStravaTokenExpiresAt(jsonObject.getInt(EXPIRES_AT))
        } catch (e: JSONException) {
            Log.e(TAG, "Error storing JSON data", e)
        }
    }

    private fun getRedirectUri(): String {
        return "strava://rainerblind.github.io"
    }

    @JvmStatic
    fun getAuthorizationUrl(): String {
        return try {
            Uri.Builder().apply {
                scheme(HTTPS)
                authority(STRAVA_AUTHORITY)
                appendPath(OAUTH)
                appendPath(MOBILE)
                appendPath(AUTHORIZE)
                appendQueryParameter(CLIENT_ID, MY_CLIENT_ID)
                appendQueryParameter(REDIRECT_URI, getRedirectUri())
                appendQueryParameter(RESPONSE_TYPE, CODE)
                appendQueryParameter(APPROVAL_PROMPT, AUTO)
                appendQueryParameter(SCOPE, "read,read_all,profile:read_all,activity:read_all,activity:write")
            }.build().toString()
        } catch (e: RuntimeException) {
            "$HTTPS://$STRAVA_AUTHORITY/$OAUTH/$MOBILE/$AUTHORIZE?$CLIENT_ID=$MY_CLIENT_ID&$REDIRECT_URI=${getRedirectUri()}&$RESPONSE_TYPE=$CODE&$APPROVAL_PROMPT=$AUTO&$SCOPE=read,read_all,profile:read_all,activity:read_all,activity:write"
        }
    }

    @JvmStatic
    fun requestAccessToken(context: Context) {
        if (DEBUG) Log.i(TAG, "requestAccessToken")

        val authUrl = getAuthorizationUrl()
        if (DEBUG) Log.i(TAG, "Launching Custom Tab with url: $authUrl")

        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        
        customTabsIntent.launchUrl(context, Uri.parse(authUrl))
    }

    @JvmStatic
    fun openActivity(context: Context, activityId: Long) {
        if (DEBUG) Log.i(TAG, "openActivity: $activityId")

        val intentUri = Uri.parse("strava://activities/$activityId")
        val intent = Intent(Intent.ACTION_VIEW, intentUri)

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.strava.com/activities/$activityId")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    @JvmStatic
    fun getRefreshedAccessToken(): String? {
        if (DEBUG) Log.i(TAG, "getRefreshedAccessToken()")
        
        if (System.currentTimeMillis() / 1000 < TrainingApplication.getStravaTokenExpiresAt()) {
            return TrainingApplication.getStravaAccessToken()
        }

        val refreshUrl = "https://www.strava.com/oauth/token"
        val refreshToken = TrainingApplication.getStravaRefreshToken() ?: return null
        
        val formBody = FormBody.Builder()
            .add(CLIENT_ID, MY_CLIENT_ID)
            .add(CLIENT_SECRET, MY_CLIENT_SECRET)
            .add(GRANT_TYPE, REFRESH_TOKEN)
            .add(REFRESH_TOKEN, refreshToken)
            .build()

        val request = Request.Builder()
            .url(refreshUrl)
            .post(formBody)
            .build()

        val client = OkHttpClient()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (DEBUG) Log.d(TAG, "Refresh Response: $responseBody")
                    responseBody?.let {
                        val responseJson = JSONObject(it)
                        storeJSONData(responseJson)
                        responseJson.optString(ACCESS_TOKEN, null)
                    }
                } else {
                    Log.e(TAG, "Refresh failed: ${response.code} ${response.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            null
        }
    }
}
