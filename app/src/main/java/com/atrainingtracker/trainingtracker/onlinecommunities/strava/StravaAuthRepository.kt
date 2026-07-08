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

import android.util.Log
import com.atrainingtracker.BuildConfig
import com.atrainingtracker.trainingtracker.TrainingApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

sealed class StravaAuthState {
    object Idle : StravaAuthState()
    object Loading : StravaAuthState()
    data class Success(val token: String) : StravaAuthState()
    data class Error(val message: String) : StravaAuthState()
}

class StravaAuthRepository private constructor() {

    private val _authState = MutableStateFlow<StravaAuthState>(StravaAuthState.Idle)
    val authState = _authState.asStateFlow()

    private val client = OkHttpClient()

    suspend fun exchangeCodeForToken(code: String) {
        _authState.value = StravaAuthState.Loading
        
        withContext(Dispatchers.IO) {
            val url = UriBuilder().apply {
                scheme(StravaHelper.HTTPS)
                authority("www.strava.com")
                appendPath(StravaHelper.OAUTH)
                appendPath(StravaHelper.TOKEN)
            }.build().toString()

            val formBody = FormBody.Builder()
                .add(StravaHelper.CLIENT_ID, BuildConfig.STRAVA_CLIENT_ID)
                .add(StravaHelper.CLIENT_SECRET, BuildConfig.STRAVA_CLIENT_SECRET)
                .add(StravaHelper.CODE, code)
                .add(StravaHelper.GRANT_TYPE, "authorization_code")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            StravaHelper.storeJSONData(json)
                            val token = json.getString(StravaHelper.ACCESS_TOKEN)
                            _authState.value = StravaAuthState.Success(token)
                        } else {
                            _authState.value = StravaAuthState.Error("Empty response body")
                        }
                    } else {
                        _authState.value = StravaAuthState.Error("Exchange failed: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token exchange error", e)
                _authState.value = StravaAuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetState() {
        _authState.value = StravaAuthState.Idle
    }

    private class UriBuilder {
        private var scheme = ""
        private var authority = ""
        private val paths = mutableListOf<String>()

        fun scheme(s: String) = apply { scheme = s }
        fun authority(a: String) = apply { authority = a }
        fun appendPath(p: String) = apply { paths.add(p) }

        override fun toString(): String {
            return "$scheme://$authority/${paths.joinToString("/")}"
        }

        fun build() = this
    }

    companion object {
        private const val TAG = "StravaAuthRepository"
        
        @Volatile
        private var instance: StravaAuthRepository? = null

        fun getInstance(): StravaAuthRepository {
            return instance ?: synchronized(this) {
                instance ?: StravaAuthRepository().also { instance = it }
            }
        }
    }
}
