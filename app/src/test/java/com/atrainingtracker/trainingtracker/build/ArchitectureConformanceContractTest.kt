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
 * Architectural conformance contract test enforcing layer boundaries, dependency directions,
 * and quantitative resource & timing budgets (docs/architecture.md, REQ-PRO-050, TST-PRO-050,
 * REQ-PRO-052, TST-PRO-052, ATT-6182, ATT-6181).
 *
 * Enforces:
 * 1. UI layer files must not reference raw SQLite *DatabaseManager or *DbHelper singletons
 *    outside the ratcheted baseline allow-list (REQ-PRO-050.1, REQ-PRO-050.3).
 * 2. Domain calculation engines remain pure JVM without Android UI or database coupling (REQ-PRO-050.2).
 * 3. Tripwires detect unallow-listed UI database references or domain UI imports (REQ-PRO-050.3).
 * 4. docs/architecture.md defines explicit resource and timing budgets (SWE.2.BP4, REQ-PRO-052).
 */
class ArchitectureConformanceContractTest {

    companion object {
        private val DB_PATTERN = Regex("""\b[A-Za-z0-9_]*(?:DatabaseManager|DbHelper)\b""")
        private val FORBIDDEN_DOMAIN_PATTERNS = listOf(
            Regex("""\bandroid\.view\b"""),
            Regex("""\bandroidx\.compose\b"""),
            Regex("""\bandroid\.widget\b"""),
            Regex("""\b[A-Za-z0-9_]*(?:DatabaseManager|DbHelper)\b"""),
            Regex("""\bcom\.atrainingtracker\.trainingtracker\.database\b""")
        )

        /**
         * Ratcheted baseline of existing UI files referencing DatabaseManager / DbHelper (REQ-PRO-050.3).
         * Net-new UI classes must use repositories/use-cases and are forbidden from direct DB bindings.
         */
        val BASELINE_ALLOWED_UI_DB_CALLERS: Set<String> = setOf(
            "com/atrainingtracker/trainingtracker/ui/aftermath/EquipmentDataProvider.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/SportDataProvider.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteProfileViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusDataProvider.kt",
            "com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/components/stats/StatsData.kt",
            "com/atrainingtracker/trainingtracker/ui/components/strava/StravaActivitySection.kt",
            "com/atrainingtracker/trainingtracker/ui/equipment/EditEquipmentDialog.kt",
            "com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorMatrixScreen.kt",
            "com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorOrdering.kt",
            "com/atrainingtracker/trainingtracker/ui/equipment/EquipmentViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/map/MapInitialLocationResolver.kt",
            "com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt"
        )
    }

