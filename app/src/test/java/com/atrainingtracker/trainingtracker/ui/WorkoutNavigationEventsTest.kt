/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying event emission, sticky replay, and reset semantics
 * of [WorkoutNavigationEvents.navigateToWorkout] (ATT-2337 / REQ-MIG-032 / TST-MIG-029).
 */
class WorkoutNavigationEventsTest {

    @Before
    fun setUp() {
        WorkoutNavigationEvents.resetNavigateToWorkout()
    }

    @Test
    fun triggerNavigateToWorkout_emitsTargetWorkoutId() = runTest {
        WorkoutNavigationEvents.triggerNavigateToWorkout(12345L)
        val emittedId = WorkoutNavigationEvents.navigateToWorkout.first()
        assertEquals(12345L, emittedId)
    }

    @Test
    fun resetNavigateToWorkout_emitsNull() = runTest {
        WorkoutNavigationEvents.triggerNavigateToWorkout(42L)
        assertEquals(42L, WorkoutNavigationEvents.navigateToWorkout.first())

        WorkoutNavigationEvents.resetNavigateToWorkout()
        val emittedId = WorkoutNavigationEvents.navigateToWorkout.first()
        assertNull(emittedId)
    }
}
