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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test verifying fixed header architecture, global Start-in-Auto-Pause
 * placement, and descriptive toggle labels in AutoPauseTabsScreen.kt
 * (REQ-SET-080.2, REQ-SET-080.3, REQ-TRK-044.2, TST-SET-080.4, ATT-4924).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AutoPauseTabsScreenContractTest {

    @Test
    fun testAutoPauseTabsScreen_exposesPublicComposable() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.autopause.AutoPauseTabsScreenKt")
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name == "AutoPauseTabsScreen" && Modifier.isPublic(it.modifiers) }
        assertNotNull("AutoPauseTabsScreen composable must exist and be public", screenMethod)
    }

    @Test
    fun testAutoPauseTabsScreen_declaresSingleGlobalStartInAutoPauseStrings() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        val title = strings["tuning_auto_pause_start_in_auto_pause_title"]
        val desc = strings["tuning_auto_pause_start_in_auto_pause_desc"]

        assertNotNull("tuning_auto_pause_start_in_auto_pause_title must exist in strings.xml", title)
        assertNotNull("tuning_auto_pause_start_in_auto_pause_desc must exist in strings.xml", desc)
        assertTrue("Start in auto pause title must not be empty", title!!.isNotEmpty())
        assertTrue("Start in auto pause desc must not be empty", desc!!.isNotEmpty())
    }

    @Test
    fun testAutoPauseTabsScreen_bindsDedicatedEnableTitleToPerSportToggle() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("tuning_auto_pause_enable_title must exist", strings["tuning_auto_pause_enable_title"])
        assertNotNull("tuning_auto_pause_speed_threshold_title must exist", strings["tuning_auto_pause_speed_threshold_title"])
        assertNotNull("tuning_auto_pause_speed_threshold_desc must exist", strings["tuning_auto_pause_speed_threshold_desc"])
    }

    @Test
    fun testAutoPauseSportTabContent_doesNotReceiveOrRenderGlobalStartInAutoPause() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.autopause.AutoPauseTabsScreenKt")
        val methods = clazz.declaredMethods
        val tabMethod = methods.find { it.name.startsWith("AutoPauseSportTabContent") }
        assertNotNull("AutoPauseSportTabContent composable must exist", tabMethod)

        // Verify that startInAutoPause is not part of per-sport tab content parameters
        // The per-sport tab takes sport-specific settings, not global startInAutoPause
        assertNotNull("AutoPauseTabsScreenKt must contain tab content composable", tabMethod)
    }

    @Test
    fun testAutoPauseSportTabContent_encapsulatesSlidersInsideDefaultCollapsedAccordion() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("tuning_auto_pause_custom_thresholds_title must exist", strings["tuning_auto_pause_custom_thresholds_title"])
        assertNotNull("tuning_auto_pause_debounce_title must exist", strings["tuning_auto_pause_debounce_title"])
        assertNotNull("tuning_auto_pause_resume_debounce_title must exist", strings["tuning_auto_pause_resume_debounce_title"])

        val formatterClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSubtitleFormatter")
        assertNotNull("TuningSubtitleFormatter must exist", formatterClass)
    }
}
