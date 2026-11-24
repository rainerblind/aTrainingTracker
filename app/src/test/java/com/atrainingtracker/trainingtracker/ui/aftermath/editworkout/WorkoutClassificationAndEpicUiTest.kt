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

package com.atrainingtracker.trainingtracker.ui.aftermath.editworkout

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.ActiveFilterChipsRow
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterBottomSheet
import com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteria
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderTestTags
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric Compose UI tests covering the two-tier classification selector,
 * WorkoutHeader Epic & Race badges, WorkoutFilterBottomSheet Epic chip, and
 * ActiveFilterChipsRow Epic chip removal (`REQ-REV-018`, `TST-REV-019`, `ATT-9022`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutClassificationAndEpicUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun classificationSection_separatesRoutineAndSpecial_andSupportsSimultaneousRaceAndEpic() {
        var commuteState = false
        var trainerState = false
        var raceState = false
        var epicState = false

        composeRule.setContent {
            var isCommute by remember { mutableStateOf(false) }
            var isTrainer by remember { mutableStateOf(false) }
            var isRace by remember { mutableStateOf(false) }
            var isEpic by remember { mutableStateOf(false) }

            ATrainingTrackerTheme {
                WorkoutClassificationSection(
                    isCommute = isCommute,
                    isTrainer = isTrainer,
                    isRace = isRace,
                    isEpic = isEpic,
                    onCommuteChange = { checked ->
                        isCommute = checked
                        if (checked) isTrainer = false
                        commuteState = isCommute
                        trainerState = isTrainer
                    },
                    onTrainerChange = { checked ->
                        isTrainer = checked
                        if (checked) isCommute = false
                        commuteState = isCommute
                        trainerState = isTrainer
                    },
                    onRaceChange = { checked ->
                        isRace = checked
                        raceState = checked
                    },
                    onEpicChange = { checked ->
                        isEpic = checked
                        epicState = checked
                    }
                )
            }
        }

        // Verify both tiers are rendered as distinct labeled groups
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.ROUTINE_GROUP).assertIsDisplayed()
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.SPECIAL_GROUP).assertIsDisplayed()

        // Routine tags preserve mutual exclusion
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.COMMUTE_TILE).performClick()
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.COMMUTE_TILE).assertIsOn()
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.INDOOR_TILE).assertIsOff()
        assertTrue(commuteState)
        assertFalse(trainerState)

        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.INDOOR_TILE).performClick()
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.INDOOR_TILE).assertIsOn()
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.COMMUTE_TILE).assertIsOff()
        assertFalse(commuteState)
        assertTrue(trainerState)

        // Special tags toggle independently and simultaneously
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.RACE_TILE).performClick()
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.EPIC_TILE).performClick()
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.RACE_TILE).assertIsOn()
        composeRule.onNodeWithTag(EditWorkoutClassificationTestTags.EPIC_TILE).assertIsOn()
        assertTrue("Race must remain active when Epic is also enabled", raceState)
        assertTrue("Epic must be active alongside Race", epicState)
    }

    @Test
    fun workoutHeader_rendersBothRaceAndEpicBadges_andHidesWhenUnset() {
        val headerState = mutableStateOf(
            WorkoutHeaderData(
                workoutName = "Ötztaler Radmarathon",
                formattedDate = "Aug 30, 2026",
                formattedTime = "06:30",
                sportName = "Road Cycling",
                bSportType = BSportType.BIKE,
                finished = true,
                commute = false,
                trainer = false,
                race = true,
                epic = true,
                startTimeS = 0,
                equipmentName = "Canyon Ultimate",
                uploadToStrava = 0,
                stravaSportName = "Ride"
            )
        )

        composeRule.setContent {
            ATrainingTrackerTheme {
                WorkoutHeader(
                    data = headerState.value,
                    onClicked = {},
                    onExport = {},
                    onSaveAsRoute = {},
                    onDeleteRequest = {},
                    onEditWorkout = {},
                    onShareWorkout = {}
                )
            }
        }

        composeRule.onNodeWithTag(WorkoutHeaderTestTags.RACE_BADGE, useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutHeaderTestTags.EPIC_BADGE, useUnmergedTree = true)
            .assertIsDisplayed()

        // Switch to routine-only commute workout -> neither Race nor Epic badge rendered, Commute shown as text
        val commuteLabel = composeRule.activity.getString(R.string.commute)
        composeRule.runOnIdle {
            headerState.value = headerState.value.copy(race = false, epic = false, commute = true)
        }
        composeRule.onNodeWithTag(WorkoutHeaderTestTags.RACE_BADGE, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag(WorkoutHeaderTestTags.EPIC_BADGE, useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText(commuteLabel, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun activeFilterChipsRow_showsEpicChip_andRemovesOnlyEpicOnClick() {
        val criteriaState = mutableStateOf(WorkoutFilterCriteria(isRace = true, isEpic = true))
        val epicChipLabel = composeRule.activity.getString(R.string.filter_epic_only)

        composeRule.setContent {
            ATrainingTrackerTheme {
                ActiveFilterChipsRow(
                    criteria = criteriaState.value,
                    onUpdateCriteria = { transform -> criteriaState.value = transform(criteriaState.value) },
                    onClearAll = { criteriaState.value = WorkoutFilterCriteria() }
                )
            }
        }

        composeRule.onNodeWithText(epicChipLabel).assertIsDisplayed()
        composeRule.onNodeWithText(epicChipLabel).performClick()

        composeRule.runOnIdle {
            assertNull("Clicking Epic active chip must clear isEpic", criteriaState.value.isEpic)
            assertEquals("Clearing Epic chip must preserve isRace", true, criteriaState.value.isRace)
        }
    }

    @Test
    fun filterBottomSheet_togglingEpicChipAndApplyingSetsIsEpicTrue() {
        var applied: WorkoutFilterCriteria? = null
        val applyLabel = composeRule.activity.getString(R.string.OK)

        composeRule.setContent {
            ATrainingTrackerTheme {
                WorkoutFilterBottomSheet(
                    criteria = WorkoutFilterCriteria(),
                    allWorkouts = emptyList(),
                    onApplyCriteria = { applied = it },
                    onClearAll = {},
                    onDismissRequest = {}
                )
            }
        }

        composeRule.onNodeWithTag("workout_filter_chip_epic")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(applyLabel).performClick()

        composeRule.runOnIdle {
            assertEquals("Applying Epic chip in filter sheet must set isEpic=true", true, applied?.isEpic)
        }
    }
}
