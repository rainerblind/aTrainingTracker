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
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BANALService
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.odometry.MultisportTelemetryAnchor
import com.atrainingtracker.banalservice.odometry.MultisportTransitionBridge
import com.atrainingtracker.banalservice.odometry.MultisportTransitionEvent
import com.atrainingtracker.banalservice.sensor.SensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.LapsDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager
import com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager.WorkoutSummaries
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Robolectric unit tests for [MultisportLapCoordinator] covering retroactive named laps,
 * UTC `TIME_START` formatting, auto-pause `T2` contiguous `LAP_NR` pre-increment checks,
 * manual-lap clamping, asynchronous `WorkoutSamples.db` `LAP_NR` backfilling, and
 * race-free monotonic `LAP_NR` enforcement & finalization reconciliation
 * (`ATT-8917`, `REQ-FIL-031.3`, `TST-FIL-031.3`; `ATT-9159`, `REQ-TRK-090`, `TST-TRK-090`;
 * `ATT-9468`, `REQ-TRK-096`, `TST-TRK-096`).
 */
// Linked test specs: TST-FIL-031, TST-TRK-090, TST-TRK-096
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultisportLapCoordinatorTest {

    private lateinit var context: Context
    private val directExecutor = Executor { it.run() }
    private val utcFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    // 2026-10-01 08:00:00 UTC
    private val sessionStartMs = 1_790_841_600_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        LapsDatabaseManager.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
    }

    @After
    fun tearDown() {
        LapsDatabaseManager.resetForTesting(null)
        WorkoutSamplesDatabaseManager.resetForTesting(null)
        unmockkAll()
    }

    private fun mockBanalLapNr(lapNr: Int): BANALService.BANALServiceComm {
        val comm = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { comm.getBestSensorData(SensorType.LAP_NR) } returns SensorData(SensorType.LAP_NR, lapNr, "$lapNr", "clock")
        return comm
    }

    @Test
    fun brickTransition_persistsNamedLapsWithUtcTimeStart_advancesSynchronously_andBackfillsSamplesDb() {
        val workoutId = 42L
        val baseFileName = "2026-10-01_080000"
        val lapsDb = LapsDatabaseManager.getInstance(context)
        val samplesDbMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        samplesDbMgr.createNewTable(baseFileName, listOf(SensorType.TIME_TOTAL, SensorType.LAP_NR, SensorType.DISTANCE_m))
        val samplesDb = samplesDbMgr.database
        val table = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        // Insert samples spanning Leg 1 (1799), T2 boundary (1800, 1825, 1849), Run boundary (1850, 1860, 1865 with NULL)
        listOf(1799 to 1, 1800 to 1, 1825 to 1, 1849 to 1, 1850 to 1, 1860 to 1, 1865 to null).forEach { (tt, lap) ->
            val cv = ContentValues().apply {
                put(SensorType.TIME_TOTAL.name, tt)
                if (lap != null) put(SensorType.LAP_NR.name, lap)
            }
            samplesDb.insertOrThrow(table, null, cv)
        }

        var advancedCount = -1
        var reseededTimeLapS = -1
        var reseededDistLapM = -1.0
        val callOrder = mutableListOf<String>()
        val coordinator = MultisportLapCoordinator { count, timeLapS, distLapM ->
            callOrder.add("advanceLaps")
            advancedCount = count
            reseededTimeLapS = timeLapS
            reseededDistLapM = distLapM
        }
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)

        val event = MultisportTransitionEvent(
            fromSport = BSportType.BIKE,
            toSport = BSportType.RUN,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 1800_000L, 1800, 1800, 14400.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 1850_000L, 1850, 1850, 14450.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 1865_000L, 1865, 1865, 14510.0)
        )
        val intent = MultisportTransitionBridge.toIntent(event, context.packageName)
        val deferredInit = CompletableFuture<Void>()
        var discreteEdgeCount = 0

        val repo = WorkoutRepository.getInstance(context)
        val plan = coordinator.onMultisportTransitionIntent(
            context = context,
            intent = intent,
            banalService = mockBanalLapNr(1),
            workoutIdProvider = { workoutId },
            baseFileNameProvider = { baseFileName },
            isStoppingProvider = { false },
            workoutRepository = repo,
            tableInitFuture = deferredInit,
            dbExecutor = directExecutor,
            onDiscreteEdge = { discreteEdgeCount++ }
        )

        assertNotNull(plan)
        // Synchronous advance happened BEFORE deferred DB init completed!
        assertEquals(listOf("advanceLaps"), callOrder)
        assertEquals(2, advancedCount)
        assertEquals(15, reseededTimeLapS)
        assertEquals(60.0, reseededDistLapM, 1e-6)
        assertEquals(0, lapsDb.getLaps(workoutId).size)

        // Complete table initialization -> DB persistence & samples backfill execute
        deferredInit.complete(null)

        // Simulate workout stop at t = 2450 s (Run leg: 600 s, 2400 m)
        coordinator.saveFinalLap(repo, workoutId, 3L, 600, 2400.0, 4.0) { _, _, _, _, _ -> }

        val laps = lapsDb.getLaps(workoutId)
        assertEquals(3, laps.size)
        assertEquals(1, discreteEdgeCount)

        // Lap 1: Bike (0..1800 s)
        assertEquals(1L, laps[0].lapNr)
        assertEquals("Bike", laps[0].name)
        assertEquals(1800, laps[0].timeTotalS)
        assertEquals(14400.0, laps[0].distanceTotalM, 0.01)
        assertEquals(sessionStartMs, parseUtcMs(laps[0].timeStart!!))

        // Lap 2: Transition (1800..1850 s)
        assertEquals(2L, laps[1].lapNr)
        assertEquals("Transition", laps[1].name)
        assertEquals(50, laps[1].timeTotalS)
        assertEquals(50.0, laps[1].distanceTotalM, 0.01)
        assertEquals(sessionStartMs + 1800_000L, parseUtcMs(laps[1].timeStart!!))

        // Lap 3: Run (anchored at 1850 s)
        assertEquals(3L, laps[2].lapNr)
        assertEquals("Run", laps[2].name)
        assertEquals(600, laps[2].timeTotalS)
        assertEquals(2400.0, laps[2].distanceTotalM, 0.01)
        assertEquals(sessionStartMs + 1850_000L, parseUtcMs(laps[2].timeStart!!))

        // Verify WorkoutSamples.db LAP_NR backfill: [1800,1850) -> 2; >=1850 (incl NULL) -> 3; 1799 -> 1
        val tagged = mutableMapOf<Int, Int>()
        samplesDb.rawQuery("SELECT ${SensorType.TIME_TOTAL.name}, ${SensorType.LAP_NR.name} FROM $table", null).use { c ->
            while (c.moveToNext()) tagged[c.getInt(0)] = c.getInt(1)
        }
        assertEquals(1, tagged[1799])
        assertEquals(2, tagged[1800])
        assertEquals(2, tagged[1825])
        assertEquals(2, tagged[1849])
        assertEquals(3, tagged[1850])
        assertEquals(3, tagged[1860])
        assertEquals(3, tagged[1865])
    }

    @Test
    fun autoPauseInsideT2_partialAndFull_enforcesReqTrk002AndContiguousLapNr() {
        val repo = WorkoutRepository.getInstance(context)
        val lapsDb = LapsDatabaseManager.getInstance(context)

        // Case A: Partial auto-pause in T2 (50 s wall-clock, 20 s active unpaused, 25 m walk) -> persists Transition (20 s)
        val partialCoord = MultisportLapCoordinator { _, _, _ -> }
        partialCoord.resetForSession(sessionStartMs, 0, 0, 0.0)
        val partialEvent = MultisportTransitionEvent(
            fromSport = BSportType.BIKE,
            toSport = BSportType.RUN,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 1000_000L, 1000, 1000, 8000.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 1050_000L, 1020, 1050, 8025.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 1065_000L, 1035, 1065, 8085.0)
        )
        val partialPlan = partialCoord.onMultisportTransitionIntent(
            context, MultisportTransitionBridge.toIntent(partialEvent, context.packageName),
            mockBanalLapNr(1), { 101L }, { null }, { false }, repo, null, directExecutor, {}
        )!!
        assertEquals(2, partialPlan.lapAdvanceCount)
        assertEquals(3, partialPlan.newLegLapNr)
        val partialLaps = lapsDb.getLaps(101L)
        assertEquals(2, partialLaps.size)
        assertEquals(20, partialLaps[1].timeTotalS)
        assertEquals("Transition", partialLaps[1].name)

        // Case B: 100% auto-paused stationary T2 (50 s wall-clock, 0 s active, 0.0 m) -> skips empty Transition, LAP_NR advances by +1!
        var fullAutoPauseAdvance = -1
        val fullCoord = MultisportLapCoordinator { count, _, _ -> fullAutoPauseAdvance = count }
        fullCoord.resetForSession(sessionStartMs, 0, 0, 0.0)
        val fullEvent = MultisportTransitionEvent(
            fromSport = BSportType.BIKE,
            toSport = BSportType.RUN,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 1000_000L, 1000, 1000, 8000.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 1050_000L, 1000, 1050, 8000.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 1065_000L, 1015, 1065, 8060.0)
        )
        val fullPlan = fullCoord.onMultisportTransitionIntent(
            context, MultisportTransitionBridge.toIntent(fullEvent, context.packageName),
            mockBanalLapNr(1), { 102L }, { null }, { false }, repo, null, directExecutor, {}
        )!!
        assertEquals(1, fullAutoPauseAdvance)
        assertEquals(false, fullPlan.hasTransition)
        assertEquals(2, fullPlan.newLegLapNr)
        fullCoord.saveFinalLap(repo, 102L, 2L, 300, 1200.0, 4.0) { _, _, _, _, _ -> }

        val fullLaps = lapsDb.getLaps(102L)
        assertEquals(2, fullLaps.size)
        assertEquals(1L, fullLaps[0].lapNr)
        assertEquals("Bike", fullLaps[0].name)
        assertEquals(2L, fullLaps[1].lapNr)
        assertEquals("Run", fullLaps[1].name)
    }

    @Test
    fun directSwitchUnder10s_skipsTransitionAndConservesTotalTimeAndDistance() {
        val coordinator = MultisportLapCoordinator { _, _, _ -> }
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)
        val event = MultisportTransitionEvent(
            fromSport = BSportType.BIKE,
            toSport = BSportType.RUN,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 600_000L, 600, 600, 4800.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 606_000L, 606, 606, 4812.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 621_000L, 621, 621, 4872.0)
        )
        val plan = coordinator.planAndCommitAnchorTransition(context, event, 1)
        assertEquals(false, plan.hasTransition)
        assertEquals(1, plan.lapAdvanceCount)
        assertEquals(606, plan.leg1ActiveTimeS)
        assertEquals(4812.0, plan.leg1DistanceM, 0.01)
        assertEquals(2, plan.newLegLapNr)
        assertEquals(15, plan.newLegTimeLapS)
        assertEquals(60.0, plan.newLegDistanceLapM, 0.01)
    }

    @Test
    fun manualLapInterleaving_clampsBoundariesAndClearsActiveLegAnchorAfterManualLap() {
        val repo = WorkoutRepository.getInstance(context)
        val coordinator = MultisportLapCoordinator { _, _, _ -> }
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)

        // Manual lap pressed at t=1810 s (during T2) before transition confirms at t=1865 s
        var unnamedSaveCalled = 0
        val manualIntent1 = Intent(BANALService.LAP_SUMMARY)
            .putExtra(BANALService.PREV_LAP_NR, 1)
            .putExtra(BANALService.PREV_LAP_TIME_S, 1810)
            .putExtra(BANALService.PREV_LAP_DISTANCE_m, 14410.0)
            .putExtra(BANALService.PREV_LAP_SPEED_mps, 7.96)
            .putExtra(BANALService.IS_PAUSE, false)
        coordinator.onLapSummaryIntent(
            manualIntent1, 200L, sessionStartMs + 1810_000L, 1810, 1810, 14410.0,
            false, repo, { _, _, _, _ -> unnamedSaveCalled++ }, {}
        )
        assertEquals(1, unnamedSaveCalled)

        // Transition had exit=1800 (< currentLapStart 1810) and enter=1850 -> clamps effectiveExit to 1810!
        val event = MultisportTransitionEvent(
            fromSport = BSportType.BIKE,
            toSport = BSportType.RUN,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 1800_000L, 1800, 1800, 14400.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 1850_000L, 1850, 1850, 14450.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 1865_000L, 1865, 1865, 14510.0)
        )
        val plan = coordinator.planAndCommitAnchorTransition(context, event, 2)
        assertEquals(false, plan.hasLeg1)
        assertEquals(true, plan.hasTransition)
        assertEquals(1, plan.lapAdvanceCount)
        assertEquals(2, plan.transitionLapNr)
        assertEquals(40, plan.transitionActiveTimeS)
        assertEquals(40.0, plan.transitionDistanceM, 0.01)
        assertEquals(3, plan.newLegLapNr)
        assertEquals("Run", coordinator.getActiveLapName())
        assertNotNull(coordinator.getActiveLapStartTimeUtc())

        // Later manual lap inside Run: closes Run with its anchored name/start, calls onDiscreteEdge, and CLEARS activeLeg!
        var discreteEdges = 0
        val manualIntent2 = Intent(BANALService.LAP_SUMMARY)
            .putExtra(BANALService.PREV_LAP_NR, 3)
            .putExtra(BANALService.PREV_LAP_TIME_S, 250)
            .putExtra(BANALService.PREV_LAP_DISTANCE_m, 1000.0)
            .putExtra(BANALService.PREV_LAP_SPEED_mps, 4.0)
            .putExtra(BANALService.IS_PAUSE, false)
        coordinator.onLapSummaryIntent(
            manualIntent2, 200L, sessionStartMs + 2100_000L, 2100, 2100, 15450.0,
            false, repo, { _, _, _, _ -> }, { discreteEdges++ }
        )
        assertEquals(1, discreteEdges)
        assertNull("Subsequent manual lap must not inherit cleared Run name", coordinator.getActiveLapName())
        assertNull("Subsequent manual lap must not inherit cleared Run timeStart", coordinator.getActiveLapStartTimeUtc())
    }

    @Test
    fun workoutSamplesBackfill_addsMissingLapNrColumnAndFallsBackToUtcTimeColumn() {
        val samplesDbMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        val db = samplesDbMgr.database
        val table = "\"legacy_ms_table\""
        db.execSQL("CREATE TABLE $table (_id INTEGER PRIMARY KEY AUTOINCREMENT, time DATETIME)")
        val t1799 = MultisportLapCoordinator.formatUtcTimestamp(sessionStartMs + 1799_000L)
        val t1820 = MultisportLapCoordinator.formatUtcTimestamp(sessionStartMs + 1820_000L)
        val t1855 = MultisportLapCoordinator.formatUtcTimestamp(sessionStartMs + 1855_000L)
        db.execSQL("INSERT INTO $table (time) VALUES (?)", arrayOf(t1799))
        db.execSQL("INSERT INTO $table (time) VALUES (?)", arrayOf(t1820))
        db.execSQL("INSERT INTO $table (time) VALUES (?)", arrayOf(t1855))

        val coordinator = MultisportLapCoordinator { _, _, _ -> }
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)
        val event = MultisportTransitionEvent(
            fromSport = BSportType.BIKE,
            toSport = BSportType.RUN,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 1800_000L, 1800, 1800, 14400.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 1850_000L, 1850, 1850, 14450.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 1865_000L, 1865, 1865, 14510.0)
        )
        val plan = coordinator.planAndCommitAnchorTransition(context, event, 1)

        coordinator.backfillWorkoutSamplesTable(db, table, plan)

        val rows = mutableMapOf<String, Int>()
        db.rawQuery("SELECT time, ${SensorType.LAP_NR.name} FROM $table", null).use { c ->
            while (c.moveToNext()) rows[c.getString(0)] = c.getInt(1)
        }
        assertEquals(1, rows[t1799])
        assertEquals(2, rows[t1820])
        assertEquals(3, rows[t1855])
    }

    // REQ-TRK-090 / TST-TRK-090
    @Test
    fun singleSportMultiLap_persistsPreMutationUtcTimeStart_withPauseNeutrality_underNonUtcTimezone_tstTrk090_1() {
        val prevTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
        try {
            val workoutId = 909L
            val repo = WorkoutRepository.getInstance(context)
            val lapsDb = LapsDatabaseManager.getInstance(context)
            val coordinator = MultisportLapCoordinator { _, _, _ -> }

            // Start session at T0 (2026-10-01 08:00:00 UTC == 10:00:00 CEST)
            coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)

            // Pause broadcast (IS_PAUSE = true) must NOT overwrite currentLapStartAnchor
            val pauseIntent = Intent(BANALService.LAP_SUMMARY)
                .putExtra(BANALService.IS_PAUSE, true)
                .putExtra(BANALService.PREV_LAP_NR, 1)
                .putExtra(BANALService.PREV_LAP_TIME_S, 0)
                .putExtra(BANALService.PREV_LAP_DISTANCE_m, 0.0)
            coordinator.onLapSummaryIntent(
                pauseIntent, workoutId, sessionStartMs + 300_000L, 120, 300, 500.0,
                false, repo, { _, _, _, _ -> }, {}
            )
            assertEquals(sessionStartMs, coordinator.getCurrentLapStartAnchor().timestampMs)
            assertEquals(0, lapsDb.getLaps(workoutId).size)

            // Manual Lap 1 split at T1 = T0 + 600s (08:10:00 UTC): Lap 1 must store T0, NOT T1!
            val lap1EndMs = sessionStartMs + 600_000L
            val lap1Intent = Intent(BANALService.LAP_SUMMARY)
                .putExtra(BANALService.IS_PAUSE, false)
                .putExtra(BANALService.PREV_LAP_NR, 1)
                .putExtra(BANALService.PREV_LAP_TIME_S, 420)
                .putExtra(BANALService.PREV_LAP_DISTANCE_m, 2000.0)
                .putExtra(BANALService.PREV_LAP_SPEED_mps, 2000.0 / 420.0)
            coordinator.onLapSummaryIntent(
                lap1Intent, workoutId, lap1EndMs, 420, 600, 2000.0,
                false, repo,
                { nr, t, d, s ->
                    repo.saveLap(workoutId, nr, coordinator.consumeLapStartTimeUtc(t), t, d, s, null, null)
                },
                {}
            )
            assertEquals(lap1EndMs, coordinator.getCurrentLapStartAnchor().timestampMs)

            // Stop workout at T2 = T1 + 300s (08:15:00 UTC): Final Lap 2 must store T1, NOT T2!
            coordinator.saveFinalLap(repo, workoutId, 2L, 300, 1500.0, 5.0) { wId, nr, t, d, s ->
                repo.saveLap(wId, nr, coordinator.consumeLapStartTimeUtc(t), t, d, s, null, null)
            }

            val laps = lapsDb.getLaps(workoutId)
            assertEquals(2, laps.size)
            assertEquals("2026-10-01 08:00:00", laps[0].timeStart)
            assertEquals("2026-10-01 08:10:00", laps[1].timeStart)
        } finally {
            TimeZone.setDefault(prevTz)
        }
    }

    // REQ-TRK-090 / TST-TRK-090
    @Test
    fun emptyManualSplit_doesNotLeakPendingStartTimestamp_tstTrk090_1() {
        val repo = WorkoutRepository.getInstance(context)
        val coordinator = MultisportLapCoordinator { _, _, _ -> }
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)

        val emptySplitMs = sessionStartMs + 120_000L
        val emptyIntent = Intent(BANALService.LAP_SUMMARY)
            .putExtra(BANALService.IS_PAUSE, false)
            .putExtra(BANALService.PREV_LAP_NR, 1)
            .putExtra(BANALService.PREV_LAP_TIME_S, 0)
            .putExtra(BANALService.PREV_LAP_DISTANCE_m, 0.0)
        var unnamedCalled = 0
        coordinator.onLapSummaryIntent(
            emptyIntent, 910L, emptySplitMs, 0, 120, 0.0,
            false, repo, { _, _, _, _ -> unnamedCalled++ }, {}
        )
        assertEquals(0, unnamedCalled)
        // Must resolve updated anchor (emptySplitMs), never a leaked pre-mutation pending timestamp
        assertEquals(
            MultisportLapCoordinator.formatUtcTimestamp(emptySplitMs),
            coordinator.consumeLapStartTimeUtc(60)
        )
    }

    // REQ-TRK-096 / TST-TRK-096.1
    @Test
    fun row425RaceInterleaving_inFlightStaleAndTornLapNrEnforcedMonotonically_tstTrk096_1() {
        val workoutId = 2423L
        val baseFileName = "2026-11-21_081228"
        val samplesDbMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        samplesDbMgr.createNewTable(baseFileName, listOf(SensorType.TIME_TOTAL, SensorType.LAP_NR, SensorType.DISTANCE_m))
        val samplesDb = samplesDbMgr.database
        val table = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        val repo = WorkoutRepository.getInstance(context)

        // Pre-transition rows: 1295..1352 with LAP_NR = 4, plus torn LAP_NR = 5 at 1350,
        // a dual-column row with TIME_TOTAL IS NULL + valid UTC time at 1351s, and a manual lap 7 at 1360s.
        fun insertRow(tt: Int?, lapNr: Int?, epochOffsetS: Int = tt ?: 0) {
            val cv = ContentValues().apply {
                put(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME, MultisportLapCoordinator.formatUtcTimestamp(sessionStartMs + epochOffsetS * 1000L))
                if (tt != null) put(SensorType.TIME_TOTAL.name, tt)
                if (lapNr != null) put(SensorType.LAP_NR.name, lapNr)
            }
            samplesDb.insertOrThrow(table, null, cv)
        }
        insertRow(1295, 4)
        insertRow(1300, 4)
        insertRow(1336, 4)
        for (t in 1337..1349) insertRow(t, 4)
        insertRow(1350, 5) // Torn mid-loop LAP_NR = 5 already in DB before backfill
        insertRow(null, 4, epochOffsetS = 1351) // TIME_TOTAL IS NULL in dual-column table!
        insertRow(1352, 4)
        insertRow(1360, 7) // Higher manual lap must NEVER be downgraded

        // Snapshot in-flight 1-Hz samples BEFORE onMultisportTransitionIntent runs (exact Pixel 10 row 425 race)
        val preTransitionSample = ContentValues().apply {
            put(SensorType.TIME_TOTAL.name, 1299)
            put(SensorType.LAP_NR.name, 4)
        }
        val preTransitionSummary = ContentValues().apply { put(WorkoutSummaries.LAPS, 4) }

        val delayedTransitionSample = ContentValues().apply {
            put(SensorType.TIME_TOTAL.name, 1320)
            put(SensorType.LAP_NR.name, 4)
        }
        val delayedTransitionSummary = ContentValues().apply { put(WorkoutSummaries.LAPS, 4) }

        val row425StaleSample = ContentValues().apply {
            put(SensorType.TIME_TOTAL.name, 1353)
            put(SensorType.LAP_NR.name, 4)
        }
        val row425StaleSummary = ContentValues().apply { put(WorkoutSummaries.LAPS, 4) }

        val tornInFlightSample = ContentValues().apply {
            put(SensorType.TIME_TOTAL.name, 1354)
            put(SensorType.LAP_NR.name, 5)
        }
        val tornInFlightSummary = ContentValues().apply { put(WorkoutSummaries.LAPS, 5) }

        val utcOnlyInFlightSample = ContentValues().apply {
            put(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME, MultisportLapCoordinator.formatUtcTimestamp(sessionStartMs + 1355_000L))
            put(SensorType.LAP_NR.name, 4)
        }
        val utcOnlySummary = ContentValues().apply { put(WorkoutSummaries.LAPS, 4) }

        val manualLapSample = ContentValues().apply {
            put(SensorType.TIME_TOTAL.name, 1361)
            put(SensorType.LAP_NR.name, 7)
        }
        val manualLapSummary = ContentValues().apply { put(WorkoutSummaries.LAPS, 7) }

        val coordinator = MultisportLapCoordinator { _, _, _ -> }
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)
        val event = MultisportTransitionEvent(
            fromSport = BSportType.RUN,
            toSport = BSportType.BIKE,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 1300_000L, 1300, 1300, 5200.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 1337_000L, 1337, 1337, 5240.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 1353_000L, 1353, 1353, 5360.0)
        )
        coordinator.onMultisportTransitionIntent(
            context = context,
            intent = MultisportTransitionBridge.toIntent(event, context.packageName),
            banalService = mockBanalLapNr(4),
            workoutIdProvider = { workoutId },
            baseFileNameProvider = { baseFileName },
            isStoppingProvider = { false },
            workoutRepository = repo,
            tableInitFuture = null,
            dbExecutor = directExecutor,
            onDiscreteEdge = {}
        )

        // Now flush all snapshotted in-flight samples AFTER backfill already committed!
        listOf(
            preTransitionSample to preTransitionSummary,
            delayedTransitionSample to delayedTransitionSummary,
            row425StaleSample to row425StaleSummary,
            tornInFlightSample to tornInFlightSummary,
            utcOnlyInFlightSample to utcOnlySummary,
            manualLapSample to manualLapSummary
        ).forEach { (sv, sum) ->
            coordinator.enforceMonotonicLapNr(baseFileName, sv, sum)
            if (!sv.containsKey(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME)) {
                val tt = sv.getAsInteger(SensorType.TIME_TOTAL.name) ?: 0
                sv.put(WorkoutSamplesDatabaseManager.WorkoutSamplesDbHelper.TIME, MultisportLapCoordinator.formatUtcTimestamp(sessionStartMs + tt * 1000L))
            }
            samplesDb.insertOrThrow(table, null, sv)
        }

        // Assert decoupled summaryValues.LAPS floor (workout-wide 6, never regressed by t=1299 or t=1320; manual 7 preserved)
        assertEquals(4, preTransitionSample.getAsInteger(SensorType.LAP_NR.name))
        assertEquals(6, preTransitionSummary.getAsInteger(WorkoutSummaries.LAPS))
        assertEquals(5, delayedTransitionSample.getAsInteger(SensorType.LAP_NR.name))
        assertEquals(6, delayedTransitionSummary.getAsInteger(WorkoutSummaries.LAPS))
        assertEquals(6, row425StaleSample.getAsInteger(SensorType.LAP_NR.name))
        assertEquals(6, row425StaleSummary.getAsInteger(WorkoutSummaries.LAPS))
        assertEquals(6, tornInFlightSample.getAsInteger(SensorType.LAP_NR.name))
        assertEquals(6, tornInFlightSummary.getAsInteger(WorkoutSummaries.LAPS))
        assertEquals(6, utcOnlyInFlightSample.getAsInteger(SensorType.LAP_NR.name))
        assertEquals(6, utcOnlySummary.getAsInteger(WorkoutSummaries.LAPS))
        assertEquals(7, manualLapSample.getAsInteger(SensorType.LAP_NR.name))
        assertEquals(7, manualLapSummary.getAsInteger(WorkoutSummaries.LAPS))

        // Verify persisted DB rows sorted chronologically are 100% monotonically non-decreasing
        val orderedRows = mutableListOf<Pair<Long, Int>>()
        val startEpochS = sessionStartMs / 1000L
        samplesDb.rawQuery(
            "SELECT COALESCE(${SensorType.TIME_TOTAL.name}, CAST(strftime('%s', time) AS INTEGER) - $startEpochS) AS t, ${SensorType.LAP_NR.name} FROM $table ORDER BY t ASC",
            null
        ).use { c ->
            while (c.moveToNext()) orderedRows.add(c.getLong(0) to c.getInt(1))
        }
        val byT = orderedRows.toMap()
        assertEquals(4, byT[1295L])
        assertEquals(4, byT[1299L])
        assertEquals(5, byT[1300L])
        assertEquals(5, byT[1320L])
        assertEquals(5, byT[1336L])
        assertEquals(6, byT[1337L])
        assertEquals(6, byT[1350L]) // Pre-existing torn 5 upgraded to 6
        assertEquals(6, byT[1351L]) // Dual-column TIME_TOTAL IS NULL backfilled via UTC time to 6
        assertEquals(6, byT[1352L])
        assertEquals(6, byT[1353L]) // Exact row 425 race sample is 6, never 4!
        assertEquals(6, byT[1354L]) // In-flight torn 5 upgraded to 6
        assertEquals(6, byT[1355L]) // UTC-only in-flight sample upgraded to 6
        assertEquals(7, byT[1360L]) // Pre-existing manual lap 7 untouched
        assertEquals(7, byT[1361L])

        for (i in 0 until orderedRows.size - 1) {
            assertTrue(
                "Non-monotonic LAP_NR at t=${orderedRows[i].first}->${orderedRows[i + 1].first}: ${orderedRows[i].second} > ${orderedRows[i + 1].second}",
                orderedRows[i].second <= orderedRows[i + 1].second
            )
        }
    }

    // REQ-TRK-096 / TST-TRK-096.2
    @Test
    fun multiTransitionWithManualLapDirectSwitchAndSessionIsolation_reconcilesMonotonically_tstTrk096_2() {
        val session1File = "2026-11-24_080000"
        val session2File = "2026-11-24_090000"
        val samplesDbMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        samplesDbMgr.createNewTable(session1File, listOf(SensorType.TIME_TOTAL, SensorType.LAP_NR))
        val samplesDb = samplesDbMgr.database
        val table1 = WorkoutSamplesDatabaseManager.getTableName(session1File)
        val repo = WorkoutRepository.getInstance(context)

        val coordinator = MultisportLapCoordinator { _, _, _ -> }
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)

        // Transition 1: Run (1) -> T1 (2) [600..640) -> Bike (3) [>=640] (simulate cold-resume null baseFileName!)
        val t1Event = MultisportTransitionEvent(
            fromSport = BSportType.RUN,
            toSport = BSportType.BIKE,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 600_000L, 600, 600, 2400.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 640_000L, 640, 640, 2450.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 655_000L, 655, 655, 2570.0)
        )
        coordinator.planAndCommitAnchorTransition(context, t1Event, 1, null)

        // Manual lap pressed at t=1200 -> Lap 4 starts
        val manualIntent = Intent(BANALService.LAP_SUMMARY)
            .putExtra(BANALService.PREV_LAP_NR, 3)
            .putExtra(BANALService.PREV_LAP_TIME_S, 560)
            .putExtra(BANALService.PREV_LAP_DISTANCE_m, 4480.0)
            .putExtra(BANALService.PREV_LAP_SPEED_mps, 8.0)
            .putExtra(BANALService.IS_PAUSE, false)
        coordinator.onLapSummaryIntent(
            manualIntent, 501L, sessionStartMs + 1200_000L, 1200, 1200, 6930.0,
            false, repo, { _, _, _, _ -> }, {}
        )

        // Transition 2: Bike (4) -> T2 (5) [1800..1850) -> Run (6) [>=1850] (with session1File)
        val t2Event = MultisportTransitionEvent(
            fromSport = BSportType.BIKE,
            toSport = BSportType.RUN,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 1800_000L, 1800, 1800, 11730.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 1850_000L, 1850, 1850, 11780.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 1865_000L, 1865, 1865, 11840.0)
        )
        coordinator.onMultisportTransitionIntent(
            context, MultisportTransitionBridge.toIntent(t2Event, context.packageName),
            mockBanalLapNr(4), { 501L }, { session1File }, { false }, repo, null, directExecutor, {}
        )

        // Transition 3: Direct <10s switch (hasTransition=false): Run (6) -> Bike (7) [>=2106]
        val t3Event = MultisportTransitionEvent(
            fromSport = BSportType.RUN,
            toSport = BSportType.BIKE,
            exitAnchor = MultisportTelemetryAnchor(sessionStartMs + 2100_000L, 2100, 2100, 12800.0),
            enterAnchor = MultisportTelemetryAnchor(sessionStartMs + 2106_000L, 2106, 2106, 12824.0),
            confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + 2121_000L, 2121, 2121, 12944.0)
        )
        coordinator.onMultisportTransitionIntent(
            context, MultisportTransitionBridge.toIntent(t3Event, context.packageName),
            mockBanalLapNr(6), { 501L }, { session1File }, { false }, repo, null, directExecutor, {}
        )

        // Verify enforceMonotonicLapNr across every segment (including manual lap 4 and direct <10s switch)
        val expectedByTimeAndRaw = listOf(
            Triple(599, 1, 1),
            Triple(620, 1, 2),
            Triple(900, 1, 3),
            Triple(1400, 4, 4), // Manual lap 4 preserved!
            Triple(1820, 4, 5),
            Triple(1865, 4, 6),
            Triple(2103, 6, 6), // Direct <10s switch: pre-enter stays 6
            Triple(2106, 6, 7)  // Direct <10s switch: >= enter becomes 7
        )
        for ((t, rawLap, expectedLap) in expectedByTimeAndRaw) {
            val cv = ContentValues().apply {
                put(SensorType.TIME_TOTAL.name, t)
                put(SensorType.LAP_NR.name, rawLap)
            }
            coordinator.enforceMonotonicLapNr(session1File, cv)
            assertEquals("Mismatch at t=$t (raw=$rawLap)", expectedLap, cv.getAsInteger(SensorType.LAP_NR.name))
        }

        // Seed stale rows directly into Session 1 DB covering BOTH the null-baseFileName T1 window and keyed T2/T3 windows
        listOf(620 to 1, 900 to 1, 1400 to 4, 1825 to 4, 1865 to 4, 2110 to 6).forEach { (tt, lap) ->
            samplesDb.insertOrThrow(table1, null, ContentValues().apply {
                put(SensorType.TIME_TOTAL.name, tt)
                put(SensorType.LAP_NR.name, lap)
            })
        }

        // Short-circuit on null / blank baseFileName must be a clean no-op
        coordinator.reconcileWorkoutSamplesOnFinalization(context, null)
        coordinator.reconcileWorkoutSamplesOnFinalization(context, "   ")

        // Mixed-window merge while Session 1 is active repairs BOTH T1 (null-scoped) and T2/T3 (keyed) rows!
        coordinator.reconcileWorkoutSamplesOnFinalization(context, session1File)
        val afterActiveReconcile = mutableMapOf<Int, Int>()
        samplesDb.rawQuery("SELECT ${SensorType.TIME_TOTAL.name}, ${SensorType.LAP_NR.name} FROM $table1", null).use { c ->
            while (c.moveToNext()) afterActiveReconcile[c.getInt(0)] = c.getInt(1)
        }
        assertEquals(2, afterActiveReconcile[620])
        assertEquals(3, afterActiveReconcile[900])
        assertEquals(4, afterActiveReconcile[1400])
        assertEquals(5, afterActiveReconcile[1825])
        assertEquals(6, afterActiveReconcile[1865])
        assertEquals(7, afterActiveReconcile[2110])

        // Rapid Stop -> Start isolation: re-commit a keyed Session 1 transition, start Session 2 via resetForSession,
        // verify Session 2 samples have ZERO cross-session bleed while Session 1's queued finalization STILL reconciles!
        val session1LateFile = "2026-11-24_083000"
        samplesDbMgr.createNewTable(session1LateFile, listOf(SensorType.TIME_TOTAL, SensorType.LAP_NR))
        val table1Late = WorkoutSamplesDatabaseManager.getTableName(session1LateFile)
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)
        coordinator.planAndCommitAnchorTransition(context, t2Event, 4, session1LateFile)
        samplesDb.insertOrThrow(table1Late, null, ContentValues().apply {
            put(SensorType.TIME_TOTAL.name, 1865)
            put(SensorType.LAP_NR.name, 4)
        })

        // Start Session 2 BEFORE Session 1's endWorkout reconciliation runs!
        coordinator.resetForSession(sessionStartMs + 3600_000L, 0, 0, 0.0)
        val session2Sample = ContentValues().apply {
            put(SensorType.TIME_TOTAL.name, 1900)
            put(SensorType.LAP_NR.name, 1)
        }
        coordinator.enforceMonotonicLapNr(session2File, session2Sample)
        assertEquals("Session 2 must NOT inherit Session 1 floor", 1, session2Sample.getAsInteger(SensorType.LAP_NR.name))

        // Session 1's queued endWorkout() still reconciles its own table via pendingReconciliationByFileName!
        coordinator.reconcileWorkoutSamplesOnFinalization(context, session1LateFile)
        samplesDb.rawQuery("SELECT ${SensorType.LAP_NR.name} FROM $table1Late WHERE ${SensorType.TIME_TOTAL.name} = 1865", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(6, c.getInt(0))
        }

        // Single-sport workout (no transitions for session2File) performs zero DB I/O cleanly
        coordinator.reconcileWorkoutSamplesOnFinalization(context, "nonexistent_single_sport_table")
    }

    // REQ-TRK-096 / TST-TRK-096.3
    @Test
    fun concurrentSamplingAndMultisportTransitions_zeroNonMonotonicAnomaliesAndZeroDeadlocks_tstTrk096_3() {
        val baseFileName = "2026-11-24_100000"
        val samplesDbMgr = WorkoutSamplesDatabaseManager.getInstance(context)
        samplesDbMgr.createNewTable(baseFileName, listOf(SensorType.TIME_TOTAL, SensorType.LAP_NR))
        val samplesDb = samplesDbMgr.database
        val table = WorkoutSamplesDatabaseManager.getTableName(baseFileName)
        val repo = WorkoutRepository.getInstance(context)

        val liveLapNr = AtomicInteger(1)
        val coordinator = MultisportLapCoordinator { count, _, _ ->
            // Simulate step-by-step BANALService.advanceLapsForMultisport with yield to expose torn reads!
            repeat(count) {
                liveLapNr.incrementAndGet()
                Thread.yield()
            }
        }
        coordinator.resetForSession(sessionStartMs, 0, 0, 0.0)

        val dbExecutor = Executors.newSingleThreadExecutor()
        val transitionTimes = listOf(100, 200, 300)
        val samplerDone = CountDownLatch(1)
        val transitionTriggers = transitionTimes.map { CountDownLatch(1) }
        val transitionEnqueued = transitionTimes.map { CountDownLatch(1) }
        val banalMock = mockk<BANALService.BANALServiceComm>(relaxed = true)
        every { banalMock.getBestSensorData(SensorType.LAP_NR) } answers {
            val v = liveLapNr.get()
            SensorData(SensorType.LAP_NR, v, "$v", "clock")
        }

        // Background sampler thread (simulates TrackerService mScheduler)
        val samplerThread = Thread {
            try {
                for (t in 1..350) {
                    val snapLap = liveLapNr.get()
                    val transIdx = transitionTimes.indexOf(t)
                    if (transIdx >= 0) {
                        // Signal transition at t == confirmT AFTER snapshotting stale oldLapNr,
                        // and wait until transition commits & enqueues backfill BEFORE enqueueing this sample!
                        transitionTriggers[transIdx].countDown()
                        transitionEnqueued[transIdx].await(5, TimeUnit.SECONDS)
                    }
                    val cv = ContentValues().apply {
                        put(SensorType.TIME_TOTAL.name, t)
                        put(SensorType.LAP_NR.name, snapLap)
                    }
                    dbExecutor.execute {
                        coordinator.enforceMonotonicLapNr(baseFileName, cv)
                        samplesDb.insertOrThrow(table, null, cv)
                    }
                }
            } finally {
                samplerDone.countDown()
            }
        }
        samplerThread.start()

        // Main-style thread dispatching 3 consecutive multisport transitions at their authentic confirmT
        val sports = listOf(
            BSportType.RUN to BSportType.BIKE,
            BSportType.BIKE to BSportType.RUN,
            BSportType.RUN to BSportType.BIKE
        )
        for ((idx, confirmT) in transitionTimes.withIndex()) {
            assertTrue("Sampler did not reach confirmT=$confirmT", transitionTriggers[idx].await(5, TimeUnit.SECONDS))
            val exitT = confirmT - 40
            val enterT = confirmT - 15
            val event = MultisportTransitionEvent(
                fromSport = sports[idx].first,
                toSport = sports[idx].second,
                exitAnchor = MultisportTelemetryAnchor(sessionStartMs + exitT * 1000L, exitT, exitT, exitT * 4.0),
                enterAnchor = MultisportTelemetryAnchor(sessionStartMs + enterT * 1000L, enterT, enterT, enterT * 4.0),
                confirmAnchor = MultisportTelemetryAnchor(sessionStartMs + confirmT * 1000L, confirmT, confirmT, confirmT * 4.0)
            )
            coordinator.onMultisportTransitionIntent(
                context = context,
                intent = MultisportTransitionBridge.toIntent(event, context.packageName),
                banalService = banalMock,
                workoutIdProvider = { 700L },
                baseFileNameProvider = { baseFileName },
                isStoppingProvider = { false },
                workoutRepository = repo,
                tableInitFuture = null,
                dbExecutor = dbExecutor,
                onDiscreteEdge = {}
            )
            transitionEnqueued[idx].countDown()
        }

        assertTrue("Sampler thread deadlocked or timed out", samplerDone.await(10, TimeUnit.SECONDS))
        dbExecutor.execute { coordinator.reconcileWorkoutSamplesOnFinalization(context, baseFileName) }
        dbExecutor.shutdown()
        assertTrue("DB executor deadlocked or timed out", dbExecutor.awaitTermination(10, TimeUnit.SECONDS))

        val rows = mutableListOf<Pair<Int, Int>>()
        samplesDb.rawQuery("SELECT ${SensorType.TIME_TOTAL.name}, ${SensorType.LAP_NR.name} FROM $table ORDER BY ${SensorType.TIME_TOTAL.name} ASC", null).use { c ->
            while (c.moveToNext()) rows.add(c.getInt(0) to c.getInt(1))
        }
        assertEquals(350, rows.size)
        for (i in 0 until rows.size - 1) {
            assertTrue(
                "Non-monotonic LAP_NR at t=${rows[i].first}->${rows[i + 1].first}: ${rows[i].second} > ${rows[i + 1].second}",
                rows[i].second <= rows[i + 1].second
            )
        }
        val byT = rows.toMap()
        assertEquals(1, byT[59])
        assertEquals(2, byT[70])
        assertEquals(3, byT[100])
        assertEquals(4, byT[170])
        assertEquals(5, byT[200])
        assertEquals(6, byT[270])
        assertEquals(7, byT[300])
        assertEquals(7, byT[350])
    }

    private fun parseUtcMs(utcText: String): Long {
        val ldt = LocalDateTime.parse(utcText.trim(), utcFormatter)
        return ldt.toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}
