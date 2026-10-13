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

package com.atrainingtracker.trainingtracker.ui.ant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural contract test validating the Material 3 Compose modernization of ANT alert dialogs
 * and elimination of legacy View-based AlertDialog.Builder popups (REQ-UI-323 / TST-UI-283).
 */
class AntAlertDialogContractTest {

    private fun resolveSourceFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val fromRepoRoot = File(System.getProperty("user.dir", "."), relativePath)
        if (fromRepoRoot.exists()) return fromRepoRoot
        val parent = File("..", relativePath)
        if (parent.exists()) return parent
        throw IllegalStateException("Cannot resolve source file: $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun testAntDialogState_hierarchy() {
        val missingAdapter: AntDialogState = AntDialogState.MissingAdapter
        assertNotNull(missingAdapter)

        val missingDependency: AntDialogState = AntDialogState.MissingDependency(
            dependencyName = "ANT Radio Service",
            packageName = "com.dsi.ant.service.socket"
        )
        assertNotNull(missingDependency)
        assertTrue(missingDependency is AntDialogState.MissingDependency)
        val dep = missingDependency as AntDialogState.MissingDependency
        org.junit.Assert.assertEquals("ANT Radio Service", dep.dependencyName)
        org.junit.Assert.assertEquals("com.dsi.ant.service.socket", dep.packageName)
    }

    @Test
    fun testAntAlertDialogs_structuralComposition() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/ant/AntAlertDialogs.kt")
        val content = file.readText()

        assertTrue(
            "AntAlertDialogs.kt must compose Material 3 AlertDialog",
            content.contains("AlertDialog(")
        )
        assertTrue(
            "AntAlertDialogs.kt must render safePainterResource with R.drawable.ant_logo",
            content.contains("safePainterResource(id = R.drawable.ant_logo)")
        )
        assertTrue(
            "AntMissingAdapterDialog must use ant_missing_adapter_title",
            content.contains("R.string.ant_missing_adapter_title")
        )
        assertTrue(
            "AntMissingAdapterDialog must use ant_missing_adapter_message",
            content.contains("R.string.ant_missing_adapter_message")
        )
        assertTrue(
            "AntMissingAdapterDialog must bind confirm button to ant_dialog_view_status",
            content.contains("R.string.ant_dialog_view_status")
        )
        assertTrue(
            "AntMissingAdapterDialog must bind dismiss button to ant_dialog_dismiss",
            content.contains("R.string.ant_dialog_dismiss")
        )
        assertTrue(
            "AntMissingDependencyDialog must use ant_missing_dependency_title",
            content.contains("R.string.ant_missing_dependency_title")
        )
        assertTrue(
            "AntMissingDependencyDialog must format ant_missing_dependency_message",
            content.contains("R.string.ant_missing_dependency_message")
        )
        assertTrue(
            "AntMissingDependencyDialog must bind confirm button to go_to_store",
            content.contains("R.string.go_to_store")
        )
        assertTrue(
            "AntMissingDependencyDialog must bind dismiss button to cancel",
            content.contains("R.string.cancel")
        )
    }

    @Test
    fun testMainActivityWithNavigation_eliminationOfLegacyAlertDialogBuilderForAnt() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt")
        val content = file.readText()

        val adapterSection = content.substringAfter("fun showANTAdapterMissingDialog()")
            .substringBefore("fun ")
        assertFalse(
            "showANTAdapterMissingDialog must NOT instantiate AlertDialog.Builder",
            adapterSection.contains("AlertDialog.Builder")
        )
        assertTrue(
            "showANTAdapterMissingDialog must set antDialogState to MissingAdapter",
            adapterSection.contains("antDialogState = AntDialogState.MissingAdapter")
        )

        val dependencySection = content.substringAfter("fun showSpecificInstallANTDialog()")
            .substringBefore("fun showANTAdapterMissingDialog()")
        assertFalse(
            "showSpecificInstallANTDialog must NOT instantiate AlertDialog.Builder",
            dependencySection.contains("AlertDialog.Builder")
        )
        assertTrue(
            "showSpecificInstallANTDialog must set antDialogState to MissingDependency",
            dependencySection.contains("antDialogState = AntDialogState.MissingDependency")
        )
    }

    @Test
    fun testATrainingTrackerApp_wiresAntDialogsAndStatusSheet() {
        val file = resolveSourceFile("app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt")
        val content = file.readText()

        assertTrue(
            "ATrainingTrackerApp must render AntMissingAdapterDialog",
            content.contains("AntMissingAdapterDialog(")
        )
        assertTrue(
            "ATrainingTrackerApp must render AntMissingDependencyDialog",
            content.contains("AntMissingDependencyDialog(")
        )
        assertTrue(
            "ATrainingTrackerApp must render AntServicesStatusSheet when showAntStatusSheet is true",
            content.contains("if (activity.showAntStatusSheet)") && content.contains("AntServicesStatusSheet(")
        )
    }
}