    private fun findMainSourceDir(): File {
        val candidates = listOf(
            File("app/src/main/java"),
            File("src/main/java"),
            File("../app/src/main/java"),
            File("../src/main/java")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not locate main source directory in: ${File(".").absolutePath}")
    }

    private fun normalizePath(file: File, baseDir: File): String {
        return file.relativeTo(baseDir).path.replace('\\', '/')
    }

    @Test
    fun testUiLayer_noUnallowlistedDatabaseManagerReferences() {
        val mainDir = findMainSourceDir()
        val uiDir = File(mainDir, "com/atrainingtracker/trainingtracker/ui")
        assertTrue("UI source directory must exist: ${uiDir.absolutePath}", uiDir.exists() && uiDir.isDirectory)

        val detectedCallers = mutableSetOf<String>()
        uiDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.endsWith(".kt") || file.name.endsWith(".java"))) {
                val content = file.readText()
                if (DB_PATTERN.containsMatchIn(content)) {
                    detectedCallers.add(normalizePath(file, mainDir))
                }
            }
        }

        val unallowlisted = detectedCallers - BASELINE_ALLOWED_UI_DB_CALLERS
        assertTrue(
            "Found net-new unallow-listed UI classes directly referencing DatabaseManager/DbHelper " +
                "(REQ-PRO-050.1, docs/architecture.md layering violation):\n  " +
                unallowlisted.joinToString("\n  "),
            unallowlisted.isEmpty()
        )
    }

    @Test
    fun testDomainLayer_pureJvmIsolation() {
        val mainDir = findMainSourceDir()
        val domainDir = File(mainDir, "com/atrainingtracker/trainingtracker/domain")
        assertTrue("Domain source directory must exist: ${domainDir.absolutePath}", domainDir.exists() && domainDir.isDirectory)

        val violations = mutableListOf<String>()
        domainDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.endsWith(".kt") || file.name.endsWith(".java"))) {
                val relPath = normalizePath(file, mainDir)
                val lines = file.readLines()
                lines.forEachIndexed { lineIdx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import ")) {
                        FORBIDDEN_DOMAIN_PATTERNS.forEach { pattern ->
                            if (pattern.containsMatchIn(trimmed)) {
                                violations.add("$relPath:${lineIdx + 1}: $trimmed (matched $pattern)")
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            "Domain calculation layer must remain pure JVM without Android UI or database dependencies " +
                "(REQ-PRO-050.2):\n  " + violations.joinToString("\n  "),
            violations.isEmpty()
        )
    }

    @Test
    fun testBaseline_containsExpectedCount() {
        assertEquals(
            "Expected exactly 24 recorded legacy UI database callers in ratcheting baseline (REQ-PRO-050.3)",
            24,
            BASELINE_ALLOWED_UI_DB_CALLERS.size
        )
    }

    @Test
    fun testRatchetingTripwire_detectsUnallowlistedUiDatabaseCall() {
        val mockDetected = setOf("com/atrainingtracker/trainingtracker/ui/fake/NewComposeScreen.kt")
        val unallowlisted = mockDetected - BASELINE_ALLOWED_UI_DB_CALLERS

        assertFalse(
            "Ratcheting tripwire must detect unallow-listed UI class referencing DatabaseManager (REQ-PRO-050.3)",
            unallowlisted.isEmpty()
        )
        assertEquals("NewComposeScreen.kt", File(unallowlisted.first()).name)
    }

    @Test
    fun testRatchetingTripwire_detectsForbiddenDomainImport() {
        val mockImport = "import androidx.compose.runtime.Composable"
        val matched = FORBIDDEN_DOMAIN_PATTERNS.any { it.containsMatchIn(mockImport) }
        assertTrue(
            "Tripwire must catch forbidden Compose import in domain layer (REQ-PRO-050.2, REQ-PRO-050.3)",
            matched
        )
    }

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate $relativePath in working directory: ${File(".").absolutePath}")
    }

    /**
     * Asserts that docs/architecture.md defines explicit resource and timing budgets
     * for runtime-critical subsystems (REQ-PRO-052.1, REQ-PRO-052.2, TST-PRO-052.1, TST-PRO-052.2).
     */
    @Test
    fun testArchitectureDoc_definesResourceAndTimingBudgets() {
        val archFile = findRepoRootFile("docs/architecture.md")
        val content = archFile.readText()

        // TST-PRO-052.1: Section 4.9 and critical subsystem domains present
        assertTrue(
            "docs/architecture.md must contain Section 4.9 (Resource and Timing Budgets) (REQ-PRO-052.1, TST-PRO-052.1)",
            content.contains("### 4.9 Resource and Timing Budgets")
        )
        assertTrue(
            "docs/architecture.md must specify budgets for TrackerService (REQ-PRO-052.1, TST-PRO-052.1)",
            content.contains("TrackerService")
        )
        assertTrue(
            "docs/architecture.md must specify budgets for BANALService / ProxySensor (REQ-PRO-052.1, TST-PRO-052.1)",
            content.contains("BANALService") && content.contains("ProxySensor")
        )
        assertTrue(
            "docs/architecture.md must specify budgets for WorkoutSamplesDatabaseManager (REQ-PRO-052.1, TST-PRO-052.1)",
            content.contains("WorkoutSamplesDatabaseManager")
        )
        assertTrue(
            "docs/architecture.md must specify budgets for Cockpit UI and Map (REQ-PRO-052.1, TST-PRO-052.1)",
            content.contains("SensorGridScreen") && content.contains("ATrainingTrackerMap")
        )

        // TST-PRO-052.2: Quantitative metric thresholds present
        assertTrue("Must specify CPU consumption ceiling (< 5%) (REQ-PRO-052.1)", content.contains("< 5%"))
        assertTrue("Must specify dedicated heap footprint ceiling (< 40 MB) (REQ-PRO-052.1)", content.contains("< 40 MB"))
        assertTrue("Must specify periodic tick latency budget (< 50 ms) (REQ-PRO-052.1)", content.contains("< 50 ms"))
        assertTrue("Must specify background battery drain ceiling (< 5%/h) (REQ-PRO-052.1)", content.contains("< 5%/h"))
        assertTrue("Must specify sensor dispatch latency (< 25 ms) (REQ-PRO-052.1)", content.contains("< 25 ms"))
        assertTrue("Must specify sensor failover window (3.5 s) (REQ-PRO-052.1)", content.contains("3.5 s"))
        assertTrue("Must specify database write latency (< 30 ms) (REQ-PRO-052.1)", content.contains("< 30 ms"))
        assertTrue("Must specify database storage growth rate (~360 KB/h) (REQ-PRO-052.1)", content.contains("360 KB/h"))
        assertTrue("Must specify UI frame render budget (< 16.6 ms) (REQ-PRO-052.1)", content.contains("16.6 ms"))
    }
}
