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

package com.atrainingtracker.trainingtracker.tracker

import android.content.Context
import android.util.Log
import com.atrainingtracker.banalservice.BANALService.BANALServiceComm
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.EquipmentAndSportTypeDiscoveryManager
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.MilestoneAutoNamingManager
import com.atrainingtracker.trainingtracker.database.WorkoutAutoNamingHelper
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.physiology.AerobicEfficiencyResult
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.physiology.PowerTrainingMetricsResult
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository
import com.atrainingtracker.trainingtracker.settings.SettingsDataStore
import com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper
import com.atrainingtracker.trainingtracker.ui.aftermath.PowerSpeedHrSamples
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.util.StreamSummaryCalculator
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit and characterization tests for [PostWorkoutFinalizer] verifying all 7 fault-isolated
 * finalization stages, lazy provider evaluation, `deferHrPhysiologyToPostProcessing` gating,
 * milestone celebration propagation, and `guessCommuteAndTrainer` XOR truth table
 * (REQ-TRK-094.2, TST-TRK-094.2, ATT-6034).
 */
class PostWorkoutFinalizerTest {

    private lateinit var mockContext: Context
    private lateinit var mockRepo: WorkoutRepository
    private lateinit var mockKnownLocationsRepo: KnownLocationsRepository
    private lateinit var mockDiscoveryManager: EquipmentAndSportTypeDiscoveryManager
    private lateinit var mockClusterEngine: WorkoutClusterEngine
    private lateinit var mockBanalRepo: BANALServiceRepository
    private lateinit var collector: WorkoutRecordingErrorCollector

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockContext = mockk(relaxed = true)
        mockRepo = mockk(relaxed = true)
        mockKnownLocationsRepo = mockk(relaxed = true)
        mockDiscoveryManager = mockk(relaxed = true)
        mockClusterEngine = mockk(relaxed = true)
        mockBanalRepo = mockk(relaxed = true)
        collector = WorkoutRecordingErrorCollector()

        mockkStatic(StreamSummaryCalculator::class)
        every { StreamSummaryCalculator.encodePolyline(any(), any()) } returns "encoded_poly"
        every { StreamSummaryCalculator.encodeDoubleStreamOrEmpty(any()) } returns "encoded_stream"

        mockkObject(WorkoutClusterEngine.Companion)
        every { WorkoutClusterEngine.getInstance(any()) } returns mockClusterEngine
        every { WorkoutClusterEngine.distanceBetween(any(), any()) } returns 500f
        every { mockClusterEngine.findApexFromPoints(any(), any(), any()) } returns LatLng(48.105, 11.505)
        every { mockClusterEngine.distanceBetween(any(), any()) } returns 150f
        every {
            mockClusterEngine.suggestCluster(
                any(), any(), any(), any(), any(),
                any<Set<BSportType>>(),
                any(), any(), any(), any(), any()
            )
        } returns null

        mockkObject(EquipmentAndSportTypeDiscoveryManager.Companion)
        every { EquipmentAndSportTypeDiscoveryManager.getInstance(any()) } returns mockDiscoveryManager
        every { EquipmentAndSportTypeDiscoveryManager.resolveEffectiveSpeedForIdentity(any(), any(), any()) } returns 7.5
        every { mockDiscoveryManager.resolveIdentity(any<Set<Long>>(), any<BSportType>(), any<Double>()) } returns EquipmentAndSportTypeDiscoveryManager.InferredIdentity(
            sportId = 2L,
            bSportType = BSportType.BIKE,
            equipmentId = 99L,
            stravaSportName = "Ride",
            uploadToStrava = 1,
            isHighConfidence = true
        )

