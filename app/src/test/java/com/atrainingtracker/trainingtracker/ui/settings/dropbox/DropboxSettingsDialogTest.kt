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

package com.atrainingtracker.trainingtracker.ui.settings.dropbox

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.dropbox.core.oauth.DbxCredential
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

/**
 * Unit test suite verifying Dropbox settings dialog context unwrapping,
 * single-authority credential capture, and lifecycle contracts (REQ-UI-152, TST-UI-105, ATT-4588).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DropboxSettingsDialogTest {

    private lateinit var activity: Activity

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field: Field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        setStaticField(TrainingApplication::class.java, "cAppContext", appContext)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", prefs)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        TrainingApplication.deleteDropboxCredential()
        TrainingApplication.setUploadToDropbox(false)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
    }

    @Test
    fun testFindActivity_withDirectActivity_returnsActivity() {
        val result = activity.findActivity()
        assertSame("Direct Activity context must return itself", activity, result)
    }

    @Test
    fun testFindActivity_withSingleContextWrapper_unwrapsActivity() {
        val wrapper = ContextWrapper(activity)
        val result = wrapper.findActivity()
        assertSame("Single ContextWrapper must resolve the wrapped Activity", activity, result)
    }

    @Test
    fun testFindActivity_withContextThemeWrapper_unwrapsActivity() {
        val themeWrapper = ContextThemeWrapper(activity, android.R.style.Theme_Material)
        val result = themeWrapper.findActivity()
        assertSame("ContextThemeWrapper must resolve the underlying Activity", activity, result)
    }

    @Test
    fun testFindActivity_withDeeplyNestedContextWrappers_unwrapsActivity() {
        val level1 = ContextThemeWrapper(activity, android.R.style.Theme_Material)
        val level2 = ContextWrapper(level1)
        val level3 = ContextWrapper(level2)
        val result = level3.findActivity()
        assertSame("Deeply nested ContextWrappers must recursively resolve the underlying Activity", activity, result)
    }

    @Test
    fun testFindActivity_withNonActivityContext_returnsNull() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val result = appContext.findActivity()
        assertNull("Plain non-Activity ApplicationContext must return null", result)
    }

    @Test
    fun testDropboxSettingsDialogFragment_doesNotOverrideOnResume() {
        val fragmentClass = DropboxSettingsDialogFragment::class.java
        val declaredOnResume = fragmentClass.declaredMethods.find { it.name == "onResume" }
        assertNull(
            "DropboxSettingsDialogFragment MUST NOT declare onResume to guarantee single-authority credential capture in Compose dialog",
            declaredOnResume
        )
    }

    @Test
    fun testSingleAuthorityCredentialCapture_persistsCredentialAndEnablesUpload() {
        val cred = DbxCredential("test_oauth_access_token_xyz")

        // Initial state: not connected
        TrainingApplication.deleteDropboxCredential()
        TrainingApplication.setUploadToDropbox(false)
        assertFalse(TrainingApplication.uploadToDropbox())
        assertNull(TrainingApplication.readDropboxCredential())

        // Simulate single authority credential capture in DropboxSettingsDialog
        TrainingApplication.storeDropboxCredential(cred)
        TrainingApplication.setUploadToDropbox(true)

        // Verify persistence and state resolution
        assertTrue(TrainingApplication.uploadToDropbox())
        val savedCred = TrainingApplication.readDropboxCredential()
        assertNotNull(savedCred)
        assertEquals("test_oauth_access_token_xyz", savedCred?.accessToken)
        assertTrue(
            "Upload flag and valid credential MUST both be present for isConnected",
            TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null
        )

        // Simulate credential revocation
        TrainingApplication.deleteDropboxCredential()
        TrainingApplication.setUploadToDropbox(false)
        assertFalse(TrainingApplication.uploadToDropbox())
        assertNull(TrainingApplication.readDropboxCredential())
    }

    @Test
    fun testGranularDropboxPreferences_commitAndReadBack() {
        // Workout upload toggle
        TrainingApplication.setUploadWorkoutsToDropbox(true)
        assertTrue(TrainingApplication.uploadWorkoutsToDropbox())
        TrainingApplication.setUploadWorkoutsToDropbox(false)
        assertFalse(TrainingApplication.uploadWorkoutsToDropbox())

        // Database backup toggle
        TrainingApplication.setUploadBackupToDropbox(true)
        assertTrue(TrainingApplication.uploadBackupToDropbox())
        TrainingApplication.setUploadBackupToDropbox(false)
        assertFalse(TrainingApplication.uploadBackupToDropbox())

        // Wi-Fi only restriction toggle
        TrainingApplication.setUploadToDropboxOnlyOnWifi(true)
        assertTrue(TrainingApplication.uploadToDropboxOnlyOnWifi())
        TrainingApplication.setUploadToDropboxOnlyOnWifi(false)
        assertFalse(TrainingApplication.uploadToDropboxOnlyOnWifi())

        // Account email persistence
        TrainingApplication.setDropboxAccountEmail("athlete@dropbox.com")
        assertEquals("athlete@dropbox.com", TrainingApplication.getDropboxAccountEmail())
        TrainingApplication.deleteDropboxCredential()
        assertNull(TrainingApplication.getDropboxAccountEmail())
    }

    @Test
    fun testBackupIntervalPersistence_updatesSharedPreferences() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)

        prefs.edit().putString("backup_interval_days", "7").apply()
        assertEquals("7", prefs.getString("backup_interval_days", "1"))

        prefs.edit().putString("backup_interval_days", "30").apply()
        assertEquals("30", prefs.getString("backup_interval_days", "1"))
    }
}

