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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.TrackingMode
import com.atrainingtracker.trainingtracker.TrainingApplication
import com.atrainingtracker.trainingtracker.WorkoutCardSectionPreferences
import com.atrainingtracker.trainingtracker.settings.TuningConfig
import com.atrainingtracker.trainingtracker.tracker.LiveWorkoutCardSnapshot
import com.atrainingtracker.trainingtracker.tracker.LiveWorkoutCardStateStore
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

/**
 * Robolectric Compose UI & visual verification suite for live-updating active workout cards
 * (`REQ-PER-047`, `TST-PER-055.1`, `TST-PER-055.3`, Rule 8.5).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w900dp-h1400dp-xhdpi")
class WorkoutSummaryLiveUpdateUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        LiveWorkoutCardStateStore.Default.clear()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)
    }

    @After
    fun tearDown() {
        LiveWorkoutCardStateStore.Default.clear()
        TrainingApplication.setTrackingModeForTesting(TrackingMode.READY)
        TrainingApplication.setActiveWorkoutIdForTesting(-1L)
    }

    private val lightTestPrefs = WorkoutCardSectionPreferences(
        showDetails = true,
        showExtrema = true,
        showLaps = true,
        showDescription = false,
        showMapPreview = false,
        showElevationProfile = false,
        showTelemetryCharts = false,
        showZoneAnalysis = false,
        showGradeDistribution = false,
        showStrava = false,
        showRpeAndFeel = false
    )

    private fun createWorkout(
        id: Long,
        name: String,
        finished: Boolean,
        bSportType: BSportType = BSportType.BIKE,
        activeTimeSec: Long = 0L,
        totalTimeSec: Long = 0L,
        distanceM: Double = 0.0,
        avgSpeedMps: Double = 0.0,
        ascentM: Long = 0L,
        descentM: Long = 0L
    ): WorkoutData {
        return WorkoutData(
            id = id,
            finished = finished,
            fileBaseName = "workout_$id",
            workoutName = name,
            sportId = 1L,
            sportName = if (bSportType == BSportType.RUN) "Running" else "Road Bike",
            bSportType = bSportType,
            startTimeS = 1763800000L,
            formattedDate = "22.11.2026",
            formattedTime = "08:15",
            localDateTime = LocalDateTime.of(2026, 11, 22, 8, 15),
            equipmentName = "Canyon Endurace",
            equipmentId = 1L,
            commute = false,
            trainer = false,
            mapPolyline = "",
            encodedAltitudes = "",
            encodedDistances = "",
            uploadToStrava = 0,
            totalDistance = distanceM,
            maxDisplacement = null,
            activeTimeSec = activeTimeSec,
            totalTimeSec = totalTimeSec,
            avgSpeedMps = avgSpeedMps,
            ascentMeters = ascentM,
            descentMeters = descentM,
            minAltitude = null,
            maxAltitude = null,
            description = null,
            goal = null,
            method = null,
            stravaSportName = null
        )
    }

    @Test
    fun workoutSummaryFullCard_updatesLiveMetricsAndFinishedCardNeverSubscribes_TST_PER_055_1() {
        val activeFlow = MutableStateFlow<LiveWorkoutCardSnapshot?>(null)
        val finishedFlow = MutableStateFlow<LiveWorkoutCardSnapshot?>(null)

        val activeWorkout = createWorkout(id = 9001L, name = "Live Morning Ride", finished = false)
        val finishedWorkout = createWorkout(
            id = 9003L,
            name = "Yesterday Tempo Ride",
            finished = true,
            activeTimeSec = 3600L,
            totalTimeSec = 3660L,
            distanceM = 30200.0,
            avgSpeedMps = 8.39,
            ascentM = 410L
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Column(modifier = Modifier.width(420.dp)) {
                    WorkoutSummary(
                        workoutData = activeWorkout,
                        isPlayServiceAvailable = false,
                        onExport = {},
                        onSaveAsRoute = {},
                        onDeleteRequest = {},
                        onEditWorkout = {},
                        onMapClick = {},
                        preferences = lightTestPrefs,
                        tuningConfig = TuningConfig(),
                        liveSnapshotFlow = activeFlow
                    )
                    WorkoutSummary(
                        workoutData = finishedWorkout,
                        isPlayServiceAvailable = false,
                        onExport = {},
                        onSaveAsRoute = {},
                        onDeleteRequest = {},
                        onEditWorkout = {},
                        onMapClick = {},
                        preferences = lightTestPrefs,
                        tuningConfig = TuningConfig(),
                        liveSnapshotFlow = finishedFlow
                    )
                }
            }
        }

        // Finished card NEVER subscribes to liveSnapshotFlow.
        assertEquals(0, finishedFlow.subscriptionCount.value)
        assertTrue(activeFlow.subscriptionCount.value >= 1)

        // Emit tick 1 with active metrics, HR/Power/Cadence averages, and Lap 1.
        activeFlow.value = LiveWorkoutCardSnapshot(
            workoutId = 9001L,
            activeTimeSec = 615L,
            totalTimeSec = 620L,
            distanceM = 5120.0,
            avgSpeedMps = 8.325,
            ascentM = 124L,
            descentM = 48L,
            avgHeartRateBpm = 154.0,
            avgPowerWatts = 238.0,
            avgCadenceRpm = 88.0,
            laps = listOf(
                LapData(workoutId = 9001L, lapNr = 1L, timeTotalS = 600, distanceTotalM = 5000.0, speedAverageMps = 8.33)
            ),
            isPaused = false
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("10:15", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("154", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("238", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("88", substring = true).assertIsDisplayed()

        // Pause tick updates total time without changing active time.
        activeFlow.value = activeFlow.value!!.copy(totalTimeSec = 650L, isPaused = true)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("10:15", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("10:50", substring = true).assertIsDisplayed()

        // Finished card still never subscribed across all ticks.
        assertEquals(0, finishedFlow.subscriptionCount.value)
    }

    @Test
    fun workoutSummaryCompact_updatesActiveCardOnlyAndNeverSubscribesFinished_TST_PER_055_1() {
        val activeFlow = MutableStateFlow<LiveWorkoutCardSnapshot?>(null)
        val finishedFlow = MutableStateFlow<LiveWorkoutCardSnapshot?>(null)

        val activeWorkout = createWorkout(id = 9101L, name = "Active Compact Ride", finished = false)
        val nonMatchingUnfinished = createWorkout(
            id = 9102L,
            name = "Interrupted Old Ride",
            finished = false,
            activeTimeSec = 120L,
            totalTimeSec = 120L,
            distanceM = 800.0,
            avgSpeedMps = 6.67,
            ascentM = 15L
        )
        val finishedWorkout = createWorkout(
            id = 9103L,
            name = "Completed Ride",
            finished = true,
            activeTimeSec = 1800L,
            totalTimeSec = 1800L,
            distanceM = 15000.0,
            avgSpeedMps = 8.33,
            ascentM = 200L
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                Column(modifier = Modifier.width(420.dp)) {
                    WorkoutSummaryCompact(
                        workoutData = activeWorkout,
                        onMapClick = {},
                        onDeleteRequest = {},
                        liveSnapshotFlow = activeFlow
                    )
                    WorkoutSummaryCompact(
                        workoutData = nonMatchingUnfinished,
                        onMapClick = {},
                        onDeleteRequest = {},
                        liveSnapshotFlow = activeFlow
                    )
                    WorkoutSummaryCompact(
                        workoutData = finishedWorkout,
                        onMapClick = {},
                        onDeleteRequest = {},
                        liveSnapshotFlow = finishedFlow
                    )
                }
            }
        }

        assertEquals(0, finishedFlow.subscriptionCount.value)

        // Emit 10 ticks for workoutId 9101L.
        for (i in 1..10) {
            activeFlow.value = LiveWorkoutCardSnapshot(
                workoutId = 9101L,
                activeTimeSec = 60L * i,
                totalTimeSec = 60L * i,
                distanceM = 500.0 * i,
                avgSpeedMps = 8.33,
                ascentM = 12L * i
            )
            composeTestRule.waitForIdle()
        }

        // Active card displays tick 10 (10:00 min, 120 m ascent).
        composeTestRule.onNodeWithText("10:00 min", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("120", substring = true).assertIsDisplayed()
        // Non-matching unfinished card still displays its original 2:00 min / 15 m.
        composeTestRule.onNodeWithText("2:00 min", substring = true).assertIsDisplayed()
        assertEquals(0, finishedFlow.subscriptionCount.value)
    }

    @Test
    fun renderLiveActiveWorkoutCard_lightAndDarkVisualShowcase_TST_PER_055_3() {
        val liveFlow = MutableStateFlow<LiveWorkoutCardSnapshot?>(
            LiveWorkoutCardSnapshot(
                workoutId = 9201L,
                activeTimeSec = 1458L,   // 24:18
                totalTimeSec = 1503L,    // 25:03
                distanceM = 11420.0,     // 11.42 km
                avgSpeedMps = 7.833,     // 28.2 km/h
                ascentM = 245L,
                descentM = 110L,
                avgHeartRateBpm = 154.0,
                avgPowerWatts = 238.0,
                avgCadenceRpm = 88.0,
                laps = listOf(
                    LapData(workoutId = 9201L, lapNr = 1L, timeTotalS = 720, distanceTotalM = 5600.0, speedAverageMps = 7.78),
                    LapData(workoutId = 9201L, lapNr = 2L, timeTotalS = 738, distanceTotalM = 5820.0, speedAverageMps = 7.89)
                ),
                isPaused = false
            )
        )

        val activeWorkout = createWorkout(
            id = 9201L,
            name = "Morning Threshold Ride (Recording)",
            finished = false
        )
        val finishedWorkout = createWorkout(
            id = 9202L,
            name = "Sunday Endurance Ride",
            finished = true,
            activeTimeSec = 5420L,
            totalTimeSec = 5510L,
            distanceM = 46800.0,
            avgSpeedMps = 8.63,
            ascentM = 620L,
            descentM = 615L
        )

        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .testTag("att9069_showcase")
                    .width(860.dp)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(false to "Light Theme — Live Active Card", true to "Dark/AMOLED — Live Active Card").forEach { (isDark, title) ->
                    ATrainingTrackerTheme(darkTheme = isDark) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.background),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                WorkoutSummary(
                                    workoutData = activeWorkout,
                                    isPlayServiceAvailable = false,
                                    onExport = {},
                                    onSaveAsRoute = {},
                                    onDeleteRequest = {},
                                    onEditWorkout = {},
                                    onMapClick = {},
                                    preferences = lightTestPrefs,
                                    tuningConfig = TuningConfig(),
                                    liveSnapshotFlow = liveFlow,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                WorkoutSummaryCompact(
                                    workoutData = activeWorkout,
                                    onMapClick = {},
                                    onDeleteRequest = {},
                                    liveSnapshotFlow = liveFlow,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                WorkoutSummaryCompact(
                                    workoutData = finishedWorkout,
                                    onMapClick = {},
                                    onDeleteRequest = {},
                                    liveSnapshotFlow = liveFlow,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        val bitmap = composeTestRule.onNodeWithTag("att9069_showcase").captureToImage().asAndroidBitmap()
        assertTrue(bitmap.width > 400)
        assertTrue(bitmap.height > 400)

        val outFile = File("docs/engineering/test_evidence/tickets/ATT-9069/screenshots/att9069_live_active_workout_card_light_dark.png")
            .let { if (File("docs").exists()) it else File("../docs/engineering/test_evidence/tickets/ATT-9069/screenshots/att9069_live_active_workout_card_light_dark.png") }
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        assertTrue(outFile.exists() && outFile.length() > 10_000L)
    }
}
