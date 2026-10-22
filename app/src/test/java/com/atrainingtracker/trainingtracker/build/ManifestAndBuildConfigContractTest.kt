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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates AndroidManifest component security, GPX intent filter scoping, and build configuration invariants
 * (REQ-BLD-003, TST-BLD-003).
 */
class ManifestAndBuildConfigContractTest {

    private fun findRepoRootFile(relativePath: String, appRelativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File(appRelativePath),
            File("../$relativePath"),
            File("../$appRelativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    private fun findAndroidManifestFile(): File =
        findRepoRootFile("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")

    private fun findGradlePropertiesFile(): File =
        findRepoRootFile("gradle.properties", "../gradle.properties")

    private fun parseManifestDocument(): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(findAndroidManifestFile())
    }

    @Test
    fun testConfigPebbleViewsActivity_notExported() {
        val doc = parseManifestDocument()
        val activities: NodeList = doc.getElementsByTagName("activity")
        var pebbleActivity: Element? = null

        for (i in 0 until activities.length) {
            val element = activities.item(i) as Element
            val name = element.getAttribute("android:name")
            if (name.contains("ConfigPebbleViewsActivity")) {
                pebbleActivity = element
                break
            }
        }

        assertNotNull("ConfigPebbleViewsActivity must be declared in AndroidManifest.xml (REQ-BLD-003.1)", pebbleActivity)
        val exported = pebbleActivity!!.getAttribute("android:exported")
        assertEquals(
            "ConfigPebbleViewsActivity must be android:exported=\"false\" to eliminate external attack surface (REQ-BLD-003.1)",
            "false",
            exported
        )
    }

    @Test
    fun testGpxImportActivity_intentFiltersHardened() {
        val doc = parseManifestDocument()
        val activities = doc.getElementsByTagName("activity")
        var gpxActivity: Element? = null

        for (i in 0 until activities.length) {
            val element = activities.item(i) as Element
            val name = element.getAttribute("android:name")
            if (name.contains("GpxImportActivity")) {
                gpxActivity = element
                break
            }
        }

        assertNotNull("GpxImportActivity must be declared in AndroidManifest.xml (REQ-BLD-003.2)", gpxActivity)

        val intentFilters = gpxActivity!!.getElementsByTagName("intent-filter")
        assertTrue("GpxImportActivity must declare intent filters", intentFilters.length > 0)

        for (i in 0 until intentFilters.length) {
            val filter = intentFilters.item(i) as Element
            val dataElements = filter.getElementsByTagName("data")

            var hasHostWildcard = false
            var hasFileScheme = false
            var hasOctetStream = false
            var hasPathPatternOrSuffix = false

            for (j in 0 until dataElements.length) {
                val data = dataElements.item(j) as Element
                val host = data.getAttribute("android:host")
                val scheme = data.getAttribute("android:scheme")
                val mimeType = data.getAttribute("android:mimeType")
                val pathPattern = data.getAttribute("android:pathPattern")
                val pathSuffix = data.getAttribute("android:pathSuffix")

                if (host == "*") hasHostWildcard = true
                if (scheme == "file") hasFileScheme = true
                if (mimeType == "application/octet-stream") hasOctetStream = true
                if (pathPattern.isNotEmpty() || pathSuffix.isNotEmpty()) hasPathPatternOrSuffix = true
            }

            assertFalse(
                "GpxImportActivity intent-filter must not use invalid/redundant android:host=\"*\" (REQ-BLD-003.2)",
                hasHostWildcard
            )
            assertFalse(
                "GpxImportActivity intent-filter must not use obsolete android:scheme=\"file\" (REQ-BLD-003.2)",
                hasFileScheme
            )
            if (hasOctetStream) {
                assertTrue(
                    "application/octet-stream in GpxImportActivity must be scoped with .gpx path pattern or suffix (REQ-BLD-003.2)",
                    hasPathPatternOrSuffix
                )
            }
        }
    }

    @Test
    fun testManifest_permissionAndMemoryInvariantsPreserved() {
        val doc = parseManifestDocument()

        // 1. Verify ACCESS_BACKGROUND_LOCATION is actively declared (REQ-TRK-015 / REQ-BLD-003.3)
        val permissions = doc.getElementsByTagName("uses-permission")
        var hasBackgroundLocation = false
        for (i in 0 until permissions.length) {
            val element = permissions.item(i) as Element
            if (element.getAttribute("android:name") == "android.permission.ACCESS_BACKGROUND_LOCATION") {
                hasBackgroundLocation = true
                break
            }
        }
        assertTrue(
            "AndroidManifest.xml must declare android.permission.ACCESS_BACKGROUND_LOCATION for Android 14+ FGS recovery (REQ-TRK-015, REQ-BLD-003.3)",
            hasBackgroundLocation
        )

        // 2. Verify largeHeap="true" is preserved (REQ-PER-012 / REQ-BLD-003.4)
        val applications = doc.getElementsByTagName("application")
        assertTrue("AndroidManifest.xml must declare <application>", applications.length > 0)
        val app = applications.item(0) as Element
        assertEquals(
            "Application must retain android:largeHeap=\"true\" for period heatmap memory budget (REQ-PER-012, REQ-BLD-003.4)",
            "true",
            app.getAttribute("android:largeHeap")
        )
    }

    @Test
    fun testManifest_backgroundLocationRationaleCommentDocumented() {
        val content = findAndroidManifestFile().readText()

        assertFalse(
            "AndroidManifest.xml must NOT contain misleading stale comment claiming background location is not necessary (REQ-BLD-003.3)",
            content.contains("Not necessary since we track within a background service")
        )
        assertTrue(
            "AndroidManifest.xml comment must document Android 14+ FGS START_STICKY recovery contract (REQ-TRK-015, REQ-BLD-003.3)",
            content.contains("START_STICKY") && content.contains("REQ-TRK-015")
        )
    }

    @Test
    fun testGradleProperties_agp9OptOutFlagsDocumented() {
        val content = findGradlePropertiesFile().readText()

        assertTrue(
            "gradle.properties must document AGP 9 deprecation flags retention (REQ-BLD-003.4)",
            content.contains("REQ-BLD-003.4")
        )
        assertTrue(
            "gradle.properties must retain android.newDsl=false for Secrets Gradle Plugin compatibility",
            content.contains("android.newDsl=false")
        )
        assertTrue(
            "gradle.properties must retain android.builtInKotlin=false",
            content.contains("android.builtInKotlin=false")
        )
    }
}
