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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
                Log.e(TAG, "Auth error: $error")
                finish()
                return
            }

            val code = data.getQueryParameter(StravaHelper.CODE)
            if (code != null) {
                StravaAuthRepository.getInstance().resetState()
                // Offload the exchange to the repository.
                // The UI (StravaUploadFragment) will be observing the repository's StateFlow via ViewModel.
                val repository = StravaAuthRepository.getInstance()
                
                // We use a global scope or similar if we want it to survive activity death, 
                // but since the repo is a singleton and we use a suspend function, 
                // we should ideally trigger it from a scope that persists.
                // For now, let's just trigger it.
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                   repository.exchangeCodeForToken(code)
                }
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
