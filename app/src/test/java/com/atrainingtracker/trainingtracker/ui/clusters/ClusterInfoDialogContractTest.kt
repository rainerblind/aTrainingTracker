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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * Architectural and 9-language localization contract tests for [ClusterInfoDialog]
 * verifying preserved line breaks and absence of XML numeric entities in the
 * 3D topological fingerprint description (ATT-2195 / REQ-UI-269 / TST-UI-228).
 */
class ClusterInfoDialogContractTest {

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

    @Test
    fun testClusterInfoFingerprintDesc_existsAcrossAll9Locales() {
        val resDir = findResDirectory()
        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")
            assertTrue("File $stringsFile must exist", stringsFile.exists())

            val rawContent = extractStringTagRawContent(stringsFile, "cluster_info_fingerprint_desc")
            assertNotNull(
                "String resource 'cluster_info_fingerprint_desc' must exist in $valuesDir/strings.xml",
                rawContent
            )
            assertFalse(
                "String resource 'cluster_info_fingerprint_desc' in $valuesDir must not be blank",
                rawContent!!.isBlank()
            )
        }
    }

    @Test
    fun testClusterInfoFingerprintDesc_containsNoXmlNumericEntitiesAcrossAll9Locales() {
        val resDir = findResDirectory()
        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")
            val rawContent = extractStringTagRawContent(stringsFile, "cluster_info_fingerprint_desc")
            assertNotNull(rawContent)

            assertFalse(
                "Locale '$locale' ($valuesDir): 'cluster_info_fingerprint_desc' must NOT use '&#10;' which causes AAPT2 whitespace collapsing (ATT-2195)",
                rawContent!!.contains("&#10;")
            )
        }
    }

    @Test
    fun testClusterInfoFingerprintDesc_containsLiteralNewlineEscapesForBulletSeparation() {
        val resDir = findResDirectory()
        for (locale in supportedLocales) {
            val valuesDir = if (locale.isEmpty()) "values" else "values-$locale"
            val stringsFile = File(resDir, "$valuesDir/strings.xml")
            val rawContent = extractStringTagRawContent(stringsFile, "cluster_info_fingerprint_desc")
            assertNotNull(rawContent)

            // Verify literal \n escape is present
            assertTrue(
                "Locale '$locale' ($valuesDir): 'cluster_info_fingerprint_desc' must contain literal '\\n' escapes to preserve line breaks",
                rawContent!!.contains("\\n")
            )

            // Verify at least 4 newlines exist (separating header and the 4 bullet points: Start, Apex, Distance, Altitude)
            val newlineCount = rawContent.split("\\n").size - 1
            assertTrue(
                "Locale '$locale' ($valuesDir): Expected at least 4 '\\n' bullet separators, but found $newlineCount in: $rawContent",
                newlineCount >= 4
            )
        }
    }

    @Test
    fun testClusterInfoDialog_referencesFingerprintDescString() {
        val candidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("ClusterInfoDialog.kt not found in candidate paths: $candidates")

        val content = file.readText()
        assertTrue(
            "ClusterInfoDialog.kt must reference R.string.cluster_info_fingerprint_desc",
            content.contains("R.string.cluster_info_fingerprint_desc")
        )
        assertTrue(
            "ClusterInfoDialog.kt must reference R.string.cluster_info_fingerprint_title",
            content.contains("R.string.cluster_info_fingerprint_title")
        )
    }

    @Test
    fun testClusterInfoDialog_hasDirectAdvancedSettingsDeepLinkAndStrictOrdering() {
        val candidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterInfoDialog.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("ClusterInfoDialog.kt not found in candidate paths: $candidates")

        val content = file.readText()

        // 1. Parameter contract
        assertTrue(
            "ClusterInfoDialog must require onNavigateToAdvancedSettings: () -> Unit parameter (REQ-SET-076, ATT-3492)",
            content.contains("onNavigateToAdvancedSettings: () -> Unit")
        )

        // 2. Button and icon
        assertTrue(
            "ClusterInfoDialog must reference R.string.cluster_info_open_advanced_tuning (REQ-SET-076, ATT-3492)",
            content.contains("R.string.cluster_info_open_advanced_tuning")
        )
        assertTrue(
            "ClusterInfoDialog must render Tune icon for Advanced Settings (REQ-SET-076, ATT-3492)",
            content.contains("Icons.Default.Tune")
        )

        // 3. Strict ordering: onDismissRequest() must be called BEFORE onNavigateToAdvancedSettings()
        val dismissIndex = content.indexOf("onDismissRequest()")
        val navigateIndex = content.indexOf("onNavigateToAdvancedSettings()")
        assertTrue("onDismissRequest() must be present in click action", dismissIndex != -1)
        assertTrue("onNavigateToAdvancedSettings() must be present in click action", navigateIndex != -1)
        assertTrue(
            "onDismissRequest() must precede onNavigateToAdvancedSettings() to ensure tear-down before navigation (REQ-SET-076)",
            dismissIndex < navigateIndex
        )

        // 4. Architectural purity: zero activity or context casts
        assertFalse(
            "ClusterInfoDialog must not cast context or activity directly (REQ-SET-076)",
            content.contains("MainActivityWithNavigation") || content.contains("as? Activity")
        )
    }

    @Test
    fun testWorkoutClusters_forwardsNavigationCallback() {
        val tabsCandidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersTabsScreen.kt")
        )
        val tabsFile = tabsCandidates.firstOrNull { it.exists() }
            ?: error("WorkoutClustersTabsScreen.kt not found in candidate paths: $tabsCandidates")
        val tabsContent = tabsFile.readText()

        assertTrue(
            "WorkoutClustersTabsScreen must accept onNavigateToAdvancedSettings parameter (REQ-SET-076)",
            tabsContent.contains("onNavigateToAdvancedSettings: () -> Unit")
        )
        assertTrue(
            "WorkoutClustersTabsScreen must forward onNavigateToAdvancedSettings to ClusterInfoDialog (REQ-SET-076)",
            tabsContent.contains("onNavigateToAdvancedSettings =")
        )

        val screenCandidates = listOf(
            File("src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt"),
            File("app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt"),
            File("../app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersScreen.kt")
        )
        val screenFile = screenCandidates.firstOrNull { it.exists() }
            ?: error("WorkoutClustersScreen.kt not found in candidate paths: $screenCandidates")
        val screenContent = screenFile.readText()

        assertTrue(
            "WorkoutClustersScreen must accept onNavigateToAdvancedSettings parameter (REQ-SET-076)",
            screenContent.contains("onNavigateToAdvancedSettings: () -> Unit")
        )
        assertTrue(
            "WorkoutClustersScreen must forward onNavigateToAdvancedSettings to WorkoutClustersTabsScreen (REQ-SET-076)",
            screenContent.contains("onNavigateToAdvancedSettings = onNavigateToAdvancedSettings")
        )
    }
}

