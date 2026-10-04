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

package com.atrainingtracker.trainingtracker.ui.climbs

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Unit tests verifying [LiveClimbSheet] composable structure, contracts, and visual tokens (REQ-MAP-027, TST-MAP-029).
 */
class LiveClimbSheetLayoutTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val liveClimbSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt")
    }

    @Test
    fun testLiveClimbSheet_composableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.climbs.LiveClimbSheetKt")
        assertNotNull("LiveClimbSheetKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val sheetMethod = methods.find { it.name.startsWith("LiveClimbSheet") && Modifier.isPublic(it.modifiers) }
        assertNotNull("LiveClimbSheet composable function must exist and be public", sheetMethod)
        assertTrue(Modifier.isPublic(sheetMethod!!.modifiers))
    }

    @Test
    fun testLiveClimbSheet_structuralDesignAndTelemetryContracts() {
        assertTrue("LiveClimbSheet.kt must exist", liveClimbSheetFile.exists())
        val content = liveClimbSheetFile.readText()

        assertTrue(
            "LiveClimbSheet must use unified MaterialTheme surface background",
            content.contains("MaterialTheme.colorScheme.surface")
        )
        assertTrue(
            "LiveClimbSheet must include ClimbProfileCanvas for colored grade elevation profile",
            content.contains("ClimbProfileCanvas(")
        )
        assertTrue(
            "LiveClimbSheet must render ClimbCategoryChip badge",
            content.contains("ClimbCategoryChip(")
        )
        assertTrue(
            "LiveClimbSheet must render ClimbStatusBadge",
            content.contains("ClimbStatusBadge(")
        )
        assertTrue(
            "LiveClimbSheet must render HUD metrics for remaining distance, elevation, and grade",
            content.contains("climb_remaining_dist") &&
            content.contains("climb_remaining_elevation") &&
            content.contains("climb_grade")
        )
    }
}
