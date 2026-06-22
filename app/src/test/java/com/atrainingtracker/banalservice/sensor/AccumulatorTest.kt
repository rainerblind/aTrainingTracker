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

package com.atrainingtracker.banalservice.sensor

import android.util.Log
import com.atrainingtracker.trainingtracker.TrainingApplication
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AccumulatorTest {

    @Before
    fun setUp() {
        mockkStatic(TrainingApplication::class)
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testPauseDiscardLogic() {
        // Create a sensor that respects pause (e.g., Distance)
        val sensor = MyDoubleAccumulatorSensor(null, SensorType.DISTANCE_m, true)

        // 1. Initial connection (Baseline)
        every { TrainingApplication.isPaused() } returns false
        sensor.accumulate(1000.0) 
        assertEquals("Baseline should not increase distance", 0.0, sensor.value, 0.001)

        // 2. Active movement
        sensor.accumulate(1100.0) // +100m
        assertEquals("Active movement should be added", 100.0, sensor.value, 0.001)

        // 3. Pause State
        every { TrainingApplication.isPaused() } returns true
        sensor.accumulate(1150.0) // +50m while paused
        assertEquals("Movement during pause should be discarded", 100.0, sensor.value, 0.001)

        // 4. Resume State
        every { TrainingApplication.isPaused() } returns false
        sensor.accumulate(1160.0) // +10m since resume
        assertEquals("Only post-resume movement should be added", 110.0, sensor.value, 0.001)
    }

    @Test
    fun testRespectPauseFalse() {
        // Create a sensor that ignores pause (e.g., Total Session Time)
        val sensor = MyDoubleAccumulatorSensor(null, SensorType.TIME_TOTAL, false)

        // 1. Initial connection (Baseline)
        every { TrainingApplication.isPaused() } returns false
        sensor.accumulate(10.0)
        assertEquals(0.0, sensor.value, 0.001)

        // 2. Movement during pause
        every { TrainingApplication.isPaused() } returns true
        sensor.accumulate(100.0) // +90
        assertEquals("When respectPause is false, movement should be added even if paused", 90.0, sensor.value, 0.001)
    }

    @Test
    fun testIncrementDirectly() {
        val sensor = MyDoubleAccumulatorSensor(null, SensorType.DISTANCE_m, true)
        
        // 1. Active increment
        every { TrainingApplication.isPaused() } returns false
        sensor.increment(10.0)
        assertEquals(10.0, sensor.value, 0.001)

        // 2. Paused increment
        every { TrainingApplication.isPaused() } returns true
        sensor.increment(5.0)
        assertEquals("Direct increment should be blocked during pause if respectPause is true", 10.0, sensor.value, 0.001)
    }
}
