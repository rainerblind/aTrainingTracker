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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architectural, UI disambiguation, and 9-language localization contract tests for [ClusterNamingDialog]
 * ensuring that route assignment and creation sections explicitly label favorite route (Lieblingsstrecke)
 * creation rather than generic naming prompts, resolving visual ambiguity against the workout name field
 * (ATT-5664 / REQ-UI-123 / TST-UI-078).
 */
class ClusterNamingDialogLabelsVisualContractTest {

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

    private fun findSourceFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../app/$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Source file not found: $relativePath")
    }

    private fun extractStringTagRawContent(stringsFile: File, stringKey: String): String? {
        val content = stringsFile.readText()
        val regex = Pattern.compile("<string\\s+name=[\"']$stringKey[\"']>(.*?)</string>", Pattern.DOTALL)
        val matcher = regex.matcher(content)
        return if (matcher.find()) matcher.group(1) else null
    }

    @Test
    fun testClusterNamingStrings_existAcrossAll9Locales() {
        val resDir = findResDirectory()
        val keys = listOf(
            "cluster_naming__assignment_label",
            "cluster_naming__new_name_prompt",
            "cluster_naming__new_route_name_label",
            "cluster_naming__found_recurring"
        )
        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")
            assertTrue("File $stringsFile must exist", stringsFile.exists())

            for (key in keys) {
                val rawContent = extractStringTagRawContent(stringsFile, key)
                assertNotNull("Key '$key' must exist in $valuesDir/strings.xml", rawContent)
                assertFalse("Key '$key' in $valuesDir must not be blank", rawContent!!.isBlank())
            }

            val recurring = extractStringTagRawContent(stringsFile, "cluster_naming__found_recurring")
            assertTrue(
                "cluster_naming__found_recurring in $valuesDir must contain '%1\$s'",
                recurring!!.contains("%1\$s")
            )
        }
    }

    @Test
    fun testGermanStrings_explicitlyReferenceLieblingsstrecke() {
        val resDir = findResDirectory()
        val stringsFile = File(resDir, "values-de/strings.xml")

        val assignmentLabel = extractStringTagRawContent(stringsFile, "cluster_naming__assignment_label")
        val newNamePrompt = extractStringTagRawContent(stringsFile, "cluster_naming__new_name_prompt")
        val newRouteNameLabel = extractStringTagRawContent(stringsFile, "cluster_naming__new_route_name_label")
        val foundRecurring = extractStringTagRawContent(stringsFile, "cluster_naming__found_recurring")

        assertEquals("Bestehende Lieblingsstrecke auswählen:", assignmentLabel)
        assertEquals("Oder neue Lieblingsstrecke anlegen:", newNamePrompt)
        assertEquals("Name der neuen Lieblingsstrecke", newRouteNameLabel)
        assertEquals("Nicht automatisch zuweisbare Strecke vom %1\$s.", foundRecurring)
    }

    @Test
    fun testEnglishStrings_explicitlyReferenceFavoriteRoute() {
        val resDir = findResDirectory()
        val stringsFile = File(resDir, "values/strings.xml")

        val assignmentLabel = extractStringTagRawContent(stringsFile, "cluster_naming__assignment_label")
        val newNamePrompt = extractStringTagRawContent(stringsFile, "cluster_naming__new_name_prompt")
        val newRouteNameLabel = extractStringTagRawContent(stringsFile, "cluster_naming__new_route_name_label")
        val foundRecurring = extractStringTagRawContent(stringsFile, "cluster_naming__found_recurring")

        assertEquals("Select existing favorite route:", assignmentLabel)
        assertEquals("Or create a new favorite route:", newNamePrompt)
        assertEquals("New Favorite Route Name", newRouteNameLabel)
        assertEquals("Unassigned route from %1\$s.", foundRecurring)
    }

    private fun containsEmoji(text: String): Boolean {
        return text.codePoints().anyMatch { cp ->
            (cp in 0x1F000..0x1FAFF) || (cp in 0x2600..0x27BF) || cp == 0xFE0F || cp == 0x200D
        }
    }

    @Test
    fun testClusterNamingStrings_containNoEmojisAcrossAll9Locales() {
        val resDir = findResDirectory()
        val keys = listOf(
            "cluster_naming__assignment_label",
            "cluster_naming__new_name_prompt",
            "cluster_naming__new_route_name_label",
            "cluster_naming__found_recurring"
        )

        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")

            for (key in keys) {
                val rawContent = extractStringTagRawContent(stringsFile, key)
                assertNotNull(rawContent)
                assertFalse(
                    "String '$key' in $valuesDir must not contain decorative emojis (ATT-5400)",
                    containsEmoji(rawContent!!)
                )
            }
        }
    }

    @Test
    fun testCallSites_referenceClusterNamingStringResources() {
        val importScreen = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt")
        val importContent = importScreen.readText()

        assertTrue(
            "ImportBackupTabsScreen must reference cluster_naming__assignment_label",
            importContent.contains("R.string.cluster_naming__assignment_label")
        )
        assertTrue(
            "ImportBackupTabsScreen must reference cluster_naming__new_name_prompt",
            importContent.contains("R.string.cluster_naming__new_name_prompt")
        )
        assertTrue(
            "ImportBackupTabsScreen must reference cluster_naming__new_route_name_label",
            importContent.contains("R.string.cluster_naming__new_route_name_label")
        )
        assertTrue(
            "ImportBackupTabsScreen must reference cluster_naming__found_recurring",
            importContent.contains("R.string.cluster_naming__found_recurring")
        )

        val clusterComponents = findSourceFile("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClusterComponents.kt")
        val clusterContent = clusterComponents.readText()

        assertTrue(
            "WorkoutClusterComponents must reference cluster_naming__new_name_prompt",
            clusterContent.contains("R.string.cluster_naming__new_name_prompt")
        )
        assertTrue(
            "WorkoutClusterComponents must reference cluster_naming__new_route_name_label",
            clusterContent.contains("R.string.cluster_naming__new_route_name_label")
        )
    }
}
