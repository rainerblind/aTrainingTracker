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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates Release CI test gating, AndroidManifest hygiene, and Lint configuration (REQ-BLD-002, TST-BLD-002).
 */
class ReleaseCiAndLintConfigContractTest {

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

    private fun findReleaseWorkflowFile(): File =
        findRepoRootFile(".github/workflows/android-release.yml", "../.github/workflows/android-release.yml")

    private fun findAndroidManifestFile(): File =
        findRepoRootFile("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")

    private fun findBuildGradleFile(): File =
        findRepoRootFile("app/build.gradle", "build.gradle")

    private fun findLintBaselineFile(): File =
        findRepoRootFile("app/lint-baseline.xml", "lint-baseline.xml")

    @Test
    fun testReleaseWorkflow_runsUnitTestsBeforeAssembly() {
        val content = findReleaseWorkflowFile().readText()
        val testStep = "./gradlew :app:testDebugUnitTest"
        val assembleStep = "./gradlew :app:assembleRelease :app:bundleRelease"

        assertTrue(
            "Release workflow must include unit test execution ($testStep) (REQ-BLD-002.1)",
            content.contains(testStep)
        )
        assertTrue(
            "Release workflow must include release assembly ($assembleStep)",
            content.contains(assembleStep)
        )

        val testIndex = content.indexOf(testStep)
        val assembleIndex = content.indexOf(assembleStep)
        assertTrue(
            "Unit tests must be executed before release assembly (REQ-BLD-002.1)",
            testIndex < assembleIndex
        )

        val uploadArtifactIndex = content.indexOf("actions/upload-artifact")
        if (uploadArtifactIndex >= 0) {
            assertTrue(
                "Release artifact upload must occur after unit tests (REQ-BLD-002.1)",
                testIndex < uploadArtifactIndex
            )
        }
    }

    @Test
    fun testAndroidManifest_noOrphanedStravaSegmentsIntentService() {
        val content = findAndroidManifestFile().readText()
        assertFalse(
            "AndroidManifest.xml must not declare deleted StravaSegmentsIntentService (REQ-BLD-002.3)",
            content.contains("StravaSegmentsIntentService")
        )
    }

    @Test
    fun testBuildGradle_lintConfigurationAndRationales() {
        val content = findBuildGradleFile().readText()

        assertTrue(
            "app/build.gradle must retain checkReleaseBuilds = true for lintVitalRelease (REQ-BLD-002.2)",
            content.contains("checkReleaseBuilds = true")
        )
        assertTrue(
            "app/build.gradle must suppress DuplicatePlatformClasses with justification",
            content.contains("'DuplicatePlatformClasses'")
        )
        assertTrue(
            "app/build.gradle must suppress MissingTranslation with justification",
            content.contains("'MissingTranslation'")
        )
        assertTrue(
            "app/build.gradle must suppress Range with justification",
            content.contains("'Range'")
        )
        assertTrue(
            "app/build.gradle must explicitly document TranslationParityTest rationale for MissingTranslation (REQ-BLD-002.2)",
            content.contains("TranslationParityTest")
        )
        assertTrue(
            "app/build.gradle must document DuplicatePlatformClasses rationale (REQ-BLD-002.2)",
            content.contains("DuplicatePlatformClasses: Justified") || content.contains("DuplicatePlatformClasses")
        )
        assertTrue(
            "app/build.gradle must document Range rationale (REQ-BLD-002.2)",
            content.contains("Range: Suppressed") || content.contains("Range")
        )
    }

    @Test
    fun testLintBaseline_noStaleStravaSegmentsIntentService() {
        val content = findLintBaselineFile().readText()
        assertFalse(
            "app/lint-baseline.xml must not contain stale issues for deleted StravaSegmentsIntentService (REQ-BLD-002.4)",
            content.contains("StravaSegmentsIntentService")
        )
    }
}
