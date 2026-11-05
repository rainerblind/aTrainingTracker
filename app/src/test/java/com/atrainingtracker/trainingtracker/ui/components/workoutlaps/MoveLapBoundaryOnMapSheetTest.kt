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
 * Contract and integration tests verifying [MoveLapBoundaryOnMapSheet] composable existence,
 * parameters, design tokens, and functional boundary move preview behavior
 * (REQ-UI-450, TST-UI-450.3, ATT-908).
 */
class MoveLapBoundaryOnMapSheetTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val sheetSourceFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/MoveLapBoundaryOnMapSheet.kt")
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
    fun testMoveLapBoundaryOnMapSheet_composableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.workoutlaps.MoveLapBoundaryOnMapSheetKt")
        assertNotNull("MoveLapBoundaryOnMapSheetKt class must exist", clazz)

        val method = clazz.declaredMethods.find {
            it.name.startsWith("MoveLapBoundaryOnMapSheet") && Modifier.isPublic(it.modifiers)
        }
        assertNotNull("MoveLapBoundaryOnMapSheet composable method must exist and be public", method)
        assertTrue(Modifier.isPublic(method!!.modifiers))
    }

    @Test
    fun testMoveLapBoundaryOnMapSheet_sourceStructureAndContract() {
        assertTrue("MoveLapBoundaryOnMapSheet.kt must exist", sheetSourceFile.exists())
        val content = sheetSourceFile.readText()

        // Verify key structural components
        assertTrue("Must use AppModalBottomSheet", content.contains("AppModalBottomSheet("))
        assertTrue("Must use PathPreviewMap for track preview", content.contains("PathPreviewMap("))
        assertTrue("Must use Slider for fine scrubber adjustment", content.contains("Slider("))
        assertTrue("Must use LapSplitAndMoveCalculator for validation", content.contains("LapSplitAndMoveCalculator.validateBoundaryMove"))

        // Verify resource token references (no hardcoded strings)
        assertTrue("Must reference move_lap_boundary_title", content.contains("R.string.move_lap_boundary_title"))
        assertTrue("Must reference move_lap_boundary_confirm", content.contains("R.string.move_lap_boundary_confirm"))
        assertTrue("Must reference move_lap_boundary_lap_k_preview", content.contains("R.string.move_lap_boundary_lap_k_preview"))
        assertTrue("Must reference move_lap_boundary_lap_k_plus_1_preview", content.contains("R.string.move_lap_boundary_lap_k_plus_1_preview"))
        assertTrue("Must reference move_lap_boundary_slider_label", content.contains("R.string.move_lap_boundary_slider_label"))

        // Verify zero hardcoded color hex codes (#AARRGGBB or Color(0x...))
        assertFalse("Must not contain hardcoded Color(0x...)", content.contains("Color(0x"))
        assertFalse("Must not contain hardcoded hex color literals", Regex("#[0-9a-fA-F]{6,8}").containsMatchIn(content))
    }

    @Test
    fun testWorkoutLaps_exposesOnMoveBoundaryClick() {
        val workoutLapsFile = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt")
        assertTrue("WorkoutLaps.kt must exist", workoutLapsFile.exists())
        val content = workoutLapsFile.readText()

        assertTrue(
            "WorkoutLaps must accept onMoveBoundaryClick callback",
            content.contains("onMoveBoundaryClick: ((boundaryLapNr: Long) -> Unit)? = null")
        )
        assertTrue(
            "WorkoutLaps must display move boundary action button",
            content.contains("R.string.move_lap_boundary_action")
        )
    }

    @Test
    fun testLapSplitVisualizer_exposesOnMoveBoundaryClick() {
        val visualizerFile = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt")
        assertTrue("LapSplitVisualizer.kt must exist", visualizerFile.exists())
        val content = visualizerFile.readText()

        assertTrue(
            "LapSplitVisualizerCard must accept onMoveBoundaryClick callback",
            content.contains("onMoveBoundaryClick: ((boundaryLapNr: Long) -> Unit)? = null")
        )
    }

    @Test
    fun testLapEditBottomSheet_exposesOnMoveBoundaryClick() {
        val editFile = File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt")
        assertTrue("LapEditBottomSheet.kt must exist", editFile.exists())
        val content = editFile.readText()

        assertTrue(
            "LapEditBottomSheet must accept onMoveBoundaryClick callback",
            content.contains("onMoveBoundaryClick: ((boundaryLapNr: Long) -> Unit)? = null")
        )
    }

    @Test
    fun testMoveLapBoundaryValidationAndPreviewLogic() {
        // Build mock samples for 2 laps: Lap 1 has samples 0..2, Lap 2 has samples 3..5
        val samples = listOf(
            createPoint(rowId = 1, index = 0, timeS = 0, distM = 0.0, lapNr = 1),
            createPoint(rowId = 2, index = 1, timeS = 100, distM = 500.0, lapNr = 1),
            createPoint(rowId = 3, index = 2, timeS = 200, distM = 1000.0, lapNr = 1),
            createPoint(rowId = 4, index = 3, timeS = 300, distM = 1500.0, lapNr = 2),
            createPoint(rowId = 5, index = 4, timeS = 400, distM = 2000.0, lapNr = 2),
            createPoint(rowId = 6, index = 5, timeS = 500, distM = 2500.0, lapNr = 2)
        )
        val existingLaps = listOf(
            LapData(id = 1, workoutId = 10, lapNr = 1, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0, name = "Lap 1"),
            LapData(id = 2, workoutId = 10, lapNr = 2, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0, name = "Lap 2")
        )

        // Shifting boundary between Lap 1 and Lap 2 forward to index 4
        val result = LapSplitAndMoveCalculator.validateBoundaryMove(samples, boundaryLapNr = 1L, newBoundaryIndex = 4, existingLaps = existingLaps)
        assertTrue("Boundary move to index 4 must be valid", result.isValid)
        assertEquals(4, result.clampedSampleIndex)

        val lap1 = result.previewLapK
        assertNotNull("Preview lap 1 must exist", lap1)
        assertEquals(1L, lap1!!.lapNr)
        assertEquals(300, lap1.timeTotalS)
        assertEquals(1500.0, lap1.distanceTotalM, 0.001)

        val lap2 = result.previewLapKPlus1
        assertNotNull("Preview lap 2 must exist", lap2)
        assertEquals(2L, lap2!!.lapNr)
        assertEquals(100, lap2.timeTotalS)
        assertEquals(500.0, lap2.distanceTotalM, 0.001)
    }
}
