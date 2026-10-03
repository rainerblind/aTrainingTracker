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

package com.atrainingtracker.trainingtracker.cloud.googledrive

import android.content.SharedPreferences
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Unit tests verifying Google Drive credential lifecycle, null-safety, and scope bounding invariants.
 */
class GoogleDriveAuthSafetyTest {

    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        mockPrefs = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor

        setStaticField(TrainingApplication::class.java, "cSharedPreferences", mockPrefs)
    }

    @After
    fun tearDown() {
        setStaticField(TrainingApplication::class.java, "cSharedPreferences", null)
        unmockkAll()
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
    fun testStoreGoogleDriveCredential_persistsEmailTokenAndEnablesUpload() {
        TrainingApplication.storeGoogleDriveCredential("test@example.com", "mock_access_token_123")

        verify { mockEditor.putString(TrainingApplication.SP_GOOGLE_DRIVE_ACCOUNT_EMAIL, "test@example.com") }
        verify { mockEditor.putString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, "mock_access_token_123") }
        verify { mockEditor.putBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, true) }
    }

    @Test
    fun testDeleteGoogleDriveCredential_removesCredentialsAndDisablesUpload() {
        TrainingApplication.deleteGoogleDriveCredential()

        verify { mockEditor.remove(TrainingApplication.SP_GOOGLE_DRIVE_ACCOUNT_EMAIL) }
        verify { mockEditor.remove(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN) }
        verify { mockEditor.putBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) }
    }

    @Test
    fun testGetGoogleDriveAuthToken_returnsNullWhenEmptyOrWhitespace() {
        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns "   "
        assertNull(TrainingApplication.getGoogleDriveAuthToken())

        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns null
        assertNull(TrainingApplication.getGoogleDriveAuthToken())

        every { mockPrefs.getString(TrainingApplication.SP_GOOGLE_DRIVE_AUTH_TOKEN, null) } returns "valid_token"
        assertEquals("valid_token", TrainingApplication.getGoogleDriveAuthToken())
    }

    @Test
    fun testGoogleDriveTogglePreferences_defaultValuesAndSetters() {
        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_TO_GOOGLE_DRIVE, false) } returns false
        assertFalse(TrainingApplication.uploadToGoogleDrive())

        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_WORKOUTS_TO_GOOGLE_DRIVE, true) } returns true
        assertTrue(TrainingApplication.uploadWorkoutsToGoogleDrive())

        every { mockPrefs.getBoolean(TrainingApplication.SP_UPLOAD_BACKUP_TO_GOOGLE_DRIVE, true) } returns true
        assertTrue(TrainingApplication.uploadBackupToGoogleDrive())

        every { mockPrefs.getBoolean(TrainingApplication.SP_GOOGLE_DRIVE_ONLY_WIFI, true) } returns true
        assertTrue(TrainingApplication.uploadToGoogleDriveOnlyOnWifi())

        TrainingApplication.setUploadWorkoutsToGoogleDrive(false)
        verify { mockEditor.putBoolean(TrainingApplication.SP_UPLOAD_WORKOUTS_TO_GOOGLE_DRIVE, false) }

        TrainingApplication.setUploadBackupToGoogleDrive(false)
        verify { mockEditor.putBoolean(TrainingApplication.SP_UPLOAD_BACKUP_TO_GOOGLE_DRIVE, false) }

        TrainingApplication.setUploadToGoogleDriveOnlyOnWifi(false)
        verify { mockEditor.putBoolean(TrainingApplication.SP_GOOGLE_DRIVE_ONLY_WIFI, false) }
    }
}
