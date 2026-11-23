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

package com.atrainingtracker.trainingtracker.ui.ant

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.navigation.AntAlertDialogsOverlay
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.maps.MapsInitializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Behavioral Compose UI & Robolectric [MainActivityWithNavigation] regression tests for
 * [AntAlertDialogsOverlay] and [com.atrainingtracker.trainingtracker.ui.ant.AntMissingAdapterDialog]
 * (ATT-9065 / REQ-UI-353 / REQ-UI-323 / TST-UI-313).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AntMissingAdapterDialogComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var storeScope: CoroutineScope

    private class ControllablePreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences(),
        private val initialEmissionGate: CompletableDeferred<Unit>? = null,
        private val writeGate: CompletableDeferred<Unit>? = null
    ) : DataStore<Preferences> {
        private val stateFlow = MutableStateFlow(initialPreferences)

        override val data: Flow<Preferences> = flow {
            initialEmissionGate?.await()
            stateFlow.collect { emit(it) }
        }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            writeGate?.await()
            val updated = transform(stateFlow.value)
            stateFlow.value = updated
            return updated
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MainActivityWithNavigation.defaultIoDispatcher = testDispatcher
        application = ApplicationProvider.getApplicationContext()
        storeScope = CoroutineScope(SupervisorJob() + testDispatcher)

        val workoutRepoMock = mockk<WorkoutRepository>(relaxed = true)
        WorkoutRepository.resetForTesting(workoutRepoMock)

        mockkStatic(MapsInitializer::class)
        every { MapsInitializer.initialize(any(), any(), any()) } returns 0
        every { MapsInitializer.initialize(any()) } returns 0

        mockkStatic(GooglePlayServicesUtil::class)
        every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns 0
        every { GooglePlayServicesUtil.getErrorDialog(any(), any(), any()) } returns null

        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setResumeFromCrash(false)
    }

    @After
    fun tearDown() {
        TuningPreferencesDataStore.setInstanceForTesting(null)
        WorkoutRepository.resetForTesting(null)
        TrackingViewsRepository.resetForTesting(null)
        unmockkAll()
        MainActivityWithNavigation.defaultIoDispatcher = Dispatchers.IO
        Dispatchers.resetMain()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setResumeFromCrash(false)
    }

    @Test
    fun dismissMissingAdapterDialog_persistsSuppressionAfterDialogExitsComposition_andBlocksReopening(): Unit = runBlocking {
        val writeGate = CompletableDeferred<Unit>()
        val backing = ControllablePreferencesDataStore(writeGate = writeGate)
        val store = TuningPreferencesDataStore.createForTesting(
            context = application,
            testScope = storeScope,
            dataStoreOverride = backing
        )

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        activity.isAntMissingAdapterSuppressionLoaded = true
        activity.isAntMissingAdapterDialogSuppressed = false
        activity.antDialogState = AntDialogState.MissingAdapter

        composeTestRule.setContent {
            val outerScope = rememberCoroutineScope()
            ATrainingTrackerTheme {
                AntAlertDialogsOverlay(
                    activity = activity,
                    scope = outerScope,
                    tuningDataStore = store
                )
            }
        }

        val title = application.getString(R.string.ant_missing_adapter_title)
        val dismissLabel = application.getString(R.string.ant_dialog_dismiss)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(dismissLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // MissingAdapter branch has now left composition while writeGate is still suspended
        composeTestRule.onNodeWithText(title).assertDoesNotExist()
        assertNull(activity.antDialogState)
        assertTrue(
            "isAntMissingAdapterDialogSuppressed must be true synchronously on dismiss",
            activity.isAntMissingAdapterDialogSuppressed
        )

        // Repeated broadcast arriving while DataStore write is still in flight must NOT reopen dialog
        activity.showANTAdapterMissingDialog()
        composeTestRule.waitForIdle()
        assertNull(activity.antDialogState)
        composeTestRule.onNodeWithText(title).assertDoesNotExist()

        // Complete the suspended DataStore write AFTER MissingAdapter left composition
        writeGate.complete(Unit)
        composeTestRule.waitForIdle()

        assertTrue(
            "Suppression must still persist to DataStore after MissingAdapter left composition",
            store.suppressAntMissingAdapterDialogFlow.first()
        )
        assertFalse(activity.isPersistingAntMissingAdapterSuppression)
    }

    @Test
    fun viewStatusAction_opensStatusSheetWithoutMutatingSuppressionPreference(): Unit = runBlocking {
        val backing = ControllablePreferencesDataStore()
        val store = TuningPreferencesDataStore.createForTesting(
            context = application,
            testScope = storeScope,
            dataStoreOverride = backing
        )

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        activity.isAntMissingAdapterSuppressionLoaded = true
        activity.isAntMissingAdapterDialogSuppressed = false
        activity.antDialogState = AntDialogState.MissingAdapter

        composeTestRule.setContent {
            val outerScope = rememberCoroutineScope()
            ATrainingTrackerTheme {
                AntAlertDialogsOverlay(
                    activity = activity,
                    scope = outerScope,
                    tuningDataStore = store
                )
            }
        }

        val viewStatusLabel = application.getString(R.string.ant_dialog_view_status)
        val statusSheetTitle = application.getString(R.string.ant_status_sheet_title)
        composeTestRule.onNodeWithText(viewStatusLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertNull("MissingAdapter dialog must dismiss on View Status", activity.antDialogState)
        assertTrue("showAntStatusSheet must become true", activity.showAntStatusSheet)
        composeTestRule.onNodeWithText(statusSheetTitle).assertIsDisplayed()
        assertFalse(
            "onViewStatus must NOT persist suppression preference (REQ-UI-353)",
            store.suppressAntMissingAdapterDialogFlow.first()
        )

        // While status sheet is visible, incoming broadcasts must not reopen MissingAdapter dialog
        activity.showANTAdapterMissingDialog()
        assertNull(activity.antDialogState)
    }

    @Test
    fun coldStartBroadcast_beforeInitialDataStoreEmission_neverShowsWhenPersistedTrue() {
        val initialGate = CompletableDeferred<Unit>()
        val backing = ControllablePreferencesDataStore(
            initialPreferences = preferencesOf(
                TuningPreferencesDataStore.KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG to true
            ),
            initialEmissionGate = initialGate
        )
        val store = TuningPreferencesDataStore.createForTesting(
            context = application,
            testScope = storeScope,
            dataStoreOverride = backing
        )
        TuningPreferencesDataStore.setInstanceForTesting(store)

        val controller = Robolectric.buildActivity(MainActivityWithNavigation::class.java)
            .create()
            .start()
            .resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        // Cold-start broadcast arrives before DataStore finishes initial emission
        application.sendBroadcast(Intent("com.atrainingtracker.ADAPTER_NOT_DETECTED").setPackage(application.packageName))
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(
            "Cold-start broadcast must not open dialog before DataStore hydrates",
            activity.antDialogState
        )

        // Release initial DataStore emission (persisted = true)
        initialGate.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(activity.isAntMissingAdapterDialogSuppressed)
        assertNull(
            "Persisted true suppression must keep dialog closed after cold-start hydration",
            activity.antDialogState
        )

        controller.pause().stop().destroy()
    }

    @Test
    fun activityBroadcastLifecycle_dismissSuppressesRepeatedBroadcasts_andTuningReenableRestores() {
        runBlocking<Unit> {
        val backing = ControllablePreferencesDataStore(
            initialPreferences = preferencesOf(
                TuningPreferencesDataStore.KEY_SUPPRESS_ANT_MISSING_ADAPTER_DIALOG to false
            )
        )
        val store = TuningPreferencesDataStore.createForTesting(
            context = application,
            testScope = storeScope,
            dataStoreOverride = backing
        )
        TuningPreferencesDataStore.setInstanceForTesting(store)

        val controller = Robolectric.buildActivity(MainActivityWithNavigation::class.java)
            .create()
            .start()
            .resume()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        // Unsuppressed broadcast opens MissingAdapter
        application.sendBroadcast(Intent("com.atrainingtracker.ADAPTER_NOT_DETECTED").setPackage(application.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AntDialogState.MissingAdapter, activity.antDialogState)

        // Dismiss synchronously + persist true -> repeated broadcasts stay suppressed
        activity.onAntMissingAdapterDialogDismissed()
        store.setSuppressAntMissingAdapterDialog(true)
        activity.isPersistingAntMissingAdapterSuppression = false
        shadowOf(Looper.getMainLooper()).idle()

        repeat(3) {
            application.sendBroadcast(Intent("com.atrainingtracker.ADAPTER_NOT_DETECTED").setPackage(application.packageName))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull("Repeated broadcast #$it after dismiss must be suppressed", activity.antDialogState)
        }

        // Re-enable warnings in Tuning settings -> next broadcast displays dialog again
        store.setSuppressAntMissingAdapterDialog(false)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(activity.isAntMissingAdapterDialogSuppressed)
        application.sendBroadcast(Intent("com.atrainingtracker.ADAPTER_NOT_DETECTED").setPackage(application.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(AntDialogState.MissingAdapter, activity.antDialogState)

        controller.pause().stop().destroy()
        }
    }
}
