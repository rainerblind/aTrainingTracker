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

package com.atrainingtracker.trainingtracker.resources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Architectural contract test suite enforcing REQ-UI-452 / TST-UI-452:
 * Dedicated Status Bar Vector Icon, Notification Builder Migration & Monolithic Density Packaging.
 */
class NotificationIconContractTest {

    private fun resolveProjectDir(): File {
        val currentDir = File(System.getProperty("user.dir") ?: ".")
        return when {
            File(currentDir, "app/src/main/res").exists() -> currentDir
            File(currentDir, "src/main/res").exists() -> currentDir.parentFile ?: currentDir
            else -> File(currentDir, "..")
        }
    }

    private fun resolveResDir(): File {
        val proj = resolveProjectDir()
        return File(proj, "app/src/main/res").takeIf { it.exists() }
            ?: File(proj, "src/main/res")
    }

    /**
     * TST-UI-452.1: Verify vector notification asset exists, has 24x24 viewport, and has no density siblings.
     */
    @Test
    fun testVectorNotificationAssetAndSilhouette() {
        val resDir = resolveResDir()
        val vectorFile = File(resDir, "drawable/ic_stat_notify_logo.xml")
        assertTrue("ic_stat_notify_logo.xml must exist in res/drawable/: ${vectorFile.absolutePath}", vectorFile.exists())
        assertTrue("ic_stat_notify_logo.xml must not be empty", vectorFile.length() > 0)

        // Parse XML DOM
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(vectorFile)
        val root = doc.documentElement

        assertEquals("Root element must be vector", "vector", root.tagName)
        assertEquals("viewportWidth must be 24", "24", root.getAttribute("android:viewportWidth"))
        assertEquals("viewportHeight must be 24", "24", root.getAttribute("android:viewportHeight"))
        assertEquals("width must be 24dp", "24dp", root.getAttribute("android:width"))
        assertEquals("height must be 24dp", "24dp", root.getAttribute("android:height"))

        // Verify no density-specific or nodpi siblings exist
        val densityDirs = resDir.listFiles()?.filter {
            it.isDirectory && it.name.startsWith("drawable-")
        } ?: emptyList()

        for (dir in densityDirs) {
            val sibling = File(dir, "ic_stat_notify_logo.xml")
            val siblingPng = File(dir, "ic_stat_notify_logo.png")
            assertFalse(
                "ic_stat_notify_logo must not have density/nodpi sibling in ${dir.name}",
                sibling.exists() || siblingPng.exists()
            )
        }
    }

    /**
     * TST-UI-452.2: Verify all 6 notification builder call sites across 5 classes use R.drawable.ic_stat_notify_logo.
     */
    @Test
    fun testNotificationBuilderSmallIconCallSites() {
        val proj = resolveProjectDir()
        val targetSourceFiles = listOf(
            "app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutDeletionNotificationManager.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportNotificationManager.kt",
            "app/src/main/java/com/atrainingtracker/trainingtracker/notifications/SensorBatteryNotificationManager.kt"
        )

        var totalMigratedCallSites = 0
        for (relPath in targetSourceFiles) {
            val file = File(proj, relPath)
            assertTrue("Target source file must exist: ${file.absolutePath}", file.exists())
            val content = file.readText()

            assertFalse(
                "Source file ${file.name} must NOT invoke .setSmallIcon(R.drawable.logo) (REQ-UI-452.2)",
                content.contains(".setSmallIcon(R.drawable.logo)")
            )

            val matches = Regex("""\.setSmallIcon\(\s*R\.drawable\.ic_stat_notify_logo\s*\)""").findAll(content).count()
            totalMigratedCallSites += matches
        }

        assertEquals(
            "Exactly 6 notification builder call sites across the 5 notification managers must invoke .setSmallIcon(R.drawable.ic_stat_notify_logo)",
            6,
            totalMigratedCallSites
        )
    }

    /**
     * TST-UI-452.3: Verify app/build.gradle configures bundle { density { enableSplit = false } }.
     */
    @Test
    fun testAppBundleDensitySplitConfiguration() {
        val proj = resolveProjectDir()
        val buildGradle = File(proj, "app/build.gradle")
        assertTrue("app/build.gradle must exist: ${buildGradle.absolutePath}", buildGradle.exists())
        val content = buildGradle.readText()

        assertTrue(
            "app/build.gradle must configure bundle { density { enableSplit = false } } (REQ-UI-452.3)",
            content.contains("enableSplit = false") && content.contains("density")
        )
    }
}
