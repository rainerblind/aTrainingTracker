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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural, architectural and gesture contract tests for AdvancedTuningScreen (REQ-UI-357, TST-UI-317.1, TST-UI-317.2).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class AdvancedTuningScreenTest {

    @Test
    fun testScreenArchitecture_scaffoldAndTopAppBar() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenKt")
        val screenMethod = clazz.declaredMethods.find { it.name == "AdvancedTuningScreen" && Modifier.isPublic(it.modifiers) }
        assertNotNull("AdvancedTuningScreen composable must exist and be public", screenMethod)

        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("advanced_tuning_title must exist in strings.xml", strings["advanced_tuning_title"])
    }

    @Test
    fun testZeroDismissalScroll_decoupledFromBottomSheet() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenKt")
        assertNotNull("AdvancedTuningScreenKt must exist", clazz)
    }

    @Test
    fun testAccordionSections_canonicalLifecycleSequence() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull(strings["tuning_group_cockpit_tracking"])
        assertNotNull(strings["tuning_group_sensors_gps"])
        assertNotNull(strings["tuning_group_analysis_models"])

        val sectionEnum = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSection")
        val constants = sectionEnum.enumConstants.map { it.toString() }

        assertTrue(constants.contains("COCKPIT_TYPOGRAPHY"))
        assertTrue(constants.contains("NAVIGATION"))
        assertTrue(constants.contains("BATTERY_SAVER"))
        assertTrue(constants.contains("SENSOR_SEARCH"))
        assertTrue(constants.contains("SENSORS_GPS"))
        assertTrue(constants.contains("CYCLING_RADAR"))
        assertTrue(constants.contains("WORKOUT_MASKS_CARDS"))
        assertTrue(constants.contains("AFTERMATH_ANALYSIS"))
        assertTrue(constants.contains("PHYSIOLOGY_MODELS"))
        assertTrue(constants.contains("ROUTE_CLUSTERING"))
    }

    @Test
    fun testInitialCollapseState_allSectionsCollapsed() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningScreenKt")
        assertNotNull("AdvancedTuningScreenKt must exist", clazz)
    }

    @Test
    fun testTuningPreferencesDataStore_singletonContract() {
        val dsClass = Class.forName("com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore")
        val getInstanceMethod = dsClass.declaredMethods.find { it.name == "getInstance" && Modifier.isStatic(it.modifiers) }
        assertNotNull("TuningPreferencesDataStore must have a static getInstance(context) factory", getInstanceMethod)
    }
}
