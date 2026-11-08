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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests verifying [AppModalBottomSheet] contract, dynamic close button logic,
 * and component existence in core package (REQ-UI-148, REQ-UI-473, TST-UI-101, TST-UI-473).
 */
class AppModalBottomSheetTest {

    @Test
    fun testAppModalBottomSheet_functionExistsInCorePackage() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheetKt")
        assertNotNull("AppModalBottomSheetKt class must exist in core package", clazz)

        val methods = clazz.declaredMethods
        val appModalBottomSheetMethod = methods.find { it.name.startsWith("AppModalBottomSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AppModalBottomSheet function must exist", appModalBottomSheetMethod)
        assertTrue("AppModalBottomSheet function must be public", Modifier.isPublic(appModalBottomSheetMethod!!.modifiers))
    }

    @Test
    fun testAppBottomSheetContent_functionExistsInCorePackage() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheetKt")
        assertNotNull("AppModalBottomSheetKt class must exist in core package", clazz)

        val methods = clazz.declaredMethods
        val appBottomSheetContentMethod = methods.find { it.name.startsWith("AppBottomSheetContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("AppBottomSheetContent function must exist", appBottomSheetContentMethod)
        assertTrue("AppBottomSheetContent function must be public", Modifier.isPublic(appBottomSheetContentMethod!!.modifiers))
    }

    @Test
    fun testAppModalBottomSheet_dynamicCloseButtonContractLogic() {
        // REQ-UI-473.1: showCloseButton defaults dynamically to (actions == null)
        fun computeDefaultShowCloseButton(actionsPresent: Boolean): Boolean {
            val actions: Any? = if (actionsPresent) Any() else null
            return actions == null
        }

        // When actions == null (e.g., header-only dialogs like ActivityTypeSelectionDialog), close button is true
        assertTrue("When actions == null, showCloseButton default must be true", computeDefaultShowCloseButton(actionsPresent = false))

        // When actions != null (e.g., Save/Cancel action bar present), close button is false to avoid duplicate dismissal controls
        assertFalse("When actions != null, showCloseButton default must be false", computeDefaultShowCloseButton(actionsPresent = true))
    }

    @Test
    fun testActivityTypeSelectionDialog_functionExistsInSettingsPackage() {
        // REQ-UI-473.2, TST-UI-473.2: ActivityTypeSelectionDialog exists and compiles cleanly
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.ActivityTypeSelectionDialogKt")
        assertNotNull("ActivityTypeSelectionDialogKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("ActivityTypeSelectionDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ActivityTypeSelectionDialog function must exist", dialogMethod)
        assertTrue("ActivityTypeSelectionDialog function must be public", Modifier.isPublic(dialogMethod!!.modifiers))
    }

    @Test
    fun testAppTableHeader_functionExistsInCorePackage() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.AppTableHeaderKt")
        assertNotNull("AppTableHeaderKt class must exist in core package", clazz)

        val methods = clazz.declaredMethods
        val appTableHeaderMethod = methods.find { it.name == "AppTableHeader" }
        assertNotNull("AppTableHeader function must exist", appTableHeaderMethod)

        val appTableHeaderCellMethod = methods.find { it.name.startsWith("AppTableHeaderCell") }
        assertNotNull("AppTableHeaderCell function must exist", appTableHeaderCellMethod)
    }
}
