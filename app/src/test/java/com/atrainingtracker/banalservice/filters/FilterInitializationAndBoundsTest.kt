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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests verifying filter initialization state tracking, alpha bounds validation,
 * zero-bias elimination, and thread safety (REQ-FIL-015, TST-FIL-015, ATT-6382).
 */
class FilterInitializationAndBoundsTest {

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isPaused() } returns false
    }

    @After
    fun tearDown() {
        unmockkStatic(TrainingApplication::class)
    }

    /**
     * TST-FIL-015.1: Verifies that MaxValueFilter correctly seeds from the first non-paused
     * sample even for sub-zero (negative) values, resolving the 0.0-bias defect (REQ-FIL-015.1).
     */
    @Test
    fun maxValueFilter_subZeroAndFirstSampleSeeding_tracksTrueMaximum() {
        val filter = MaxValueFilter("test_device", SensorType.TEMPERATURE)
        assertFalse(filter.isInitialized)
        assertEquals(0, filter.filteredValue.toInt())

        // First non-paused sub-zero sample seeds directly (-5.0°C)
        filter.newValue(-5.0)
        assertTrue(filter.isInitialized)
        assertEquals(-5.0, filter.filteredValue.toDouble(), 0.001)

        // Warmer sub-zero sample updates maximum (-3.0°C > -5.0°C)
        filter.newValue(-3.0)
        assertEquals(-3.0, filter.filteredValue.toDouble(), 0.001)

        // Colder sample does not lower maximum (-8.0°C < -3.0°C)
        filter.newValue(-8.0)
        assertEquals(-3.0, filter.filteredValue.toDouble(), 0.001)

        // Positive sample updates maximum (10.0°C > -3.0°C)
        filter.newValue(10.0)
        assertEquals(10.0, filter.filteredValue.toDouble(), 0.001)

        // Null value must not alter state (REQ-FIL-011)
        filter.newValue(null)
        assertEquals(10.0, filter.filteredValue.toDouble(), 0.001)
    }

    /**
     * TST-FIL-015.2: Verifies that MaxValueFilter enforces session pause isolation,
     * resets clean state, and re-seeds cleanly on subsequent unpaused telemetry (REQ-FIL-015.1, REQ-FIL-013.3).
     */
    @Test
    fun maxValueFilter_pauseSessionIsolationAndReset_retainsCorrectState() {
        val filter = MaxValueFilter("test_device", SensorType.POWER)

        // In paused state: samples must not initialize or update maximum
        every { TrainingApplication.isPaused() } returns true
        filter.newValue(500)
        assertFalse(filter.isInitialized)
        assertEquals(0, filter.filteredValue.toInt())

        // Unpause tracking: first unpaused sample seeds maximum
        every { TrainingApplication.isPaused() } returns false
        filter.newValue(350)
        assertTrue(filter.isInitialized)
        assertEquals(350, filter.filteredValue.toInt())

        // Reset clears max value and uninitializes state
        filter.reset()
        assertFalse(filter.isInitialized)
        assertEquals(0, filter.filteredValue.toInt())

        // After reset: subsequent sub-zero sample seeds properly
        filter.newValue(-12.5)
        assertTrue(filter.isInitialized)
        assertEquals(-12.5, filter.filteredValue.toDouble(), 0.001)
    }

    /**
     * TST-FIL-015.3: Verifies that ExponentialSmoothingFilter constructor validates alpha
     * range (0.0, 1.0] and rejects non-finite or out-of-bound values (REQ-FIL-015.2).
     */
    @Test
    fun exponentialSmoothingFilter_alphaValidation_rejectsInvalidBounds() {
        val invalidAlphas = doubleArrayOf(
            0.0,
            -0.001,
            -1.0,
            1.0001,
            2.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        )

        for (alpha in invalidAlphas) {
            val ex = assertThrows(IllegalArgumentException::class.java) {
                ExponentialSmoothingFilter("test", SensorType.HR, alpha)
            }
            assertTrue(ex.message!!.contains("alpha must be in range (0.0, 1.0]"))
        }

        // Boundary valid values must succeed
        val minAlphaFilter = ExponentialSmoothingFilter("test", SensorType.HR, Double.MIN_VALUE)
        assertEquals(Double.MIN_VALUE, minAlphaFilter.filterConstant, 0.0)

        val fullWeightFilter = ExponentialSmoothingFilter("test", SensorType.HR, 1.0)
        assertEquals(1.0, fullWeightFilter.filterConstant, 0.0)

        val standardFilter = ExponentialSmoothingFilter("test", SensorType.HR, 0.5)
        assertEquals(0.5, standardFilter.filterConstant, 0.0)
    }

    /**
     * TST-FIL-015.4: Verifies that ExponentialSmoothingFilter seeds directly from the first
     * non-null sample without 0.0-bias and that reseed() cleanly updates baseline (REQ-FIL-015.3, REQ-FIL-012.5).
     */
    @Test
    fun exponentialSmoothingFilter_firstSampleSeedingAndReseed_eliminatesZeroBias() {
        val filter = ExponentialSmoothingFilter("test", SensorType.POWER, 0.5)
        assertFalse(filter.isInitialized)
        assertEquals(0.0, filter.filteredValue.toDouble(), 0.001)

        // First sample seeds directly: 100 W (not 50 W from 0.5 * 100 + 0.5 * 0)
        filter.newValue(100.0)
        assertTrue(filter.isInitialized)
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.001)

        // Second sample applies exponential smoothing: 0.5 * 150 + 0.5 * 100 = 125 W
        filter.newValue(150.0)
        assertEquals(125.0, filter.filteredValue.toDouble(), 0.001)

        // reseed() clears history and directly seeds new baseline (200 W)
        filter.reseed(200.0)
        assertTrue(filter.isInitialized)
        assertEquals(200.0, filter.filteredValue.toDouble(), 0.001)

        // Subsequent sample averages with new baseline: 0.5 * 300 + 0.5 * 200 = 250 W
        filter.newValue(300.0)
        assertEquals(250.0, filter.filteredValue.toDouble(), 0.001)

        // reset() clears filtered value and resets initialization flag
        filter.reset()
        assertFalse(filter.isInitialized)
        assertEquals(0.0, filter.filteredValue.toDouble(), 0.001)
    }

    /**
     * TST-FIL-015.5: Verifies concurrent thread safety for MaxValueFilter and
     * ExponentialSmoothingFilter under high-velocity multi-threaded mutation and queries (REQ-FIL-015.4).
     */
    @Test
    fun filters_concurrentMultiThreadedAccess_maintainsStateIntegrity() {
        val maxFilter = MaxValueFilter("thread_test", SensorType.POWER)
        val expFilter = ExponentialSmoothingFilter("thread_test", SensorType.POWER, 0.2)

        val threadCount = 8
        val iterationsPerThread = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (i in 0 until threadCount) {
            val threadId = i
            executor.execute {
                try {
                    startLatch.await()
                    for (j in 0 until iterationsPerThread) {
                        val sample = (threadId * 100 + j).toDouble()
                        maxFilter.newValue(sample)
                        expFilter.newValue(sample)

                        val maxVal = maxFilter.filteredValue
                        val expVal = expFilter.filteredValue
                        if (maxVal == null || expVal == null) {
                            errorCount.incrementAndGet()
                        }

                        if (j % 100 == 0) {
                            maxFilter.isInitialized
                            expFilter.isInitialized
                        }
                    }
                } catch (t: Throwable) {
                    errorCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals(0, errorCount.get())
        assertTrue(maxFilter.isInitialized)
        assertTrue(expFilter.isInitialized)
    }
}
