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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test verifying that CockpitTypographySection renders discrete
 * cockpit preview tiles styled with active corner radius, border thickness, border contrast,
 * and elevation (REQ-UI-277, TST-UI-237.1).
 */
class CockpitPreviewTileContractTest {

    private fun findSectionFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/CockpitTypographySection.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/CockpitTypographySection.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/CockpitTypographySection.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("CockpitTypographySection.kt not found in candidates: $candidates")
    }

    @Test
    fun testLivePreview_usesSensorFieldStyleAndDiscreteTiles() {
        val file = findSectionFile()
        val content = file.readText()

        // 1. Dynamic style resolution from SensorFieldStyle
        assertTrue(
            "Preview must resolve shape using SensorFieldStyle.resolveShape",
            content.contains("SensorFieldStyle.resolveShape")
        )
        assertTrue(
            "Preview must resolve border using SensorFieldStyle.resolveBorder",
            content.contains("SensorFieldStyle.resolveBorder")
        )
        assertTrue(
            "Preview must resolve base style via SensorFieldStyle.forVariant",
            content.contains("SensorFieldStyle.forVariant")
        )

        // 2. Composable structure and discrete preview tiles
        assertTrue(
            "CockpitTypographySection must declare PreviewCockpitTile composable",
            content.contains("fun PreviewCockpitTile(")
        )
        assertTrue(
            "Preview must instantiate PreviewCockpitTile with shape, border, and elevation",
            content.contains("PreviewCockpitTile(") &&
                content.contains("shape = tileShape") &&
                content.contains("border = tileBorder") &&
                content.contains("elevation = tileElevation")
        )

        // 3. Spaced grid arrangement
        assertTrue(
            "Tiles must be arranged horizontally with spacing",
            content.contains("Arrangement.spacedBy(") && content.contains("gridSpacing")
        )

        // 4. Sample telemetry values and units
        assertTrue("Preview must display sample BPM metric", content.contains("\"148\"") && content.contains("\"bpm\""))
        assertTrue("Preview must display sample KM/H metric", content.contains("\"28.5\"") && content.contains("\"km/h\""))
        assertTrue("Preview must display sample TIME metric", content.contains("\"1:24:35\"") && content.contains("\"TIME\""))
    }
}
