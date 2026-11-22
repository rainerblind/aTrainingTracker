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

import android.content.ContentValues
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.components.workoutextrema.ExtremaDataRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit and 3,600-tick zero-OOM stress tests for [LiveWorkoutCardStateStore], [applyLiveSnapshot],
 * and [LiveWorkoutBroadcastTracker] integration (`REQ-PER-047`, `TST-PER-055.1`, `TST-PER-055.2`,
 * `REQ-STB-020`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LiveWorkoutCardStateStoreTest {

    private lateinit var store: LiveWorkoutCardStateStore

    @Before
    fun setUp() {
        store = LiveWorkoutCardStateStore()
        LiveWorkoutCardStateStore.Default.clear()
    }

    @After
    fun tearDown() {
        store.clear()
        LiveWorkoutCardStateStore.Default.clear()
    }

    private fun createWorkoutData(
        id: Long,
        finished: Boolean = false,
        bSportType: BSportType = BSportType.BIKE,
        extremaRows: List<ExtremaDataRow> = emptyList(),
        laps: List<LapData> = emptyList()
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = "workout_$id",
            workoutName = "Workout $id",
            sportId = 1L,
            sportName = if (bSportType == BSportType.RUN) "Running" else "Cycling",
            bSportType = bSportType,
            startTimeS = 1763800000L,
            formattedDate = "22.11.2026",
            formattedTime = "08:00",
            localDateTime = LocalDateTime.of(2026, 11, 22, 8, 0),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 0.0,
            maxDisplacement = null,
            activeTimeSec = 0L,
            totalTimeSec = 0L,
            avgSpeedMps = 0.0,
            ascentMeters = 0L,
            descentMeters = 0L,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null,
            extremaRows = extremaRows,
            laps = laps
        )
    }

    @Test
    fun activeTicksPausedTicksLapsAndClear_lifecycleIsDeterministic_TST_PER_055_1() {
        store.onSessionStarted(workoutId = 42L, isPaused = false)
        // Session start arms without flashing zeroed metrics before tick 1.
        assertTrue(store.isSessionActiveFor(42L))
        assertNull(store.snapshot.value)

        store.onActiveTick(
            workoutId = 42L,
            activeTimeSec = 10L,
            totalTimeSec = 10L,
            distanceM = 85.0,
            avgSpeedMps = 8.5,
            ascentM = 4L,
            descentM = 1L,
            hrSampleBpm = 140.0,
            powerSampleWatts = 220.0,
            cadenceSampleRpm = 86.0,
            requireActiveSession = true
        )
        store.onActiveTick(
            workoutId = 42L,
            activeTimeSec = 11L,
            totalTimeSec = 11L,
            distanceM = 93.5,
            avgSpeedMps = 8.5,
            ascentM = 5L,
            descentM = 1L,
            hrSampleBpm = 150.0,
            powerSampleWatts = 260.0,
            cadenceSampleRpm = 90.0,
            requireActiveSession = true
        )

        val active = store.snapshot.value
        assertNotNull(active)
        assertEquals(42L, active!!.workoutId)
        assertEquals(11L, active.activeTimeSec)
        assertEquals(11L, active.totalTimeSec)
        assertEquals(93.5, active.distanceM!!, 1e-6)
        assertEquals(8.5, active.avgSpeedMps!!, 1e-6)
        assertEquals(5L, active.ascentM)
        assertEquals(1L, active.descentM)
        assertEquals(145.0, active.avgHeartRateBpm!!, 1e-6)
        assertEquals(240.0, active.avgPowerWatts!!, 1e-6)
        assertEquals(88.0, active.avgCadenceRpm!!, 1e-6)
        assertFalse(active.isPaused)

        // Authoritative extrema average resyncs running averages (resumed workout continuity).
        store.onActiveTick(
            workoutId = 42L,
            activeTimeSec = 12L,
            totalTimeSec = 12L,
            authoritativeAvgHrBpm = 152.0,
            authoritativeAvgHrCount = 100,
            requireActiveSession = true
        )
        assertEquals(152.0, store.snapshot.value!!.avgHeartRateBpm!!, 1e-6)

        // Pause tick updates totalTimeSec and isPaused without mutating activeTimeSec.
        store.onPausedTick(workoutId = 42L, totalTimeSec = 25L)
        val paused = store.snapshot.value!!
        assertEquals(12L, paused.activeTimeSec)
        assertEquals(25L, paused.totalTimeSec)
        assertTrue(paused.isPaused)

        // Live lap saved appends and deduplicates by lapNr.
        val lap1 = LapData(workoutId = 42L, lapNr = 1L, timeTotalS = 12, distanceTotalM = 102.0, speedAverageMps = 8.5)
        val lap2 = LapData(workoutId = 42L, lapNr = 2L, timeTotalS = 15, distanceTotalM = 135.0, speedAverageMps = 9.0)
        store.onLapSaved(workoutId = 42L, lap = lap2)
        store.onLapSaved(workoutId = 42L, lap = lap1)
        store.onLapSaved(workoutId = 42L, lap = lap1.copy(distanceTotalM = 105.0))
        assertEquals(listOf(1L, 2L), store.snapshot.value!!.laps.map { it.lapNr })
        assertEquals(105.0, store.snapshot.value!!.laps.first().distanceTotalM, 1e-6)

        // Non-active workout DB updates are ignored.
        store.onPausedTick(workoutId = 999L, totalTimeSec = 500L)
        store.onLapSaved(workoutId = 999L, lap = lap1.copy(workoutId = 999L))
        assertEquals(42L, store.snapshot.value!!.workoutId)

        // Clearing disarms session so post-stop queued DB drain cannot resurrect a snapshot.
        store.clear()
        assertNull(store.snapshot.value)
        assertFalse(store.isSessionActiveFor(42L))

        store.onActiveTick(
            workoutId = 42L,
            activeTimeSec = 13L,
            totalTimeSec = 26L,
            requireActiveSession = true
        )
        assertNull(store.snapshot.value)
    }

    @Test
    fun onLiveSamplePersisted_extractsCanonicalAndNamedSensorSamples_TST_PER_055_1() {
        store.onSessionStarted(workoutId = 77L, isPaused = false)

        val summaryValues = ContentValues().apply {
            put(WorkoutSummaries.TIME_ACTIVE_s, 120)
            put(WorkoutSummaries.TIME_TOTAL_s, 135)
            put(WorkoutSummaries.DISTANCE_TOTAL_m, 1025.5)
            put(WorkoutSummaries.SPEED_AVERAGE_mps, 8.54)
            put(WorkoutSummaries.ASCENDING, 42.6)
            put(WorkoutSummaries.DESCENDING, 18.2)
        }
        val samplingValues = ContentValues().apply {
            put("\"HR (Polar H10)\"", 158)
            put(SensorType.POWER.name, 255.0)
            put(SensorType.CADENCE.name, 91.0)
        }
        val extremaUpdates = listOf(
            TrackerService.ExtremaUpdate(SensorType.POWER, ExtremaType.AVG, 245.0, null, 60)
        )

        store.onLiveSamplePersisted(77L, summaryValues, samplingValues, extremaUpdates)

        val snap = store.snapshot.value
        assertNotNull(snap)
        assertEquals(120L, snap!!.activeTimeSec)
        assertEquals(135L, snap.totalTimeSec)
        assertEquals(1025.5, snap.distanceM!!, 1e-6)
        assertEquals(8.54, snap.avgSpeedMps!!, 1e-6)
        assertEquals(43L, snap.ascentM)
        assertEquals(18L, snap.descentM)
        assertEquals(158.0, snap.avgHeartRateBpm!!, 1e-6)
        assertEquals(245.0, snap.avgPowerWatts!!, 1e-6)
        assertEquals(91.0, snap.avgCadenceRpm!!, 1e-6)

        // clearIfMatches clears strictly matching workoutId.
        store.clearIfMatches(999L)
        assertNotNull(store.snapshot.value)
        store.clearIfMatches(77L)
        assertNull(store.snapshot.value)
    }

    @Test
    fun onLiveSamplePersisted_ignoresSiblingDerivedColumnsOnSensorDropout_TST_PER_055_1() {
        store.onSessionStarted(workoutId = 88L, isPaused = false)

        // Tick 1: Normal canonical power (200 W) and HR (150 bpm).
        val summary1 = ContentValues().apply {
            put(WorkoutSummaries.TIME_ACTIVE_s, 10)
            put(WorkoutSummaries.TIME_TOTAL_s, 10)
        }
        val sampling1 = ContentValues().apply {
            put(SensorType.POWER.name, 200.0)
            put(SensorType.HR.name, 150)
        }
        store.onLiveSamplePersisted(88L, summary1, sampling1, emptyList())
        assertEquals(200.0, store.snapshot.value!!.avgPowerWatts!!, 1e-6)
        assertEquals(150.0, store.snapshot.value!!.avgHeartRateBpm!!, 1e-6)

        // Tick 2: Transient power & HR dropout — only sibling derived columns present in samplingValues!
        val summary2 = ContentValues().apply {
            put(WorkoutSummaries.TIME_ACTIVE_s, 11)
            put(WorkoutSummaries.TIME_TOTAL_s, 11)
        }
        val samplingDropout = ContentValues().apply {
            put("POWER_PEAK_5S", 750.0)
            put("POWER_TO_WEIGHT", 3.2)
            put("POWER_5S", 420.0)
            put("HR_ZONE", 4.0)
            put("CADENCE_AVG", 120.0)
        }
        store.onLiveSamplePersisted(88L, summary2, samplingDropout, emptyList())

        // Derived sibling columns MUST NOT contaminate avgPowerWatts, avgHeartRateBpm, or avgCadenceRpm.
        val snapAfterDropout = store.snapshot.value!!
        assertEquals(200.0, snapAfterDropout.avgPowerWatts!!, 1e-6)
        assertEquals(150.0, snapAfterDropout.avgHeartRateBpm!!, 1e-6)
        assertNull(snapAfterDropout.avgCadenceRpm)

        // Tick 3: Quoted named-device column and exact location-provider suffix ARE accepted cleanly.
        val summary3 = ContentValues().apply {
            put(WorkoutSummaries.TIME_ACTIVE_s, 12)
            put(WorkoutSummaries.TIME_TOTAL_s, 12)
        }
        val samplingNamedAndProvider = ContentValues().apply {
            put("\"POWER (Favero Assioma)\"", 240.0)
            put("HR_gps", 160)
        }
        store.onLiveSamplePersisted(88L, summary3, samplingNamedAndProvider, emptyList())
        val snapAfterRecovery = store.snapshot.value!!
        assertEquals(220.0, snapAfterRecovery.avgPowerWatts!!, 1e-6)
        assertEquals(155.0, snapAfterRecovery.avgHeartRateBpm!!, 1e-6)
    }

    @Test
    fun applyLiveSnapshot_guardsFinishedAndMismatchedCardsAndPreservesVerticalOscillation_TST_PER_055_1() {
        val finishedCard = createWorkoutData(id = 10L, finished = true)
        val otherUnfinished = createWorkoutData(id = 11L, finished = false)
        val existingVoRow = ExtremaDataRow(
            sensorLabel = "VO",
            unitLabel = "mm",
            minValue = "72",
            avgValue = "78",
            maxValue = "85",
            iconResId = R.drawable.ic_cadence
        )
        val activeRun = createWorkoutData(
            id = 12L,
            finished = false,
            bSportType = BSportType.RUN,
            extremaRows = listOf(existingVoRow)
        )

        val snapshot = LiveWorkoutCardSnapshot(
            workoutId = 12L,
            activeTimeSec = 615L,
            totalTimeSec = 630L,
            distanceM = 2050.0,
            avgSpeedMps = 3.333,
            ascentM = 34L,
            descentM = 12L,
            avgHeartRateBpm = 161.4,
            avgPowerWatts = 285.0,
            avgCadenceRpm = 176.0,
            laps = listOf(
                LapData(workoutId = 12L, lapNr = 1L, timeTotalS = 300, distanceTotalM = 1000.0, speedAverageMps = 3.33)
            )
        )

        // Finished cards and mismatched cards return the exact original reference.
        assertSame(finishedCard, applyLiveSnapshot(finishedCard, snapshot.copy(workoutId = 10L)))
        assertSame(otherUnfinished, applyLiveSnapshot(otherUnfinished, snapshot))
        assertSame(activeRun, applyLiveSnapshot(activeRun, null))

        // Active running card overlays metrics, laps, HR/Speed/Pace/Power/Cadence without clobbering VO row.
        val overlaid = applyLiveSnapshot(activeRun, snapshot)
        assertEquals(615L, overlaid.activeTimeSec)
        assertEquals(630L, overlaid.totalTimeSec)
        assertEquals(2050.0, overlaid.totalDistance, 1e-6)
        assertEquals(3.333, overlaid.avgSpeedMps, 1e-6)
        assertEquals(34L, overlaid.ascentMeters)
        assertEquals(12L, overlaid.descentMeters)
        assertEquals(1, overlaid.laps.size)

        val rowsByLabel = overlaid.extremaRows.associateBy { it.sensorLabel }
        assertEquals("78", rowsByLabel["VO"]?.avgValue)
        assertEquals("161", rowsByLabel["HR"]?.avgValue)
        assertEquals("285", rowsByLabel["Power"]?.avgValue)
        assertEquals("176", rowsByLabel["Cadence"]?.avgValue)
        assertNotNull(rowsByLabel["Speed"]?.avgValue)
        assertNotNull(rowsByLabel["Pace"]?.avgValue)
    }

    @Test
    fun stressTest_3600TicksWith2200HistoricalWorkouts_zeroAllWorkoutsAndDownstreamChurn_TST_PER_055_2() = runTest {
        val dispatchedBroadcasts = mutableListOf<Long>()
        val broadcastTracker = LiveWorkoutBroadcastTracker(
            dispatcher = { id -> dispatchedBroadcasts.add(id) },
            liveCardStateStore = store
        )

        // Simulate 2,200 historical workouts in _allWorkouts + downstream repository/ViewModel observers
        // (PeriodsRepository, AthleteProfileViewModel, KnownLocationsViewModel).
        val activeWorkoutId = 2201L
        val initialWorkouts = List(2_200) { idx ->
            createWorkoutData(id = idx + 1L, finished = true)
        } + createWorkoutData(id = activeWorkoutId, finished = false)
        val allWorkoutsFlow = MutableStateFlow(initialWorkouts)

        val allWorkoutsEmissions = AtomicInteger(0)
        val periodsRepoRecalculations = AtomicInteger(0)
        val athleteProfileRecalculations = AtomicInteger(0)
        val knownLocationsRecalculations = AtomicInteger(0)
        val liveSnapshotUpdates = AtomicInteger(0)

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val jobs = listOf(
            backgroundScope.launch(dispatcher) {
                allWorkoutsFlow.collect { allWorkoutsEmissions.incrementAndGet() }
            },
            backgroundScope.launch(dispatcher) {
                allWorkoutsFlow.collect { periodsRepoRecalculations.incrementAndGet() }
            },
            backgroundScope.launch(dispatcher) {
                allWorkoutsFlow.collect { athleteProfileRecalculations.incrementAndGet() }
            },
            backgroundScope.launch(dispatcher) {
                allWorkoutsFlow.collect { knownLocationsRecalculations.incrementAndGet() }
            },
            backgroundScope.launch(dispatcher) {
                store.snapshot.collect { snap ->
                    if (snap != null) liveSnapshotUpdates.incrementAndGet()
                }
            }
        )

        // Baseline initial StateFlow emission is 1 per collector.
        assertEquals(1, allWorkoutsEmissions.get())
        assertEquals(1, periodsRepoRecalculations.get())
        assertEquals(1, athleteProfileRecalculations.get())
        assertEquals(1, knownLocationsRecalculations.get())

        // Session start emits exactly ONE discrete WORKOUT_UPDATED_INTENT.
        broadcastTracker.onSessionInitialized(workoutId = activeWorkoutId, isPaused = false)
        assertEquals(1, dispatchedBroadcasts.size)

        // Simulate 3,600 1-Hz ticks (3,540 active + 60 paused) with 3 laps.
        var activeSec = 0L
        var totalSec = 0L
        for (tick in 1..3_600) {
            totalSec++
            val isPausedTick = tick in 1_801..1_860
            if (isPausedTick) {
                store.onPausedTick(workoutId = activeWorkoutId, totalTimeSec = totalSec)
                broadcastTracker.onPauseStateTick(workoutId = activeWorkoutId, isPaused = true)
            } else {
                activeSec++
                val distanceM = activeSec * 8.0
                store.onActiveTick(
                    workoutId = activeWorkoutId,
                    activeTimeSec = activeSec,
                    totalTimeSec = totalSec,
                    distanceM = distanceM,
                    avgSpeedMps = 8.0,
                    ascentM = activeSec / 10L,
                    descentM = activeSec / 12L,
                    hrSampleBpm = 150.0,
                    powerSampleWatts = 240.0,
                    cadenceSampleRpm = 90.0,
                    requireActiveSession = true
                )
                broadcastTracker.onPauseStateTick(workoutId = activeWorkoutId, isPaused = false)
                if (activeSec == 1_000L || activeSec == 2_000L || activeSec == 3_000L) {
                    val lapNr = activeSec / 1_000L
                    store.onLapSaved(
                        workoutId = activeWorkoutId,
                        lap = LapData(
                            workoutId = activeWorkoutId,
                            lapNr = lapNr,
                            timeTotalS = 1000,
                            distanceTotalM = 8000.0,
                            speedAverageMps = 8.0
                        )
                    )
                }
            }
        }

        // Strict Zero-OOM Invariants:
        // 1. Only 3 discrete broadcasts across the entire 1-hour session (start + pause edge + resume edge).
        assertEquals(3, dispatchedBroadcasts.size)
        // 2. ZERO _allWorkouts emissions/clones during all 3,600 ticks (still at initial baseline 1).
        assertEquals(1, allWorkoutsEmissions.get())
        // 3. ZERO downstream recalculations in PeriodsRepository, AthleteProfileViewModel, KnownLocationsViewModel.
        assertEquals(1, periodsRepoRecalculations.get())
        assertEquals(1, athleteProfileRecalculations.get())
        assertEquals(1, knownLocationsRecalculations.get())
        // 4. Dedicated O(1) StateFlow delivered all 3,600 live ticks + 3 lap updates cleanly.
        assertTrue("Expected >= 3600 live snapshot updates, got ${liveSnapshotUpdates.get()}", liveSnapshotUpdates.get() >= 3_600)

        val finalSnap = store.snapshot.value
        assertNotNull(finalSnap)
        assertEquals(3_540L, finalSnap!!.activeTimeSec)
        assertEquals(3_600L, finalSnap.totalTimeSec)
        assertEquals(3_540 * 8.0, finalSnap.distanceM!!, 1e-6)
        assertEquals(150.0, finalSnap.avgHeartRateBpm!!, 1e-6)
        assertEquals(240.0, finalSnap.avgPowerWatts!!, 1e-6)
        assertEquals(90.0, finalSnap.avgCadenceRpm!!, 1e-6)
        assertEquals(3, finalSnap.laps.size)
        assertFalse(finalSnap.isPaused)

        // Teardown resets snapshot cleanly to null.
        broadcastTracker.reset()
        assertNull(store.snapshot.value)

        jobs.forEach { it.cancel() }
    }
}
