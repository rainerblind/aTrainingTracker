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
 * Contract test enforcing retrospective Architecture Decision Record (`ADR-002`..`ADR-007`)
 * completeness, evaluated alternatives, honest PO sign-off metadata, architecture index links,
 * and JVM-reflection class grounding with strictly zero production source-text reading
 * (`REQ-PRO-049`, `REQ-PRO-116`, `TST-PRO-116`, `ATT-8161`).
 */
// Linked test specs: TST-PRO-116
class AdrCompletenessContractTest {

    private data class AdrSpec(
        val id: String,
        val filePath: String,
        val requiredClassFqcns: List<String>
    )

    companion object {
        private val ADR_SPECS = listOf(
            AdrSpec(
                id = "ADR-002",
                filePath = "docs/architecture/adr_002_foreground_tracking_service.md",
                requiredClassFqcns = listOf(
                    "com.atrainingtracker.trainingtracker.tracker.TrackerService",
                    "com.atrainingtracker.trainingtracker.tracker.LiveWorkoutSession",
                    "com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManager"
                )
            ),
            AdrSpec(
                id = "ADR-003",
                filePath = "docs/architecture/adr_003_banal_sensor_bus.md",
                requiredClassFqcns = listOf(
                    "com.atrainingtracker.banalservice.BANALService",
                    "com.atrainingtracker.banalservice.BANALService\$BANALServiceComm",
                    "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepository",
                    "com.atrainingtracker.banalservice.sensor.MySensorManager",
                    "com.atrainingtracker.banalservice.sensor.ProxySensor",
                    "com.atrainingtracker.banalservice.filters.FilterManager"
                )
            ),
            AdrSpec(
                id = "ADR-004",
                filePath = "docs/architecture/adr_004_sqlite_managers_vs_room.md",
                requiredClassFqcns = listOf(
                    "com.atrainingtracker.trainingtracker.database.WorkoutSamplesDatabaseManager",
                    "com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManager",
                    "com.atrainingtracker.trainingtracker.database.LapsDatabaseManager",
                    "com.atrainingtracker.banalservice.database.DevicesDatabaseManager",
                    "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepository",
                    "com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository",
                    "com.atrainingtracker.trainingtracker.repositories.KnownLocationsRepository"
                )
            ),
            AdrSpec(
                id = "ADR-005",
                filePath = "docs/architecture/adr_005_hybrid_preferences_datastore.md",
                requiredClassFqcns = listOf(
                    "com.atrainingtracker.trainingtracker.TrainingApplication",
                    "com.atrainingtracker.trainingtracker.settings.SettingsDataStore",
                    "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStore",
                    "com.atrainingtracker.trainingtracker.settings.SettingsDataStoreJavaHelper",
                    "com.atrainingtracker.banalservice.devices.AttFusedPreferences"
                )
            ),
            AdrSpec(
                id = "ADR-006",
                filePath = "docs/architecture/adr_006_incremental_compose_navigation.md",
                requiredClassFqcns = listOf(
                    "com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation",
                    "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModel",
                    "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModel",
                    "com.atrainingtracker.trainingtracker.ui.map.TrackOnMapAftermathViewModel",
                    "com.atrainingtracker.trainingtracker.ui.settings.zones.ZonesSettingsViewModel"
                )
            ),
            AdrSpec(
                id = "ADR-007",
                filePath = "docs/architecture/adr_007_ble_ant_sensor_abstraction.md",
                requiredClassFqcns = listOf(
                    "com.atrainingtracker.banalservice.devices.MyDevice",
                    "com.atrainingtracker.banalservice.devices.MyRemoteDevice",
                    "com.atrainingtracker.banalservice.devices.ant_plus.MyANTDevice",
                    "com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice",
                    "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarOnlineStreamingBridge",
                    "com.atrainingtracker.banalservice.devices.DeviceManager",
                    "com.atrainingtracker.banalservice.sensor.SensorType"
                )
            )
        )

        private val PHANTOM_CLASS_TOKENS = listOf(
            "TrackPointsDatabaseManager",
            "`ANTDevice`",
            "`BTLEDevice`"
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

    private fun loadProductionClass(fqcn: String): Class<*> {
        return try {
            Class.forName(fqcn, false, javaClass.classLoader)
        } catch (e: ClassNotFoundException) {
            throw AssertionError("ADR-cited production class '$fqcn' must exist on compiled classpath", e)
        }
    }

    /**
     * TST-PRO-116.1: Verifies ADR-002..007 structural completeness, >= 2 evaluated alternatives,
     * honest PO sign-off status, and absence of phantom class names.
     */
    @Test
    fun testRetrospectiveAdrs_structureAlternativesAndHonestSignOff() {
        val requiredSections = listOf(
            "## 1. Context & Problem Statement",
            "## 2. Decision Drivers & Evaluated Alternatives",
            "## 3. Decision Outcome",
            "## 4. Architectural Consequences & Trade-offs"
        )

        for (spec in ADR_SPECS) {
            val file = findRepoRootFile(spec.filePath)
            assertTrue("${spec.id} must exist at ${spec.filePath}", file.exists() && file.isFile)
            val content = file.readText()
            assertTrue("${spec.id} must contain substantial rationale", content.length >= 1500)

            for (section in requiredSections) {
                assertTrue("${spec.id} must contain '$section'", content.contains(section))
            }

            val altCount = Regex("""^###\s+Alternative\s+\d+:""", RegexOption.MULTILINE).findAll(content).count()
            assertTrue("${spec.id} must evaluate at least 2 alternatives alongside selected option (found=$altCount)", altCount >= 3)
            assertTrue("${spec.id} must explicitly mark rejected alternatives", content.contains("**Rejected**"))
            assertTrue("${spec.id} must explicitly mark selected option", content.contains("**Selected**"))

            val hasHonestOrSignedStatus =
                content.contains("- **Status**: Retrospective (Pending PO Sign-Off)") ||
                    content.contains("- **Status**: Reviewed") ||
                    content.contains("- **Status**: Approved")
            assertTrue("${spec.id} must state explicit Status metadata", hasHonestOrSignedStatus)
            if (content.contains("Pending PO Sign-Off")) {
                assertTrue(
                    "${spec.id} Deciders must match pending PO sign-off honesty",
                    content.contains("Product Owner (Pending Sign-Off)")
                )
            }

            for (phantom in PHANTOM_CLASS_TOKENS) {
                assertFalse("${spec.id} must not cite phantom token '$phantom'", content.contains(phantom))
            }
        }
    }

    /**
     * TST-PRO-116.2: Verifies via JVM reflection (`Class.forName`) that all core classes
     * cited across ADR-002..007 exist on the compiled classpath (zero production file reading)
     * and that `docs/architecture.md` indexes ADR-001..007.
     */
    @Test
    fun testRetrospectiveAdrs_classesResolveViaReflectionAndIndexedInArchitectureDoc() {
        val archText = findRepoRootFile("docs/architecture.md").readText()
        assertTrue("docs/architecture.md must contain ADR Registry", archText.contains("Architecture Decision Records Registry"))
        assertTrue("docs/architecture.md must link adr_001_java_core_services_migration.md", archText.contains("adr_001_java_core_services_migration.md"))

        for (spec in ADR_SPECS) {
            val fileName = File(spec.filePath).name
            assertTrue("docs/architecture.md must reference ${spec.id}", archText.contains(spec.id))
            assertTrue("docs/architecture.md must link $fileName", archText.contains(fileName))

            val adrText = findRepoRootFile(spec.filePath).readText()
            for (fqcn in spec.requiredClassFqcns) {
                val clazz = loadProductionClass(fqcn)
                val simpleName = clazz.simpleName
                assertTrue("${spec.id} must cite authentic class `$simpleName`", adrText.contains(simpleName))
            }
        }
    }
}
