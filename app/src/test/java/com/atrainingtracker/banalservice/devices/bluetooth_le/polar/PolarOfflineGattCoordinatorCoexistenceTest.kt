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
import androidx.test.core.app.ApplicationProvider
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests verifying persistent [PolarBleApi] session reuse and Verity Sense per-channel
 * `ACC` mutual exclusion in [PolarOfflineGattCoordinator] and [PolarVeritySenseOfflineController]
 * (`ATT-8016`, `REQ-SET-112.3`, `TST-SET-114.4`).
 */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class PolarOfflineGattCoordinatorCoexistenceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PolarOfflineGattCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineGattCoordinator.resetForTesting()
    }

    @Test
    fun testWithGattYieldAndPolarApi_reusesActiveBridgeSessionWithoutTeardown() = runTest {
        // TST-SET-114.4
        val persistentApi = mockk<PolarBleApi>(relaxed = true)
        val bridgeCallback = mockk<PolarBleApiCallback>(relaxed = true)
        every { persistentApi.isFeatureReady("ABCD1234", any()) } returns true

        PolarOfflineGattCoordinator.registerActiveSession("ABCD1234", persistentApi, bridgeCallback)

        val result = PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
            context = context,
            macAddress = "AA:BB:CC:DD:EE:01",
            polarId = "ABCD1234",
            features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
            timeoutMs = 5_000L
        ) { api ->
            assertSame("Must reuse active bridge PolarBleApi", persistentApi, api)
            "ok"
        }

        assertTrue(result == "ok")
        verify(exactly = 0) { persistentApi.disconnectFromDevice(any()) }
        verify(exactly = 0) { persistentApi.cleanup() }
    }

    @Test
    fun testPerChannelAccMutualExclusion_blocksConcurrentOfflineAndOnlineAccOnVeritySense() = runTest {
        // TST-SET-114.4
        val mockApi = mockk<PolarBleApi>(relaxed = true)

        assertTrue(
            "Online bridge acquires ACC channel first",
            PolarOfflineGattCoordinator.tryAcquireAccChannel("ABCD1234", PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        )
        assertFalse(
            "Offline ACC recording must be blocked while online ACC is active",
            PolarVeritySenseOfflineController.startOfflineRecordingGuarded(
                mockApi,
                "ABCD1234",
                PolarBleApi.PolarDeviceDataType.ACC
            )
        )
        assertTrue(
            "Offline HR recording remains allowed alongside online ACC",
            PolarVeritySenseOfflineController.startOfflineRecordingGuarded(
                mockApi,
                "ABCD1234",
                PolarBleApi.PolarDeviceDataType.HR
            )
        )
        coVerify(exactly = 0) {
            mockApi.startOfflineRecording("ABCD1234", PolarBleApi.PolarDeviceDataType.ACC, any(), any())
        }
        coVerify(exactly = 1) {
            mockApi.startOfflineRecording("ABCD1234", PolarBleApi.PolarDeviceDataType.HR, null, null)
        }

        PolarOfflineGattCoordinator.releaseAccChannel("ABCD1234", PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        assertTrue(
            "Offline ACC acquires channel once online stream releases it",
            PolarVeritySenseOfflineController.startOfflineRecordingGuarded(
                mockApi,
                "ABCD1234",
                PolarBleApi.PolarDeviceDataType.ACC
            )
        )
    }
}
