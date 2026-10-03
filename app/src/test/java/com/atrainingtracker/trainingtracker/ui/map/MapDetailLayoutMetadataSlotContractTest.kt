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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural and architectural contract tests for [MapDetailLayout] verifying the upper metadata slot
 * architecture for immediate post-workout notes and extrema visibility (TST-UI-204.1 / REQ-UI-245 / ATT-2112).
 */
class MapDetailLayoutMetadataSlotContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val mapDetailLayoutFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt")
    }

    @Test
    fun testMapDetailLayout_declaresMetadataContentParameterWithDefaultNull() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "MapDetailLayout must declare optional slotted composable metadataContent parameter with default null",
            Regex("""metadataContent:\s*\(@Composable\s+ColumnScope\.\(\)\s*->\s*Unit\)\?\s*=\s*null""").containsMatchIn(content)
        )
    }

    @Test
    fun testMapDetailLayout_evaluatesMetadataContentInHasScrollableContent() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        assertTrue(
            "hasScrollableContent must evaluate metadataContent != null",
            content.contains("metadataContent != null || analyticsContent != null || hasTelemetryGraphs")
        )
    }

    @Test
    fun testMapDetailLayout_rendersMetadataContentAtTopOfLowerColumnBeforeGraphs() {
        assertTrue("MapDetailLayout.kt must exist", mapDetailLayoutFile.exists())
        val content = mapDetailLayoutFile.readText()

        val lowerColIdx = content.indexOf("val lowerColumn:")
        assertTrue("lowerColumn must be declared", lowerColIdx > 0)

        val metadataSlotIdx = content.indexOf("metadataContent?.let", lowerColIdx)
        assertTrue("metadataContent?.let must be rendered inside lowerColumn", metadataSlotIdx > lowerColIdx)

        val graphsIdx = content.indexOf("if (showElevationProfile || hasTelemetryGraphs)", metadataSlotIdx)
        assertTrue("metadataContent must precede elevation profile and telemetry graphs in lowerColumn", graphsIdx > metadataSlotIdx)
    }
}
