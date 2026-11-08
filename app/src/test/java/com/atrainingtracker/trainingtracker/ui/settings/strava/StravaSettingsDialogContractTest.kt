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

package com.atrainingtracker.trainingtracker.ui.settings.strava

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Contract tests verifying `REQ-UI-488` (`TST-UI-488`):
 * Strava Settings Dialog Close Button Elimination & Sync Card Action Icons.
 */
class StravaSettingsDialogContractTest {

    private fun findSourceFile(): File {
        val candidates = listOf(
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt"),
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("StravaSettingsDialog.kt not found in candidates: $candidates")
    }

    /**
     * TST-UI-488.1: Verify StravaSettingsDialog composable exists and is public in settings.strava package.
     */
    @Test
    fun testStravaSettingsDialog_functionExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.strava.StravaSettingsDialogKt")
        assertNotNull("StravaSettingsDialogKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("StravaSettingsDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("StravaSettingsDialog function must exist", dialogMethod)
        assertTrue("StravaSettingsDialog function must be public", Modifier.isPublic(dialogMethod!!.modifiers))
    }

    /**
     * TST-UI-488.1: Verify that StravaSettingsDialog supplies actions without explicit showCloseButton = true,
     * ensuring automatic top-right close button elimination per REQ-UI-473 / REQ-UI-488.1.
     */
    @Test
    fun testCloseButtonSuppressionContract() {
        val sourceFile = findSourceFile()
        val sourceText = sourceFile.readText()

        // 1. Must supply bottom actions bar (SaveCancel)
        assertTrue(
            "StravaSettingsDialog must supply actions parameter to AppBottomSheetContent",
            sourceText.contains("actions = {") || sourceText.contains("actions =")
        )
        assertTrue(
            "StravaSettingsDialog actions must include SaveCancel buttons",
            sourceText.contains("AppDialogActions.SaveCancel")
        )

        // 2. Must not override showCloseButton = true (which would force the redundant X close button)
        assertFalse(
            "StravaSettingsDialog must not explicitly set showCloseButton = true when actions are present",
            sourceText.contains("showCloseButton = true")
        )
    }

    /**
     * TST-UI-488.2 & TST-UI-488.3: Verify manual sync cards render horizontal Row layout
     * with weight(1f) text Column and reactive Icons.Default.Refresh / CircularProgressIndicator feedback.
     */
    @Test
    fun testManualSyncCards_layoutAndInteractiveFeedbackContract() {
        val sourceFile = findSourceFile()
        val sourceText = sourceFile.readText()

        // 1. Must define StravaManualSyncCard helper
        assertTrue(
            "StravaSettingsDialog must define StravaManualSyncCard helper composable",
            sourceText.contains("fun StravaManualSyncCard")
        )

        // 2. Must use Row with verticalAlignment = Alignment.CenterVertically
        assertTrue(
            "StravaManualSyncCard must format content in a horizontal Row",
            sourceText.contains("verticalAlignment = Alignment.CenterVertically")
        )

        // 3. Must give text column Modifier.weight(1f)
        assertTrue(
            "StravaManualSyncCard must give text Column Modifier.weight(1f)",
            sourceText.contains("Modifier.weight(1f)")
        )

        // 4. Must render Icons.Default.Refresh when idle
        assertTrue(
            "StravaManualSyncCard must import and render Icons.Default.Refresh",
            sourceText.contains("Icons.Default.Refresh")
        )

        // 5. Must render CircularProgressIndicator with size 20.dp and strokeWidth 2.dp when syncing
        assertTrue(
            "StravaManualSyncCard must render CircularProgressIndicator when isSyncing is true",
            sourceText.contains("CircularProgressIndicator") &&
                sourceText.contains("size(20.dp)") &&
                sourceText.contains("strokeWidth = 2.dp")
        )

        // 6. Must invoke sync cards for Equipment, Routes, and Segments
        assertTrue(
            "Must invoke StravaManualSyncCard for updateStravaEquipment",
            sourceText.contains("updateStravaEquipment")
        )
        assertTrue(
            "Must invoke StravaManualSyncCard for updateStravaRoutes",
            sourceText.contains("updateStravaRoutes")
        )
        assertTrue(
            "Must invoke StravaManualSyncCard for updateStravaSegments",
            sourceText.contains("updateStravaSegments")
        )
    }
}
