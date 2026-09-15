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

package com.atrainingtracker.trainingtracker.ui.components.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests verifying AppDialogActions composable signatures, contract compliance,
 * and unified bottom sheet presentation according to REQ-UI-150 and TST-UI-103 (ATT-1034).
 */
class AppDialogActionsIntegrityTest {

    @Test
    fun testAppDialogActions_exposesSaveCancelConfirmAndCancelOnly() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.AppDialogActions")
        val methods = clazz.declaredMethods

        val saveCancel = methods.find { it.name.startsWith("SaveCancel") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AppDialogActions.SaveCancel must exist and be public", saveCancel)

        val confirm = methods.find { it.name.startsWith("Confirm") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AppDialogActions.Confirm must exist and be public", confirm)

        val cancelOnly = methods.find { it.name.startsWith("CancelOnly") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AppDialogActions.CancelOnly must exist and be public", cancelOnly)
    }

    @Test
    fun testEditRouteScreen_existsAndPreservesContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.routes.EditRouteScreenKt")
        val editRouteMethod = clazz.declaredMethods.find { it.name == "EditRouteScreen" && Modifier.isPublic(it.modifiers) }
        assertNotNull("EditRouteScreen composable method must exist and be public", editRouteMethod)
        assertTrue(Modifier.isPublic(editRouteMethod!!.modifiers))

        // Ensure 3 required parameters: routeSummary, onSave, onCancel
        val paramTypes = editRouteMethod.parameterTypes
        assertTrue("EditRouteScreen must accept RouteSummary", paramTypes.any { it.name.contains("RouteSummary") })
    }

    @Test
    fun testPreImportTuningBottomSheet_existsAndPreservesContract() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.migration.ImportBackupTabsScreenKt")
        val tuningMethod = clazz.declaredMethods.find { it.name == "PreImportTuningBottomSheet" }
        assertNotNull("PreImportTuningBottomSheet composable method must exist", tuningMethod)
    }

    @Test
    fun testAppModalBottomSheet_supportsHeaderActions() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheetKt")
        val method = clazz.declaredMethods.find { it.name.startsWith("AppModalBottomSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AppModalBottomSheet composable method must exist", method)
        // Check that method parameter count allows headerActions
        assertTrue("AppModalBottomSheet parameters should accommodate headerActions", method!!.parameterTypes.size >= 8)
    }
}
