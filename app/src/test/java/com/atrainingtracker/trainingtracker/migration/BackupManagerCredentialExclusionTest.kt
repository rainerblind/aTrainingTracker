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

package com.atrainingtracker.trainingtracker.migration

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.security.AuthCredentialsStore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipFile

/**
 * Unit test verifying that BackupManager strictly excludes auth_credentials_prefs.xml and
 * migrates/purges legacy credentials from default SharedPreferences prior to bundling (REQ-DAT-032.3, TST-DAT-027).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerCredentialExclusionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
    fun testCreateBackupArchive_excludesAuthCredentialsPrefsAndPurgesLegacyKeys() {
        // Set up database directory and core database
        val dataDir = File(context.applicationInfo.dataDir)
        val databasesDir = File(dataDir, "databases").apply { mkdirs() }
        val dummyDb = File(databasesDir, "WorkoutSummaries.db")
        if (!dummyDb.exists()) {
            val db = context.openOrCreateDatabase("WorkoutSummaries.db", Context.MODE_PRIVATE, null)
            db.execSQL("CREATE TABLE IF NOT EXISTS workouts (_id INTEGER PRIMARY KEY);")
            db.close()
        }

        // Put legacy keys in default SharedPreferences
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPrefs.edit()
            .putString(AuthCredentialsStore.KEY_STRAVA_TOKEN, "secret_strava_token")
            .putString(AuthCredentialsStore.KEY_DROPBOX_CREDENTIAL, "secret_dropbox_token")
            .putString(AuthCredentialsStore.LEGACY_KEY_DROPBOX_CREDENTIAL, "secret_legacy_dropbox_token")
            .putString(AuthCredentialsStore.LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL, "secret_legacy_drive_email")
            .putString("athlete_display_name", "Test Athlete")
            .commit()

        // Create auth_credentials_prefs.xml and a fake backup file inside shared_prefs dir
        val sharedPrefsDir = File(dataDir, "shared_prefs").apply { mkdirs() }
        val credPrefsFile = File(sharedPrefsDir, "auth_credentials_prefs.xml")
        credPrefsFile.writeText("<map><string name=\"token\">secret</string></map>")
        val credBakFile = File(sharedPrefsDir, "auth_credentials_prefs.xml.bak")
        credBakFile.writeText("<map><string name=\"token\">secret_bak</string></map>")

        // Create regular app prefs
        val regularPrefsFile = File(sharedPrefsDir, "com.atrainingtracker_preferences.xml")
        regularPrefsFile.writeText("<map><string name=\"athlete_display_name\">Test Athlete</string></map>")

        val archive = BackupManager.createBackup(context)
        assertNotNull("Backup archive should be created", archive)
        assertTrue("Archive file should exist", archive!!.exists())

        // Inspect zip archive entries
        val zip = ZipFile(archive)
        val entryNames = mutableListOf<String>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            entryNames.add(entries.nextElement().name)
        }
        zip.close()

        // 1. Must NOT contain auth_credentials_prefs.xml or any variant
        for (name in entryNames) {
            assertFalse(
                "Backup archive must not contain auth_credentials_prefs: $name",
                name.contains("auth_credentials_prefs")
            )
        }

        // 2. Must contain the regular preferences
        assertTrue(
            "Backup archive should include regular preferences",
            entryNames.any { it.contains("com.atrainingtracker_preferences.xml") }
        )

        // 3. Default preferences must have legacy credential keys purged by migrateLegacyCredentials
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_STRAVA_TOKEN))
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_DROPBOX_CREDENTIAL))
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.LEGACY_KEY_DROPBOX_CREDENTIAL))
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.LEGACY_KEY_GOOGLE_DRIVE_ACCOUNT_EMAIL))
        assertTrue(defaultPrefs.contains("athlete_display_name"))
    }
}
