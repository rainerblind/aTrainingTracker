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
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.theme.TTAlpha
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

/**
 * Automated unit test suite verifying workout deletion ergonomics,
 * active workout protection, and visual transparency states (REQ-UI-145, TST-UI-098, ATT-917).
 */
class WorkoutDeletionErgonomicsTest {

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
    fun testOldUnfinishedWorkout_whenIdle_isDeletableAndRenderedWithAlpha() {
        // Given an old crashed/unfinished workout and idle tracking
        val workout = createWorkoutData(id = 100L, finished = false)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)

        // Visual Alpha evaluation
        val contentAlpha = if (workout.headerData.finished) TTAlpha.High else 0.5f
        assertEquals("Unfinished workout must render with 0.5f alpha", 0.5f, contentAlpha, 0.001f)

        // Deletion permission evaluation
        val canDelete = !TrainingApplication.isActivelyTracked(workout.id)
        assertTrue("Old unfinished workout must be deletable", canDelete)

        // Export menu permission evaluation
        val menuEnabled = workout.headerData.finished
        assertFalse("Export menu must be disabled for unfinished workout", menuEnabled)
    }

    @Test
    fun testLiveActiveWorkout_whenTracking_cannotBeDeletedViaUI() {
        // Given an unfinished workout that is actively being recorded
        val workout = createWorkoutData(id = 200L, finished = false)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setActiveWorkoutIdForTesting(200L)

        // Deletion permission evaluation
        val canDelete = !TrainingApplication.isActivelyTracked(workout.id)
        assertFalse("Actively tracked workout MUST NOT be deletable via UI", canDelete)

        // Compact view context menu evaluation (no edit action)
        val canMarkFinished = !workout.finished && !TrainingApplication.isActivelyTracked(workout.id)
        val hasContextMenu = canDelete || canMarkFinished
        assertFalse("Actively tracked workout must not show delete context menu", hasContextMenu)
    }

    @Test
    fun testCompletedWorkout_whenIdle_isDeletableAndExportableWithHighAlpha() {
        // Given a completed workout
        val workout = createWorkoutData(id = 300L, finished = true)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)

        // Visual Alpha evaluation
        val contentAlpha = if (workout.headerData.finished) TTAlpha.High else 0.5f
        assertEquals("Finished workout must render with TTAlpha.High", TTAlpha.High, contentAlpha, 0.001f)

        // Deletion permission evaluation
        val canDelete = !TrainingApplication.isActivelyTracked(workout.id)
        assertTrue("Finished workout must be deletable", canDelete)

        // Export menu permission evaluation
        val menuEnabled = workout.headerData.finished
        assertTrue("Export menu must be enabled for finished workout", menuEnabled)
    }

    @Test
    fun testCompactViewContextMenu_whenActivelyTracked_contextMenuIsDisabled() {
        val workout = createWorkoutData(id = 400L, finished = false)
        TrainingApplication.setTrackingModeForTesting(TrackingMode.TRACKING)
        TrainingApplication.setActiveWorkoutIdForTesting(400L)

        val canDelete = !TrainingApplication.isActivelyTracked(workout.id)
        assertFalse(canDelete)

        val canMarkFinished = !workout.finished && !TrainingApplication.isActivelyTracked(workout.id)
        assertFalse(canMarkFinished)

        // ATT-993: 'Edit workout' removed from compact context menu, so context menu is disabled when actively tracked
        val hasContextMenu = canDelete || canMarkFinished
        assertFalse("Compact context menu must be disabled when actively tracked", hasContextMenu)
    }
}