        mockkStatic(PowerPhysiologyCalculator::class)
        every {
            PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(any(), any(), any(), any())
        } returns AerobicEfficiencyResult(1.65, 3.2, 1.70, 1.60, true, false)
        every {
            PowerPhysiologyCalculator.computePowerTrainingMetrics(any(), any(), any(), any())
        } returns PowerTrainingMetricsResult(
            normalizedPowerWatts = 240.0,
            avgPowerWatts = 235.0,
            maxPowerWatts = 450.0,
            variabilityIndex = 1.02,
            intensityFactor = 0.85,
            tss = 72.0,
            workKj = 820.0,
            avgPowerToWeight = 3.1,
            maxPowerToWeight = 6.2,
            npPowerToWeight = 3.2
        )

        mockkStatic(PowerHrDynamicsCalculator::class)
        every { PowerHrDynamicsCalculator.calculate(any(), any(), any(), any()) } returns mockk<CardioPowerDynamicsRecord>(relaxed = true)

        mockkStatic(SettingsDataStoreJavaHelper::class)
        every { SettingsDataStoreJavaHelper.resolveEffectiveFtpAt(any(), any()) } returns 280
        every { SettingsDataStoreJavaHelper.resolveEffectiveWeightKgAt(any(), any()) } returns 75.0f

        mockkStatic(WorkoutAutoNamingHelper::class)
        every { WorkoutAutoNamingHelper.generateWorkoutName(any(), any(), any(), any(), any(), any()) } returns "Home Round Trip"

        mockkObject(SettingsDataStore.Companion)
        every { SettingsDataStore.getInstance(any()) } returns mockk(relaxed = true)

        mockkStatic(MilestoneAutoNamingManager::class)
        every { MilestoneAutoNamingManager.evaluateAndApplyMilestone(any(), any(), any(), any(), any()) } returns
            MilestoneAutoNamingManager.MilestoneCelebrationData(42L, 100, "Cycling")

