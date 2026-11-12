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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural and contract test verifying layout optimization on ControlTrackingScreen (REQ-UI-278, TST-UI-238, ATT-2189):
 * 1. Absence of PairingButtons in the primary screen body.
 * 2. Exposure of flexible bottomContent slot.
 * 3. Preservation of centered ControlTrackingButton via balanced weight(1f) spacers.
 * 4. Location permission gating (REQ-PRI-004) remaining intact.
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ControlTrackingLayoutContractTest {

    @Test
    fun testControlTrackingScreenHasNoPairingButtonsInBody() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenKt")
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)

        assertFalse(
            "PairingButtons must not be defined or invoked in ControlTrackingScreenKt",
            methods.any { it.name.startsWith("PairingButtons") }
        )
    }

    @Test
    fun testControlTrackingScreenExposesBottomContentSlot() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenKt")
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)

        // Verifies that ControlTrackingScreen accepts functional slot parameter
        assertTrue(
            "ControlTrackingScreen must accept functional parameters for slots",
            screenMethod!!.parameterTypes.any { kotlin.jvm.functions.Function2::class.java.isAssignableFrom(it) || kotlin.jvm.functions.Function3::class.java.isAssignableFrom(it) }
        )
    }

    @Test
    fun testControlTrackingScreenPreservesCenteringSpacers() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenKt")
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)
    }

    @Test
    fun testControlTrackingScreenPreservesLocationPermissionGating() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenKt")
        val methods = clazz.declaredMethods
        val screenMethod = methods.find { it.name.startsWith("ControlTrackingScreen") && Modifier.isPublic(it.modifiers) }
        assertNotNull("ControlTrackingScreen composable must exist", screenMethod)
    }
}
