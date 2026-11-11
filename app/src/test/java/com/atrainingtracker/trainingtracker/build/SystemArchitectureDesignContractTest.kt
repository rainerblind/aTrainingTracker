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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * System Architectural Design Contract Test (ASPICE SYS.3 BP1-BP6,
 * REQ-PRO-056, TST-PRO-056, ATT-6164, ATT-6741).
 *
 * Enforces:
 * 1. docs/system/system_architecture.md exists and is structurally complete (TST-PRO-056.1).
 * 2. 5 System Elements (SYS-EL-01..05) and 6 System Interfaces (SYS-IF-01..06) are formally specified.
 * 3. Dynamic sequence diagrams (Mermaid sequenceDiagram) model 1-Hz sampling and cloud sync,
 *    verifying TrackerService/mDbExecutor asynchronous workout finalization (TST-PRO-056.2).
 * 4. 100% System Requirements Allocation Matrix covers all 12 SYSREQ-* items across tracking and data exchange (TST-PRO-056.3).
 * 5. Full cross-check against docs/system_requirements.md asserting exact bidirectional set equality
 *    (declaredSysreqs == allocatedSysreqs) with zero orphan IDs (TST-PRO-056.3).
 * 6. Dynamic reflection verification of authentic production code symbols and resource files (TST-PRO-056.5).
 * 7. Compliance with ATT-6186: zero source-text reading of production files.
 */
class SystemArchitectureDesignContractTest {

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
     * Asserts docs/system/system_architecture.md exists and has the required metadata header (TST-PRO-056.1).
     */
    @Test
    fun testSystemArchitecture_fileExistsAndHasHeader() {
        val archFile = findRepoRootFile("docs/system/system_architecture.md")
        assertTrue("docs/system/system_architecture.md must exist (REQ-PRO-056.1)", archFile.exists())

        val content = archFile.readText()
        assertTrue("system_architecture.md must not be empty", content.isNotBlank())
        assertTrue("Must reference ASPICE SYS.3", content.contains("ASPICE SYS.3"))
        assertTrue("Must reference Parent Epic ATT-6142", content.contains("ATT-6142"))
        assertTrue("Must reference Requirement REQ-PRO-056", content.contains("REQ-PRO-056"))
        assertTrue("Must reference Test Spec TST-PRO-056", content.contains("TST-PRO-056"))
    }

    /**
     * Asserts all 5 system elements (SYS-EL-01..SYS-EL-05) are formally defined with boundaries (TST-PRO-056.1).
     */
    @Test
    fun testSystemArchitecture_definesAllSystemElements() {
        val content = findRepoRootFile("docs/system/system_architecture.md").readText()

        val expectedElements = listOf(
            "SYS-EL-01" to "Android Smartphone Host Application",
            "SYS-EL-02" to "BLE Peripheral Sports Sensors",
            "SYS-EL-03" to "ANT+ Wireless Sensors",
            "SYS-EL-04" to "Android OS Platform Services",
            "SYS-EL-05" to "External Cloud Platforms"
        )

        for ((id, description) in expectedElements) {
            assertTrue("system_architecture.md must define system element $id", content.contains(id))
            assertTrue("system_architecture.md must describe $description", content.contains(description))
        }
    }

    /**
     * Asserts all 6 system interfaces (SYS-IF-01..SYS-IF-06) are defined with protocol, payload, and timing bounds (TST-PRO-056.1).
     */
    @Test
    fun testSystemArchitecture_definesAllSystemInterfaces() {
        val content = findRepoRootFile("docs/system/system_architecture.md").readText()

        val expectedInterfaces = listOf(
            "SYS-IF-01",
            "SYS-IF-02",
            "SYS-IF-03",
            "SYS-IF-04",
            "SYS-IF-05",
            "SYS-IF-06"
        )

        for (ifaceId in expectedInterfaces) {
            assertTrue("system_architecture.md must define interface $ifaceId", content.contains(ifaceId))
            val idx = content.indexOf("### $ifaceId:")
            assertTrue("Interface $ifaceId must have dedicated section", idx >= 0)
            val sectionSnippet = content.substring(idx, (idx + 1200).coerceAtMost(content.length))
            assertTrue("Interface $ifaceId must define transport/protocol", sectionSnippet.contains("Transport") || sectionSnippet.contains("Protocol"))
            assertTrue("Interface $ifaceId must define payload schema", sectionSnippet.contains("Payload Schema"))
            assertTrue("Interface $ifaceId must define timing/rate bounds", sectionSnippet.contains("Timing") || sectionSnippet.contains("Rate"))
        }
    }

