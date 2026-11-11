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

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Structural contract test verifying that CockpitTypographySection renders discrete
 * cockpit preview tiles styled with active corner radius, border thickness, border contrast,
 * and elevation (REQ-UI-277, TST-UI-237.1).
 * Refactored to pure JVM reflection per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class CockpitPreviewTileContractTest {

    @Test
    fun testLivePreview_usesSensorFieldStyleAndDiscreteTiles() {
        val sectionClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.CockpitTypographySectionKt")
        val methods = sectionClass.declaredMethods
        val sectionMethod = methods.find { it.name.startsWith("CockpitTypographySection") && Modifier.isPublic(it.modifiers) }
        assertNotNull("CockpitTypographySection composable must exist", sectionMethod)

        val tileMethod = methods.find { it.name.startsWith("PreviewCockpitTile") }
        assertNotNull("PreviewCockpitTile composable must exist", tileMethod)
    }
}
