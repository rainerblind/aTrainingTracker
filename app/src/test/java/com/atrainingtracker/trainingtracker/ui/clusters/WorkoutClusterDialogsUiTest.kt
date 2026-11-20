/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.repositories.SimpleSportTypeInfo
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeader
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderData
import com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderTestTags
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric `createComposeRule()` behavioral UI tests for cluster identity/selection/edit
 * dialogs and workout header reassignment trigger (`REQ-PRO-132`, `REQ-PRO-133`: zero `src/main/` reads).
 */
// Linked test specs: TST-SET-008, TST-SET-009, TST-SET-010, TST-SET-012, TST-SET-044
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WorkoutClusterDialogsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportRepo.sportTypesList } returns listOf(
            SimpleSportTypeInfo(1L, "Road Cycling", BSportType.BIKE),
            SimpleSportTypeInfo(2L, "Running", BSportType.RUN)
        )
        SportTypesRepository.resetForTesting(mockSportRepo)
    }

    @After
    fun tearDown() {
        SportTypesRepository.resetForTesting(null)
    }

    private fun sampleCluster(
        id: Long = 10L,
        name: String = "Morning Loop",
        hitCount: Int = 5
    ) = WorkoutCluster(
        id = id,
        name = name,
        probableSportId = 2L,
        startLat = 48.1,
        startLng = 11.5,
        endLat = 48.1,
        endLng = 11.5,
        maxDispLat = 48.12,
        maxDispLng = 11.52,
        refDistance = 8000.0,
        hitCount = hitCount,
        bSportType = BSportType.RUN,
        hasCounter = true
    )

    @Test
    fun editWorkoutClusterIdentityDialog_renamesChangesSportAndTogglesCounter_TST_SET_008() {
        // TST-SET-008
        var confirmedName: String? = null
        var confirmedSportId: Long? = null
        var confirmedHasCounter: Boolean? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                EditWorkoutClusterIdentityDialog(
                    cluster = sampleCluster(),
                    onConfirm = { newName, newSportId, hasCounter ->
                        confirmedName = newName
                        confirmedSportId = newSportId
                        confirmedHasCounter = hasCounter
                    },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Morning Loop").performScrollTo().performTextReplacement("Evening Gravel Loop")
        // DropdownSelector places a clickable overlay Box over the readOnly OutlinedTextField("Running")
        composeTestRule.onNodeWithText("Running").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Road Cycling").performSemanticsAction(SemanticsActions.OnClick)
        // Toggle activity counter off
        composeTestRule.onNode(isToggleable()).performScrollTo().performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.save)).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("Evening Gravel Loop", confirmedName)
        assertEquals(1L, confirmedSportId)
        assertEquals(false, confirmedHasCounter)
    }

    @Test
    fun workoutClusterSelectionDialog_displaysSyncedHitCountAndSelectsCandidate_TST_SET_009_TST_SET_044() {
        // TST-SET-009, TST-SET-044
        val c1 = sampleCluster(id = 1L, name = "Isar Trail", hitCount = 5)
        val c2 = sampleCluster(id = 2L, name = "Canal Loop", hitCount = 2)
        var selectedCluster: WorkoutCluster? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutClusterSelectionDialog(
                    title = context.getString(R.string.cluster_move_workout_title),
                    candidates = listOf(c1 to 0.12, c2 to 0.48),
                    onSelect = { selectedCluster = it },
                    onDismiss = {},
                    sportNameResolver = { "Running" },
                    bSportTypeResolver = { BSportType.RUN }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Isar Trail").assertIsDisplayed()
        composeTestRule.onNodeWithText("Canal Loop").assertIsDisplayed()
        // Hit count 5 rendered accurately via plural/formatted string
        val expectedHitsText = context.resources.getQuantityString(R.plurals.cluster_recordings, 5, 5)
        composeTestRule.onNodeWithText(expectedHitsText, substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText("Canal Loop").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2L, selectedCluster?.id)
    }

    @Test
    fun editWorkoutClusterDialog_displaysCandidatesAndInvokesOnSelectCluster_TST_SET_010() {
        // TST-SET-010
        val candidate = sampleCluster(id = 42L, name = "Suggested Park Loop", hitCount = 5)
        var selected: WorkoutCluster? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                EditWorkoutClusterDialog(
                    currentClusterId = -1L,
                    candidates = listOf(candidate to 0.25),
                    initialWorkoutName = "Morning Run",
                    onSelectCluster = { selected = it },
                    onUnassignCluster = {},
                    onCreateNewCluster = { _, _ -> },
                    onDismiss = {},
                    sportNameResolver = { "Running" },
                    bSportTypeResolver = { BSportType.RUN }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Suggested Park Loop").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Suggested Park Loop").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(42L, selected?.id)
    }

    @Test
    fun workoutHeader_onChangeClusterButton_triggersClusterMoveDialog_TST_SET_012() {
        // TST-SET-012
        var changeClusterClicked = false
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutHeader(
                    data = WorkoutHeaderData(
                        workoutName = "Morning Loop #5",
                        formattedDate = "2026-11-20",
                        formattedTime = "07:00",
                        sportName = "Running",
                        bSportType = BSportType.RUN,
                        finished = true,
                        commute = false,
                        trainer = false,
                        startTimeS = 1700000000L,
                        equipmentName = null,
                        uploadToStrava = 0,
                        clusterId = 10L,
                        clusterName = "Morning Loop"
                    ),
                    onExport = {},
                    onSaveAsRoute = {},
                    onDeleteRequest = {},
                    onChangeCluster = { changeClusterClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.CHANGE_CLUSTER_BUTTON, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        assertTrue("Tapping Move/Change Cluster button must invoke onChangeCluster", changeClusterClicked)
    }
}
