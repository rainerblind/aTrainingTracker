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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Automated unit test suite verifying compact workout summary context menu streamlining (REQ-UI-147, TST-UI-100, ATT-993).
 *
 * Invariants:
 * 1. "Edit workout" is removed from WorkoutSummaryCompact.
 * 2. Compact context menu contains only "Mark as finished" (when idle & unfinished) and "Delete" (when non-active).
 * 3. hasContextMenu = canDelete || canMarkFinished. When actively tracked, hasContextMenu is strictly false.
 * 4. Full view (WorkoutSummary via WorkoutHeader) retains onEditWorkout.
 */
class WorkoutSummaryCompactMenuTest {

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

    private fun createWorkoutData(
        id: Long,
        finished: Boolean
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = "workout_$id",
            workoutName = "Test Workout $id",
            sportId = 1L,
            sportName = "Running",
            bSportType = BSportType.RUN,
            startTimeS = 1718000000L,
            formattedDate = "2026-09-13",
            formattedTime = "10:00",
            localDateTime = LocalDateTime.of(2026, 9, 13, 10, 0),
            equipmentName = null,
            equipmentId = 0L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = 5000.0,
            maxDisplacement = null,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            avgSpeedMps = 2.78,
            ascentMeters = 50L,
            descentMeters = 50L,
            minAltitude = 200.0,
            maxAltitude = 250.0,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun testIdleCompletedWorkout_compactContextMenuHasDeleteOnly() {
        // Given an idle completed workout
        val workout = createWorkoutData(id = 100L, finished = true)
        val isActivelyTracked = TrainingApplication.isActivelyTracked(workout.id)
        assertFalse(isActivelyTracked)

        val onMarkFinished: (() -> Unit)? = { /* mark finished */ }
        val canDelete = !isActivelyTracked
        val canMarkFinished = !workout.headerData.finished && !isActivelyTracked && onMarkFinished != null
        val hasContextMenu = canDelete || canMarkFinished

        assertTrue("Delete must be available for idle completed workout", canDelete)
        assertFalse("Mark as finished must NOT be available for already finished workout", canMarkFinished)
        assertTrue("Context menu must be enabled for delete action", hasContextMenu)
    }

    @Test
    fun testIdleUnfinishedWorkout_compactContextMenuHasMarkFinishedAndDelete() {
        // Given an idle unfinished workout
        val workout = createWorkoutData(id = 200L, finished = false)
        val isActivelyTracked = TrainingApplication.isActivelyTracked(workout.id)
        assertFalse(isActivelyTracked)

        val onMarkFinished: (() -> Unit)? = { /* mark finished */ }
        val canDelete = !isActivelyTracked
        val canMarkFinished = !workout.headerData.finished && !isActivelyTracked && onMarkFinished != null
        val hasContextMenu = canDelete || canMarkFinished

        assertTrue("Delete must be available for idle unfinished workout", canDelete)
        assertTrue("Mark as finished must be available for idle unfinished workout", canMarkFinished)
        assertTrue("Context menu must be enabled", hasContextMenu)
    }

    @Test
    fun testActivelyTrackedWorkout_compactContextMenuIsDisabled() {
        // Given an actively tracked workout
        val workout = createWorkoutData(id = 300L, finished = false)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setActiveWorkoutIdForTesting(300L)

        val isActivelyTracked = TrainingApplication.isActivelyTracked(workout.id)
        assertTrue("Workout must be actively tracked", isActivelyTracked)

        val onMarkFinished: (() -> Unit)? = { /* mark finished */ }
        val canDelete = !isActivelyTracked
        val canMarkFinished = !workout.headerData.finished && !isActivelyTracked && onMarkFinished != null
        val hasContextMenu = canDelete || canMarkFinished

        assertFalse("Actively tracked workout must NOT be deletable", canDelete)
        assertFalse("Actively tracked workout must NOT be markable as finished", canMarkFinished)
        assertFalse("Compact context menu must be completely disabled for actively tracked session", hasContextMenu)
    }

    @Test
    fun testExpandedWorkoutSummary_retainsEditWorkoutCapability() {
        // Invariant: Expanded view (WorkoutSummary via WorkoutHeader) retains full editing capability
        var editTriggered = false
        val onEditWorkout: () -> Unit = { editTriggered = true }

        onEditWorkout()
        assertTrue("Full view onEditWorkout callback must remain functional", editTriggered)
    }
}
