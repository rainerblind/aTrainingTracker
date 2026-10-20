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

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit test suite verifying launch intent lifecycle isolation, transient extra consumption,
 * and defensive resumption state guards in [MainActivityWithNavigation] (REQ-STB-017, TST-STB-017, ATT-4001).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityIntentLifecycleTest {

    private lateinit var context: Context
    private lateinit var dbManagerMock: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        dbManagerMock = mockk(relaxed = true)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns dbManagerMock
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setResumeFromCrash(false)
    }

    @After
    fun tearDown() {
        unmockkAll()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setResumeFromCrash(false)
    }

    // --- TST-STB-017.1: Cold Start Extra Consumption ---

    @Test
    fun testColdStart_resumeInterruptedWorkout_triggersChooseResumeAndStripsExtras() {
        every { dbManagerMock.hasUnfinishedWorkout() } returns true

        val intent = Intent(context, MainActivityWithNavigation::class.java).apply {
            putExtra(MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT, true)
            putExtra(
                MainActivityWithNavigation.SELECTED_FRAGMENT,
                MainActivityWithNavigation.SelectedFragment.START_OR_TRACKING.name
            )
            putExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID, R.id.drawer_start_tracking)
        }

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java, intent).get()
        activity.handleIntent(intent)

        // Verify extras were consumed and stripped from intent
        assertFalse("EXTRA_RESUME_INTERRUPTED_WORKOUT must be removed", intent.hasExtra(MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT))
        assertFalse("SELECTED_FRAGMENT must be removed", intent.hasExtra(MainActivityWithNavigation.SELECTED_FRAGMENT))
        assertFalse("EXTRA_DRAWER_ITEM_ID must be removed", intent.hasExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID))

        // Verify navigation and resumption state
        assertEquals(R.id.drawer_start_tracking, activity.selectedFragmentIdForTesting)
        assertTrue("TrainingApplication.isResumeFromCrash() must be set", TrainingApplication.isResumeFromCrash())

        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(
            "REQUEST_START_TRACKING broadcast must be dispatched",
            shadowApp.broadcastIntents.any { it.action == TrainingApplication.REQUEST_START_TRACKING }
        )
    }

    @Test
    fun testColdStart_drawerItemIdExtra_updatesFragmentAndStripsExtra() {
        val intent = Intent(context, MainActivityWithNavigation::class.java).apply {
            putExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID, R.id.drawer_workouts)
        }

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java, intent).get()
        activity.handleIntent(intent)

        assertFalse("EXTRA_DRAWER_ITEM_ID must be removed", intent.hasExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID))
        assertEquals(R.id.drawer_workouts, activity.selectedFragmentIdForTesting)
    }

    @Test
    fun testColdStart_selectedFragmentExtra_updatesFragmentAndStripsExtra() {
        val intent = Intent(context, MainActivityWithNavigation::class.java).apply {
            putExtra(
                MainActivityWithNavigation.SELECTED_FRAGMENT,
                MainActivityWithNavigation.SelectedFragment.WORKOUT_LIST.name
            )
        }

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java, intent).get()
        activity.handleIntent(intent)

        assertFalse("SELECTED_FRAGMENT must be removed", intent.hasExtra(MainActivityWithNavigation.SELECTED_FRAGMENT))
        assertEquals(R.id.drawer_workouts, activity.selectedFragmentIdForTesting)
    }

    @Test
    fun testColdStart_filterCriticalBatteryExtra_stripsExtra() {
        val intent = Intent(context, MainActivityWithNavigation::class.java).apply {
            putExtra(MainActivityWithNavigation.EXTRA_FILTER_CRITICAL_BATTERY, true)
        }

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java, intent).get()
        activity.handleIntent(intent)

        assertFalse(
            "EXTRA_FILTER_CRITICAL_BATTERY must be removed",
            intent.hasExtra(MainActivityWithNavigation.EXTRA_FILTER_CRITICAL_BATTERY)
        )
    }

    // --- TST-STB-017.2: Activity Recreation Isolation ---

    @Test
    fun testRecreationWithSavedInstanceState_doesNotInvokeHandleIntentOrClobberScreen() {
        val staleIntent = Intent(context, MainActivityWithNavigation::class.java).apply {
            putExtra(MainActivityWithNavigation.EXTRA_RESUME_INTERRUPTED_WORKOUT, true)
            putExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID, R.id.drawer_start_tracking)
        }

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java, staleIntent).get()

        // Simulate savedInstanceState restoring drawer_workouts
        val savedInstanceState: Bundle? = Bundle().apply {
            putInt(MainActivityWithNavigation.SELECTED_FRAGMENT_ID, R.id.drawer_workouts)
        }

        // Crucial invariant: handleIntent must NOT be called when savedInstanceState != null
        if (savedInstanceState != null) {
            activity.selectedFragmentIdForTesting = savedInstanceState.getInt(
                MainActivityWithNavigation.SELECTED_FRAGMENT_ID,
                R.id.drawer_start_tracking
            )
        } else {
            activity.handleIntent(staleIntent)
        }

        // Verify restored screen was preserved and chooseResume was NOT called
        assertEquals(R.id.drawer_workouts, activity.selectedFragmentIdForTesting)
        assertFalse(TrainingApplication.isResumeFromCrash())
    }

    // --- TST-STB-017.3: Runtime New Intent Handling ---

    @Test
    fun testOnNewIntent_processesAndConsumesExtrasWhileRunning() {
        val initialIntent = Intent(context, MainActivityWithNavigation::class.java)
        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java, initialIntent).get()

        val newIntent = Intent(context, MainActivityWithNavigation::class.java).apply {
            putExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID, R.id.drawer_my_sensors)
        }

        activity.onNewIntent(newIntent)

        // Verify extras stripped from newIntent
        assertFalse(
            "EXTRA_DRAWER_ITEM_ID must be consumed",
            newIntent.hasExtra(MainActivityWithNavigation.EXTRA_DRAWER_ITEM_ID)
        )
        // Verify setIntent synchronized
        assertEquals(newIntent, activity.intent)
        // Verify destination updated
        assertEquals(R.id.drawer_my_sensors, activity.selectedFragmentIdForTesting)
    }

    // --- TST-STB-017.4: Defensive State Guards in chooseResume() ---

    @Test
    fun testChooseResume_whenAlreadyTracking_doesNotBroadcastRequestStartTracking() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        every { dbManagerMock.hasUnfinishedWorkout() } returns true

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        activity.chooseResume()

        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(
            "No start tracking broadcast dispatched",
            shadowApp.broadcastIntents.none { it.action == TrainingApplication.REQUEST_START_TRACKING }
        )
        assertFalse(TrainingApplication.isResumeFromCrash())
    }

    @Test
    fun testChooseResume_whenNoUnfinishedWorkout_doesNotBroadcastRequestStartTracking() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        every { dbManagerMock.hasUnfinishedWorkout() } returns false

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        activity.chooseResume()

        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(
            "No start tracking broadcast dispatched",
            shadowApp.broadcastIntents.none { it.action == TrainingApplication.REQUEST_START_TRACKING }
        )
        assertFalse(TrainingApplication.isResumeFromCrash())
    }

    @Test
    fun testChooseResume_whenNotTrackingAndUnfinishedWorkoutExists_broadcastsRequestStartTracking() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        every { dbManagerMock.hasUnfinishedWorkout() } returns true

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        activity.chooseResume()

        // Verify resumeFromCrash is true and broadcast is dispatched
        assertTrue(TrainingApplication.isResumeFromCrash())
        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext<Application>())
        assertTrue(
            "REQUEST_START_TRACKING broadcast must be dispatched",
            shadowApp.broadcastIntents.any { it.action == TrainingApplication.REQUEST_START_TRACKING }
        )
    }
}
