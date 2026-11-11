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

package com.atrainingtracker.trainingtracker.ui.hrv

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract, Rule 81 host navigation mounting, and UI component tests for [HrvRampTestScreen]
 * implementing TST-TRK-072.5 pursuant to REQ-TRK-072.
 */
class HrvRampTestScreenTest {

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
    fun testHrvRampTestScreenComposableContract_TST_TRK_072_5() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.hrv.HrvRampTestScreenKt")
        val methods = clazz.declaredMethods.filter { it.name == "HrvRampTestScreen" }
        assertTrue("HrvRampTestScreen composable method must exist", methods.isNotEmpty())

        val method = methods.first()
        val hasComposerParam = method.parameterTypes.any { it.name.contains("Composer") }
        assertTrue("HrvRampTestScreen must take Composer parameter from Compose compiler", hasComposerParam)
    }

    @Test
    fun testRule81HostNavigationMountingInATrainingTrackerApp_TST_TRK_072_5() {
        // 1. Verify route definition in NavRoutes
        val navRoutesFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt")
        assertTrue("NavRoutes.kt must exist", navRoutesFile.exists())
        val navRoutesContent = navRoutesFile.readText()
        assertTrue("NavRoutes must define HRV_TEST", navRoutesContent.contains("const val HRV_TEST = \"hrv_test\""))

        // 2. Verify destination registration in ATrainingTrackerApp
        val appNavFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt")
        assertTrue("ATrainingTrackerApp.kt must exist", appNavFile.exists())
        val appNavContent = appNavFile.readText()
        assertTrue(
            "ATrainingTrackerApp must wire composable(NavRoutes.HRV_TEST)",
            appNavContent.contains("composable(NavRoutes.HRV_TEST)")
        )
        assertTrue(
            "ATrainingTrackerApp must render HrvRampTestScreen",
            appNavContent.contains("HrvRampTestScreen(")
        )
    }

    @Test
    fun testRule81HostNavigationMountingInAthleteProfileScreen_TST_TRK_072_5() {
        val profileFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileScreen.kt")
        assertTrue("AthleteProfileScreen.kt must exist", profileFile.exists())
        val profileContent = profileFile.readText()

        assertTrue(
            "AthleteProfileScreen must accept onNavigateToHrvTest parameter",
            profileContent.contains("onNavigateToHrvTest: (() -> Unit)? = null")
        )
        assertTrue(
            "AthleteProfileScreen must render CTA button with btn_guided_hrv_test tag",
            profileContent.contains("testTag(\"btn_guided_hrv_test\")")
        )
    }

    @Test
    fun testRequiredTestTagsPresentInHrvRampTestScreen_TST_TRK_072_5() {
        val screenFile = findFile("src/main/java/com/atrainingtracker/trainingtracker/ui/hrv/HrvRampTestScreen.kt")
        assertTrue("HrvRampTestScreen.kt must exist", screenFile.exists())
        val content = screenFile.readText()

        val requiredTags = listOf(
            "hrv_test_title",
            "hrv_test_sport_selector",
            "hrv_test_protocol_selector",
            "hrv_test_preflight_button",
            "hrv_test_preflight_card",
            "hrv_test_start_button",
            "hrv_test_active_card",
            "hrv_test_target_intensity",
            "hrv_test_timer",
            "hrv_test_live_a1",
            "hrv_test_early_complete_banner",
            "hrv_test_cooldown_button",
            "hrv_test_finish_button"
        )

        for (tag in requiredTags) {
            assertTrue("HrvRampTestScreen must include testTag('$tag')", content.contains("testTag(\"$tag\")"))
        }
    }

    @Test
    fun testStringKeysPresenceInHrvStringsXml_TST_TRK_072_6() {
        val requiredKeys = listOf(
            "hrv_test_title",
            "hrv_test_subtitle",
            "hrv_test_protocol_label",
            "hrv_test_protocol_step",
            "hrv_test_protocol_ramp",
            "hrv_test_sport_label",
            "hrv_test_sport_cycling",
            "hrv_test_sport_running",
            "hrv_test_step_duration_label",
            "hrv_test_step_duration_4min",
            "hrv_test_step_duration_3min",
            "hrv_test_start_load_label",
            "hrv_test_increment_label",
            "hrv_test_warmup_label",
            "hrv_test_warmup_desc",
            "hrv_test_preflight_title",
            "hrv_test_preflight_checking",
            "hrv_test_preflight_pass",
            "hrv_test_preflight_warning",
            "hrv_test_preflight_fail",
            "hrv_test_preflight_no_sensor",
            "hrv_test_phase_warmup",
            "hrv_test_phase_step",
            "hrv_test_phase_ramp",
            "hrv_test_phase_cooldown",
            "hrv_test_target_power",
            "hrv_test_target_pace",
            "hrv_test_time_remaining",
            "hrv_test_live_a1",
            "hrv_test_live_artifacts",
            "hrv_test_early_complete_title",
            "hrv_test_early_complete_desc",
            "hrv_test_btn_start",
            "hrv_test_btn_finish",
            "hrv_test_btn_cooldown",
            "hrv_test_btn_cancel"
        )

        val defaultFile = findFile("src/main/res/values/strings_hrv.xml")
        assertTrue("Default strings_hrv.xml must exist", defaultFile.exists())
        val defaultContent = defaultFile.readText()

        for (key in requiredKeys) {
            assertTrue("Key '$key' must exist in values/strings_hrv.xml", defaultContent.contains("name=\"$key\""))
        }
    }
}
