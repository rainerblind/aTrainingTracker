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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.tracker

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
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
import java.lang.reflect.Field
import java.util.concurrent.ExecutorService

/**
 * Unit tests verifying guaranteed workout finalization and broadcast dispatch upon stop
 * (REQ-TRK-016, TST-TRK-008, ATT-2972).
 */
class TrackerServiceFinalizationTest {

    private class TestableTrackerService : TrackerService() {
        var endWorkoutCalled = false
        var testPackageName = "com.atrainingtracker"
        var testSdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        var bluetoothGranted = false
        var healthGranted = false

        override fun getBuildVersionSdkInt(): Int = testSdkInt
        override fun hasBluetoothPermission(): Boolean = bluetoothGranted
        override fun hasHealthPermission(): Boolean = healthGranted

        override fun endWorkout() {
            endWorkoutCalled = true
        }

        override fun getPackageName(): String = testPackageName

        override fun performSuperOnDestroy() {
            // No-op in unit tests
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            // No-op in unit tests
        }

        override fun unbindService(conn: ServiceConnection) {
            // No-op in unit tests
        }
    }

    private lateinit var service: TestableTrackerService

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        service = TestableTrackerService()

        // Mock mDbExecutor to prevent NPE during shutdown in onDestroy
        val executorMock = mockk<ExecutorService>(relaxed = true)
        every { executorMock.awaitTermination(any(), any()) } returns true
        val dbExecutorField: Field = TrackerService::class.java.getDeclaredField("mDbExecutor")
        dbExecutorField.isAccessible = true
        dbExecutorField.set(service, executorMock)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setWorkoutId(workoutId: Long) {
        val field: Field = TrackerService::class.java.getDeclaredField("mWorkoutID")
        field.isAccessible = true
        field.set(service, workoutId)
    }

    private fun setTrackingInterrupted(interrupted: Boolean) {
        val field: Field = TrackerService::class.java.getDeclaredField("mTrackingInterrupted")
        field.isAccessible = true
        field.set(service, interrupted)
    }

    @Test
    fun testOnDestroy_WhenTrackingNotInterrupted_CallsEndWorkout() {
        setWorkoutId(1234L)
        setTrackingInterrupted(false)

        service.onDestroy()

        assertTrue("endWorkout must be called when tracking stops normally", service.endWorkoutCalled)
    }

    @Test
    fun testOnDestroy_WhenTrackingInterrupted_DoesNotCallEndWorkoutToPreserveUnfinishedWorkout() {
        setWorkoutId(1234L)
        setTrackingInterrupted(true)

        service.onDestroy()

        assertFalse("endWorkout must NOT be called if tracking was interrupted so unfinished state is preserved for resume", service.endWorkoutCalled)
    }

    @Test
    fun testDetermineForegroundServiceType_WithoutHealthPermission_OmitsHealthType() {
        service.bluetoothGranted = true
        service.healthGranted = false

        val fgsType = service.determineForegroundServiceType()

        val expected = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        assertEquals(expected, fgsType)
        assertFalse("Health FGS type must NOT be requested without health permission to prevent SecurityException",
            (fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH) != 0)
    }
}
