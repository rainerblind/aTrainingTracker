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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit tests verifying that MigrationEngine sanitizes restored preferences by discarding
 * auth_credentials_prefs.xml and stripping legacy credential keys to enforce clean re-authentication (REQ-DAT-032.3, TST-DAT-027).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationEngineCredentialSanitizationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private var restarterCalled = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AuthCredentialsStore.resetForTesting(context)
        restarterCalled = false
        MigrationEngine.appRestarter = { restarterCalled = true }
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AuthCredentialsStore.resetForTesting(context)
    }

    @Test
    fun testSanitizeStagedPreferencesDirectory_removesCredentialsFileAndStripsLegacyXmlKeys() {
        val stagingPrefsDir = tempFolder.newFolder("staging_shared_prefs")

        // Create auth_credentials_prefs.xml
        val credFile = File(stagingPrefsDir, "auth_credentials_prefs.xml")
        credFile.writeText("<map><string name=\"token\">secret_token</string></map>")

        val credBakFile = File(stagingPrefsDir, "auth_credentials_prefs.xml.bak")
        credBakFile.writeText("<map><string name=\"token\">secret_bak</string></map>")

        // Create a legacy preferences file containing both credentials and general settings
        val legacyPrefsFile = File(stagingPrefsDir, "com.atrainingtracker_preferences.xml")
        legacyPrefsFile.writeText(
            """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="athlete_name">John Doe</string>
                <string name="stravaToken">staged_legacy_strava_token</string>
                <string name="stravaRefreshToken">staged_refresh</string>
                <string name="googleDriveAuthToken">staged_drive</string>
                <string name="sp_dropbox_credential">staged_dropbox</string>
                <string name="dropboxCredential">staged_legacy_dropbox</string>
                <string name="googleDriveAccountEmail">staged_legacy_drive_email</string>
                <string name="preferred_units">metric</string>
            </map>
            """.trimIndent()
        )

        AuthCredentialsStore.sanitizeStagedPreferencesDirectory(stagingPrefsDir)

        // 1. auth_credentials_prefs.xml and .bak must be deleted
        assertFalse("auth_credentials_prefs.xml must be deleted", credFile.exists())
        assertFalse("auth_credentials_prefs.xml.bak must be deleted", credBakFile.exists())

        // 2. legacy keys must be stripped from the xml file
        val sanitizedContent = legacyPrefsFile.readText()
        assertFalse(sanitizedContent.contains("stravaToken"))
        assertFalse(sanitizedContent.contains("stravaRefreshToken"))
        assertFalse(sanitizedContent.contains("googleDriveAuthToken"))
        assertFalse(sanitizedContent.contains("sp_dropbox_credential"))
        assertFalse(sanitizedContent.contains("dropboxCredential"))
        assertFalse(sanitizedContent.contains("googleDriveAccountEmail"))

        // 3. general settings must be preserved
        assertTrue(sanitizedContent.contains("athlete_name"))
        assertTrue(sanitizedContent.contains("preferred_units"))
    }

    @Test
    fun testPerformFullRestore_sanitizesCredentialsOnRestore() {
        // Prepare mock archive
        val archiveFile = tempFolder.newFile("test_restore.attbackup")
        val dbFile = tempFolder.newFile("WorkoutSummaries.db")
        val db = context.openOrCreateDatabase(dbFile.absolutePath, Context.MODE_PRIVATE, null)
        db.version = 1
        db.execSQL("CREATE TABLE IF NOT EXISTS workouts (_id INTEGER PRIMARY KEY);")
        db.close()

        // Write zip archive containing databases and shared_prefs with credentials
        ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zos ->
            // databases entry
            zos.putNextEntry(ZipEntry("databases/WorkoutSummaries.db"))
            zos.write(dbFile.readBytes())
            zos.closeEntry()

            // shared_prefs credential entry
            zos.putNextEntry(ZipEntry("shared_prefs/auth_credentials_prefs.xml"))
            zos.write("<map><string name=\"stravaToken\">archive_token</string></map>".toByteArray())
            zos.closeEntry()

            // shared_prefs default prefs with legacy key
            val legacyContent = "<map><string name=\"stravaToken\">archive_legacy</string><string name=\"setting_k\">v</string></map>"
            zos.putNextEntry(ZipEntry("shared_prefs/com.atrainingtracker_preferences.xml"))
            zos.write(legacyContent.toByteArray())
            zos.closeEntry()
        }

        // Pre-populate credentials in current app
        AuthCredentialsStore.setStravaAccessToken(context, "current_token")

        val result = MigrationEngine.performFullRestore(context, archiveFile)
        assertTrue("Restore should succeed", result)
        assertTrue("Restarter should be called", restarterCalled)

        // After restore: clean re-authentication invariant must hold
        assertNull(AuthCredentialsStore.getStravaAccessToken(context))
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        assertFalse(defaultPrefs.contains(AuthCredentialsStore.KEY_STRAVA_TOKEN))
    }
}
