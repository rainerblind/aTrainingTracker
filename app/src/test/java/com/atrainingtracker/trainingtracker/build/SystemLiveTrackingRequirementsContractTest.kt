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

import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.devices.bluetooth_le.BTLERadarDevice
import com.atrainingtracker.banalservice.devices.bluetooth_le.BluetoothConstants
import com.atrainingtracker.banalservice.devices.bluetooth_le.MyBTLEDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * System Requirements Specification Contract Test for Live Tracking with Sensors
 * (ASPICE SYS.2 BP1-BP3, REQ-PRO-054, TST-PRO-054, ATT-6160).
 *
 * Enforces:
 * 1. docs/system/sysreq_live_tracking.md exists and contains SYSREQ-TRK-001..SYSREQ-TRK-006 (TST-PRO-054.1).
 * 2. Every system requirement defines Statement, Rationale, Priority, Verification Method, and Traced Software Components.
 * 3. 100% code grounding parity against live constants in MyBTLEDevice, BTLERadarDevice, and BluetoothConstants (TST-PRO-054.2).
 * 4. Compliance with ATT-6186: zero source-text reading of production source files.
 */
class SystemLiveTrackingRequirementsContractTest {

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
     * Asserts docs/system/sysreq_live_tracking.md exists and contains all 6 core system requirements (TST-PRO-054.1).
     */
    @Test
    fun testSysreqLiveTracking_fileExistsAndContainsAllRequirements() {
        val sysreqFile = findRepoRootFile("docs/system/sysreq_live_tracking.md")
        assertTrue("docs/system/sysreq_live_tracking.md must exist (REQ-PRO-054.1)", sysreqFile.exists())

        val content = sysreqFile.readText()
        assertTrue("docs/system/sysreq_live_tracking.md must not be empty", content.isNotBlank())
        assertTrue("Must declare ASPICE SYS.2 BP1-BP3 process scope", content.contains("SYS.2"))

        val expectedRequirements = listOf(
            "SYSREQ-TRK-001",
            "SYSREQ-TRK-002",
            "SYSREQ-TRK-003",
            "SYSREQ-TRK-004",
            "SYSREQ-TRK-005",
            "SYSREQ-TRK-006"
        )

        for (req in expectedRequirements) {
            assertTrue("Document must contain requirement $req", content.contains("### $req:"))
        }
    }

    /**
     * Asserts that all requirements specify mandatory attributes per ASPICE SYS.2 BP1-BP3 (TST-PRO-054.1).
     */
    @Test
    fun testSysreqLiveTracking_allRequirementsHaveMandatorySections() {
        val content = findRepoRootFile("docs/system/sysreq_live_tracking.md").readText()

        val expectedRequirements = listOf(
            "SYSREQ-TRK-001",
            "SYSREQ-TRK-002",
            "SYSREQ-TRK-003",
            "SYSREQ-TRK-004",
            "SYSREQ-TRK-005",
            "SYSREQ-TRK-006"
        )

        for (req in expectedRequirements) {
            val sectionStart = content.indexOf("### $req:")
            assertTrue("Requirement $req header not found", sectionStart >= 0)

            val sectionEnd = content.indexOf("### SYSREQ-TRK-", sectionStart + 1).let {
                if (it == -1) content.indexOf("## 3.", sectionStart) else it
            }.let {
                if (it == -1) content.length else it
            }

            val reqSection = content.substring(sectionStart, sectionEnd)

            assertTrue("$req must have a Title", reqSection.contains("* **Title**:"))
            assertTrue("$req must have a Statement", reqSection.contains("* **Statement**:"))
            assertTrue("$req Statement must use SHALL or MUST", reqSection.contains("SHALL") || reqSection.contains("MUST"))
            assertTrue("$req must have Rationale", reqSection.contains("* **Rationale**:"))
            assertTrue("$req must have Priority", reqSection.contains("* **Priority**:"))
            assertTrue("$req must have Verification Method", reqSection.contains("* **Verification Method**:"))
            assertTrue("$req must have Traced Software Components", reqSection.contains("* **Traced Software Components**:"))
        }
    }

