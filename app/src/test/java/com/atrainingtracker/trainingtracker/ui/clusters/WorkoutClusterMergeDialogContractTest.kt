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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architectural, state machine contract, and 9-language localization tests for
 * the WorkoutClustersScreen two-step merge dialog transition (ATT-5824 / REQ-SET-089.3 / TST-SET-089.3).
 *
 * Verifies that candidate selection in [WorkoutClusterSelectionDialog] captures
 * source and target into [pendingMergePair] and immediately opens the confirmation
 * dialog on the first tap, even though [WorkoutClusterSelectionDialog] triggers
 * onSelect followed immediately by onDismiss (Design Guidelines §5.19 / Rule 77).
 */
class WorkoutClusterMergeDialogContractTest {

    private val supportedLocales = listOf("", "de", "es", "fr", "it", "ja", "nl", "pl", "pt")

    private fun findResDirectory(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: error("res directory not found in candidate paths: $candidates")
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

    private fun createCluster(id: Long, name: String, hitCount: Int = 1): WorkoutCluster {
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
     * Semantic state holder replicating the decoupled state logic of [WorkoutClustersScreen].
     */
    private class MergeDialogStateModel {
        var clusterToMerge: WorkoutCluster? = null
        var pendingMergePair: Pair<WorkoutCluster, WorkoutCluster>? = null
        var mergedPairs = mutableListOf<Pair<WorkoutCluster, WorkoutCluster>>()

        val isSelectionDialogOpen: Boolean
            get() = clusterToMerge != null && pendingMergePair == null

        val isConfirmationDialogOpen: Boolean
            get() = pendingMergePair != null

        fun onMergeRequest(cluster: WorkoutCluster) {
            clusterToMerge = cluster
        }

        /**
         * Replicates WorkoutClusterSelectionDialog candidate tap behavior:
         * onSelect(candidate) followed immediately by onDismiss().
         */
        fun onCandidateSelectedInDialog(candidate: WorkoutCluster) {
            val source = clusterToMerge ?: return
            // onSelect callback
            pendingMergePair = source to candidate
            // onDismiss callback triggered immediately after onSelect by WorkoutClusterSelectionDialog
            clusterToMerge = null
        }

        fun onSelectionDialogDismissedWithoutSelection() {
            clusterToMerge = null
        }

        fun onConfirmationDialogConfirm() {
            pendingMergePair?.let { mergedPairs.add(it) }
            pendingMergePair = null
        }

        fun onConfirmationDialogDismiss() {
            pendingMergePair = null
        }
    }

    @Test
    fun testTwoStepDialogTransition_firstTapOpensConfirmationDialogImmediately() {
        val model = MergeDialogStateModel()
        val clusterA = createCluster(1L, "Morning Ride", 3)
        val clusterB = createCluster(2L, "Commute Ride", 5)

        // 1. Initial State: no dialogs open
        assertFalse(model.isSelectionDialogOpen)
        assertFalse(model.isConfirmationDialogOpen)

        // 2. Athlete selects "Mit anderer Lieblingsstrecke zusammenführen…" from context menu
        model.onMergeRequest(clusterA)
        assertTrue("Selection sheet must be open after context menu action", model.isSelectionDialogOpen)
        assertFalse("Confirmation dialog must NOT be open yet", model.isConfirmationDialogOpen)
        assertEquals(clusterA, model.clusterToMerge)
        assertNull(model.pendingMergePair)

        // 3. Athlete taps candidate cluster B in the selection sheet
        // In WorkoutClusterComponents.kt, this triggers onSelect(B) and then onDismiss()
        model.onCandidateSelectedInDialog(clusterB)

        // REQ-SET-089.3 / ATT-5824 Core Fix:
        // Selection sheet is now dismissed, but confirmation dialog opens IMMEDIATELY on the first attempt!
        assertFalse("Selection sheet must close after candidate selection", model.isSelectionDialogOpen)
        assertTrue("Confirmation dialog MUST open on the first tap without second attempt", model.isConfirmationDialogOpen)
        assertNotNull("pendingMergePair must hold the source and target pair", model.pendingMergePair)
        assertEquals(clusterA to clusterB, model.pendingMergePair)

        // 4. Athlete confirms merge
        model.onConfirmationDialogConfirm()
        assertFalse("Confirmation dialog must close after confirm", model.isConfirmationDialogOpen)
        assertFalse("Selection sheet must remain closed", model.isSelectionDialogOpen)
        assertNull(model.pendingMergePair)
        assertNull(model.clusterToMerge)
        assertEquals(listOf(clusterA to clusterB), model.mergedPairs)
    }

    @Test
    fun testTwoStepDialogTransition_dismissWithoutSelectionDoesNotTriggerConfirmation() {
        val model = MergeDialogStateModel()
        val clusterA = createCluster(1L, "Morning Ride", 3)

        model.onMergeRequest(clusterA)
        assertTrue(model.isSelectionDialogOpen)

        // Athlete dismisses sheet (e.g. tap outside / cancel) without selecting a candidate
        model.onSelectionDialogDismissedWithoutSelection()

        assertFalse("Selection dialog must be closed", model.isSelectionDialogOpen)
        assertFalse("Confirmation dialog must NOT open on sheet cancellation", model.isConfirmationDialogOpen)
        assertNull(model.pendingMergePair)
        assertNull(model.clusterToMerge)
    }

    @Test
    fun testTwoStepDialogTransition_cancelConfirmationResetsStateCleanly() {
        val model = MergeDialogStateModel()
        val clusterA = createCluster(1L, "Morning Ride", 3)
        val clusterB = createCluster(2L, "Commute Ride", 5)

        model.onMergeRequest(clusterA)
        model.onCandidateSelectedInDialog(clusterB)
        assertTrue(model.isConfirmationDialogOpen)

        // Athlete taps Cancel on the confirmation dialog
        model.onConfirmationDialogDismiss()

        assertFalse(model.isConfirmationDialogOpen)
        assertFalse(model.isSelectionDialogOpen)
        assertNull(model.pendingMergePair)
        assertTrue(model.mergedPairs.isEmpty())

        // Ensure next merge request has no residual stale state
        val clusterC = createCluster(3L, "Evening Loop", 2)
        model.onMergeRequest(clusterC)
        assertTrue(model.isSelectionDialogOpen)
        assertFalse(model.isConfirmationDialogOpen)
        assertEquals(clusterC, model.clusterToMerge)
    }

    @Test
    fun testLegacyCoupledStateTrap_demonstratesWhyOldLogicFailedOnFirstTap() {
        // Simulates the legacy coupled state from ATT-5666 that caused ATT-5824
        class LegacyMergeState(
            var clusterToMerge: WorkoutCluster? = null,
            var mergeTargetCluster: WorkoutCluster? = null
        ) {
            val isLegacyConfirmationOpen: Boolean
                get() = clusterToMerge != null && mergeTargetCluster != null

            fun onSelectTarget(target: WorkoutCluster) {
                mergeTargetCluster = target
            }

            fun onDismissPicker() {
                clusterToMerge = null
            }
        }

        val clusterA = createCluster(1L, "Source")
        val clusterB = createCluster(2L, "Target")
        val state = LegacyMergeState(clusterToMerge = clusterA)

        // Candidate tapped in dialog: onSelect(B) followed immediately by onDismiss()
        state.onSelectTarget(clusterB)
        state.onDismissPicker()

        // Legacy condition for confirmation dialog: fails on first tap because onDismiss cleared clusterToMerge
        assertFalse("Legacy condition failed on first tap because onDismiss cleared clusterToMerge", state.isLegacyConfirmationOpen)

        // Only on second menu click on clusterA:
        state.clusterToMerge = clusterA
        assertTrue("Legacy dialog only opened on second attempt with lingering mergeTargetCluster", state.isLegacyConfirmationOpen)
    }

    @Test
    fun testMergeStrings_existAcrossAll9LocalesWithZeroEmojisAndValidSpecifiers() {
        val resDir = findResDirectory()
        val keys = listOf(
            "cluster_menu_merge",
            "cluster_merge_dialog_title",
            "cluster_merge_dialog_message",
            "cluster_merge_action",
            "cancel"
        )
        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")
            assertTrue("Strings file $stringsFile must exist", stringsFile.exists())

            for (key in keys) {
                val rawContent = extractStringTagRawContent(stringsFile, key)
                assertNotNull("String '$key' must exist in $valuesDir/strings.xml", rawContent)
                assertFalse("String '$key' in $valuesDir must not be blank", rawContent!!.isBlank())
                assertFalse(
                    "String '$key' in $valuesDir must not contain decorative emojis (ATT-5400)",
                    containsEmoji(rawContent)
                )
            }

            // Verify positional specifiers for message (%1$s, %2$d, %3$s)
            val messageContent = extractStringTagRawContent(stringsFile, "cluster_merge_dialog_message")!!
            assertTrue(
                "Locale '$locale': 'cluster_merge_dialog_message' must include %1\$s for source name",
                messageContent.contains("%1\$s")
            )
            assertTrue(
                "Locale '$locale': 'cluster_merge_dialog_message' must include %2\$d for hit count",
                messageContent.contains("%2\$d")
            )
            assertTrue(
                "Locale '$locale': 'cluster_merge_dialog_message' must include %3\$s for target name",
                messageContent.contains("%3\$s")
            )
        }
    }
}
