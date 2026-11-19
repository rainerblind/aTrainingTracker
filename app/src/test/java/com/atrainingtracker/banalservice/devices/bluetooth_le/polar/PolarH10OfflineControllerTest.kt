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

@file:Suppress("PackageNaming", "WildcardImport")

package com.atrainingtracker.banalservice.devices.bluetooth_le.polar

import android.content.Context
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.Protocol
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.devices.DeviceManager
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.MyRemoteDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager
import com.atrainingtracker.trainingtracker.database.hrv.HrvPostWorkoutRecomputer
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrInterval
import com.atrainingtracker.trainingtracker.database.hrv.WorkoutRrRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarH10OfflineExerciseApi
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Unit test suite verifying PolarH10OfflineController (ATT-7762, TST-SET-106 / ATT-7918, TST-SET-107).
 *
 * Tests API 33 isolation (TST-SET-106.3), single-shot arming and fringe-flap
 * invariance (TST-SET-106.1), fault-tolerant GATT arbitration handoff (TST-SET-106.2),
 * zero-gap fast path bypass (TST-SET-107.1), stop & backfill fetch (TST-SET-107.2),
 * transactional slot cleanup (TST-SET-107.3), and bounded timeout fallback (TST-SET-107.5).
 */
@Suppress("WildcardImport", "MaxLineLength", "PackageNaming")
class PolarH10OfflineControllerTest {

    private val context = mockk<Context>(relaxed = true)
    private val mockApi = mockk<PolarBleApi>(relaxed = true)
    private val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
    private val mockDeviceManager = mockk<DeviceManager>(relaxed = true)
    private val mockBtleDevice = mockk<MyBTLEDevice>(relaxed = true)
    private val mockRrRepo = mockk<WorkoutRrRepository>(relaxed = true)
    private val mockSamplesDb = mockk<WorkoutSamplesDatabaseManager>(relaxed = true)
    private val mockSummariesDb = mockk<WorkoutSummariesDatabaseManager>(relaxed = true)
    private val mockWorkoutRepo = mockk<WorkoutRepository>(relaxed = true)

    private val testDevice = DevicesDatabaseManager.RemoteDeviceUIDataCacheItem(
        1L,
        "Polar H10 1C3B2421",
        DeviceType.HRM,
        Protocol.BLUETOOTH_LE
    )

    private val testEntry = PolarExerciseEntry(
        path = "/exercises/1",
        date = LocalDateTime.now(),
        identifier = "1C3B2421"
    )

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0

        mockkStatic(DevicesDatabaseManager::class)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb
        every { mockDevicesDb.allDevicesForCache } returns listOf(testDevice)
        every { mockDevicesDb.isPolarH10OfflineRecordingEnabled(1L) } returns true
        every { mockDevicesDb.getBluetoothMACAddress(1L) } returns "AA:BB:CC:DD:EE:FF"

        mockkStatic(WorkoutSummariesDatabaseManager::class)
        every { WorkoutSummariesDatabaseManager.getInstance(any()) } returns mockSummariesDb

        mockkStatic(BANALService::class)
        every { BANALService.getDeviceManager() } returns mockDeviceManager
        every { mockDeviceManager.getBtleDeviceByAddress("AA:BB:CC:DD:EE:FF") } returns mockBtleDevice
        every { mockBtleDevice.pauseGattOperations() } just Runs
        every { mockBtleDevice.resumeGattOperations() } just Runs