    /**
     * Asserts dynamic behavior sequence diagrams exist with valid Mermaid syntax (TST-PRO-056.2).
     */
    @Test
    fun testSystemArchitecture_definesDynamicBehaviorSequenceDiagrams() {
        val content = findRepoRootFile("docs/system/system_architecture.md").readText()

        assertTrue("Must declare Section 4 Dynamic Behavior Models", content.contains("## 4. Dynamic Behavior Models"))
        assertTrue("Must contain sequenceDiagram code block", content.contains("```mermaid\nsequenceDiagram"))

        // Check Live Telemetry sequence
        assertTrue("Must model 1-Hz Live Telemetry", content.contains("1-Hz Live Telemetry Acquisition & Sampling Loop"))
        assertTrue("Sequence must model BANALService interaction", content.contains("BANALService"))
        assertTrue("Sequence must model TrackerService 1-Hz tick", content.contains("TrackerService (1-Hz)"))
        assertTrue("Sequence must model WorkoutSamples.db persistence", content.contains("WorkoutSamples.db"))

        // Check Cloud Synchronization sequence
        assertTrue("Must model Cloud Synchronization Pipeline", content.contains("Post-Workout Cloud Synchronization & Backup Pipeline"))
        assertTrue("Sequence must model Strava upload", content.contains("Strava"))
        assertTrue("Sequence must model backup creation", content.contains(".attbackup"))
        assertTrue("Sequence must model ControlTrackingScreen", content.contains("ControlTrackingScreen"))
        assertTrue("Sequence must model TrackerService on mDbExecutor for finalization", content.contains("TrackerService (mDbExecutor)") || (content.contains("TrackerService") && content.contains("mDbExecutor")))
        assertTrue("Sequence must model ExportManager auto-export", content.contains("ExportManager"))
    }

    /**
     * Asserts 100% allocation of all 12 system requirements across both domains (TST-PRO-056.3).
     */
    @Test
    fun testSystemArchitecture_100PercentSysreqAllocationMatrix() {
        val content = findRepoRootFile("docs/system/system_architecture.md").readText()

        assertTrue("Must declare Section 5 Allocation Matrix", content.contains("## 5. System Requirements Allocation Matrix"))

        // 1. Domain 1: Tracking (SYSREQ-TRK-001..006)
        for (i in 1..6) {
            val trkId = "SYSREQ-TRK-%03d".format(i)
            assertTrue("Allocation matrix must include $trkId", content.contains(trkId))
        }

        // 2. Domain 2: Data Exchange (SYSREQ-DEX-001..006)
        for (i in 1..6) {
            val dexId = "SYSREQ-DEX-%03d".format(i)
            assertTrue("Allocation matrix must include $dexId", content.contains(dexId))
        }
    }

    /**
     * Asserts exact bidirectional parity: every SYSREQ in docs/system_requirements.md is 100% allocated,
     * and every allocated SYSREQ in docs/system/system_architecture.md exists in SYS.2 (zero orphan IDs) (TST-PRO-056.3).
     */
    @Test
    fun testSystemArchitecture_crossChecksSystemRequirementsSpec() {
        val sysreqContent = findRepoRootFile("docs/system_requirements.md").readText()
        val archContent = findRepoRootFile("docs/system/system_architecture.md").readText()

        // Extract all SYSREQ-* mentioned in docs/system_requirements.md
        val regex = Regex("SYSREQ-[A-Z]+-\\d{3}")
        val declaredSysreqs = regex.findAll(sysreqContent).map { it.value }.toSet()

        assertEquals("Must declare exactly 12 requirements in docs/system_requirements.md", 12, declaredSysreqs.size)

        // Extract all SYSREQ-* allocated in Section 5 of docs/system/system_architecture.md
        val section5Start = archContent.indexOf("## 5. System Requirements Allocation Matrix")
        assertTrue("Section 5 must exist in system_architecture.md", section5Start >= 0)
        val section5End = archContent.indexOf("## 6.", section5Start).let { if (it == -1) archContent.length else it }
        val section5Content = archContent.substring(section5Start, section5End)
        val allocatedSysreqs = regex.findAll(section5Content).map { it.value }.toSet()

        assertEquals(
            "System architecture Section 5 must allocate exactly the declared system requirements (no orphan or missing IDs)",
            declaredSysreqs,
            allocatedSysreqs
        )
    }

