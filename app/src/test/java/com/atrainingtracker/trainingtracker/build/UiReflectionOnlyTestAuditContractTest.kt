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
 * Architectural audit contract test banning new reflection-only UI tests under `ui/`
 * (`Class.forName("...Kt")` without `createComposeRule()`) while preserving legitimate
 * `build/` and non-`*Kt` architectural/excision reflection checks
 * (REQ-PRO-132, TST-PRO-132, docs/coding_standards.md §5.4, ATT-8198).
 */
class UiReflectionOnlyTestAuditContractTest {

    companion object {
        private const val LEGACY_BASELINE_CEILING = 189
        private val UI_PATH_REGEX = Regex("(?:^|/)ui/")
        private val KT_REFLECTION_REGEX = Regex("""Class\.forName\s*\(\s*"[^"]*Kt"\s*\)""")
    }

    private fun findRepoRootFile(relativePath: String, appRelativePath: String? = null): File {
        val candidates = buildList {
            add(File(relativePath))
            if (appRelativePath != null) add(File(appRelativePath))
            add(File("../$relativePath"))
            if (appRelativePath != null) add(File("../$appRelativePath"))
        }
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

    private fun loadBaselineLegacyReflectionTests(): Set<String> {
        val file = findRepoRootFile("app/ui-reflection-baseline.txt", "ui-reflection-baseline.txt")
        return file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }

    internal fun isReflectionOnlyUiTest(relativePath: String, content: String): Boolean {
        if (!UI_PATH_REGEX.containsMatchIn(relativePath)) return false
        return KT_REFLECTION_REGEX.containsMatchIn(content) && !content.contains("createComposeRule")
    }

    private fun scanReflectionOnlyUiTests(testDir: File): Set<String> {
        val detected = mutableSetOf<String>()
        val thisClassName = "UiReflectionOnlyTestAuditContractTest.kt"

        testDir.walkTopDown().forEach { file ->
            if ((file.name.endsWith(".kt") || file.name.endsWith(".java")) && file.name != thisClassName) {
                val relativePath = file.relativeTo(testDir).invariantSeparatorsPath
                if (UI_PATH_REGEX.containsMatchIn(relativePath)) {
                    val content = file.readText()
                    if (isReflectionOnlyUiTest(relativePath, content)) {
                        detected.add(relativePath)
                    }
                }
            }
        }
        return detected
    }

    /**
     * TST-PRO-132.1: Ensures zero unallowlisted reflection-only UI tests exist under `ui/`.
     */
    @Test
    fun testUiReflectionAudit_noUnallowlistedReflectionOnlyUiTests() {
        val testDir = findTestDir()
        val detected = scanReflectionOnlyUiTests(testDir)
        val baseline = loadBaselineLegacyReflectionTests()

        val unallowlisted = detected - baseline
        assertTrue(
            "Found unallowlisted reflection-only UI tests using Class.forName(\"...Kt\") without createComposeRule() " +
                "(REQ-PRO-132.1, docs/coding_standards.md §5.4):\n  " + unallowlisted.joinToString("\n  "),
            unallowlisted.isEmpty()
        )
    }

    /**
     * TST-PRO-132.1: Ensures the legacy UI reflection baseline is monotonically shrinking and free of stale entries.
     */
    @Test
    fun testBaseline_containsOnlyExpectedLegacyCount() {
        val testDir = findTestDir()
        val detected = scanReflectionOnlyUiTests(testDir)
        val baseline = loadBaselineLegacyReflectionTests()

        val staleEntries = baseline - detected
        assertTrue(
            "UI reflection baseline contains stale entries that now execute Compose UI or no longer use *Kt reflection " +
                "(REQ-PRO-132.1):\n  " + staleEntries.joinToString("\n  "),
            staleEntries.isEmpty()
        )

        assertTrue(
            "Baseline legacy reflection-only UI test count must not exceed ratcheted ceiling of $LEGACY_BASELINE_CEILING: " +
                "current=${baseline.size}",
            baseline.size <= LEGACY_BASELINE_CEILING
        )
        assertEquals(
            "Baseline legacy reflection-only UI test count must match recorded count (REQ-PRO-132.1)",
            LEGACY_BASELINE_CEILING,
            baseline.size
        )
    }

    /**
     * TST-PRO-132.1 & TST-PRO-132.2: Verifies negative tripwire detection and protection of legitimate
     * `build/`, non-`*Kt`, and `createComposeRule()`-backed tests.
     */
    @Test
    fun testRatchetingTripwire_andLegitimateReflectionProtections() {
        val baseline = loadBaselineLegacyReflectionTests()

        val syntheticViolationPath = "com/atrainingtracker/trainingtracker/ui/fake/NewReflectionOnlyUiTest.kt"
        val syntheticViolationCode = """
            class NewReflectionOnlyUiTest {
                fun check() {
                    val clazz = Class.forName("com.atrainingtracker.trainingtracker.ui.fake.FakeScreenKt")
                    assert(clazz.declaredMethods.isNotEmpty())
                }
            }
        """.trimIndent()
        assertTrue(
            "Detector must flag reflection-only UI test under trainingtracker/ui/",
            isReflectionOnlyUiTest(syntheticViolationPath, syntheticViolationCode)
        )
        assertFalse(
            "Synthetic unallowlisted UI test must fail baseline check",
            syntheticViolationPath in baseline
        )

        val banalUiViolationPath = "com/atrainingtracker/banalservice/ui/fake/NewBanalUiReflectionTest.kt"
        assertTrue(
            "Detector must also flag reflection-only UI test under banalservice/ui/",
            isReflectionOnlyUiTest(banalUiViolationPath, syntheticViolationCode)
        )

        // Legitimate protections (REQ-PRO-132.2)
        val testDir = findTestDir()
        val protectedCases = listOf(
            "com/atrainingtracker/trainingtracker/build/AndroidViewModelReflectionContractTest.kt",
            "com/atrainingtracker/trainingtracker/ui/navigation/CoreDependencyAlignmentTest.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryDeleteTest.kt",
            "com/atrainingtracker/trainingtracker/ui/tracking/radar/RadarLightQuickSwitchSheetTest.kt",
            "com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorSourceDialogContractTest.kt"
        )
        for (relPath in protectedCases) {
            val file = File(testDir, relPath)
            assertTrue("Protected test file must exist on disk: $relPath", file.exists())
            assertFalse(
                "Legitimate test must NOT be flagged as reflection-only UI test: $relPath",
                isReflectionOnlyUiTest(relPath, file.readText())
            )
        }
    }

    /**
     * TST-PRO-132.3: Verifies docs/coding_standards.md §5.4 and Stage 4/5 skills mandate
     * behavioral Compose UI testing via createComposeRule().
     */
    @Test
    fun testBehavioralComposeMandate_codingStandardsAndSkillsAligned() {
        val codingStandards = findRepoRootFile("docs/coding_standards.md").readText()
        assertTrue(
            "docs/coding_standards.md §5.4 must mandate createComposeRule() behavioral UI verification",
            codingStandards.contains("createComposeRule()") && codingStandards.contains("UiReflectionOnlyTestAuditContractTest")
        )
        assertTrue(
            "docs/coding_standards.md §5.4 must preserve legitimate excision/decoupling reflection guidance",
            codingStandards.contains("Excision/decoupling")
        )

        val stage4 = findRepoRootFile(".agents/skills/stage4-implementation/SKILL.md").readText()
        assertTrue(
            "Stage 4 skill must mandate createComposeRule() behavioral Compose UI tests",
            stage4.contains("createComposeRule()")
        )

        val stage5 = findRepoRootFile(".agents/skills/stage5-verification/SKILL.md").readText()
        assertTrue(
            "Stage 5 skill must mandate createComposeRule() behavioral Compose UI verification",
            stage5.contains("createComposeRule()")
        )
    }
}
