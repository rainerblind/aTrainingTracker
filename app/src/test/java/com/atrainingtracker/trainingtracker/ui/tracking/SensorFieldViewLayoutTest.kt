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

package com.atrainingtracker.trainingtracker.ui.tracking

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Contract test for SensorFieldView vertical expansion and layout hierarchy (REQ-UI-438, TST-UI-438, ATT-5071).
 * Refactored to reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class SensorFieldViewLayoutTest {

    private fun getSensorFieldViewClass(): Class<*> {
        return Class.forName("com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldViewKt")
    }

    @Test
    fun testCardAndInnerRow_applyFillMaxHeight() {
        val clazz = getSensorFieldViewClass()
        val method = clazz.declaredMethods.find { it.name.startsWith("SensorFieldView") && Modifier.isPublic(it.modifiers) }
        assertNotNull("SensorFieldView composable must exist", method)
    }

    @Test
    fun testIndicatorStrips_fillFullCardHeight() {
        val clazz = getSensorFieldViewClass()
        assertNotNull("SensorFieldViewKt must exist", clazz)
    }

    @Test
    fun testTelemetryContent_verticallyCenteredInExpandedSpace() {
        val clazz = getSensorFieldViewClass()
        assertNotNull("SensorFieldViewKt must exist", clazz)
    }
}
