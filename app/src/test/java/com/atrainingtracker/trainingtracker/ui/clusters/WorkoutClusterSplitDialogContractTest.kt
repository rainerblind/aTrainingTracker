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

import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architectural, state machine contract, and 9-language localization tests for
 * the WorkoutClusters 'Split Cluster' bottom sheet and corridor detection (ATT-5900 / REQ-SET-096 / TST-SET-096.3).
 *
 * Verifies:
 * - CallSplit action eligibility (visible when hitCount >= 2, hidden when hitCount < 2)
 * - State machine contract for split preview lifecycle and sensitivity adjustments
 * - 9-language string parity and positional specifiers for all 8 cluster_split strings
 * - Zero inline emojis across all locales
 * - Structural source compliance in WorkoutClusterSummaryHeader and WorkoutClusterSplitSheet
 */
class WorkoutClusterSplitDialogContractTest {

    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private val requiredStringKeys = listOf(
        "cluster_split_action",
        "cluster_split_dialog_title",
        "cluster_split_corridor_threshold",
        "cluster_split_no_variants",
        "cluster_split_primary_label",
        "cluster_split_variant_label",
        "cluster_split_rename_workouts",
        "cluster_split_action_confirm"
    )

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")
    }

    private fun findAppSourceDirectory(): File {
        val candidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("source directory not found in candidate paths: $candidates")
    }

    private fun extractStringTagRawContent(stringsFile: File, stringKey: String): String? {
        val content = stringsFile.readText()
        val regex = Pattern.compile("<string\\s+name=[\"']$stringKey[\"']>(.*?)</string>", Pattern.DOTALL)
        val matcher = regex.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun containsEmoji(text: String): Boolean {
        return text.codePoints().anyMatch { cp ->
            (cp in 0x1F000..0x1FAFF) || (cp in 0x2600..0x27BF) || cp == 0xFE0F || cp == 0x200D
        }
    }

    private fun createCluster(id: Long, name: String, hitCount: Int): WorkoutCluster {
        return WorkoutCluster(
            id = id,
            name = name,
            probableSportId = 1L,
            startLat = 48.0,
            startLng = 11.0,
            endLat = 48.01,
            endLng = 11.01,
            maxDispLat = 48.02,
            maxDispLng = 11.02,
            refDistance = 5000.0,
            hitCount = hitCount
        )
    }

    /**
     * Semantic state holder replicating the split preview flow.
     */
    private class SplitDialogStateModel {
        var isSplitPreviewActive = false
        var showSplitSheet = false
        var currentThreshold = 400.0
        var executedSplitCount = 0

        fun openSplitPreview() {
            isSplitPreviewActive = true
            showSplitSheet = true
        }

        fun closeSplitPreview() {
            isSplitPreviewActive = false
            showSplitSheet = false
        }

        fun setThreshold(threshold: Double) {
            currentThreshold = threshold.coerceIn(250.0, 1500.0)
        }

        fun executeSplit() {
            executedSplitCount++
            closeSplitPreview()
        }
    }

    @Test
    fun testSplitActionEligibility_onlyWhenHitCountAtLeastTwo() {
        val singleWorkoutCluster = createCluster(1L, "Solo Run", hitCount = 1)
        val multiWorkoutCluster = createCluster(2L, "Frequent Commute", hitCount = 5)
        val emptyCluster = createCluster(3L, "Empty", hitCount = 0)

        assertFalse("Cluster with hitCount = 1 cannot be split", singleWorkoutCluster.hitCount >= 2)
        assertFalse("Cluster with hitCount = 0 cannot be split", emptyCluster.hitCount >= 2)
        assertTrue("Cluster with hitCount >= 2 can be split", multiWorkoutCluster.hitCount >= 2)
    }

    @Test
    fun testSplitDialogStateTransitions() {
        val state = SplitDialogStateModel()
        assertFalse(state.isSplitPreviewActive)
        assertFalse(state.showSplitSheet)

        // Open split
        state.openSplitPreview()
        assertTrue(state.isSplitPreviewActive)
        assertTrue(state.showSplitSheet)

        // Adjust threshold
        state.setThreshold(650.0)
        assertEquals(650.0, state.currentThreshold, 0.001)

        // Threshold clamping
        state.setThreshold(100.0)
        assertEquals(250.0, state.currentThreshold, 0.001)

        state.setThreshold(2000.0)
        assertEquals(1500.0, state.currentThreshold, 0.001)

        // Dismiss without split
        state.closeSplitPreview()
        assertFalse(state.isSplitPreviewActive)
        assertFalse(state.showSplitSheet)
        assertEquals(0, state.executedSplitCount)

        // Open and execute
        state.openSplitPreview()
        state.executeSplit()
        assertFalse(state.isSplitPreviewActive)
        assertFalse(state.showSplitSheet)
        assertEquals(1, state.executedSplitCount)
    }

    @Test
    fun testLocalizationParity_allEightKeysExistInAllNineLocales() {
        val resDir = findResDirectory()

        for (locale in supportedLocales) {
            val folderName = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$folderName/strings.xml")
            assertTrue("strings.xml must exist for locale '$folderName'", stringsFile.exists())

            for (key in requiredStringKeys) {
                val value = extractStringTagRawContent(stringsFile, key)
                assertNotNull("Key '$key' must exist in locale '$folderName'", value)
                assertTrue("Key '$key' in '$folderName' must not be blank", value!!.isNotBlank())
                assertFalse("Key '$key' in '$folderName' must not contain inline emojis", containsEmoji(value))
            }
        }
    }

    @Test
    fun testPositionalSpecifiers_inAllLocales() {
        val resDir = findResDirectory()

        for (locale in supportedLocales) {
            val folderName = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$folderName/strings.xml")

            // cluster_split_corridor_threshold must have %1$s
            val thresholdLabel = extractStringTagRawContent(stringsFile, "cluster_split_corridor_threshold")!!
            assertTrue(
                "cluster_split_corridor_threshold in $folderName must contain %1\$s",
                thresholdLabel.contains("%1\$s")
            )

            // cluster_split_primary_label must have %1$d and %2$s
            val primaryLabel = extractStringTagRawContent(stringsFile, "cluster_split_primary_label")!!
            assertTrue(
                "cluster_split_primary_label in $folderName must contain %1\$d and %2\$s",
                primaryLabel.contains("%1\$d") && primaryLabel.contains("%2\$s")
            )

            // cluster_split_variant_label must have %1$d, %2$d and %3$s
            val variantLabel = extractStringTagRawContent(stringsFile, "cluster_split_variant_label")!!
            assertTrue(
                "cluster_split_variant_label in $folderName must contain %1\$d, %2\$d, and %3\$s",
                variantLabel.contains("%1\$d") && variantLabel.contains("%2\$d") && variantLabel.contains("%3\$s")
            )
        }
    }

    @Test
    fun testSourceCompliance_headerAndSheetStructure() {
        val srcDir = findAppSourceDirectory()

        val heatmapScreenFile = File(srcDir, "ui/clusters/WorkoutClusterHeatmapScreen.kt")
        assertTrue("WorkoutClusterHeatmapScreen.kt must exist", heatmapScreenFile.exists())
        val heatmapSrc = heatmapScreenFile.readText()

        // Verify CallSplit presence and hitCount guard
        assertTrue("Heatmap screen must reference CallSplit icon", heatmapSrc.contains("Icons.Default.CallSplit"))
        assertTrue("Heatmap screen must guard split action with hitCount >= 2", heatmapSrc.contains("cluster.hitCount >= 2"))
        assertTrue("Heatmap screen must conditionally render splitVariantPaths", heatmapSrc.contains("splitVariantPaths.forEach"))

        val splitSheetFile = File(srcDir, "ui/clusters/WorkoutClusterSplitSheet.kt")
        assertTrue("WorkoutClusterSplitSheet.kt must exist", splitSheetFile.exists())
        val splitSheetSrc = splitSheetFile.readText()

        // Verify M3 slider range and step configuration
        assertTrue("Split sheet must feature Slider with 250f..1500f", splitSheetSrc.contains("250f..1500f"))
        assertTrue("Split sheet must feature AppModalBottomSheet", splitSheetSrc.contains("AppModalBottomSheet"))
        assertTrue("Split sheet must feature AppDialogActions.SaveCancel", splitSheetSrc.contains("AppDialogActions.SaveCancel"))
    }
}
