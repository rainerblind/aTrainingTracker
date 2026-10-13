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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural and contract tests verifying that workout route clustering tuning controls
 * strictly adhere to the Single Source of Truth and DRY principles (Rule 36, TST-UI-318.4, REQ-SET-075).
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
    }

    @Test
    fun clusterTuningScreen_delegatesToClusterTuningContent() {
        val screenFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningScreen.kt")
        assertTrue("ClusterTuningScreen.kt must exist", screenFile.exists())

        val content = screenFile.readText()
        assertTrue(
            "ClusterTuningScreen must delegate to shared ClusterTuningContent composable (Rule 36)",
            content.contains("ClusterTuningContent(")
        )
        assertTrue(
            "ClusterTuningScreen must preserve contextual recalculation button",
            content.contains("recalculateClusters") || content.contains("cluster_tuning_recalculate")
        )
    }

    @Test
    fun clusterTuningContent_isEncapsulatedAndReusable() {
        val contentFile = File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt")
        assertTrue("ClusterTuningContent.kt must exist", contentFile.exists())

        val content = contentFile.readText()
        assertTrue("Must declare ClusterTuningContent composable", content.contains("fun ClusterTuningContent("))
        assertTrue("Must declare TuningSlider composable", content.contains("fun TuningSlider("))
    }
}
