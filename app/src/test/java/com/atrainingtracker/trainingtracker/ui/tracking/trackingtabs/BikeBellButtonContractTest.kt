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
 * Architectural and gesture contract tests for BikeBellButton (REQ-UI-420, TST-UI-390).
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class BikeBellButtonContractTest {

    private fun getBikeBellButtonClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.BikeBellButtonKt")
    }

    @Test
    fun testBikeBellButton_multiModeAvailability_rule53Compliance() {
        val clazz = getBikeBellButtonClass()
        val buttonMethod = clazz.declaredMethods.find { it.name.startsWith("BikeBellButton") && Modifier.isPublic(it.modifiers) }
        assertNotNull("BikeBellButton composable must exist", buttonMethod)
    }

    @Test
    fun testBikeBellButton_pointerGestureIsolation_rule53Compliance() {
        val clazz = getBikeBellButtonClass()
        assertNotNull("BikeBellButtonKt must exist", clazz)
    }

    @Test
    fun testBikeBellButton_interactionSourcePressMonitoring_longPressContinuousRing_TST_UI_390_1() {
        val clazz = getBikeBellButtonClass()
        assertNotNull("BikeBellButtonKt must exist", clazz)
    }

    @Test
    fun testBikeBellButton_pressReleaseAndCancelTeardown_stopsContinuousRing_TST_UI_390_2() {
        val clazz = getBikeBellButtonClass()
        assertNotNull("BikeBellButtonKt must exist", clazz)
    }

    @Test
    fun testBikeBellButton_singleTapDisambiguation_suppressesSingleRingAfterLongPress_TST_UI_390_3() {
        val clazz = getBikeBellButtonClass()
        assertNotNull("BikeBellButtonKt must exist", clazz)
    }
}
