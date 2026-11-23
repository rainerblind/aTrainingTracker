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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.atrainingtracker.R
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.testing.LocalizationTestCache
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository
import com.atrainingtracker.trainingtracker.migration.BackupRestoreViewModel
import com.atrainingtracker.trainingtracker.migration.ClusterNamingDialog
import com.atrainingtracker.trainingtracker.migration.LegacyImportEngine
import com.atrainingtracker.trainingtracker.repositories.SportTypesRepository
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Behavioral Compose UI and 9-language localization tests for [ClusterNamingDialog]
 * escape hatches and [WorkoutClusterSelectionDialog] create-new capability (ATT-5674 / REQ-UI-462 / TST-UI-462).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
class ClusterNamingDialogEscapeContractTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val sampleCluster = WorkoutCluster(
        id = 10L,
        name = "Existing Loop",
        probableSportId = 2L,
        startLat = 48.1,
        startLng = 11.5,
        endLat = 48.1,
        endLng = 11.5,
        maxDispLat = 48.12,
        maxDispLng = 11.52,
        refDistance = 8000.0,
        hitCount = 4,
        bSportType = BSportType.RUN
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val mockClusterRepo = mockk<WorkoutClusterRepository>(relaxed = true)
        every { mockClusterRepo.allClusters } returns MutableStateFlow(listOf(sampleCluster))
        coEvery { mockClusterRepo.refreshClusters() } returns Unit
        WorkoutClusterRepository.resetForTesting(mockClusterRepo)

        val mockEngine = mockk<WorkoutClusterEngine>(relaxed = true)
        every {
            mockEngine.scoreClusters(
                any(), any(), any(), any(), any(), any(), any<Set<BSportType>>(), any(), any(), any()
            )
        } returns listOf(sampleCluster to 0.12)
        every {
            mockEngine.scoreClusters(
                any(), any(), any(), any(), any(), any(), any<BSportType>(), any(), any(), any()
            )
        } returns listOf(sampleCluster to 0.12)
        WorkoutClusterEngine.resetForTesting(mockEngine)

        val mockSportRepo = mockk<SportTypesRepository>(relaxed = true)
        every { mockSportRepo.getUIName(any()) } returns "Running"
        every { mockSportRepo.getBSportType(any()) } returns BSportType.RUN
        SportTypesRepository.resetForTesting(mockSportRepo)
    }

    @After
    fun tearDown() {
        SportTypesRepository.resetForTesting(null)
        WorkoutClusterEngine.resetForTesting(null)
        WorkoutClusterRepository.resetForTesting(null)
    }

    private fun extractStringTagRawContent(locale: String, stringKey: String): String? {
        val dirName = if (locale.isEmpty()) "values" else "values-$locale"
        val content = LocalizationTestCache.getRawContent(dirName, "strings.xml")
        val regex = Pattern.compile("<string\\s+name=[\"']$stringKey[\"']>(.*?)</string>", Pattern.DOTALL)
        val matcher = regex.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun containsEmoji(text: String): Boolean {
        return text.codePoints().anyMatch { cp ->
            (cp in 0x1F000..0x1FAFF) || (cp in 0x2600..0x27BF) || cp == 0xFE0F || cp == 0x200D
        }
    }

    @Test
    fun testWorkoutClusterSelectionDialog_rendersCreateNewOptionAndInvokesCallback() {
        var createNewCalled = false
        var dismissedCalled = false

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                WorkoutClusterSelectionDialog(
                    title = context.getString(R.string.cluster_naming__select_existing_route),
                    candidates = listOf(sampleCluster to 0.12),
                    onSelect = {},
                    onDismiss = { dismissedCalled = true },
                    sportNameResolver = { "Running" },
                    bSportTypeResolver = { BSportType.RUN },
                    onCreateNew = { createNewCalled = true }
                )
            }
        }

        val createNewLabel = context.getString(R.string.cluster_naming__create_new)
        composeTestRule.onNodeWithText(createNewLabel).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertTrue("onCreateNew callback must be invoked", createNewCalled)
        assertTrue("onDismiss callback must be invoked after create-new", dismissedCalled)
    }

    @Test
    fun testClusterEscapeStrings_existAcrossAll9Locales() {
        val keys = listOf(
            "cluster_naming__create_new",
            "clear",
            "select_route"
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

    @Test
    fun testRouteNamePreservation_stripHitCountInvariant() {
        // REQ-UI-462.3: Switching back from existing cluster to create-new restores previous stripped name
        val originalName = "Hausrunde #14"
        val stripped = WorkoutClusterEngine.stripHitCount(originalName)
        assertEquals("Hausrunde", stripped)

        val unnumbered = "Feierabendrunde"
        assertEquals("Feierabendrunde", WorkoutClusterEngine.stripHitCount(unnumbered))
    }

    @Test
    fun testClusterNamingDialog_routePickerTapOpensSelectionAndClearRestoresCreateNew() {
        // ATT-5818 / Rule 77 / REQ-UI-462.4 / TST-UI-462.5:
        // Behavioral verification that tapping the read-only route picker field opens
        // WorkoutClusterSelectionDialog, selecting an existing route reveals the Clear button,
        // and Clear / Create-New reverts back to creating a new route.
        val interactionState = BackupRestoreViewModel.ClusterInteraction(
            date = "2026-11-23",
            start = LatLng(48.1, 11.5),
            end = LatLng(48.1, 11.5),
            apex = LatLng(48.12, 11.52),
            distance = 8000.0,
            bSportType = BSportType.RUN,
            polyline = PolyUtil.encode(listOf(LatLng(48.1, 11.5), LatLng(48.12, 11.52))),
            deferred = CompletableDeferred(),
            workoutName = "Hausrunde #14"
        )
        var confirmedDecision: LegacyImportEngine.ClusterDecision? = null

        composeTestRule.setContent {
            ATrainingTrackerTheme {
                ClusterNamingDialog(
                    state = interactionState,
                    queueCount = 2,
                    onConfirm = { confirmedDecision = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val createNewText = context.getString(R.string.cluster_naming__create_new)
        val selectRouteDesc = context.getString(R.string.select_route)
        val clearDesc = context.getString(R.string.clear)

        // Initially no Clear button; tapping route picker opens WorkoutClusterSelectionDialog
        composeTestRule.onNodeWithContentDescription(clearDesc).assertDoesNotExist()
        composeTestRule.onNodeWithText(createNewText).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(selectRouteDesc).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Select existing route from the opened selection sheet
        composeTestRule.onNodeWithText("Existing Loop").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        // Clear button now appears alongside selected route; tapping it reverts to Create New
        composeTestRule.onNodeWithText("Existing Loop").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(clearDesc).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(createNewText).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(clearDesc).assertDoesNotExist()

        // Tapping select_route icon also opens selection dialog; choosing Create New confirms null clusterId
        composeTestRule.onNodeWithContentDescription(selectRouteDesc).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodes(hasText(createNewText) and hasClickAction()).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.save)).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertNotNull("Confirm decision must be emitted", confirmedDecision)
        assertNull("After clearing/create-new, clusterId must be null", confirmedDecision?.clusterId)
        assertEquals("Hausrunde", confirmedDecision?.newClusterName)
    }
}
