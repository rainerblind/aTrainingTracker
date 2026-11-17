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

package com.atrainingtracker.trainingtracker.integration

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.MyUnits
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.climbs.ClimbCategory
import com.atrainingtracker.trainingtracker.climbs.ClimbDetector
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseAudioNotifier
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseConfig
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager
import com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseState
import com.atrainingtracker.trainingtracker.tracker.autopause.SoundCue
import com.atrainingtracker.trainingtracker.ui.map.PathPoint
import com.google.android.gms.maps.model.LatLng
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.exp

/**
 * Software Integration Verification Test: Domain Calculators & Algorithms (ASPICE SWE.5,
 * INT-SWE-05, IF-SWE-05, REQ-PRO-111.1, TST-INT-013, ATT-8156).
 *
 * Verifies real cross-component domain pipelines using synthetic time-series and spatial fixtures:
 * 1. AutoPauseManager state machine debounce & hysteresis transitions.
 * 2. PowerPhysiologyCalculator (NP, CP/W', W'bal) & PowerHrDynamicsCalculator (tau, HR@200W, cardiac drift).
 * 3. WorkoutClusterEngine (apex detection + route cluster matching) & ClimbDetector elevation segmentation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DomainCalculatorsIntegrationTest {

    private class RecordingAudioNotifier : AutoPauseAudioNotifier {
        val tones = mutableListOf<SoundCue>()
        override fun playTone(soundCue: SoundCue) {
            tones.add(soundCue)
        }
        override fun release() {}
    }

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TrainingApplication.setAppContextForTesting(context)
        TrainingApplication.setSharedPreferencesForTesting(
            PreferenceManager.getDefaultSharedPreferences(context)
        )
        TrainingApplication.setUnitForTesting(MyUnits.METRIC)
        TrainingApplication.setManualPauseLock(false, null)
        TrainingApplication.setAutoPaused(false, null)

        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        WorkoutClusterEngine.resetForTesting(null)
        TrainingApplication.setUnitForTesting(null)
        TrainingApplication.setAppContextForTesting(null)
        TrainingApplication.setSharedPreferencesForTesting(null)
        unmockkAll()
        context.deleteDatabase("WorkoutClusters.db")
        context.deleteDatabase("Routes.db")
        context.deleteDatabase("WorkoutSummaries.db")
    }

    /**
     * TST-INT-013.1: Verifies AutoPauseManager debounce pause and motion resume state transitions
     * across synthetic velocity time-series ticks.
     */
    @Test
    fun testAutoPauseManager_velocityTimeSeries_pauseAndResumeTransitions() {
        val notifier = RecordingAudioNotifier()
        val manager = AutoPauseManager(notifier)
        val config = AutoPauseConfig(
            bikeEnabled = true,
            bikeSpeedThreshold = 5, // 5 km/h (~1.39 m/s)
            bikeDebounceSec = 2,
            bikeResumeDebounceSec = 2
        )

        val t0 = 100_000L
        // Moving at 6.0 m/s -> remains TRACKING
        val moving = manager.evaluateTick(6.0, t0, t0, BSportType.BIKE, config, false)
        assertEquals(AutoPauseState.TRACKING, moving.newState)
        assertFalse(moving.triggerPause)

        // Two slow ticks (0.2 m/s) -> PAUSE_DEBOUNCE_PENDING -> AUTO_PAUSED
        val slow1 = manager.evaluateTick(0.2, t0 + 1000L, t0 + 1000L, BSportType.BIKE, config, false)
        assertEquals(AutoPauseState.PAUSE_DEBOUNCE_PENDING, slow1.newState)
        assertFalse(slow1.triggerPause)

        val slow2 = manager.evaluateTick(0.2, t0 + 2000L, t0 + 2000L, BSportType.BIKE, config, false)
        assertEquals(AutoPauseState.AUTO_PAUSED, slow2.newState)
        assertTrue(slow2.triggerPause)
        assertEquals(listOf(SoundCue.AUTO_PAUSE), notifier.tones)

        // Two fast ticks (7.0 m/s) -> stays AUTO_PAUSED during debounce -> TRACKING
        val fast1 = manager.evaluateTick(7.0, t0 + 3000L, t0 + 3000L, BSportType.BIKE, config, false)
        assertEquals(AutoPauseState.AUTO_PAUSED, fast1.newState)
        assertFalse(fast1.triggerResume)

        val fast2 = manager.evaluateTick(7.0, t0 + 4000L, t0 + 4000L, BSportType.BIKE, config, false)
        assertEquals(AutoPauseState.TRACKING, fast2.newState)
        assertTrue(fast2.triggerResume)
        assertEquals(listOf(SoundCue.AUTO_PAUSE, SoundCue.AUTO_RESUME), notifier.tones)
    }

    /**
     * TST-INT-013.2: Verifies PowerPhysiologyCalculator (NP, CP/W', W'bal) and PowerHrDynamicsCalculator
     * across a 1-hour synthetic interval workout with injected cardiac drift.
     */
    @Test
    fun testPhysiologyAndPowerHrCalculators_syntheticWorkoutSeries() {
        val cpEstimate = PowerPhysiologyCalculator.estimateCpAndWPrime(
            p1Watts = 420.0,
            t1Seconds = 180,
            p2Watts = 320.0,
            t2Seconds = 720
        )
        assertNotNull(cpEstimate)
        assertEquals(286.67, cpEstimate!!.cpWatts, 0.1)
        assertEquals(24.0, cpEstimate.wPrimeKj, 0.1)

        // 1-hour 1-Hz interval series with 1st-order HR dynamics + 15 bpm cardiac drift
        val n = 3600
        val tau = 35.0
        val alpha = exp(-1.0 / tau)
        var filteredPower = 160.0
        val powerSamples = ArrayList<Double>(n)
        val hrSamples = ArrayList<Double>(n)

        for (i in 0 until n) {
            val p = if ((i / 240) % 2 == 0) 160.0 else 310.0
            filteredPower = alpha * filteredPower + (1.0 - alpha) * p
            val drift = if (i > 1800) ((i - 1800).toDouble() / (n - 1800)) * 15.0 else 0.0
            val hr = 82.0 + 0.22 * filteredPower + drift
            powerSamples.add(p)
            hrSamples.add(hr)
        }

        val np = PowerPhysiologyCalculator.calculateNormalizedPower(powerSamples, sampleIntervalSec = 1.0)
        assertNotNull(np)
        val meanPower = powerSamples.average()
        assertTrue("Normalized power ($np) must exceed arithmetic mean ($meanPower) for variable intervals", np!! > meanPower)

        val wPrimeResult = PowerPhysiologyCalculator.computeWPrimeBalanceSeries(
            powerSamplesWatts = powerSamples,
            cpWatts = cpEstimate.cpWatts,
            wPrimeKj = cpEstimate.wPrimeKj
        )
        assertEquals(n, wPrimeResult.seriesKj.size)
        assertTrue("Supra-CP intervals must deplete W'bal below initial 24 kJ", (wPrimeResult.minWPrimeBalKj ?: 99.0) < 24.0)

        val dynamics = PowerHrDynamicsCalculator.calculate(powerSamples, hrSamples, workoutId = 77L)
        assertNotNull("PowerHrDynamicsCalculator must identify dynamics from 1h series", dynamics)
        assertEquals(77L, dynamics!!.workoutId)
        assertTrue("R^2 must indicate clean model fit (>= 0.90)", dynamics.r2Start >= 0.90)
        assertTrue("Injected cardiac drift must produce positive deltaHr200", (dynamics.deltaHr200 ?: -1.0) > 4.0)
    }

    /**
     * TST-INT-013.3: Verifies WorkoutClusterEngine apex & route cluster matching alongside
     * ClimbDetector elevation segmentation on a synthetic loop track with a Cat-4 climb.
     */
    @Test
    fun testSpatialRouteAndClimbCalculators_syntheticTrack() {
        val engine = WorkoutClusterEngine.getInstance(context)
        val start = LatLng(48.1371, 11.5761)
        val midClimb = LatLng(48.1460, 11.5840)
        val summit = LatLng(48.1550, 11.5920)
        val end = LatLng(48.1373, 11.5762)

        val path = (0..20).map { idx ->
            val f = idx / 20.0
            PathPoint(
                distance = f * 2000.0,
                latLng = LatLng(start.latitude + f * (summit.latitude - start.latitude), start.longitude + f * (summit.longitude - start.longitude)),
                altitude = 500.0 + f * 120.0
            )
        }

        val apex = engine.findApexFromPoints(start, listOf(start, midClimb, summit, end), end)
        assertEquals(summit.latitude, apex.latitude, 0.0001)

        val storedCluster = WorkoutCluster(
            id = 501L,
            name = "South Loop Climb",
            probableSportId = 1L,
            startLat = start.latitude,
            startLng = start.longitude,
            endLat = end.latitude,
            endLng = end.longitude,
            maxDispLat = summit.latitude,
            maxDispLng = summit.longitude,
            refDistance = 4200.0,
            hitCount = 4,
            bSportType = BSportType.BIKE
        )
        val suggested = engine.suggestCluster(
            start = start,
            end = end,
            apex = apex,
            distance = 4250.0,
            candidateSportTypes = setOf(BSportType.BIKE),
            cachedClusters = listOf(storedCluster)
        )
        assertNotNull("Matching loop route must match stored WorkoutCluster", suggested)
        assertEquals(501L, suggested!!.id)

        val climbs = ClimbDetector.detectClimbs(path)
        assertEquals(1, climbs.size)
        assertEquals(ClimbCategory.CAT_4, climbs[0].category)
        assertEquals(120.0, climbs[0].elevationGainMeters, 1.0)
        assertEquals(6.0, climbs[0].avgGradePercent, 0.1)
    }
}
