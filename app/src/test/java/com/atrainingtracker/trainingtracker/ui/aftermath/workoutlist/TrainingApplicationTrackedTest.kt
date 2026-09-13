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
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.TrackingMode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Automated unit test suite verifying active workout tracking detection
 * and deletion protection invariants (REQ-UI-145, TST-UI-098, ATT-917).
 */
class TrainingApplicationTrackedTest {

    @Before
    fun setUp() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)
    }

    @After
    fun tearDown() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)
    }

    @Test
    fun testIsActivelyTracked_whenTrackingModeIsReady_returnsFalseForAnyId() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(100L)

        assertFalse("When mode is READY, workout 100 must not be actively tracked",
            TrainingApplication.isActivelyTracked(100L))
        assertFalse("When mode is READY, workout 101 must not be actively tracked",
            TrainingApplication.isActivelyTracked(101L))
        assertFalse("Negative ID must not be actively tracked",
            TrainingApplication.isActivelyTracked(-1L))
        assertFalse("Zero ID must not be actively tracked",
            TrainingApplication.isActivelyTracked(0L))
    }

    @Test
    fun testIsActivelyTracked_whenTrackingModeIsTracking_returnsTrueOnlyForActiveWorkoutId() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setActiveWorkoutIdForTesting(100L)

        assertTrue("When mode is TRACKING, active workout 100 must be actively tracked",
            TrainingApplication.isActivelyTracked(100L))
        assertFalse("When mode is TRACKING, different workout 101 must not be actively tracked",
            TrainingApplication.isActivelyTracked(101L))
        assertFalse("Negative ID must never be actively tracked",
            TrainingApplication.isActivelyTracked(-1L))
        assertFalse("Zero ID must never be actively tracked",
            TrainingApplication.isActivelyTracked(0L))
    }

    @Test
    fun testIsActivelyTracked_whenTrackingModeIsPaused_returnsTrueForActiveWorkoutId() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.PAUSED)
        TrainingApplication.setActiveWorkoutIdForTesting(200L)

        assertTrue("When mode is PAUSED, active workout 200 must be actively tracked",
            TrainingApplication.isActivelyTracked(200L))
        assertFalse("When mode is PAUSED, different workout 201 must not be actively tracked",
            TrainingApplication.isActivelyTracked(201L))
    }

    @Test
    fun testActiveWorkoutId_updatesAndResetsCorrectly() {
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setActiveWorkoutIdForTesting(500L)
        assertEquals(500L, TrainingApplication.getActiveWorkoutID())
        assertTrue(TrainingApplication.isActivelyTracked(500L))

        // Reset to idle
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)
        assertEquals(-1L, TrainingApplication.getActiveWorkoutID())
        assertFalse(TrainingApplication.isActivelyTracked(500L))
    }
}
