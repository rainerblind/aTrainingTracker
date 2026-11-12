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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * System Verification Protocol Contract Test (ASPICE SYS.5 BP1-BP7,
 * REQ-PRO-069, TST-PRO-069, ATT-6167).
 *
 * Enforces:
 * 1. docs/system/sys_verification_protocol.md exists and contains required metadata header (TST-PRO-069.1).
 * 2. Complete Hardware Reference Matrix covering reference phones, BLE HRMs, Cycling Power, Radar, and GNSS/Baro (TST-PRO-069.2).
 * 3. Complete Environmental Operating Conditions Matrix across 5 scenarios (E1..E5) (TST-PRO-069.2).
 * 4. All 8 formal System Verification Test Cases (SYS-VRF-001 through SYS-VRF-008) are defined (TST-PRO-069.3).
 * 5. Full bidirectional system requirements coverage across SYSREQ-TRK-001..006 and SYSREQ-DEX-001..006 (TST-PRO-069.3).
 * 6. Objective Pass/Fail criteria, RF reconnection tolerances, and release sign-off record template (TST-PRO-069.4).
 * 7. docs/architecture.md Section 6.3 links to SYS.5 system verification.
 * 8. Compliance with ATT-6186: zero source-text reading of production files.
 */
class SystemVerificationProtocolContractTest {

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
     * Asserts docs/system/sys_verification_protocol.md exists and contains formal header and Mermaid diagrams (TST-PRO-069.1).
     */
    @Test
    fun testSysVerificationProtocol_fileExistsAndHasHeader() {
        val docFile = findRepoRootFile("docs/system/sys_verification_protocol.md")
        assertTrue("docs/system/sys_verification_protocol.md must exist (REQ-PRO-069.1)", docFile.exists())

        val content = docFile.readText()
        assertTrue("Specification must not be empty", content.isNotBlank())
        assertTrue("Must reference ASPICE SYS.5", content.contains("SYS.5"))
        assertTrue("Must reference REQ-PRO-069", content.contains("REQ-PRO-069"))
        assertTrue("Must reference TST-PRO-069", content.contains("TST-PRO-069"))
        assertTrue("Must include Mermaid architectural diagram", content.contains("```mermaid"))
        assertTrue("Must declare graph TD or LR diagram", content.contains("graph TD") || content.contains("graph LR"))
    }

    /**
     * Asserts Hardware Reference Matrix covers all specified sensor categories and reference phones (TST-PRO-069.2).
     */
    @Test
    fun testSysVerificationProtocol_coversHardwareReferenceMatrix() {
        val docFile = findRepoRootFile("docs/system/sys_verification_protocol.md")
        val content = docFile.readText()

        val expectedHardwareElements = listOf(
            "SYS-HW-01", // Primary Reference Smartphone (Google Pixel 8 / 9)
            "SYS-HW-02", // Secondary Reference Smartphone (Google Pixel 6 / 7)
            "SYS-HW-03", // Heart Rate Monitor (Polar H10 / Garmin HRM-Pro)
            "SYS-HW-04", // Cycling Power Meter (Favero Assioma / Stages)
            "SYS-HW-05", // Smart Trainer (Wahoo KICKR / Tacx Neo)
            "SYS-HW-06", // Cycling Radar & Tail Light (Garmin Varia RTL515)
            "SYS-HW-07"  // Barometer & Multi-GNSS
        )

        for (hwId in expectedHardwareElements) {
            assertTrue("sys_verification_protocol.md must define hardware element $hwId", content.contains(hwId))
        }

        assertTrue("Must reference Google Pixel reference family", content.contains("Pixel"))
        assertTrue("Must reference Polar H10 HRM", content.contains("Polar H10"))
        assertTrue("Must reference Favero Assioma power meter", content.contains("Favero Assioma"))
        assertTrue("Must reference Garmin Varia RTL515 radar", content.contains("Garmin Varia RTL515") || content.contains("RTL515"))
        assertTrue("Must reference Barometer / GNSS", content.contains("Barometer") || content.contains("barometric"))
    }

    /**
     * Asserts Environmental Operating Conditions Matrix covers all 5 scenarios (E1..E5) (TST-PRO-069.2).
     */
    @Test
    fun testSysVerificationProtocol_coversEnvironmentalScenarios() {
        val docFile = findRepoRootFile("docs/system/sys_verification_protocol.md")
        val content = docFile.readText()

        assertTrue("Must specify E1 Urban Canyon", content.contains("Urban Canyon") || content.contains("E1"))
        assertTrue("Must specify E2 Rural Road / High Speed", content.contains("Rural Road") || content.contains("E2"))
        assertTrue("Must specify E3 Alpine Climb", content.contains("Alpine Climb") || content.contains("E3"))
        assertTrue("Must specify E4 Indoor Smart Trainer", content.contains("Indoor Smart Trainer") || content.contains("Indoor Trainer") || content.contains("E4"))
        assertTrue("Must specify E5 Extended Endurance / Battery Longevity", content.contains("Extended Endurance") || content.contains("Battery Longevity") || content.contains("E5"))
    }

