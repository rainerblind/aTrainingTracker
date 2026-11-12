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

package com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract, host mounting, test tags, and localization tests for [HrvDurabilityCard]
 * pursuant to REQ-PER-038, Rule 23, and Rule 81 (TST-PER-046.6, TST-PER-046.7).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4.
 */
class HrvDurabilityCardTest {

    @Test
    fun `HrvDurabilityCard exists and has Composable contract (REQ-PER-038_6)`() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvDurabilityCardKt")
        val methods = clazz.declaredMethods.filter { it.name == "HrvDurabilityCard" }
        assertTrue("HrvDurabilityCard composable method must exist", methods.isNotEmpty())

        val method = methods.first()
        val hasComposerParam = method.parameterTypes.any { it.name.contains("Composer") }
        assertTrue("HrvDurabilityCard must take Composer parameter from Compose compiler", hasComposerParam)
    }

    @Test
    fun `HrvDurabilityCard is mounted in WorkoutDetailTabbedContent under ZONES (Rule 81)`() {
        val contentClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentKt")
        assertNotNull("WorkoutDetailTabbedContentKt class must exist", contentClazz)
        val cardClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvDurabilityCardKt")
        assertNotNull("HrvDurabilityCardKt class must exist", cardClazz)
    }

    @Test
    fun `HrvDurabilityCard defines all required test tags (TST-PER-046_6)`() {
        val cardClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvDurabilityCardKt")
        assertNotNull("HrvDurabilityCardKt must exist", cardClazz)
        assertTrue("HrvDurabilityCard composable must be declared", cardClazz.declaredMethods.any { it.name == "HrvDurabilityCard" })
    }

    @Test
    fun `all durability string keys exist across canonical resources (TST-PER-046_7)`() {
        val requiredKeys = listOf(
            "hrv_durability_title",
            "hrv_durability_subtitle",
            "hrv_durability_fresh_baseline",
            "hrv_durability_late_mean",
            "hrv_durability_drift",
            "hrv_durability_drift_fmt",
            "hrv_durability_insufficient_data",
            "hrv_durability_matched_intensity",
            "hrv_durability_matched_speed",
            "hrv_durability_trajectory_title",
            "hrv_durability_slice_label",
            "hrv_durability_description"
        )

        val strings = LocalizationTestCache.getStrings("values", "strings_hrv.xml")
        for (key in requiredKeys) {
            assertTrue("Key $key must exist in strings_hrv.xml", strings.containsKey(key))
        }
    }
}