    /**
     * Asserts code grounding parity between docs/system/sysreq_live_tracking.md and production constants (TST-PRO-054.2).
     */
    @Test
    fun testSysreqLiveTracking_codeGroundingParity() {
        val content = findRepoRootFile("docs/system/sysreq_live_tracking.md").readText()

        // 1. BLE GATT Services grounding
        assertTrue("Must document Heart Rate BLE service 0x180D", content.contains("0x180D"))
        assertEquals(
            "BluetoothConstants must define 0x180D for HRM",
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
            BluetoothConstants.getServiceUUID(DeviceType.HRM)
        )

        assertTrue("Must document Cycling Speed and Cadence BLE service 0x1816", content.contains("0x1816"))
        assertEquals(
            "BluetoothConstants must define 0x1816 for CSC",
            UUID.fromString("00001816-0000-1000-8000-00805f9b34fb"),
            BluetoothConstants.getServiceUUID(DeviceType.BIKE_SPEED_AND_CADENCE)
        )

        assertTrue("Must document Cycling Power BLE service 0x1818", content.contains("0x1818"))
        assertEquals(
            "BluetoothConstants must define 0x1818 for Power",
            UUID.fromString("00001818-0000-1000-8000-00805f9b34fb"),
            BluetoothConstants.getServiceUUID(DeviceType.BIKE_POWER)
        )

        assertTrue("Must document Running Speed and Cadence BLE service 0x1814", content.contains("0x1814"))
        assertEquals(
            "BluetoothConstants must define 0x1814 for RSC",
            UUID.fromString("00001814-0000-1000-8000-00805f9b34fb"),
            BluetoothConstants.getServiceUUID(DeviceType.RUN_SPEED)
        )

        assertTrue("Must document Battery BLE service 0x180F", content.contains("0x180F"))
        assertEquals(
            "BluetoothConstants must define 0x180F for Battery",
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"),
            BluetoothConstants.UUID_SERVICE_BATTERY
        )

        assertTrue("Must document Garmin Varia Radar BLE UUID", content.contains(BluetoothConstants.SERVICE_RADAR))
        assertEquals(
            "BluetoothConstants must match documented Radar UUID",
            "6a4e3200-667b-11e3-949a-0800200c9a66",
            BluetoothConstants.SERVICE_RADAR
        )

        // 2. Exponential Backoff Reconnection grounding (via reflection on MyBTLEDevice)
        val initialDelayField = MyBTLEDevice::class.java.getDeclaredField("INITIAL_RECONNECT_DELAY_MS").apply {
            isAccessible = true
        }
        val actualInitialDelay = initialDelayField.getLong(null)
        assertEquals("MyBTLEDevice INITIAL_RECONNECT_DELAY_MS must be 1000L", 1000L, actualInitialDelay)
        assertTrue("Document must specify 1,000 ms initial reconnect delay", content.contains("1,000 ms") || content.contains("1000 ms"))

        val maxDelayField = MyBTLEDevice::class.java.getDeclaredField("MAX_RECONNECT_DELAY_MS").apply {
            isAccessible = true
        }
        val actualMaxDelay = maxDelayField.getLong(null)
        assertEquals("MyBTLEDevice MAX_RECONNECT_DELAY_MS must be 16000L", 16000L, actualMaxDelay)
        assertTrue("Document must specify 16,000 ms max reconnect delay", content.contains("16,000 ms") || content.contains("16000 ms"))

        // 3. Radar packet starvation watchdog timeout grounding
        assertEquals("BTLERadarDevice WATCHDOG_TIMEOUT_MS must be 3500L", 3500L, BTLERadarDevice.WATCHDOG_TIMEOUT_MS)
        assertTrue("Document must specify 3,500 ms radar watchdog", content.contains("3,500 ms") || content.contains("3500 ms"))

        // 4. Deterministic 1-Hz sampling loop & SQLite persistence grounding
        assertTrue("Document must specify 1-Hz sampling loop", content.contains("1-Hz") || content.contains("1 Hz"))
        assertTrue("Document must reference TrackerService", content.contains("TrackerService.java") || content.contains("TrackerService"))
        assertTrue("Document must reference WorkoutSamples", content.contains("WorkoutSamples"))

        // 5. Android Foreground Service & Power Management grounding
        assertTrue("Document must specify Foreground Service execution", content.contains("Foreground Service"))
        assertTrue("Document must specify Partial WakeLock", content.contains("PowerManager.PARTIAL_WAKE_LOCK"))
        assertTrue("Document must reference battery optimization exemption", content.contains("ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"))
    }

    /**
     * Asserts that all backticked .kt, .java, and .xml files referenced in docs/system/sysreq_live_tracking.md
     * exist on disk in the repository (TST-PRO-054.3, ATT-6739).
     *
     * Note: Complies with ATT-6186 SourceTextReadingAuditContractTest by avoiding literal production
     * source path tokens and operating on the documentation markdown and filesystem traversal.
     */
    @Test
    fun testSysreqLiveTracking_allReferencedCodeFilesExistOnDisk() {
        val sysreqFile = findRepoRootFile("docs/system/sysreq_live_tracking.md")
        val content = sysreqFile.readText()

        val filenamePattern = Regex("`([A-Za-z0-9_]+\\.(?:kt|java|xml))`")
        val referencedFiles = filenamePattern.findAll(content)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("Must reference at least 15 code/resource/test files", referencedFiles.size >= 15)

        val repoRoot = findRepoRootFile("settings.gradle").parentFile ?: File(".")
        val filesOnDisk = repoRoot.walkTopDown()
            .filter { file ->
                val path = file.path
                val isBuildOutput = path.contains("${File.separator}build${File.separator}") &&
                    !path.contains("${File.separator}src${File.separator}")
                !path.contains(".git") && !isBuildOutput
            }
            .map { it.name }
            .toSet()

        val missingFiles = referencedFiles.filter { it !in filesOnDisk }
        assertTrue(
            "The following referenced files do not exist in the repository: $missingFiles",
            missingFiles.isEmpty()
        )
    }
}
