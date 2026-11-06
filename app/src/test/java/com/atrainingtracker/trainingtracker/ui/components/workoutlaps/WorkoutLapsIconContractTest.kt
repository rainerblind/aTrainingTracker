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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Contract test verifying dedicated lap split and move boundary iconography,
 * TrackType.BEST canonical blue polyline usage, and PathPreviewMap marker customization
 * (REQ-UI-449, REQ-UI-450, TST-UI-449.3, TST-UI-450.3, ATT-5480).
 */
class WorkoutLapsIconContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private fun getSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(projectRoot, relativePath),
            File(relativePath),
            File(projectRoot, "app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: File(projectRoot, relativePath)
    }

    @Test
    fun testVectorDrawables_existAndAreValidXml() {
        val scissorsFile = getSourceFile("app/src/main/res/drawable/ic_lap_split_scissors.xml")
        assertTrue("ic_lap_split_scissors.xml must exist at ${scissorsFile.absolutePath}", scissorsFile.exists())

        val moveFile = getSourceFile("app/src/main/res/drawable/ic_lap_move_boundary.xml")
        assertTrue("ic_lap_move_boundary.xml must exist at ${moveFile.absolutePath}", moveFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()

        // Validate scissors XML DOM
        val scissorsDoc = builder.parse(scissorsFile)
        val scissorsRoot = scissorsDoc.documentElement
        assertTrue("Root element must be vector", scissorsRoot.nodeName == "vector")
        assertTrue("Scissors must have path child", scissorsRoot.getElementsByTagName("path").length > 0)

        // Validate move boundary XML DOM
        val moveDoc = builder.parse(moveFile)
        val moveRoot = moveDoc.documentElement
        assertTrue("Root element must be vector", moveRoot.nodeName == "vector")
        assertTrue("Move boundary must have path child", moveRoot.getElementsByTagName("path").length > 0)
    }

    @Test
    fun testPathPreviewMap_declaresCustomMarkerParametersWithDefaults() {
        val pathPreviewFile = getSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt")
        assertTrue("PathPreviewMap.kt must exist", pathPreviewFile.exists())
        val content = pathPreviewFile.readText()

        assertTrue(
            "PathPreviewMap must declare apexIconRes parameter with default R.drawable.ic_distance",
            content.contains("apexIconRes: Int = R.drawable.ic_distance")
        )
        assertTrue(
            "PathPreviewMap must pass apexIconRes to createSensorMarker",
            content.contains("createSensorMarker(context, apexIconRes, apexColor)")
        )
    }

    @Test
    fun testCreateLapOnMapSheet_wiresCanonicalBlueTrackAndScissorsIcons() {
        val sheetFile = getSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/CreateLapOnMapSheet.kt")
        assertTrue("CreateLapOnMapSheet.kt must exist", sheetFile.exists())
        val content = sheetFile.readText()

        assertTrue("Must use TrackType.BEST for canonical blue polyline", content.contains("type = TrackType.BEST"))
        assertTrue("Must pass ic_lap_split_scissors to PathPreviewMap", content.contains("apexIconRes = R.drawable.ic_lap_split_scissors"))
        assertTrue("Must wire ic_lap_split_scissors in sheet header or confirm", content.contains("R.drawable.ic_lap_split_scissors"))
    }

    @Test
    fun testMoveLapBoundaryOnMapSheet_wiresCanonicalBlueTrackAndMoveIcons() {
        val sheetFile = getSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/MoveLapBoundaryOnMapSheet.kt")
        assertTrue("MoveLapBoundaryOnMapSheet.kt must exist", sheetFile.exists())
        val content = sheetFile.readText()

        assertTrue("Must use TrackType.BEST for canonical blue polyline", content.contains("type = TrackType.BEST"))
        assertTrue("Must pass ic_lap_move_boundary to PathPreviewMap", content.contains("apexIconRes = R.drawable.ic_lap_move_boundary"))
        assertTrue("Must wire ic_lap_move_boundary in sheet header or confirm", content.contains("R.drawable.ic_lap_move_boundary"))
    }

    @Test
    fun testAftermathActionButtons_wireDedicatedIcons() {
        // WorkoutLaps.kt
        val workoutLapsContent = getSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt").readText()
        assertTrue("WorkoutLaps must wire ic_lap_move_boundary", workoutLapsContent.contains("R.drawable.ic_lap_move_boundary"))
        assertTrue("WorkoutLaps must wire ic_lap_split_scissors", workoutLapsContent.contains("R.drawable.ic_lap_split_scissors"))

        // LapEditBottomSheet.kt
        val lapEditContent = getSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt").readText()
        assertTrue("LapEditBottomSheet must wire ic_lap_move_boundary", lapEditContent.contains("R.drawable.ic_lap_move_boundary"))

        // LapSplitVisualizer.kt
        val visualizerContent = getSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt").readText()
        assertTrue("LapSplitVisualizer must wire ic_lap_move_boundary", visualizerContent.contains("R.drawable.ic_lap_move_boundary"))
        assertTrue("LapSplitVisualizer must wire ic_lap_split_scissors", visualizerContent.contains("R.drawable.ic_lap_split_scissors"))

        // MapDetailLayout.kt
        val mapDetailContent = getSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt").readText()
        assertTrue("MapDetailLayout must wire ic_lap_split_scissors", mapDetailContent.contains("R.drawable.ic_lap_split_scissors"))
    }
}
