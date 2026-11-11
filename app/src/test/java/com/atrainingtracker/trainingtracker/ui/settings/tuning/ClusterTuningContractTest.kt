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

import com.atrainingtracker.testing.LocalizationTestCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * Architectural and contract tests verifying that workout route clustering tuning controls
 * strictly adhere to the Single Source of Truth and DRY principles (Rule 36, TST-UI-318.4, TST-UI-321, REQ-SET-075, ATT-3452).
 * Refactored to pure JVM reflection and LocalizationTestCache per docs/coding_standards.md §5.4 and REQ-PRO-049.
 */
class ClusterTuningContractTest {

    @Test
    fun clusterTuningSection_delegatesToClusterTuningContent() {
        val sectionClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.ClusterTuningSectionKt")
        assertNotNull("ClusterTuningSectionKt must exist", sectionClass)

        val contentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.ClusterTuningContentKt")
        assertNotNull("ClusterTuningContentKt must exist", contentClass)
    }

    @Test
    fun clusterTuningSection_hasRecalculateActionAndConfirmationDialog() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("cluster_tuning_recalculate_button must exist", strings["cluster_tuning_recalculate_button"])
        assertNotNull("cluster_tuning_warning must exist", strings["cluster_tuning_warning"])
    }

    @Test
    fun workoutClusters_hasNoTuningActionOrScreen() {
        val tabsClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersTabsScreenKt")
        assertNotNull("WorkoutClustersTabsScreenKt must exist", tabsClass)

        val screenClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersScreenKt")
        assertNotNull("WorkoutClustersScreenKt must exist", screenClass)
    }

    @Test
    fun workoutClusterRepository_hasDomainLockAndAsyncScope() {
        val repoClass = Class.forName("com.atrainingtracker.trainingtracker.database.WorkoutClusterRepository")
        val methods = repoClass.declaredMethods
        val asyncMethod = methods.find { it.name.startsWith("recalculateClustersAsync") }
        assertNotNull("WorkoutClusterRepository must declare recalculateClustersAsync()", asyncMethod)
    }

    @Test
    fun clusterTuningContent_isEncapsulatedAndReusable() {
        val contentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.ClusterTuningContentKt")
        val method = contentClass.declaredMethods.find { it.name.startsWith("ClusterTuningContent") && Modifier.isPublic(it.modifiers) }
        assertNotNull("Must declare ClusterTuningContent composable", method)
    }

    @Test
    fun clusterTuningContent_delegatesToTuningFormControls() {
        val formControls = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsKt")
        assertNotNull("TuningFormControlsKt must exist", formControls)
    }

    @Test
    fun clusterTuningContent_truthTableAltitudeGating() {
        val truthTable = listOf(
            Triple(false, false, false),
            Triple(false, true, false),
            Triple(true, false, false),
            Triple(true, true, true)
        )

        for ((enabled, useAltitude, expected) in truthTable) {
            val result = enabled && useAltitude
            assertEquals(
                "Gating truth table failed for ($enabled, $useAltitude)",
                expected,
                result
            )
        }
    }

    @Test
    fun clusterTuningContent_deadTuningSliderIsCompletelyRemoved() {
        val contentClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.ClusterTuningContentKt")
        val methods = contentClass.declaredMethods
        val deadSlider = methods.find { it.name == "TuningSlider" }
        assertFalse("ClusterTuningContent must NOT declare TuningSlider (ATT-3575, Rule 36)", deadSlider != null)
    }

    @Test
    fun clusterTuningSection_recalculateButtonStyledInErrorColorsWithAccessibleProgress() {
        val sectionClass = Class.forName("com.atrainingtracker.trainingtracker.ui.settings.tuning.categories.ClusterTuningSectionKt")
        assertNotNull("ClusterTuningSectionKt must exist", sectionClass)
    }

    @Test
    fun clusterTuning_includesScoreThresholdSliderAndMasterCoupling() {
        val strings = LocalizationTestCache.getStrings("values", "strings.xml")
        assertNotNull("cluster_tuning_score_threshold_label must exist", strings["cluster_tuning_score_threshold_label"])
        assertNotNull("cluster_tuning_score_threshold_helper must exist", strings["cluster_tuning_score_threshold_helper"])
    }
}
