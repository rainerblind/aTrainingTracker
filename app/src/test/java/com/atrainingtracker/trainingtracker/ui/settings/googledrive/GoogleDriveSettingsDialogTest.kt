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

package com.atrainingtracker.trainingtracker.ui.settings.googledrive

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.TrainingApplication
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

/**
 * Unit test suite verifying Google Drive settings dialog preferences,
 * backup interval persistence, and state contracts (REQ-UI-430, TST-UI-395.2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveSettingsDialogTest {

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
    }

    @After
    fun tearDown() {
        TrainingApplication.setUploadToGoogleDrive(false)
        TrainingApplication.setUploadWorkoutsToGoogleDrive(false)
        TrainingApplication.setUploadBackupToGoogleDrive(false)
        TrainingApplication.setUploadToGoogleDriveOnlyOnWifi(false)
        TrainingApplication.deleteGoogleDriveCredential()
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
    }

    @Test
    fun testGoogleDrivePreferences_commitAndReadBack() {
        TrainingApplication.setUploadToGoogleDrive(true)
        assertTrue(TrainingApplication.uploadToGoogleDrive())

        TrainingApplication.setUploadWorkoutsToGoogleDrive(true)
        assertTrue(TrainingApplication.uploadWorkoutsToGoogleDrive())
        TrainingApplication.setUploadWorkoutsToGoogleDrive(false)
        assertFalse(TrainingApplication.uploadWorkoutsToGoogleDrive())

        TrainingApplication.setUploadBackupToGoogleDrive(true)
        assertTrue(TrainingApplication.uploadBackupToGoogleDrive())
        TrainingApplication.setUploadBackupToGoogleDrive(false)
        assertFalse(TrainingApplication.uploadBackupToGoogleDrive())

        TrainingApplication.setUploadToGoogleDriveOnlyOnWifi(true)
        assertTrue(TrainingApplication.uploadToGoogleDriveOnlyOnWifi())
        TrainingApplication.setUploadToGoogleDriveOnlyOnWifi(false)
        assertFalse(TrainingApplication.uploadToGoogleDriveOnlyOnWifi())

        TrainingApplication.storeGoogleDriveCredential("athlete@gmail.com", "dummy_token")
        assertEquals("athlete@gmail.com", TrainingApplication.getGoogleDriveAccountEmail())
    }

    @Test
    fun testBackupIntervalPersistence_updatesSharedPreferences() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)

        prefs.edit().putString("backup_interval_days", "3").apply()
        assertEquals("3", prefs.getString("backup_interval_days", "1"))

        prefs.edit().putString("backup_interval_days", "7").apply()
        assertEquals("7", prefs.getString("backup_interval_days", "1"))
    }

    @Test
    fun testLastBackupTimestamp_readFromPreferences() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)

        assertEquals(0L, prefs.getLong("last_backup_timestamp", 0L))
        val testTs = 1729580000000L
        prefs.edit().putLong("last_backup_timestamp", testTs).apply()
        assertEquals(testTs, prefs.getLong("last_backup_timestamp", 0L))
    }
}
