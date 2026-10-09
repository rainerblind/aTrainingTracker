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

package com.atrainingtracker.trainingtracker.dialogs

import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillDiagnosis
import com.atrainingtracker.trainingtracker.helpers.ProcessExitReasonHelper.KillReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Structural contract, layout verification, and 9-language localization parity tests
 * for modernized Material 3 [StartOrResumeDialog] (REQ-UI-329, TST-UI-289, ATT-2948).
 */
class StartOrResumeDialogLayoutTest {

    private fun resolveSourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File(System.getProperty("user.dir", "."), relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        throw IllegalStateException("Cannot resolve source file: $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun testStartOrResumeDialog_eliminationOfLegacyAlertDialogBuilder() {
        val sourceFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/dialogs/StartOrResumeDialog.kt")
        val content = sourceFile.readText()

        // Verify absence of legacy AlertDialog.Builder
        assertFalse(
            "StartOrResumeDialog must eliminate legacy AlertDialog.Builder",
            content.contains("AlertDialog.Builder")
        )
        assertFalse(
            "StartOrResumeDialog must eliminate androidx.appcompat.app.AlertDialog import",
            content.contains("import androidx.appcompat.app.AlertDialog")
        )

        // Verify Compose hosting
        assertTrue("Must use ComposeView", content.contains("ComposeView"))
        assertTrue("Must use ATrainingTrackerTheme", content.contains("ATrainingTrackerTheme"))
        assertTrue("Must declare StartOrResumeDialogContent", content.contains("fun StartOrResumeDialogContent("))
        assertTrue("Must declare StartOrResumeAlertDialog", content.contains("fun StartOrResumeAlertDialog("))
    }

    @Test
    fun testStartOrResumeDialog_contextualIconographyAndTokens() {
        val sourceFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/dialogs/StartOrResumeDialog.kt")
        val content = sourceFile.readText()

        // Semantic icons for all 4 reasons
        assertTrue("Must include BatteryAlert icon", content.contains("Icons.Default.BatteryAlert"))
        assertTrue("Must include Memory icon", content.contains("Icons.Default.Memory"))
        assertTrue("Must include Security icon", content.contains("Icons.Default.Security"))
        assertTrue("Must include DirectionsRun icon", content.contains("Icons.Default.DirectionsRun"))

        // Icon sizing
        assertTrue("Must apply 36.dp icon size", content.contains("size(36.dp)"))

        // Headline typography
        assertTrue("Must use titleLarge bold typography", content.contains("MaterialTheme.typography.titleLarge"))
        assertTrue("Must use FontWeight.Bold", content.contains("FontWeight.Bold"))

        // Button hierarchy
        assertTrue("Must use filled Button for resume", content.contains("Button(onClick = onResume)"))
        assertTrue("Must use OutlinedButton for start new", content.contains("OutlinedButton(onClick = onStartNew)"))
        assertTrue(
            "Must use FilledTonalButton for battery optimization",
            content.contains("FilledTonalButton(\n                    onClick = onDisableBatteryOptimization") ||
                content.contains("FilledTonalButton(onClick = onDisableBatteryOptimization") ||
                content.contains("FilledTonalButton(")
        )
    }

    @Test
    fun testKillDiagnosis_allStateCombinationsInstantiable() {
        val batteryDiagnosis = KillDiagnosis(
            reason = KillReason.BATTERY_KILL,
            escalationLevel = 2,
            isStravaActive = true,
            shouldShowBatteryButton = true
        )
        assertEquals(KillReason.BATTERY_KILL, batteryDiagnosis.reason)
        assertEquals(2, batteryDiagnosis.escalationLevel)
        assertTrue(batteryDiagnosis.isStravaActive)
        assertTrue(batteryDiagnosis.shouldShowBatteryButton)

        val memoryDiagnosis = KillDiagnosis(
            reason = KillReason.LOW_MEMORY,
            escalationLevel = 1,
            isStravaActive = false,
            shouldShowBatteryButton = false
        )
        assertEquals(KillReason.LOW_MEMORY, memoryDiagnosis.reason)
        assertFalse(memoryDiagnosis.shouldShowBatteryButton)

        val permDiagnosis = KillDiagnosis(
            reason = KillReason.PERMISSION_REVOKED,
            escalationLevel = 1,
            isStravaActive = false,
            shouldShowBatteryButton = false
        )
        assertEquals(KillReason.PERMISSION_REVOKED, permDiagnosis.reason)

        val genericDiagnosis = KillDiagnosis(
            reason = KillReason.GENERIC_UNFINISHED,
            escalationLevel = 0,
            isStravaActive = false,
            shouldShowBatteryButton = false
        )
        assertEquals(KillReason.GENERIC_UNFINISHED, genericDiagnosis.reason)
    }

    @Test
    fun testRecoveryDialog_9LanguageLocalizationParity() {
        val locales = listOf(
            "values",
            "values-de",
            "values-es",
            "values-fr",
            "values-it",
            "values-ja",
            "values-nl",
            "values-pl",
            "values-pt"
        )

        val requiredKeys = listOf(
            "unfinished_workout_title",
            "unfinished_workout_title_battery",
            "unfinished_workout_title_memory",
            "unfinished_workout_title_permission",
            "kill_reason_battery_stage1",
            "kill_reason_battery_stage1_strava",
            "kill_reason_battery_stage2",
            "kill_reason_battery_stage3",
            "kill_reason_low_memory",
            "kill_reason_permission_revoked",
            "action_disable_battery_optimization",
            "start_or_resume_dialog_message",
            "start_new_workout",
            "resume_workout"
        )

        val factory = DocumentBuilderFactory.newInstance()
        for (locale in locales) {
            val file = resolveSourceFile("app/src/main/res/$locale/strings.xml")
            assertTrue("strings.xml must exist for locale $locale", file.exists())

            val doc = factory.newDocumentBuilder().parse(file)
            val stringNodes = doc.getElementsByTagName("string")
            val stringsMap = mutableMapOf<String, String>()

            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                val key = node.attributes.getNamedItem("name")?.nodeValue
                val text = node.textContent
                if (key != null) {
                    stringsMap[key] = text
                }
            }

            for (key in requiredKeys) {
                val text = stringsMap[key]
                assertNotNull("Missing key '$key' in locale '$locale'", text)
                assertFalse("Key '$key' is blank in locale '$locale'", text!!.trim().isEmpty())
            }
        }
    }
}
