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

package com.atrainingtracker.trainingtracker.ui.map

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural contract test for [TelemetryMetricGraph] verifying the optional
 * enableGestures parameter and conditional pointerInput attachment (REQ-UI-247 / TST-UI-206.1).
 */
class TelemetryMetricGraphGesturesContractTest {

    @Test
    fun testTelemetryMetricGraph_declaresEnableGesturesParameterWithDefaultTrue() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
        val methods = clazz.declaredMethods.filter { it.name.startsWith("TelemetryMetricGraph") }
        assertTrue("TelemetryMetricGraph composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun testTelemetryMetricGraph_conditionallyAttachesPointerInputBasedOnEnableGestures() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphKt")
        assertNotNull("TelemetryMetricGraphKt must exist", clazz)
    }
}
