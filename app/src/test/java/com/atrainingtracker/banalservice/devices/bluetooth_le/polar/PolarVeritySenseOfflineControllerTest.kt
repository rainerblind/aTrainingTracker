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

@file:Suppress("PackageNaming")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Unit tests for [PolarVeritySenseOfflineController], [PolarOfflineGattCoordinator], and
 * [PolarOfflineCapabilityStore] (`ATT-7923`, `REQ-SET-108`, `TST-SET-110`).
 */
class PolarVeritySenseOfflineControllerTest {

    private lateinit var context: Context
    private lateinit var mockApi: PolarBleApi
    private lateinit var mockDb: DevicesDatabaseManager
    private lateinit var mockDeviceManager: DeviceManager
    private lateinit var mockBtleDevice: MyBTLEDevice

    private val verityMac = "AA:BB:CC:11:22:33"
    private val verityPolarId = "A1B2C3D4"

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context

        mockApi = mockk(relaxed = true)
        mockDb = mockk(relaxed = true)
        mockDeviceManager = mockk(relaxed = true)
        mockBtleDevice = mockk(relaxed = true)

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDb

        mockkStatic(BANALService::class)
        every { BANALService.getDeviceManager() } returns mockDeviceManager
        every { mockDeviceManager.getBtleDeviceByAddress(verityMac) } returns mockBtleDevice
        every { mockBtleDevice.pauseGattOperations() } just Runs
        every { mockBtleDevice.resumeGattOperations() } just Runs

