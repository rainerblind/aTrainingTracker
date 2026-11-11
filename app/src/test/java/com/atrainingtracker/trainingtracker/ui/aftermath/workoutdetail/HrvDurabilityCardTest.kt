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

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract, host mounting, test tags, and localization tests for [HrvDurabilityCard]
 * pursuant to REQ-PER-038, Rule 23, and Rule 81 (TST-PER-046.6, TST-PER-046.7).
 */
class HrvDurabilityCardTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app", relativePath),
            File("../app", relativePath),
            File(File("").absoluteFile, relativePath),
            File(File("").absoluteFile, "app/$relativePath"),
            File(File("").absoluteFile.parentFile, "app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Could not find file '$relativePath' in candidates: ${candidates.map { it.absolutePath }}")
    }

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
        val contentFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt")
        assertTrue("WorkoutDetailTabbedContent.kt must exist", contentFile.exists())

        val source = contentFile.readText()
        assertTrue(
            "WorkoutDetailTabbedContent must import HrvDurabilityCard",
            source.contains("import com.atrainingtracker.trainingtracker.ui.aftermath.workoutdetail.HrvDurabilityCard")
        )
        assertTrue(
            "WorkoutDetailTabbedContent must invoke HrvDurabilityCard",
            source.contains("HrvDurabilityCard(")
        )
    }

    @Test
    fun `HrvDurabilityCard defines all required test tags (TST-PER-046_6)`() {
        val cardFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutdetail/HrvDurabilityCard.kt")
        assertTrue("HrvDurabilityCard.kt must exist", cardFile.exists())

        val source = cardFile.readText()
        assertTrue("Card must have hrv_durability_card testTag", source.contains("testTag(\"hrv_durability_card\")"))
        assertTrue("Card must have hrv_durability_fresh_baseline testTag", source.contains("testTag(\"hrv_durability_fresh_baseline\")"))
        assertTrue("Card must have hrv_durability_late_mean testTag", source.contains("testTag(\"hrv_durability_late_mean\")"))
        assertTrue("Card must have hrv_durability_drift testTag", source.contains("testTag(\"hrv_durability_drift\")"))
        assertTrue("Card must have hrv_durability_trajectory testTag", source.contains("testTag(\"hrv_durability_trajectory\")"))
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

        val stringsFile = findFile("src/main/res/values/strings_hrv.xml")
        val content = stringsFile.readText()
        for (key in requiredKeys) {
            assertTrue("Key $key must exist in strings_hrv.xml", content.contains("name=\"$key\""))
        }
    }
}
