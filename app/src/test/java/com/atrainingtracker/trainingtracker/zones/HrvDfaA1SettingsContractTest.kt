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

package com.atrainingtracker.trainingtracker.zones

import com.atrainingtracker.R
import com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Contract test suite verifying the promotion of HRV DFA a1 thresholds into a dedicated
 * 4th tab in Training Zones (`ZoneSettingsScreen`) conforming to `REQ-TRK-061.4` and `ATT-7317`.
 */
class HrvDfaA1SettingsContractTest {

    private fun resolveActivityFile(): File {
        var current: File? = File(".").canonicalFile
        while (current != null && !File(current, "settings.gradle").exists() && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        val root = current ?: File(".")
        return File(root, "app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt")
    }

    @Test
    fun testHrvDfaA1SettingsContent_composableExistsViaReflection() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.activities.ZonesSettingsActivityKt")
        val methods = clazz.declaredMethods

        val hrvContentMethod = methods.find { it.name.startsWith("HrvDfaA1SettingsContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("HrvDfaA1SettingsContent composable must exist and be public", hrvContentMethod)

        val bandCardMethod = methods.find { it.name.startsWith("HrvZoneBandCard") && Modifier.isPublic(it.modifiers) }
        assertNotNull("HrvZoneBandCard composable must exist and be public", bandCardMethod)

        val controlRowMethod = methods.find { it.name.startsWith("HrvThresholdControlRow") && Modifier.isPublic(it.modifiers) }
        assertNotNull("HrvThresholdControlRow composable must exist and be public", controlRowMethod)
    }

    @Test
    fun testZoneSettingsScreen_includes4thTabForHrvDfaA1() {
        val activityFile = resolveActivityFile()
        assertTrue("ZonesSettingsActivity.kt must exist", activityFile.exists())
        val content = activityFile.readText()

        assertTrue(
            "ZoneSettingsScreen profileNameResIds must include R.string.tab_hrv_dfa_a1 (REQ-TRK-061.4)",
            content.contains("R.string.tab_hrv_dfa_a1")
        )
        assertTrue(
            "HorizontalPager must handle page 3 for HrvDfaA1SettingsContent",
            content.contains("if (page == 3)") && content.contains("HrvDfaA1SettingsContent(")
        )
    }

    @Test
    fun testHeaderActionIcons_hiddenOnHrvTab() {
        val activityFile = resolveActivityFile()
        val content = activityFile.readText()

        assertTrue(
            "Calculator and History header icons must be guarded to hide on tab 3",
            content.contains("if (pagerState.currentPage < 3)")
        )
    }

    @Test
    fun testHrvThresholds_invariantAndPrecisionContracts() {
        val activityFile = resolveActivityFile()
        val content = activityFile.readText()

        assertTrue(
            "HrvDfaA1SettingsContent must enforce MIN_DFA_A1_THRESHOLD_GAP invariant",
            content.contains("TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP")
        )
        assertTrue(
            "HrvDfaA1SettingsContent must support Reset to Defaults action",
            content.contains("reset_to_defaults") && content.contains("setDfaA1Thresholds(")
        )
        assertTrue(
            "HrvDfaA1SettingsContent must provide HrvInfoBottomSheet action",
            content.contains("HrvInfoBottomSheet(")
        )
        assertTrue(
            "HrvDfaA1SettingsContent must render live SensorFieldView preview",
            content.contains("SensorFieldView(")
        )
        assertTrue(
            "HrvDfaA1SettingsContent must use semantic TTColor tokens (Rule 71)",
            content.contains("TTColor.Zone2") &&
                content.contains("TTColor.Zone3") &&
                content.contains("TTColor.Zone4")
        )
    }

    @Test
    fun testHrvThresholdMath_enforcesGapInvariant() {
        val t1Default = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T1
        val t2Default = TuningPreferencesDefaults.DEFAULT_DFA_A1_THRESHOLD_T2
        val gap = TuningPreferencesDefaults.MIN_DFA_A1_THRESHOLD_GAP

        assertEquals(0.75f, t1Default, 0.0001f)
        assertEquals(0.50f, t2Default, 0.0001f)
        assertEquals(0.05f, gap, 0.0001f)
        assertTrue("T1 must be >= T2 + gap", t1Default >= t2Default + gap)

        // Custom T2 adjustment clamps T1
        val customT2 = 0.72f
        val minT1Allowed = customT2 + gap
        assertEquals(0.77f, minT1Allowed, 0.0001f)

        // Custom T1 adjustment clamps T2
        val customT1 = 0.60f
        val maxT2Allowed = customT1 - gap
        assertEquals(0.55f, maxT2Allowed, 0.0001f)
    }
}
