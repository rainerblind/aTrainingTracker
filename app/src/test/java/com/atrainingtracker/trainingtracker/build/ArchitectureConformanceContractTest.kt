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
        private val BLOCK_COMMENT_PATTERN = Regex("""/\*[\s\S]*?\*/""")
        private val LINE_COMMENT_PATTERN = Regex("""//.*""")

        private val FORBIDDEN_DOMAIN_PATTERNS = listOf(
            Regex("""\bandroid\.view\b"""),
            Regex("""\bandroidx\.compose\b"""),
            Regex("""\bandroid\.widget\b"""),
            Regex("""\b[A-Za-z0-9_]*(?:DatabaseManager|DbHelper)\b""")
        )

        private val FORBIDDEN_DATABASE_UPWARD_PATTERNS = listOf(
            Regex("""\bandroidx\.compose\b"""),
            Regex("""\bcom\.atrainingtracker\..*\.ui\..*(?:Screen|Dialog|BottomSheet|Fragment|Activity|View|Sheet)\b""")
        )

        /**
         * Ratcheted baseline of existing UI files referencing DatabaseManager / DbHelper (REQ-PRO-050.3).
         * Net-new UI classes must use repositories/use-cases and are forbidden from direct DB bindings.
         * Enforces downward ratcheting: (baseline - detectedCallers).isEmpty() and size <= 23.
         */
        val BASELINE_ALLOWED_UI_DB_CALLERS: Set<String> = setOf(
            "com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt",
            "com/atrainingtracker/banalservice/ui/devices/devicedata/RawDeviceDataProvider.kt",
            "com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedViewModel.kt",
            "com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialog.kt",
            "com/atrainingtracker/banalservice/ui/sporttype/SportTypeViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/EquipmentDataProvider.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/SportDataProvider.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/editworkout/EditWorkoutViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt",
            "com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/athleteprofile/AthleteMetricsRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusDataProvider.kt",
            "com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/components/stats/StatsData.kt",
            "com/atrainingtracker/trainingtracker/ui/equipment/EquipmentSensorOrdering.kt",
            "com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/map/MapFragmentWithTrackViewModel.kt",
            "com/atrainingtracker/trainingtracker/ui/map/MapInitialLocationResolver.kt",
            "com/atrainingtracker/trainingtracker/ui/tracking/TrackingViewsRepository.kt",
            "com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt"
        )

        fun stripComments(content: String): String {
            return content.replace(BLOCK_COMMENT_PATTERN, "").replace(LINE_COMMENT_PATTERN, "")
        }
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
        val uiDirs = listOf(
            File(mainDir, "com/atrainingtracker/trainingtracker/ui"),
            File(mainDir, "com/atrainingtracker/banalservice/ui")
        )
        uiDirs.forEach { dir ->
            assertTrue("UI source directory must exist: ${dir.absolutePath}", dir.exists() && dir.isDirectory)
        }

        val detectedCallers = mutableSetOf<String>()
        uiDirs.forEach { uiDir ->
            uiDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.name.endsWith(".kt") || file.name.endsWith(".java"))) {
                    val content = stripComments(file.readText())
                    if (DB_PATTERN.containsMatchIn(content)) {
                        detectedCallers.add(normalizePath(file, mainDir))
                    }
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

        val staleInBaseline = BASELINE_ALLOWED_UI_DB_CALLERS - detectedCallers
        assertTrue(
            "Ratcheted baseline contains refactored files that no longer reference DatabaseManager/DbHelper. " +
                "Remove them to ratchet down the debt ceiling (REQ-PRO-050.3):\n  " +
                staleInBaseline.joinToString("\n  "),
            staleInBaseline.isEmpty()
        )

        assertTrue(
            "Detected UI callers (${detectedCallers.size}) exceeds ratcheted ceiling (${BASELINE_ALLOWED_UI_DB_CALLERS.size}) (REQ-PRO-050.3)",
            detectedCallers.size <= BASELINE_ALLOWED_UI_DB_CALLERS.size
        )
    }

    @Test
    fun testDomainLayer_pureJvmIsolation() {
        val mainDir = findMainSourceDir()
        val domainDirs = listOf(
            File(mainDir, "com/atrainingtracker/trainingtracker/domain"),
            File(mainDir, "com/atrainingtracker/trainingtracker/physiology")
        )
        domainDirs.forEach { dir ->
            assertTrue("Domain/Physiology directory must exist: ${dir.absolutePath}", dir.exists() && dir.isDirectory)
        }

        val violations = mutableListOf<String>()
        domainDirs.forEach { domainDir ->
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
        }

        assertTrue(
            "Domain and physiology calculation layer must remain pure JVM without Android UI or database dependencies " +
                "(REQ-PRO-050.2):\n  " + violations.joinToString("\n  "),
            violations.isEmpty()
        )
    }

    @Test
    fun testDatabaseLayer_noUpwardUiOrComposeImports() {
        val mainDir = findMainSourceDir()
        val dbDirs = listOf(
            File(mainDir, "com/atrainingtracker/trainingtracker/database"),
            File(mainDir, "com/atrainingtracker/banalservice/database")
        )
        dbDirs.forEach { dir ->
            assertTrue("Database directory must exist: ${dir.absolutePath}", dir.exists() && dir.isDirectory)
        }

        val violations = mutableListOf<String>()
        dbDirs.forEach { dbDir ->
            dbDir.walkTopDown().forEach { file ->
                if (file.isFile && (file.name.endsWith(".kt") || file.name.endsWith(".java"))) {
                    val relPath = normalizePath(file, mainDir)
                    val lines = file.readLines()
                    lines.forEachIndexed { lineIdx, line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("import ")) {
                            FORBIDDEN_DATABASE_UPWARD_PATTERNS.forEach { pattern ->
                                if (pattern.containsMatchIn(trimmed)) {
                                    violations.add("$relPath:${lineIdx + 1}: $trimmed (matched $pattern)")
                                }
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            "Database persistence layer must not import Compose or UI screen/composable classes (REQ-PRO-050.5):\n  " +
                violations.joinToString("\n  "),
            violations.isEmpty()
        )
    }

    @Test
    fun testBaseline_containsExpectedCount() {
        assertEquals(
            "Expected exactly 23 recorded legacy UI database callers in ratcheting baseline (REQ-PRO-050.3)",
            23,
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

    @Test
    fun testRatchetingTripwire_commentStrippingPreventsFalsePositive() {
        val mockFileWithComments = """
            package com.atrainingtracker.trainingtracker.ui.fake
            // Refactored from WorkoutSummariesDatabaseManager
            /* In legacy version, this used DevicesDatabaseManager directly */
            class CleanUiScreen {
                val title = "Safe Title"
            }
        """.trimIndent()

        assertTrue(
            "Raw content without comment stripping would falsely match DB_PATTERN",
            DB_PATTERN.containsMatchIn(mockFileWithComments)
        )
        assertFalse(
            "Stripped content must NOT match DB_PATTERN when references exist only in comments (REQ-PRO-050.1)",
            DB_PATTERN.containsMatchIn(stripComments(mockFileWithComments))
        )
    }

    @Test
    fun testRatchetingTripwire_detectsForbiddenDatabaseUpwardImport() {
        val mockComposeImport = "import androidx.compose.ui.graphics.Color"
        val mockUiScreenImport = "import com.atrainingtracker.trainingtracker.ui.tracking.TrackingTabsScreen"

        assertTrue(
            "Tripwire must catch forbidden Compose import in database layer (REQ-PRO-050.5)",
            FORBIDDEN_DATABASE_UPWARD_PATTERNS.any { it.containsMatchIn(mockComposeImport) }
        )
        assertTrue(
            "Tripwire must catch forbidden UI screen import in database layer (REQ-PRO-050.5)",
            FORBIDDEN_DATABASE_UPWARD_PATTERNS.any { it.containsMatchIn(mockUiScreenImport) }
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

        // TST-PRO-052.3: WakeLock attribution & threading accuracy (ATT-6735, REQ-PRO-052.2)
        assertTrue(
            "Section 4.9 must attribute PARTIAL_WAKE_LOCK to TrackerService under Tracking Core (ATT-6735)",
            content.contains("`PARTIAL_WAKE_LOCK` (`aTrainingTracker:TrackingLock`)") &&
                content.contains("TrackerService.java:842-857")
        )
        assertTrue(
            "Section 4.9 must accurately attribute 1-Hz database writes to mScheduler / mDbExecutor (ATT-6735)",
            content.contains("mScheduler") && content.contains("mDbExecutor")
        )

        // TST-PRO-052.4: Explicit REQ-NFR-* Traceability Links (ATT-6735, REQ-NFR-003..014)
        for (i in 3..14) {
            val nfrId = String.format("REQ-NFR-%03d", i)
            assertTrue("Section 4.9 must explicitly link to $nfrId (ATT-6735)", content.contains(nfrId))
        }
    }
}
