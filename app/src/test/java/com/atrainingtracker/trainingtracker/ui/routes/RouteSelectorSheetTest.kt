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

package com.atrainingtracker.trainingtracker.ui.routes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract tests verifying structural integrity, token consumption, and component boundaries
 * of [RouteSelectorSheetKt] (REQ-MAP-024, REQ-UI-280 / TST-UI-240 / ATT-2459).
 */
class RouteSelectorSheetTest {

    private fun findProjectRoot(): File {
        var dir: File = File(".").canonicalFile
        while (dir.parentFile != null) {
            if (File(dir, "gradlew").exists() && File(dir, "app").exists()) {
                return dir
            }
            dir = dir.parentFile!!
        }
        return File(".").canonicalFile
    }

    private fun resolveSourceFile(relativePath: String): File {
        val root = findProjectRoot()
        val target = File(root, relativePath)
        assertTrue("File must exist: $relativePath", target.exists())
        return target
    }

    @Test
    fun testRouteSelectorSheet_exposesModalBottomSheetAndContent() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt")
        val content = file.readText()

        assertTrue(
            "RouteSelectorSheet must expose RouteSelectorModalBottomSheet",
            content.contains("fun RouteSelectorModalBottomSheet(")
        )
        assertTrue(
            "RouteSelectorSheet must expose RouteSelectorContent",
            content.contains("fun RouteSelectorContent(")
        )
        assertTrue(
            "RouteSelectorSheet must expose ActiveRouteBanner",
            content.contains("fun ActiveRouteBanner(")
        )
        assertTrue(
            "RouteSelectorSheet must expose AutoDetectedRouteBanner",
            content.contains("fun AutoDetectedRouteBanner(")
        )
        assertTrue(
            "RouteSelectorSheet must expose RouteCard",
            content.contains("fun RouteCard(")
        )
    }

    @Test
    fun testRouteSelectorModalBottomSheet_consumesDesignTokens() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt")
        val content = file.readText()

        assertTrue(
            "RouteSelectorModalBottomSheet must set shape to BottomSheetDesign.SheetShape",
            content.contains("shape = BottomSheetDesign.SheetShape")
        )
        assertTrue(
            "RouteSelectorModalBottomSheet must set containerColor to surface",
            content.contains("containerColor = MaterialTheme.colorScheme.surface")
        )
    }

    @Test
    fun testRouteSelectorContent_doesNotRenderFilterTabsOrTabRow() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt")
        val content = file.readText()

        assertFalse(
            "RouteSelectorSheet must NOT contain TabRow (REQ-UI-280 / ATT-2459)",
            content.contains("TabRow")
        )
        assertFalse(
            "RouteSelectorSheet must NOT reference showFilterTabs (REQ-UI-280 / ATT-2459)",
            content.contains("showFilterTabs")
        )
        assertFalse(
            "RouteSelectorSheet must NOT reference RouteFilterTab (REQ-UI-280 / ATT-2459)",
            content.contains("RouteFilterTab")
        )
    }

    @Test
    fun testRouteSelectorContent_doesNotRenderTakeMeHomeCardUnconditionally() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt")
        val content = file.readText()

        // Unconditional Take Me Home card is prohibited at workout start (REQ-UI-280 / ATT-2459)
        // Mid-ride entry point is conditionally gated by isMidRide (REQ-UI-282 / ATT-2462)
        assertTrue(
            "RouteSelectorSheet must guard Take Me Home card with isMidRide (REQ-UI-282 / ATT-2462)",
            content.contains("if (isMidRide && onTakeMeHome != null)")
        )
    }

    @Test
    fun testRouteCard_displaysSportIconWithAppropriateTokens() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt")
        val content = file.readText()

        assertTrue(
            "RouteCard must render sport icon via route.summary.bSportType.iconResId (REQ-UI-310 / ATT-2668)",
            content.contains("painterResource(id = route.summary.bSportType.iconResId)")
        )
        assertTrue(
            "RouteCard must provide localized accessibility description via stringResId",
            content.contains("stringResource(id = route.summary.bSportType.stringResId)")
        )
        assertTrue(
            "RouteCard must size sport icon at 24.dp",
            content.contains("modifier = Modifier.size(24.dp)")
        )
    }
}

