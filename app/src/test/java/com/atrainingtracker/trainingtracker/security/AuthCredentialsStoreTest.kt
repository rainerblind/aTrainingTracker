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
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test suite verifying AuthCredentialsStore isolation, legacy credential migration,
 * provider accessors, and post-restore preference sanitization (REQ-DAT-032, TST-DAT-027).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthCredentialsStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AuthCredentialsStore.resetForTesting(context)
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AuthCredentialsStore.resetForTesting(context)
    }

    @Test
    fun testLegacyMigrationPurgesDefaultPrefsAndPopulatesAuthStore() {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPrefs.edit()
            .putString(AuthCredentialsStore.KEY_STRAVA_TOKEN, "legacy_strava_token")
            .putString(AuthCredentialsStore.KEY_STRAVA_REFRESH_TOKEN, "legacy_refresh_token")
            .putInt(AuthCredentialsStore.KEY_STRAVA_TOKEN_EXPIRES_AT, 1720000000)
            .putInt(AuthCredentialsStore.KEY_STRAVA_ATHLETE_ID, 123456)
            .putString(AuthCredentialsStore.KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL, "athlete@example.com")
            .putString(AuthCredentialsStore.KEY_GOOGLE_DRIVE_AUTH_TOKEN, "legacy_drive_token")
            .putString(AuthCredentialsStore.KEY_DROPBOX_CREDENTIAL, "{\"access_token\":\"legacy_dropbox\"}")
            .putString("app_user_theme", "dark_mode")
            .commit()

        AuthCredentialsStore.migrateLegacyCredentials(context)

        // Verify default preferences no longer contain any credential keys
        for (key in AuthCredentialsStore.ALL_CREDENTIAL_KEYS) {
            assertFalse("Default preferences should not contain key $key", defaultPrefs.contains(key))
        }

        // Verify non-credential settings remain untouched in default preferences
        assertEquals("dark_mode", defaultPrefs.getString("app_user_theme", null))

        // Verify AuthCredentialsStore holds migrated values
        assertEquals("legacy_strava_token", AuthCredentialsStore.getStravaAccessToken(context))
        assertEquals("legacy_refresh_token", AuthCredentialsStore.getStravaRefreshToken(context))
        assertEquals(1720000000, AuthCredentialsStore.getStravaTokenExpiresAt(context))
        assertEquals(123456, AuthCredentialsStore.getStravaAthleteId(context))
        assertEquals("athlete@example.com", AuthCredentialsStore.getGoogleDriveAccountEmail(context))
        assertEquals("legacy_drive_token", AuthCredentialsStore.getGoogleDriveAuthToken(context))
        assertEquals("{\"access_token\":\"legacy_dropbox\"}", AuthCredentialsStore.readDropboxCredential(context))
    }

    @Test
    fun testCredentialIsolationFromDefaultPreferences() {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)

        AuthCredentialsStore.setStravaAccessToken(context, "isolated_strava_token")
        AuthCredentialsStore.storeGoogleDriveCredential(context, "isolated@example.com", "isolated_drive_token")
        AuthCredentialsStore.storeDropboxCredential(context, "isolated_dropbox_credential")

        assertEquals("isolated_strava_token", AuthCredentialsStore.getStravaAccessToken(context))
        assertEquals("isolated@example.com", AuthCredentialsStore.getGoogleDriveAccountEmail(context))
        assertEquals("isolated_drive_token", AuthCredentialsStore.getGoogleDriveAuthToken(context))
        assertEquals("isolated_dropbox_credential", AuthCredentialsStore.readDropboxCredential(context))

        // None of these keys should exist in default preferences
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_STRAVA_TOKEN))
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL))
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_GOOGLE_DRIVE_AUTH_TOKEN))
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_DROPBOX_CREDENTIAL))
    }

    @Test
    fun testAccessorsAndMutatorsForStravaGoogleDriveDropbox() {
        // Strava tokens
        AuthCredentialsStore.setStravaAccessToken(context, "strava_1")
        assertEquals("strava_1", AuthCredentialsStore.getStravaAccessToken(context))
        AuthCredentialsStore.setStravaAccessToken(context, null)
        assertNull(AuthCredentialsStore.getStravaAccessToken(context))

        AuthCredentialsStore.setStravaRefreshToken(context, "refresh_1")
        assertEquals("refresh_1", AuthCredentialsStore.getStravaRefreshToken(context))
        AuthCredentialsStore.setStravaRefreshToken(context, null)
        assertNull(AuthCredentialsStore.getStravaRefreshToken(context))

        AuthCredentialsStore.setStravaTokenExpiresAt(context, 12345)
        assertEquals(12345, AuthCredentialsStore.getStravaTokenExpiresAt(context))

        AuthCredentialsStore.setStravaAthleteId(context, 789)
        assertEquals(789, AuthCredentialsStore.getStravaAthleteId(context))

        // Strava OAuth state
        AuthCredentialsStore.setStravaOAuthState(context, "state_token_abc", 987654321L)
        assertEquals("state_token_abc", AuthCredentialsStore.getStravaOAuthState(context))
        assertEquals(987654321L, AuthCredentialsStore.getStravaOAuthStateTimestamp(context))
        AuthCredentialsStore.clearStravaOAuthState(context)
        assertNull(AuthCredentialsStore.getStravaOAuthState(context))
        assertEquals(0L, AuthCredentialsStore.getStravaOAuthStateTimestamp(context))

        // Google Drive
        AuthCredentialsStore.storeGoogleDriveCredential(context, "user@test.com", "token_drive_xyz")
        assertEquals("user@test.com", AuthCredentialsStore.getGoogleDriveAccountEmail(context))
        assertEquals("token_drive_xyz", AuthCredentialsStore.getGoogleDriveAuthToken(context))
        AuthCredentialsStore.deleteGoogleDriveCredential(context)
        assertNull(AuthCredentialsStore.getGoogleDriveAccountEmail(context))
        assertNull(AuthCredentialsStore.getGoogleDriveAuthToken(context))

        // Dropbox
        AuthCredentialsStore.storeDropboxCredential(context, "dropbox_token_payload")
        assertEquals("dropbox_token_payload", AuthCredentialsStore.readDropboxCredential(context))
        AuthCredentialsStore.deleteDropboxCredential(context)
        assertNull(AuthCredentialsStore.readDropboxCredential(context))
    }

    @Test
    fun testClearAllCredentials() {
        AuthCredentialsStore.setStravaAccessToken(context, "token_s")
        AuthCredentialsStore.setStravaRefreshToken(context, "refresh_s")
        AuthCredentialsStore.setStravaTokenExpiresAt(context, 555)
        AuthCredentialsStore.setStravaAthleteId(context, 999)
        AuthCredentialsStore.storeGoogleDriveCredential(context, "g@test.com", "token_g")
        AuthCredentialsStore.storeDropboxCredential(context, "token_d")

        AuthCredentialsStore.clearAllCredentials(context)

        assertNull(AuthCredentialsStore.getStravaAccessToken(context))
        assertNull(AuthCredentialsStore.getStravaRefreshToken(context))
        assertEquals(0, AuthCredentialsStore.getStravaTokenExpiresAt(context))
        assertEquals(0, AuthCredentialsStore.getStravaAthleteId(context))
        assertNull(AuthCredentialsStore.getGoogleDriveAccountEmail(context))
        assertNull(AuthCredentialsStore.getGoogleDriveAuthToken(context))
        assertNull(AuthCredentialsStore.readDropboxCredential(context))
    }

    @Test
    fun testSanitizeAfterRestoreRemovesLegacyKeysAndClearsAuthStore() {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPrefs.edit()
            .putString(AuthCredentialsStore.KEY_STRAVA_TOKEN, "restored_legacy_token")
            .putString(AuthCredentialsStore.KEY_DROPBOX_CREDENTIAL, "restored_legacy_dropbox")
            .putString("restored_setting", "value_retained")
            .commit()

        AuthCredentialsStore.setStravaAccessToken(context, "pre_existing_store_token")

        AuthCredentialsStore.sanitizeAfterRestore(context)

        // Legacy credential keys in default prefs should be removed
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_STRAVA_TOKEN))
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_DROPBOX_CREDENTIAL))
        // Regular restored setting should remain
        assertEquals("value_retained", defaultPrefs.getString("restored_setting", null))

        // AuthCredentialsStore should be cleared
        assertNull(AuthCredentialsStore.getStravaAccessToken(context))
    }
}
