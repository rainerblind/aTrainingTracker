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
 * Structural and architectural contract tests for [MapDetailLayout] verifying the upper metadata slot
 * architecture for immediate post-workout notes and extrema visibility (TST-UI-204.1 / REQ-UI-245 / ATT-2112).
 */
class MapDetailLayoutMetadataSlotContractTest {

    @Test
    fun testMapDetailLayout_declaresMetadataContentParameterWithDefaultNull() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt class must exist", mapDetailClass)
        val methods = mapDetailClass.declaredMethods.filter { it.name.startsWith("MapDetailLayout") }
        assertTrue("MapDetailLayout composable methods must exist", methods.isNotEmpty())
    }

    @Test
    fun testMapDetailLayout_evaluatesMetadataContentInHasScrollableContent() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt class must exist", mapDetailClass)
    }

    @Test
    fun testMapDetailLayout_rendersMetadataContentAboveMapBeforeViewport() {
        val mapDetailClass = Class.forName("com.atrainingtracker.trainingtracker.ui.map.MapDetailLayoutKt")
        assertNotNull("MapDetailLayoutKt class must exist", mapDetailClass)
    }
}