    /**
     * Asserts all 8 formal system verification test cases (SYS-VRF-001..008) are defined (TST-PRO-069.3).
     */
    @Test
    fun testSysVerificationProtocol_coversAllEightTestCases() {
        val docFile = findRepoRootFile("docs/system/sys_verification_protocol.md")
        val content = docFile.readText()

        val expectedTestCases = listOf(
            "SYS-VRF-001", // Multi-Sensor Ingestion
            "SYS-VRF-002", // RF Dropout Resilience & Reconnection Backoff
            "SYS-VRF-003", // Deterministic 1-Hz Sampling Loop
            "SYS-VRF-004", // Multi-Source GNSS & Barometer Fusion
            "SYS-VRF-005", // Foreground Execution & Battery Longevity
            "SYS-VRF-006", // Real-Time Cockpit & Radar Threat Alerts
            "SYS-VRF-007", // Multi-Format Telemetry Export & Ingestion Deduplication
            "SYS-VRF-008"  // Cloud Synchronization & Disaster Recovery
        )

        for (testId in expectedTestCases) {
            assertTrue("sys_verification_protocol.md must define test case $testId", content.contains(testId))
        }
    }

    /**
     * Asserts complete bidirectional coverage of all system requirements (SYSREQ-TRK-* and SYSREQ-DEX-*) (TST-PRO-069.3).
     */
    @Test
    fun testSysVerificationProtocol_tracesAllSystemRequirements() {
        val docFile = findRepoRootFile("docs/system/sys_verification_protocol.md")
        val content = docFile.readText()

        val allSysreqs = listOf(
            "SYSREQ-TRK-001", // Multi-Sensor Acquisition
            "SYSREQ-TRK-002", // Reconnection Backoff
            "SYSREQ-TRK-003", // 1-Hz Sampling Loop
            "SYSREQ-TRK-004", // GNSS Positioning
            "SYSREQ-TRK-005", // Foreground & Power Governance
            "SYSREQ-TRK-006", // Cockpit & Radar Sensory Alerts
            "SYSREQ-DEX-001", // Telemetry Export Interoperability
            "SYSREQ-DEX-002", // Ingestion Deduplication
            "SYSREQ-DEX-003", // Cloud Activity Sync
            "SYSREQ-DEX-004", // Cloud Storage Backup
            "SYSREQ-DEX-005", // Disaster Recovery Pipeline
            "SYSREQ-DEX-006"  // Credential Protection & Security Boundaries
        )

        for (sysreq in allSysreqs) {
            assertTrue("sys_verification_protocol.md must trace to system requirement $sysreq", content.contains(sysreq))
        }
    }

    /**
     * Asserts objective pass/fail criteria and release sign-off record template are defined (TST-PRO-069.4).
     */
    @Test
    fun testSysVerificationProtocol_specifiesCriteriaAndReleaseSignOffTemplate() {
        val docFile = findRepoRootFile("docs/system/sys_verification_protocol.md")
        val content = docFile.readText()

        assertTrue("Must specify Pass / Fail Criteria", content.contains("Pass/Fail Criteria") || content.contains("Pass / Fail Criteria"))
        assertTrue("Must specify sample drop tolerance (<= 1%)", content.contains("1.0%") || content.contains("1.0\\%") || content.contains("1%") || content.contains("1\\%"))
        assertTrue("Must specify battery drain threshold (<= 8%)", content.contains("8.0%") || content.contains("8.0\\%") || content.contains("8%") || content.contains("8\\%"))
        assertTrue("Must specify release verification record path under docs/releases/<tag>/", content.contains("docs/releases/<tag>/sys_verification_record.md"))
        assertTrue("Must provide formal sign-off section in record template", content.contains("Formal Sign-Off") || content.contains("Sign-Off Record"))
    }

    /**
     * Asserts docs/architecture.md Section 6.3 links to SYS.5 system verification protocol.
     */
    @Test
    fun testArchitecture_linksToSysVerificationProtocol() {
        val archFile = findRepoRootFile("docs/architecture.md")
        val content = archFile.readText()

        assertTrue("architecture.md must document SYS.5 verification in Section 6", content.contains("SYS.5"))
        assertTrue("architecture.md must reference sys_verification_protocol.md", content.contains("sys_verification_protocol.md"))
        assertTrue("architecture.md must reference REQ-PRO-069", content.contains("REQ-PRO-069"))
        assertTrue("architecture.md must reference TST-PRO-069", content.contains("TST-PRO-069"))
    }
}
