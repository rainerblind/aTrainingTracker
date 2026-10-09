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

package com.atrainingtracker.trainingtracker.ui.tracking.tracking

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural contract tests verifying that [SensorGridScreen] directly integrates
 * the Quick Route Selector HUD entry point, Auto-Detected Route Banner, and Modal Bottom Sheet
 * (REQ-MAP-024 / TST-MAP-026 / ATT-1835).
 */
class SensorGridScreenRouteIntegrationTest {

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
    fun testSensorGridScreen_doesNotDeclareRouteSelectorViewModelParameter() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not declare routeSelectorViewModel parameter (REQ-UI-311)",
            content.contains("routeSelectorViewModel")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotIntegrateRouteActionChipRow() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not render RouteActionChipRow in tracking mode (REQ-UI-279.1 / ATT-2458)",
            content.contains("RouteActionChipRow(")
        )
        org.junit.Assert.assertFalse(
            "SensorGridScreen must not define RouteActionChipRow composable",
            content.contains("fun RouteActionChipRow(")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotIntegrateAutoDetectedRouteBanner() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not host AutoDetectedRouteBanner (REQ-UI-311)",
            content.contains("AutoDetectedRouteBanner(")
        )
        org.junit.Assert.assertFalse(
            "SensorGridScreen must not hook into activateCandidate",
            content.contains("activateCandidate")
        )
        org.junit.Assert.assertFalse(
            "SensorGridScreen must not hook into dismissCandidate",
            content.contains("dismissCandidate")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotHostRedundantRouteSelectorModalBottomSheet() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not host redundant RouteSelectorModalBottomSheet (REQ-UI-279.1)",
            content.contains("RouteSelectorModalBottomSheet(")
        )

        val tabsFile = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt")
        assertTrue(
            "TrackingTabsScreen must host RouteSelectorModalBottomSheet (REQ-UI-279.2)",
            tabsFile.readText().contains("RouteSelectorModalBottomSheet(")
        )
    }

    @Test
    fun testSensorGridScreen_doesNotForwardLocationUpdatesToRouteSelectorViewModel() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        org.junit.Assert.assertFalse(
            "SensorGridScreen must not dispatch location updates to RouteSelectorViewModel (REQ-UI-311)",
            content.contains("actualRouteSelectorViewModel.onLocationChanged")
        )
    }

    @Test
    fun testRouteSelectionButton_integratesClearRouteAction() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButton.kt")
        val content = file.readText()

        assertTrue(
            "RouteSelectionButton must declare onClearRoute parameter",
            content.contains("onClearRoute: (() -> Unit)? = null")
        )
        assertTrue(
            "RouteSelectionButton must use route_action_clear string resource",
            content.contains("R.string.route_action_clear")
        )
    }

    /**
     * TST-UI-281.2 & TST-UI-281.3: ForkDecisionCard floats as top-center overlay and consumes navigationCueTransparency.
     */
    @Test
    fun testSensorGridScreen_forkDecisionCard_floatsAsTopCenterOverlay() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        assertTrue(
            "SensorGridScreen must render ForkDecisionCard with Alignment.TopCenter overlay modifier (REQ-UI-321.1 / ATT-2874)",
            content.contains("modifier = Modifier.align(Alignment.TopCenter)")
        )
        assertTrue(
            "SensorGridScreen must gate ForkDecisionCard behind state.showNavigationHints (REQ-UI-321.2 / ATT-2874)",
            content.contains("if (state.showNavigationHints)")
        )
        assertTrue(
            "SensorGridScreen must bind ForkDecisionCard overlayAlpha to tuningConfig.navigationCueTransparency (REQ-UI-321.3 / §5.7)",
            content.contains("overlayAlpha = tuningConfig.navigationCueTransparency")
        )
    }

    /**
     * TST-MAP-041.4 & TST-MAP-041.5: ReturnNavigationHud floats in top-center overlay and is gated by showNavigationHints (REQ-MAP-039.4 / ATT-2938).
     */
    @Test
    fun testSensorGridScreen_returnNavigationHud_floatsAsTopCenterOverlayAndGatedByNavigationHints() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        val mainColumnSection = content.substringAfter("Column(\n                    modifier = Modifier.fillMaxSize()\n                ) {")
            .substringBefore("Column(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .verticalScroll")

        org.junit.Assert.assertFalse(
            "ReturnNavigationHud must not be rendered inside the in-flow sensor grid Column",
            mainColumnSection.contains("ReturnNavigationHud(")
        )

        assertTrue(
            "ReturnNavigationHud must be gated behind state.showNavigationHints",
            content.contains("if (state.showNavigationHints)") && content.contains("ReturnNavigationHud(")
        )

        assertTrue(
            "ReturnNavigationHud must consume tuningConfig.navigationCueTransparency",
            content.contains("overlayAlpha = tuningConfig.navigationCueTransparency")
        )
    }

    /**
     * TST-UI-284.1: TurnPromptBanner floats in top-center overlay and is gated by showNavigationHints (REQ-UI-324 / ATT-2941).
     */
    @Test
    fun testSensorGridScreen_turnPromptBanner_floatsAsTopCenterOverlayAndGatedByNavigationHints() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt")
        val content = file.readText()

        val mainColumnSection = content.substringAfter("Column(\n                    modifier = Modifier.fillMaxSize()\n                ) {")
            .substringBefore("Column(\n                        modifier = Modifier\n                            .fillMaxWidth()\n                            .verticalScroll")

        org.junit.Assert.assertFalse(
            "TurnPromptBanner must not be rendered inside the in-flow sensor grid Column",
            mainColumnSection.contains("TurnPromptBanner(")
        )

        assertTrue(
            "TurnPromptBanner must be gated behind state.showNavigationHints in the top overlay Column",
            content.contains("if (state.showNavigationHints)") && content.contains("TurnPromptBanner(")
        )

        assertTrue(
            "TurnPromptBanner must consume tuningConfig.navigationCueTransparency",
            content.contains("overlayAlpha = tuningConfig.navigationCueTransparency")
        )

        assertTrue(
            "TurnPromptBanner must consume tuningConfig.navigationCueDismissDurationSec",
            content.contains("dismissDurationSec = tuningConfig.navigationCueDismissDurationSec")
        )
    }

    /**
     * TST-UI-284.2: TurnPromptBanner styling tokens conform to Design Guidelines §§ 5.2, 5.3, 5.4, 5.7 (REQ-UI-324 / ATT-2941).
     */
    @Test
    fun testTurnPromptBanner_stylingTokens_conformsToDesignGuidelines() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt")
        val content = file.readText()

        assertTrue(
            "TurnCueCard must use RoundedCornerShape(16.dp)",
            content.contains("shape = RoundedCornerShape(16.dp)")
        )
        assertTrue(
            "TurnCueCard must resolve containerColor to surfaceContainer / primaryContainer",
            content.contains("MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer")
        )
        assertTrue(
            "TurnCueCard must apply Royal Blue navigation border stroke",
            content.contains("val borderColor = TTColor.RouteActiveNavigation.copy(alpha = overlayAlpha.coerceAtLeast(0.4f))")
        )
        assertTrue(
            "TurnCueCard must use 16.dp horizontal and 12.dp vertical content padding",
            content.contains(".padding(horizontal = 16.dp, vertical = 12.dp)")
        )
        assertTrue(
            "OffRouteCard must use RoundedCornerShape(16.dp) and errorContainer",
            content.contains("MaterialTheme.colorScheme.errorContainer.copy(alpha = overlayAlpha)")
        )
    }
}
