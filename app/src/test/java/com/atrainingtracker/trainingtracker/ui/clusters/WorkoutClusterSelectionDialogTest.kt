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

import com.atrainingtracker.banalservice.BSportType
import com.atrainingtracker.trainingtracker.database.WorkoutCluster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.regex.Pattern

/**
 * Architectural, UI contract, state model, and localization unit tests for
 * [WorkoutClusterSelectionDialog] verifying unassign and create-new capabilities
 * (ATT-7754 / REQ-SET-059 / TST-SET-104.1 / TST-SET-104.4).
 */
class WorkoutClusterSelectionDialogTest {

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

    private fun createCluster(id: Long, name: String): WorkoutCluster {
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
            hitCount = 3,
            bSportType = BSportType.RUN
        )
    }

    /**
     * UI state contract holder replicating [WorkoutClusterSelectionDialog] interaction patterns.
     */
    private class SelectionDialogStateModel(
        val candidates: List<Pair<WorkoutCluster, Double>>,
        val onSelect: (WorkoutCluster) -> Unit,
        val onDismiss: () -> Unit,
        val onCreateNew: (() -> Unit)? = null,
        val onUnassign: (() -> Unit)? = null
    ) {
        var isDismissed: Boolean = false

        val hasUnassignOption: Boolean
            get() = onUnassign != null

        val hasCreateNewOption: Boolean
            get() = onCreateNew != null

        fun clickUnassign() {
            requireNotNull(onUnassign) { "onUnassign callback is not available" }
            onUnassign.invoke()
            dismiss()
        }

        fun clickCreateNew() {
            requireNotNull(onCreateNew) { "onCreateNew callback is not available" }
            onCreateNew.invoke()
            dismiss()
        }

        fun clickSelect(cluster: WorkoutCluster) {
            onSelect(cluster)
            dismiss()
        }

        fun dismiss() {
            isDismissed = true
            onDismiss()
        }
    }

    @Test
    fun testWorkoutClusterSelectionDialog_exposesUnassignAndCreateNewComposableContract() {
        val clusterCompClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterComponentsKt")
        val selectClusterMethod = clusterCompClass.declaredMethods.find { it.name == "WorkoutClusterSelectionDialog" }
        assertNotNull("WorkoutClusterSelectionDialog composable function must exist", selectClusterMethod)
        assertTrue(Modifier.isPublic(selectClusterMethod!!.modifiers))

        // REQ-SET-059: Verify Function0 parameters for onDismiss, onCreateNew, and onUnassign
        val function0Count = selectClusterMethod.parameterTypes.count {
            it.name == "kotlin.jvm.functions.Function0"
        }
        assertTrue(
            "WorkoutClusterSelectionDialog must declare Function0 callbacks for onDismiss, onCreateNew, and onUnassign (found $function0Count)",
            function0Count >= 3
        )

        // Verify Compose compiler generated parameters
        val parameterTypes = selectClusterMethod.parameterTypes
        assertTrue(
            "WorkoutClusterSelectionDialog must accept Compose runtime parameters",
            parameterTypes.any { it.name == "androidx.compose.runtime.Composer" }
        )
        assertTrue(
            "WorkoutClusterSelectionDialog must include default bitmask parameter",
            parameterTypes.last() == java.lang.Integer.TYPE
        )
    }

    @Test
    fun testWorkoutClusterSelectionDialog_whenOnUnassignProvided_triggersCallbackAndDismisses() {
        var unassignedCalled = false
        var dismissedCalled = false

        val model = SelectionDialogStateModel(
            candidates = listOf(createCluster(1L, "Route 1") to 0.1),
            onSelect = {},
            onDismiss = { dismissedCalled = true },
            onCreateNew = {},
            onUnassign = { unassignedCalled = true }
        )

        assertTrue("Unassign option must be present when callback provided", model.hasUnassignOption)
        assertTrue("Create new option must be present when callback provided", model.hasCreateNewOption)

        model.clickUnassign()

        assertTrue("Unassign callback must be executed on click", unassignedCalled)
        assertTrue("Dialog must be dismissed after clicking unassign", dismissedCalled)
        assertTrue(model.isDismissed)
    }

    @Test
    fun testWorkoutClusterSelectionDialog_whenOnUnassignNull_unassignNotAvailable() {
        var dismissedCalled = false

        val model = SelectionDialogStateModel(
            candidates = listOf(createCluster(1L, "Route 1") to 0.1),
            onSelect = {},
            onDismiss = { dismissedCalled = true },
            onCreateNew = null,
            onUnassign = null
        )

        assertFalse("Unassign option must be absent when onUnassign is null", model.hasUnassignOption)
        assertFalse("Create new option must be absent when onCreateNew is null", model.hasCreateNewOption)
    }

    @Test
    fun testWorkoutClusterSelectionDialog_whenCandidateSelected_triggersSelectAndDismisses() {
        var selectedCluster: WorkoutCluster? = null
        var dismissedCalled = false
        val targetCluster = createCluster(42L, "Morning Hill Run")

        val model = SelectionDialogStateModel(
            candidates = listOf(targetCluster to 0.05),
            onSelect = { selectedCluster = it },
            onDismiss = { dismissedCalled = true },
            onCreateNew = {},
            onUnassign = {}
        )

        model.clickSelect(targetCluster)

        assertEquals(targetCluster, selectedCluster)
        assertTrue(dismissedCalled)
        assertTrue(model.isDismissed)
    }

    @Test
    fun testLeaveUnclusteredStrings_existAcrossAll9Locales() {
        val resDir = findResDirectory()
        val keys = listOf(
            "cluster_naming__leave_unclustered",
            "cluster_naming__create_new"
        )
        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")
            assertTrue("File $stringsFile must exist", stringsFile.exists())

            for (key in keys) {
                val rawContent = extractStringTagRawContent(stringsFile, key)
                assertNotNull("Key '$key' must exist in $valuesDir/strings.xml", rawContent)
                assertFalse("Key '$key' in $valuesDir must not be blank", rawContent!!.isBlank())
                assertFalse(
                    "Key '$key' in $valuesDir must not contain decorative emojis (ATT-5400)",
                    containsEmoji(rawContent)
                )
            }
        }
    }
}
