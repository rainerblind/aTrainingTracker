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

package com.atrainingtracker.trainingtracker.ui.settings.autopause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract test verifying fixed header architecture, global Start-in-Auto-Pause
 * placement, and descriptive toggle labels in AutoPauseTabsScreen.kt
 * (REQ-SET-080.2, REQ-SET-080.3, REQ-TRK-044.2, TST-SET-080.4, ATT-4924).
 */
class AutoPauseTabsScreenContractTest {

    private fun resolveSourceFile(relativePath: String): File {
        return sequenceOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        ).firstOrNull { it.exists() } ?: File(relativePath)
    }

    @Test
    fun testAutoPauseTabsScreen_doesNotUseCollapsingHeaderOrNestedScroll() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/autopause/AutoPauseTabsScreen.kt")
        assertTrue("AutoPauseTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        assertFalse(
            "AutoPauseTabsScreen must not use CollapsingAppBarNestedScrollConnection",
            content.contains("CollapsingAppBarNestedScrollConnection")
        )
        assertFalse(
            "AutoPauseTabsScreen must not use appBarOffset",
            content.contains("appBarOffset")
        )
        assertFalse(
            "AutoPauseTabsScreen must not use nestedScroll Modifier",
            content.contains("nestedScroll(")
        )
    }

    @Test
    fun testAutoPauseTabsScreen_declaresSingleGlobalStartInAutoPauseCardAboveTabs() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/autopause/AutoPauseTabsScreen.kt")
        assertTrue("AutoPauseTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        // Count occurrences of tuning_auto_pause_start_in_auto_pause_title
        val globalTitleOccurrences = Regex("R\\.string\\.tuning_auto_pause_start_in_auto_pause_title")
            .findAll(content).count()
        assertEquals(
            "tuning_auto_pause_start_in_auto_pause_title must appear exactly once across AutoPauseTabsScreen.kt",
            1,
            globalTitleOccurrences
        )

        val globalDescOccurrences = Regex("R\\.string\\.tuning_auto_pause_start_in_auto_pause_desc")
            .findAll(content).count()
        assertEquals(
            "tuning_auto_pause_start_in_auto_pause_desc must appear exactly once across AutoPauseTabsScreen.kt",
            1,
            globalDescOccurrences
        )

        // Verify the global card appears before PrimaryScrollableTabRow inside AutoPauseTabsScreen
        val screenBody = content.substringAfter("fun AutoPauseTabsScreen(")
            .substringBefore("private fun AutoPauseSportTabContent(")
        val cardIndex = screenBody.indexOf("R.string.tuning_auto_pause_start_in_auto_pause_title")
        val tabRowIndex = screenBody.indexOf("PrimaryScrollableTabRow")
        assertTrue(
            "Global Start in Auto-Pause card must precede PrimaryScrollableTabRow in screen body",
            cardIndex in 0..<tabRowIndex
        )
    }

    @Test
    fun testAutoPauseTabsScreen_bindsDedicatedEnableTitleToPerSportToggle() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/autopause/AutoPauseTabsScreen.kt")
        assertTrue("AutoPauseTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        assertTrue(
            "AutoPauseTabsScreen must bind R.string.tuning_auto_pause_enable_title for per-sport switch",
            content.contains("R.string.tuning_auto_pause_enable_title")
        )
        assertTrue(
            "AutoPauseTabsScreen must bind R.string.tuning_auto_pause_speed_threshold_title",
            content.contains("R.string.tuning_auto_pause_speed_threshold_title")
        )
        assertTrue(
            "AutoPauseTabsScreen must bind R.string.tuning_auto_pause_speed_threshold_desc",
            content.contains("R.string.tuning_auto_pause_speed_threshold_desc")
        )
    }

    @Test
    fun testAutoPauseSportTabContent_doesNotReceiveOrRenderGlobalStartInAutoPause() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/autopause/AutoPauseTabsScreen.kt")
        assertTrue("AutoPauseTabsScreen.kt must exist", file.exists())
        val content = file.readText()

        // Extract AutoPauseSportTabContent function body
        val tabContentDecl = content.substringAfter("private fun AutoPauseSportTabContent(")
            .substringBefore("@Preview")

        assertFalse(
            "AutoPauseSportTabContent signature must not accept startInAutoPauseEnabled",
            tabContentDecl.contains("startInAutoPauseEnabled")
        )
        assertFalse(
            "AutoPauseSportTabContent signature must not accept onStartInAutoPauseChange",
            tabContentDecl.contains("onStartInAutoPauseChange")
        )
        assertFalse(
            "AutoPauseSportTabContent body must not reference tuning_auto_pause_start_in_auto_pause_title",
            tabContentDecl.contains("tuning_auto_pause_start_in_auto_pause_title")
        )
    }
}
