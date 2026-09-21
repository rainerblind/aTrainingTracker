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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Unit tests verifying consolidation and integrity of all universal UI design primitives
 * in package [com.atrainingtracker.trainingtracker.ui.components.core] (REQ-UI-148, TST-UI-101, ATT-939).
 */
class CoreComponentsIntegrityTest {

    @Test
    fun testCorePackage_containsAllUniversalDesignPrimitives() {
        val expectedClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheetKt",
            "com.atrainingtracker.trainingtracker.ui.components.core.AppTableHeaderKt",
            "com.atrainingtracker.trainingtracker.ui.components.core.AppTableTypography",
            "com.atrainingtracker.trainingtracker.ui.components.core.AppTableTypographyKt",
            "com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandleKt",
            "com.atrainingtracker.trainingtracker.ui.components.core.FastScrollbarKt",
            "com.atrainingtracker.trainingtracker.ui.components.core.BadgeBoxKt"
        )

        for (className in expectedClasses) {
            val clazz = Class.forName(className)
            assertNotNull("Class $className must exist in ui.components.core", clazz)
        }
    }

    @Test
    fun testMinimumDragHandle_composableExistsInCore() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.MinimumDragHandleKt")
        val method = clazz.declaredMethods.find { it.name == "MinimumDragHandle" }
        assertNotNull("MinimumDragHandle function must exist in ui.components.core", method)
        assertTrue(Modifier.isPublic(method!!.modifiers))
    }

    @Test
    fun testFastScrollbar_composablesExistInCore() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.FastScrollbarKt")
        val fastScrollbarMethod = clazz.declaredMethods.find { it.name.startsWith("FastScrollbar") && Modifier.isPublic(it.modifiers) }
        assertNotNull("Public FastScrollbar function must exist in ui.components.core", fastScrollbarMethod)

        val fastScrollableBoxMethod = clazz.declaredMethods.find { it.name.startsWith("FastScrollableBox") && Modifier.isPublic(it.modifiers) }
        assertNotNull("Public FastScrollableBox function must exist in ui.components.core", fastScrollableBoxMethod)
    }

    @Test
    fun testBadgeBox_constantsAndComposables() {
        assertEquals("BADGE_BOX_DEFAULT_WIDTH must be 26.dp", 26.dp, BADGE_BOX_DEFAULT_WIDTH)

        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.BadgeBoxKt")
        val badgeBoxMethod = clazz.declaredMethods.find { it.name.startsWith("BadgeBox") && Modifier.isPublic(it.modifiers) }
        assertNotNull("Public BadgeBox function must exist in ui.components.core", badgeBoxMethod)

        val badgeSpacerMethod = clazz.declaredMethods.find { it.name.startsWith("BadgeSpacer") && Modifier.isPublic(it.modifiers) }
        assertNotNull("Public BadgeSpacer function must exist in ui.components.core", badgeSpacerMethod)
    }
}
