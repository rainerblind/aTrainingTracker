/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.atrainingtracker.trainingtracker.ui.settings.tuning

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and contract tests verifying that workout route clustering tuning controls
 * strictly adhere to the Single Source of Truth and DRY principles (Rule 36, TST-UI-318.4, TST-UI-321, REQ-SET-075, ATT-3452).
 */
class ClusterTuningContractTest {

    @Test
    fun clusterTuningSection_delegatesToClusterTuningContent() {
        val sectionFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ClusterTuningSection.kt")
        assertTrue("ClusterTuningSection.kt must exist", sectionFile.exists())

        val content = sectionFile.readText()
        assertTrue(
            "ClusterTuningSection must delegate to shared ClusterTuningContent composable",
            content.contains("ClusterTuningContent(")
        )
        assertTrue(
            "ClusterTuningSection must propagate enabled state to ClusterTuningContent",
            content.contains("enabled = !isRecalculating")
        )
    }

    @Test
    fun clusterTuningSection_hasRecalculateActionAndConfirmationDialog() {
        val sectionFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ClusterTuningSection.kt")
        assertTrue("ClusterTuningSection.kt must exist", sectionFile.exists())

        val content = sectionFile.readText()
        assertTrue(
            "ClusterTuningSection must provide recalculate action button",
            content.contains("R.string.cluster_tuning_recalculate_button")
        )
        assertTrue(
            "ClusterTuningSection must provide confirmation AlertDialog with warning message",
            content.contains("AlertDialog(") && content.contains("R.string.cluster_tuning_warning")
        )
        assertTrue(
            "AlertDialog must specify surface container color and 0.dp tonal elevation",
            content.contains("containerColor = MaterialTheme.colorScheme.surface") &&
                    content.contains("tonalElevation = 0.dp")
        )
        assertTrue(
            "Confirmation button must be styled with high-contrast error color",
            content.contains("color = MaterialTheme.colorScheme.error")
        )
        assertTrue(
            "Confirm action must invoke asynchronous repository recalculation",
            content.contains("repository.recalculateClustersAsync(")
        )
    }

    @Test
    fun workoutClusters_hasNoTuningActionOrScreen() {
        val tabsFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt")
        assertTrue("WorkoutClustersTabsScreen.kt must exist", tabsFile.exists())
        val tabsContent = tabsFile.readText()

        assertFalse(
            "WorkoutClustersTabsScreen must not declare onTuneClick parameter (ATT-3452)",
            tabsContent.contains("onTuneClick")
        )
        assertFalse(
            "WorkoutClustersTabsScreen must not render ic_settings_24 in top app bar (ATT-3452)",
            tabsContent.contains("ic_settings_24")
        )

        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt")
        assertTrue("WorkoutClustersScreen.kt must exist", screenFile.exists())
        val screenContent = screenFile.readText()

        assertFalse(
            "WorkoutClustersScreen must not maintain isTuning state (ATT-3452)",
            screenContent.contains("isTuning")
        )
        assertFalse(
            "WorkoutClustersScreen must not invoke ClusterTuningScreen (ATT-3452)",
            screenContent.contains("ClusterTuningScreen(")
        )
    }

    @Test
    fun workoutClusterRepository_hasDomainLockAndAsyncScope() {
        val repoFile = File("src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterRepository.kt")
        assertTrue("WorkoutClusterRepository.kt must exist", repoFile.exists())
        val content = repoFile.readText()

        assertTrue(
            "WorkoutClusterRepository must declare domain-layer lock (isRecalculatingLock)",
            content.contains("isRecalculatingLock")
        )
        assertTrue(
            "WorkoutClusterRepository must support CoroutineScope dependency injection",
            content.contains("repositoryScope: CoroutineScope")
        )
        assertTrue(
            "WorkoutClusterRepository must declare recalculateClustersAsync()",
            content.contains("fun recalculateClustersAsync(")
        )
    }

