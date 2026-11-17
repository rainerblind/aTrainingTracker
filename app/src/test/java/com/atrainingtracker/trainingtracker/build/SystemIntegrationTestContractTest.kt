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
 * Developer Sensor Simulator Self-Test & Interface Spec Contract Test
 * (REQ-PRO-068, TST-PRO-068, REQ-PRO-112, TST-PRO-112, ATT-6166, ATT-8157).
 *
 * Enforces:
 * 1. docs/system/sys_integration_tests.md exists, documents external interfaces (SYS-IF-01, 02, 03, 05),
 *    and explicitly discloses simulator-only scope vs INT-SWE-01..08 / AppSmokeAndroidTest / manual SYS.5.
 * 2. Simulator self-test cases (SIM-HR-001, SIM-PWR-001, SIM-RDR-001, SIM-GPS-001, SIM-RPL-001) are documented.
 * 3. Bidirectional system requirements allocation to SYSREQ-TRK-* and SYSREQ-DEX-* without dangling IDs.
 * 4. Renamed tools/run_simulator_self_tests.py and tools/test_simulator_self_tests.py exist on disk,
 *    while legacy tools/run_system_integration_tests.py is removed.
 * 5. Zero release-gate claims on simulator JSON in sys_integration_tests.md.
 */
class SystemIntegrationTestContractTest {

    private fun findRepoRootFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    /**
     * Asserts docs/system/sys_integration_tests.md exists, references REQs/TSTs, and discloses simulator-only scope (TST-PRO-068.1).
     */
    @Test
    fun testSysIntegrationTests_fileExistsAndDisclosesSimulatorScope() {
        val docFile = findRepoRootFile("docs/system/sys_integration_tests.md")
        assertTrue("docs/system/sys_integration_tests.md must exist (REQ-PRO-068.1)", docFile.exists())

        val content = docFile.readText()
        assertTrue("Specification must not be empty", content.isNotBlank())
        assertTrue("Must reference REQ-PRO-068", content.contains("REQ-PRO-068"))
        assertTrue("Must reference TST-PRO-068", content.contains("TST-PRO-068"))
        assertTrue("Must reference REQ-PRO-112", content.contains("REQ-PRO-112"))
        assertTrue("Must include Mermaid architectural diagram", content.contains("```mermaid"))
        assertTrue("Must declare graph TD diagram", content.contains("graph TD"))
        assertTrue("Must detail Environment A", content.contains("Environment A"))
        assertTrue("Must detail Environment B", content.contains("Environment B"))
        assertTrue("Must explicitly state simulator tests do NOT execute Android APK",
            content.contains("NOT") && content.contains("Android APK"))
        assertTrue("Must reference real app integration INT-SWE-01..08", content.contains("INT-SWE-01..08"))
    }

    /**
     * Asserts simulator self-test IDs are documented cleanly (TST-PRO-068.1 / TST-PRO-112.1).
     */
    @Test
    fun testSysIntegrationTests_documentsSimulatorSelfTests() {
        val docFile = findRepoRootFile("docs/system/sys_integration_tests.md")
        val content = docFile.readText()

        val expectedSelfTests = listOf(
            "SIM-HR-001",
            "SIM-PWR-001",
            "SIM-RDR-001",
            "SIM-GPS-001",
            "SIM-RPL-001"
        )

        for (testId in expectedSelfTests) {
            assertTrue("sys_integration_tests.md must define simulator self-test $testId", content.contains(testId))
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
            "SYSREQ-TRK-001",
            "SYSREQ-TRK-002",
            "SYSREQ-TRK-003",
            "SYSREQ-TRK-004",
            "SYSREQ-DEX-001",
            "SYSREQ-DEX-003"
        )

        for (sysreq in requiredSysreqs) {
            assertTrue("sys_integration_tests.md must trace to $sysreq", content.contains(sysreq))
        }

        val requiredElements = listOf(
            "SYS-EL-01",
            "SYS-EL-02",
            "SYS-EL-03",
            "SYS-EL-04",
            "SYS-EL-05"
        )

        for (elId in requiredElements) {
            assertTrue("sys_integration_tests.md must reference system element $elId", content.contains(elId))
        }
    }

    /**
     * Asserts renamed simulator self-test scripts exist and legacy fake integration runner is gone (TST-PRO-068.3 / TST-PRO-112.1).
     */
    @Test
    fun testSysIntegrationTests_renamedSimulatorToolsExistAndLegacyRemoved() {
        val requiredTools = listOf(
            "tools/run_simulator_self_tests.py",
            "tools/test_simulator_self_tests.py",
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

        assertFalse(
            "Legacy tools/run_system_integration_tests.py must be removed (REQ-PRO-112.1)",
            findRepoRootFile("tools/run_system_integration_tests.py").exists()
        )
        assertFalse(
            "Legacy tools/test_system_integration_tests.py must be removed (REQ-PRO-112.1)",
            findRepoRootFile("tools/test_system_integration_tests.py").exists()
        )
    }

    /**
     * Asserts sys_integration_tests.md enforces non-gating simulator classification (TST-PRO-112.1 / TST-PRO-112.3).
     */
    @Test
    fun testSysIntegrationTests_doesNotClaimReleaseGating() {
        val docFile = findRepoRootFile("docs/system/sys_integration_tests.md")
        val content = docFile.readText()

        assertTrue("Must state simulator self-tests are never a blocking release gate",
            content.contains("never") && content.contains("blocking release gate"))
        assertFalse("Must not reference retired run_system_integration_tests.py",
            content.contains("run_system_integration_tests.py"))
        assertFalse("Must not mandate docs/releases/<tag>/system_integration_results.json",
            content.contains("docs/releases/<tag>/system_integration_results.json"))
    }
}
