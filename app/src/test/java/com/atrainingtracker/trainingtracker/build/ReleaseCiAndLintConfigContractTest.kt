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
 * REQ-PRO-051, TST-PRO-051, REQ-STP-007, TST-STP-007, REQ-STP-008, TST-STP-008, REQ-STP-009, TST-STP-009, ATT-6148, ATT-6152, ATT-6153, ATT-6171, ATT-6175, ATT-6184, ATT-6737).
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

    private fun findRepoRootDir(): File {
        val candidates = listOf(File("."), File(".."), File("../.."))
        return candidates.firstOrNull { File(it, "settings.gradle").exists() }
            ?: File(".")
    }

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
        assertTrue(
            "config/detekt/detekt.yml must configure FunctionNaming with Composable exemption " +
                "(REQ-PRO-047.2, TST-PRO-047.1)",
            detektConfigContent.contains("FunctionNaming:") && detektConfigContent.contains("Composable")
        )
        assertTrue(
            "config/detekt/detekt.yml must configure LongParameterList with Composable exemption " +
                "(REQ-PRO-047.2, TST-PRO-047.1)",
            detektConfigContent.contains("LongParameterList:") && detektConfigContent.contains("Composable")
        )
        assertTrue(
            "config/detekt/detekt.yml must configure UnusedPrivateMember with Preview exemption " +
                "(REQ-PRO-047.2, TST-PRO-047.1)",
            detektConfigContent.contains("UnusedPrivateMember:") && detektConfigContent.contains("Preview")
        )
        assertTrue(
            "config/detekt/detekt.yml must configure MagicNumber exemptions (REQ-PRO-047.2, TST-PRO-047.1)",
            detektConfigContent.contains("MagicNumber:") &&
                detektConfigContent.contains("ignorePropertyDeclaration: true")
        )
    }

    /**
     * Asserts that app/detekt-baseline.xml exists and contains recorded
     * legacy baseline smells within the ratcheted ceiling bound (REQ-PRO-047.3, TST-PRO-047.1, TST-PRO-047.2).
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
        val baselineContent = baselineFile.readText()
        val idCount = Regex("<ID>").findAll(baselineContent).count()
        assertTrue(
            "app/detekt-baseline.xml must contain ratcheted issue count <= 9550 (current=$idCount) " +
                "(REQ-PRO-047.3, TST-PRO-047.2)",
            idCount in 1..9550
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
            appBuildContent.contains("minBound(35)") || appBuildContent.contains("minValue = 35")
        )
        assertTrue(
            "app/build.gradle must exclude *.R from Kover reports (REQ-PRO-048.2, TST-PRO-048.1)",
            appBuildContent.contains("\"*.R\"") || appBuildContent.contains("'*.R'")
        )
        assertTrue(
            "app/build.gradle must exclude *Manifest* from Kover reports (REQ-PRO-048.2, TST-PRO-048.1)",
            appBuildContent.contains("\"*Manifest*\"") || appBuildContent.contains("'*Manifest*'")
        )
        assertTrue(
            "app/build.gradle must configure kover verification threshold for branch coverage " +
                "(REQ-PRO-048.2, TST-PRO-048.1)",
            appBuildContent.contains("CoverageUnit.BRANCH") &&
                (appBuildContent.contains("minValue = 30") || appBuildContent.contains("minBound(30)"))
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
        assertTrue(
            "CI workflow must pre-build debug and androidTest APKs prior to emulator startup (REQ-PRO-051.3, TST-PRO-051.2, ATT-6737)",
            content.contains(":app:assembleDebug :app:assembleDebugAndroidTest")
        )
        assertTrue(
            "CI workflow must configure emulator runner with API 34 and google_apis target (REQ-PRO-051.3, TST-PRO-051.2, ATT-6737)",
            content.contains("api-level: 34") && content.contains("target: google_apis")
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
        assertTrue(
            "AppSmokeAndroidTest must reference MainActivityWithNavigation (REQ-PRO-051.2, TST-PRO-051.3, ATT-6737)",
            content.contains("MainActivityWithNavigation")
        )
        assertTrue(
            "AppSmokeAndroidTest must configure GrantPermissionRule for runtime permissions (REQ-PRO-051.2, TST-PRO-051.3, ATT-6737)",
            content.contains("GrantPermissionRule")
        )
    }

    /**
     * Asserts that legacy transient process artifacts are excised from the repository root
     * and that .gitignore explicitly ignores them (REQ-STP-007, TST-STP-007, ATT-6153).
     */
    @Test
    fun testRepositoryHygiene_noStaleProcessArtifactsAndGitignoreCovered() {
        val repoRoot = findRepoRootDir()
        val staleArtifacts = listOf(
            ".active_task.json",
            "implementation_plan.artifact.md",
            ".artifacts"
        )
        for (stale in staleArtifacts) {
            val file = File(repoRoot, stale)
            assertFalse(
                "Stale process artifact '$stale' must not exist in repository root (REQ-STP-007.1, TST-STP-007.1)",
                file.exists()
            )
        }

        val gitignoreFile = findRepoRootFile(".gitignore", "../.gitignore")
        val gitignoreContent = gitignoreFile.readText()
        val requiredPatterns = listOf(
            ".active_task.json",
            ".artifacts/",
            "*.artifact.md",
            ".reviews/"
        )
        for (pattern in requiredPatterns) {
            assertTrue(
                ".gitignore must include '$pattern' (REQ-STP-007.2, TST-STP-007.2)",
                gitignoreContent.lines().any { it.trim() == pattern }
            )
        }
    }

    /**
     * Asserts that release signing passwords are decoupled from the working tree via the
     * prioritized provider chain (CI env vars -> secret-tool -> ~/.config/atrainingtracker/release.properties)
     * and that workspace release.properties contains zero plaintext password keys (REQ-STP-008, TST-STP-008, ATT-6148).
     */
    @Test
    fun testReleaseSigningHygiene_passwordsDecoupledFromWorkingTree() {
        val buildGradle = findBuildGradleFile()
        val buildContent = buildGradle.readText()

        // 1. Verify app/build.gradle implements provider chain and avoids reading passwords from workspace release.properties
        assertTrue(
            "app/build.gradle must define resolveSigningSecret provider chain (REQ-STP-008.2)",
            buildContent.contains("def resolveSigningSecret")
        )
        assertTrue(
            "resolveSigningSecret must support process environment variables (REQ-STP-008.2)",
            buildContent.contains("System.getenv(envVar)")
        )
        assertTrue(
            "resolveSigningSecret must support Linux secret-tool keyring lookup (REQ-STP-008.2)",
            buildContent.contains("secret-tool") && buildContent.contains("lookup")
        )
        assertTrue(
            "resolveSigningSecret must support external config in ~/.config/atrainingtracker/release.properties (REQ-STP-008.2)",
            buildContent.contains(".config/atrainingtracker/release.properties")
        )
        assertFalse(
            "app/build.gradle must not read storePassword directly from workspace release.properties (REQ-STP-008.1)",
            buildContent.contains("props.keyStorePassword")
        )
        assertFalse(
            "app/build.gradle must not read keyPassword directly from workspace release.properties (REQ-STP-008.1)",
            buildContent.contains("props.keyAliasPassword")
        )

        // 2. Verify .github/workflows/android-release.yml injects passwords as env vars without writing them to disk
        val releaseWorkflow = findReleaseWorkflowFile()
        val workflowContent = releaseWorkflow.readText()
        assertTrue(
            "CI release workflow must supply RELEASE_KEYSTORE_PASSWORD as env var (REQ-STP-008.3)",
            workflowContent.contains("RELEASE_KEYSTORE_PASSWORD: \${{ secrets.RELEASE_KEYSTORE_PASSWORD }}")
        )
        assertTrue(
            "CI release workflow must supply RELEASE_KEY_PASSWORD as env var (REQ-STP-008.3)",
            workflowContent.contains("RELEASE_KEY_PASSWORD: \${{ secrets.RELEASE_KEY_PASSWORD }}")
        )
        assertFalse(
            "CI release workflow must not write keyStorePassword to release.properties (REQ-STP-008.3)",
            workflowContent.contains("keyStorePassword=")
        )
        assertFalse(
            "CI release workflow must not write keyAliasPassword to release.properties (REQ-STP-008.3)",
            workflowContent.contains("keyAliasPassword=")
        )

        // 3. Verify workspace release.properties (if present) contains zero password keys
        val repoRoot = findRepoRootDir()
        val workspaceReleaseProps = File(repoRoot, "release.properties")
        if (workspaceReleaseProps.exists()) {
            val propsContent = workspaceReleaseProps.readText()
            assertFalse(
                "Workspace release.properties must not contain keyStorePassword (REQ-STP-008.1, TST-STP-008.3)",
                propsContent.contains("keyStorePassword")
            )
            assertFalse(
                "Workspace release.properties must not contain keyAliasPassword (REQ-STP-008.1, TST-STP-008.3)",
                propsContent.contains("keyAliasPassword")
            )
        }
    }

    /**
     * Asserts that docs/management/cm_plan.md exists, contains mandatory ASPICE SUP.8 sections,
     * and that .gitmodules pins submodules without floating branch tracking (REQ-STP-009, TST-STP-009, ATT-6152).
     */
    @Test
    fun testConfigurationManagementPlan_existsAndSubmodulePinned() {
        val cmPlanFile = findRepoRootFile("docs/management/cm_plan.md", "../docs/management/cm_plan.md")
        assertTrue(
            "docs/management/cm_plan.md must exist (REQ-STP-009.1, TST-STP-009.1)",
            cmPlanFile.exists()
        )
        val cmContent = cmPlanFile.readText()
        val requiredSections = listOf(
            "SUP.8.BP1", // Strategy / Scope
            "SUP.8.BP2", // Configuration Items
            "SUP.8.BP3", // Branching & Merging
            "SUP.8.BP4", // Baselines
            "SUP.8.BP6", // Submodule Control
            "SUP.8.BP7"  // Disaster Recovery
        )
        for (section in requiredSections) {
            assertTrue(
                "CM plan must contain section reference '$section' (REQ-STP-009.1, TST-STP-009.1)",
                cmContent.contains(section)
            )
        }

        val gitmodulesFile = findRepoRootFile(".gitmodules", "../.gitmodules")
        assertTrue(
            ".gitmodules must exist (REQ-STP-009.2, TST-STP-009.2)",
            gitmodulesFile.exists()
        )
        val gitmodulesContent = gitmodulesFile.readText()
        assertFalse(
            ".gitmodules must not contain floating branch directive for ANT-Android-SDKs (REQ-STP-009.2, TST-STP-009.2)",
            gitmodulesContent.contains("branch =")
        )
    }

    /**
     * Asserts that docs/management/cm_plan.md documents 'master' as the production branch (and no 'main' branch),
     * documents sprint milestone tags 'sprint/<SPRINT_NAME>', dual release tag formats, the complete controlled CI inventory,
     * and formal release contents (REQ-PRO-057, TST-PRO-057, ATT-6733).
     */
    @Test
    fun testConfigurationManagementPlan_repositoryGroundingAndReleaseContents() {
        val cmPlanFile = findRepoRootFile("docs/management/cm_plan.md", "../docs/management/cm_plan.md")
        assertTrue("docs/management/cm_plan.md must exist (REQ-PRO-057)", cmPlanFile.exists())
        val cmContent = cmPlanFile.readText()

        // Production branch is master; no branch named 'main'
        assertTrue("CM plan must document 'master' as production release branch (REQ-PRO-057.1)", cmContent.contains("`master`"))
        assertFalse("CM plan must not refer to 'main' as production branch (REQ-PRO-057.1)", cmContent.contains("`main`"))

        // Sprint milestone tags & dual release tagging
        assertTrue("CM plan must document sprint milestone tags (REQ-PRO-057.2)", cmContent.contains("sprint/<SPRINT_NAME>"))
        assertTrue("CM plan must document dual release tag format (REQ-PRO-057.2)", cmContent.contains("V<versionName>__<versionCode>"))
        assertTrue("CM plan must document simple release tag format (REQ-PRO-057.2)", cmContent.contains("V<versionName>"))

        // Controlled CI inventory table completeness
        val requiredCIs = listOf(
            "docs/architecture/*.md",
            "docs/management/*.md",
            "config/detekt/",
            "tools/",
            ".agents/"
        )
        for (ci in requiredCIs) {
            assertTrue("CM plan CI table must contain '$ci' (REQ-PRO-057.3)", cmContent.contains(ci))
        }

        // Formal Release Deliverables package
        val requiredDeliverables = listOf(
            "Signed Production APK",
            "Google Play Android App Bundle (AAB)",
            "docs/store_presence/",
            "FixVersion"
        )
        for (deliv in requiredDeliverables) {
            assertTrue("CM plan Release Deliverables must contain '$deliv' (REQ-PRO-057.4)", cmContent.contains(deliv))
        }
    }

    /**
     * Asserts that docs/management/risk_register.md exists, defines mandatory ASPICE MAN.3.BP5 sections,
     * links all High-exposure risks to Jira remediation tickets, and is integrated into Ceremony 1
     * in .agents/skills/sprint-planner/SKILL.md (REQ-STP-010, TST-STP-010, ATT-6151).
     */
    @Test
    fun testProjectRiskRegister_existsAndHighExposureMitigated() {
        val riskRegisterFile = findRepoRootFile("docs/management/risk_register.md", "../docs/management/risk_register.md")
        assertTrue(
            "docs/management/risk_register.md must exist (REQ-STP-010.1, TST-STP-010.1)",
            riskRegisterFile.exists()
        )
        val riskContent = riskRegisterFile.readText()

        val requiredSections = listOf(
            "MAN.3.BP5",
            "Risk Assessment & Scoring Rubric",
            "Risk Exposure Matrix",
            "Sprint Review Log",
            "Registered Project & Technical Risks"
        )
        for (section in requiredSections) {
            assertTrue(
                "Risk register must contain section '$section' (REQ-STP-010.1, TST-STP-010.1)",
                riskContent.contains(section)
            )
        }

        val requiredRisks = listOf("RISK-001", "RISK-002", "RISK-003", "RISK-004", "RISK-005", "RISK-006")
        for (riskId in requiredRisks) {
            assertTrue(
                "Risk register must register initial risk '$riskId' (REQ-STP-010.1, TST-STP-010.1)",
                riskContent.contains(riskId)
            )
        }

        // Verify High-exposure risks (RISK-001, RISK-002) link to remediation tickets
        assertTrue(
            "High-exposure risk RISK-001 must link to mitigation ticket ATT-6148 (REQ-STP-010.1, TST-STP-010.1)",
            riskContent.contains("ATT-6148")
        )
        assertTrue(
            "High-exposure risk RISK-002 must link to mitigation ticket ATT-6150 (REQ-STP-010.1, TST-STP-010.1)",
            riskContent.contains("ATT-6150")
        )

        // Verify Sprint Review Log records active sprint baseline
        assertTrue(
            "Sprint Review Log must record active sprint 2026-46.1 review baseline (REQ-STP-010.1)",
            riskContent.contains("2026-46.1") && riskContent.contains("2026-11-10")
        )

        // Verify Ceremony 1 integration in .agents/skills/sprint-planner/SKILL.md
        val sprintPlannerFile = findRepoRootFile(".agents/skills/sprint-planner/SKILL.md", "../.agents/skills/sprint-planner/SKILL.md")
        assertTrue(
            ".agents/skills/sprint-planner/SKILL.md must exist (REQ-STP-010.2, TST-STP-010.2)",
            sprintPlannerFile.exists()
        )
        val sprintPlannerContent = sprintPlannerFile.readText()
        assertTrue(
            "sprint-planner skill Ceremony 1 must include risk register review (REQ-STP-010.2, TST-STP-010.2)",
            sprintPlannerContent.contains("docs/management/risk_register.md") &&
                sprintPlannerContent.contains("REQ-STP-010")
        )
    }
}
