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

package com.atrainingtracker.trainingtracker.ui.tracking.controltracking

import com.atrainingtracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural and contract test verifying the presence, signatures, string references,
 * and 9-language localization parity for [PermissionRationaleSheet] and progressive JIT flow (REQ-PRI-003, TST-PRI-002, ATT-2075).
 */
class PermissionRationaleSheetContractTest {

    @Test
    fun testPermissionRationaleSheetComposablesExist() {
        val sheetClass = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.PermissionRationaleSheetKt")
        assertNotNull(sheetClass)

        val methods = sheetClass.declaredMethods
        val hasSheet = methods.any { it.name.startsWith("PermissionRationaleSheet") && Modifier.isPublic(it.modifiers) }
        val hasContent = methods.any { it.name.startsWith("PermissionRationaleContent") && Modifier.isPublic(it.modifiers) }

        assertTrue("PermissionRationaleSheet composable must exist", hasSheet)
        assertTrue("PermissionRationaleContent composable must exist", hasContent)
    }

    @Test
    fun testRationaleTypeEnumConstants() {
        val types = RationaleType.values().map { it.name }
        assertEquals(3, types.size)
        assertTrue(types.contains("FOREGROUND"))
        assertTrue(types.contains("BACKGROUND_LOCATION"))
        assertTrue(types.contains("BATTERY_OPTIMIZATION"))
    }

    @Test
    fun testRationaleStepEnumConstants() {
        val steps = RationaleStep.values().map { it.name }
        assertEquals(4, steps.size)
        assertTrue(steps.contains("NONE"))
        assertTrue(steps.contains("FOREGROUND"))
        assertTrue(steps.contains("BACKGROUND_LOCATION"))
        assertTrue(steps.contains("BATTERY_OPTIMIZATION"))
    }

    @Test
    fun testRequiredStringResourcesExist() {
        // Foreground permission rationale strings
        assertTrue(R.string.permission_rationale_title != 0)
        assertTrue(R.string.permission_rationale_subtitle != 0)
        assertTrue(R.string.permission_rationale_location_title != 0)
        assertTrue(R.string.permission_rationale_location_desc != 0)
        assertTrue(R.string.permission_rationale_bluetooth_title != 0)
        assertTrue(R.string.permission_rationale_bluetooth_desc != 0)
        assertTrue(R.string.permission_rationale_notification_title != 0)
        assertTrue(R.string.permission_rationale_notification_desc != 0)
        assertTrue(R.string.permission_rationale_continue != 0)
        assertTrue(R.string.permission_rationale_not_now != 0)
        assertTrue(R.string.permission_rationale_open_settings != 0)
        assertTrue(R.string.permission_rationale_settings_explanation != 0)
        assertTrue(R.string.permission_warning_badge_desc != 0)

        // Background location & battery optimization strings
        assertTrue(R.string.background_location_permission_title != 0)
        assertTrue(R.string.background_location_permission_text != 0)
        assertTrue(R.string.battery_optimization_title != 0)
        assertTrue(R.string.battery_optimization_text != 0)
    }

    @Test
    fun testRequiredDrawableResourcesExist() {
        assertTrue(R.drawable.my_locations != 0)
        assertTrue(R.drawable.ic_location != 0)
        assertTrue(R.drawable.ic_my_paired_devices != 0)
        assertTrue(R.drawable.ic_lap_timer != 0)
        assertTrue(R.drawable.ic_battery_full != 0)
    }

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testNineLanguageLocalizationAudit() {
        val localeDirs = listOf(
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

        val keysToCheck = listOf(
            "permission_rationale_title",
            "background_location_permission_title",
            "background_location_permission_text",
            "battery_optimization_title",
            "battery_optimization_text"
        )

        for (localeDir in localeDirs) {
            val stringsFile = findFile("src/main/res/$localeDir/strings.xml")
            assertTrue("strings.xml must exist for locale $localeDir", stringsFile.exists())
            val content = stringsFile.readText()

            for (key in keysToCheck) {
                val tag = "<string name=\"$key\">"
                assertTrue(
                    "Key '$key' must exist in $localeDir/strings.xml",
                    content.contains(tag)
                )
            }
        }
    }
}
