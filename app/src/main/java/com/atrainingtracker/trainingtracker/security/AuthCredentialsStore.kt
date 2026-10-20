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

package com.atrainingtracker.trainingtracker.security

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.File

/**
 * Dedicated, encapsulated store for third-party OAuth and API authentication credentials
 * (Strava, Google Drive, Dropbox) isolated into a private SharedPreferences file
 * (`auth_credentials_prefs.xml`) to prevent token leakage into Android Auto Backup,
 * device-to-device transfers, and portable `.attbackup` archives (REQ-DAT-032.1).
 */
object AuthCredentialsStore {
    private const val TAG = "AuthCredentialsStore"

    const val PREFS_NAME = "auth_credentials_prefs"
    const val PREFS_FILE_NAME = "auth_credentials_prefs.xml"

    // Strava credential keys
    const val KEY_STRAVA_TOKEN = "stravaToken"
    const val KEY_STRAVA_REFRESH_TOKEN = "stravaRefreshToken"
    const val KEY_STRAVA_TOKEN_EXPIRES_AT = "stravaTokenExpiresAt"
    const val KEY_STRAVA_ATHLETE_ID = "stravaAthleteId"
    const val KEY_STRAVA_OAUTH_STATE = "stravaOAuthState"
    const val KEY_STRAVA_OAUTH_STATE_TIMESTAMP = "stravaOAuthStateTimestamp"

    // Google Drive credential keys
    const val KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL = "sp_google_drive_account_email"
    const val KEY_GOOGLE_DRIVE_AUTH_TOKEN = "googleDriveAuthToken"

    // Dropbox credential keys
    const val KEY_DROPBOX_CREDENTIAL = "sp_dropbox_credential"

    // Legacy credential keys from TrainingApplication.java
    const val LEGACY_KEY_DROPBOX_CREDENTIAL = "dropboxCredential"
    const val LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL = "googleDriveAccountEmail"
    const val LEGACY_KEY_STRAVA_OAUTH_STATE = "SP_STRAVA_OAUTH_STATE"
    const val LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP = "SP_STRAVA_OAUTH_STATE_TIMESTAMP"

    val LEGACY_KEY_MIGRATION_MAP = mapOf(
        LEGACY_KEY_DROPBOX_CREDENTIAL to KEY_DROPBOX_CREDENTIAL,
        LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL to KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL,
        LEGACY_KEY_STRAVA_OAUTH_STATE to KEY_STRAVA_OAUTH_STATE,
        LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP to KEY_STRAVA_OAUTH_STATE_TIMESTAMP
    )

    val ALL_CREDENTIAL_KEYS = setOf(
        KEY_STRAVA_TOKEN,
        KEY_STRAVA_REFRESH_TOKEN,
        KEY_STRAVA_TOKEN_EXPIRES_AT,
        KEY_STRAVA_ATHLETE_ID,
        KEY_STRAVA_OAUTH_STATE,
        KEY_STRAVA_OAUTH_STATE_TIMESTAMP,
        KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL,
        KEY_GOOGLE_DRIVE_AUTH_TOKEN,
        KEY_DROPBOX_CREDENTIAL
    )

