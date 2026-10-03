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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and contract test verifying the presence, signatures, and string references
 * for [PermissionRationaleSheet] (REQ-PRI-003, TST-PRI-002, ATT-2075).
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
    fun testRequiredStringResourcesExist() {
        // Verify all required string resource identifiers are generated in R.string
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
    }

    @Test
    fun testRequiredDrawableResourcesExist() {
        assertTrue(R.drawable.my_locations != 0)
        assertTrue(R.drawable.ic_location != 0)
        assertTrue(R.drawable.ic_my_paired_devices != 0)
        assertTrue(R.drawable.ic_lap_timer != 0)
        assertTrue(R.drawable.ic_place != 0)
    }
}
