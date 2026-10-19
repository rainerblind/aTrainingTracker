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
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

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
    private val DEBUG = BuildConfig.DEBUG

    internal val refreshLock = ReentrantLock()
    internal const val TOKEN_EXPIRY_MARGIN_SECONDS = 300L // 5-minute safety margin
    internal const val LOCK_WAIT_TIMEOUT_SECONDS = 15L

    private var customHttpClient: OkHttpClient? = null

    internal var httpClient: OkHttpClient
        get() = customHttpClient ?: defaultHttpClient
        set(value) {
            customHttpClient = value
        }

    private val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    internal fun resetHttpClient() {
        customHttpClient = null
    }

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
            Log.d(TAG, "storeJSONData: hasAthlete=${jsonObject.has("athlete")}, hasAccessToken=${jsonObject.has(ACCESS_TOKEN)}")
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
        } catch (e: Exception) {
            Log.e(TAG, "Error storing JSON data", e)
        }
    }

    private fun getRedirectUri(): String {
        return "strava://rainerblind.github.io"
    }

    @JvmStatic
    @JvmOverloads
    fun getAuthorizationUrl(state: String? = null): String {
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
                if (!state.isNullOrBlank()) {
                    appendQueryParameter("state", state)
                }
            }.build().toString()
        } catch (e: RuntimeException) {
            val base = "$HTTPS://$STRAVA_AUTHORITY/$OAUTH/$MOBILE/$AUTHORIZE?$CLIENT_ID=$MY_CLIENT_ID&$REDIRECT_URI=${getRedirectUri()}&$RESPONSE_TYPE=$CODE&$APPROVAL_PROMPT=$AUTO&$SCOPE=read,read_all,profile:read_all,activity:read_all,activity:write"
            if (!state.isNullOrBlank()) "$base&state=$state" else base
        }
    }

    @JvmStatic
    fun requestAccessToken(context: Context) {
        if (DEBUG) Log.i(TAG, "requestAccessToken")

        val state = StravaOAuthStateManager.generateAndSaveState(context)
        val authUrl = getAuthorizationUrl(state)
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
        val appIntent = Intent(Intent.ACTION_VIEW, intentUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch strava:// URI", e)
        }

        try {
            val webUri = Uri.parse("https://www.strava.com/activities/$activityId")
            val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open Strava activity web URL", e)
        }
    }

    internal fun isTokenValid(): Boolean {
        return try {
            val nowSec = System.currentTimeMillis() / 1000
            val expiresAt = TrainingApplication.getStravaTokenExpiresAt()
            if (nowSec + TOKEN_EXPIRY_MARGIN_SECONDS < expiresAt) {
                val token = TrainingApplication.getStravaAccessToken()
                !token.isNullOrEmpty()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading cached Strava token status", e)
            false
        }
    }

    internal fun sanitizeForLog(input: String): String {
        if (input.isBlank()) return ""
        val trimmed = if (input.length > 200) input.take(200) + "... [truncated]" else input
        return trimmed.replace(Regex("(?i)(client_secret|access_token|refresh_token)=\\w+"), "$1=***")
    }

    internal fun shouldPurgeOnRefreshError(code: Int, errorBody: String): Boolean {
        if (code != 400 && code != 401) {
            return false
        }
        if (errorBody.isBlank()) {
            return false
        }

        try {
            val json = JSONObject(errorBody)

            // 1. Check for application/client credential configuration errors -> STRICTLY DO NOT PURGE
            if (json.has("errors")) {
                val errorsArray = json.optJSONArray("errors")
                if (errorsArray != null) {
                    var hasAppError = false
                    var hasRefreshTokenInvalid = false

                    for (i in 0 until errorsArray.length()) {
                        val err = errorsArray.optJSONObject(i) ?: continue
                        val resource = err.optString("resource")
                        val field = err.optString("field")
                        val errCode = err.optString("code")

                        if (field.equals("client_secret", ignoreCase = true) ||
                            field.equals("client_id", ignoreCase = true) ||
                            resource.equals("Application", ignoreCase = true)) {
                            hasAppError = true
                        }

                        if ((resource.equals("RefreshToken", ignoreCase = true) || field.equals("refresh_token", ignoreCase = true)) &&
                            errCode.equals("invalid", ignoreCase = true)) {
                            hasRefreshTokenInvalid = true
                        }
                    }

                    if (hasAppError) {
                        Log.w(TAG, "Strava token refresh failed due to application credential configuration error; suppressing purge.")
                        return false
                    }

                    if (hasRefreshTokenInvalid) {
                        Log.w(TAG, "Strava token refresh failed: refresh token permanently invalid.")
                        return true
                    }
                }
            }

            // 2. Check top-level OAuth error field (e.g. {"error": "invalid_grant"})
            val error = json.optString("error")
            if (error.equals("invalid_grant", ignoreCase = true)) {
                Log.w(TAG, "Strava token refresh failed with invalid_grant.")
                return true
            }

            // 3. Check message or error description for explicit revocation
            val message = json.optString("message")
            val errorDesc = json.optString("error_description")
            if (message.contains("revoked", ignoreCase = true) || errorDesc.contains("revoked", ignoreCase = true)) {
                Log.w(TAG, "Strava token refresh indicates token revoked.")
                return true
            }

        } catch (e: JSONException) {
            // Non-JSON response (e.g. Cloudflare HTML error page or WAF block) -> STRICTLY DO NOT PURGE
            Log.w(TAG, "Non-JSON error response from Strava token refresh endpoint; defaulting to DO NOT PURGE.")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error parsing Strava refresh error response; defaulting to DO NOT PURGE.", e)
            return false
        }

        return false
    }

    private fun performTokenRefresh(): String? {
        val refreshUrl = "https://www.strava.com/oauth/token"
        val refreshToken = try {
            TrainingApplication.getStravaRefreshToken()
        } catch (e: Exception) {
            null
        } ?: return null

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

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body.string()
                    if (DEBUG) Log.d(TAG, "Refresh Response: success (HTTP ${response.code})")
                    val responseJson = JSONObject(responseBody)
                    storeJSONData(responseJson)
                    responseJson.optString(ACCESS_TOKEN).takeIf { it.isNotEmpty() }
                } else {
                    val code = response.code
                    val errorBody = response.body.string()
                    val sanitized = sanitizeForLog(errorBody)
                    Log.e(TAG, "Refresh failed: HTTP $code $sanitized")
                    if (shouldPurgeOnRefreshError(code, errorBody)) {
                        Log.w(TAG, "Strava authorization permanently revoked. Triggering local data purge (REQ-EXT-009).")
                        TrainingApplication.getAppContext()?.let { ctx ->
                            StravaDataPurgeManager.purgeAllStravaData(ctx, alsoRevokeRemote = false)
                        }
                    }
                    null
                }
            }
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Network timeout during Strava token refresh", e)
            null
        } catch (e: IOException) {
            Log.w(TAG, "I/O error during Strava token refresh", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token", e)
            null
        }
    }

    @JvmStatic
    fun getRefreshedAccessToken(): String? {
        if (DEBUG) Log.i(TAG, "getRefreshedAccessToken()")

        // Fast path: check without acquiring lock
        if (isTokenValid()) {
            return TrainingApplication.getStravaAccessToken()
        }

        // Bounded lock acquisition slow path: serialize refresh without deadlock/starvation
        val acquired = try {
            refreshLock.tryLock(LOCK_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Interrupted while waiting for Strava refresh lock", e)
            return null
        }

        if (!acquired) {
            Log.w(TAG, "Timed out waiting for Strava refresh lock; checking if another thread refreshed token")
            return if (isTokenValid()) TrainingApplication.getStravaAccessToken() else null
        }

        try {
            // Re-check validity inside lock (double-checked locking)
            if (isTokenValid()) {
                return TrainingApplication.getStravaAccessToken()
            }
            return performTokenRefresh()
        } finally {
            refreshLock.unlock()
        }
    }
}

