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
}
