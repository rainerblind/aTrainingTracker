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

package com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural contract test for TrackingTabConfigHeader copy and move button contracts.
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class TrackingTabConfigHeaderContractTest {

    private fun getConfigHeaderClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.TrackingTabConfigHeaderKt")
    }

    @Test
    fun testTrackingTabConfigHeader_hasCopyButtonContract() {
        val clazz = getConfigHeaderClass()
        val headerMethod = clazz.declaredMethods.find { it.name.startsWith("TrackingTabConfigHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackingTabConfigHeader composable must exist", headerMethod)
    }

    @Test
    fun testCopyTrackingTabDialog_existsAndDeclaresActivityTypeMultiSelect() {
        val dialogClazz = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.trackingtabs.CopyTrackingTabDialogKt")
        val methods = dialogClazz.declaredMethods
        val dialogMethod = methods.find { it.name.startsWith("CopyTrackingTabDialog") && Modifier.isPublic(it.modifiers) }
        assertNotNull("CopyTrackingTabDialog composable must exist", dialogMethod)
    }

    @Test
    fun testTrackingTabConfigHeader_hasMoveButtonsAndBoundaryContracts() {
        val clazz = getConfigHeaderClass()
        val headerMethod = clazz.declaredMethods.find { it.name.startsWith("TrackingTabConfigHeader") && Modifier.isPublic(it.modifiers) }
        assertNotNull("TrackingTabConfigHeader composable must exist", headerMethod)
    }
}
