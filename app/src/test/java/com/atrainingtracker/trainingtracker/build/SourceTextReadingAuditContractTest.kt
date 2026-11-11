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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural audit test enforcing prohibition of source-text reading tests
 * while exempting legitimate system architectural governance contract tests
 * (REQ-PRO-049, TST-PRO-049, docs/coding_standards.md §5.4, ATT-6186, ATT-6731).
 */
class SourceTextReadingAuditContractTest {

    companion object {
        /**
         * Legitimate system policy and architectural governance contract tests
         * preserved by docs/coding_standards.md §5.4 (REQ-PRO-049.2).
         */
        val LEGITIMATE_GOVERNANCE_GUARDS: Set<String> = setOf(
            "com/atrainingtracker/architecture/TryWithResourcesCursorAuditTest.kt",
            "com/atrainingtracker/architecture/SafeEnumDeserializationAuditTest.kt",
            "com/atrainingtracker/architecture/LifecycleServiceReceiverSafetyAuditTest.kt",
            "com/atrainingtracker/trainingtracker/build/ManifestAndBuildConfigContractTest.kt",
            "com/atrainingtracker/trainingtracker/build/ReleaseCiAndLintConfigContractTest.kt",
            "com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt",
            "com/atrainingtracker/trainingtracker/build/ExceptionHandlingHygieneTest.kt",
            "com/atrainingtracker/trainingtracker/security/BackupRulesXmlValidationTest.kt",
            "com/atrainingtracker/trainingtracker/security/FileProviderHardeningContractTest.kt",
            "com/atrainingtracker/trainingtracker/security/LogcatPrivacySecurityTest.kt",
            "com/atrainingtracker/trainingtracker/tracker/MergedManifestFgsAuditTest.kt",
            "com/atrainingtracker/trainingtracker/resources/NotificationIconContractTest.kt",
            "com/atrainingtracker/trainingtracker/routes/TurnDirectionIconContractTest.kt",
            "com/atrainingtracker/trainingtracker/build/ArchitectureConformanceContractTest.kt",
            "com/atrainingtracker/testing/LocalizationTestCache.kt",
            "com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt",
            "com/atrainingtracker/trainingtracker/ui/theme/ThemeColorCentralizationAuditTest.kt",
            "com/atrainingtracker/trainingtracker/ui/RepositoryEngineCleanlinessContractTest.kt",
            "com/atrainingtracker/trainingtracker/ui/RepositoryUiCleanlinessContractTest.kt",
            "com/atrainingtracker/trainingtracker/ui/GlobalDeleteContextMenuAuditTest.kt",
            "com/atrainingtracker/trainingtracker/ui/EditIconConsistencyTest.kt",
            "com/atrainingtracker/trainingtracker/build/AspiceSweAuditContractTest.kt"
        )
    }

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

    private fun findTestDir(): File {
        val candidates = listOf(
            File("app/src/test/java"),
            File("src/test/java"),
            File("../app/src/test/java"),
            File("../src/test/java")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not locate test directory in: ${File(".").absolutePath}")
    }

    private fun loadBaselineLegacyReaders(): Set<String> {
        val file = findRepoRootFile("app/source-reading-baseline.txt", "source-reading-baseline.txt")
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    private fun scanTestSourceFiles(testDir: File): Set<String> {
        val readers = mutableSetOf<String>()
        val thisClassName = "SourceTextReadingAuditContractTest.kt"

        testDir.walkTopDown().forEach { file ->
            if ((file.name.endsWith(".kt") || file.name.endsWith(".java")) && file.name != thisClassName) {
                val content = file.readText()
                val hasReadInvocation = content.contains("readText") ||
                    content.contains("readLines") ||
                    content.contains("readString") ||
                    content.contains("useLines") ||
                    content.contains("bufferedReader")
                if (hasReadInvocation && content.contains("src/main")) {
                    val relativePath = file.relativeTo(testDir).invariantSeparatorsPath
                    readers.add(relativePath)
                }
            }
        }
        return readers
    }

    @Test
    fun testSourceReadingAudit_noUnallowlistedSourceReaders() {
        val testDir = findTestDir()
        val detectedReaders = scanTestSourceFiles(testDir)
        val baseline = loadBaselineLegacyReaders()

        val allowedSet = LEGITIMATE_GOVERNANCE_GUARDS + baseline
        val unallowedReaders = detectedReaders - allowedSet

        assertTrue(
            "Found unallow-listed test files reading production source code (REQ-PRO-049.1, docs/coding_standards.md §5.4): " +
                unallowedReaders.joinToString("\n  "),
            unallowedReaders.isEmpty()
        )
    }

    @Test
    fun testGovernanceGuards_allExplicitlyAllowed() {
        assertEquals(
            "Expected exactly 22 approved architectural governance guards (REQ-PRO-049.2, REQ-PRO-050.4)",
            22,
            LEGITIMATE_GOVERNANCE_GUARDS.size
        )
        val testDir = findTestDir()
        LEGITIMATE_GOVERNANCE_GUARDS.forEach { guard ->
            assertTrue(
                "Governance guard $guard must end with Test.kt or Cache.kt",
                guard.endsWith("Test.kt") || guard.endsWith("Cache.kt")
            )
            val guardFile = File(testDir, guard)
            assertTrue(
                "Governance guard $guard must exist on disk at ${guardFile.path}",
                guardFile.exists()
            )
        }
    }

    @Test
    fun testBaseline_containsOnlyExpectedLegacyCount() {
        val testDir = findTestDir()
        val detectedReaders = scanTestSourceFiles(testDir)
        val baseline = loadBaselineLegacyReaders()

        val staleBaselineEntries = baseline - detectedReaders
        assertTrue(
            "Baseline contains stale entries that no longer read source files (REQ-PRO-049.3): " +
                staleBaselineEntries.joinToString("\n  "),
            staleBaselineEntries.isEmpty()
        )

        assertTrue(
            "Baseline legacy source-reading tests debt must not exceed ratcheted ceiling of 251 (REQ-PRO-049.3): current=${baseline.size}",
            baseline.size <= 251
        )
        assertEquals(
            "Baseline legacy source-reading tests debt must match recorded count (REQ-PRO-049.3)",
            251,
            baseline.size
        )
    }

    @Test
    fun testRatchetingTripwire_detectsUnallowlistedReader() {
        val mockDetected = setOf("com/atrainingtracker/trainingtracker/ui/fake/NewSourceReadingTest.kt")
        val baseline = loadBaselineLegacyReaders()
        val allowed = LEGITIMATE_GOVERNANCE_GUARDS + baseline

        val violations = mockDetected - allowed
        assertFalse(
            "Ratcheting tripwire must detect synthetic unallow-listed reader (REQ-PRO-049.3)",
            violations.isEmpty()
        )
        assertEquals("NewSourceReadingTest.kt", File(violations.first()).name)
    }
}
