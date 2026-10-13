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
import java.io.File
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests for [EntityDetailSheetScaffold] (REQ-UI-333, TST-UI-293).
 *
 * Verifies shared sheet scaffolding, styling tokens, close button, and its consumption
 * across [com.atrainingtracker.trainingtracker.ui.segments.SegmentDetailSheet],
 * [com.atrainingtracker.trainingtracker.ui.routes.RouteDetailSheet], and
 * [com.atrainingtracker.trainingtracker.ui.climbs.ClimbDetailSheet].
 */
class EntityDetailSheetScaffoldContractTest {

    private val projectRoot: File by lazy {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (!File(dir, "app").exists() && dir.parentFile != null) {
            dir = dir.parentFile!!
        }
        dir
    }

    private val scaffoldFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/EntityDetailSheetScaffold.kt")
    }

    private val segmentSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/SegmentDetailSheet.kt")
    }

    private val routeSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteDetailSheet.kt")
    }

    private val climbSheetFile: File by lazy {
        File(projectRoot, "app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheet.kt")
    }

    @Test
    fun testEntityDetailSheetScaffold_composableExistsAndIsPublic() {
        val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.components.core.EntityDetailSheetScaffoldKt")
        assertNotNull("EntityDetailSheetScaffoldKt class must exist", clazz)

        val methods = clazz.declaredMethods
        val scaffoldMethod = methods.find { it.name.startsWith("EntityDetailSheetScaffold") && Modifier.isPublic(it.modifiers) }
        assertNotNull("EntityDetailSheetScaffold composable function must exist and be public", scaffoldMethod)
    }

    @Test
    fun testEntityDetailSheetScaffold_structuralTokensAndStyling() {
        assertTrue("EntityDetailSheetScaffold.kt must exist", scaffoldFile.exists())
        val content = scaffoldFile.readText()

        // 1. Full height ModalBottomSheet with skipPartiallyExpanded
        assertTrue("Must use ModalBottomSheet", content.contains("ModalBottomSheet("))
        assertTrue("Must skip partially expanded state", content.contains("skipPartiallyExpanded = true"))
        assertTrue("Must fill max height", content.contains("fillMaxHeight()"))

        // 2. Standard BottomSheetDesign tokens
        assertTrue("Must use BottomSheetDesign.SheetShape", content.contains("BottomSheetDesign.SheetShape"))
        assertTrue("Must use BottomSheetDesign.SheetTonalElevation", content.contains("BottomSheetDesign.SheetTonalElevation"))
        assertTrue("Must suppress drag handle", content.contains("dragHandle = null"))

        // 3. Floating circular dismiss close button
        assertTrue("Must render dismiss IconButton", content.contains("IconButton("))
        assertTrue("Must use CircleShape for close button", content.contains("CircleShape"))
        assertTrue("Must use Close icon", content.contains("Icons.Default.Close"))
        assertTrue("Must use 2.dp shadow elevation for close button", content.contains("shadowElevation = 2.dp"))
        assertTrue("Must anchor close button to TopEnd", content.contains("Alignment.TopEnd"))
    }

    @Test
    fun testEntityDetailSheetScaffold_reusedAcrossAllEntityPopups() {
        assertTrue("SegmentDetailSheet.kt must exist", segmentSheetFile.exists())
        val segmentContent = segmentSheetFile.readText()
        assertTrue("SegmentDetailSheet must delegate to EntityDetailSheetScaffold", segmentContent.contains("EntityDetailSheetScaffold("))

        assertTrue("RouteDetailSheet.kt must exist", routeSheetFile.exists())
        val routeContent = routeSheetFile.readText()
        assertTrue("RouteDetailSheet must delegate to EntityDetailSheetScaffold", routeContent.contains("EntityDetailSheetScaffold("))

        assertTrue("ClimbDetailSheet.kt must exist", climbSheetFile.exists())
        val climbContent = climbSheetFile.readText()
        assertTrue("ClimbDetailSheet must delegate to EntityDetailSheetScaffold", climbContent.contains("EntityDetailSheetScaffold("))
    }
}