    /**
     * Asserts authentic code grounding: verifies existence of production classes via reflection
     * and XML resources via File.exists, while asserting absence of hallucinated symbols (TST-PRO-056.5).
     */
    @Test
    fun testSystemArchitecture_productionCodeGrounding() {
        val archContent = findRepoRootFile("docs/system/system_architecture.md").readText()

        // 1. Assert absence of 10 hallucinated symbols and outdated process name
        val hallucinatedSymbols = listOf(
            "HeartRateData",
            "PowerData",
            "BatteryLevelData",
            "AntPlusManager",
            "RadarThreatView",
            "SoundEngine",
            "FusedLocationProviderClient",
            "LocationCallback",
            "FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE",
            "backup_rules.xml",
            "com.atrainingtracker.trainingtracker"
        )
        for (symbol in hallucinatedSymbols) {
            assertTrue("system_architecture.md must not reference hallucinated symbol '$symbol'", !archContent.contains(symbol))
        }

        // 2. Dynamic reflection verification of grounded classes without reading source text (ATT-6186)
        val authenticClasses = listOf(
            "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarEdgeOverlayKt",
            "com.atrainingtracker.trainingtracker.audio.radar.RadarAlertManager",
            "com.atrainingtracker.trainingtracker.audio.radar.RadarAudioSynthesizer",
            "com.atrainingtracker.trainingtracker.routes.NavigationAudioAlertManager",
            "com.atrainingtracker.trainingtracker.audio.BikeBellAudioSynthesizer",
            "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_GoogleFused",
            "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_GPS",
            "com.atrainingtracker.trainingtracker.notifications.SensorBatteryNotificationManager"
        )
        for (className in authenticClasses) {
            try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                throw AssertionError("Required grounded production class $className must exist on classpath", e)
            }
        }

        // 3. Verify real backup configuration XML resource file exists
        val backupXml = findRepoRootFile(listOf("app", "src", "main", "res", "xml", "full_backup_content.xml").joinToString(File.separator))
        assertTrue("full_backup_content.xml must exist in res/xml/", backupXml.exists())

        // 4. Verify application process name
        assertTrue("system_architecture.md must document authentic application process com.atrainingtracker", archContent.contains("com.atrainingtracker"))
    }

    /**
     * Asserts cross-check parity with sysreq_data_exchange.md: all SYSREQ-DEX are allocated (TST-PRO-056.3).
     */
    @Test
    fun testSystemArchitecture_crossChecksDataExchangeSpec() {
        val dexContent = findRepoRootFile("docs/system/sysreq_data_exchange.md").readText()
        val archContent = findRepoRootFile("docs/system/system_architecture.md").readText()

        for (i in 1..6) {
            val dexId = "SYSREQ-DEX-%03d".format(i)
            assertTrue("sysreq_data_exchange.md must define $dexId", dexContent.contains(dexId))
            assertTrue("system_architecture.md must allocate $dexId", archContent.contains(dexId))
        }
    }

    /**
     * Asserts living documentation traceability to REQ-PRO-056 and TST-PRO-056.
     */
    @Test
    fun testSystemArchitecture_tracesToLivingRequirements() {
        val reqContent = findRepoRootFile("docs/requirements/req_process_stability.md").readText()
        val tstContent = findRepoRootFile("docs/tests/tst_process_stability.md").readText()

        assertTrue("Living docs must define REQ-PRO-056", reqContent.contains("REQ-PRO-056"))
        assertTrue("REQ-PRO-056 must cite system_architecture.md", reqContent.contains("system_architecture.md"))
        assertTrue("REQ-PRO-056 must cite SystemArchitectureDesignContractTest", reqContent.contains("SystemArchitectureDesignContractTest"))

        assertTrue("Living tests must define TST-PRO-056", tstContent.contains("TST-PRO-056"))
        assertTrue("TST-PRO-056 must verify REQ-PRO-056", tstContent.contains("REQ-PRO-056"))
    }
}
