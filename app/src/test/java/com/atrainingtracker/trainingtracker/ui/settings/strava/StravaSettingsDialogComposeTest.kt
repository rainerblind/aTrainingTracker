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

package com.atrainingtracker.trainingtracker.ui.settings.strava

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaAuthRepository
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaEquipmentSyncWorker
import com.atrainingtracker.trainingtracker.onlinecommunities.strava.StravaPhotoPreferences
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.routes.StravaRoutesSyncWorker
import com.atrainingtracker.trainingtracker.security.AuthCredentialsStore
import com.atrainingtracker.trainingtracker.segments.SegmentsRepository
import com.atrainingtracker.trainingtracker.segments.StravaSegmentsSyncWorker
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Robolectric Compose UI tests for StravaSettingsDialog photo download toggle
 * (`ATT-8100`, `REQ-EXP-050`, `TST-EXP-050.1`).
 */
// Linked test specs: TST-EXP-050
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class StravaSettingsDialogComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setStaticField(TrainingApplication::class.java, "cAppContext", context)
        setStaticField(
            TrainingApplication::class.java,
            "cSharedPreferences",
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
        AuthCredentialsStore.setStravaAccessToken(context, "test_access_token")

        val mockSegmentsRepo = mockk<SegmentsRepository>(relaxed = true)
        every { mockSegmentsRepo.isSyncing } returns MutableStateFlow(false)
        SegmentsRepository.resetForTesting(mockSegmentsRepo)

        val mockRoutesRepo = mockk<RoutesRepository>(relaxed = true)
        every { mockRoutesRepo.isSyncing } returns MutableStateFlow(false)
        RoutesRepository.resetForTesting(mockRoutesRepo)

        mockkObject(StravaSegmentsSyncWorker)
        every { StravaSegmentsSyncWorker.schedule(any()) } returns Unit
        mockkObject(StravaRoutesSyncWorker)
        every { StravaRoutesSyncWorker.schedule(any()) } returns Unit
        mockkObject(StravaEquipmentSyncWorker)
        every { StravaEquipmentSyncWorker.schedule(any()) } returns Unit

        StravaAuthRepository.getInstance().resetState()
    }

    @After
    fun tearDown() {
        unmockkAll()
        SegmentsRepository.resetForTesting(null)
        RoutesRepository.resetForTesting(null)
        AuthCredentialsStore.clearStravaCredentials(context)
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    private fun setStaticField(clazz: Class<*>, fieldName: String, value: Any?) {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(null, value)
    }

    @Test
    fun testStravaPhotoDownloadToggle_displayedAndPersistedOnSave() {
        var dismissed = false
        composeRule.setContent {
            ATrainingTrackerTheme {
                StravaSettingsDialog(onDismiss = { dismissed = true })
            }
        }

        val sectionText = context.getString(R.string.strava_activity_photos_section)
        val titleText = context.getString(R.string.strava_download_photos_title)
        val descText = context.getString(R.string.strava_download_photos_desc)
        val saveText = context.getString(R.string.save)

        composeRule.onNodeWithText(sectionText).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(titleText).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(descText).performScrollTo().assertIsDisplayed()

        val switchNode = composeRule.onNodeWithTag("strava_download_photos_switch")
        switchNode.performScrollTo()
        switchNode.assertIsOff()
        switchNode.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        switchNode.assertIsOn()

        // Not persisted yet until Save is tapped
        assertFalse(StravaPhotoPreferences.isDownloadEnabled(context))

        composeRule.onNodeWithText(saveText).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue("Dismiss must be invoked on Save", dismissed)
        assertTrue("downloadStravaPhotos must persist true after Save", StravaPhotoPreferences.isDownloadEnabled(context))
    }

    @Test
    fun testStravaPhotoDownloadToggle_cancelDoesNotPersistChange() {
        var dismissed = false
        composeRule.setContent {
            ATrainingTrackerTheme {
                StravaSettingsDialog(onDismiss = { dismissed = true })
            }
        }

        val switchNode = composeRule.onNodeWithTag("strava_download_photos_switch")
        switchNode.performScrollTo()
        switchNode.assertIsOff()
        switchNode.performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        switchNode.assertIsOn()

        val cancelText = context.getString(R.string.Cancel)
        composeRule.onNodeWithText(cancelText).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertTrue("Dismiss must be invoked on Cancel", dismissed)
        assertFalse("downloadStravaPhotos must remain false after Cancel", StravaPhotoPreferences.isDownloadEnabled(context))
    }
}
