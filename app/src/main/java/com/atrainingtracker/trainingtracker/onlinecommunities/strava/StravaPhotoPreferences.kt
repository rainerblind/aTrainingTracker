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
import androidx.preference.PreferenceManager

/**
 * Preference store for opt-in Strava API v3 activity photo download (`REQ-EXP-050`).
 */
object StravaPhotoPreferences {
    const val SP_DOWNLOAD_STRAVA_PHOTOS = "downloadStravaPhotos"

    @JvmStatic
    fun isDownloadEnabled(context: Context?): Boolean {
        if (context == null) return false
        return try {
            PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(SP_DOWNLOAD_STRAVA_PHOTOS, false)
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun setDownloadEnabled(context: Context?, enabled: Boolean) {
        if (context == null) return
        try {
            PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(SP_DOWNLOAD_STRAVA_PHOTOS, enabled)
                .apply()
        } catch (_: Throwable) {
            // Defensive guard for unmocked JVM test contexts
        }
    }
}
