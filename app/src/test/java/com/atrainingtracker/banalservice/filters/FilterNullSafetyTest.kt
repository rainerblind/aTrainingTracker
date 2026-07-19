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
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class FilterNullSafetyTest {

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        every { TrainingApplication.isPaused() } returns false
    }

    @After
    fun tearDown() {
        unmockkStatic(TrainingApplication::class)
    }

    @Test
    fun averageFilter_shouldHandleNullValue() {
        val filter = AverageFilter("test", SensorType.HR)
        
        // This should not crash
        filter.newValue(null)
        
        // Value should remain null if no valid data was added
        assertNull(filter.filteredValue)
        
        // Valid value should work
        filter.newValue(100)
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.01)
        
        // Subsequent null should not crash and not affect average
        filter.newValue(null)
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.01)
    }

    @Test
    fun exponentialSmoothingFilter_shouldHandleNullValue() {
        val filter = ExponentialSmoothingFilter("test", SensorType.HR, 0.5)
        
        // This should not crash
        filter.newValue(null)
        
        // Valid value should work
        filter.newValue(100)
        assertEquals(50.0, filter.filteredValue.toDouble(), 0.01) // 0.5 * 100 + 0.5 * 0
        
        // Subsequent null should not crash
        filter.newValue(null)
        assertEquals(50.0, filter.filteredValue.toDouble(), 0.01)
    }

    @Test
    fun numberedMovingAverageFilter_shouldHandleNullValue() {
        val filter = NumberedMovingAverageFilter("test", SensorType.HR, 2)
        
        // This should not crash
        filter.newValue(null)
        assertNull(filter.filteredValue)
        
        filter.newValue(100)
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.01)
        
        filter.newValue(null)
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.01)
        
        filter.newValue(200)
        assertEquals(150.0, filter.filteredValue.toDouble(), 0.01)
    }

    @Test
    fun timedMovingAverageFilter_shouldHandleNullValue() {
        val filter = TimedMovingAverageFilter("test", SensorType.HR, 10)
        
        // This should not crash
        filter.newValue(null)
        assertNull(filter.filteredValue)
        
        filter.newValue(100)
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.01)
        
        filter.newValue(null)
        assertEquals(100.0, filter.filteredValue.toDouble(), 0.01)
    }
}
