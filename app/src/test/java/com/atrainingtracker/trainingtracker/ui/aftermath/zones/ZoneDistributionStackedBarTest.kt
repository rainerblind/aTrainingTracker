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

package com.atrainingtracker.trainingtracker.ui.aftermath.zones

import androidx.compose.ui.graphics.Color
import com.atrainingtracker.trainingtracker.ui.theme.TTColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests verifying continuous horizontal stacked distribution bar layout math,
 * zero-duration filtering, 5-zone HR / 7-zone Power parity, and contrast contracts (REQ-UI-372 / TST-UI-335).
 */
class ZoneDistributionStackedBarTest {

    @Test
    fun testActiveEntries_filtersZeroDurationOrPercentage() {
        val entries = listOf(
            ZoneTimeEntry(zoneIndex = 1, zoneLabelResId = 0, durationSec = 300L, percentage = 25f, color = TTColor.Zone1),
            ZoneTimeEntry(zoneIndex = 2, zoneLabelResId = 0, durationSec = 600L, percentage = 50f, color = TTColor.Zone2),
            ZoneTimeEntry(zoneIndex = 3, zoneLabelResId = 0, durationSec = 300L, percentage = 25f, color = TTColor.Zone3),
            ZoneTimeEntry(zoneIndex = 4, zoneLabelResId = 0, durationSec = 0L, percentage = 0f, color = TTColor.Zone4),
            ZoneTimeEntry(zoneIndex = 5, zoneLabelResId = 0, durationSec = 0L, percentage = 0f, color = TTColor.Zone5)
        )

        val active = ZoneDistributionStackedBarMath.getActiveEntries(entries)
        assertEquals("Should only retain 3 active zones with positive duration and percentage", 3, active.size)
        assertEquals(1, active[0].zoneIndex)
        assertEquals(2, active[1].zoneIndex)
        assertEquals(3, active[2].zoneIndex)
    }

    @Test
    fun testIsAllZeroDuration_handlesEmptyAndZeroDistributions() {
        val emptyData = ZoneDistributionData(totalActiveTimeSec = 0L, entries = emptyList())
        assertTrue("Empty distribution should be recognized as all-zero", ZoneDistributionStackedBarMath.isAllZeroDuration(emptyData))

        val allZeroEntries = listOf(
            ZoneTimeEntry(zoneIndex = 1, zoneLabelResId = 0, durationSec = 0L, percentage = 0f, color = TTColor.Zone1),
            ZoneTimeEntry(zoneIndex = 2, zoneLabelResId = 0, durationSec = 0L, percentage = 0f, color = TTColor.Zone2)
        )
        val zeroData = ZoneDistributionData(totalActiveTimeSec = 0L, entries = allZeroEntries)
        assertTrue("All-zero duration distribution should be recognized as all-zero", ZoneDistributionStackedBarMath.isAllZeroDuration(zeroData))

        val positiveData = ZoneDistributionData(
            totalActiveTimeSec = 100L,
            entries = listOf(ZoneTimeEntry(1, 0, 100L, 100f, TTColor.Zone1))
        )
        assertFalse("Distribution with active time is not all-zero", ZoneDistributionStackedBarMath.isAllZeroDuration(positiveData))
    }

    @Test
    fun testStackedBar_sevenZonePowerDatasetParity() {
        val sevenZoneEntries = listOf(
            ZoneTimeEntry(1, 0, 120L, 10f, TTColor.Zone1),
            ZoneTimeEntry(2, 0, 240L, 20f, TTColor.Zone2),
            ZoneTimeEntry(3, 0, 360L, 30f, TTColor.Zone3),
            ZoneTimeEntry(4, 0, 240L, 20f, TTColor.Zone4),
            ZoneTimeEntry(5, 0, 120L, 10f, TTColor.Zone5),
            ZoneTimeEntry(6, 0, 60L, 5f, TTColor.Zone6),
            ZoneTimeEntry(7, 0, 60L, 5f, TTColor.Zone7)
        )
        val data = ZoneDistributionData(totalActiveTimeSec = 1200L, entries = sevenZoneEntries)

        val active = ZoneDistributionStackedBarMath.getActiveEntries(data.entries)
        assertEquals("All 7 zones must be active", 7, active.size)
        val totalPercentage = active.sumOf { it.percentage.toDouble() }
        assertEquals(100.0, totalPercentage, 0.001)
    }

    @Test
    fun testStackedBar_componentSourceContract() {
        val sourceFile = resolve("app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionStackedBar.kt")
        assertTrue("ZoneDistributionStackedBar.kt must exist", sourceFile.exists())
        val content = sourceFile.readText()

        assertTrue("Must use RoundedCornerShape(6.dp) for container styling",
            content.contains("RoundedCornerShape(6.dp)"))
        assertTrue("Must weight segments proportionally via entry.percentage",
            content.contains("weight(entry.percentage)"))
        assertTrue("Must enclose bar with outline border for WCAG 2.1 AA contrast",
            content.contains("BorderStroke") && content.contains("0.5.dp"))
        assertTrue("Must default showLegend to false for compact STACKED_BAR mode (REQ-UI-387)",
            Regex("""showLegend\s*:\s*Boolean\s*=\s*false""").containsMatchIn(content))
        assertTrue("Regression check: Must preserve conditional FlowRow legend rendering when showLegend is true",
            content.contains("if (showLegend && distribution.entries.isNotEmpty())") &&
                content.contains("FlowRow") &&
                content.contains("StackedBarLegendItem"))
    }

    @Test
    fun testZoneDistributionCards_stackedBarModeOmitsDetailedBreakdownList() {
        val cardFiles = listOf(
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt"
        )
        for (relPath in cardFiles) {
            val file = resolve(relPath)
            assertTrue("$relPath must exist", file.exists())
            val content = file.readText()
            assertTrue(
                "$relPath must pass showLegend = false to ZoneDistributionStackedBar in STACKED_BAR mode (REQ-UI-387)",
                Regex("""ZoneDistributionStackedBar\s*\([\s\S]*?showLegend\s*=\s*false""").containsMatchIn(content)
            )
        }
    }

    private fun resolve(relPath: String): File {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle").exists() && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        val root = current ?: File(".")
        return File(root, relPath)
    }
}
