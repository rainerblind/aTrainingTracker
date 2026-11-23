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
import com.polar.androidcommunications.api.ble.BleLogger
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.model.PolarDeviceInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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

    // REQ-CON-054.1 / TST-CON-054.1
    @Test
    fun testForceDisconnectDeviceAndDisconnectUnpairedSessions_clearsTargetAliasesWithoutHarmingSibling_TST_CON_054_1() {
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }
        val verityMac = "24:AC:AC:24:82:56"
        val verityId = "24825632"
        val h10Mac = "AA:BB:CC:DD:EE:01"
        val h10Id = "1C3B2421"

        val verityCallback = mockk<PolarBleApiCallback>(relaxed = true)
        val h10Callback = mockk<PolarBleApiCallback>(relaxed = true)

        PolarOfflineGattCoordinator.acquirePersistentSession(context, verityId, verityMac, verityCallback)
        PolarOfflineGattCoordinator.acquirePersistentSession(context, h10Id, h10Mac, h10Callback)
        PolarOfflineGattCoordinator.tryAcquireAccChannel(verityId, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)
        PolarOfflineGattCoordinator.tryAcquireAccChannel(h10Id, PolarOfflineGattCoordinator.ACC_OWNER_ONLINE)

        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()
        mux.deviceConnected(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        mux.deviceConnected(PolarDeviceInfo(h10Id, h10Mac, -48, "Polar H10 $h10Id", true))
        assertTrue(PolarOfflineGattCoordinator.isDeviceConnected(verityMac))
        assertTrue(PolarOfflineGattCoordinator.isDeviceConnected(h10Mac))

        // 1. In-session watchdog reconnect (clearRegistrations = false) disconnects both aliases but keeps registrations intact
        PolarOfflineGattCoordinator.forceDisconnectDevice(context, verityMac, verityId, clearRegistrations = false)
        verify(exactly = 1) { sharedApi.disconnectFromDevice(verityMac) }
        verify(exactly = 1) { sharedApi.disconnectFromDevice(verityId) }
        verify(exactly = 0) { sharedApi.disconnectFromDevice(h10Mac) }
        verify(exactly = 0) { sharedApi.disconnectFromDevice(h10Id) }
        verify(exactly = 0) { sharedApi.shutDown() }
        assertFalse(PolarOfflineGattCoordinator.isDeviceConnected(verityId))
        assertNotNull(PolarOfflineGattCoordinator.getActiveSession(verityId))
        assertTrue(PolarOfflineGattCoordinator.isDeviceConnected(h10Id))

        // Reconnect callback still delivers to verityCallback when clearRegistrations was false
        mux.deviceConnected(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        verify(exactly = 2) { verityCallback.deviceConnected(any()) }

        // 2. disconnectUnpairedSessions releases only unpaired Verity Sense while preserving paired H10
        PolarOfflineGattCoordinator.disconnectUnpairedSessions(context) { setOf(h10Mac) }
        verify(exactly = 2) { sharedApi.disconnectFromDevice(verityMac) }
        verify(exactly = 2) { sharedApi.disconnectFromDevice(verityId) }
        verify(exactly = 0) { sharedApi.disconnectFromDevice(h10Mac) }
        verify(exactly = 0) { sharedApi.disconnectFromDevice(h10Id) }
        verify(exactly = 0) { sharedApi.shutDown() }
        assertFalse(PolarOfflineGattCoordinator.isDeviceConnected(verityId))
        assertNull(PolarOfflineGattCoordinator.getActiveSession(verityId))
        assertNull(PolarOfflineGattCoordinator.getAccChannelOwner(verityId))
        assertTrue(PolarOfflineGattCoordinator.isDeviceConnected(h10Id))
        assertNotNull(PolarOfflineGattCoordinator.getActiveSession(h10Id))
        assertEquals(PolarOfflineGattCoordinator.ACC_OWNER_ONLINE, PolarOfflineGattCoordinator.getAccChannelOwner(h10Id))
    }

    // REQ-SET-120.1 / TST-SET-122.1
    @Test
    fun testWithGattYieldAndPolarApi_pausesAndResumesTargetAliasesWithoutHarmingSibling_TST_SET_122_1() = runBlocking {
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }

        val verityMac = "24:AC:AC:24:82:56"
        val verityId = "24825632"
        val h10Mac = "AA:BB:CC:DD:EE:01"
        val h10Id = "1C3B2421"
        PolarOfflineGattCoordinator.registerDeviceAliases(verityId, verityMac)
        PolarOfflineGattCoordinator.registerDeviceAliases(h10Id, h10Mac)

        var verityPauseCount = 0
        var verityResumeCount = 0
        var h10PauseCount = 0
        var h10ResumeCount = 0
        var yieldActiveInsideCallback = false

        val verityListener = object : PolarOfflineGattCoordinator.OfflineYieldListener {
            override fun matchesDevice(polarId: String?, macAddress: String?): Boolean =
                PolarOfflineGattCoordinator.matchesDeviceIdentifier(verityId, polarId) ||
                    PolarOfflineGattCoordinator.matchesDeviceIdentifier(verityMac, macAddress)

            override fun onOfflineYieldStarted() {
                verityPauseCount++
                // Re-entering isOfflineGattYieldActive() inside callback proves callback executes outside offlineYieldLock
                yieldActiveInsideCallback = PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityMac)
            }

            override fun onOfflineYieldFinished() {
                verityResumeCount++
            }
        }
        val h10Listener = object : PolarOfflineGattCoordinator.OfflineYieldListener {
            override fun matchesDevice(polarId: String?, macAddress: String?): Boolean =
                PolarOfflineGattCoordinator.matchesDeviceIdentifier(h10Id, polarId) ||
                    PolarOfflineGattCoordinator.matchesDeviceIdentifier(h10Mac, macAddress)

            override fun onOfflineYieldStarted() {
                h10PauseCount++
            }

            override fun onOfflineYieldFinished() {
                h10ResumeCount++
            }
        }

        PolarOfflineGattCoordinator.registerOfflineYieldListener(verityListener)
        PolarOfflineGattCoordinator.registerOfflineYieldListener(h10Listener)

        val result = PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
            context = context,
            macAddress = verityMac,
            polarId = verityId,
            features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
            timeoutMs = 5_000L
        ) {
            assertTrue(PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityId))
            assertTrue(PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityMac))
            assertFalse(PolarOfflineGattCoordinator.isOfflineGattYieldActive(h10Id))
            assertFalse(PolarOfflineGattCoordinator.isOfflineGattYieldActive(h10Mac))
            assertEquals(1, verityPauseCount)
            assertEquals(0, verityResumeCount)
            "ok"
        }

        assertEquals("ok", result)
        assertTrue(yieldActiveInsideCallback)
        assertFalse(PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityId))
        assertFalse(PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityMac))
        assertEquals(1, verityPauseCount)
        assertEquals(1, verityResumeCount)
        assertEquals(0, h10PauseCount)
        assertEquals(0, h10ResumeCount)
    }

    // REQ-SET-120.1 / TST-SET-122.1
    @Test
    fun testWithGattYieldAndPolarApi_nestedLeasesAndExceptionOrCancellationReleaseCleanly_TST_SET_122_1() = runBlocking {
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }

        val verityMac = "24:AC:AC:24:82:56"
        val verityId = "24825632"
        var pauseCount = 0
        var resumeCount = 0

        val listener = object : PolarOfflineGattCoordinator.OfflineYieldListener {
            override fun matchesDevice(polarId: String?, macAddress: String?): Boolean =
                PolarOfflineGattCoordinator.matchesDeviceIdentifier(verityId, polarId) ||
                    PolarOfflineGattCoordinator.matchesDeviceIdentifier(verityMac, macAddress)

            override fun onOfflineYieldStarted() {
                pauseCount++
            }

            override fun onOfflineYieldFinished() {
                resumeCount++
            }
        }
        PolarOfflineGattCoordinator.registerOfflineYieldListener(listener)

        // Nested leases: pause fires strictly on 0->1, resume fires strictly on final 1->0 (even when inner throws)
        try {
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = verityMac,
                polarId = verityId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 5_000L
            ) {
                assertEquals(1, pauseCount)
                assertEquals(0, resumeCount)
                try {
                    PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                        context = context,
                        macAddress = verityMac,
                        polarId = verityId,
                        features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                        timeoutMs = 5_000L
                    ) {
                        assertEquals("Nested lease must NOT double-pause", 1, pauseCount)
                        assertEquals(0, resumeCount)
                        throw IllegalStateException("Simulated PFTP failure")
                    }
                } catch (_: IllegalStateException) {
                    // Expected inner exception
                }
                assertTrue("Outer lease must remain active after inner failure", PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityId))
                assertEquals("Nested release 2->1 must NOT prematurely resume", 0, resumeCount)
                throw kotlinx.coroutines.CancellationException("Simulated coroutine cancel")
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Expected cancellation propagation
        }

        assertFalse("Final 1->0 release must clear yield even after CancellationException", PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityId))
        assertEquals(1, pauseCount)
        assertEquals(1, resumeCount)
    }

    // REQ-SET-120.1 / TST-SET-122.1
    @Test
    fun testWithGattYieldAndPolarApi_aliasExpansionAcrossOverlappingLeases_transitionsPausedSetOnce_TST_SET_122_1() = runBlocking {
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }
        PolarOfflineGattCoordinator.deviceManagerProvider = { null }

        val verityMac = "24:AC:AC:24:82:77"
        val verityId = "24825677"
        var pauseCount = 0
        var resumeCount = 0

        // Listener initially knows only MAC (no alias registered yet)
        val macOnlyListener = object : PolarOfflineGattCoordinator.OfflineYieldListener {
            override fun matchesDevice(polarId: String?, macAddress: String?): Boolean =
                PolarOfflineGattCoordinator.matchesDeviceIdentifier(verityMac, polarId) ||
                    PolarOfflineGattCoordinator.matchesDeviceIdentifier(verityMac, macAddress)

            override fun onOfflineYieldStarted() {
                pauseCount++
            }

            override fun onOfflineYieldFinished() {
                resumeCount++
            }
        }
        PolarOfflineGattCoordinator.registerOfflineYieldListener(macOnlyListener)

        // Lease 1 starts with polarId only (not yet aliased to MAC) -> listener not yet paused
        PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
            context = context,
            macAddress = null,
            polarId = verityId,
            features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
            timeoutMs = 5_000L
        ) {
            assertEquals(0, pauseCount)
            assertEquals(0, resumeCount)

            // Overlapping Lease 2 introduces polarId <-> MAC alias -> listener transitions into paused set ONCE
            PolarOfflineGattCoordinator.withGattYieldAndPolarApi(
                context = context,
                macAddress = verityMac,
                polarId = verityId,
                features = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
                timeoutMs = 5_000L
            ) {
                assertEquals("Alias expansion must pause listener exactly once", 1, pauseCount)
                assertEquals(0, resumeCount)
            }

            // Lease 2 released, but Lease 1 still matches via newly registered alias -> MUST NOT resume yet
            assertTrue(PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityMac))
            assertEquals(1, pauseCount)
            assertEquals("Listener must stay paused while Lease 1 still matches via alias", 0, resumeCount)
        }

        // Lease 1 released -> final transition out of paused set resumes listener ONCE
        assertFalse(PolarOfflineGattCoordinator.isOfflineGattYieldActive(verityMac))
        assertEquals(1, pauseCount)
        assertEquals(1, resumeCount)
    }

    // REQ-STB-023.2 / TST-STB-023.2
    @Test
    fun testAcquirePolarApi_disablesDefaultBleLoggerOnSingletonCreation_TST_STB_023_2() {
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }

        // Install a synthetic BleLoggerInterface to verify default bleLoggerDisabler actually clears it
        var hexLogCalls = 0
        BleLogger.setLoggerInterface(object : BleLogger.BleLoggerInterface {
            override fun d(tag: String, msg: String) { hexLogCalls++ }
            override fun e(tag: String, msg: String) {}
            override fun w(tag: String, msg: String) {}
            override fun i(tag: String, msg: String) {}
            override fun d_hex(tag: String, msg: String, data: ByteArray) { hexLogCalls++ }
        })
        BleLogger.d_hex("BlePMDClient", "pmd data:", byteArrayOf(0x01, 0x02))
        assertEquals("Pre-condition: installed logger must receive d_hex before acquirePolarApi", 1, hexLogCalls)

        // First lease creates singleton -> invokes bleLoggerDisabler -> BleLogger.setLoggerInterface(null)
        val lease1 = PolarOfflineGattCoordinator.acquirePolarApi(context)
        assertSame(sharedApi, lease1)

        // Subsequent d_hex calls must short-circuit with zero callbacks (zero hex serialization churn)
        BleLogger.d_hex("BlePMDClient", "pmd data:", byteArrayOf(0x03, 0x04, 0x05))
        assertEquals("BleLogger.d_hex must not invoke logger after acquirePolarApi clears it", 1, hexLogCalls)

        // Second lease reuses singleton without re-invoking disabler
        var customDisablerCalls = 0
        PolarOfflineGattCoordinator.bleLoggerDisabler = { customDisablerCalls++ }
        val lease2 = PolarOfflineGattCoordinator.acquirePolarApi(context)
        assertSame(sharedApi, lease2)
        assertEquals("Reused singleton lease must not re-invoke bleLoggerDisabler", 0, customDisablerCalls)

        // Releasing both leases tears down singleton cleanly; next creation invokes disabler once even if it throws
        PolarOfflineGattCoordinator.releasePolarApi(lease1)
        PolarOfflineGattCoordinator.releasePolarApi(lease2)
        PolarOfflineGattCoordinator.bleLoggerDisabler = {
            customDisablerCalls++
            throw IllegalStateException("Simulated logger error")
        }
        val lease3 = PolarOfflineGattCoordinator.acquirePolarApi(context)
        assertSame(sharedApi, lease3)
        assertEquals("Fresh singleton creation must invoke bleLoggerDisabler once", 1, customDisablerCalls)
        PolarOfflineGattCoordinator.releasePolarApi(lease3)
    }

    // REQ-SET-129.2 / TST-SET-131.2
    @Test
    fun testConnectWithinTimeout_recoversFromMidWaitDisconnectAndIsolatesSiblingDisconnect_TST_SET_131_2() = runBlocking {
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }
        PolarOfflineGattCoordinator.connectRetryBackoffMs = 20L

        val verityId = "24825632"
        val verityMac = "24:AC:AC:24:82:56"
        val h10Id = "1C3B2421"
        val h10Mac = "AA:BB:CC:DD:EE:01"
        PolarOfflineGattCoordinator.registerDeviceAliases(verityId, verityMac)
        PolarOfflineGattCoordinator.registerDeviceAliases(h10Id, h10Mac)

        val api = PolarOfflineGattCoordinator.acquirePolarApi(context)
        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()

        assertFalse("Blank polarId must return false immediately", PolarOfflineGattCoordinator.connectWithinTimeout(api, "  ", 500L))

        val connectCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val firstWaitEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val secondConnectIssued = kotlinx.coroutines.CompletableDeferred<Unit>()
        every { sharedApi.connectToDevice(verityId) } answers {
            if (connectCalls.incrementAndGet() == 2) secondConnectIssued.complete(Unit)
        }
        coEvery { sharedApi.waitForConnection(verityId) } coAnswers {
            firstWaitEntered.complete(Unit)
            // Simulate Polar SDK waitForConnection hanging silently after a disconnect
            kotlinx.coroutines.awaitCancellation()
        }

        val connectJob = async(kotlinx.coroutines.Dispatchers.Default) {
            PolarOfflineGattCoordinator.connectWithinTimeout(api, verityId, 5_000L)
        }

        firstWaitEntered.await()
        assertEquals(1, connectCalls.get())

        // Sibling H10 disconnect must NOT cancel or retry Verity Sense's connectWithinTimeout
        mux.deviceDisconnected(PolarDeviceInfo(h10Id, h10Mac, -50, "Polar H10 $h10Id", true))
        kotlinx.coroutines.delay(50L)
        assertEquals("Sibling disconnect must not trigger Verity Sense retry", 1, connectCalls.get())
        assertFalse(connectJob.isCompleted)

        // Target Verity Sense mid-wait disconnect (10:57:36 teardown) must cancel wedged wait and retry immediately
        mux.deviceDisconnected(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        kotlinx.coroutines.withTimeout(2_000L) { secondConnectIssued.await() }
        assertEquals("Mid-wait disconnect must re-issue connectToDevice", 2, connectCalls.get())

        // Subsequent deviceConnected resolves Attempt 1 immediately
        mux.deviceConnected(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        assertTrue(kotlinx.coroutines.withTimeout(2_000L) { connectJob.await() })
        PolarOfflineGattCoordinator.releasePolarApi(api)
    }

    // REQ-SET-129.2 / TST-SET-131.2
    @Test
    fun testConnectWithinTimeout_reusesConnectedAndInFlightConnectingSessionsAndClearsStateOnRelease_TST_SET_131_2() = runBlocking {
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }
        PolarOfflineGattCoordinator.connectRetryBackoffMs = 10L

        val verityId = "24825632"
        val verityMac = "24:AC:AC:24:82:56"
        PolarOfflineGattCoordinator.registerDeviceAliases(verityId, verityMac)
        val api = PolarOfflineGattCoordinator.acquirePolarApi(context)
        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()

        // 1. In-flight deviceConnecting: awaits without issuing duplicate connectToDevice()
        mux.deviceConnecting(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        assertTrue(PolarOfflineGattCoordinator.isDeviceConnecting(verityId))
        assertTrue(PolarOfflineGattCoordinator.isDeviceConnecting(verityMac))
        coEvery { sharedApi.waitForConnection(verityId) } coAnswers { kotlinx.coroutines.awaitCancellation() }

        val inFlightJob = async(kotlinx.coroutines.Dispatchers.Default) {
            PolarOfflineGattCoordinator.connectWithinTimeout(api, verityId, 5_000L)
        }
        kotlinx.coroutines.delay(40L)
        verify(exactly = 0) { sharedApi.connectToDevice(verityId) }
        mux.deviceConnected(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        assertTrue(kotlinx.coroutines.withTimeout(2_000L) { inFlightJob.await() })
        assertFalse("deviceConnected must clear connectingDeviceIds across aliases", PolarOfflineGattCoordinator.isDeviceConnecting(verityMac))
        assertTrue(PolarOfflineGattCoordinator.isDeviceConnected(verityId))

        // 2. Already connected: returns true immediately with 0 connectToDevice() calls
        assertTrue(PolarOfflineGattCoordinator.connectWithinTimeout(api, verityId, 5_000L))
        verify(exactly = 0) { sharedApi.connectToDevice(verityId) }

        // 3. CancellationException propagates cleanly & releasePolarApi 1->0 clears both sets
        mux.deviceDisconnected(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        mux.deviceConnecting(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        assertTrue(PolarOfflineGattCoordinator.isDeviceConnecting(verityId))
        PolarOfflineGattCoordinator.clearConnectingState(verityId)
        assertFalse("clearConnectingState must clear full alias closure", PolarOfflineGattCoordinator.isDeviceConnecting(verityMac))

        mux.deviceConnecting(PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true))
        PolarOfflineGattCoordinator.releasePolarApi(api)
        assertFalse("releasePolarApi 1->0 must clear connectingDeviceIds", PolarOfflineGattCoordinator.isDeviceConnecting(verityId))
        assertFalse("releasePolarApi 1->0 must clear connectedDeviceIds", PolarOfflineGattCoordinator.isDeviceConnected(verityId))
    }

    // REQ-SET-129.3 / TST-SET-131.3
    @Test
    fun testAwaitFeaturesReady_uses20sDefaultAndResolvesMacHexAliases_TST_SET_131_3() = runBlocking {
        assertEquals(20_000L, PolarOfflineGattCoordinator.FEATURE_READY_TIMEOUT_MS)

        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }
        val verityId = "24825632"
        val verityMac = "24:AC:AC:24:82:56"
        PolarOfflineGattCoordinator.registerDeviceAliases(verityId, verityMac)

        every { sharedApi.isFeatureReady(verityId, any()) } returns false
        every {
            sharedApi.isFeatureReady(verityMac, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING)
        } returns true

        val api = PolarOfflineGattCoordinator.acquirePolarApi(context)
        val ready = PolarOfflineGattCoordinator.awaitFeaturesReady(
            api = api,
            polarId = verityId,
            requiredFeatures = setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING),
            timeoutMs = 500L
        )
        assertTrue("awaitFeaturesReady must resolve feature keyed by MAC alias", ready)
        PolarOfflineGattCoordinator.releasePolarApi(api)
    }

    // REQ-SET-129.2 / TST-SET-131.2 (Stage 6 Bounce 1/5 regression)
    @Test
    @Suppress("DEPRECATION")
    fun testConnectWithinTimeout_coalescesConnectedThenDisconnectedBurstBeforeSelectAndDuringBackoff_TST_SET_131_2() = runBlocking {
        val sharedApi = mockk<PolarBleApi>(relaxed = true)
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> sharedApi }
        PolarOfflineGattCoordinator.connectRetryBackoffMs = 80L

        val verityId = "24825632"
        val verityMac = "24:AC:AC:24:82:56"
        val verityInfo = PolarDeviceInfo(verityId, verityMac, -50, "Polar Sense $verityId", true)
        PolarOfflineGattCoordinator.registerDeviceAliases(verityId, verityMac)

        val api = PolarOfflineGattCoordinator.acquirePolarApi(context)
        val mux = PolarOfflineGattCoordinator.getMultiplexerCallbackForTesting()

        val connectCalls = java.util.concurrent.atomic.AtomicInteger(0)
        val firstWaitEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val secondConnectIssued = kotlinx.coroutines.CompletableDeferred<Unit>()
        val thirdConnectIssued = kotlinx.coroutines.CompletableDeferred<Unit>()

        every { sharedApi.connectToDevice(verityId) } answers {
            when (connectCalls.incrementAndGet()) {
                1 -> {
                    // Case B: Queue [CONNECTED, DISCONNECTED] synchronously before select inside awaitSingleConnectionAttempt
                    mux.deviceConnected(verityInfo)
                    mux.deviceDisconnected(verityInfo)
                }
                2 -> secondConnectIssued.complete(Unit)
                3 -> thirdConnectIssued.complete(Unit)
            }
        }
        coEvery { sharedApi.waitForConnection(verityId) } coAnswers {
            if (connectCalls.get() == 2) firstWaitEntered.complete(Unit)
            kotlinx.coroutines.awaitCancellation()
        }

        val connectJob = async(kotlinx.coroutines.Dispatchers.Default) {
            PolarOfflineGattCoordinator.connectWithinTimeout(api, verityId, 5_000L)
        }

        // Case B verification: [CONNECTED, DISCONNECTED] before select must NOT return true; must retry -> call #2
        kotlinx.coroutines.withTimeout(2_000L) { secondConnectIssued.await() }
        assertFalse("Coalesced [CONNECTED, DISCONNECTED] before select must not return true", connectJob.isCompleted)
        kotlinx.coroutines.withTimeout(2_000L) { firstWaitEntered.await() }

        // Case A verification: Disconnect enters 80ms backoff; burst [CONNECTED, DISCONNECTED] during backoff must NOT return true
        mux.deviceDisconnected(verityInfo)
        kotlinx.coroutines.delay(20L)
        mux.deviceConnected(verityInfo)
        mux.deviceDisconnected(verityInfo)

        // Must retry -> call #3 instead of falsely returning true after backoff
        kotlinx.coroutines.withTimeout(2_000L) { thirdConnectIssued.await() }
        assertEquals(3, connectCalls.get())
        assertFalse("Coalesced [CONNECTED, DISCONNECTED] during backoff must not return true", connectJob.isCompleted)

        // Genuine final CONNECTED resolves cleanly
        mux.deviceConnected(verityInfo)
        assertTrue(kotlinx.coroutines.withTimeout(2_000L) { connectJob.await() })
        PolarOfflineGattCoordinator.releasePolarApi(api)
    }
}
