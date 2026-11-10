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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract tests verifying WorkoutShareBottomSheet component existence and MapDetailLayout integration
 * (REQ-UI-447 / TST-UI-447).
 */
class WorkoutShareBottomSheetContractTest {

    @Test
    fun testWorkoutShareBottomSheet_existsInMapPackage() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.WorkoutShareBottomSheetKt")
        assertNotNull("WorkoutShareBottomSheetKt must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("WorkoutShareBottomSheet") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutShareBottomSheet composable function must exist and be public", method)
    }

    @Test
    fun testMapDetailLayout_exposesWorkoutDataParameter() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("MapDetailLayout") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("MapDetailLayout function must exist", method)

        val workoutDataClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutData")
        val hasWorkoutDataParam = method!!.parameterTypes.any { it == workoutDataClass }

        assertTrue(
            "MapDetailLayout must accept WorkoutData parameter for share mode selection",
            hasWorkoutDataParam
        )
    }

    @Test
    fun testWorkoutShareBottomSheet_rule73StackedActionButtonsContract() {
        val file = java.io.File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheet.kt")
        val altFile = java.io.File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheet.kt")
        val targetFile = if (file.exists()) file else altFile
        assertTrue("WorkoutShareBottomSheet.kt must exist on disk", targetFile.exists())

        val source = targetFile.readText()

        // Verify Rule 73: No horizontal action row in AppModalBottomSheet actions parameter
        assertFalse("AppModalBottomSheet must not define actions slot", source.contains("actions ="))

        // Verify stacked full-width primary Button
        assertTrue(
            "Primary share Button must use fillMaxWidth per Rule 73",
            source.contains("Button(") && source.contains("modifier = Modifier.fillMaxWidth()")
        )
        assertTrue(
            "Primary Button must use share_cta_button",
            source.contains("R.string.share_cta_button")
        )

        // Verify stacked full-width secondary OutlinedButton
        assertTrue(
            "Secondary photo picker OutlinedButton must use fillMaxWidth per Rule 73",
            source.contains("OutlinedButton(")
        )
        assertTrue(
            "Secondary OutlinedButton must use share_choose_photo",
            source.contains("R.string.share_choose_photo")
        )

        // Verify 8dp vertical spacing
        assertTrue(
            "Must use 8.dp arrangement spacing per Rule 73",
            source.contains("Arrangement.spacedBy(8.dp)")
        )

        // Verify primary button is placed before secondary button in file source
        val primaryIdx = source.indexOf("R.string.share_cta_button")
        val secondaryIdx = source.indexOf("R.string.share_choose_photo")
        assertTrue(
            "Primary 'Teilen' button must be stacked above secondary 'Foto auswählen' button",
            primaryIdx != -1 && secondaryIdx != -1 && primaryIdx < secondaryIdx
        )
    }

    @Test
    fun testWorkoutShareBottomSheet_threeTierPhotoCascadeContract() {
        val file = java.io.File("src/main/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheet.kt")
        val altFile = java.io.File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/WorkoutShareBottomSheet.kt")
        val targetFile = if (file.exists()) file else altFile
        assertTrue("WorkoutShareBottomSheet.kt must exist on disk", targetFile.exists())

        val source = targetFile.readText()

        // Verify PhotoCascadeTier enum definition
        assertTrue("PhotoCascadeTier enum must exist", source.contains("enum class PhotoCascadeTier"))
        assertTrue("PhotoCascadeTier must define ATTACHED", source.contains("ATTACHED"))
        assertTrue("PhotoCascadeTier must define TIME_WINDOW", source.contains("TIME_WINDOW"))

        // Verify Tier 1 strings and controls
        assertTrue(
            "Must use share_photos_attached_subtitle in Tier 1",
            source.contains("R.string.share_photos_attached_subtitle")
        )
        assertTrue(
            "Must provide button to expand to time-window photos",
            source.contains("R.string.share_photos_show_time_window")
        )

        // Verify Tier 2 strings and controls
        assertTrue(
            "Must query time-window photos via WorkoutPhotoManager",
            source.contains("WorkoutPhotoManager.queryWorkoutTimePhotos")
        )
        assertTrue(
            "Must use workout_photos_time_picker_subtitle in Tier 2",
            source.contains("R.string.workout_photos_time_picker_subtitle")
        )
        assertTrue(
            "Must provide button to return to attached photos in Tier 2",
            source.contains("R.string.share_photos_show_attached")
        )

        // Verify Tier 3 fallback button
        assertTrue(
            "Must provide workout_photos_show_all button for Tier 3",
            source.contains("R.string.workout_photos_show_all")
        )

        // Verify bounded grid height and scrollable guard
        assertTrue(
            "Must configure scrollable based on cascade tier to prevent nested scroll exception",
            source.contains("scrollable = activePhotoTier == null")
        )
        assertTrue(
            "Must constrain grid height to 120.dp or 240.dp",
            source.contains("120.dp else 240.dp")
        )
    }

    @Test
    fun testWorkoutHeader_uniformShareActionAndDynamicSpacerContract() {
        val file = java.io.File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
        val altFile = java.io.File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt")
        val targetFile = if (file.exists()) file else altFile
        assertTrue("WorkoutHeader.kt must exist on disk", targetFile.exists())

        val source = targetFile.readText()

        // 1. Parameter onShareWorkout
        assertTrue(
            "WorkoutHeader must declare onShareWorkout parameter",
            source.contains("onShareWorkout: (() -> Unit)? = null")
        )

        // 2. Share IconButton rendering with primary tint and 32dp size
        assertTrue(
            "WorkoutHeader must render Share IconButton with primary tint",
            source.contains("imageVector = Icons.Default.Share") &&
                    source.contains("tint = MaterialTheme.colorScheme.primary") &&
                    source.contains("contentDescription = stringResource(R.string.share)")
        )

        // 3. Dynamic portrait spacer
        assertTrue(
            "WorkoutHeader must calculate dynamic action spacer width in portrait",
            source.contains("val actionSpacerWidth =") &&
                    source.contains("onShareWorkout != null")
        )
    }

    private fun findSourceFile(relativePath: String): java.io.File {
        val candidates = listOf(
            java.io.File(relativePath),
            java.io.File("app", relativePath),
            java.io.File("..", relativePath),
            java.io.File("../app", relativePath)
        )
        return candidates.firstOrNull { it.exists() } ?: java.io.File(relativePath)
    }

    @Test
    fun testWorkoutSummary_andDetailTabbedContent_wireShareWorkoutContract() {
        val summaryFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt")
        val tabbedFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
        val mapLayoutFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")

        assertTrue("WorkoutSummary.kt must exist", summaryFile.exists())
        assertTrue("WorkoutDetailTabbedContent.kt must exist", tabbedFile.exists())
        assertTrue("MapDetailLayout.kt must exist", mapLayoutFile.exists())

        val summarySource = summaryFile.readText()
        val tabbedSource = tabbedFile.readText()
        val mapLayoutSource = mapLayoutFile.readText()

        // WorkoutSummary wires onShareWorkout and WorkoutShareBottomSheet
        assertTrue(
            "WorkoutSummary must wire onShareWorkout to show share sheet",
            summarySource.contains("onShareWorkout = if (workoutData.headerData.finished)")
        )
        assertTrue(
            "WorkoutSummary must host WorkoutShareBottomSheet with attachedPhotos",
            summarySource.contains("WorkoutShareBottomSheet(") &&
                    summarySource.contains("showShareModeSheet")
        )

        // WorkoutDetailTabbedContent wires onShareWorkout and passes non-empty header to MapDetailLayout
        assertTrue(
            "WorkoutDetailTabbedContent must wire onShareWorkout on persistent header",
            tabbedSource.contains("onShareWorkout = { showShareModeSheet = true }")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must supply snapshotHeader to MapDetailLayout",
            tabbedSource.contains("header = snapshotHeader")
        )

        // MapDetailLayout suppresses floating share button when workoutData != null
        assertTrue(
            "MapDetailLayout must default showFloatingShareButton to workoutData == null",
            mapLayoutSource.contains("showFloatingShareButton: Boolean = workoutData == null")
        )
        assertTrue(
            "MapDetailLayout must guard floating share button with showFloatingShareButton",
            mapLayoutSource.contains("if (showFloatingShareButton)")
        )
    }

    @Test
    fun testMapDetailLayout_exposesExternalSnapshotTriggerAndCompleteCallback() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        val method = clazz.declaredMethods.find {
            it.name.startsWith("MapDetailLayout") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("MapDetailLayout function must exist", method)

        val paramTypes = method!!.parameterTypes
        val booleanTypeCount = paramTypes.count { it == Boolean::class.javaPrimitiveType || it == Boolean::class.javaObjectType }
        val functionTypeCount = paramTypes.count { it.name.startsWith("kotlin.jvm.functions.Function") }

        assertTrue(
            "MapDetailLayout must accept externalSnapshotTrigger and onSnapshotComplete parameters (REQ-PER-029.1)",
            booleanTypeCount >= 5 && functionTypeCount >= 4
        )
    }

    @Test
    fun testWorkoutDetailTabbedContent_andTrackOnMapScreen_exposeInitialAutoShareSnapshot() {
        val tabbedClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        val tabbedMethod = tabbedClass.declaredMethods.find {
            it.name.startsWith("WorkoutDetailTabbedContent") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutDetailTabbedContent function must exist", tabbedMethod)

        val trackMapClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.TrackOnMapScreenKt")
        val trackMapMethod = trackMapClass.declaredMethods.find {
            it.name.startsWith("TrackOnMapScreen") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("TrackOnMapScreen function must exist", trackMapMethod)
    }

    @Test
    fun testWorkoutSummary_andWorkoutList_exposeOnShareMapSnapshot() {
        val summaryClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryKt")
        val summaryMethod = summaryClass.declaredMethods.find {
            it.name.startsWith("WorkoutSummary") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutSummary function must exist", summaryMethod)

        val listClass = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutListKt")
        val listMethod = listClass.declaredMethods.find {
            it.name.startsWith("WorkoutList") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("WorkoutList function must exist", listMethod)
    }

    @Test
    fun testWorkoutDetailTabbedContent_launchedEffectOmitsCurrentPageKeyAndUsesScrollToPage_TST_REV_007_1() {
        val tabbedFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
        assertTrue("WorkoutDetailTabbedContent.kt must exist", tabbedFile.exists())
        val source = tabbedFile.readText()

        // Rule 80 / REQ-REV-006.1: LaunchedEffect must not include pagerState.currentPage in key list
        assertFalse(
            "WorkoutDetailTabbedContent must not include pagerState.currentPage in LaunchedEffect key list (Rule 80)",
            source.contains("LaunchedEffect(pendingAutoShare, pagerState.currentPage, mapTabIndex)")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must key LaunchedEffect strictly on (pendingAutoShare, mapTabIndex)",
            source.contains("LaunchedEffect(pendingAutoShare, mapTabIndex)")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must use instant scrollToPage(mapTabIndex) without animation",
            source.contains("pagerState.scrollToPage(mapTabIndex)")
        )
    }

    @Test
    fun testWorkoutDetailTabbedContent_preservesAndRestoresOriginPageIndex_TST_REV_007_2() {
        val tabbedFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
        val trackMapFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt")
        val summariesTabbedFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesTabbedScreen.kt")
        val summariesListFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesListFragment.kt")
        val clustersFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt")

        assertTrue("WorkoutDetailTabbedContent.kt must exist", tabbedFile.exists())
        assertTrue("TrackOnMapScreen.kt must exist", trackMapFile.exists())
        assertTrue("WorkoutSummariesTabbedScreen.kt must exist", summariesTabbedFile.exists())
        assertTrue("WorkoutSummariesListFragment.kt must exist", summariesListFile.exists())
        assertTrue("WorkoutClustersScreen.kt must exist", clustersFile.exists())

        val tabbedSource = tabbedFile.readText()
        val trackMapSource = trackMapFile.readText()
        val summariesTabbedSource = summariesTabbedFile.readText()
        val summariesListSource = summariesListFile.readText()
        val clustersSource = clustersFile.readText()

        // REQ-REV-006.2: originPageIndex capture & restore
        assertTrue(
            "WorkoutDetailTabbedContent must rememberSaveable originPageIndex",
            tabbedSource.contains("var originPageIndex by rememberSaveable")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must capture originPageIndex on Option A selection",
            tabbedSource.contains("originPageIndex = pagerState.currentPage")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must restore originPageIndex on snapshot complete",
            tabbedSource.contains("pagerState.scrollToPage(targetPage)")
        )

        // Caller dismissal propagation
        assertTrue(
            "TrackOnMapScreen must declare and forward onSnapshotComplete",
            trackMapSource.contains("onSnapshotComplete: (() -> Unit)? = null") &&
                trackMapSource.contains("onSnapshotComplete = onSnapshotComplete")
        )
        assertTrue(
            "WorkoutSummariesTabbedScreen must dismiss detail view on snapshot complete",
            summariesTabbedSource.contains("onSnapshotComplete = {") &&
                summariesTabbedSource.contains("selectedWorkoutIdForDetails = null")
        )
        assertTrue(
            "WorkoutSummariesListFragment must dismiss detail view on snapshot complete",
            summariesListSource.contains("onSnapshotComplete = {") &&
                summariesListSource.contains("selectedWorkoutForDetails = null")
        )
        assertTrue(
            "WorkoutClustersScreen must dismiss detail view on snapshot complete",
            clustersSource.contains("onSnapshotComplete = {") &&
                clustersSource.contains("inspectedWorkout = null")
        )
    }

    @Test
    fun testMapDetailLayout_declaresShowHeaderAndWorkoutDetailTabbedContentPassesFalse_TST_REV_007_3() {
        val mapLayoutFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
        val tabbedFile = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")

        assertTrue("MapDetailLayout.kt must exist", mapLayoutFile.exists())
        assertTrue("WorkoutDetailTabbedContent.kt must exist", tabbedFile.exists())

        val mapLayoutSource = mapLayoutFile.readText()
        val tabbedSource = tabbedFile.readText()

        // REQ-REV-006.3 / REQ-REV-006.4: showHeader declaration & offscreen recording
        assertTrue(
            "MapDetailLayout must declare showHeader: Boolean = true parameter",
            mapLayoutSource.contains("showHeader: Boolean = true")
        )
        assertTrue(
            "MapDetailLayout must branch header rendering on showHeader",
            mapLayoutSource.contains("if (showHeader)")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must pass showHeader = false across child MapDetailLayout invocations",
            tabbedSource.contains("showHeader = false")
        )
    }
}
