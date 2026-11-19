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
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Robolectric unit tests for [MultisportLapCoordinator] covering retroactive named laps,
 * UTC `TIME_START` formatting, auto-pause `T2` contiguous `LAP_NR` pre-increment checks,
 * manual-lap clamping, and asynchronous `WorkoutSamples.db` `LAP_NR` backfilling
 * (`ATT-8917`, `REQ-FIL-031.3`, `TST-FIL-031.3`).
 */
// Linked test specs: TST-FIL-031
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

    private fun parseUtcMs(utcText: String): Long {
        val ldt = LocalDateTime.parse(utcText.trim(), utcFormatter)
        return ldt.toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}
