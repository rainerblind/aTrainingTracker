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

package com.atrainingtracker.trainingtracker.ui.clusters

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.regex.Pattern

/**
 * Behavioral Compose UI and localization unit tests for [WorkoutClusterSelectionDialog]
 * verifying unassign and create-new capabilities
 * (ATT-7754 / REQ-SET-059 / TST-SET-104.1 / TST-SET-104.4).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class WorkoutClusterSelectionDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun extractStringTagRawContent(locale: String, stringKey: String): String? {
        val dirName = if (locale.isEmpty()) "values" else "values-$locale"
        val content = com.atrainingtracker.testing.LocalizationTestCache.getRawContent(dirName, "strings.xml")
        val regex = Pattern.compile("<string\\s+name=[\"']$stringKey[\"']>(.*?)</string>", Pattern.DOTALL)
        val matcher = regex.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun containsEmoji(text: String): Boolean {
        return text.codePoints().anyMatch { cp ->
            (cp in 0x1F000..0x1FAFF) || (cp in 0x2600..0x27BF) || cp == 0xFE0F || cp == 0x200D
        }
    }

    private fun createCluster(id: Long, name: String): WorkoutCluster {
        return WorkoutCluster(
            id = id,
            name = name,
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.01,
            maxDispLat = 48.02,
            maxDispLng = 11.02,
            refDistance = 5000.0,
            hitCount = 3,
            bSportType = BSportType.RUN
        )
    }

    @Test
    fun testWorkoutClusterSelectionDialog_whenOnUnassignProvided_triggersCallbackAndDismisses() {
        var unassignedCalled = false
        var dismissedCalled = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutClusterSelectionDialog(
                    title = context.getString(R.string.cluster_move_workout_title),
                    candidates = listOf(createCluster(1L, "Route 1") to 0.1),
                    onSelect = {},
                    onDismiss = { dismissedCalled = true },
                    sportNameResolver = { "Running" },
                    bSportTypeResolver = { BSportType.RUN },
                    onCreateNew = {},
                    onUnassign = { unassignedCalled = true }
                )
            }
        }

        val unassignText = context.getString(R.string.cluster_naming__leave_unclustered)
        val createNewText = context.getString(R.string.cluster_naming__create_new)
        composeTestRule.onNodeWithText(unassignText).assertIsDisplayed()
        composeTestRule.onNodeWithText(createNewText).assertIsDisplayed()

        composeTestRule.onNodeWithText(unassignText).performSemanticsAction(SemanticsActions.OnClick)

        assertTrue("Unassign callback must be executed on click", unassignedCalled)
        assertTrue("Dialog must be dismissed after clicking unassign", dismissedCalled)
    }

    @Test
    fun testWorkoutClusterSelectionDialog_whenCallbacksNull_optionalActionsHidden() {
        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutClusterSelectionDialog(
                    title = context.getString(R.string.cluster_move_workout_title),
                    candidates = listOf(createCluster(1L, "Route 1") to 0.1),
                    onSelect = {},
                    onDismiss = {},
                    sportNameResolver = { "Running" },
                    bSportTypeResolver = { BSportType.RUN },
                    onCreateNew = null,
                    onUnassign = null
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.cluster_naming__leave_unclustered)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.cluster_naming__create_new)).assertDoesNotExist()
    }

    @Test
    fun testWorkoutClusterSelectionDialog_whenCandidateSelected_triggersSelect() {
        var selectedCluster: WorkoutCluster? = null
        val targetCluster = createCluster(42L, "Morning Hill Run")

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutClusterSelectionDialog(
                    title = context.getString(R.string.cluster_move_workout_title),
                    candidates = listOf(targetCluster to 0.05),
                    onSelect = { selectedCluster = it },
                    onDismiss = {},
                    sportNameResolver = { "Running" },
                    bSportTypeResolver = { BSportType.RUN },
                    onCreateNew = {},
                    onUnassign = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Morning Hill Run").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(targetCluster, selectedCluster)
    }

    @Test
    fun testLeaveUnclusteredStrings_existAcrossAll9Locales() {
        val keys = listOf(
            "cluster_naming__leave_unclustered",
            "cluster_naming__create_new"
        )
        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            for (key in keys) {
                val rawContent = extractStringTagRawContent(locale, key)
                assertNotNull("Key '$key' must exist in $valuesDir/strings.xml", rawContent)
                assertFalse("Key '$key' in $valuesDir must not be blank", rawContent!!.isBlank())
                assertFalse(
                    "Key '$key' in $valuesDir must not contain decorative emojis (ATT-5400)",
                    containsEmoji(rawContent)
                )
            }
        }
    }
}
