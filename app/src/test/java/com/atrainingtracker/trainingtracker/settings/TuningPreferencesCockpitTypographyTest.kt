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

package com.atrainingtracker.trainingtracker.settings

import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontFamily
import com.atrainingtracker.trainingtracker.ui.tracking.typography.CockpitFontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying Cockpit Typography configuration defaults, serialization,
 * and defensive deserialization in [TuningConfig] and [TuningPreferencesDefaults] (REQ-UI-212 / TST-UI-166).
 */
class TuningPreferencesCockpitTypographyTest {

    @Test
    fun defaultValues_conformToEstablishedBehavior() {
        val config = TuningConfig()
        assertEquals(CockpitFontFamily.SYSTEM_DEFAULT, config.cockpitFontFamily)
        assertEquals(CockpitFontWeight.SEMI_BOLD, config.cockpitFontWeight)
        assertEquals(CockpitFontFamily.SYSTEM_DEFAULT, TuningPreferencesDefaults.COCKPIT_FONT_FAMILY)
        assertEquals(CockpitFontWeight.SEMI_BOLD, TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT)
    }

    @Test
    fun allFontFamilies_serializeAndDeserializeCleanly() {
        for (family in CockpitFontFamily.values()) {
            val serialized = family.name
            val deserialized = CockpitFontFamily.valueOf(serialized)
            assertEquals(family, deserialized)
        }
    }

    @Test
    fun allFontWeights_serializeAndDeserializeCleanly() {
        for (weight in CockpitFontWeight.values()) {
            val serialized = weight.name
            val deserialized = CockpitFontWeight.valueOf(serialized)
            assertEquals(weight, deserialized)
        }
    }

    @Test
    fun defensiveDeserialization_fallsBackToDefaultsOnCorruptedInput() {
        val invalidFamilyString = "NON_EXISTENT_FONT_FAMILY"
        val resolvedFamily = try {
            CockpitFontFamily.valueOf(invalidFamilyString)
        } catch (e: IllegalArgumentException) {
            TuningPreferencesDefaults.COCKPIT_FONT_FAMILY
        }
        assertEquals(CockpitFontFamily.SYSTEM_DEFAULT, resolvedFamily)

        val invalidWeightString = "EXTRA_SUPER_BOLD"
        val resolvedWeight = try {
            CockpitFontWeight.valueOf(invalidWeightString)
        } catch (e: IllegalArgumentException) {
            TuningPreferencesDefaults.COCKPIT_FONT_WEIGHT
        }
        assertEquals(CockpitFontWeight.SEMI_BOLD, resolvedWeight)
    }

    @Test
    fun tuningConfig_customCopyRetainsValues() {
        val config = TuningConfig(
            cockpitFontFamily = CockpitFontFamily.SEVEN_SEGMENT,
            cockpitFontWeight = CockpitFontWeight.BOLD
        )
        assertEquals(CockpitFontFamily.SEVEN_SEGMENT, config.cockpitFontFamily)
        assertEquals(CockpitFontWeight.BOLD, config.cockpitFontWeight)
    }
}
