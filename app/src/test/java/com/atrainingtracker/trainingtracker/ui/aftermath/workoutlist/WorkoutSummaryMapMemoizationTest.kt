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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutSectionType
import com.atrainingtracker.trainingtracker.ui.map.toMapTrack
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Behavioral Compose UI tests verifying 9-key track memoization in [WorkoutPathPreviewMap]
 * and [WorkoutSummary] (ATT-8736, REQ-MAP-068.3, TST-MAP-071.4).
 *
 * Adheres strictly to REQ-PRO-049.1 (zero src/main file reads).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutSummaryMapMemoizationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun baseWorkoutData(): WorkoutData = WorkoutData(
        id = 101L,
        finished = true,
        fileBaseName = "ride_101.tcx",
        workoutName = "Isar Loop",
        sportId = 1L,
        sportName = "Road Bike",
        bSportType = BSportType.BIKE,
        startTimeS = 1_760_000_000L,
        formattedDate = "18.11.2026",
        formattedTime = "08:30",
        localDateTime = LocalDateTime.of(2026, 11, 18, 8, 30),
        equipmentName = "Road Bike",
        equipmentId = 1L,
        commute = false,
        trainer = false,
        mapPolyline = "encoded_poly_a",
        encodedAltitudes = "encoded_alt_a",
        encodedDistances = "encoded_dist_a",
        uploadToStrava = 0,
        totalDistance = 25_000.0,
        maxDisplacement = 8_500.0,
        activeTimeSec = 3600L,
        totalTimeSec = 3700L,
        avgSpeedMps = 7.0,
        ascentMeters = 320L,
        descentMeters = 320L,
        minAltitude = 500.0,
        maxAltitude = 650.0,
        startLatLng = LatLng(48.1351, 11.5820),
        endLatLng = LatLng(48.1360, 11.5840),
        maxDisplacementLatLng = LatLng(48.1520, 11.6100),
        description = null,
        goal = null,
        method = null,
        stravaSportName = null,
        minLat = 48.1351,
        minLng = 11.5820,
        maxLat = 48.1520,
        maxLng = 11.6100
    )

    @Test
    fun workoutPathPreviewMap_memoizesAcrossUnrelatedChangesAndInvalidatesOnAll9Keys() {
        var workoutState by mutableStateOf(baseWorkoutData())
        var titleState by mutableStateOf<String?>("Start: Munich")
        var decodeInvocations = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    WorkoutPathPreviewMap(
                        workoutData = workoutState,
                        startTitle = titleState,
                        endTitle = "Stop: Munich",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        onMapClick = {},
                        trackDecoder = { data ->
                            decodeInvocations++
                            data.toMapTrack()
                        }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        assertEquals("Initial composition decodes once", 1, decodeInvocations)

        // Unrelated recompositions must NOT re-decode
        titleState = "Start: Isar"
        workoutState = workoutState.copy(workoutName = "Renamed Loop", totalDistance = 26_000.0)
        composeTestRule.waitForIdle()
        assertEquals("Unrelated state changes must keep memoized track", 1, decodeInvocations)

        // All 9 read fields must invalidate memoization individually
        workoutState = workoutState.copy(id = 102L)
        composeTestRule.waitForIdle()
        assertEquals(2, decodeInvocations)

        workoutState = workoutState.copy(bSportType = BSportType.RUN)
        composeTestRule.waitForIdle()
        assertEquals(3, decodeInvocations)

        workoutState = workoutState.copy(mapPolyline = "encoded_poly_b")
        composeTestRule.waitForIdle()
        assertEquals(4, decodeInvocations)

        workoutState = workoutState.copy(encodedAltitudes = "encoded_alt_b")
        composeTestRule.waitForIdle()
        assertEquals(5, decodeInvocations)

        workoutState = workoutState.copy(encodedDistances = "encoded_dist_b")
        composeTestRule.waitForIdle()
        assertEquals(6, decodeInvocations)

        workoutState = workoutState.copy(minLat = 48.1300)
        composeTestRule.waitForIdle()
        assertEquals(7, decodeInvocations)

        workoutState = workoutState.copy(minLng = 11.5800)
        composeTestRule.waitForIdle()
        assertEquals(8, decodeInvocations)

        workoutState = workoutState.copy(maxLat = 48.1550)
        composeTestRule.waitForIdle()
        assertEquals(9, decodeInvocations)

        workoutState = workoutState.copy(maxLng = 11.6150)
        composeTestRule.waitForIdle()
        assertEquals(10, decodeInvocations)
    }

    @Test
    fun workoutSummary_rendersMapSectionWithValidatedBoundsSafely() {
        val workout = baseWorkoutData()
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    WorkoutSummary(
                        workoutData = workout,
                        isPlayServiceAvailable = true,
                        onExport = {},
                        onSaveAsRoute = {},
                        onDeleteRequest = {},
                        onEditWorkout = {},
                        onMapClick = {},
                        preferences = WorkoutCardSectionPreferences(showMapPreview = true),
                        workoutSectionsOrder = listOf(WorkoutSectionType.MAP)
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }
}