        mockkObject(BANALServiceRepository.Companion)
        every { BANALServiceRepository.getInstance(any()) } returns mockBanalRepo
        every { mockBanalRepo.persistConvergedCircumferences(any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun buildLiveSessionWithGps(): LiveWorkoutSession {
        val session = LiveWorkoutSession(42L, TrackerService.IMPORTANT_SENSOR_TYPES)
        val p1 = LatLng(48.100, 11.500)
        val p2 = LatLng(48.105, 11.505)
        session.setStartLatLng(p1)
        session.setLastLatLng(p2)
        session.addRawLatLng(p1)
        session.addRawLatLng(p2)
        session.recordStreamPoint(p1, 520.0, 0.0)
        session.recordStreamPoint(p2, 540.0, 1000.0)
        session.addSample(SensorType.SPEED_mps, 7.0, p1)
        session.addSample(SensorType.SPEED_mps, 8.0, p2)
        session.addSample(SensorType.POWER, 230.0, p1)
        session.addSample(SensorType.POWER, 250.0, p2)
        return session
    }

    private fun createInput(
        liveSession: LiveWorkoutSession = buildLiveSessionWithGps(),
        deferHrPhysiology: Boolean = false,
        banalService: BANALServiceComm? = null,
        snapshot: TrackerService.BanalFinalizationSnapshot? = null,
        activeSessionDeviceIdsProvider: () -> Set<Long> = { setOf(10L) },
        knownLocationsProvider: () -> KnownLocationsRepository = { mockKnownLocationsRepo },
        fallbackSportIdProvider: () -> Long = { 1L },
        guessCallback: (Long) -> Unit = { wId -> PostWorkoutFinalizer.guessCommuteAndTrainer(mockRepo, wId, false) }
    ): PostWorkoutFinalizer.FinalizationInput {
        return PostWorkoutFinalizer.FinalizationInput(
            context = mockContext,
            repository = mockRepo,
            knownLocationsRepositoryProvider = knownLocationsProvider,
            banalService = banalService,
            diagnosticCollector = collector,
            snapshot = snapshot,
            workoutId = 42L,
            baseFileName = "2026-11-22_test",
            liveSession = liveSession,
            effectiveAvgSpeed = 7.5,
            effectiveAvgSpeedCalculable = true,
            effectiveDistanceTotalM = 1000.0,
            effectiveTimeActiveS = 133,
            deferHrPhysiologyToPostProcessing = deferHrPhysiology,
            activeSessionDeviceIdsProvider = activeSessionDeviceIdsProvider,
            fallbackSportTypeIdProvider = fallbackSportIdProvider,
            importantSensorTypes = TrackerService.IMPORTANT_SENSOR_TYPES,
            guessCommuteAndTrainerCallback = guessCallback,
            debugLogging = false
        )
    }

    @Test
    fun finalizeLiveSession_runsAll7StagesAndReturnsMilestoneCelebration() {
        every { mockRepo.getPowerSpeedHrSamples("2026-11-22_test") } returns PowerSpeedHrSamples(
            listOf(230.0, 250.0),
            listOf(7.0, 8.0),
            listOf(140.0, 145.0)
        )
        every { mockRepo.getExtremaPosition(42L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns LatLng(48.105, 11.505)
        every { mockRepo.getWorkoutMethod(42L) } returns null

        var guessInvokedFor: Long? = null
        val celebration = PostWorkoutFinalizer.finalizeLiveSession(
            createInput(deferHrPhysiology = false, guessCallback = { guessInvokedFor = it })
        )

        assertNotNull(celebration)
        assertEquals(100, celebration?.count)
        assertEquals("Cycling", celebration?.sportName)
        assertEquals(42L, guessInvokedFor)

        verify(exactly = 1) { mockRepo.updateMapAndStreams(42L, "encoded_poly", "encoded_stream", "encoded_stream") }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.POWER, ExtremaType.EFFICIENCY_FACTOR, 1.65, null) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.POWER, ExtremaType.AEROBIC_DECOUPLING, 3.2, null) }
        verify(exactly = 1) { mockRepo.saveCardioPowerDynamics(42L, any()) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.NORMALIZED_POWER, ExtremaType.AVG, 240.0, null) }
        verify(exactly = 1) { mockRepo.updateWorkoutName(42L, "Home Round Trip") }
        verify(exactly = 1) { mockBanalRepo.persistConvergedCircumferences(99L, any()) }
        assertTrue(collector.getEntries().isEmpty())
    }

    @Test
    fun finalizeLiveSession_whenDeferHrPhysiologyTrue_skipsEfAndDynamicsWhilePersistingPowerMetrics() {
        every { mockRepo.getPowerSpeedHrSamples("2026-11-22_test") } returns PowerSpeedHrSamples(
            listOf(230.0, 250.0),
            listOf(7.0, 8.0),
            listOf(140.0, 145.0)
        )
        every { mockRepo.getExtremaPosition(42L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns null

        PostWorkoutFinalizer.finalizeLiveSession(createInput(deferHrPhysiology = true))

        verify(exactly = 0) { PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(any(), any(), any(), any()) }
        verify(exactly = 0) { PowerHrDynamicsCalculator.calculate(any(), any(), any(), any()) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.NORMALIZED_POWER, ExtremaType.AVG, 240.0, null) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.TSS, ExtremaType.END, 72.0, null) }
    }

