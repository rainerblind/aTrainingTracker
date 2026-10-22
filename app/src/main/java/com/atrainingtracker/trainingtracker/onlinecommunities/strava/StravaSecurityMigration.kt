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
import android.preference.PreferenceManager
import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication

/**
 * Executes a one-time non-destructive security migration on upgrade to V4.9.40 (REQ-SEC-041.5).
 * Flags credentials for fresh re-authentication and resets stored tokens without purging
 * local athlete databases, routes, or equipment.
 */
object StravaSecurityMigration {
    private const val TAG = "StravaSecurityMigration"
    const val PREF_KEY_SECURITY_CHECK = "strava_security_migration_v4_9_40"

    @JvmStatic
    fun runMigrationIfRequired(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(PREF_KEY_SECURITY_CHECK, false)) {
            prefs.edit().putBoolean(PREF_KEY_SECURITY_CHECK, true).apply()
            // Invalidate existing cached tokens non-destructively so next sync/upload negotiates a fresh session
            if (TrainingApplication.hasStravaAccessToken()) {
                Log.i(TAG, "V4.9.40 Security: Marking Strava tokens for fresh re-authentication")
                // Clear active in-memory and shared prefs token without wiping athlete database routes or equipment
                TrainingApplication.setStravaAccessToken("")
                TrainingApplication.setStravaRefreshToken("")
            }
        }
    }
}