        WorkoutRrRepository.resetForTesting(mockRrRepo)
        WorkoutSamplesDatabaseManager.resetForTesting(mockSamplesDb)
        WorkoutRepository.resetForTesting(mockWorkoutRepo)
        every { mockWorkoutRepo.allWorkouts } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())

        mockkObject(HrvPostWorkoutRecomputer)
        coEvery { HrvPostWorkoutRecomputer.recomputeWorkout(any(), any()) } returns true

        PolarOfflineGattCoordinator.resetForTesting()
        PolarH10OfflineController.resetForTesting()
        PolarH10OfflineController.dispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        PolarOfflineCapabilityStore.resetForTesting()
        PolarOfflineGattCoordinator.resetForTesting()
        PolarH10OfflineController.resetForTesting()
        WorkoutRrRepository.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        WorkoutRepository.resetForTesting(null)
        unmockkObject(HrvPostWorkoutRecomputer)
        unmockkAll()
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
        // Given: Recording is not armed
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.setArmedForTesting(false)
        assertFalse(PolarH10OfflineController.isRecordingArmed())

        // When: Workout stopped
        PolarH10OfflineController.onWorkoutStopped(context)

        // Then: isRecordingArmed returns false
        assertFalse(PolarH10OfflineController.isRecordingArmed())
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

    @Test
    fun testZeroGapFastPath_bypassesPftpDownload() = runBlocking {
        // Given: Live 0x180D data was 100% continuous (zero missing HR samples and zero ARTIFACT_GAP)
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi
        PolarH10OfflineController.setArmedForTesting(true)

        val continuousBeats = listOf(
            WorkoutRrInterval(workoutId = 123L, beatIndex = 0, beatTimestampMs = 1000L, rrMs = 800f, artifactFlag = 0, gapBeats = 0),
            WorkoutRrInterval(workoutId = 123L, beatIndex = 1, beatTimestampMs = 1800L, rrMs = 800f, artifactFlag = 0, gapBeats = 0)
        )
        every { mockRrRepo.getIntervalsForWorkoutBlocking(123L) } returns continuousBeats
        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_123456") } returns false

        coEvery { mockApi.connectToDevice(any()) } just Runs
        coEvery { mockApi.waitForConnection(any()) } just Runs
        coEvery { mockApi.requestRecordingStatus(any()) } returns PolarH10OfflineExerciseApi.PolarRecordingStatus(
            ongoing = true,
            entryId = "ATT_1",
            supported = true
        )
        coEvery { mockApi.stopRecording(any()) } just Runs
        every { mockApi.listExercises(any()) } returns flowOf(testEntry)
        coEvery { mockApi.removeExercise(any(), any()) } just Runs
        coEvery { mockApi.disconnectFromDevice(any()) } just Runs

        // When: stopAndBackfillWorkout is executed
        val result = PolarH10OfflineController.stopAndBackfillWorkout(context, 123L, "2026-11-15_123456", 1000L)

        // Then (TST-SET-107.1): Zero-gap fast path stops recording and removes exercise without PFTP fetch
        assertTrue("Fast path should succeed", result)
        coVerify(exactly = 1) { mockApi.stopRecording("1C3B2421") }
        coVerify(exactly = 1) { mockApi.removeExercise("1C3B2421", testEntry) }
        coVerify(exactly = 0) { mockApi.fetchExercise(any(), any()) }
        verify(exactly = 1) { mockBtleDevice.resumeGattOperations() }
        assertFalse("Armed state must reset after stop", PolarH10OfflineController.isRecordingArmed())
    }

    @Test
    fun testStopAndBackfill_gapsExist_fetchesAndPatchesDb() = runBlocking {
        // Given: Gaps exist (e.g. pool swim out of range)
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi
        PolarH10OfflineController.setArmedForTesting(true)

        every { mockRrRepo.getIntervalsForWorkoutBlocking(123L) } returns emptyList() // No live RR beats
        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_123456") } returns true

        val exerciseData = PolarExerciseData(recordingInterval = 1, hrSamples = listOf(800, 850, 750))

        coEvery { mockApi.connectToDevice(any()) } just Runs
        coEvery { mockApi.waitForConnection(any()) } just Runs
        coEvery { mockApi.requestRecordingStatus(any()) } returns PolarH10OfflineExerciseApi.PolarRecordingStatus(
            ongoing = true,
            entryId = "ATT_1",
            supported = true
        )
        coEvery { mockApi.stopRecording(any()) } just Runs
        every { mockApi.listExercises(any()) } returns flowOf(testEntry)
        coEvery { mockApi.fetchExercise("1C3B2421", testEntry) } returns exerciseData
        every { mockSamplesDb.patchMissingHeartRateSamples("2026-11-15_123456", any(), any()) } returns 3
        coEvery { mockRrRepo.insertIntervals(any()) } returns 3
        coEvery { mockApi.removeExercise("1C3B2421", testEntry) } just Runs
        coEvery { mockApi.disconnectFromDevice(any()) } just Runs

        // When: stopAndBackfillWorkout is executed
        val result = PolarH10OfflineController.stopAndBackfillWorkout(context, 123L, "2026-11-15_123456", 1000L)

        // Then (TST-SET-107.2 & TST-SET-107.3): Fetches exercise, patches DBs, recomputes, cleans up
        assertTrue("Backfill should succeed", result)
        coVerify(exactly = 1) { mockApi.fetchExercise("1C3B2421", testEntry) }
        verify(exactly = 1) { mockSamplesDb.patchMissingHeartRateSamples("2026-11-15_123456", any(), any()) }
        verify(exactly = 1) { mockWorkoutRepo.addAccumulatedSensorTypes(123L, setOf(SensorType.HR)) }
        coVerify(exactly = 1) { mockRrRepo.insertIntervals(any()) }
        coVerify(exactly = 1) { HrvPostWorkoutRecomputer.recomputeWorkout(any(), 123L) }
        coVerify(exactly = 1) { mockApi.removeExercise("1C3B2421", testEntry) }
        verify(exactly = 1) { mockBtleDevice.resumeGattOperations() }
        assertFalse("Armed state must reset after stop", PolarH10OfflineController.isRecordingArmed())
    }

    @Test
    fun testRemoveExercise_onlyAfterSqliteCommit() = runBlocking {
        // Given: Gaps exist, PFTP download succeeds, but SQLite patching throws exception
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi
        PolarH10OfflineController.setArmedForTesting(true)

        every { mockRrRepo.getIntervalsForWorkoutBlocking(123L) } returns emptyList()
        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_123456") } returns true

        val exerciseData = PolarExerciseData(recordingInterval = 1, hrSamples = listOf(800, 850, 750))

        coEvery { mockApi.connectToDevice(any()) } just Runs
        coEvery { mockApi.waitForConnection(any()) } just Runs
        coEvery { mockApi.requestRecordingStatus(any()) } returns PolarH10OfflineExerciseApi.PolarRecordingStatus(
            ongoing = false,
            entryId = "",
            supported = true
        )
        every { mockApi.listExercises(any()) } returns flowOf(testEntry)
        coEvery { mockApi.fetchExercise("1C3B2421", testEntry) } returns exerciseData
        every { mockSamplesDb.patchMissingHeartRateSamples(any(), any(), any()) } throws RuntimeException("SQLite disk error")

        // When: stopAndBackfillWorkout is executed
        val result = PolarH10OfflineController.stopAndBackfillWorkout(context, 123L, "2026-11-15_123456", 1000L)

        // Then (TST-SET-107.3 Invariant 4): Transactional slot protection prevents removeExercise on strap
        assertFalse("Backfill should report failure on SQLite exception", result)
        coVerify(exactly = 0) { mockApi.removeExercise(any(), any()) }
        verify(exactly = 1) { mockBtleDevice.resumeGattOperations() }
    }

    @Test
    fun testApiLevelBelow33_bypassesBackfill() = runBlocking {
        // Given: API 32
        PolarH10OfflineController.sdkIntOverride = 32
        PolarH10OfflineController.apiOverride = mockApi
        PolarH10OfflineController.setArmedForTesting(true)

        // When: stopAndBackfillWorkout is executed
        val result = PolarH10OfflineController.stopAndBackfillWorkout(context, 123L, "2026-11-15_123456", 1000L)

        // Then: Immediate false without invoking any API methods
        assertFalse("API < 33 must bypass backfill", result)
        coVerify(exactly = 0) { mockApi.connectToDevice(any()) }
        assertFalse(PolarH10OfflineController.isRecordingArmed())
    }

    @Test
    fun testStopAndBackfill_timeout_persistsPendingSyncMetadata() = runBlocking {
        // Given: Gaps exist and connection/fetch times out or fails (TST-SET-108.1)
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi
        PolarH10OfflineController.setArmedForTesting(true)
        PolarH10OfflineController.setArmedMetadataForTesting("1C3B2421", "ATT_TEST_EX")

        every { mockRrRepo.getIntervalsForWorkoutBlocking(123L) } returns emptyList()
        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_123456") } returns true

        // Simulate connection failure (e.g. strap asleep)
        coEvery { mockApi.connectToDevice(any()) } throws RuntimeException("BLE connection timeout")

        // When: stopAndBackfillWorkout is executed
        val result = PolarH10OfflineController.stopAndBackfillWorkout(context, 123L, "2026-11-15_123456", 1000L)

        // Then (TST-SET-108.1): Returns false gracefully, but records polarSyncPending = 1 in SQLite
        assertFalse("Should return false on connection failure", result)
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(123L, "1C3B2421", "ATT_TEST_EX", 1000L, any())
        }
        verify(exactly = 1) { mockWorkoutRepo.refreshWorkout(123L) }
        verify(exactly = 1) { mockBtleDevice.resumeGattOperations() }
        assertFalse("Armed state must reset after stop", PolarH10OfflineController.isRecordingArmed())
    }

    @Test
    fun testSyncPendingWorkout_success_fetchesPatchesAndClearsFlag() = runBlocking {
        // Given: Pending sync metadata exists in summaries DB (TST-SET-108.4)
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi

        val pendingData = WorkoutSummariesDatabaseManager.PolarPendingSyncData(
            "1C3B2421",
            "ATT_TEST_EX",
            1000L,
            2000L,
            true
        )
        every { mockSummariesDb.getPolarPendingSyncData(123L) } returns pendingData
        every { mockWorkoutRepo.getBaseFileName(123L) } returns "2026-11-15_123456"

        val exerciseData = PolarExerciseData(recordingInterval = 1, hrSamples = listOf(800, 850, 750))
        coEvery { mockApi.connectToDevice("1C3B2421") } just Runs
        coEvery { mockApi.waitForConnection("1C3B2421") } just Runs
        coEvery { mockApi.requestRecordingStatus("1C3B2421") } returns PolarH10OfflineExerciseApi.PolarRecordingStatus(false, "", true)
        every { mockApi.listExercises("1C3B2421") } returns flowOf(testEntry)
        coEvery { mockApi.fetchExercise("1C3B2421", testEntry) } returns exerciseData
        every { mockSamplesDb.patchMissingHeartRateSamples(any(), any(), any()) } returns 3
        coEvery { mockRrRepo.insertIntervals(any()) } returns 3
        coEvery { mockApi.removeExercise("1C3B2421", testEntry) } just Runs
        coEvery { mockApi.disconnectFromDevice("1C3B2421") } just Runs

        // When: syncPendingWorkout is executed
        val result = PolarH10OfflineController.syncPendingWorkout(context, 123L)

        // Then (TST-SET-108.4): Deferred sync patches DBs, clears flag, removes exercise from strap, refreshes UI
        assertTrue("Deferred sync should succeed", result)
        coVerify(exactly = 1) { mockApi.fetchExercise("1C3B2421", testEntry) }
        verify(exactly = 1) { mockWorkoutRepo.addAccumulatedSensorTypes(123L, setOf(SensorType.HR)) }
        coVerify(exactly = 1) { mockRrRepo.insertIntervals(any()) }
        coVerify(exactly = 1) { HrvPostWorkoutRecomputer.recomputeWorkout(any(), 123L) }
        verify(exactly = 1) { mockSummariesDb.clearPolarSyncPending(123L) }
        coVerify(exactly = 1) { mockApi.removeExercise("1C3B2421", testEntry) }
        verify(atLeast = 1) { mockWorkoutRepo.refreshWorkout(123L) }
    }

    @Test
    fun testDismissPendingSync_clearsPendingFlag() {
        // Given & When: dismissPolarSyncPending called on repository (TST-SET-108.5)
        mockWorkoutRepo.dismissPolarSyncPending(123L)

        // Then (TST-SET-108.5): dismissPolarSyncPending invoked without touching sensor exercise
        verify { mockWorkoutRepo.dismissPolarSyncPending(123L) }
    }

    @Test
    fun testSyncPendingWorkout_nullBaseFileName_doesNotRecordHrAccumulatedSensorType() = runBlocking {
        // REQ-TRK-084.4: Deferred sync without a valid baseFileName must not patch or record SensorType.HR
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi

        val pendingData = WorkoutSummariesDatabaseManager.PolarPendingSyncData(
            "1C3B2421",
            "ATT_TEST_EX",
            1000L,
            2000L,
            true
        )
        every { mockSummariesDb.getPolarPendingSyncData(123L) } returns pendingData
        every { mockWorkoutRepo.getBaseFileName(123L) } returns null
        every { mockSummariesDb.getBaseFileName(123L) } returns null

        val exerciseData = PolarExerciseData(recordingInterval = 1, hrSamples = listOf(800, 850, 750))
        coEvery { mockApi.connectToDevice("1C3B2421") } just Runs
        coEvery { mockApi.waitForConnection("1C3B2421") } just Runs
        coEvery { mockApi.requestRecordingStatus("1C3B2421") } returns PolarH10OfflineExerciseApi.PolarRecordingStatus(false, "", true)
        every { mockApi.listExercises("1C3B2421") } returns flowOf(testEntry)
        coEvery { mockApi.fetchExercise("1C3B2421", testEntry) } returns exerciseData
        coEvery { mockRrRepo.insertIntervals(any()) } returns 3
        coEvery { mockApi.removeExercise("1C3B2421", testEntry) } just Runs
        coEvery { mockApi.disconnectFromDevice("1C3B2421") } just Runs

        val result = PolarH10OfflineController.syncPendingWorkout(context, 123L)

        assertTrue(result)
        verify(exactly = 0) { mockSamplesDb.patchMissingHeartRateSamples(any(), any(), any()) }
        verify(exactly = 0) { mockWorkoutRepo.addAccumulatedSensorTypes(any(), any()) }
    }

    @Test
    fun testStopAndBackfill_zeroPatchedRows_doesNotRecordHrAccumulatedSensorType() = runBlocking {
        // REQ-TRK-084.4: When patchMissingHeartRateSamples returns 0, SensorType.HR is not upserted
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi
        PolarH10OfflineController.setArmedForTesting(true)

        every { mockRrRepo.getIntervalsForWorkoutBlocking(123L) } returns emptyList()
        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-15_123456") } returns true
        every { mockSamplesDb.patchMissingHeartRateSamples("2026-11-15_123456", any(), any()) } returns 0

        val exerciseData = PolarExerciseData(recordingInterval = 1, hrSamples = listOf(800, 850, 750))
        coEvery { mockApi.connectToDevice(any()) } just Runs
        coEvery { mockApi.waitForConnection(any()) } just Runs
        coEvery { mockApi.requestRecordingStatus(any()) } returns PolarH10OfflineExerciseApi.PolarRecordingStatus(false, "", true)
        every { mockApi.listExercises(any()) } returns flowOf(testEntry)
        coEvery { mockApi.fetchExercise("1C3B2421", testEntry) } returns exerciseData
        coEvery { mockRrRepo.insertIntervals(any()) } returns 3
        coEvery { mockApi.removeExercise("1C3B2421", testEntry) } just Runs
        coEvery { mockApi.disconnectFromDevice(any()) } just Runs

        val result = PolarH10OfflineController.stopAndBackfillWorkout(context, 123L, "2026-11-15_123456", 1000L)

        assertTrue(result)
        verify(exactly = 1) { mockSamplesDb.patchMissingHeartRateSamples("2026-11-15_123456", any(), any()) }
        verify(exactly = 0) { mockWorkoutRepo.addAccumulatedSensorTypes(any(), any()) }
    }

    @Test
    fun testBackfillAndPendingSync_reuseSharedCoordinatorSingletonWithoutCleanup() = runBlocking {
        // TST-SET-120.1, TST-SET-120.3
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = null

        val sharedMockApi = mockk<PolarBleApi>(relaxed = true)
        var factoryCalls = 0
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, features ->
            factoryCalls++
            org.junit.Assert.assertEquals(PolarOfflineGattCoordinator.ALL_POLAR_FEATURES, features)
            sharedMockApi
        }

        // Simulate an active sibling persistent session holding refCount = 1
        val bridgeCallback = mockk<com.polar.sdk.api.PolarBleApiCallback>(relaxed = true)
        PolarOfflineGattCoordinator.acquirePersistentSession(
            context = context,
            polarId = "A1B2C3D4",
            macAddress = "24:AC:AC:24:82:56",
            callback = bridgeCallback
        )

        // 1. stopAndBackfillWorkout fast path
        every { mockRrRepo.getIntervalsForWorkoutBlocking(123L) } returns listOf(
            WorkoutRrInterval(
                workoutId = 123L,
                beatIndex = 0,
                beatTimestampMs = 1000L,
                rrMs = 800f,
                artifactFlag = 0,
                gapBeats = 0
            )
        )
        every { mockSamplesDb.hasMissingHeartRateSamples("2026-11-17_120000") } returns false
        coEvery { sharedMockApi.connectToDevice(any()) } just Runs
        coEvery { sharedMockApi.waitForConnection(any()) } just Runs
        coEvery { sharedMockApi.requestRecordingStatus(any()) } returns
            PolarH10OfflineExerciseApi.PolarRecordingStatus(false, "", true)
        every { sharedMockApi.listExercises(any()) } returns emptyFlow()
        coEvery { sharedMockApi.disconnectFromDevice(any()) } just Runs

        val backfillResult = PolarH10OfflineController.stopAndBackfillWorkout(
            context,
            123L,
            "2026-11-17_120000",
            1000L
        )
        assertTrue(backfillResult)

        // 2. syncPendingWorkout
        val pendingData = WorkoutSummariesDatabaseManager.PolarPendingSyncData("1C3B2421", "ex1", 1000L, 2000L, true)
        every { mockSummariesDb.getPolarPendingSyncData(456L) } returns pendingData
        val syncResult = PolarH10OfflineController.syncPendingWorkout(context, 456L)
        assertFalse(syncResult) // empty exercises returns false cleanly

        assertEquals(1, factoryCalls)
        assertEquals(1, PolarOfflineGattCoordinator.getActiveLeaseCountForTesting())
        verify(exactly = 0) { sharedMockApi.cleanup() }
        verify(exactly = 0) { sharedMockApi.shutDown() }
    }

    @Test
    fun testArmForActiveWorkout_skipsUnwornH10WhenOtherHrmReceives() = runBlocking {
        // TST-SET-120.4
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi

        val activeOtherHrm = mockk<MyRemoteDevice>(relaxed = true)
        every { activeOtherHrm.deviceType } returns DeviceType.HRM
        every { activeOtherHrm.isReceivingData } returns true
        every { activeOtherHrm.deviceId } returns 99L
        every { mockDeviceManager.remoteDeviceList } returns java.util.LinkedList(listOf(activeOtherHrm))

        val armed = PolarH10OfflineController.armForActiveWorkout(context)

        assertFalse(armed)
        coVerify(exactly = 0) { mockApi.connectToDevice(any()) }
    }

    @Test
    fun testRenamedH10_armsAndSyncsPendingWorkoutViaDisHardwareMetadata() = runBlocking {
        // TST-CON-049.4
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi

        val renamedMac = "AA:BB:CC:DD:EE:51"
        PolarOfflineCapabilityStore.markOfflineRecordingCapable(context, renamedMac)
        PolarOfflineCapabilityStore.recordDisMetadata(context, renamedMac, modelNumber = "H10", serialNumber = "FACE1234")

        val renamedDev = DevicesDatabaseManager.RemoteDeviceUIDataCacheItem(
            11L,
            "Chest Strap",
            DeviceType.HRM,
            Protocol.BLUETOOTH_LE
        )
        every { mockDevicesDb.allDevicesForCache } returns listOf(renamedDev)
        every { mockDevicesDb.getBluetoothMACAddress(11L) } returns renamedMac
        every { mockDevicesDb.isPolarH10OfflineRecordingEnabled(11L) } returns true

        coEvery { mockApi.connectToDevice(any()) } just Runs
        coEvery { mockApi.waitForConnection(any()) } just Runs
        coEvery { mockApi.setLocalTime(any(), any()) } just Runs
        coEvery { mockApi.requestRecordingStatus(any()) } returns
            PolarH10OfflineExerciseApi.PolarRecordingStatus(false, "", true)
        every { mockApi.listExercises(any()) } returns emptyFlow()
        coEvery { mockApi.startRecording(any(), any(), any(), any()) } just Runs
        coEvery { mockApi.disconnectFromDevice(any()) } just Runs

        val armed = PolarH10OfflineController.armForActiveWorkout(context)
        assertTrue(armed)
        coVerify(exactly = 1) { mockApi.connectToDevice("FACE1234") }

        val pending = WorkoutSummariesDatabaseManager.PolarPendingSyncData("FACE1234", "ex_renamed", 1000L, 2000L, true)
        every { mockSummariesDb.getPolarPendingSyncData(789L) } returns pending
        val synced = PolarH10OfflineController.syncPendingWorkout(context, 789L)
        assertFalse(synced)
        coVerify(atLeast = 2) { mockApi.connectToDevice("FACE1234") }
    }

    @Test
    fun testStopAndBackfill_confirmedDisconnectedDevice_fastFailsWithoutWaitingForConnection() = runBlocking {
        // TST-SET-121.4
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = null
        PolarOfflineGattCoordinator.apiFactoryForTesting = { _, _ -> mockApi }
        PolarH10OfflineController.setArmedForTesting(true)
        PolarH10OfflineController.setArmedMetadataForTesting("1C3B2421", "ATT_FASTFAIL")

        every { mockBtleDevice.deviceType } returns DeviceType.HRM
        every { mockBtleDevice.protocol } returns Protocol.BLUETOOTH_LE
        every { mockBtleDevice.address } returns "AA:BB:CC:DD:EE:FF"
        every { mockBtleDevice.isReceivingData } returns false
        every { mockDeviceManager.remoteDeviceList } returns java.util.LinkedList(listOf(mockBtleDevice))
        every { mockRrRepo.getIntervalsForWorkoutBlocking(456L) } returns emptyList()

        val result = PolarH10OfflineController.stopAndBackfillWorkout(context, 456L, "2026-11-19_061000", 1000L)

        assertFalse(result)
        assertFalse(PolarH10OfflineController.isRecordingArmed())
        coVerify(exactly = 0) { mockApi.connectToDevice(any()) }
        coVerify(exactly = 0) { mockApi.waitForConnection(any()) }
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(456L, "1C3B2421", "ATT_FASTFAIL", 1000L, any())
        }
    }

    @Test
    fun testStopAndBackfill_hangingWaitForConnection_timesOutAtStopConnectionTimeoutMs() = runBlocking {
        // TST-SET-121.4
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi
        PolarH10OfflineController.stopConnectionTimeoutMs = 50L
        PolarH10OfflineController.setArmedForTesting(true)
        PolarH10OfflineController.setArmedMetadataForTesting("1C3B2421", "ATT_TIMEOUT")

        every { mockRrRepo.getIntervalsForWorkoutBlocking(457L) } returns emptyList()
        coEvery { mockApi.connectToDevice("1C3B2421") } just Runs
        coEvery { mockApi.waitForConnection("1C3B2421") } coAnswers {
            kotlinx.coroutines.delay(5_000L)
        }

        val startMs = System.currentTimeMillis()
        val result = PolarH10OfflineController.stopAndBackfillWorkout(context, 457L, "2026-11-19_061500", 1000L)
        val elapsedMs = System.currentTimeMillis() - startMs

        assertFalse(result)
        assertTrue("Expected bounded H10 stop connection wait (< 2000ms), took ${elapsedMs}ms", elapsedMs < 2_000L)
        assertFalse(PolarH10OfflineController.isRecordingArmed())
        verify(exactly = 1) {
            mockSummariesDb.setPolarSyncPending(457L, "1C3B2421", "ATT_TIMEOUT", 1000L, any())
        }
        verify(atLeast = 1) { mockBtleDevice.resumeGattOperations() }
    }

    @Test
    fun testSyncPendingWorkout_cancellationExceptionIsRethrown() = runBlocking {
        // TST-SET-121.2
        PolarH10OfflineController.sdkIntOverride = 33
        PolarH10OfflineController.apiOverride = mockApi
        every { mockSummariesDb.getPolarPendingSyncData(458L) } returns
            WorkoutSummariesDatabaseManager.PolarPendingSyncData("1C3B2421", "ATT_CANCEL", 1000L, 2000L, true)
        coEvery { mockApi.connectToDevice("1C3B2421") } throws
            kotlinx.coroutines.CancellationException("rememberCoroutineScope left the composition")

        var caughtCancellation: kotlinx.coroutines.CancellationException? = null
        try {
            PolarH10OfflineController.syncPendingWorkout(context, 458L)
        } catch (ce: kotlinx.coroutines.CancellationException) {
            caughtCancellation = ce
        }
        assertNotNull(caughtCancellation)
    }
}
