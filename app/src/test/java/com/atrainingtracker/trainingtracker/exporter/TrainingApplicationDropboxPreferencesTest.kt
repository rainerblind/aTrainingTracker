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

package com.atrainingtracker.trainingtracker.exporter

import android.content.Context
import android.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.security.AuthCredentialsStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Field

/**
 * Unit tests verifying granular Dropbox preferences in TrainingApplication (TST-DAT-031.1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingApplicationDropboxPreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPrefs.edit().clear().commit()
        AuthCredentialsStore.resetForTesting(context)

        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", defaultPrefs)
    }

    @After
    fun tearDown() {
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        defaultPrefs.edit().clear().commit()
        AuthCredentialsStore.resetForTesting(context)
        setStaticField(TrainingApplication::class.java, "cAppContext", null)
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(null, value)
        } catch (_: Exception) {
        }
    }

    @Test
    fun testDefaultValues_areTrueForBackwardCompatibility() {
        assertFalse("uploadToDropbox should default to false", TrainingApplication.uploadToDropbox())
        assertTrue("uploadWorkoutsToDropbox should default to true", TrainingApplication.uploadWorkoutsToDropbox())
        assertTrue("uploadBackupToDropbox should default to true", TrainingApplication.uploadBackupToDropbox())
        assertTrue("uploadToDropboxOnlyOnWifi should default to true", TrainingApplication.uploadToDropboxOnlyOnWifi())
        assertEquals(0L, TrainingApplication.getDropboxLastSyncTimestamp())
        assertNull(TrainingApplication.getDropboxAccountEmail())
    }

    @Test
    fun testGranularToggles_persistAndRetrieveCorrectly() {
        TrainingApplication.setUploadToDropbox(true)
        assertTrue(TrainingApplication.uploadToDropbox())

        TrainingApplication.setUploadWorkoutsToDropbox(false)
        assertFalse(TrainingApplication.uploadWorkoutsToDropbox())

        TrainingApplication.setUploadBackupToDropbox(false)
        assertFalse(TrainingApplication.uploadBackupToDropbox())

        TrainingApplication.setUploadToDropboxOnlyOnWifi(false)
        assertFalse(TrainingApplication.uploadToDropboxOnlyOnWifi())

        TrainingApplication.setDropboxLastSyncTimestamp(1720000000L)
        assertEquals(1720000000L, TrainingApplication.getDropboxLastSyncTimestamp())

        TrainingApplication.setDropboxAccountEmail("test@athlete.com")
        assertEquals("test@athlete.com", TrainingApplication.getDropboxAccountEmail())
    }

    @Test
    fun testDeleteDropboxCredential_purgesCredentialAndAccountEmail() {
        TrainingApplication.setDropboxAccountEmail("test@athlete.com")
        assertEquals("test@athlete.com", TrainingApplication.getDropboxAccountEmail())

        TrainingApplication.deleteDropboxCredential()
        assertNull(TrainingApplication.getDropboxAccountEmail())
        assertNull(TrainingApplication.readDropboxCredential())
    }
}