    val ALL_CREDENTIAL_KEYS_TO_SANITIZE: Set<String> = ALL_CREDENTIAL_KEYS + LEGACY_KEY_MIGRATION_MAP.keys

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getDefaultPrefs(context: Context): SharedPreferences? {
        return try {
            PreferenceManager.getDefaultSharedPreferences(context)
        } catch (_: Throwable) {
            try {
                val pkg = try { context.packageName } catch (_: Throwable) { null }
                val prefName = if (pkg.isNullOrBlank()) "com.atrainingtracker_preferences" else "${pkg}_preferences"
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Sanitizes preferences after a backup restore:
     * 1. Strips any legacy credential keys residing in default SharedPreferences.
     * 2. Clears all credentials in auth_credentials_prefs to enforce the clean re-authentication invariant.
     */
    @JvmStatic
    fun sanitizeAfterRestore(context: Context) {
        try {
            val defaultPrefs = getDefaultPrefs(context)
            if (defaultPrefs != null) {
                val keysToRemove = ALL_CREDENTIAL_KEYS_TO_SANITIZE.filter { 
                    try { defaultPrefs.contains(it) } catch (_: Throwable) { false } 
                }
                if (keysToRemove.isNotEmpty()) {
                    val editor = defaultPrefs.edit()
                    for (key in keysToRemove) {
                        editor.remove(key)
                    }
                    editor.commit()
                    Log.i(TAG, "Stripped ${keysToRemove.size} legacy credential keys from default preferences.")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Sanitizing default preferences bypassed or failed: ${e.message}")
        }
        try {
            clearAllCredentials(context)
        } catch (e: Throwable) {
            Log.w(TAG, "Clearing credentials bypassed or failed: ${e.message}")
        }
    }

    /**
     * Sanitizes a staging shared_prefs directory before swap:
     * Discards auth_credentials_prefs.xml and strips legacy credential entries from all preference XML files.
     */
    @JvmStatic
    fun sanitizeStagedPreferencesDirectory(stagingPrefsDir: File) {
        if (!stagingPrefsDir.exists() || !stagingPrefsDir.isDirectory) return

        // 1. Delete any credentials preference file
        val credFile = File(stagingPrefsDir, PREFS_FILE_NAME)
        if (credFile.exists()) credFile.delete()
        val credBak = File(stagingPrefsDir, "$PREFS_FILE_NAME.bak")
        if (credBak.exists()) credBak.delete()

        // 2. Scan remaining xml files and strip lines containing legacy credential keys
        val xmlFiles = stagingPrefsDir.listFiles { f -> f.isFile && f.extension == "xml" } ?: return
        for (file in xmlFiles) {
            try {
                val lines = file.readLines()
                var modified = false
                val sanitizedLines = mutableListOf<String>()
                for (line in lines) {
                    val containsCredKey = ALL_CREDENTIAL_KEYS_TO_SANITIZE.any { key ->
                        line.contains("name=\"$key\"")
                    }
                    if (containsCredKey) {
                        modified = true
                    } else {
                        sanitizedLines.add(line)
                    }
                }
                if (modified) {
                    file.writeText(sanitizedLines.joinToString("\n"))
                    Log.i(TAG, "Stripped legacy credentials from staging file: ${file.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sanitize staging preference file: ${file.name}", e)
            }
        }
    }

    /**
     * Transparently migrates any legacy credentials residing in default SharedPreferences
     * into `auth_credentials_prefs` and purges them immediately from default preferences.
     */
    @JvmStatic
    fun migrateLegacyCredentials(context: Context) {
        try {
            val defaultPrefs = getDefaultPrefs(context) ?: return
            val credPrefs = getPrefs(context)

            val keysToMigrate = ALL_CREDENTIAL_KEYS_TO_SANITIZE.filter { 
                try { defaultPrefs.contains(it) } catch (_: Throwable) { false } 
            }
            if (keysToMigrate.isEmpty()) {
                return
            }

            Log.i(TAG, "Migrating ${keysToMigrate.size} legacy credential keys to $PREFS_NAME...")
            val credEditor = credPrefs.edit()
            val defaultEditor = defaultPrefs.edit()

            for (key in keysToMigrate) {
                val allEntries = try { defaultPrefs.all } catch (_: Throwable) { null } ?: emptyMap()
                val value = allEntries[key]
                val targetKey = LEGACY_KEY_MIGRATION_MAP[key] ?: key
                when (value) {
                    is String -> credEditor.putString(targetKey, value)
                    is Int -> credEditor.putInt(targetKey, value)
                    is Long -> credEditor.putLong(targetKey, value)
                    is Boolean -> credEditor.putBoolean(targetKey, value)
                    is Float -> credEditor.putFloat(targetKey, value)
                }
                defaultEditor.remove(key)
            }

            credEditor.apply()
            defaultEditor.apply()
            Log.i(TAG, "Legacy credential migration complete.")
        } catch (e: Throwable) {
            Log.w(TAG, "Legacy credential migration bypassed or failed: ${e.message}")
        }
    }

    // --- Strava Accessors ---

    @JvmStatic
    fun getStravaAccessToken(context: Context): String? {
        val token = getPrefs(context).getString(KEY_STRAVA_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }

    @JvmStatic
    fun setStravaAccessToken(context: Context, token: String?) {
        if (token == null) {
            getPrefs(context).edit().remove(KEY_STRAVA_TOKEN).apply()
        } else {
            getPrefs(context).edit().putString(KEY_STRAVA_TOKEN, token).apply()
        }
    }

    @JvmStatic
    fun getStravaRefreshToken(context: Context): String? {
        val token = getPrefs(context).getString(KEY_STRAVA_REFRESH_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }

    @JvmStatic
    fun setStravaRefreshToken(context: Context, token: String?) {
        if (token == null) {
            getPrefs(context).edit().remove(KEY_STRAVA_REFRESH_TOKEN).apply()
        } else {
            getPrefs(context).edit().putString(KEY_STRAVA_REFRESH_TOKEN, token).apply()
        }
    }

    @JvmStatic
    fun getStravaTokenExpiresAt(context: Context): Int {
        return getPrefs(context).getInt(KEY_STRAVA_TOKEN_EXPIRES_AT, 0)
    }

    @JvmStatic
    fun setStravaTokenExpiresAt(context: Context, expiresAt: Int) {
        getPrefs(context).edit().putInt(KEY_STRAVA_TOKEN_EXPIRES_AT, expiresAt).apply()
    }

    @JvmStatic
    fun getStravaAthleteId(context: Context): Int {
        return getPrefs(context).getInt(KEY_STRAVA_ATHLETE_ID, 0)
    }

    @JvmStatic
    fun setStravaAthleteId(context: Context, athleteId: Int) {
        getPrefs(context).edit().putInt(KEY_STRAVA_ATHLETE_ID, athleteId).apply()
    }

    @JvmStatic
    fun getStravaOAuthState(context: Context): String? {
        val state = getPrefs(context).getString(KEY_STRAVA_OAUTH_STATE, null)
        return if (state.isNullOrBlank()) null else state
    }

    @JvmStatic
    fun getStravaOAuthStateTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_STRAVA_OAUTH_STATE_TIMESTAMP, 0L)
    }

    @JvmStatic
    fun setStravaOAuthState(context: Context, state: String?, timestamp: Long) {
        getPrefs(context).edit()
            .putString(KEY_STRAVA_OAUTH_STATE, state)
            .putLong(KEY_STRAVA_OAUTH_STATE_TIMESTAMP, timestamp)
            .apply()
    }

    @JvmStatic
    fun clearStravaOAuthState(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_STRAVA_OAUTH_STATE)
            .remove(LEGACY_KEY_STRAVA_OAUTH_STATE)
            .remove(KEY_STRAVA_OAUTH_STATE_TIMESTAMP)
            .remove(LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP)
            .apply()
    }

    @JvmStatic
    fun clearStravaCredentials(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_STRAVA_TOKEN)
            .remove(KEY_STRAVA_REFRESH_TOKEN)
            .remove(KEY_STRAVA_TOKEN_EXPIRES_AT)
            .remove(KEY_STRAVA_ATHLETE_ID)
            .remove(KEY_STRAVA_OAUTH_STATE)
            .remove(LEGACY_KEY_STRAVA_OAUTH_STATE)
            .remove(KEY_STRAVA_OAUTH_STATE_TIMESTAMP)
            .remove(LEGACY_KEY_STRAVA_OAUTH_STATE_TIMESTAMP)
            .apply()
    }

    // --- Google Drive Accessors ---

    @JvmStatic
    fun getGoogleDriveAccountEmail(context: Context): String? {
        val email = getPrefs(context).getString(KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL, null)
        return if (email.isNullOrBlank()) null else email
    }

    @JvmStatic
    fun getGoogleDriveAuthToken(context: Context): String? {
        val token = getPrefs(context).getString(KEY_GOOGLE_DRIVE_AUTH_TOKEN, null)
        return if (token.isNullOrBlank()) null else token
    }

    @JvmStatic
    fun storeGoogleDriveCredential(context: Context, email: String?, token: String?) {
        getPrefs(context).edit()
            .putString(KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL, email)
            .putString(KEY_GOOGLE_DRIVE_AUTH_TOKEN, token)
            .apply()
    }

    @JvmStatic
    fun deleteGoogleDriveCredential(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL)
            .remove(KEY_GOOGLE_DRIVE_AUTH_TOKEN)
            .apply()
    }

    // --- Dropbox Accessors ---

    @JvmStatic
    fun readDropboxCredential(context: Context): String? {
        val credential = getPrefs(context).getString(KEY_DROPBOX_CREDENTIAL, null)
        if (!credential.isNullOrBlank()) {
            return credential
        }
        val legacyCredential = getPrefs(context).getString(LEGACY_KEY_DROPBOX_CREDENTIAL, null)
        return if (legacyCredential.isNullOrBlank()) null else legacyCredential
    }

    @JvmStatic
    fun storeDropboxCredential(context: Context, credentialString: String?) {
        if (credentialString == null) {
            getPrefs(context).edit().remove(KEY_DROPBOX_CREDENTIAL).apply()
        } else {
            getPrefs(context).edit().putString(KEY_DROPBOX_CREDENTIAL, credentialString).apply()
        }
    }

    @JvmStatic
    fun deleteDropboxCredential(context: Context) {
        getPrefs(context).edit().remove(KEY_DROPBOX_CREDENTIAL).apply()
    }

    // --- General Maintenance & Purge ---

    @JvmStatic
    fun clearAllCredentials(context: Context) {
        try {
            getPrefs(context).edit().clear().commit()
            Log.i(TAG, "All authentication credentials cleared from $PREFS_NAME.")
        } catch (e: Throwable) {
            Log.w(TAG, "Clear credentials bypassed or failed: ${e.message}")
        }
    }

    @VisibleForTesting
    fun resetForTesting(context: Context) {
        getPrefs(context).edit().clear().commit()
    }
}
