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
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.annotation.VisibleForTesting
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.security.AuthCredentialsStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages cryptographically secure OAuth 2.0 state parameter generation, persistence,
 * constant-time validation, and single-use consumption to protect against Login CSRF attacks
 * (RFC 6749 Section 10.12, REQ-EXT-012, TST-EXT-009, ATT-4022).
 */
object StravaOAuthStateManager {

    const val SP_STRAVA_OAUTH_STATE = "SP_STRAVA_OAUTH_STATE"
    const val SP_STRAVA_OAUTH_STATE_TIMESTAMP = "SP_STRAVA_OAUTH_STATE_TIMESTAMP"

    /** State TTL: 10 minutes (600,000 milliseconds) */
    const val STATE_TTL_MS = 600_000L

    private val secureRandom = SecureRandom()

    @VisibleForTesting
    internal var timeProvider: () -> Long = { System.currentTimeMillis() }

    private fun getPrefs(context: Context? = null): SharedPreferences? {
        val ctx = context?.applicationContext ?: TrainingApplication.getAppContext()
        return if (ctx != null) {
            AuthCredentialsStore.getPrefs(ctx)
        } else {
            null
        }
    }

    /**
     * Generates a 128-bit cryptographically secure random token (32 lowercase hex characters),
     * stores it along with the current epoch timestamp in SharedPreferences, and returns the state string.
     */
    @JvmOverloads
    fun generateAndSaveState(context: Context? = null): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        val state = bytes.joinToString("") { "%02x".format(it) }
        val now = timeProvider()

        getPrefs(context)?.edit()?.apply {
            putString(SP_STRAVA_OAUTH_STATE, state)
            putLong(SP_STRAVA_OAUTH_STATE_TIMESTAMP, now)
            apply()
        }

        return state
    }

    /**
     * Validates that:
     * 1. A non-empty state exists in storage.
     * 2. The stored state has not expired (<= [STATE_TTL_MS]).
     * 3. The incoming state is non-null and equals the stored state in constant time.
     *
     * In ALL cases (success, mismatch, expiration, null), the stored state is immediately cleared
     * to enforce single-use semantics and prevent replay attacks.
     *
     * @return true if state is valid and consumed; false otherwise.
     */
    @JvmOverloads
    fun validateAndConsumeState(incomingState: String?, context: Context? = null): Boolean {
        val prefs = getPrefs(context)
        val storedState = prefs?.getString(SP_STRAVA_OAUTH_STATE, null)
        val timestamp = prefs?.getLong(SP_STRAVA_OAUTH_STATE_TIMESTAMP, 0L) ?: 0L

        // Always consume/clear stored state immediately to prevent replay attacks
        clearState(context)

        if (storedState.isNullOrEmpty() || incomingState.isNullOrEmpty()) {
            return false
        }

        val now = timeProvider()
        if (now - timestamp > STATE_TTL_MS || timestamp <= 0L) {
            return false
        }

        val incomingBytes = incomingState.toByteArray(StandardCharsets.UTF_8)
        val storedBytes = storedState.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.isEqual(incomingBytes, storedBytes)
    }

    /**
     * Clears any active OAuth state from SharedPreferences.
     */
    @JvmOverloads
    fun clearState(context: Context? = null) {
        getPrefs(context)?.edit()?.apply {
            remove(SP_STRAVA_OAUTH_STATE)
            remove(SP_STRAVA_OAUTH_STATE_TIMESTAMP)
            apply()
        }
    }

    @VisibleForTesting
    fun setStateForTesting(state: String?, timestamp: Long, context: Context? = null) {
        getPrefs(context)?.edit()?.apply {
            if (state != null) {
                putString(SP_STRAVA_OAUTH_STATE, state)
                putLong(SP_STRAVA_OAUTH_STATE_TIMESTAMP, timestamp)
            } else {
                remove(SP_STRAVA_OAUTH_STATE)
                remove(SP_STRAVA_OAUTH_STATE_TIMESTAMP)
            }
            apply()
        }
    }
}
