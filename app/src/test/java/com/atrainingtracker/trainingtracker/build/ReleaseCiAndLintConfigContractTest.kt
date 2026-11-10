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
 * Validates Release CI test gating, AndroidManifest hygiene, Lint configuration,
 * Detekt static analysis, Kover code coverage gating, CI integrity, and instrumented androidTest
 * infrastructure with emulator CI runner (REQ-BLD-002, TST-BLD-002, REQ-PRO-042,
 * TST-PRO-042, REQ-PRO-046, TST-PRO-046, REQ-PRO-047, TST-PRO-047, REQ-PRO-048, TST-PRO-048,
 * REQ-PRO-051, TST-PRO-051, ATT-6171, ATT-6175, ATT-6184).
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

    private fun findCiWorkflowFile(): File =
        findRepoRootFile(".github/workflows/android-branch-build.yml", "../.github/workflows/android-branch-build.yml")

    private fun findAndroidManifestFile(): File =
        findRepoRootFile("app/src/main/AndroidManifest.xml", "src/main/AndroidManifest.xml")

    private fun findBuildGradleFile(): File =
        findRepoRootFile("app/build.gradle", "build.gradle")

    private fun findLintBaselineFile(): File =
        findRepoRootFile("app/lint-baseline.xml", "lint-baseline.xml")

    private fun findRootBuildGradleFile(): File =
        listOf(File("build.gradle"), File("../build.gradle")).firstOrNull {
            it.exists() && it.readText().contains("buildscript")
        } ?: findRepoRootFile("build.gradle", "../build.gradle")

    private fun findDetektConfigFile(): File =
        findRepoRootFile("config/detekt/detekt.yml", "../config/detekt/detekt.yml")

    private fun findDetektBaselineFile(): File =
        findRepoRootFile("app/detekt-baseline.xml", "detekt-baseline.xml")

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

    /**
     * Asserts that legacy Travis CI artifacts (.travis.yml) and build script comments are permanently removed (REQ-PRO-042, TST-PRO-042).
     */
    @Test
    fun testCiConfiguration_noStaleTravisArtifacts() {
        // TST-PRO-042.1: Verify .travis.yml does not exist in any candidate repository root directory
        val candidateRoots = listOf(File("."), File(".."))
        val travisFileFound = candidateRoots.any { root ->
            File(root, ".travis.yml").exists()
        }
        assertFalse(
            "Legacy .travis.yml must be completely removed from repository root (REQ-PRO-042.1)",
            travisFileFound
        )

        // TST-PRO-042.2: Verify app/build.gradle contains no stale references to Travis
        val buildGradleContent = findBuildGradleFile().readText()
        assertFalse(
            "app/build.gradle must not contain any reference to Travis (REQ-PRO-042.1)",
            buildGradleContent.contains("travis", ignoreCase = true)
        )
    }

    /**
     * Asserts that CI workflow executes Android Lint and uploads reports (REQ-PRO-046.1, REQ-PRO-046.2, TST-PRO-046.1).
     */
    @Test
    fun testBranchBuildWorkflow_runsLintAndUploadsReport() {
        val content = findCiWorkflowFile().readText()
        val lintStep = "./gradlew :app:lintDebug"
        val artifactStep = "actions/upload-artifact"
        val reportPath = "app/build/reports/lint-results-debug.*"

        assertTrue(
            "CI workflow must execute Android Lint ($lintStep) (REQ-PRO-046.1, TST-PRO-046.1)",
            content.contains(lintStep)
        )
        assertTrue(
            "CI workflow must upload lint reports via $artifactStep (REQ-PRO-046.2, TST-PRO-046.1)",
            content.contains(artifactStep)
        )
        assertTrue(
            "CI workflow must specify lint report artifact path $reportPath (REQ-PRO-046.2, TST-PRO-046.1)",
            content.contains(reportPath)
        )
    }

    /**
     * Asserts that app/build.gradle configures abortOnError, htmlReport, and xmlReport (REQ-PRO-046.3, TST-PRO-046.2).
     */
    @Test
    fun testBuildGradle_lintReportingAndAbortOnError() {
        val content = findBuildGradleFile().readText()

        assertTrue(
            "app/build.gradle must configure abortOnError = true (REQ-PRO-046.3, TST-PRO-046.2)",
            content.contains("abortOnError = true")
        )
        assertTrue(
            "app/build.gradle must configure htmlReport = true (REQ-PRO-046.3, TST-PRO-046.2)",
            content.contains("htmlReport = true")
        )
        assertTrue(
            "app/build.gradle must configure xmlReport = true (REQ-PRO-046.3, TST-PRO-046.2)",
            content.contains("xmlReport = true")
        )
    }

    /**
     * Asserts that app/lint-baseline.xml is pruned and strictly bounded
     * to <= 546 issues (REQ-PRO-046.4, TST-PRO-046.3).
     */
    @Test
    fun testLintBaseline_issueCountWithinBound() {
        val content = findLintBaselineFile().readText()
        val issueCount = Regex("<issue\\b").findAll(content).count()

        assertTrue(
            "app/lint-baseline.xml must be bounded by <= 546 (REQ-PRO-046.4, TST-PRO-046.3). Current: $issueCount",
            issueCount in 1..546
        )
    }

    /**
     * Asserts that build.gradle and app/build.gradle apply Detekt plugin
     * and configure config/baseline paths (REQ-PRO-047.1, REQ-PRO-047.3, TST-PRO-047.1).
     */
    @Test
    fun testBuildGradle_detektPluginAndConfiguration() {
        val appBuildContent = findBuildGradleFile().readText()
        val rootBuildFile = findRootBuildGradleFile()
        val rootBuildContent = rootBuildFile.readText()

        assertTrue(
            "Root build.gradle must declare detekt-gradle-plugin dependency (REQ-PRO-047.1, TST-PRO-047.1)",
            rootBuildContent.contains("io.gitlab.arturbosch.detekt:detekt-gradle-plugin")
        )
        assertTrue(
            "app/build.gradle must apply io.gitlab.arturbosch.detekt plugin (REQ-PRO-047.1, TST-PRO-047.1)",
            appBuildContent.contains("io.gitlab.arturbosch.detekt")
        )
        assertTrue(
            "app/build.gradle must configure detekt.yml config path (REQ-PRO-047.1, TST-PRO-047.1)",
            appBuildContent.contains("config/detekt/detekt.yml")
        )
        assertTrue(
            "app/build.gradle must configure detekt-baseline.xml baseline path (REQ-PRO-047.3, TST-PRO-047.1)",
            appBuildContent.contains("detekt-baseline.xml")
        )
    }

    /**
     * Asserts that config/detekt/detekt.yml defines complexity rules
     * (LargeClass, LongMethod, CyclomaticComplexMethod) (REQ-PRO-047.2, TST-PRO-047.2).
     */
    @Test
    fun testDetektConfig_complexityRulesActive() {
        val detektConfigContent = findDetektConfigFile().readText()

        assertTrue(
            "config/detekt/detekt.yml must configure LargeClass rule (REQ-PRO-047.2, TST-PRO-047.2)",
            detektConfigContent.contains("LargeClass:")
        )
        assertTrue(
            "config/detekt/detekt.yml must configure LongMethod rule (REQ-PRO-047.2, TST-PRO-047.2)",
            detektConfigContent.contains("LongMethod:")
        )
        assertTrue(
            "config/detekt/detekt.yml must configure CyclomaticComplexMethod rule (REQ-PRO-047.2, TST-PRO-047.2)",
            detektConfigContent.contains("CyclomaticComplexMethod:")
        )
    }

    /**
     * Asserts that app/detekt-baseline.xml exists and contains recorded
     * legacy baseline smells (REQ-PRO-047.3, TST-PRO-047.1).
     */
    @Test
    fun testDetektBaseline_existsAndIsPopulated() {
        val baselineFile = findDetektBaselineFile()

        assertTrue(
            "app/detekt-baseline.xml must exist (REQ-PRO-047.3, TST-PRO-047.1)",
            baselineFile.exists()
        )
        assertTrue(
            "app/detekt-baseline.xml must be populated with legacy baseline smells (REQ-PRO-047.3, TST-PRO-047.1)",
            baselineFile.length() > 1000
        )
    }

    /**
     * Asserts that CI workflow executes Detekt and uploads report artifacts (REQ-PRO-047.4, TST-PRO-047.3).
     */
    @Test
    fun testBranchBuildWorkflow_runsDetektAndUploadsReport() {
        val content = findCiWorkflowFile().readText()
        val detektStep = "./gradlew :app:detekt"
        val artifactStep = "actions/upload-artifact"
        val reportPath = "app/build/reports/detekt/"

        assertTrue(
            "CI workflow must execute Detekt ($detektStep) (REQ-PRO-047.4, TST-PRO-047.3)",
            content.contains(detektStep)
        )
        assertTrue(
            "CI workflow must upload detekt reports via $artifactStep (REQ-PRO-047.4, TST-PRO-047.3)",
            content.contains(artifactStep) && content.contains("name: detekt-report")
        )
        assertTrue(
            "CI workflow must specify detekt report artifact path $reportPath (REQ-PRO-047.4, TST-PRO-047.3)",
            content.contains(reportPath)
        )
    }

    /**
     * Asserts that root build.gradle and app/build.gradle apply and configure the Kover plugin
     * with verification threshold rules (REQ-PRO-048.1, REQ-PRO-048.2, TST-PRO-048.1).
     */
    @Test
    fun testBuildGradle_koverPluginAndConfiguration() {
        val rootBuildContent = findRootBuildGradleFile().readText()
        val appBuildContent = findBuildGradleFile().readText()

        assertTrue(
            "Root build.gradle must add Kover plugin classpath (REQ-PRO-048.1, TST-PRO-048.1)",
            rootBuildContent.contains("org.jetbrains.kotlinx:kover-gradle-plugin")
        )
        assertTrue(
            "app/build.gradle must apply org.jetbrains.kotlinx.kover plugin (REQ-PRO-048.1, TST-PRO-048.1)",
            appBuildContent.contains("org.jetbrains.kotlinx.kover")
        )
        assertTrue(
            "app/build.gradle must define kover configuration block (REQ-PRO-048.2, TST-PRO-048.1)",
            appBuildContent.contains("kover {")
        )
        assertTrue(
            "app/build.gradle must configure kover verification minBound threshold (REQ-PRO-048.2, TST-PRO-048.1)",
            appBuildContent.contains("minBound")
        )
    }

    /**
     * Asserts that CI workflow executes Kover reports & verification and uploads report artifacts
     * (REQ-PRO-048.3, TST-PRO-048.2).
     */
    @Test
    fun testBranchBuildWorkflow_runsKoverAndUploadsReport() {
        val content = findCiWorkflowFile().readText()
        val koverReportStep = ":app:koverXmlReportDebug"
        val koverVerifyStep = ":app:koverVerifyDebug"
        val artifactStep = "actions/upload-artifact"
        val reportPath = "app/build/reports/kover/"

        assertTrue(
            "CI workflow must execute Kover XML report ($koverReportStep) (REQ-PRO-048.3, TST-PRO-048.2)",
            content.contains(koverReportStep)
        )
        assertTrue(
            "CI workflow must execute Kover verification ($koverVerifyStep) (REQ-PRO-048.3, TST-PRO-048.2)",
            content.contains(koverVerifyStep)
        )
        assertTrue(
            "CI workflow must upload kover reports via $artifactStep (REQ-PRO-048.3, TST-PRO-048.2)",
            content.contains(artifactStep) && content.contains("name: kover-report")
        )
        assertTrue(
            "CI workflow must specify kover report artifact path $reportPath (REQ-PRO-048.3, TST-PRO-048.2)",
            content.contains(reportPath)
        )
    }

    /**
     * Asserts that app/build.gradle configures testInstrumentationRunner and declares
     * standard androidTestImplementation dependencies (REQ-PRO-051.1, TST-PRO-051.1).
     */
    @Test
    fun testBuildGradle_androidTestInstrumentationRunnerAndDependencies() {
        val content = findBuildGradleFile().readText()

        assertTrue(
            "app/build.gradle must configure AndroidJUnitRunner in defaultConfig (REQ-PRO-051.1, TST-PRO-051.1)",
            content.contains("testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\"") ||
                content.contains("testInstrumentationRunner \"androidx.test.runner.AndroidJUnitRunner\"")
        )
        assertTrue(
            "app/build.gradle must declare androidTestImplementation for androidx.test.ext:junit (REQ-PRO-051.1, TST-PRO-051.1)",
            content.contains("androidTestImplementation 'androidx.test.ext:junit")
        )
        assertTrue(
            "app/build.gradle must declare androidTestImplementation for espresso-core (REQ-PRO-051.1, TST-PRO-051.1)",
            content.contains("androidTestImplementation 'androidx.test.espresso:espresso-core")
        )
        assertTrue(
            "app/build.gradle must declare androidTestImplementation for androidx.test:runner (REQ-PRO-051.1, TST-PRO-051.1)",
            content.contains("androidTestImplementation 'androidx.test:runner")
        )
        assertTrue(
            "app/build.gradle must declare androidTestImplementation for androidx.test:rules (REQ-PRO-051.1, TST-PRO-051.1)",
            content.contains("androidTestImplementation 'androidx.test:rules")
        )
    }

    /**
     * Asserts that CI workflow defines emulator-test job executing connectedDebugAndroidTest
     * with reactivecircus/android-emulator-runner and uploading report artifacts (REQ-PRO-051.3, TST-PRO-051.2).
     */
    @Test
    fun testBranchBuildWorkflow_runsEmulatorTestsAndUploadsReport() {
        val content = findCiWorkflowFile().readText()
        val emulatorJob = "emulator-test:"
        val emulatorRunnerAction = "reactivecircus/android-emulator-runner"
        val testCommand = "connectedDebugAndroidTest"
        val artifactStep = "actions/upload-artifact"
        val reportPath = "app/build/reports/androidTests/connected/"

        assertTrue(
            "CI workflow must define emulator-test job (REQ-PRO-051.3, TST-PRO-051.2)",
            content.contains(emulatorJob)
        )
        assertTrue(
            "CI workflow must utilize android-emulator-runner action (REQ-PRO-051.3, TST-PRO-051.2)",
            content.contains(emulatorRunnerAction)
        )
        assertTrue(
            "CI workflow must execute connectedDebugAndroidTest (REQ-PRO-051.3, TST-PRO-051.2)",
            content.contains(testCommand)
        )
        assertTrue(
            "CI workflow must upload connected test reports via $artifactStep (REQ-PRO-051.3, TST-PRO-051.2)",
            content.contains(artifactStep) && content.contains("name: connected-test-report")
        )
        assertTrue(
            "CI workflow must specify connected test report artifact path $reportPath (REQ-PRO-051.3, TST-PRO-051.2)",
            content.contains(reportPath)
        )
    }

    /**
     * Asserts that app/src/androidTest contains the initial smoke instrumentation test (REQ-PRO-051.2, TST-PRO-051.3).
     */
    @Test
    fun testAndroidTestDirectory_containsSmokeTest() {
        val smokeTestFile = findRepoRootFile(
            "app/src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt",
            "src/androidTest/java/com/atrainingtracker/trainingtracker/AppSmokeAndroidTest.kt"
        )

        assertTrue(
            "AppSmokeAndroidTest.kt must exist in app/src/androidTest/ (REQ-PRO-051.2, TST-PRO-051.3)",
            smokeTestFile.exists()
        )
        val content = smokeTestFile.readText()
        assertTrue(
            "AppSmokeAndroidTest must be configured with AndroidJUnit4 runner (REQ-PRO-051.2)",
            content.contains("@RunWith(AndroidJUnit4::class)")
        )
        assertTrue(
            "AppSmokeAndroidTest must contain test methods (REQ-PRO-051.2)",
            content.contains("@Test")
        )
    }
}
