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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [LiveWorkoutBroadcastTracker] lifecycle-edge detection and 1-Hz broadcast suppression.
 *
 * Traceability:
 * - REQ-STB-020.1 / TST-STB-020.1: Zero steady-state active/paused WORKOUT_UPDATED_INTENT broadcasts.
 * - REQ-STB-020.2 / TST-STB-020.1: Single discrete broadcasts on session init/resume, PAUSE<->TRACKING,
 *   live lap, and sport changes.
 */
class LiveWorkoutBroadcastTrackerTest {

    @Before
    fun setUp() {
        LiveWorkoutCardStateStore.Default.clear()
    }

    @After
    fun tearDown() {
        LiveWorkoutCardStateStore.Default.clear()
    }

    @Test
    fun onSessionInitialized_dispatchesOnceAndSuppressesSteadyStateTicks_TST_STB_020_1() {
        val dispatchedIds = mutableListOf<Long>()
        val tracker = LiveWorkoutBroadcastTracker { workoutId -> dispatchedIds.add(workoutId) }

        tracker.onSessionInitialized(workoutId = 101L, isPaused = false)
        assertEquals(listOf(101L), dispatchedIds)

        repeat(3600) {
            assertFalse(tracker.onPauseStateTick(workoutId = 101L, isPaused = false))
        }
        assertEquals(listOf(101L), dispatchedIds)
    }

    @Test
    fun onPauseStateTick_emitsOnlyOnStateTransitionEdges_TST_STB_020_1() {
        val dispatchedIds = mutableListOf<Long>()
        val tracker = LiveWorkoutBroadcastTracker { workoutId -> dispatchedIds.add(workoutId) }

        // Initial tick without prior onSessionInitialized seeds baseline without emitting.
        assertFalse(tracker.onPauseStateTick(workoutId = 55L, isPaused = true))
        repeat(60) {
            assertFalse(tracker.onPauseStateTick(workoutId = 55L, isPaused = true))
        }
        assertTrue(dispatchedIds.isEmpty())

        // PAUSED -> TRACKING transition emits once.
        assertTrue(tracker.onPauseStateTick(workoutId = 55L, isPaused = false))
        assertEquals(listOf(55L), dispatchedIds)

        // Steady active ticks emit zero broadcasts.
        repeat(60) {
            assertFalse(tracker.onPauseStateTick(workoutId = 55L, isPaused = false))
        }
        assertEquals(listOf(55L), dispatchedIds)

        // TRACKING -> PAUSED transition emits once.
        assertTrue(tracker.onPauseStateTick(workoutId = 55L, isPaused = true))
        assertEquals(listOf(55L, 55L), dispatchedIds)
    }

    @Test
    fun onDiscreteEdge_andInvalidWorkoutIds_andReset_behaveSafely_TST_STB_020_1() {
        val dispatchedIds = mutableListOf<Long>()
        val tracker = LiveWorkoutBroadcastTracker { workoutId -> dispatchedIds.add(workoutId) }

        tracker.onSessionInitialized(workoutId = 0L, isPaused = false)
        tracker.onSessionInitialized(workoutId = -1L, isPaused = false)
        tracker.onDiscreteEdge(workoutId = 0L)
        assertFalse(tracker.onPauseStateTick(workoutId = -1L, isPaused = true))
        assertTrue(dispatchedIds.isEmpty())

        tracker.onSessionInitialized(workoutId = 77L, isPaused = false)
        tracker.onDiscreteEdge(workoutId = 77L)
        assertEquals(listOf(77L, 77L), dispatchedIds)
        // Pause baseline remains intact after discrete lap/sport edge.
        assertFalse(tracker.onPauseStateTick(workoutId = 77L, isPaused = false))

        tracker.reset()
        // Re-observing after reset seeds cleanly without stale mismatch emission.
        assertFalse(tracker.onPauseStateTick(workoutId = 77L, isPaused = true))
        assertEquals(listOf(77L, 77L), dispatchedIds)
    }
}
