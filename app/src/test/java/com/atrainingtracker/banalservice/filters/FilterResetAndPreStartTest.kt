/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.banalservice.filters

import com.atrainingtracker.banalservice.sensor.SensorType
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying continuous rolling smoothing telemetry prior to tracking start,
 * session isolation for cumulative aggregators, and lifecycle reset contracts
 * (REQ-FIL-013, TST-FIL-005, ATT-3222).
 */
class FilterResetAndPreStartTest {

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        // Default to paused state (pre-start / ready mode or during pause)
        every { TrainingApplication.isPaused() } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(TrainingApplication::class)
    }

    @Test
    fun exponentialSmoothingFilter_shouldProcessSamplesContinuouslyWhenPaused() {
        val filter = ExponentialSmoothingFilter("test", SensorType.HR, 0.5)

        // Pre-start / paused mode: should update value continuously
        filter.newValue(100.0)
        assertEquals(50.0, filter.filteredValue.toDouble(), 0.01)

        filter.newValue(150.0)
        // 0.5 * 150 + 0.5 * 50 = 100.0
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.01)

        // Passing null should not mutate or crash (REQ-FIL-011)
        filter.newValue(null)
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.01)

        // Verify reset clears filtered value
        filter.reset()
        assertEquals(0.0, filter.filteredValue.toDouble(), 0.01)
    }

    @Test
    fun timedMovingAverageFilter_shouldProcessSamplesContinuouslyWhenPaused() {
        val filter = TimedMovingAverageFilter("test", SensorType.POWER, 10)

        // In pre-start / paused mode: should compute moving average
        filter.newValue(200)
        assertEquals(200.0, filter.filteredValue.toDouble(), 0.01)

        filter.newValue(300)
        assertEquals(250.0, filter.filteredValue.toDouble(), 0.01)

        // Null should not corrupt average
        filter.newValue(null)
        assertEquals(250.0, filter.filteredValue.toDouble(), 0.01)

        // Reset should clear buffer
        filter.reset()
        assertNull(filter.filteredValue)
    }

    @Test
    fun numberedMovingAverageFilter_shouldProcessSamplesContinuouslyWhenPausedAndMaintainBoundedWindow() {
        val filter = NumberedMovingAverageFilter("test", SensorType.SPEED_mps, 3)

        // In pre-start / paused mode: should compute moving average over window
        filter.newValue(10.0)
        assertEquals(10.0, filter.filteredValue.toDouble(), 0.01)

        filter.newValue(20.0)
        assertEquals(15.0, filter.filteredValue.toDouble(), 0.01)

        filter.newValue(30.0)
        assertEquals(20.0, filter.filteredValue.toDouble(), 0.01)

        // 4th sample: ring buffer replaces oldest (10.0) with 40.0 -> [40, 20, 30] -> avg = 30.0
        filter.newValue(40.0)
        assertEquals(30.0, filter.filteredValue.toDouble(), 0.01)

        // Null should not mutate
        filter.newValue(null)
        assertEquals(30.0, filter.filteredValue.toDouble(), 0.01)

        // Reset should clear values
        filter.reset()
        assertNull(filter.filteredValue)
    }

    @Test
    fun averageFilter_shouldDiscardSamplesWhenPaused_andAccumulateOnlyWhenTracking() {
        val filter = AverageFilter("test", SensorType.CADENCE)

        // Pre-start / paused: samples must be ignored to protect session stats
        filter.newValue(90)
        filter.newValue(95)
        assertNull(filter.filteredValue)

        // Active workout tracking starts
        every { TrainingApplication.isPaused() } returns false
        filter.newValue(80)
        assertEquals(80.0, filter.filteredValue.toDouble(), 0.01)

        filter.newValue(100)
        assertEquals(90.0, filter.filteredValue.toDouble(), 0.01)

        // Pause occurs: subsequent samples ignored
        every { TrainingApplication.isPaused() } returns true
        filter.newValue(200)
        assertEquals(90.0, filter.filteredValue.toDouble(), 0.01)

        // Reset flushes accumulated session stats
        filter.reset()
        assertNull(filter.filteredValue)
    }

    @Test
    fun maxValueFilter_shouldDiscardSamplesWhenPaused_andTrackMaxOnlyWhenTracking() {
        val filter = MaxValueFilter("test", SensorType.POWER)

        // Pre-start / paused: max value must not update
        filter.newValue(500)
        assertEquals(0, filter.filteredValue.toInt())

        // Active workout tracking starts
        every { TrainingApplication.isPaused() } returns false
        filter.newValue(350)
        assertEquals(350, filter.filteredValue.toInt())

        filter.newValue(450)
        assertEquals(450, filter.filteredValue.toInt())

        filter.newValue(400)
        assertEquals(450, filter.filteredValue.toInt())

        // Paused: higher sample ignored
        every { TrainingApplication.isPaused() } returns true
        filter.newValue(900)
        assertEquals(450, filter.filteredValue.toInt())

        // Reset flushes max
        filter.reset()
        assertEquals(0, filter.filteredValue.toInt())
    }

    @Test
    fun statelessFilter_resetShouldBeSafeNoOp() {
        val filter = InstantaneousFilter<Number>("test", SensorType.HR)
        filter.reset() // should execute cleanly without error
    }
}
