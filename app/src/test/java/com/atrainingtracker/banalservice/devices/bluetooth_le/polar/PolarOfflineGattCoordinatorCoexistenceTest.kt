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
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.model.PolarDeviceInfo
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        DevicesDatabaseManager.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
    }

    @After
    fun tearDown() {
        PolarOfflineGattCoordinator.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        DevicesDatabaseManager.resetForTesting()
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

    @Test
    fun testSingletonRefCounting_creates8FeatureSupersetOnceAndShutsDownOnlyOnLastRelease() = runTest {
        // TST-SET-120.1, TST-SET-120.3
        val sharedMockApi = mockk<PolarBleApi>(relaxed = true)
        var factoryCalls = 0
        var capturedFeatures: Set<PolarBleApi.PolarBleSdkFeature>? = null
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, features ->
            factoryCalls++
            capturedFeatures = features
            sharedMockApi
        }

        val bridgeCallback = mockk<PolarBleApiCallback>(relaxed = true)
        val bridgeApi = PolarOfflineGattCoordinator.acquirePersistentSession(
            context = context,
            polarId = "1C3B2421",
            macAddress = "24:AC:AC:25:0F:A6",
            callback = bridgeCallback
        )
        assertSame(sharedMockApi, bridgeApi)
        assertEquals(1, factoryCalls)
        assertEquals(PolarOfflineGattCoordinator.ALL_POLAR_FEATURES, capturedFeatures)
        assertEquals(8, capturedFeatures?.size)
        assertEquals(1, PolarOfflineGattCoordinator.getActiveLeaseCountForTesting())

        // Sibling device offline operation must reuse the exact same singleton without cleanup/shutDown
        val offlineResult = PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
            context = context,
            macAddress = "24:AC:AC:24:82:56",
            polarId = "A1B2C3D4",
            features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
            timeoutMs = 5_000L
        ) { api ->
            assertSame(sharedMockApi, api)
            assertEquals(2, PolarOfflineGattCoordinator.getActiveLeaseCountForTesting())
            "done"
        }
        assertEquals("done", offlineResult)
        assertEquals(1, factoryCalls)
        assertEquals(1, PolarOfflineGattCoordinator.getActiveLeaseCountForTesting())
        verify(exactly = 0) { sharedMockApi.cleanup() }
        verify(exactly = 0) { sharedMockApi.shutDown() }

        // Final release shuts down singleton cleanly without ever calling cleanup()
        PolarOfflineGattCoordinator.releasePersistentSession(
            polarId = "1C3B2421",
            macAddress = "24:AC:AC:25:0F:A6",
            api = bridgeApi,
            callback = bridgeCallback
        )
        assertEquals(0, PolarOfflineGattCoordinator.getActiveLeaseCountForTesting())
        assertNull(PolarOfflineGattCoordinator.getSharedApiForTesting())
        verify(exactly = 0) { sharedMockApi.cleanup() }
        verify(exactly = 1) { sharedMockApi.shutDown() }
    }

    @Test
    fun testMultiplexedCallbacks_routeBidirectionalMacAndHexIdsWithoutClobbering() = runTest {
        // TST-SET-120.2
        val sharedMockApi = mockk<PolarBleApi>(relaxed = true)
        every { sharedMockApi.isFeatureReady(any(), any()) } returns false
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedMockApi }

        val h10Callback = mockk<PolarBleApiCallback>(relaxed = true)
        val verityCallback = mockk<PolarBleApiCallback>(relaxed = true)

        // Register H10 by MAC (without 8-hex name) and Verity by 8-hex ID
        val api1 = PolarOfflineGattCoordinator.acquirePersistentSession(
            context = context,
            polarId = "24:AC:AC:25:0F:A6",
            macAddress = "24:AC:AC:25:0F:A6",
            callback = h10Callback
        )
        val api2 = PolarOfflineGattCoordinator.acquirePersistentSession(
            context = context,
            polarId = "A1B2C3D4",
            macAddress = "24:AC:AC:24:82:56",
            callback = verityCallback
        )
        assertSame(api1, api2)
        // Global multiplexer installed strictly once
        verify(exactly = 1) { sharedMockApi.setApiCallback(any()) }

        // Launch concurrent awaitFeaturesReady alongside active bridge callbacks
        val awaitJob = async {
            PolarOfflineGattCoordinator.awaitFeaturesReady(
                api = api1,
                polarId = "A1B2C3D4",
                requiredFeatures = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 2_000L
            )
        }
        testScheduler.runCurrent()
        // awaitFeaturesViaCallback must NOT clobber api.setApiCallback()
        verify(exactly = 1) { sharedMockApi.setApiCallback(any()) }

        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
        val h10Info = PolarDeviceInfo("1C3B2421", "24:AC:AC:25:0F:A6", -50, "Polar H10", true)
        mux.deviceConnected(h10Info)
        // Callback emitted with 8-hex ID must route to H10 registered by MAC
        mux.bleSdkFeatureReady("1C3B2421", PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        // Callback emitted with MAC must route to Verity registered by 8-hex ID and resolve awaitFeaturesReady
        mux.bleSdkFeatureReady(
            "24:AC:AC:24:82:56",
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
        )

        assertTrue(awaitJob.await())
        verify(exactly = 1) { h10Callback.deviceConnected(h10Info) }
        verify(exactly = 1) { h10Callback.bleSdkFeatureReady("1C3B2421", PolarBleApi.PolarBleSdkFeature.FEATURE_HR) }
        verify(exactly = 0) { verityCallback.deviceConnected(h10Info) }
        verify(exactly = 0) { verityCallback.bleSdkFeatureReady("1C3B2421", any()) }
        verify(exactly = 1) {
            verityCallback.bleSdkFeatureReady(
                "24:AC:AC:24:82:56",
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
            )
        }
        verify(exactly = 0) { h10Callback.bleSdkFeatureReady("24:AC:AC:24:82:56", any()) }
        assertSame(api1, PolarOfflineGattCoordinator.getActiveSession("1C3B2421"))
        assertSame(api2, PolarOfflineGattCoordinator.getActiveSession("24:AC:AC:24:82:56"))
    }

    @Test
    fun testFindEnabledPolarDevice_skipsUnwornSecondarySensorWhenAnotherHrmReceives() {
        // TST-SET-120.4
        val db = DevicesDatabaseManager.getInstance(context)
        val h10Id = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "24:AC:AC:25:0F:A6",
            "Polar H10 1C3B2421",
            "Polar Electro",
            95,
            true
        )
        db.setPolarH10OfflineRecordingEnabled(h10Id, true)
        val verityId = db.insertNewBluetoothDeviceIntoDB(
            DeviceType.HRM,
            "24:AC:AC:24:82:56",
            "Polar Verity Sense A1B2C3D4",
            "Polar Electro",
            95,
            true
        )
        db.setPolarH10OfflineRecordingEnabled(verityId, true)

        val mockDm = mockk<DeviceManager>(relaxed = true)
        val activeH10 = mockk<MyRemoteDevice>(relaxed = true)
        every { activeH10.deviceType } returns DeviceType.HRM
        every { activeH10.isReceivingData } returns true
        every { activeH10.deviceId } returns h10Id

        mockkStatic(BANALService::class)
        try {
            every { BANALService.getDeviceManager() } returns mockDm
            every { mockDm.remoteDeviceList } returns java.util.LinkedList(listOf(activeH10))

            // Active H10 is resolved; unworn Verity Sense is skipped
            val resolvedH10 = PolarOfflineGattCoordinator.findEnabledPolarDevice(context) { name, _ ->
                PolarOfflineGattCoordinator.isPolarH10Name(name)
            }
            val skippedVerity = PolarOfflineGattCoordinator.findEnabledPolarDevice(context) { name, _ ->
                PolarOfflineGattCoordinator.isPolarVeritySenseName(name)
            }
            assertNotNull(resolvedH10)
            assertEquals("1C3B2421", resolvedH10?.polarId)
            assertNull(skippedVerity)

            // Fallback compatibility when DeviceManager is null (unit-test / cold-start parity)
            every { BANALService.getDeviceManager() } returns null
            val fallbackVerity = PolarOfflineGattCoordinator.findEnabledPolarDevice(context) { name, _ ->
                PolarOfflineGattCoordinator.isPolarVeritySenseName(name)
            }
            assertNotNull(fallbackVerity)
            assertEquals("A1B2C3D4", fallbackVerity?.polarId)
        } finally {
            unmockkStatic(BANALService::class)
        }
    }
}
