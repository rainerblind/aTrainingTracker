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
 * Unit tests verifying [AppModalBottomSheet] contract and existence in core package
 * (REQ-UI-148, TST-UI-101, ATT-939).
 */
class AppModalBottomSheetTest {

    @Test
    fun testAppModalBottomSheet_functionExistsInCorePackage() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheetKt")
        assertNotNull("AppModalBottomSheetKt class must exist in core package", clazz)

        val methods = clazz.declaredMethods
        val appModalBottomSheetMethod = methods.find { it.name == "AppModalBottomSheet" }
        assertNotNull("AppModalBottomSheet function must exist", appModalBottomSheetMethod)
        assertTrue("AppModalBottomSheet function must be public", Modifier.isPublic(appModalBottomSheetMethod!!.modifiers))
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