    @Test
    fun finalizeLiveSession_lazyProvidersNotEvaluatedWhenNotNeededAndFaultIsolatedWhenThrowing() {
        every { mockRepo.getPowerSpeedHrSamples("2026-11-22_test") } returns PowerSpeedHrSamples(emptyList(), emptyList(), emptyList())
        every { mockRepo.getExtremaPosition(42L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns null

        var knownLocationsEvaluated = false
        var fallbackSportEvaluated = false
        val celebration = PostWorkoutFinalizer.finalizeLiveSession(
            createInput(
                knownLocationsProvider = {
                    knownLocationsEvaluated = true
                    throw IllegalStateException("must not be invoked without spatial no-cluster auto-naming")
                },
                fallbackSportIdProvider = {
                    fallbackSportEvaluated = true
                    throw IllegalStateException("must not be invoked when identity.sportId > 0")
                }
            )
        )

        assertNotNull(celebration)
        assertFalse("knownLocationsRepositoryProvider must remain lazy when skipped", knownLocationsEvaluated)
        assertFalse("fallbackSportTypeIdProvider must remain lazy when identity.sportId > 0", fallbackSportEvaluated)

        // Now force fallbackSportTypeIdProvider to throw inside Stage 6 and confirm fault isolation
        every { mockDiscoveryManager.resolveIdentity(any<Set<Long>>(), any<BSportType>(), any<Double>()) } returns EquipmentAndSportTypeDiscoveryManager.InferredIdentity(
            sportId = -1L,
            bSportType = BSportType.UNKNOWN,
            equipmentId = -1L,
            stravaSportName = null,
            uploadToStrava = 0,
            isHighConfidence = false
        )
        val resultAfterStage6Error = PostWorkoutFinalizer.finalizeLiveSession(
            createInput(
                fallbackSportIdProvider = { throw RuntimeException("sportTypeId binder error") }
            )
        )
        assertNull(resultAfterStage6Error)
        assertTrue("Stage 6 error must be captured in DiagnosticCollector", collector.getEntries().isNotEmpty())
        verify(atLeast = 1) { mockRepo.updateRecordingErrors(42L, any()) }
    }

    @Test
    fun finalizeLiveSession_isolatesStage1FailureSoDownstreamStagesStillExecute() {
        every { mockRepo.updateMapAndStreams(any(), any(), any(), any()) } throws RuntimeException("SQLite disk full")
        every { mockRepo.getPowerSpeedHrSamples("2026-11-22_test") } returns PowerSpeedHrSamples(emptyList(), emptyList(), emptyList())
        every { mockRepo.getExtremaPosition(42L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns null

        var guessInvoked = false
        PostWorkoutFinalizer.finalizeLiveSession(createInput(guessCallback = { guessInvoked = true }))

        assertTrue("Stage 4 must still execute after Stage 1 failure", guessInvoked)
        assertTrue("Stage 1 failure must be recorded in DiagnosticCollector", collector.getEntries().isNotEmpty())
        verify(exactly = 1) { mockRepo.applyInferredIdentity(42L, any()) }
        verify(exactly = 1) { mockBanalRepo.persistConvergedCircumferences(99L, any()) }
        verify(exactly = 1) { mockRepo.updateRecordingErrors(42L, any()) }
    }

    @Test
    fun finalizeLiveSession_activeSessionDeviceIdsProviderIsLazyAndStage5FaultIsolated() {
        every { mockRepo.getPowerSpeedHrSamples("2026-11-22_test") } returns PowerSpeedHrSamples(emptyList(), emptyList(), emptyList())
        every { mockRepo.getExtremaPosition(42L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns null

        // (a) Non-empty snapshot.activeRemoteDeviceIds skips provider
        var providerInvokedForSnapshot = false
        val snapshotWithDevices = TrackerService.BanalFinalizationSnapshot(
            1, 60, 500.0, setOf(77L), emptySet(), null, BSportType.BIKE, null, 2L, 7.5, true, 1000.0, 133
        )
        PostWorkoutFinalizer.finalizeLiveSession(
            createInput(
                snapshot = snapshotWithDevices,
                activeSessionDeviceIdsProvider = {
                    providerInvokedForSnapshot = true
                    throw IllegalStateException("must not be evaluated when snapshot has device IDs")
                }
            )
        )
        assertFalse("Provider must not be invoked when snapshot has activeRemoteDeviceIds", providerInvokedForSnapshot)

        // (b) Non-null banalService (with null snapshot) skips provider
        var providerInvokedForBanal = false
        val mockBanal = mockk<BANALServiceComm>(relaxed = true)
        every { mockBanal.databaseIdsOfActiveRemoteDevices } returns listOf(88L)
        every { mockBanal.bSportType } returns BSportType.BIKE
        PostWorkoutFinalizer.finalizeLiveSession(
            createInput(
                banalService = mockBanal,
                activeSessionDeviceIdsProvider = {
                    providerInvokedForBanal = true
                    throw IllegalStateException("must not be evaluated when banalService is non-null")
                }
            )
        )
        assertFalse("Provider must not be invoked when banalService is non-null", providerInvokedForBanal)

        // (c) Provider throwing in Stage 5 is fault-isolated: Stages 1-4 and 7a-7b still execute
        var guessInvoked = false
        val result = PostWorkoutFinalizer.finalizeLiveSession(
            createInput(
                activeSessionDeviceIdsProvider = { throw ConcurrentModificationException("concurrent session mutation") },
                guessCallback = { guessInvoked = true }
            )
        )
        assertNull(result)
        assertTrue("Stage 4 must execute before Stage 5 provider error", guessInvoked)
        assertTrue("Stage 5 provider error must be recorded in DiagnosticCollector", collector.getEntries().isNotEmpty())
        verify(atLeast = 1) { mockRepo.updateMapAndStreams(42L, any(), any(), any()) }
        verify(atLeast = 1) { mockBanalRepo.persistConvergedCircumferences(null, any()) }
        verify(atLeast = 1) { mockRepo.updateRecordingErrors(42L, any()) }
    }

    @Test
    fun guessCommuteAndTrainer_verifiesXorTruthTable() {
        // Case 1: maxLineDistance == null -> trainer = true, commute = false -> persisted
        every { mockRepo.getWorkoutDistance(1L) } returns 5000.0
        every { mockRepo.getExtremaValue(1L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns null
        every { mockRepo.getExtremaValue(1L, SensorType.LINE_DISTANCE_m, ExtremaType.END) } returns null
        PostWorkoutFinalizer.guessCommuteAndTrainer(mockRepo, 1L)
        verify(exactly = 1) { mockRepo.setCommuteAndTrainer(1L, false, true) }

        // Case 2: maxLineDistance >= trainer threshold, max < end * commute ratio -> commute = true
        every { mockRepo.getWorkoutDistance(2L) } returns 12000.0
        every { mockRepo.getExtremaValue(2L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns 5000.0
        every { mockRepo.getExtremaValue(2L, SensorType.LINE_DISTANCE_m, ExtremaType.END) } returns 5000.0
        PostWorkoutFinalizer.guessCommuteAndTrainer(mockRepo, 2L)
        verify(exactly = 1) { mockRepo.setCommuteAndTrainer(2L, true, false) }

        // Case 3: both commute=true and trainer=true -> XOR is false -> NOT persisted
        every { mockRepo.getWorkoutDistance(3L) } returns 100.0
        every { mockRepo.getExtremaValue(3L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns 10.0
        every { mockRepo.getExtremaValue(3L, SensorType.LINE_DISTANCE_m, ExtremaType.END) } returns 10.0
        PostWorkoutFinalizer.guessCommuteAndTrainer(mockRepo, 3L)
        verify(exactly = 0) { mockRepo.setCommuteAndTrainer(3L, any(), any()) }

        // Case 4: neither commute nor trainer (round trip) -> XOR is false -> NOT persisted
        every { mockRepo.getWorkoutDistance(4L) } returns 20000.0
        every { mockRepo.getExtremaValue(4L, SensorType.LINE_DISTANCE_m, ExtremaType.MAX) } returns 8000.0
        every { mockRepo.getExtremaValue(4L, SensorType.LINE_DISTANCE_m, ExtremaType.END) } returns 50.0
        PostWorkoutFinalizer.guessCommuteAndTrainer(mockRepo, 4L)
        verify(exactly = 0) { mockRepo.setCommuteAndTrainer(4L, any(), any()) }
    }
}
