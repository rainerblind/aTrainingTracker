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

import com.atrainingtracker.trainingtracker.database.WorkoutClusterEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.regex.Pattern

/**
 * Architectural, UI contract, and 9-language localization tests for [ClusterNamingDialog]
 * escape hatches and [WorkoutClusterSelectionDialog] create-new capability (ATT-5674 / REQ-UI-462 / TST-UI-462).
 */
class ClusterNamingDialogEscapeContractTest {

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

    @Test
    fun testWorkoutClusterSelectionDialog_exposesCreateNewComposableContract() {
        val clusterCompClass = Class.forName("com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClusterComponentsKt")
        val selectClusterMethod = clusterCompClass.declaredMethods.find { it.name == "WorkoutClusterSelectionDialog" }
        assertNotNull("WorkoutClusterSelectionDialog composable function must exist", selectClusterMethod)
        assertTrue(Modifier.isPublic(selectClusterMethod!!.modifiers))

        // REQ-UI-462.1: Verify parameter count and presence of Function0 callback for onCreateNew
        val function0Count = selectClusterMethod.parameterTypes.count {
            it.name == "kotlin.jvm.functions.Function0"
        }
        assertTrue(
            "WorkoutClusterSelectionDialog must declare Function0 callbacks for both onDismiss and onCreateNew (found $function0Count)",
            function0Count >= 2
        )

        // Verify Compose compiler generates default-argument bitmask parameter
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
    fun testClusterEscapeStrings_existAcrossAll9Locales() {
        val resDir = findResDirectory()
        val keys = listOf(
            "cluster_naming__create_new",
            "clear",
            "select_route"
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

    @Test
    fun testRouteNamePreservation_stripHitCountInvariant() {
        // REQ-UI-462.3: Switching back from existing cluster to create-new restores previous stripped name
        val originalName = "Hausrunde #14"
        val stripped = WorkoutClusterEngine.stripHitCount(originalName)
        assertEquals("Hausrunde", stripped)

        val unnumbered = "Feierabendrunde"
        assertEquals("Feierabendrunde", WorkoutClusterEngine.stripHitCount(unnumbered))
    }
}
