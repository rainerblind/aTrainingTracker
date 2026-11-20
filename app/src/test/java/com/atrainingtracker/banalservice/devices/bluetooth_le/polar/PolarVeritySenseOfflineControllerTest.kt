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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.exporter.ExportManager
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.syncPendingPolarWorkout
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarOfflineRecordingData
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import com.polar.sdk.api.model.trainingsession.PolarExercise
import com.polar.sdk.api.model.trainingsession.PolarTrainingSession
import com.polar.sdk.api.model.trainingsession.PolarTrainingSessionReference
import fi.polar.remote.representation.protobuf.ExerciseSamples.PbExerciseSamples
import fi.polar.remote.representation.protobuf.Structures.PbSportIdentifier
import fi.polar.remote.representation.protobuf.Training.PbExerciseBase
import fi.polar.remote.representation.protobuf.TrainingSession.PbTrainingSession
import fi.polar.remote.representation.protobuf.Types
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
 * [PolarOfflineCapabilityStore] (`ATT-7923`, `REQ-SET-108`, `TST-SET-110`,
 * `ATT-7924`, `REQ-SET-109`, `TST-SET-111`).
 */
@Suppress("LargeClass")
// Linked test specs: TST-SET-112
class PolarVeritySenseOfflineControllerTest {

    private lateinit var context: Context
    private lateinit var mockApi: PolarBleApi
    private lateinit var mockDb: DevicesDatabaseManager
    private lateinit var mockDeviceManager: DeviceManager
    private lateinit var mockBtleDevice: MyBTLEDevice
    private lateinit var mockSamplesDb: WorkoutSamplesDatabaseManager
    private lateinit var mockSummariesDb: WorkoutSummariesDatabaseManager
    private lateinit var mockRrRepo: WorkoutRrRepository
    private lateinit var mockWorkoutRepo: WorkoutRepository
    private lateinit var mockLapsDb: LapsDatabaseManager
    private lateinit var mockSportTypesRepo: SportTypesRepository

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

        mockSamplesDb = mockk(relaxed = true)
        mockSummariesDb = mockk(relaxed = true)
        mockRrRepo = mockk(relaxed = true)
        mockWorkoutRepo = mockk(relaxed = true)
        mockLapsDb = mockk(relaxed = true)
        mockSportTypesRepo = mockk(relaxed = true)

        WorkoutSamplesDatabaseManager.resetForTesting(mockSamplesDb)

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb
        every { mockSummariesDb.getString(any<Long>(), any()) } returns BSportType.UNKNOWN.name

        WorkoutRrRepository.resetForTesting(mockRrRepo)

        mockkObject(HrvPostWorkoutRecomputer)
        coEvery { HrvPostWorkoutRecomputer.recomputeWorkout(any(), any()) } returns false

        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        every { mockWorkoutRepo.refreshWorkout(any()) } just Runs

        mockkStatic(LapsDatabaseManager::class)
        every { LapsDatabaseManager.getInstance(any<Context>()) } returns mockLapsDb

        SportTypesRepository.resetForTesting(mockSportTypesRepo)
        every { mockSportTypesRepo.ensureSwimSportTypeExists() } returns 101L

        PolarVeritySenseOfflineController.poolLengthOverrideMeters = 25.0

        coEvery { mockApi.isFtuDone(any()) } returns true
        every { mockApi.getTrainingSessionReferences(any()) } returns flowOf()
        every { mockApi.getTrainingSessionReferences(any(), null, null) } returns flowOf()
        coEvery { mockApi.deleteTrainingSession(any(), any()) } just Runs

        PolarOfflineGattCoordinator.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        PolarVeritySenseOfflineController.resetForTesting()
        PolarVeritySenseOfflineController.dispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        PolarOfflineGattCoordinator.resetForTesting()
        PolarVeritySenseOfflineController.resetForTesting()
        PolarOfflineCapabilityStore.resetForTesting()
        WorkoutRrRepository.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        WorkoutRepository.resetForTesting(null)
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
    fun testWorkoutStopped_preservesArmedStateForEndWorkoutBackfill() {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        PolarVeritySenseOfflineController.onWorkoutStopped(context)

        assertTrue(PolarVeritySenseOfflineController.isRecordingArmed())
    }

