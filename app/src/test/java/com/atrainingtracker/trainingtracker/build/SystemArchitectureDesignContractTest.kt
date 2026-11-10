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
 * REQ-PRO-056, TST-PRO-056, ATT-6164).
 *
 * Enforces:
 * 1. docs/system/system_architecture.md exists and is structurally complete (TST-PRO-056.1).
 * 2. 5 System Elements (SYS-EL-01..05) and 6 System Interfaces (SYS-IF-01..06) are formally specified.
 * 3. Dynamic sequence diagrams (Mermaid sequenceDiagram) model 1-Hz sampling and cloud sync (TST-PRO-056.2).
 * 4. 100% System Requirements Allocation Matrix covers all SYSREQ-* items across acquisition, tracking,
 *    peripherals, environment, and data exchange (TST-PRO-056.3).
 * 5. Full cross-check against docs/system_requirements.md and docs/system/sysreq_data_exchange.md.
 * 6. Compliance with ATT-6186: zero source-text reading of production files.
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
    }

    /**
     * Asserts 100% allocation of all 24 system requirements across all 5 domains (TST-PRO-056.3).
     */
    @Test
    fun testSystemArchitecture_100PercentSysreqAllocationMatrix() {
        val content = findRepoRootFile("docs/system/system_architecture.md").readText()

        assertTrue("Must declare Section 5 Allocation Matrix", content.contains("## 5. System Requirements Allocation Matrix"))

        // 1. Domain 1: Acquisition (SYSREQ-ACQ-001..005)
        for (i in 1..5) {
            val acqId = "SYSREQ-ACQ-%03d".format(i)
            assertTrue("Allocation matrix must include $acqId", content.contains(acqId))
        }

        // 2. Domain 2: Tracking (SYSREQ-TRK-001..006)
        for (i in 1..6) {
            val trkId = "SYSREQ-TRK-%03d".format(i)
            assertTrue("Allocation matrix must include $trkId", content.contains(trkId))
        }

        // 3. Domain 3: Peripherals (SYSREQ-PER-001..005)
        for (i in 1..5) {
            val perId = "SYSREQ-PER-%03d".format(i)
            assertTrue("Allocation matrix must include $perId", content.contains(perId))
        }

        // 4. Domain 4: Environment (SYSREQ-ENV-001..003)
        for (i in 1..3) {
            val envId = "SYSREQ-ENV-%03d".format(i)
            assertTrue("Allocation matrix must include $envId", content.contains(envId))
        }

        // 5. Domain 5: Data Exchange (SYSREQ-DEX-001..006)
        for (i in 1..6) {
            val dexId = "SYSREQ-DEX-%03d".format(i)
            assertTrue("Allocation matrix must include $dexId", content.contains(dexId))
        }
    }

    /**
     * Asserts cross-check parity: every SYSREQ in docs/system_requirements.md is 100% allocated (TST-PRO-056.3).
     */
    @Test
    fun testSystemArchitecture_crossChecksSystemRequirementsSpec() {
        val sysreqContent = findRepoRootFile("docs/system_requirements.md").readText()
        val archContent = findRepoRootFile("docs/system/system_architecture.md").readText()

        // Extract all SYSREQ-TRK-xxx and SYSREQ-DEX-xxx mentioned in docs/system_requirements.md
        val regex = Regex("SYSREQ-[A-Z]+-\\d{3}")
        val declaredSysreqs = regex.findAll(sysreqContent).map { it.value }.toSet()

        assertTrue("Must detect at least 12 requirements in docs/system_requirements.md", declaredSysreqs.size >= 12)

        for (reqId in declaredSysreqs) {
            assertTrue("system_architecture.md must allocate $reqId from system_requirements.md", archContent.contains(reqId))
        }
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