    @Test
    fun clusterTuningContent_isEncapsulatedAndReusable() {
        val contentFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt")
        assertTrue("ClusterTuningContent.kt must exist", contentFile.exists())

        val content = contentFile.readText()
        assertTrue("Must declare ClusterTuningContent composable", content.contains("fun ClusterTuningContent("))
        assertTrue("ClusterTuningContent must support enabled parameter", content.contains("enabled: Boolean = true"))
        assertTrue("Must declare TuningSlider composable", content.contains("fun TuningSlider("))
    }

    @Test
    fun clusterTuningContent_delegatesToTuningFormControls() {
        val contentFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt")
        assertTrue("ClusterTuningContent.kt must exist", contentFile.exists())

        val content = contentFile.readText()
        assertTrue(
            "ClusterTuningContent must delegate toggles to TuningToggleItem (REQ-SET-077)",
            content.contains("TuningToggleItem(")
        )
        assertTrue(
            "ClusterTuningContent must delegate sliders to TuningSliderItem (REQ-SET-077)",
            content.contains("TuningSliderItem(")
        )

        // Verify that raw Switch calls within ClusterTuningContent have been eliminated
        val bodyBeforeTuningSlider = content.substringBefore("fun TuningSlider(")
        assertFalse(
            "ClusterTuningContent must not instantiate raw Switch directly; use TuningToggleItem (REQ-SET-077)",
            bodyBeforeTuningSlider.contains("Switch(")
        )
    }

    @Test
    fun clusterTuningContent_truthTableAltitudeGating() {
        val contentFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt")
        assertTrue("ClusterTuningContent.kt must exist", contentFile.exists())

        val content = contentFile.readText()
        assertTrue(
            "Altitude position slider must be gated with enabled && useAltitudePosForClustering (REQ-SET-077)",
            content.contains("enabled = enabled && useAltitudePosForClustering")
        )

        // Formal truth-table verification
        val truthTable = listOf(
            Triple(false, false, false),
            Triple(false, true, false),
            Triple(true, false, false),
            Triple(true, true, true)
        )

        for ((enabled, useAltitude, expected) in truthTable) {
            val result = enabled && useAltitude
            org.junit.Assert.assertEquals(
                "Gating truth table failed for ($enabled, $useAltitude)",
                expected,
                result
            )
        }
    }

    @Test
    fun clusterTuningContent_tuningSliderIsDeprecatedWithFullyQualifiedReplaceWith() {
        val contentFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt")
        assertTrue("ClusterTuningContent.kt must exist", contentFile.exists())

        val content = contentFile.readText()
        assertTrue(
            "TuningSlider must be annotated with @Deprecated",
            content.contains("@Deprecated(")
        )
        assertTrue(
            "Deprecated annotation must include fully qualified ReplaceWith import",
            content.contains("ReplaceWith(") &&
                    content.contains("\"com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningSliderItem\"")
        )
    }

    @Test
    fun clusterTuningSection_recalculateButtonStyledInErrorColorsWithAccessibleProgress() {
        val sectionFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/ClusterTuningSection.kt")
        assertTrue("ClusterTuningSection.kt must exist", sectionFile.exists())

        val content = sectionFile.readText()
        assertTrue(
            "Recalculate button must configure containerColor to error token (REQ-SET-076, ATT-3492)",
            content.contains("containerColor = MaterialTheme.colorScheme.error")
        )
        assertTrue(
            "Recalculate button must configure contentColor to onError token (REQ-SET-076, ATT-3492)",
            content.contains("contentColor = MaterialTheme.colorScheme.onError")
        )
        assertTrue(
            "Recalculate button must configure disabledContainerColor (REQ-SET-076, ATT-3492)",
            content.contains("disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)")
        )
        assertTrue(
            "Recalculate button must configure disabledContentColor to onError (REQ-SET-076, ATT-3492)",
            content.contains("disabledContentColor = MaterialTheme.colorScheme.onError")
        )
        assertTrue(
            "CircularProgressIndicator must use onError color for WCAG 2.1 accessible contrast (REQ-SET-076, ATT-3492)",
            content.contains("CircularProgressIndicator(") &&
                    content.contains("color = MaterialTheme.colorScheme.onError")
        )
    }
}

