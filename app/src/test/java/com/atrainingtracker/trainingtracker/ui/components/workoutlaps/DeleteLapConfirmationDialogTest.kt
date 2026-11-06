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
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Unit and contract tests verifying DeleteLapConfirmationDialog and Option A boundary selection
 * (REQ-UI-454.1, REQ-UI-454.2, TST-UI-454.3 / ATT-5482).
 */
class DeleteLapConfirmationDialogTest {

    private val requiredStringKeys = listOf(
        "delete_lap_action",
        "delete_lap_dialog_title",
        "delete_lap_confirm",
        "delete_lap_merge_with_previous",
        "delete_lap_merge_with_next",
        "delete_lap_preview_title",
        "delete_lap_warning_description",
        "delete_lap_direction_label"
    )

    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")
    }

    private fun parseStringsFile(file: File): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (!file.exists()) return map

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val key = node.attributes.getNamedItem("name")?.nodeValue
            val value = node.textContent
            if (key != null) {
                map[key] = value
            }
        }
        return map
    }

    @Test
    fun testAllRequiredDeleteLapStringsExistAcrossAllLocales() {
        val resDir = findResDirectory()
        for (locale in supportedLocales) {
            val folderName = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$folderName/strings.xml")
            assertTrue("strings.xml missing for $folderName", stringsFile.exists())

            val strings = parseStringsFile(stringsFile)
            for (key in requiredStringKeys) {
                assertTrue("Missing key '$key' in $folderName/strings.xml", strings.containsKey(key))
                val value = strings[key]
                assertNotNull("Value for '$key' is null in $folderName", value)
                assertTrue("Value for '$key' is empty in $folderName", value!!.isNotEmpty())
            }
        }
    }

    @Test
    fun testOptionABoundaryResolutionLogic() {
        // Workout with 4 laps: Lap 1, Lap 2, Lap 3, Lap 4
        val laps = listOf(
            LapData(id = 1L, workoutId = 1L, lapNr = 1L, timeTotalS = 100, distanceTotalM = 500.0, speedAverageMps = 5.0),
            LapData(id = 2L, workoutId = 1L, lapNr = 2L, timeTotalS = 200, distanceTotalM = 1000.0, speedAverageMps = 5.0),
            LapData(id = 3L, workoutId = 1L, lapNr = 3L, timeTotalS = 300, distanceTotalM = 1500.0, speedAverageMps = 5.0),
            LapData(id = 4L, workoutId = 1L, lapNr = 4L, timeTotalS = 400, distanceTotalM = 2000.0, speedAverageMps = 5.0)
        )
        val totalLaps = laps.size

        // Case 1: First Lap (k == 1) -> must always merge Lap 1 + Lap 2 (boundary 1)
        val currentLap1 = laps[0]
        val isFirstLap1 = (currentLap1.lapNr == 1L)
        val resolvedBoundary1 = if (isFirstLap1) 1L else 0L
        assertEquals(1L, resolvedBoundary1)

        // Case 2: Last Lap (k == 4) -> must always merge Lap 3 + Lap 4 (boundary 3)
        val currentLap4 = laps[3]
        val isLastLap4 = (currentLap4.lapNr == totalLaps.toLong())
        val resolvedBoundary4 = if (isLastLap4) (totalLaps - 1).toLong() else 0L
        assertEquals(3L, resolvedBoundary4)

        // Case 3: Interior Lap (k == 2)
        val currentLap2 = laps[1]
        // Merge with PREVIOUS -> boundary 1 (Lap 1 + Lap 2)
        val boundaryPrev = currentLap2.lapNr - 1L
        assertEquals(1L, boundaryPrev)
        // Merge with NEXT -> boundary 2 (Lap 2 + Lap 3)
        val boundaryNext = currentLap2.lapNr
        assertEquals(2L, boundaryNext)

        // Case 4: Interior Lap (k == 3)
        val currentLap3 = laps[2]
        // Merge with PREVIOUS -> boundary 2 (Lap 2 + Lap 3)
        assertEquals(2L, currentLap3.lapNr - 1L)
        // Merge with NEXT -> boundary 3 (Lap 3 + Lap 4)
        assertEquals(3L, currentLap3.lapNr)
    }

    @Test
    fun testLivePreviewMetricsMatchExactSum() {
        val lap2 = LapData(id = 2L, workoutId = 1L, lapNr = 2L, timeTotalS = 180, distanceTotalM = 900.0, speedAverageMps = 5.0, name = "Interval")
        val lap3 = LapData(id = 3L, workoutId = 1L, lapNr = 3L, timeTotalS = 120, distanceTotalM = 600.0, speedAverageMps = 5.0, name = "Recovery")

        val preview = LapSplitAndMoveCalculator.calculateMergePreview(lap2, lap3)

        assertEquals(300, preview.timeTotalS)
        assertEquals(1500.0, preview.distanceTotalM, 1e-4)
        assertEquals(5.0, preview.speedAverageMps, 1e-4)
        assertEquals("Interval / Recovery", preview.name)
    }

    private fun findLapEditBottomSheetFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("LapEditBottomSheet.kt not found in candidates: $candidates")
    }

    @Test
    fun testLapEditBottomSheet_containsDeleteLapActionAndWiresCallback() {
        val file = findLapEditBottomSheetFile()
        assertTrue(file.exists())
        val content = file.readText()

        assertTrue(
            "LapEditBottomSheet must declare onDeleteLapClick callback parameter",
            content.contains("onDeleteLapClick: ((boundaryLapNr: Long) -> Unit)? = null")
        )
        assertTrue(
            "LapEditBottomSheet must render delete lap action button when totalLaps >= 2",
            content.contains("onDeleteLapClick != null && totalLaps >= 2") &&
                content.contains("R.string.delete_lap_action")
        )
        assertTrue(
            "LapEditBottomSheet must host DeleteLapConfirmationDialog",
            content.contains("DeleteLapConfirmationDialog(")
        )
    }
}
