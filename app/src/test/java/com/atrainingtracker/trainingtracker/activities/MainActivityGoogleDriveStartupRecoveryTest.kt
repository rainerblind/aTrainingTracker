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

package com.atrainingtracker.trainingtracker.activities

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.cloud.googledrive.GoogleDriveAuthManager
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test suite verifying automatic recovery of credential-failed Google Drive workout exports on app startup
 * (REQ-DAT-037.4, TST-DAT-033.4, ATT-5081).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityGoogleDriveStartupRecoveryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MainActivityWithNavigation.defaultIoDispatcher = testDispatcher
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setAppContextForTesting(context)
        TrainingApplication.setSharedPreferencesForTesting(PreferenceManager.getDefaultSharedPreferences(context))

        mockkStatic(ExportManager::class)
        every { ExportManager.recoverStuckGoogleDriveExports(any()) } just Runs

        mockkStatic(GoogleSignIn::class)
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns null

        mockkObject(GoogleDriveAuthManager)
        every { GoogleDriveAuthManager.refreshTokenSync(any()) } returns Result.failure(IllegalStateException("No account"))
    }

    @After
    fun tearDown() {
        unmockkAll()
        MainActivityWithNavigation.defaultIoDispatcher = Dispatchers.IO
        Dispatchers.resetMain()
        TrainingApplication.setUploadToGoogleDrive(false)
        TrainingApplication.deleteGoogleDriveCredential()
    }

    @Test
    fun testStartup_validToken_triggersStuckGoogleDriveExportRecovery() {
        TrainingApplication.storeGoogleDriveCredential("test@example.com", "valid_mock_token")
        TrainingApplication.setUploadToGoogleDrive(true)

        val activityController = Robolectric.buildActivity(MainActivityWithNavigation::class.java)
        val activity = activityController.get()
        activity.ioDispatcher = testDispatcher
        activity.checkPreferences()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(TrainingApplication.uploadToGoogleDrive())
        verify(exactly = 1) { ExportManager.recoverStuckGoogleDriveExports(any()) }
        verify(exactly = 0) { GoogleDriveAuthManager.refreshTokenSync(any()) }
    }

    @Test
    fun testStartup_nullTokenWithAccount_refreshesTokenAndRecovers() {
        TrainingApplication.deleteGoogleDriveCredential()
        TrainingApplication.setUploadToGoogleDrive(true)

        val mockAccount = mockk<GoogleSignInAccount>(relaxed = true)
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns mockAccount
        every { GoogleDriveAuthManager.refreshTokenSync(any()) } returns Result.success("refreshed_mock_token")

        val activityController = Robolectric.buildActivity(MainActivityWithNavigation::class.java)
        val activity = activityController.get()
        activity.ioDispatcher = testDispatcher
        activity.checkPreferences()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(TrainingApplication.uploadToGoogleDrive())
        verify(exactly = 1) { GoogleDriveAuthManager.refreshTokenSync(any()) }
        verify(exactly = 1) { ExportManager.recoverStuckGoogleDriveExports(any()) }
    }

    @Test
    fun testStartup_nullTokenAndNoAccount_disablesUploadWithoutRecovery() {
        TrainingApplication.deleteGoogleDriveCredential()
        TrainingApplication.setUploadToGoogleDrive(true)
        every { GoogleSignIn.getLastSignedInAccount(any()) } returns null

        val activityController = Robolectric.buildActivity(MainActivityWithNavigation::class.java)
        val activity = activityController.get()
        activity.ioDispatcher = testDispatcher
        activity.checkPreferences()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(TrainingApplication.uploadToGoogleDrive())
        verify(exactly = 0) { GoogleDriveAuthManager.refreshTokenSync(any()) }
        verify(exactly = 0) { ExportManager.recoverStuckGoogleDriveExports(any()) }
    }
}
