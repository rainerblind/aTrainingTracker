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
 */

package com.atrainingtracker.trainingtracker.ui.clusters

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract and architecture verification test suite for WorkoutClusterSplitSheet embedded map preview
 * and list-level split entry point (ATT-6318 / REQ-UI-505 / TST-UI-505).
 */
class WorkoutClusterSplitMapPreviewTest {

    private fun findAppSourceDirectory(): File {
        val candidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("Source directory not found in candidate paths: $candidates")
    }

    @Test
    fun testWorkoutClusterSplitSheet_embedsColorCodedMapPreviewAboveSlider() {
        val srcDir = findAppSourceDirectory()
        val splitSheetFile = File(srcDir, "ui/clusters/WorkoutClusterSplitSheet.kt")
        assertTrue("WorkoutClusterSplitSheet.kt must exist", splitSheetFile.exists())
        val content = splitSheetFile.readText()

        // REQ-UI-505.1 & TST-UI-505.1: PathPreviewMap embedded above slider with 200.dp height
        assertTrue("Must import PathPreviewMap in WorkoutClusterSplitSheet", content.contains("import com.atrainingtracker.trainingtracker.ui.map.PathPreviewMap"))
        assertTrue("Must collect splitVariantPaths from viewModel", content.contains("viewModel.splitVariantPaths.collectAsState()"))
        assertTrue("Must embed PathPreviewMap", content.contains("PathPreviewMap("))
        assertTrue("Must pass paths = splitVariantPaths to PathPreviewMap", content.contains("paths = splitVariantPaths"))
        assertTrue("Must use 200.dp height container for embedded map preview", content.contains("height(200.dp)"))
        assertTrue("Must calculate cluster bounds for map preview", content.contains("targetBounds = clusterBounds"))

        // REQ-UI-505.2: openSplitPreview called on cluster launch
        assertTrue("Must trigger openSplitPreview on cluster launch", content.contains("viewModel.openSplitPreview(cluster)"))

        // Placement contract: map preview Surface must appear before the corridor threshold slider Surface
        val mapIndex = content.indexOf("paths = splitVariantPaths")
        val sliderIndex = content.indexOf("cluster_split_corridor_threshold")
        assertTrue("PathPreviewMap must be placed directly above the corridor threshold slider", mapIndex != -1 && sliderIndex != -1 && mapIndex < sliderIndex)
    }

    @Test
    fun testPathPreviewMap_supportsMultiPathAndCompositeBounds() {
        val srcDir = findAppSourceDirectory()
        val pathPreviewFile = File(srcDir, "ui/map/PathPreviewMap.kt")
        assertTrue("PathPreviewMap.kt must exist", pathPreviewFile.exists())
        val content = pathPreviewFile.readText()

        // REQ-UI-505.3 & TST-UI-505.2: Multi-path parameter with backward compatibility
        assertTrue("PathPreviewMap must accept paths: List<MappablePath> with default emptyList()",
            content.contains("paths: List<MappablePath> = emptyList()"))
        assertTrue("PathPreviewMap must support single path default null",
            content.contains("path: MappablePath? = null"))
        assertTrue("PathPreviewMap must combine paths into allPaths",
            content.contains("allPaths"))
        assertTrue("PathPreviewMap must iterate over all paths for Polyline rendering",
            content.contains("allPaths.forEach"))
        assertTrue("PathPreviewMap must calculate composite bounds across all paths",
            content.contains("boundsBuilder.include(it)"))
    }

    @Test
    fun testClusterItem_and_WorkoutClustersScreen_wireSplitAction() {
        val srcDir = findAppSourceDirectory()

        // 1. ClusterItem in WorkoutClusterComponents.kt
        val componentsFile = File(srcDir, "ui/clusters/WorkoutClusterComponents.kt")
        assertTrue("WorkoutClusterComponents.kt must exist", componentsFile.exists())
        val componentsContent = componentsFile.readText()

        assertTrue("ClusterItem must declare onSplitRequest callback parameter",
            componentsContent.contains("onSplitRequest: ((WorkoutCluster) -> Unit)? = null"))
        assertTrue("ClusterItem context menu must guard split action by hitCount >= 2",
            componentsContent.contains("cluster.hitCount >= 2 && onSplitRequest != null"))
        assertTrue("ClusterItem context menu must display cluster_split_action",
            componentsContent.contains("R.string.cluster_split_action"))
        assertTrue("ClusterItem context menu must use CallSplit icon",
            componentsContent.contains("Icons.Default.CallSplit"))

        // 2. WorkoutClustersTabsScreen.kt forwarding
        val tabsFile = File(srcDir, "ui/clusters/WorkoutClustersTabsScreen.kt")
        assertTrue("WorkoutClustersTabsScreen.kt must exist", tabsFile.exists())
        val tabsContent = tabsFile.readText()
        assertTrue("WorkoutClustersTabsScreen must declare onSplitRequest",
            tabsContent.contains("onSplitRequest: (WorkoutCluster) -> Unit = {}"))
        assertTrue("WorkoutClustersTabsScreen must pass onSplitRequest to WorkoutClustersList",
            tabsContent.contains("onSplitRequest = onSplitRequest"))

        // 3. WorkoutClustersScreen.kt split sheet presentation
        val screenFile = File(srcDir, "ui/clusters/WorkoutClustersScreen.kt")
        assertTrue("WorkoutClustersScreen.kt must exist", screenFile.exists())
        val screenContent = screenFile.readText()
        assertTrue("WorkoutClustersScreen must declare clusterToSplit state",
            screenContent.contains("clusterToSplit"))
        assertTrue("WorkoutClustersScreen must wire onSplitRequest callback",
            screenContent.contains("onSplitRequest = { clusterToSplit = it }"))
        assertTrue("WorkoutClustersScreen must display WorkoutClusterSplitSheet when clusterToSplit is active",
            screenContent.contains("WorkoutClusterSplitSheet("))
    }

    @Test
    fun testViewModel_supportsStandaloneSplitPreview() {
        val srcDir = findAppSourceDirectory()
        val vmFile = File(srcDir, "ui/clusters/WorkoutClustersViewModel.kt")
        assertTrue("WorkoutClustersViewModel.kt must exist", vmFile.exists())
        val vmContent = vmFile.readText()

        assertTrue("ViewModel must support openSplitPreview with cluster parameter",
            vmContent.contains("fun openSplitPreview(cluster: WorkoutCluster? = null)"))
        assertTrue("ViewModel must load workouts for cluster if supplied",
            vmContent.contains("repository.getWorkoutsForCluster(cluster.id)"))
        assertTrue("ViewModel closeSplitPreview must reset splitTargetCluster",
            vmContent.contains("splitTargetCluster = null"))
    }
}
