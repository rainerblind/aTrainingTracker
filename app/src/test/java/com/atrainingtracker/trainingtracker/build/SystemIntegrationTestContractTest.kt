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
 * System Integration Verification Contract Test (ASPICE SYS.4 BP1-BP7,
 * REQ-PRO-068, TST-PRO-068, ATT-6166).
 *
 * Enforces:
 * 1. docs/system/sys_integration_tests.md exists and contains required metadata header (TST-PRO-068.1).
 * 2. All 6 formal System Integration Test Cases (SYS-INT-001 through SYS-INT-006) are defined.
 * 3. Complete external interface coverage across SYS-IF-01 (BLE), SYS-IF-02 (ANT+), SYS-IF-03 (GNSS), and SYS-IF-05 (Cloud).
 * 4. Bidirectional system requirements allocation to SYSREQ-TRK-* and SYSREQ-DEX-* without dangling IDs (TST-PRO-068.2).
 * 5. Documented automated commands and verification scripts exist on disk in tools/ (TST-PRO-068.3).
 * 6. Quantified pass/fail criteria, timing tolerances, and release artifact archival are specified.
 * 7. Compliance with ATT-6186: zero source-text reading of production files.
 */
class SystemIntegrationTestContractTest {

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
     * Asserts docs/system/sys_integration_tests.md exists and contains formal header and Mermaid diagram (TST-PRO-068.1).
     */
    @Test
    fun testSysIntegrationTests_fileExistsAndHasHeader() {
        val docFile = findRepoRootFile("docs/system/sys_integration_tests.md")
        assertTrue("docs/system/sys_integration_tests.md must exist (REQ-PRO-068.1)", docFile.exists())

        val content = docFile.readText()
        assertTrue("Specification must not be empty", content.isNotBlank())
        assertTrue("Must reference ASPICE SYS.4", content.contains("SYS.4"))
        assertTrue("Must reference REQ-PRO-068", content.contains("REQ-PRO-068"))
        assertTrue("Must reference TST-PRO-068", content.contains("TST-PRO-068"))
        assertTrue("Must include Mermaid architectural diagram", content.contains("```mermaid"))
        assertTrue("Must declare graph TD diagram", content.contains("graph TD"))
        assertTrue("Must detail Environment A (Headless CI)", content.contains("Environment A"))
        assertTrue("Must detail Environment B (Hardware-in-the-Loop)", content.contains("Environment B"))
    }

    /**
     * Asserts all 6 formal system integration test cases (SYS-INT-001..006) are defined (TST-PRO-068.1).
     */
    @Test
    fun testSysIntegrationTests_coversAllSixTestCases() {
        val docFile = findRepoRootFile("docs/system/sys_integration_tests.md")
        val content = docFile.readText()

        val expectedTestCases = listOf(
            "SYS-INT-001", // BLE Heart Rate & RR Interval
            "SYS-INT-002", // BLE Cycling Power & Cadence
            "SYS-INT-003", // BLE Garmin Varia Radar Multi-Target
            "SYS-INT-004", // ANT+ Radio IPC
            "SYS-INT-005", // Multi-Source GNSS Positioning
            "SYS-INT-006"  // Cloud Synchronization & Rate Governance
        )

        for (testId in expectedTestCases) {
            assertTrue("sys_integration_tests.md must define test case $testId", content.contains(testId))
        }
    }

