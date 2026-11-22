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
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.database.CardioPowerDynamicsRecord
import com.atrainingtracker.trainingtracker.database.ExtremaType
import com.atrainingtracker.trainingtracker.physiology.AerobicEfficiencyResult
import com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculator
import com.atrainingtracker.trainingtracker.physiology.PowerPhysiologyCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.PowerSpeedHrSamples
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSummaryUpdateData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [PostWorkoutPhysiologyRecomputer] (`REQ-TRK-093.3`, `TST-TRK-093.3`, `ATT-8124`).
 */
class PostWorkoutPhysiologyRecomputerTest {

    private lateinit var mockContext: Context
    private lateinit var mockRepo: WorkoutRepository

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockContext = mockk(relaxed = true)
        every { mockContext.applicationContext } returns mockContext

        mockRepo = mockk(relaxed = true)
        WorkoutRepository.resetForTesting(mockRepo)
    }

    @After
    fun tearDown() {
        WorkoutRepository.resetForTesting(null)
        unmockkAll()
    }

    @Test
    fun recomputeAfterHrBackfill_filtersNullableAndNonFiniteHr_resolvesSportType_andPersistsAllMetrics_TST_TRK_093_3() {
        val hrSamples = listOf(null, Double.NaN, 0.0, -5.0, Double.POSITIVE_INFINITY, 120.0, 150.0, 180.0)
        val powerSamples = listOf(200.0, 210.0, 220.0, 230.0, 240.0, 250.0, 260.0, 270.0)
        val speedSamples = listOf(8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0)

        every { mockRepo.getPowerSpeedHrSamples("ride_01") } returns PowerSpeedHrSamples(
            powerSamples = powerSamples,
            speedSamples = speedSamples,
            hrSamples = hrSamples
        )
        val mockSummaryUpdate = mockk<WorkoutSummaryUpdateData>(relaxed = true)
        every { mockSummaryUpdate.bSportType } returns BSportType.BIKE
        every { mockRepo.getWorkoutSummaryUpdateData("ride_01") } returns mockSummaryUpdate

        mockkStatic(PowerPhysiologyCalculator::class)
        every {
            PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
                powerSamples,
                speedSamples,
                hrSamples,
                BSportType.BIKE
            )
        } returns AerobicEfficiencyResult(
            efficiencyFactor = 1.65,
            decouplingPct = 3.2,
            efFirstHalf = 1.68,
            efSecondHalf = 1.62,
            isPowerBased = true,
            isHighDrift = false
        )

        val mockDynamics = mockk<CardioPowerDynamicsRecord>(relaxed = true)
        mockkStatic(PowerHrDynamicsCalculator::class)
        every {
            PowerHrDynamicsCalculator.calculate(powerSamples, hrSamples, 42L, 1.0, any())
        } returns mockDynamics

        val result = PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(mockContext, 42L, "ride_01")

        assertTrue("Recomputer must return true when HR extrema and physiology are persisted", result)
        // Only the 3 finite positive HR values (120, 150, 180) must be included, with count = 3
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.HR, ExtremaType.MIN, 120.0, null, 3) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.HR, ExtremaType.MAX, 180.0, null, 3) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.HR, ExtremaType.AVG, 150.0, null, 3) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.POWER, ExtremaType.EFFICIENCY_FACTOR, 1.65, null) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(42L, SensorType.POWER, ExtremaType.AEROBIC_DECOUPLING, 3.2, null) }
        verify(exactly = 1) { mockRepo.saveCardioPowerDynamics(42L, mockDynamics) }
    }

    @Test
    fun recomputeAfterHrBackfill_fallsBackToUnknownSportAndSpeedSensorForRunning_TST_TRK_093_3() {
        val hrSamples = listOf(140.0, 145.0)
        val speedSamples = listOf(3.2, 3.4)
        every { mockRepo.getPowerSpeedHrSamples("run_01") } returns PowerSpeedHrSamples(
            powerSamples = emptyList(),
            speedSamples = speedSamples,
            hrSamples = hrSamples
        )
        every { mockRepo.getWorkoutSummaryUpdateData("run_01") } returns null

        mockkStatic(PowerPhysiologyCalculator::class)
        every {
            PowerPhysiologyCalculator.computeAerobicEfficiencyAndDecoupling(
                emptyList(),
                speedSamples,
                hrSamples,
                BSportType.UNKNOWN
            )
        } returns AerobicEfficiencyResult(
            efficiencyFactor = 1.40,
            decouplingPct = null,
            efFirstHalf = 1.40,
            efSecondHalf = null,
            isPowerBased = false,
            isHighDrift = false
        )

        mockkStatic(PowerHrDynamicsCalculator::class)
        every { PowerHrDynamicsCalculator.calculate(emptyList(), hrSamples, 77L, 1.0, any()) } returns null

        val result = PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(mockContext, 77L, "run_01")

        assertTrue(result)
        verify(exactly = 1) { mockRepo.updateExtremaValue(77L, SensorType.HR, ExtremaType.MIN, 140.0, null, 2) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(77L, SensorType.HR, ExtremaType.MAX, 145.0, null, 2) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(77L, SensorType.HR, ExtremaType.AVG, 142.5, null, 2) }
        verify(exactly = 1) { mockRepo.updateExtremaValue(77L, SensorType.SPEED_mps, ExtremaType.EFFICIENCY_FACTOR, 1.40, null) }
        verify(exactly = 0) { mockRepo.updateExtremaValue(77L, SensorType.SPEED_mps, ExtremaType.AEROBIC_DECOUPLING, any(), any()) }
        verify(exactly = 0) { mockRepo.saveCardioPowerDynamics(any(), any()) }
    }

    @Test
    fun recomputeAfterHrBackfill_guardsInvalidInputsAndEmptySamples_TST_TRK_093_3() {
        assertFalse(PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(mockContext, 0L, "ride_01"))
        assertFalse(PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(mockContext, 10L, null))
        assertFalse(PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(mockContext, 10L, "   "))

        every { mockRepo.getPowerSpeedHrSamples("empty_01") } returns PowerSpeedHrSamples(
            powerSamples = emptyList(),
            speedSamples = emptyList(),
            hrSamples = listOf(null, 0.0, Double.NaN)
        )
        every { mockRepo.getWorkoutSummaryUpdateData("empty_01") } returns null

        assertFalse(PostWorkoutPhysiologyRecomputer.recomputeAfterHrBackfill(mockContext, 10L, "empty_01"))
        verify(exactly = 0) { mockRepo.updateExtremaValue(any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mockRepo.updateExtremaValue(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { mockRepo.saveCardioPowerDynamics(any(), any()) }
    }
}
