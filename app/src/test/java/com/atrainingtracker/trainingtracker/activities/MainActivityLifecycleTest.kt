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

import android.app.Activity
import android.app.Dialog
import android.content.Context
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.maps.MapsInitializer
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Unit and contract tests for [MainActivityWithNavigation] lifecycle safety, coroutine scoping,
 * in-ride battery alert deduplication retention, off-main-thread database execution,
 * and dialog leak defense (REQ-STB-019, TST-STB-019, ATT-4020).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityLifecycleTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var dbManagerMock: WorkoutSummariesDatabaseManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MainActivityWithNavigation.defaultIoDispatcher = testDispatcher
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(WorkoutSummariesDatabaseManager::class)
        dbManagerMock = mockk(relaxed = true)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns dbManagerMock

        mockkStatic(MapsInitializer::class)
        every { MapsInitializer.initialize(any(), any(), any()) } returns 0
        every { MapsInitializer.initialize(any()) } returns 0

        mockkStatic(GooglePlayServicesUtil::class)
        every { GooglePlayServicesUtil.isGooglePlayServicesAvailable(any()) } returns 0
        every { GooglePlayServicesUtil.getErrorDialog(any(), any(), any()) } returns null

        try {
            val rmiClass = Class.forName("androidx.appcompat.widget.ResourceManagerInternal")
            val getMethod = rmiClass.getDeclaredMethod("get")
            getMethod.isAccessible = true
            val rmiInstance = getMethod.invoke(null)
            val field = rmiClass.getDeclaredField("mHasCheckedVectorDrawableSetup")
            field.isAccessible = true
            field.setBoolean(rmiInstance, true)
        } catch (ignored: Exception) {}

        MainActivityWithNavigation.sInRideAlertedSensorIds.clear()
        MainActivityWithNavigation.sInRideCriticalAlertedSensorIds.clear()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setResumeFromCrash(false)
    }

    @After
    fun tearDown() {
        unmockkAll()
        MainActivityWithNavigation.sInRideAlertedSensorIds.clear()
        MainActivityWithNavigation.sInRideCriticalAlertedSensorIds.clear()
        MainActivityWithNavigation.defaultIoDispatcher = Dispatchers.IO
        Dispatchers.resetMain()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setResumeFromCrash(false)
    }

    private fun createActivity(): ActivityController<MainActivityWithNavigation> {
        return Robolectric.buildActivity(MainActivityWithNavigation::class.java).create()
    }

    // --- TST-STB-019.1: Unconditional Service Unbinding & Runnable Cleanup ---

    @Test
    fun testOnDestroy_unconditionallyUnbindsAndRemovesRunnable() {
        val controller = createActivity()
        val activity = controller.get()

        // Schedule disconnect runnable on handler
        activity.mHandler.post(activity.mDisconnectFromBANALServiceRunnable)
        assertTrue(
            "Disconnect runnable must be pending in handler",
            activity.mHandler.hasCallbacks(activity.mDisconnectFromBANALServiceRunnable)
        )

        // Invoke destroy via controller (calls onDestroy exactly once)
        controller.destroy()

        // Verify runnable was removed and service disconnected
        assertFalse(
            "Disconnect runnable must be cancelled in onDestroy",
            activity.mHandler.hasCallbacks(activity.mDisconnectFromBANALServiceRunnable)
        )
        assertNull("BANALServiceComm must be null after disconnect", activity.getBanalServiceComm())
    }

    @Test
    fun testOnDestroy_whenChangingConfigurations_unconditionallyUnbindsAndRemovesRunnable() {
        val controller = createActivity()
        val activity = controller.get()

        val field = Activity::class.java.getDeclaredField("mChangingConfigurations")
        field.isAccessible = true
        field.setBoolean(activity, true)

        activity.mHandler.post(activity.mDisconnectFromBANALServiceRunnable)
        assertTrue(
            "Disconnect runnable must be pending in handler",
            activity.mHandler.hasCallbacks(activity.mDisconnectFromBANALServiceRunnable)
        )

        controller.destroy()

        assertFalse(
            "Disconnect runnable must be cancelled in onDestroy during configuration change",
            activity.mHandler.hasCallbacks(activity.mDisconnectFromBANALServiceRunnable)
        )
        assertNull("BANALServiceComm must be null after disconnect", activity.getBanalServiceComm())
    }

    // --- TST-STB-019.2: Coroutine Scoping & Job Cancellation ---

    @Test
    fun testLifecycleScopeCoroutines_automaticallyCancelledOnDestroy() {
        val controller = createActivity()
        val activity = controller.get()

        val job = activity.lifecycleScope.coroutineContext.job
        assertTrue("lifecycleScope job must be active while activity is running", job.isActive)

        controller.destroy()
        assertTrue("lifecycleScope job must be cancelled upon onDestroy", job.isCancelled)
    }

    // --- TST-STB-019.3: In-Ride Battery State Retention & Screen-Off Collection ---

    @Test
    fun testInRideBatteryAlertDeduplication_survivesActivityRecreation() {
        val controller1 = createActivity()
        val activity1 = controller1.get()

        activity1.mInRideAlertedSensorIds.add(101L)
        activity1.mInRideCriticalAlertedSensorIds.add(202L)

        controller1.destroy()

        // Recreate activity (simulating rotation rebuild)
        val controller2 = createActivity()
        val activity2 = controller2.get()

        assertTrue(
            "Warning alert deduplication state must survive recreation",
            activity2.mInRideAlertedSensorIds.contains(101L)
        )
        assertTrue(
            "Critical alert deduplication state must survive recreation",
            activity2.mInRideCriticalAlertedSensorIds.contains(202L)
        )

        controller2.destroy()
    }

    @Test
    fun testOnPause_whenTracking_maintainsInRideBatteryMonitoring() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        val controller = createActivity()
        val activity = controller.get()

        activity.startInRideBatteryMonitoring()
        assertTrue(
            "In-ride monitoring job must be active",
            activity.mInRideBatteryMonitoringJob?.isActive == true
        )

        controller.pause()
        assertTrue(
            "In-ride monitoring must remain active during onPause while tracking",
            activity.mInRideBatteryMonitoringJob?.isActive == true
        )

        controller.destroy()
    }

    @Test
    fun testOnPause_whenNotTracking_stopsInRideBatteryMonitoring() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        val controller = createActivity()
        val activity = controller.get()

        activity.startInRideBatteryMonitoring()
        assertTrue(
            "In-ride monitoring job must be active",
            activity.mInRideBatteryMonitoringJob?.isActive == true
        )

        controller.pause()
        assertNull(
            "In-ride monitoring job must be stopped during onPause when not tracking",
            activity.mInRideBatteryMonitoringJob
        )

        controller.destroy()
    }

    // --- TST-STB-019.4: Off-Main-Thread SQLite Database Execution ---

    @Test
    fun testCheckUnfinishedWorkout_dispatchesToIoDispatcher() {
        every { dbManagerMock.hasUnfinishedWorkout() } returns true

        val controller = createActivity()
        val activity = controller.get()
        activity.checkUnfinishedWorkout()

        verify(atLeast = 1) { dbManagerMock.hasUnfinishedWorkout() }
        controller.destroy()
    }

    @Test
    fun testChooseStart_dispatchesDiscardToIoDispatcher() {
        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        activity.chooseStart()

        verify { dbManagerMock.discardOrFinishUnfinishedWorkout() }
        assertFalse(TrainingApplication.isResumeFromCrash())
    }

    @Test
    fun testChooseResume_dispatchesCheckToIoDispatcher() {
        every { dbManagerMock.hasUnfinishedWorkout() } returns true
        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        activity.chooseResume()

        verify { dbManagerMock.hasUnfinishedWorkout() }
        assertTrue(TrainingApplication.isResumeFromCrash())
    }

    @Test
    fun testCheckGpsEnabledIfPermitted_runsOnIoDispatcher() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.trackLocation() } returns false

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        activity.checkGpsEnabledIfPermitted()

        verify { TrainingApplication.trackLocation() }
    }

    @Test
    fun testOnServiceConnected_dispatchesFiltersQueryToIoDispatcher() {
        mockkStatic(TrackingViewsDatabaseManager::class)
        val trackingViewsDbMock = mockk<TrackingViewsDatabaseManager>(relaxed = true)
        every { TrackingViewsDatabaseManager.getInstance(any()) } returns trackingViewsDbMock
        every { trackingViewsDbMock.getAllFilterData(any()) } returns emptyList()

        val activity = Robolectric.buildActivity(MainActivityWithNavigation::class.java).get()
        val mockServiceComm = mockk<BANALService.BANALServiceComm>(relaxed = true)

        activity.mBanalConnection.onServiceConnected(null, mockServiceComm)

        verify { trackingViewsDbMock.getAllFilterData(any()) }
    }

    // --- TST-STB-019.5: Dialog Window Leak Prevention ---

    @Test
    fun testPlayServicesDialog_dismissedInOnDestroy() {
        val controller = createActivity()
        val activity = controller.get()

        val mockDialog = mockk<Dialog>(relaxed = true)
        every { mockDialog.isShowing } returns true
        activity.mPlayServicesErrorDialog = mockDialog

        controller.destroy()

        verify { mockDialog.dismiss() }
        assertNull(
            "mPlayServicesErrorDialog must be cleared after onDestroy",
            activity.mPlayServicesErrorDialog
        )
    }
}
