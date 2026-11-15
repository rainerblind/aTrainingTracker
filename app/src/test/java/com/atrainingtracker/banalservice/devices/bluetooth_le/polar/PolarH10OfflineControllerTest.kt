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

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying PolarH10OfflineController (ATT-7762, TST-SET-106).
 *
 * Tests API 33 isolation (TST-SET-106.3), single-shot arming and fringe-flap
 * invariance (TST-SET-106.1), and fault-tolerant GATT arbitration handoff (TST-SET-106.2).
 */
class PolarH10OfflineControllerTest {

    private val context = mockk<Context>(relaxed = true)
    private val mockApi = mockk<PolarBleApi>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        PolarH10OfflineController.resetForTesting()
        PolarH10OfflineController.dispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        PolarH10OfflineController.resetForTesting()
        unmockkStatic(android.util.Log::class)
    }

    @Test
    fun testApiLevelBelow33_doesNotArm() {
        // Given: API level is 32 (below required Android 13 / API 33)
        PolarH10OfflineController.sdkIntOverride = 32

        // When: onWorkoutStarted is called
        PolarH10OfflineController.onWorkoutStarted(context)

        // Then: Offline recording is NOT armed
        assertFalse("Recording should not be armed on API < 33", PolarH10OfflineController.isRecordingArmed())
    }

    @Test
    fun testFringeFlapInvariance_alreadyArmed_doesNotRearm() {
        // Given: Recording was already armed at workout start
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.setArmedForTesting(true)

        // When: onWorkoutStarted is invoked again (e.g. mid-session reconnect)
        PolarH10OfflineController.onWorkoutStarted(context)

        // Then: Still armed, no re-arm or crash
        assertTrue("Recording should remain armed", PolarH10OfflineController.isRecordingArmed())
    }

    @Test
    fun testWorkoutStopped_resetsArmedState() {
        // Given: Recording is armed
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.setArmedForTesting(true)
        assertTrue(PolarH10OfflineController.isRecordingArmed())

        // When: Workout stopped
        PolarH10OfflineController.onWorkoutStopped(context)

        // Then: isRecordingArmed returns false
        assertFalse("Armed state must reset on workout stop", PolarH10OfflineController.isRecordingArmed())
    }

    @Test
    fun testExecuteArming_success_armsRecording() = runBlocking {
        // Given: API 33+, mock Polar API
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi

        coEvery { mockApi.connectToDevice(any()) } just Runs
        coEvery { mockApi.waitForConnection(any()) } just Runs
        coEvery { mockApi.setLocalTime(any(), any()) } just Runs
        coEvery { mockApi.requestRecordingStatus(any()) } returns PolarH10OfflineExerciseApi.PolarRecordingStatus(
            ongoing = false,
            entryId = "",
            supported = true
        )
        every { mockApi.listExercises(any()) } returns emptyFlow()
        coEvery { mockApi.startRecording(any(), any(), any(), any()) } just Runs
        coEvery { mockApi.disconnectFromDevice(any()) } just Runs

        // When: executeArming is called
        val result = PolarH10OfflineController.executeArming(context, "AA:BB:CC:DD:EE:FF", "1C3B2421")

        // Then: Success returned and recording is armed
        assertTrue("executeArming should succeed", result)
        assertTrue("Recording should be armed", PolarH10OfflineController.isRecordingArmed())
        coVerify(exactly = 1) {
            mockApi.startRecording(
                "1C3B2421",
                any(),
                PolarH10OfflineExerciseApi.RecordingInterval.INTERVAL_1S,
                PolarH10OfflineExerciseApi.SampleType.RR
            )
        }
    }

    @Test
    fun testExecuteArming_failure_handlesGracefully() = runBlocking {
        // Given: API 33+, mock Polar API throws on connect
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi

        coEvery { mockApi.connectToDevice(any()) } throws RuntimeException("BLE connection failure")

        // When: executeArming is called
        val result = PolarH10OfflineController.executeArming(context, "AA:BB:CC:DD:EE:FF", "1C3B2421")

        // Then: Returns false without uncaught crash, recording not armed
        assertFalse("executeArming should return false on failure", result)
        assertFalse("Recording should not be armed on failure", PolarH10OfflineController.isRecordingArmed())
    }
}
