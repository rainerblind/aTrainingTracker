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

package com.atrainingtracker.trainingtracker.ui.components.workoutheader

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterSelectionItem
import com.atrainingtracker.trainingtracker.ui.dialogs.WorkoutDiagnosticTestTags
import com.atrainingtracker.trainingtracker.ui.theme.ATrainingTrackerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Behavioral Compose UI tests for [WorkoutHeader] detail action icon uniformity, global-vs-unit
 * visual grouping (`[Tune]` tonal pill + `VerticalDivider` before `[⇄][Edit][Share]`),
 * narrow-viewport `actionSpacerWidth` reservation, and `Rule 8.5` Light/Dark preview capture
 * (ATT-8097 / REQ-UI-2786 / TST-UI-2786).
 *
 * Strictly performs zero `src/main` file reads (`REQ-PRO-049.1`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1080dp-h2200dp-xhdpi")
class WorkoutDetailHeaderActionsComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private companion object {
        const val WIDTH_411_DP = 411
        const val WIDTH_360_DP = 360
        const val ICON_SIZE_DP = 32f
        const val DP_TOLERANCE = 1.5f
        const val PREVIEW_HOST_TAG = "att_8097_header_actions_preview"
        const val PREVIEW_PNG_REL_PATH = "docs/engineering/walkthroughs/img/ATT-8097_header_actions_preview.png"
        const val PNG_QUALITY = 100
        const val PREVIEW_PADDING_DP = 12
        const val PREVIEW_GAP_DP = 12
        const val SAMPLE_TIME_S = 1_760_000_000L
        const val SAMPLE_DISTANCE_M = 32_000.0
    }

    private fun sampleHeaderData(
        name: String = "Morning Tempo Ride",
        clusterId: Long = 5L,
        clusterName: String? = "Isar Valley Loop",
        recordingErrors: String? = null
    ) = WorkoutHeaderData(
        workoutName = name,
        formattedDate = "18.11.2026",
        formattedTime = "08:30",
        sportName = "Cycling",
        bSportType = BSportType.BIKE,
        clusterId = clusterId,
        clusterName = clusterName,
        finished = true,
        commute = false,
        trainer = false,
        startTimeS = SAMPLE_TIME_S,
        equipmentName = "Gravel Bike",
        uploadToStrava = 1,
        stravaSportName = "Ride",
        recordingErrors = recordingErrors
    )

    @Composable
    private fun WithScreenWidth(widthDp: Int, content: @Composable () -> Unit) {
        val baseConfig = LocalConfiguration.current
        val overridden = Configuration(baseConfig).apply { screenWidthDp = widthDp }
        CompositionLocalProvider(LocalConfiguration provides overridden) {
            Box(modifier = Modifier.width(widthDp.dp)) {
                content()
            }
        }
    }

    private fun resolvePreviewOutputFile(): File {
        val cwd = File(".").canonicalFile
        val repoRoot = if (cwd.name == "app") cwd.parentFile else cwd
        return File(repoRoot, PREVIEW_PNG_REL_PATH)
    }

    @Test
    fun workoutHeader_detailActionsOrderedLeftToRight_globalPillBeforePerWorkoutActions() {
        var tabsClicks = 0
        var clusterClicks = 0
        var editClicks = 0
        var shareClicks = 0

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WithScreenWidth(WIDTH_411_DP) {
                    WorkoutHeader(
                        data = sampleHeaderData(),
                        menuEnabled = false,
                        canDelete = false,
                        onClicked = null,
                        onExport = {},
                        onSaveAsRoute = {},
                        onDeleteRequest = {},
                        onOpenTabsConfig = { tabsClicks++ },
                        onChangeCluster = { clusterClicks++ },
                        onEditWorkout = { editClicks++ },
                        onShareWorkout = { shareClicks++ }
                    )
                }
            }
        }

        val pillNode = composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.GLOBAL_TABS_PILL, useUnmergedTree = true)
        val tuneNode = composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.TABS_CONFIG_BUTTON, useUnmergedTree = true)
        val dividerNode = composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.ACTIONS_DIVIDER, useUnmergedTree = true)
        val clusterNode = composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.CHANGE_CLUSTER_BUTTON, useUnmergedTree = true)
        val editNode = composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.EDIT_BUTTON, useUnmergedTree = true)
        val shareNode = composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.SHARE_BUTTON, useUnmergedTree = true)

        pillNode.assertIsDisplayed()
        tuneNode.assertIsDisplayed()
        dividerNode.assertIsDisplayed()
        clusterNode.assertIsDisplayed()
        editNode.assertIsDisplayed()
        shareNode.assertIsDisplayed()

        val tuneBounds = tuneNode.getBoundsInRoot()
        val dividerBounds = dividerNode.getBoundsInRoot()
        val clusterBounds = clusterNode.getBoundsInRoot()
        val editBounds = editNode.getBoundsInRoot()
        val shareBounds = shareNode.getBoundsInRoot()

        assertTrue("Tune must precede divider", tuneBounds.right.value <= dividerBounds.left.value + DP_TOLERANCE)
        assertTrue("Divider must precede cluster swap", dividerBounds.right.value <= clusterBounds.left.value + DP_TOLERANCE)
        assertTrue("Cluster swap must precede Edit", clusterBounds.right.value <= editBounds.left.value + DP_TOLERANCE)
        assertTrue("Edit must precede Share", editBounds.right.value <= shareBounds.left.value + DP_TOLERANCE)

        listOf(tuneBounds, clusterBounds, editBounds, shareBounds).forEach { bounds ->
            assertEquals(ICON_SIZE_DP, bounds.width.value, DP_TOLERANCE)
            assertEquals(ICON_SIZE_DP, bounds.height.value, DP_TOLERANCE)
        }

        tuneNode.performClick()
        clusterNode.performClick()
        editNode.performClick()
        shareNode.performClick()

        assertEquals(1, tabsClicks)
        assertEquals(1, clusterClicks)
        assertEquals(1, editClicks)
        assertEquals(1, shareClicks)
    }

    @Test
    fun workoutHeader_nonOverlapReservationOn360dp_withLongTitleAndDiagnosticBadge() {
        val longTitle = "Long Endurance Ride Through Alpine Passes With Multiple Loops"
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WithScreenWidth(WIDTH_360_DP) {
                    WorkoutHeader(
                        data = sampleHeaderData(name = longTitle, recordingErrors = "BLE dropout"),
                        menuEnabled = false,
                        canDelete = false,
                        onClicked = null,
                        onExport = {},
                        onSaveAsRoute = {},
                        onDeleteRequest = {},
                        onOpenTabsConfig = {},
                        onChangeCluster = {},
                        onEditWorkout = {},
                        onShareWorkout = {}
                    )
                }
            }
        }

        val titleBounds = composeTestRule.onNodeWithText(longTitle).getBoundsInRoot()
        val pillBounds = composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.GLOBAL_TABS_PILL, useUnmergedTree = true).getBoundsInRoot()
        val badgeBounds = composeTestRule.onNodeWithTag(WorkoutDiagnosticTestTags.BADGE, useUnmergedTree = true).getBoundsInRoot()
        val shareBounds = composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.SHARE_BUTTON, useUnmergedTree = true).getBoundsInRoot()

        assertTrue(
            "Title right (${titleBounds.right.value}) must not overlap global pill left (${pillBounds.left.value})",
            titleBounds.right.value <= pillBounds.left.value + DP_TOLERANCE
        )
        assertEquals(ICON_SIZE_DP, badgeBounds.width.value, DP_TOLERANCE)
        assertTrue("Share action must stay within 360dp width", shareBounds.right.value <= WIDTH_360_DP.toFloat())
    }

    @Test
    fun workoutHeader_summaryDefaults_omitGlobalTabsAndClusterSwapIcons() {
        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WithScreenWidth(WIDTH_411_DP) {
                    WorkoutHeader(
                        data = sampleHeaderData(),
                        menuEnabled = true,
                        onClicked = {},
                        onExport = {},
                        onSaveAsRoute = {},
                        onDeleteRequest = {},
                        onEditWorkout = {},
                        onShareWorkout = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.GLOBAL_TABS_PILL, useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.TABS_CONFIG_BUTTON, useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.ACTIONS_DIVIDER, useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.CHANGE_CLUSTER_BUTTON, useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.EDIT_BUTTON, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(WorkoutHeaderTestTags.SHARE_BUTTON, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun workoutClusterSelectionItem_hidesScoreBracketsWhenNaN() {
        val cluster = WorkoutCluster(
            id = 9L,
            name = "Indoor Zwift Base",
            probableSportId = 1L,
            startLat = 0.0,
            startLng = 0.0,
            endLat = 0.0,
            endLng = 0.0,
            maxDispLat = 0.0,
            maxDispLng = 0.0,
            refDistance = SAMPLE_DISTANCE_M,
            hitCount = 4,
            bSportType = BSportType.BIKE
        )

        composeTestRule.setContent {
            ATrainingTrackerTheme(darkTheme = false) {
                WorkoutClusterSelectionItem(
                    cluster = cluster,
                    score = Double.NaN,
                    sportName = "Indoor Cycling",
                    bSportType = BSportType.BIKE
                )
            }
        }

        composeTestRule.onNodeWithText("Indoor Zwift Base").assertIsDisplayed()
        composeTestRule.onNodeWithText("Indoor Cycling").assertIsDisplayed()
        composeTestRule.onNodeWithText("(NaN)").assertDoesNotExist()
    }

    @Composable
    private fun PreviewColumn(isDark: Boolean) {
        ATrainingTrackerTheme(darkTheme = isDark) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(WIDTH_411_DP.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)
                ) {
                    Text(
                        text = if (isDark) "Dark (411 dp & 360 dp)" else "Light (411 dp & 360 dp)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    WithScreenWidth(WIDTH_411_DP) {
                        WorkoutHeader(
                            data = sampleHeaderData(),
                            menuEnabled = false,
                            canDelete = false,
                            onClicked = null,
                            onExport = {},
                            onSaveAsRoute = {},
                            onDeleteRequest = {},
                            onOpenTabsConfig = {},
                            onChangeCluster = {},
                            onEditWorkout = {},
                            onShareWorkout = {}
                        )
                    }
                    WithScreenWidth(WIDTH_360_DP) {
                        WorkoutHeader(
                            data = sampleHeaderData(
                                name = "Long Endurance Ride Along Isar Valley",
                                recordingErrors = "BLE dropout"
                            ),
                            menuEnabled = false,
                            canDelete = false,
                            onClicked = null,
                            onExport = {},
                            onSaveAsRoute = {},
                            onDeleteRequest = {},
                            onOpenTabsConfig = {},
                            onChangeCluster = {},
                            onEditWorkout = {},
                            onShareWorkout = {}
                        )
                    }
                }
            }
        }
    }

    @Test
    fun captureLightAndDarkPreviewPng_forRule85Inspection() {
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .width((WIDTH_411_DP * 2 + PREVIEW_PADDING_DP * 3).dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .padding(PREVIEW_PADDING_DP.dp)
                    .testTag(PREVIEW_HOST_TAG),
                horizontalArrangement = Arrangement.spacedBy(PREVIEW_GAP_DP.dp)
            ) {
                PreviewColumn(isDark = false)
                PreviewColumn(isDark = true)
            }
        }

        val bitmap = composeTestRule.onNodeWithTag(PREVIEW_HOST_TAG)
            .captureToImage()
            .asAndroidBitmap()

        val outFile = resolvePreviewOutputFile()
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
        }
        assertTrue("Preview PNG must exist and be non-empty", outFile.exists() && outFile.length() > 0L)
    }
}
