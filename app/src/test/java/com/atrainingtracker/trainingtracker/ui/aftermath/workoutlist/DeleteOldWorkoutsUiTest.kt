/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist

import android.app.Application
import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Behavioral Robolectric Compose UI tests for [WorkoutListActions] delete-sweep button
 * and [DeleteOldWorkoutsDialog] (`REQ-UI-127`, `TST-UI-080`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeleteOldWorkoutsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun workoutListActions_opensDeleteOldWorkoutsDialogWithDefault365AndConfirmsInput_TST_UI_080() {
        // TST-UI-080
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deleteActionDesc = context.getString(R.string.deleteOldWorkouts)
        val defaultDays = context.getString(R.string.defaultDaysToKeep)
        val deleteLabel = context.getString(R.string.delete)
        assertEquals("365", defaultDays)

        var showDialog by mutableStateOf(false)
        var confirmedDays: Int? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutListActions(
                    isCompactView = false,
                    onToggleCompactView = {},
                    sortOrder = WorkoutSortOrder.DATE,
                    onSortOrderChange = {},
                    onDeleteOldWorkoutsClicked = { showDialog = true }
                )
                if (showDialog) {
                    DeleteOldWorkoutsDialog(
                        onConfirm = { confirmedDays = it },
                        onDismiss = { showDialog = false }
                    )
                }
            }
        }

        // Tap delete sweep icon button
        composeTestRule.onNodeWithContentDescription(deleteActionDesc).performClick()

        // Dialog shows default "365", accepts numeric input "180", and emits confirmed value
        composeTestRule.onNodeWithText("365").performTextReplacement("180")
        composeTestRule.onNodeWithText(deleteLabel).performClick()
        assertEquals(180, confirmedDays)
    }
}
