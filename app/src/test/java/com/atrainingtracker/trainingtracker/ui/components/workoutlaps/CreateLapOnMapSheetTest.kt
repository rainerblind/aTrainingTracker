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

package com.atrainingtracker.trainingtracker.ui.components.workoutlaps

import com.atrainingtracker.trainingtracker.domain.laps.LapSplitAndMoveCalculator
import com.atrainingtracker.trainingtracker.ui.aftermath.LapData
import com.atrainingtracker.trainingtracker.ui.aftermath.crop.CropSamplePoint
import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Contract and integration tests verifying [CreateLapOnMapSheet] composable existence,
 * parameters, design tokens, and functional split preview behavior
 * (REQ-UI-449, TST-UI-449.3, ATT-907).
 */
class CreateLapOnMapSheetTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val sheetSourceFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/CreateLapOnMapSheet.kt")
    }

    private fun createPoint(
        rowId: Long,
        index: Int,
        timeS: Long,
        distM: Double,
        lapNr: Long
    ) = CropSamplePoint(
        rowId = rowId,
        index = index,
        timeStr = "2026-11-05 10:00:$index",
        timeTotalS = timeS,
        timeActiveS = timeS,
        distanceTotalM = distM,
        latLng = LatLng(48.0 + index * 0.001, 11.0 + index * 0.001),
        altitude = 500.0,
        lapNr = lapNr
    )

    @Test
    fun testCreateLapOnMapSheet_composableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutlaps.CreateLapOnMapSheetKt")
        assertNotNull("CreateLapOnMapSheetKt class must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("CreateLapOnMapSheet") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("CreateLapOnMapSheet composable method must exist and be public", method)
        assertTrue(Modifier.isPublic(method!!.modifiers))
    }

    @Test
    fun testCreateLapOnMapSheet_sourceStructureAndContract() {
        assertTrue("CreateLapOnMapSheet.kt must exist", sheetSourceFile.exists())
        val content = sheetSourceFile.readText()

        // Verify key structural components
        assertTrue("Must use AppModalBottomSheet", content.contains("AppModalBottomSheet("))
        assertTrue("Must use PathPreviewMap for track preview", content.contains("PathPreviewMap("))
        assertTrue("Must use TrackType.BEST for canonical blue track (ATT-5480)", content.contains("type = TrackType.BEST"))
        assertTrue("Must pass ic_lap_split_scissors as apexIconRes to PathPreviewMap (ATT-5480)", content.contains("apexIconRes = R.drawable.ic_lap_split_scissors"))
        assertTrue("Must use ic_lap_split_scissors on confirm button (ATT-5480)", content.contains("R.drawable.ic_lap_split_scissors"))
        assertTrue("Must use Slider for fine scrubber adjustment", content.contains("Slider("))
        assertTrue("Must use LapSplitAndMoveCalculator for validation", content.contains("LapSplitAndMoveCalculator.validateSplit"))

        // Verify resource token references (no hardcoded strings)
        assertTrue("Must reference create_lap_on_map_title", content.contains("R.string.create_lap_on_map_title"))
        assertTrue("Must reference split_lap_confirm", content.contains("R.string.split_lap_confirm"))
        assertTrue("Must reference split_lap_preview_before", content.contains("R.string.split_lap_preview_before"))
        assertTrue("Must reference split_lap_preview_after", content.contains("R.string.split_lap_preview_after"))
        assertTrue("Must reference split_lap_slider_label", content.contains("R.string.split_lap_slider_label"))

        // Verify zero hardcoded color hex codes (#AARRGGBB or Color(0x...))
        assertFalse("Must not contain hardcoded Color(0x...)", content.contains("Color(0x"))
        assertFalse("Must not contain hardcoded hex color literals", Regex("#[0-9a-fA-F]{6,8}").containsMatchIn(content))
    }

    @Test
    fun testWorkoutLaps_exposesOnSplitLapClick() {
        val workoutLapsFile = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt")
        assertTrue("WorkoutLaps.kt must exist", workoutLapsFile.exists())
        val content = workoutLapsFile.readText()

        assertTrue(
            "WorkoutLaps must accept onSplitLapClick callback",
            content.contains("onSplitLapClick: (() -> Unit)? = null")
        )
        assertTrue(
            "WorkoutLaps must display split lap action button",
            content.contains("R.string.split_lap_action")
        )
    }

    @Test
    fun testLapSplitVisualizer_exposesOnSplitLapClick() {
        val visualizerFile = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt")
        assertTrue("LapSplitVisualizer.kt must exist", visualizerFile.exists())
        val content = visualizerFile.readText()

        assertTrue(
            "LapSplitVisualizerCard must accept onSplitLapClick callback",
            content.contains("onSplitLapClick: (() -> Unit)? = null")
        )
    }

    @Test
    fun testCreateLapValidationAndPreviewLogic() {
        // Build mock samples
        val samples = listOf(
            createPoint(rowId = 1, index = 0, timeS = 0, distM = 0.0, lapNr = 1),
            createPoint(rowId = 2, index = 1, timeS = 300, distM = 1000.0, lapNr = 1),
            createPoint(rowId = 3, index = 2, timeS = 600, distM = 2000.0, lapNr = 1),
            createPoint(rowId = 4, index = 3, timeS = 900, distM = 3000.0, lapNr = 1)
        )
        val existingLaps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 900, distanceTotalM = 3000.0, speedAverageMps = 3.33)
        )

        // Split index at start (index 0) must be invalid
        val startVal = LapSplitAndMoveCalculator.validateSplit(samples, 0, existingLaps)
        assertFalse("Split at start must be invalid", startVal.isValid)

        // Split index at end (index 3) must be invalid (or split at boundary)
        val endVal = LapSplitAndMoveCalculator.validateSplit(samples, 3, existingLaps)
        // If splitSampleIndex is 3 (endIndex), splitSampleIndex <= startIndex || splitSampleIndex > endIndex is false,
        // but duration2S = 0 and distance2M = 0 -> invalid
        assertFalse("Split at end must be invalid due to zero duration second lap", endVal.isValid)

        // Split index at index 2 must be valid
        val midVal = LapSplitAndMoveCalculator.validateSplit(samples, 2, existingLaps)
        assertTrue("Split at midpoint must be valid", midVal.isValid)

        // Preview calculation verification
        val lapBefore = midVal.previewLapBefore
        assertNotNull("Preview lap before must exist", lapBefore)
        assertEquals(1, lapBefore!!.lapNr)
        assertEquals(300, lapBefore.timeTotalS)
        assertEquals(1000.0, lapBefore.distanceTotalM, 0.001)

        val lapAfter = midVal.previewLapAfter
        assertNotNull("Preview lap after must exist", lapAfter)
        assertEquals(2, lapAfter!!.lapNr)
        assertEquals(300, lapAfter.timeTotalS)
        assertEquals(1000.0, lapAfter.distanceTotalM, 0.001)
    }
}