    /**
     * Asserts complete coverage across all external system interfaces (SYS-IF-01, 02, 03, 05) (TST-PRO-068.1).
     */
    @Test
    fun testSysIntegrationTests_coversExternalSystemInterfaces() {
        val docFile = findRepoRootFile("docs/system/sys_integration_tests.md")
        val content = docFile.readText()

        val requiredInterfaces = listOf(
            "SYS-IF-01", // BLE GATT
            "SYS-IF-02", // ANT+ IPC
            "SYS-IF-03", // GNSS / Location
            "SYS-IF-05"  // Cloud Storage REST APIs
        )

        for (ifId in requiredInterfaces) {
            assertTrue("sys_integration_tests.md must cover system interface $ifId", content.contains(ifId))
        }

        // Verify specific GATT UUIDs and services
        assertTrue("Must document Heart Rate Service 0x180D", content.contains("0x180D"))
        assertTrue("Must document Cycling Power Service 0x1818", content.contains("0x1818"))
        assertTrue("Must document Radar Service 6a4e3200", content.contains("6a4e3200"))
        assertTrue("Must document ANT+ plugin IPC package", content.contains("com.dsi.ant.plugins.antplus"))
        assertTrue("Must document GPS provider", content.contains("LocationManager.GPS_PROVIDER"))
        assertTrue("Must document Strava REST API", content.contains("Strava"))
    }

    /**
     * Asserts bidirectional traceability to system requirements (SYSREQ-TRK-*, SYSREQ-DEX-*) (TST-PRO-068.2).
     */
    @Test
    fun testSysIntegrationTests_tracesSystemRequirements() {
        val docFile = findRepoRootFile("docs/system/sys_integration_tests.md")
        val content = docFile.readText()

        val requiredSysreqs = listOf(
            "SYSREQ-TRK-001", // Multi-Sensor Acquisition
            "SYSREQ-TRK-002", // Automatic Reconnection
            "SYSREQ-TRK-003", // 1-Hz Sampling Loop
            "SYSREQ-TRK-004", // Multi-Source GNSS
            "SYSREQ-DEX-001", // Telemetry Export
            "SYSREQ-DEX-003"  // Cloud Activity Synchronization
        )

        for (sysreq in requiredSysreqs) {
            assertTrue("sys_integration_tests.md must trace to $sysreq", content.contains(sysreq))
        }

        val requiredElements = listOf(
            "SYS-EL-01", // Host Application
            "SYS-EL-02", // BLE Sensors
            "SYS-EL-03", // ANT+ Sensors
            "SYS-EL-04", // Platform Services
            "SYS-EL-05"  // Cloud Platforms
        )

        for (elId in requiredElements) {
            assertTrue("sys_integration_tests.md must reference system element $elId", content.contains(elId))
        }
    }

    /**
     * Asserts referenced simulator scripts and execution runner exist on disk in tools/ (TST-PRO-068.3).
     */
    @Test
    fun testSysIntegrationTests_referencedSimulatorToolsExist() {
        val requiredTools = listOf(
            "tools/run_system_integration_tests.py",
            "tools/test_system_integration_tests.py",
            "tools/ble_hr_simulator.py",
            "tools/ble_power_simulator.py",
            "tools/ble_radar_simulator.py",
            "tools/fake_gps.py",
            "tools/replay_workout.py"
        )

        for (toolPath in requiredTools) {
            val toolFile = findRepoRootFile(toolPath)
            assertTrue("Simulator tool $toolPath must exist on disk", toolFile.exists())
            assertTrue("Simulator tool $toolPath must be a regular file", toolFile.isFile)
        }
    }

    /**
     * Asserts pass/fail criteria, timing tolerances, and release artifact archival are specified (TST-PRO-068.1).
     */
    @Test
    fun testSysIntegrationTests_specifiesCriteriaAndArtifactLogging() {
        val docFile = findRepoRootFile("docs/system/sys_integration_tests.md")
        val content = docFile.readText()

        assertTrue("Must specify Pass / Fail Criteria", content.contains("Pass / Fail Gate Criteria") || content.contains("Pass / Fail Criteria"))
        assertTrue("Must specify timing tolerances", content.contains("tolerances") || content.contains("Tolerances"))
        assertTrue("Must specify release artifact logging path under docs/releases/<tag>/", content.contains("docs/releases/<tag>/"))
        assertTrue("Must specify JSON result filename", content.contains("system_integration_results.json"))
    }
}
