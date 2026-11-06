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
}
