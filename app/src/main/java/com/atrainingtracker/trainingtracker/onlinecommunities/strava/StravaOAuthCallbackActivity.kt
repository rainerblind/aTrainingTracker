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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Handles OAuth 2.0 authorization redirect callbacks for Strava (REQ-EXT-012, ATT-4022).
 *
 * Validates the cryptographically random state parameter using [StravaOAuthStateManager]
 * to prevent Login CSRF attacks, enforces single-use state consumption, and dispatches
 * token exchange within [StravaAuthRepository]'s structured coroutine scope.
 */
class StravaOAuthCallbackActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.toString().startsWith(REDIRECT_URI_PREFIX)) {
            val error = data.getQueryParameter("error")
            if (error != null) {
                Log.w(TAG, "Auth error: $error")
                StravaOAuthStateManager.clearState(this)
                StravaAuthRepository.getInstance().setAuthState(StravaAuthState.Error("Authorization cancelled: $error"))
                finish()
                return
            }

            val state = data.getQueryParameter("state")
            val isValidState = StravaOAuthStateManager.validateAndConsumeState(state, this)
            if (!isValidState) {
                Log.w(TAG, "OAuth state validation failed; rejecting callback")
                StravaAuthRepository.getInstance().setAuthState(StravaAuthState.Error("OAuth state validation failed"))
                finish()
                return
            }

            val code = data.getQueryParameter(StravaHelper.CODE)
            if (!code.isNullOrEmpty()) {
                StravaAuthRepository.getInstance().resetState()
                StravaAuthRepository.getInstance().exchangeCodeForTokenAsync(code)
            } else {
                Log.w(TAG, "Missing authorization code in callback")
                StravaAuthRepository.getInstance().setAuthState(StravaAuthState.Error("Missing authorization code"))
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "StravaOAuthCallback"
        private const val REDIRECT_URI_PREFIX = "strava://rainerblind.github.io"
        const val StravaOAuthSuccess = "com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaOAuthSuccess"
    }
}
