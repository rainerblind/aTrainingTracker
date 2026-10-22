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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Validates dead code elimination, volatile visibility, lock-screen API modernization,
 * and Compose preview private visibility scoping (REQ-ARC-003, TST-ARC-003, ATT-4031).
 */
class DeadCodeEliminationContractTest {

    private fun findFile(primaryPath: String, fallbackPath: String): File {
        val candidates = listOf(
            File(primaryPath),
            File(fallbackPath),
            File("../$primaryPath"),
            File("../$fallbackPath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $primaryPath in working directory: ${File(".").absolutePath}")
    }

    private fun findDir(primaryPath: String, fallbackPath: String): File {
        val candidates = listOf(
            File(primaryPath),
            File(fallbackPath),
            File("../$primaryPath"),
            File("../$fallbackPath")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("Could not locate directory $primaryPath in working directory: ${File(".").absolutePath}")
    }

    /**
     * TST-ARC-003.1: Dead Legacy Class Elimination Verification.
     * Asserts that dead Java dialogs and views are physically excised from disk
     * and cannot be loaded reflectively by the ClassLoader.
     */
    @Test
    fun testDeadLegacyClassesAreEliminated() {
        val deadClasses = listOf(
            "com.atrainingtracker.banalservice.dialogs.SelectSportTypeDialog",
            "com.atrainingtracker.trainingtracker.views.MultiSelectionSpinner",
            "com.atrainingtracker.trainingtracker.views.DeviceIdAndNameArrayAdapter",
            "com.atrainingtracker.banalservice.dialogs.InstallANTShitDialog"
        )

        for (className in deadClasses) {
            try {
                Class.forName(className)
                fail("Dead class $className must not exist in classpath")
            } catch (_: ClassNotFoundException) {
                // Expected: class was removed
            }
        }

        val deadFiles = listOf(
            "app/src/main/java/com/atrainingtracker/banalservice/dialogs/SelectSportTypeDialog.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/views/MultiSelectionSpinner.java",
            "app/src/main/java/com/atrainingtracker/trainingtracker/views/DeviceIdAndNameArrayAdapter.java",
            "app/src/main/java/com/atrainingtracker/banalservice/dialogs/InstallANTShitDialog.java"
        )

        for (path in deadFiles) {
            val fileCandidate1 = File(path)
            val fileCandidate2 = File(path.removePrefix("app/"))
            val fileCandidate3 = File("../$path")
            assertFalse(
                "Dead source file must not exist on disk: $path",
                fileCandidate1.exists() || fileCandidate2.exists() || fileCandidate3.exists()
            )
        }

        // Verify lint baseline has been cleaned
        val baselineFile = findFile("app/lint-baseline.xml", "lint-baseline.xml")
        val baselineContent = baselineFile.readText()
        assertFalse(
            "lint-baseline.xml must not retain references to MultiSelectionSpinner",
            baselineContent.contains("MultiSelectionSpinner")
        )
    }

    /**
     * TST-ARC-003.2: TrainingApplication Dead Method Excision & Volatile State Verification.
     * Asserts that dead methods todo(), setIsSegmentListUpdating(), isSegmentListUpdating()
     * and field mSegmentListUpdating are removed, and cResumeFromCrash is volatile.
     */
    @Test
    fun testTrainingApplicationDeadMethodsAndVolatileState() {
        val appClass = Class.forName("com.atrainingtracker.trainingtracker.TrainingApplication")
        val declaredMethods = appClass.declaredMethods.map { it.name }.toSet()

        assertFalse(
            "TrainingApplication must not declare dead method todo()",
            declaredMethods.contains("todo")
        )
        assertFalse(
            "TrainingApplication must not declare dead method setIsSegmentListUpdating()",
            declaredMethods.contains("setIsSegmentListUpdating")
        )
        assertFalse(
            "TrainingApplication must not declare dead method isSegmentListUpdating()",
            declaredMethods.contains("isSegmentListUpdating")
        )

        val declaredFields = appClass.declaredFields.map { it.name }.toSet()
        assertFalse(
            "TrainingApplication must not declare dead field mSegmentListUpdating",
            declaredFields.contains("mSegmentListUpdating")
        )

        val resumeField = appClass.getDeclaredField("cResumeFromCrash")
        assertNotNull("cResumeFromCrash field must exist", resumeField)
        assertTrue(
            "cResumeFromCrash must be declared volatile for cross-thread visibility",
            Modifier.isVolatile(resumeField.modifiers)
        )
    }

    /**
     * TST-ARC-003.3: MainActivityWithNavigation Dead Method Excision & Lock Screen API Verification.
     * Asserts that checkBatteryOptimizations and showInstallANTShitDialog are removed,
     * and lock-screen flags are modernized via setShowWhenLocked for API 27+.
     */
    @Test
    fun testMainActivityWithNavigationDeadMethodsAndLockScreenApi() {
        val activityClass = Class.forName("com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation")
        val declaredMethods = activityClass.declaredMethods.map { it.name }.toSet()

        assertFalse(
            "MainActivityWithNavigation must not declare dead method checkBatteryOptimizations",
            declaredMethods.contains("checkBatteryOptimizations")
        )
        assertFalse(
            "MainActivityWithNavigation must not declare dead method showInstallANTShitDialog",
            declaredMethods.contains("showInstallANTShitDialog")
        )

        val activityFile = findFile(
            "app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt",
            "src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt"
        )
        val content = activityFile.readText()

        assertFalse(
            "MainActivityWithNavigation must not import InstallANTShitDialog",
            content.contains("InstallANTShitDialog")
        )
        assertTrue(
            "MainActivityWithNavigation must branch on Build.VERSION_CODES.O_MR1 for lock screen handling",
            content.contains("Build.VERSION_CODES.O_MR1")
        )
        assertTrue(
            "MainActivityWithNavigation must invoke setShowWhenLocked(true)",
            content.contains("setShowWhenLocked(true)")
        )
        assertTrue(
            "MainActivityWithNavigation must invoke setShowWhenLocked(false)",
            content.contains("setShowWhenLocked(false)")
        )
    }

    /**
     * TST-ARC-003.4: AntStartupCheckContractTest Invariant Audit.
     * Asserts that AntStartupCheckContractTest anchors its onCreate inspection
     * to observeNavigationEvents rather than excised checkBatteryOptimizations.
     */
    @Test
    fun testAntStartupCheckContractTestInvariantAudit() {
        val testFile = findFile(
            "app/src/test/java/com/atrainingtracker/banalservice/ui/devices/ant/AntStartupCheckContractTest.kt",
            "src/test/java/com/atrainingtracker/banalservice/ui/devices/ant/AntStartupCheckContractTest.kt"
        )
        val content = testFile.readText()

        assertTrue(
            "AntStartupCheckContractTest must anchor to observeNavigationEvents",
            content.contains("observeNavigationEvents")
        )
        assertFalse(
            "AntStartupCheckContractTest must not reference excised checkBatteryOptimizations",
            content.contains("checkBatteryOptimizations")
        )
    }

    /**
     * TST-ARC-003.5: Top-Level Compose Preview Private Visibility Audit.
     * Asserts that all Compose @Preview functions across app/src/main/java
     * are declared with private visibility.
     */
    @Test
    fun testComposePreviewsVisibilityAudit() {
        val mainSrcDir = findDir("app/src/main/java", "src/main/java")
        val nonPrivatePreviews = mutableListOf<String>()

        val previewRegex = Regex("""(@Preview[\s\S]*?)(?:(private|internal|public)\s+)?fun\s+([A-Za-z0-9_]+)""")

        mainSrcDir.walkTopDown()
            .filter { it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                previewRegex.findAll(text).forEach { match ->
                    val annotations = match.groupValues[1]
                    val visibility = match.groupValues[2]
                    val fnName = match.groupValues[3]
                    if (!annotations.contains("class ") && !annotations.contains("fun ")) {
                        if (visibility != "private") {
                            nonPrivatePreviews.add("${file.name}: $fnName (declared: ${if (visibility.isEmpty()) "public/default" else visibility})")
                        }
                    }
                }
            }

        assertTrue(
            "All Compose @Preview functions must have private visibility. Violations: $nonPrivatePreviews",
            nonPrivatePreviews.isEmpty()
        )
    }
}
