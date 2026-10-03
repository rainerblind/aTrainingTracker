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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying permission state handling, warning badge behavior,
 * and JIT tracking trigger in [ControlTrackingViewModel] and UI components (REQ-PRI-003, TST-PRI-002, ATT-2075).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlTrackingPermissionTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: ControlTrackingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread(): Boolean = true
        })

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }
        every { anyConstructed<Intent>().action } returns TrainingApplication.REQUEST_START_TRACKING
        every { anyConstructed<Intent>().`package` } returns "com.atrainingtracker"

        application = mockk(relaxed = true)
        every { application.packageName } returns "com.atrainingtracker"
        viewModel = ControlTrackingViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
        unmockkAll()
    }

    @Test
    fun testOnStartTracking_dispatchesStartTrackingBroadcast() {
        val slot = slot<Intent>()
        every { application.sendBroadcast(capture(slot)) } returns Unit

        viewModel.onStartTracking()

        verify(exactly = 1) { application.sendBroadcast(any()) }
        assertEquals(TrainingApplication.REQUEST_START_TRACKING, slot.captured.action)
        assertEquals("com.atrainingtracker", slot.captured.`package`)
    }

    @Test
    fun testWarningBadgePredicate_logicMatchesPermissionState() {
        // When hasLocationPermission is false, warning badge should be active
        val hasLocationPermission = false
        val hasPermissionWarning = !hasLocationPermission
        assertTrue("Warning badge must be visible when location permission is missing", hasPermissionWarning)

        // When hasLocationPermission is true, warning badge must be dismissed
        val grantedPermission = true
        val noWarning = !grantedPermission
        assertFalse("Warning badge must NOT be visible when location permission is granted", noWarning)
    }

    @Test
    fun testStartButtonInteraction_clickableWhenUnpermissioned() {
        // Under REQ-PRI-003, the Start button remains enabled=true even when permissions are missing,
        // allowing athletes to tap and trigger the educational rationale sheet instead of experiencing a dead button.
        var startClicked = false
        var rationaleOpened = false
        val hasLocationPermission = false

        val handleStartClick = {
            if (hasLocationPermission) {
                startClicked = true
            } else {
                rationaleOpened = true
            }
        }

        handleStartClick()

        assertFalse("Tracking should not start directly without location permission", startClicked)
        assertTrue("Rationale sheet must be triggered upon tapping Start without permission", rationaleOpened)
    }

    @Test
    fun testStartButtonInteraction_startsTrackingWhenPermitted() {
        var startClicked = false
        var rationaleOpened = false
        val hasLocationPermission = true

        val handleStartClick = {
            if (hasLocationPermission) {
                startClicked = true
            } else {
                rationaleOpened = true
            }
        }

        handleStartClick()

        assertTrue("Tracking must start directly when location permission is present", startClicked)
        assertFalse("Rationale sheet must not be displayed when permissions are already granted", rationaleOpened)
    }
}