        PolarOfflineCapabilityStore.resetForTesting()
        PolarVeritySenseOfflineController.resetForTesting()
        PolarVeritySenseOfflineController.dispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        PolarVeritySenseOfflineController.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        unmockkAll()
    }

    @Test
    fun testApiLevelBelow33_doesNotArmOrInvokeSdk() {
        PolarVeritySenseOfflineController.sdkIntOverride = 32
        PolarVeritySenseOfflineController.apiOverride = mockApi

        PolarVeritySenseOfflineController.onWorkoutStarted(context)

        assertFalse(PolarVeritySenseOfflineController.isRecordingArmed())
        assertEquals(
            PolarVeritySenseOfflineController.ArmingOutcome.SKIPPED_UNSUPPORTED_API,
            PolarVeritySenseOfflineController.getLastArmingOutcome()
        )
        verify(exactly = 0) { mockApi.connectToDevice(any()) }
    }

    @Test
    fun testArmForActiveWorkout_detectsVeritySenseAndIgnoresPolarH10() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        val h10Item = DevicesDatabaseManager.RemoteDeviceUIDataCacheItem(
            1L, "Polar H10 1C3B2421", DeviceType.HRM, Protocol.BLUETOOTH_LE
        )
        val verityItem = DevicesDatabaseManager.RemoteDeviceUIDataCacheItem(
            2L, "Polar Sense $verityPolarId", DeviceType.HRM, Protocol.BLUETOOTH_LE
        )
        every { mockDb.allDevicesForCache } returns listOf(h10Item, verityItem)
        every { mockDb.isPolarH10OfflineRecordingEnabled(1L) } returns true
        every { mockDb.isPolarH10OfflineRecordingEnabled(2L) } returns true
        every { mockDb.getBluetoothMACAddress(1L) } returns "11:22:33:44:55:66"
        every { mockDb.getBluetoothMACAddress(2L) } returns verityMac

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.setLocalTime(verityPolarId, any()) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns emptyList()
        coEvery {
            mockApi.startOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR, null, null)
        } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val armed = PolarVeritySenseOfflineController.armForActiveWorkout(context)

        assertTrue(armed)
        assertTrue(PolarVeritySenseOfflineController.isRecordingArmed())
        assertEquals(verityPolarId, PolarVeritySenseOfflineController.getArmedDeviceId())
        verify(exactly = 0) { mockApi.connectToDevice("1C3B2421") }
        coVerify(exactly = 1) {
            mockApi.startOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR, null, null)
        }
        verify(exactly = 1) { mockBtleDevice.pauseGattOperations() }
        verify(exactly = 1) { mockBtleDevice.resumeGattOperations() }
    }

    @Test
    fun testArmForActiveWorkout_detectsRenamedDeviceViaCapabilityStore() {
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(context, verityMac)
        assertTrue(
            PolarOfflineGattCoordinator.isPolarVeritySenseDevice(
                context = context,
                name = "Swim Arm Band",
                manufacturer = "Polar Electro",
                macAddress = verityMac
            )
        )
        assertFalse(
            PolarOfflineGattCoordinator.isPolarVeritySenseDevice(
                context = context,
                name = "Polar H10 1C3B2421",
                manufacturer = "Polar Electro",
                macAddress = verityMac
            )
        )
    }

    @Test
    fun testExecuteArming_stopsPreExistingHrBeforeStartingNewHrRecording() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.setLocalTime(verityPolarId, any()) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns listOf(
            PolarBleApi.PolarDeviceDataType.HR
        )
        coEvery {
            mockApi.stopOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR)
        } just Runs
        coEvery {
            mockApi.startOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR, null, null)
        } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val armed = PolarVeritySenseOfflineController.executeArming(context, verityMac, verityPolarId)

        assertTrue(armed)
        coVerifyOrder {
            mockBtleDevice.pauseGattOperations()
            mockApi.connectToDevice(verityPolarId)
            mockApi.waitForConnection(verityPolarId)
            mockApi.setLocalTime(verityPolarId, any())
            mockApi.getOfflineRecordingStatus(verityPolarId)
            mockApi.stopOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR)
            mockApi.startOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR, null, null)
            mockApi.disconnectFromDevice(verityPolarId)
            mockBtleDevice.resumeGattOperations()
        }
    }

    @Test
    fun testExecuteArming_awaitsFeatureCallbacksBeforeSetLocalTime() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        val callbackSlot = slot<PolarBleApiCallbackProvider>()
        every { mockApi.isFeatureReady(verityPolarId, any()) } returns false
        every { mockApi.setApiCallback(capture(callbackSlot)) } answers {
            callbackSlot.captured.bleSdkFeatureReady(
                verityPolarId,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
            )
            callbackSlot.captured.bleSdkFeatureReady(
                verityPolarId,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
            )
        }
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.setLocalTime(verityPolarId, any()) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns emptyList()
        coEvery {
            mockApi.startOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR, null, null)
        } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val armed = PolarVeritySenseOfflineController.executeArming(context, verityMac, verityPolarId)

        assertTrue(armed)
        verify { mockApi.setApiCallback(match { it !== callbackSlot.captured }) }
    }

    @Test
    fun testMidWorkoutFringeFlapInvariant_neverStopsOrQueriesWhileAlreadyArmed() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        PolarVeritySenseOfflineController.setArmedForTesting(true)

        // Repeated onWorkoutStarted triggers (e.g., mid-workout 0x180D disconnect/reconnect flaps)
        PolarVeritySenseOfflineController.onWorkoutStarted(context)
        val rearmResult = PolarVeritySenseOfflineController.armForActiveWorkout(context)

        assertTrue(rearmResult)
        assertTrue(PolarVeritySenseOfflineController.isRecordingArmed())
        coVerify(exactly = 0) { mockApi.getOfflineRecordingStatus(any()) }
        coVerify(exactly = 0) { mockApi.stopOfflineRecording(any(), any()) }
        coVerify(exactly = 0) { mockApi.startOfflineRecording(any(), any(), any(), any()) }
    }

    @Test
    fun testExecuteArming_systemBusy_nonBlockingResetsArmedAndResumesGatt() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.setLocalTime(verityPolarId, any()) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns emptyList()
        val busyError = mockk<BlePsFtpUtils.PftpResponseError>(relaxed = true)
        every { busyError.errorName } returns "SYSTEM_BUSY"
        every { busyError.message } returns null
        coEvery {
            mockApi.startOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR, null, null)
        } throws busyError
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val armed = PolarVeritySenseOfflineController.executeArming(context, verityMac, verityPolarId)

        assertFalse(armed)
        assertFalse(PolarVeritySenseOfflineController.isRecordingArmed())
        assertEquals(
            PolarVeritySenseOfflineController.ArmingOutcome.ERROR_SYSTEM_BUSY,
            PolarVeritySenseOfflineController.getLastArmingOutcome()
        )
        verify(exactly = 1) { mockBtleDevice.resumeGattOperations() }
    }

    @Test
    fun testStopAndFetchOfflineHr_andRemoveOfflineRecord() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        val now = LocalDateTime.of(2026, 11, 15, 10, 0, 0)
        val startMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val entry = PolarOfflineRecordingEntry(
            path = "/U/0/20261115/R/100000/HR.REC",
            size = 1024L,
            date = now,
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        val hrSample = PolarHrData.PolarHrSample(
            hr = 148,
            correctedHr = 148,
            ppgQuality = 100,
            rrs = emptyList(),
            rrsMs = emptyList(),
            rrAvailable = false,
            contactStatus = true,
            contactStatusSupported = true
        )
        val hrData = PolarOfflineRecordingData.HrOfflineRecording(
            data = PolarHrData(listOf(hrSample)),
            startTime = now
        )

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns listOf(
            PolarBleApi.PolarDeviceDataType.HR
        )
        coEvery {
            mockApi.stopOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR)
        } just Runs
        every { mockApi.listOfflineRecordings(verityPolarId) } returns flowOf(entry)
        coEvery { mockApi.getOfflineRecord(verityPolarId, entry, null) } returns hrData
        coEvery { mockApi.removeOfflineRecord(verityPolarId, entry) } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val fetched = PolarVeritySenseOfflineController.stopAndFetchOfflineHr(
            context = context,
            macAddress = verityMac,
            polarId = verityPolarId,
            windowStartMs = startMs - 10_000L,
            windowEndMs = startMs + 60_000L
        )

        assertTrue(fetched.stoppedSuccessfully)
        assertEquals(entry, fetched.entry)
        assertNotNull(fetched.data)
        assertEquals(148, fetched.data?.data?.samples?.single()?.hr)

        val removed = PolarVeritySenseOfflineController.removeOfflineRecord(
            context = context,
            macAddress = verityMac,
            polarId = verityPolarId,
            entry = entry
        )
        assertTrue(removed)
        coVerify(exactly = 1) { mockApi.removeOfflineRecord(verityPolarId, entry) }
        verify(atLeast = 2) { mockBtleDevice.resumeGattOperations() }
    }

    @Test
    fun testWorkoutStopped_clearsArmedState() {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.setArmedForTesting(true)

        PolarVeritySenseOfflineController.onWorkoutStopped(context)

        assertFalse(PolarVeritySenseOfflineController.isRecordingArmed())
    }

    private fun mockReadyFeatures(polarId: String) {
        every {
            mockApi.isFeatureReady(
                polarId,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING
            )
        } returns true
        every {
            mockApi.isFeatureReady(
                polarId,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP
            )
        } returns true
    }
}