    @Test
    fun testStopAndBackfill_zeroGapFastPath_skipsDownloadAndRemovesMatchedEntry() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        val now = LocalDateTime.now()
        val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val matchingEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261115/R/120000/HR.BPB",
            size = 512L,
            date = now,
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        val unrelatedEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261114/R/080000/HR.BPB",
            size = 900L,
            date = now.minusDays(1),
            type = PolarBleApi.PolarDeviceDataType.HR
        )

        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_120000") } returns false
        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns listOf(
            PolarBleApi.PolarDeviceDataType.HR
        )
        coEvery {
            mockApi.stopOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR)
        } just Runs
        every { mockApi.listOfflineRecordings(verityPolarId) } returns flowOf(unrelatedEntry, matchingEntry)
        coEvery { mockApi.removeOfflineRecord(verityPolarId, matchingEntry) } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val result = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = 77L,
            baseFileName = "2026-11-15_120000",
            startTimeMs = nowMs - 5_000L
        )

        assertTrue(result)
        assertFalse(PolarVeritySenseOfflineController.isRecordingArmed())
        coVerify(exactly = 0) { mockApi.getOfflineRecord(any(), any(), any()) }
        coVerify(exactly = 1) {
            mockApi.stopOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR)
        }
        coVerify(exactly = 1) { mockApi.removeOfflineRecord(verityPolarId, matchingEntry) }
        coVerify(exactly = 0) { mockApi.removeOfflineRecord(verityPolarId, unrelatedEntry) }
        coVerify(exactly = 0) { mockRrRepo.insertIntervals(any()) }
        verify(atLeast = 1) { mockBtleDevice.resumeGattOperations() }
    }

    @Test
    @Suppress("LongMethod")
    fun testStopAndBackfill_gapsExist_aligns1HzHrUpdatesExtremaAndProtectsHrv() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        val now = LocalDateTime.now().minusMinutes(5)
        val anchorMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val matchingEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261115/R/120500/HR.BPB",
            size = 1024L,
            date = now,
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        val unrelatedEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261113/R/070000/HR.BPB",
            size = 2048L,
            date = now.minusDays(2),
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        val samples = listOf(
            createHrSample(hr = 132),
            createHrSample(hr = 0, correctedHr = 144),
            createHrSample(hr = 156)
        )
        val hrRecording = PolarOfflineRecordingData.HrOfflineRecording(
            data = PolarHrData(samples),
            startTime = now
        )
        val pairsSlot = slot<List<android.util.Pair<Long, Int>>>()

        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_120500") } returns true
        every {
            mockSamplesDb.patchMissingHeartRateSamples("2026-11-15_120500", anchorMs, capture(pairsSlot))
        } returns 2
        every { mockSamplesDb.calcHeartRateStats("2026-11-15_120500") } returns
            WorkoutSamplesDatabaseManager.HrStats(132.0, 156.0, 144.0, 3)

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns listOf(
            PolarBleApi.PolarDeviceDataType.HR
        )
        coEvery {
            mockApi.stopOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR)
        } just Runs
        every { mockApi.listOfflineRecordings(verityPolarId) } returns flowOf(unrelatedEntry, matchingEntry)
        coEvery { mockApi.getOfflineRecord(verityPolarId, matchingEntry, null) } returns hrRecording
        coEvery { mockApi.removeOfflineRecord(verityPolarId, matchingEntry) } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val result = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = 88L,
            baseFileName = "2026-11-15_120500",
            startTimeMs = anchorMs - 2_000L
        )

        assertTrue(result)
        assertFalse(PolarVeritySenseOfflineController.isRecordingArmed())
        assertEquals(3, pairsSlot.captured.size)
        verify(exactly = 1) { mockWorkoutRepo.addAccumulatedSensorTypes(88L, setOf(SensorType.HR)) }
        val aligned = PolarVeritySenseOfflineController.alignHrSamples(hrRecording, anchorMs)
        assertEquals(
            listOf(
                anchorMs to 132,
                (anchorMs + 1_000L) to 144,
                (anchorMs + 2_000L) to 156
            ),
            aligned
        )

        verify(exactly = 1) {
            mockSummariesDb.updateExtremaValue(88L, SensorType.HR, ExtremaType.MIN, 132.0, null)
            mockSummariesDb.updateExtremaValue(88L, SensorType.HR, ExtremaType.MAX, 156.0, null)
            mockSummariesDb.updateExtremaValue(88L, SensorType.HR, ExtremaType.AVG, 144.0, null, 3)
            mockWorkoutRepo.refreshWorkout(88L)
        }
        coVerify(exactly = 0) { mockRrRepo.insertIntervals(any()) }
        coVerify(exactly = 0) { HrvPostWorkoutRecomputer.recomputeWorkout(any(), any()) }
        coVerify(exactly = 1) { mockApi.removeOfflineRecord(verityPolarId, matchingEntry) }
        coVerify(exactly = 0) { mockApi.removeOfflineRecord(verityPolarId, unrelatedEntry) }
    }

    @Test
    fun testStopAndBackfill_sqliteFailure_doesNotDeleteFlashAndPersistsPendingSync() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        val now = LocalDateTime.now().minusMinutes(3)
        val anchorMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val matchingEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261115/R/120800/HR.BPB",
            size = 640L,
            date = now,
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        val hrRecording = PolarOfflineRecordingData.HrOfflineRecording(
            data = PolarHrData(listOf(createHrSample(hr = 140))),
            startTime = now
        )

        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_120800") } returns true
        every { mockSamplesDb.patchMissingHeartRateSamples(any(), any(), any()) } throws
            RuntimeException("SQLite write error")

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        every { mockApi.listOfflineRecordings(verityPolarId) } returns flowOf(matchingEntry)
        coEvery { mockApi.getOfflineRecord(verityPolarId, matchingEntry, null) } returns hrRecording

        val result = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = 89L,
            baseFileName = "2026-11-15_120800",
            startTimeMs = anchorMs
        )

        assertFalse(result)
        coVerify(exactly = 0) { mockApi.removeOfflineRecord(any(), any()) }
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(
                89L,
                verityPolarId,
                match { PolarVeritySenseOfflineController.isVerityPendingSync(it) },
                any(),
                any()
            )
        }
    }

    @Test
    fun testStopAndBackfill_sensorUnreachable_recordsVerityPendingSync() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_121000") } returns true
        coEvery { mockApi.connectToDevice(verityPolarId) } throws RuntimeException("GATT unreachable")

        val result = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = 90L,
            baseFileName = "2026-11-15_121000",
            startTimeMs = 1_700_000_000_000L
        )

        assertFalse(result)
        assertFalse(PolarVeritySenseOfflineController.isRecordingArmed())
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(
                90L,
                verityPolarId,
                PolarVeritySenseOfflineController.VERITY_EXERCISE_PREFIX,
                1_700_000_000_000L,
                any()
            )
        }
        verify(atLeast = 1) { mockBtleDevice.resumeGattOperations() }
    }

    @Test
    @Suppress("LongMethod")
    fun testSyncPendingWorkout_backfillsClearsPendingDeletesOnlyMatchedEntryAndReexports() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        mockkConstructor(ExportManager::class)
        every { anyConstructed<ExportManager>().invalidateWorkoutExport(any()) } just Runs
        every { anyConstructed<ExportManager>().exportWorkout(any()) } just Runs

        val now = LocalDateTime.now().minusMinutes(10)
        val anchorMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val matchedPath = "/U/0/20261115/R/121500/HR.BPB"
        val matchingEntry = PolarOfflineRecordingEntry(
            path = matchedPath,
            size = 1024L,
            date = now,
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        val unrelatedEntry = PolarOfflineRecordingEntry(
            path = "/U/0/20261114/R/090000/HR.BPB",
            size = 500L,
            date = now.minusDays(1),
            type = PolarBleApi.PolarDeviceDataType.HR
        )
        val hrRecording = PolarOfflineRecordingData.HrOfflineRecording(
            data = PolarHrData(listOf(createHrSample(hr = 150), createHrSample(hr = 160))),
            startTime = now
        )
        val devItem = DevicesDatabaseManager.RemoteDeviceUIDataCacheItem(
            7L, "Polar Sense $verityPolarId", DeviceType.HRM, Protocol.BLUETOOTH_LE
        )
        every { mockDb.allDevicesForCache } returns listOf(devItem)
        every { mockDb.getBluetoothMACAddress(7L) } returns verityMac

        every { mockSummariesDb.getPolarPendingSyncData(91L) } returns
            WorkoutSummariesDatabaseManager.PolarPendingSyncData(
                verityPolarId,
                "${PolarVeritySenseOfflineController.VERITY_EXERCISE_PREFIX}$matchedPath",
                anchorMs,
                anchorMs + 60_000L,
                true
            )
        every { mockWorkoutRepo.getBaseFileName(91L) } returns "2026-11-15_121500"
        val mockWorkoutData = mockk<WorkoutData>(relaxed = true) {
            every { id } returns 91L
        }
        every { mockWorkoutRepo.allWorkouts } returns MutableStateFlow(listOf(mockWorkoutData))
        every { mockSamplesDb.calcHeartRateStats("2026-11-15_121500") } returns
            WorkoutSamplesDatabaseManager.HrStats(150.0, 160.0, 155.0, 2)

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns emptyList()
        every { mockApi.listOfflineRecordings(verityPolarId) } returns flowOf(unrelatedEntry, matchingEntry)
        coEvery { mockApi.getOfflineRecord(verityPolarId, matchingEntry, null) } returns hrRecording
        coEvery { mockApi.removeOfflineRecord(verityPolarId, matchingEntry) } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val result = PolarVeritySenseOfflineController.syncPendingWorkout(context, 91L)

        assertTrue(result)
        verify(exactly = 1) {
            mockSamplesDb.patchMissingHeartRateSamples("2026-11-15_121500", anchorMs, any())
            mockSummariesDb.clearPolarSyncPending(91L)
            mockWorkoutRepo.refreshWorkout(91L)
            anyConstructed<ExportManager>().invalidateWorkoutExport("2026-11-15_121500")
            anyConstructed<ExportManager>().exportWorkout(mockWorkoutData)
        }
        coVerify(exactly = 0) { mockRrRepo.insertIntervals(any()) }
        coVerify(exactly = 0) { HrvPostWorkoutRecomputer.recomputeWorkout(any(), any()) }
        coVerify(exactly = 1) { mockApi.removeOfflineRecord(verityPolarId, matchingEntry) }
        coVerify(exactly = 0) { mockApi.removeOfflineRecord(verityPolarId, unrelatedEntry) }
    }

    @Test
    fun testSyncPendingPolarWorkout_routesVerityPrefixAndLegacyH10Separately() = runBlocking {
        assertTrue(PolarVeritySenseOfflineController.isVerityPendingSync("verity:/U/0/HR.BPB"))
        assertFalse(PolarVeritySenseOfflineController.isVerityPendingSync("/exercises/1"))

        mockkObject(PolarVeritySenseOfflineController)
        mockkObject(PolarH10OfflineController)
        coEvery { PolarVeritySenseOfflineController.syncPendingWorkout(context, 10L) } returns true
        coEvery { PolarH10OfflineController.syncPendingWorkout(context, 20L) } returns true

        assertTrue(syncPendingPolarWorkout(context, 10L, "verity:/U/0/HR.BPB"))
        assertTrue(syncPendingPolarWorkout(context, 20L, "/exercises/1"))

        coVerify(exactly = 1) { PolarVeritySenseOfflineController.syncPendingWorkout(context, 10L) }
        coVerify(exactly = 0) { PolarH10OfflineController.syncPendingWorkout(context, 10L) }
        coVerify(exactly = 1) { PolarH10OfflineController.syncPendingWorkout(context, 20L) }
    }

    private fun createHrSample(hr: Int, correctedHr: Int = hr): PolarHrData.PolarHrSample {
        return PolarHrData.PolarHrSample(
            hr = hr,
            correctedHr = correctedHr,
            ppgQuality = 100,
            rrs = emptyList(),
            rrsMs = emptyList(),
            rrAvailable = false,
            contactStatus = true,
            contactStatusSupported = true
        )
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
        every {
            mockApi.isFeatureReady(
                polarId,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_TRAINING_DATA
            )
        } returns true
    }

    @Test
    fun testIsVeritySwimBusy_identifiesSwimBusyPrefix() {
        assertTrue(PolarVeritySenseOfflineController.isVeritySwimBusy("verity:swim_busy:A1B2C3D4"))
        assertTrue(PolarVeritySenseOfflineController.isVerityPendingSync("verity:swim_busy:A1B2C3D4"))
        assertFalse(PolarVeritySenseOfflineController.isVeritySwimBusy("verity:/U/0/20261115/R/120000/HR.BPB"))
        assertFalse(PolarVeritySenseOfflineController.isVeritySwimBusy(null))
    }

    @Test
    fun testArming_unprovisionedSensor_invokesDoFirstTimeUse() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.setLocalTime(verityPolarId, any()) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns emptyList()
        coEvery { mockApi.startOfflineRecording(verityPolarId, any(), any(), any()) } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        // Unprovisioned sensor: isFtuDone returns false
        coEvery { mockApi.isFtuDone(verityPolarId) } returns false
        coEvery { mockApi.doFirstTimeUse(verityPolarId, any()) } just Runs

        val armed = PolarVeritySenseOfflineController.executeArming(context, verityMac, verityPolarId)

        assertTrue(armed)
        coVerify(exactly = 1) { mockApi.isFtuDone(verityPolarId) }
        coVerify(exactly = 1) { mockApi.doFirstTimeUse(verityPolarId, any()) }
    }

    @Test
    fun testSwimSession_parsesPoolSwimAndExtractsHrAndTurns() {
        val pbDate = Types.PbDate.newBuilder()
            .setYear(2026)
            .setMonth(11)
            .setDay(15)
            .buildPartial()
        val pbTime = Types.PbTime.newBuilder()
            .setHour(10)
            .setMinute(30)
            .setSeconds(0)
            .setMillis(0)
            .buildPartial()
        val pbStart = Types.PbLocalDateTime.newBuilder()
            .setDate(pbDate)
            .setTime(pbTime)
            .buildPartial()
        val pbDuration = Types.PbDuration.newBuilder()
            .setHours(0)
            .setMinutes(30)
            .setSeconds(0)
            .setMillis(0)
            .buildPartial()
        val pbSport = PbSportIdentifier.newBuilder()
            .setValue(103L) // Pool Swimming
            .buildPartial()

        val exerciseBase = PbExerciseBase.newBuilder()
            .setStart(pbStart)
            .setDuration(pbDuration)
            .setSport(pbSport)
            .buildPartial()

        val samples = PbExerciseSamples.newBuilder()
            .addAllHeartRateSamples(listOf(130, 135, 140, 145))
            .addAllDistanceSamples(listOf(0f, 25f, 25f, 50f))
            .buildPartial()

        val polarExercise = PolarExercise(
            index = 0,
            path = "/U/0/20261115/E/103000/0",
            exerciseDataTypes = emptyList(),
            exerciseSummary = exerciseBase,
            samples = samples
        )

        val ref = PolarTrainingSessionReference(
            date = java.time.LocalDate.of(2026, 11, 15),
            path = "/U/0/20261115/E/103000",
            trainingDataTypes = emptyList(),
            exercises = emptyList(),
            fileSize = 1024L
        )

        val pbSession = PbTrainingSession.newBuilder()
            .setStart(pbStart)
            .setDuration(pbDuration)
            .setSport(pbSport)
            .buildPartial()

        val trainingSession = PolarTrainingSession(
            reference = ref,
            sessionSummary = pbSession,
            exercises = listOf(polarExercise)
        )

        val parsed = PolarVeritySenseOfflineController.parseSwimTrainingSession(trainingSession)
        assertNotNull(parsed)
        assertEquals(ref, parsed!!.reference)
        assertEquals(4, parsed.hrSamples.size)
        assertEquals(listOf(130, 135, 140, 145), parsed.hrSamples)

        // Turns detected at index 1 (0 -> 25) and index 3 (25 -> 50)
        assertEquals(2, parsed.turnTimestampsMs.size)
        assertEquals(parsed.startTimeMs + 1000L, parsed.turnTimestampsMs[0])
        assertEquals(parsed.startTimeMs + 3000L, parsed.turnTimestampsMs[1])
    }

    @Test
    fun testStopAndBackfill_swimMode_patchesHrAndDeletesSessionPostCommit() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        val workoutId = 95L
        val baseFileName = "2026-11-15_103000"
        val startLdt = LocalDateTime.of(2026, 11, 15, 10, 30, 0)
        val anchorMs = startLdt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val pbDate = Types.PbDate.newBuilder().setYear(2026).setMonth(11).setDay(15).buildPartial()
        val pbTime = Types.PbTime.newBuilder().setHour(10).setMinute(30).setSeconds(0).setMillis(0).buildPartial()
        val pbStart = Types.PbLocalDateTime.newBuilder().setDate(pbDate).setTime(pbTime).buildPartial()
        val pbDuration = Types.PbDuration.newBuilder().setHours(0).setMinutes(10).setSeconds(0).setMillis(0).buildPartial()
        val pbSport = PbSportIdentifier.newBuilder().setValue(103L).buildPartial()

        val exerciseBase = PbExerciseBase.newBuilder()
            .setStart(pbStart)
            .setDuration(pbDuration)
            .setSport(pbSport)
            .buildPartial()
        val samples = PbExerciseSamples.newBuilder()
            .addAllHeartRateSamples(listOf(130, 140, 150))
            .addAllDistanceSamples(listOf(0f, 25f, 50f))
            .buildPartial()
        val polarExercise = PolarExercise(
            index = 0,
            path = "/U/0/20261115/E/103000/0",
            exerciseDataTypes = emptyList(),
            exerciseSummary = exerciseBase,
            samples = samples
        )
        val ref = PolarTrainingSessionReference(
            date = java.time.LocalDate.of(2026, 11, 15),
            path = "/U/0/20261115/E/103000",
            trainingDataTypes = emptyList(),
            exercises = emptyList(),
            fileSize = 1024L
        )
        val pbSession = PbTrainingSession.newBuilder()
            .setStart(pbStart)
            .setDuration(pbDuration)
            .setSport(pbSport)
            .buildPartial()
        val trainingSession = PolarTrainingSession(
            reference = ref,
            sessionSummary = pbSession,
            exercises = listOf(polarExercise)
        )

        val pairsSlot = slot<List<android.util.Pair<Long, Int>>>()
        every { mockSamplesDb.hasMissingHeartRateSamples(baseFileName) } returns true
        every {
            mockSamplesDb.patchMissingHeartRateSamples(baseFileName, anchorMs, capture(pairsSlot))
        } returns 3
        every { mockSamplesDb.calcHeartRateStats(baseFileName) } returns
            WorkoutSamplesDatabaseManager.HrStats(130.0, 150.0, 140.0, 3)

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } returns emptyList()
        every { mockApi.listOfflineRecordings(verityPolarId) } returns flowOf()
        every { mockApi.getTrainingSessionReferences(verityPolarId, null, null) } returns flowOf(ref)
        coEvery { mockApi.getTrainingSession(verityPolarId, ref) } returns trainingSession
        coEvery { mockApi.deleteTrainingSession(verityPolarId, ref) } just Runs
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val success = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = workoutId,
            baseFileName = baseFileName,
            startTimeMs = anchorMs
        )

        assertTrue(success)
        assertEquals(3, pairsSlot.captured.size)
        verify(exactly = 1) { mockSportTypesRepo.ensureSwimSportTypeExists() }
        verify(exactly = 1) { mockSamplesDb.patchSwimKinematics(baseFileName, anchorMs, any(), any(), 25.0) }
        verify(atLeast = 1) { mockLapsDb.saveLap(workoutId, any(), any(), 25.0, any()) }
        verify(exactly = 1) { mockSummariesDb.updateSwimMetrics(workoutId, 101L, any(), any(), any(), any()) }
        verify(exactly = 1) { mockWorkoutRepo.updateWorkoutSport(workoutId, 101L, any()) }
        coVerify(exactly = 1) { mockApi.deleteTrainingSession(verityPolarId, ref) }
        coVerify(exactly = 0) { mockRrRepo.insertIntervals(any()) }
    }

    @Test
    fun testStopAndBackfill_systemBusy_savesSwimBusyPendingTag() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        val workoutId = 96L
        val baseFileName = "2026-11-15_110000"
        val anchorMs = 1_700_000_000_000L

        every { mockSamplesDb.hasMissingHeartRateSamples(baseFileName) } returns true
        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs

        val busyError = mockk<BlePsFtpUtils.PftpResponseError>(relaxed = true)
        every { busyError.errorName } returns "SYSTEM_BUSY"
        every { busyError.message } returns "SYSTEM_BUSY"
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } throws busyError
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val success = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = workoutId,
            baseFileName = baseFileName,
            startTimeMs = anchorMs
        )

        assertFalse(success)
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(
                workoutId,
                verityPolarId,
                "${PolarVeritySenseOfflineController.VERITY_SWIM_BUSY_PREFIX}$verityPolarId",
                anchorMs,
                any()
            )
        }
    }

    @Test
    fun testSyncPendingWorkout_systemBusy_updatesSwimBusyPendingTag() = runBlocking {
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        val workoutId = 97L
        val anchorMs = 1_700_000_000_000L
        val devItem = DevicesDatabaseManager.RemoteDeviceUIDataCacheItem(
            8L, "Polar Sense $verityPolarId", DeviceType.HRM, Protocol.BLUETOOTH_LE
        )
        every { mockDb.allDevicesForCache } returns listOf(devItem)
        every { mockDb.getBluetoothMACAddress(8L) } returns verityMac

        every { mockSummariesDb.getPolarPendingSyncData(workoutId) } returns
            WorkoutSummariesDatabaseManager.PolarPendingSyncData(
                verityPolarId,
                PolarVeritySenseOfflineController.VERITY_EXERCISE_PREFIX,
                anchorMs,
                anchorMs + 60_000L,
                true
            )

        mockReadyFeatures(verityPolarId)
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } just Runs

        val busyError = mockk<BlePsFtpUtils.PftpResponseError>(relaxed = true)
        every { busyError.errorName } returns "SYSTEM_BUSY"
        every { busyError.message } returns "SYSTEM_BUSY"
        coEvery { mockApi.getOfflineRecordingStatus(verityPolarId) } throws busyError
        coEvery { mockApi.disconnectFromDevice(verityPolarId) } just Runs

        val success = PolarVeritySenseOfflineController.syncPendingWorkout(context, workoutId)

        assertFalse(success)
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(
                workoutId,
                verityPolarId,
                "${PolarVeritySenseOfflineController.VERITY_SWIM_BUSY_PREFIX}$verityPolarId",
                anchorMs,
                anchorMs + 60_000L
            )
        }
    }

    @Test
    fun testApplySwimBackfill_turnsLessThanOrEqualToAnchor_filtersUnanchoredTurnsAndPreventsPhantomLaps() {
        val workoutId = 98L
        val baseFileName = "2026-11-15_120000"
        val anchorMs = 1_700_000_000_000L

        val ref = PolarTrainingSessionReference(
            date = java.time.LocalDate.of(2026, 11, 15),
            path = "/U/0/20261115/E/120000",
            trainingDataTypes = emptyList(),
            exercises = emptyList(),
            fileSize = 1024L
        )

        // 4 turns: 2 are <= anchorMs, 2 are > anchorMs (at +25s and +50s)
        val swimSession = PolarVeritySenseOfflineController.VeritySwimSession(
            reference = ref,
            startTimeMs = anchorMs,
            durationMs = 75_000L,
            hrSamples = listOf(130, 135, 140),
            turnTimestampsMs = listOf(
                anchorMs - 5_000L,  // < anchorMs -> MUST be filtered out
                anchorMs,           // == anchorMs -> MUST be filtered out
                anchorMs + 25_000L, // > anchorMs -> valid turn 1
                anchorMs + 50_000L   // > anchorMs -> valid turn 2
            )
        )

        every { mockSamplesDb.hasMissingHeartRateSamples(baseFileName) } returns false
        every { mockSamplesDb.patchMissingHeartRateSamples(baseFileName, anchorMs, any()) } returns 3
        every { mockSportTypesRepo.ensureSwimSportTypeExists() } returns 101L

        val committed = PolarVeritySenseOfflineController.applySwimBackfillAndCommit(
            appContext = context,
            workoutId = workoutId,
            baseFileName = baseFileName,
            swimSession = swimSession,
            fallbackAnchorMs = anchorMs,
            clearPendingFlag = false
        )

        assertTrue(committed)

        // 2 valid turns -> lengthCount = 2 + 1 = 3 (NOT 4 + 1 = 5)
        // totalDistance = 3 * 25.0 = 75.0m
        verify(exactly = 1) {
            mockSummariesDb.updateSwimMetrics(
                workoutId,
                101L,
                any(),
                75.0,
                3,
                any()
            )
        }

        // Discrete laps saved in Laps.db must be exactly 3 laps with 0 phantom 1s laps
        verify(exactly = 3) {
            mockLapsDb.saveLap(
                workoutId,
                any(),
                any(),
                25.0,
                any()
            )
        }
        verify(exactly = 1) {
            mockWorkoutRepo.addAccumulatedSensorTypes(workoutId, setOf(SensorType.HR))
        }
    }

    @Test
    fun testExecuteArming_reusesSharedCoordinatorSingletonAndMultiplexerWithoutClobbering() = runBlocking {
        // TST-SET-120.1, TST-SET-120.2
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = null

        val sharedMockApi = mockk<PolarBleApi>(relaxed = true)
        var factoryCalls = 0
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, features ->
            factoryCalls++
            assertEquals(PolarOfflineGattCoordinator.ALL_POLAR_FEATURES, features)
            sharedMockApi
        }
        every { sharedMockApi.isFeatureReady(verityPolarId, any()) } returns true
        coEvery { sharedMockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { sharedMockApi.waitForConnection(verityPolarId) } just Runs
        coEvery { sharedMockApi.setLocalTime(verityPolarId, any()) } just Runs
        coEvery { sharedMockApi.getOfflineRecordingStatus(verityPolarId) } returns emptyList()
        coEvery {
            sharedMockApi.startOfflineRecording(verityPolarId, PolarBleApi.PolarDeviceDataType.HR, null, null)
        } just Runs
        coEvery { sharedMockApi.disconnectFromDevice(verityPolarId) } just Runs

        val bridgeCallback = mockk<com.polar.sdk.api.PolarBleApiCallback>(relaxed = true)
        PolarOfflineGattCoordinator.acquirePersistentSession(
            context = context,
            polarId = "1C3B2421",
            macAddress = "24:AC:AC:25:0F:A6",
            callback = bridgeCallback
        )

        val armed = PolarVeritySenseOfflineController.executeArming(context, verityMac, verityPolarId)

        assertTrue(armed)
        assertEquals(1, factoryCalls)
        // setApiCallback called strictly once for multiplexer; never clobbered by offline arming
        verify(exactly = 1) { sharedMockApi.setApiCallback(any()) }
        verify(exactly = 0) { sharedMockApi.cleanup() }
        verify(exactly = 0) { sharedMockApi.shutDown() }
        assertEquals(1, PolarOfflineGattCoordinator.getActiveLeaseCountForTesting())
    }

    @Test
    fun testArmForActiveWorkout_skipsUnwornVeritySenseWhenActiveH10Receives() = runBlocking {
        // TST-SET-120.4
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        val verityDev = DevicesDatabaseManager.RemoteDeviceUIDataCacheItem(
            2L,
            "Polar Verity Sense $verityPolarId",
            DeviceType.HRM,
            com.atrainingtracker.banalservice.Protocol.BLUETOOTH_LE
        )
        every { mockDb.allDevicesForCache } returns listOf(verityDev)
        every { mockDb.getBluetoothMACAddress(2L) } returns verityMac
        every { mockDb.isPolarH10OfflineRecordingEnabled(2L) } returns true

        val activeH10 = mockk<com.atrainingtracker.banalservice.devices.MyRemoteDevice>(relaxed = true)
        every { activeH10.deviceType } returns DeviceType.HRM
        every { activeH10.isReceivingData } returns true
        every { activeH10.deviceId } returns 1L
        every { mockDeviceManager.remoteDeviceList } returns java.util.LinkedList(listOf(activeH10))

        val armed = PolarVeritySenseOfflineController.armForActiveWorkout(context)

        assertFalse(armed)
        coVerify(exactly = 0) { mockApi.connectToDevice(any()) }
    }

    @Test
    fun testRenamedVeritySense_armsViaDisHardwareMetadataAndSerialId() = runBlocking {
        // TST-CON-049.4
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi

        val renamedMac = "AA:BB:CC:DD:EE:52"
        PolarOfflineCapabilityStore.recordDisMetadata(context, renamedMac, modelNumber = "OH1", serialNumber = "0123CAFE")

        val renamedDev = DevicesDatabaseManager.RemoteDeviceUIDataCacheItem(
            12L,
            "Swim Band",
            DeviceType.HRM,
            com.atrainingtracker.banalservice.Protocol.BLUETOOTH_LE
        )
        every { mockDb.allDevicesForCache } returns listOf(renamedDev)
        every { mockDb.getBluetoothMACAddress(12L) } returns renamedMac
        every { mockDb.isPolarH10OfflineRecordingEnabled(12L) } returns true

        coEvery { mockApi.connectToDevice(any()) } just Runs
        coEvery { mockApi.waitForConnection(any()) } just Runs
        every { mockApi.isFeatureReady(any(), any()) } returns true
        coEvery { mockApi.doFirstTimeUse(any(), any()) } just Runs
        coEvery { mockApi.setLocalTime(any(), any()) } just Runs
        coEvery { mockApi.listOfflineRecordings(any()) } returns flowOf()
        coEvery { mockApi.startOfflineRecording(any(), any()) } just Runs
        coEvery { mockApi.disconnectFromDevice(any()) } just Runs

        val armed = PolarVeritySenseOfflineController.armForActiveWorkout(context)
        assertTrue(armed)
        coVerify(exactly = 1) { mockApi.connectToDevice("0123CAFE") }
    }

    @Test
    fun testStopAndBackfill_confirmedDisconnectedDevice_fastFailsWithoutWaitingForConnection() = runBlocking {
        // TST-SET-121.4
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = null
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> mockApi }
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        every { mockBtleDevice.deviceType } returns DeviceType.HRM
        every { mockBtleDevice.protocol } returns Protocol.BLUETOOTH_LE
        every { mockBtleDevice.address } returns verityMac
        every { mockBtleDevice.isReceivingData } returns false
        every { mockDeviceManager.remoteDeviceList } returns java.util.LinkedList(listOf(mockBtleDevice))
        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-19_060000") } returns true

        val result = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = 2195L,
            baseFileName = "2026-11-19_060000",
            startTimeMs = 1_700_000_000_000L
        )

        assertFalse(result)
        assertFalse(PolarVeritySenseOfflineController.isRecordingArmed())
        coVerify(exactly = 0) { mockApi.connectToDevice(any()) }
        coVerify(exactly = 0) { mockApi.waitForConnection(any()) }
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(
                2195L,
                verityPolarId,
                PolarVeritySenseOfflineController.VERITY_EXERCISE_PREFIX,
                1_700_000_000_000L,
                any()
            )
        }
    }

    @Test
    fun testStopAndBackfill_hangingWaitForConnection_timesOutAtStopConnectionTimeoutMs() = runBlocking {
        // TST-SET-121.4
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        PolarVeritySenseOfflineController.stopConnectionTimeoutMs = 50L
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)

        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-19_060500") } returns true
        coEvery { mockApi.connectToDevice(verityPolarId) } just Runs
        coEvery { mockApi.waitForConnection(verityPolarId) } coAnswers {
            kotlinx.coroutines.delay(5_000L)
        }

        val startMs = System.currentTimeMillis()
        val result = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = 2198L,
            baseFileName = "2026-11-19_060500",
            startTimeMs = 1_700_000_000_000L
        )
        val elapsedMs = System.currentTimeMillis() - startMs

        assertFalse(result)
        assertTrue("Expected bounded stop connection wait (< 2000ms), took ${elapsedMs}ms", elapsedMs < 2_000L)
        assertFalse(PolarVeritySenseOfflineController.isRecordingArmed())
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(
                2198L,
                verityPolarId,
                PolarVeritySenseOfflineController.VERITY_EXERCISE_PREFIX,
                1_700_000_000_000L,
                any()
            )
        }
        verify(atLeast = 1) { mockBtleDevice.resumeGattOperations() }
    }

    @Test
    fun testSyncPendingWorkout_cancellationExceptionIsRethrown() = runBlocking {
        // TST-SET-121.2
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.apiOverride = mockApi
        every { mockSummariesDb.getPolarPendingSyncData(2199L) } returns
            WorkoutSummariesDatabaseManager.PolarPendingSyncData(
                verityPolarId,
                "${PolarVeritySenseOfflineController.VERITY_EXERCISE_PREFIX}/U/0/HR.BPB",
                1000L,
                2000L,
                true
            )
        coEvery { mockApi.connectToDevice(verityPolarId) } throws
            kotlinx.coroutines.CancellationException("rememberCoroutineScope left the composition")

        var caughtCancellation: kotlinx.coroutines.CancellationException? = null
        try {
            PolarVeritySenseOfflineController.syncPendingWorkout(context, 2199L)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            caughtCancellation = ce
        }
        assertNotNull(caughtCancellation)
    }

    @Test
    fun testStopAndBackfillWorkout_staleArmedTokenPreservesNewerWorkoutArmedStateAndSuppressesNotification_tstTrk091_1() = runBlocking {
        // REQ-TRK-091.1 / TST-TRK-091.1
        PolarVeritySenseOfflineController.sdkIntOverride = 33
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)
        val workout1Token = PolarVeritySenseOfflineController.getArmedSessionToken()

        // Simulate back-to-back workout 2 arming before workout 1's async backfill exits
        PolarVeritySenseOfflineController.setArmedForTesting(true, verityPolarId, verityMac)
        val workout2Token = PolarVeritySenseOfflineController.getArmedSessionToken()
        assertTrue("Monotonic armed token must increment on re-arm", workout2Token > workout1Token)

        every { mockDb.allDevicesForCache } returns emptyList()
        PolarVeritySenseOfflineController.setArmedForTesting(true, null, null)
        val workout3Token = PolarVeritySenseOfflineController.getArmedSessionToken()

        val result = PolarVeritySenseOfflineController.stopAndBackfillWorkout(
            context = context,
            workoutId = 2200L,
            baseFileName = "2026-11-20_180000",
            startTimeMs = 1000L,
            showOwnNotification = false,
            expectedArmedToken = workout1Token
        )

        assertFalse(result)
        assertTrue("Stale workout 1 exit must NOT disarm newer workout", PolarVeritySenseOfflineController.isRecordingArmed())
        assertEquals(workout3Token, PolarVeritySenseOfflineController.getArmedSessionToken())
    }
}
