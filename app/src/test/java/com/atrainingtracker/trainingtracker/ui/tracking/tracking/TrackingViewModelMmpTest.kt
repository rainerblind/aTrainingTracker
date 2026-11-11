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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import android.app.Application
import com.atrainingtracker.banalservice.filters.FilterType
import com.atrainingtracker.banalservice.filters.FilteredSensorData
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository
import com.atrainingtracker.trainingtracker.repositories.RoutesRepository
import com.atrainingtracker.trainingtracker.segments.LiveSegmentsRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.tracking.MmpBenchmark
import com.atrainingtracker.trainingtracker.ui.tracking.TrackingViewsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit and contract tests for [TrackingViewModel] MMP curve telemetry extraction,
 * historical PR benchmark integration, and screen state wiring conforming to REQ-TRK-058.6 and TST-TRK-058.6c.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackingViewModelMmpTest {

    private lateinit var viewModel: TrackingViewModel
    private val trackingViewsRepo = mockk<TrackingViewsRepository>(relaxed = true)
    private val banalServiceRepo = mockk<BANALServiceRepository>(relaxed = true)
    private val liveSegmentsRepo = mockk<LiveSegmentsRepository>(relaxed = true)
    private val routesRepo = mockk<RoutesRepository>(relaxed = true)
    private val workoutRepo = mockk<WorkoutRepository>(relaxed = true)

    private val benchmarkPRs = mapOf(
        5 to 600.0,
        60 to 400.0,
        300 to 300.0,
        1200 to 250.0
    )

    @Before
    fun setUp() {
        val app = mockk<Application>(relaxed = true)
        every { workoutRepo.getHistoricalPowerBenchmarks() } returns benchmarkPRs

        viewModel = TrackingViewModel(
            application = app,
            trackingViewsRepository = trackingViewsRepo,
            banalServiceRepository = banalServiceRepo,
            liveSegmentsRepository = liveSegmentsRepo,
            routesRepository = routesRepo,
            viewId = 1L,
            workoutRepository = workoutRepo
        )
    }

    private fun createData(sensorType: SensorType, value: Double): FilteredSensorData<Double> {
        return FilteredSensorData(
            sensorType,
            value,
            value.toInt().toString(),
            "PowerMeter",
            FilterType.INSTANTANEOUS,
            1.0
        )
    }

    @Test
    fun extractMmpCurveData_correctlyExtractsRollingMeansAndPeaks() {
        val sensorDataList = listOf(
            createData(SensorType.POWER, 280.0),
            createData(SensorType.POWER_5S, 520.0),
            createData(SensorType.POWER_1M, 380.0),
            createData(SensorType.POWER_5M, 310.0),
            createData(SensorType.POWER_20M, 260.0),
            createData(SensorType.POWER_PEAK_5S, 650.0),
            createData(SensorType.POWER_PEAK_1M, 410.0),
            createData(SensorType.POWER_PEAK_5M, 320.0),
            createData(SensorType.POWER_PEAK_20M, 270.0)
        )

        val curveData = viewModel.extractMmpCurveData(sensorDataList, benchmarkPRs)

        assertEquals(280.0, curveData.instantaneousPower!!, 0.001)

        // Rolling means
        assertEquals(520.0, curveData.rollingMeans[5]!!, 0.001)
        assertEquals(380.0, curveData.rollingMeans[60]!!, 0.001)
        assertEquals(310.0, curveData.rollingMeans[300]!!, 0.001)
        assertEquals(260.0, curveData.rollingMeans[1200]!!, 0.001)

        // Session peaks
        assertEquals(650.0, curveData.peakPowers[5]!!, 0.001)
        assertEquals(410.0, curveData.peakPowers[60]!!, 0.001)
        assertEquals(320.0, curveData.peakPowers[300]!!, 0.001)
        assertEquals(270.0, curveData.peakPowers[1200]!!, 0.001)

        // Reference PRs preserved
        assertEquals(4, curveData.referencePoints.size)
        assertEquals(600.0, curveData.referencePoints[5]!!, 0.001)

        // All 4 benchmark peaks exceed PRs in this fixture -> 4 breakthroughs
        val breakthroughs = curveData.getBreakthroughs()
        assertEquals(4, breakthroughs.size)

        val b5s = breakthroughs.first { it.benchmark == MmpBenchmark.SPRINT_5S }
        assertEquals(50.0, b5s.deltaWatts, 0.001)
    }

    @Test
    fun onUpdateShowMmpCurve_delegatesToRepository() = runTest {
        viewModel.onUpdateShowMmpCurve(true)
        coVerify { trackingViewsRepo.updateShowMmpCurve(1L, true) }

        viewModel.onUpdateShowMmpCurve(false)
        coVerify { trackingViewsRepo.updateShowMmpCurve(1L, false) }
    }

    @Test
    fun trackingScreenState_defaultsPreserveInvariants() {
        val state = TrackingScreenState()
        assertFalse(state.showMmpCurve)
        assertNotNull(state.mmpCurveData)
        assertTrue(state.mmpCurveData.peakPowers.isEmpty())
        assertTrue(state.mmpReferencePoints.isEmpty())
    }
}
