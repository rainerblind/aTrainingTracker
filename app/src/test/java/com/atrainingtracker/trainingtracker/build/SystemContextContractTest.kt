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

import com.atrainingtracker.banalservice.devices.bluetooth_le.BluetoothConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * System Context and External System Elements Contract Test (ASPICE SYS.2 BP1, SYS.3,
 * REQ-PRO-053, TST-PRO-053, ATT-6158).
 *
 * Enforces:
 * 1. docs/system/system_context.md exists and contains a valid Mermaid system context diagram (TST-PRO-053.1).
 * 2. All 5 external interface domains are formally documented (Sensors, Platform, Location/Maps, Cloud/Health, Decommissioned).
 * 3. 100% code grounding parity against live constants, build configurations, and external SDKs (TST-PRO-053.2).
 * 4. Operational assumptions (offline-first, auto-reconnect, async cloud sync) are explicitly stated.
 */
class SystemContextContractTest {

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
     * Asserts docs/system/system_context.md exists and contains the authoritative Mermaid context diagram (TST-PRO-053.1).
     */
    @Test
    fun testSystemContext_fileExistsAndContainsMermaidDiagram() {
        val contextFile = findRepoRootFile("docs/system/system_context.md")
        assertTrue("docs/system/system_context.md must exist (REQ-PRO-053.1)", contextFile.exists())

        val content = contextFile.readText()
        assertTrue("docs/system/system_context.md must not be empty", content.isNotBlank())
        assertTrue("Must contain Mermaid code fence", content.contains("```mermaid"))
        assertTrue("Must declare a graph TD context diagram", content.contains("graph TD"))
        assertTrue("Context diagram must include Athlete user node", content.contains("Athlete"))
        assertTrue("Context diagram must include application system boundary", content.contains("AppCore"))
        assertTrue("Context diagram must include SensorsDomain", content.contains("SensorsDomain"))
        assertTrue("Context diagram must include PlatformDomain", content.contains("PlatformDomain"))
        assertTrue("Context diagram must include LocationDomain", content.contains("LocationDomain"))
        assertTrue("Context diagram must include CloudDomain", content.contains("CloudDomain"))
        assertTrue("Context diagram must include DecommissionedDomain", content.contains("DecommissionedDomain"))
    }

    /**
     * Asserts docs/system/system_context.md covers all 5 mandatory external interface domains (TST-PRO-053.1).
     */
    @Test
    fun testSystemContext_coversAllFiveInterfaceDomains() {
        val content = findRepoRootFile("docs/system/system_context.md").readText()

        assertTrue("Must document Domain 1 (Sensors & Peripherals)", content.contains("### 3.1 Domain 1: Sensors & Peripherals"))
        assertTrue("Must document Domain 2 (Platform Frameworks)", content.contains("### 3.2 Domain 2: Platform Frameworks"))
        assertTrue("Must document Domain 3 (Location & Mapping Services)", content.contains("### 3.3 Domain 3: Location & Mapping Services"))
        assertTrue("Must document Domain 4 (Cloud Storage & Health Aggregators)", content.contains("### 3.4 Domain 4: Cloud Storage & Health Aggregators"))
        assertTrue("Must document Domain 5 (Decommissioned Legacy Interfaces)", content.contains("### 3.5 Domain 5: Decommissioned Legacy Interfaces"))
    }

    /**
     * Asserts code grounding parity between docs/system/system_context.md and actual application code/config (TST-PRO-053.2).
     */
    @Test
    fun testSystemContext_codeGroundingParity() {
        val content = findRepoRootFile("docs/system/system_context.md").readText()
        val buildGradleContent = findRepoRootFile("app/build.gradle").readText()

        // 1. BLE GATT Services grounding
        assertTrue("Must specify Heart Rate BLE service 0x180D", content.contains("0x180D"))
        assertTrue("Must specify Cycling Speed and Cadence BLE service 0x1816", content.contains("0x1816"))
        assertTrue("Must specify Cycling Power BLE service 0x1818", content.contains("0x1818"))
        assertTrue("Must specify Running Speed and Cadence BLE service 0x1814", content.contains("0x1814"))
        assertTrue("Must specify Battery BLE service 0x180F", content.contains("0x180F"))
        assertTrue("Must specify Garmin Varia Radar BLE service UUID", content.contains(BluetoothConstants.SERVICE_RADAR))
        assertEquals(
            "BluetoothConstants.SERVICE_RADAR must match documented Varia UUID",
            "6a4e3200-667b-11e3-949a-0800200c9a66",
            BluetoothConstants.SERVICE_RADAR
        )

        // 2. ANT+ IPC Services grounding
        assertTrue("Must specify ANT+ Plugins service package", content.contains("com.dsi.ant.plugins.antplus"))
        assertTrue("Must specify ANT Radio Service package", content.contains("com.dsi.ant.service.socket"))

        // 3. Android Platform SDK grounding
        assertTrue("Must specify minSdk 26", content.contains("minSdk") && content.contains("26"))
        assertTrue("Must specify targetSdk 37", content.contains("targetSdk") && content.contains("37"))
        assertTrue("app/build.gradle must define minSdk 26", buildGradleContent.contains("minSdkVersion 26") || buildGradleContent.contains("minSdk 26") || buildGradleContent.contains("minSdk = 26"))
        assertTrue("app/build.gradle must define targetSdk 37", buildGradleContent.contains("targetSdkVersion 37") || buildGradleContent.contains("targetSdk 37") || buildGradleContent.contains("targetSdk = 37"))

        // 4. Cloud SDK grounding
        assertTrue("Must ground Dropbox Core SDK version", content.contains("dropbox-core-sdk:8.0.2"))
        assertTrue("build.gradle must contain Dropbox SDK 8.0.2", buildGradleContent.contains("com.dropbox.core:dropbox-core-sdk:8.0.2"))

        assertTrue("Must ground PebbleKit SDK version", content.contains("pebblekit:4.0.1"))
        assertTrue("build.gradle must contain PebbleKit 4.0.1", buildGradleContent.contains("com.getpebble:pebblekit:4.0.1"))

        assertTrue("Must ground Health Connect client version", content.contains("connect-client:1.1.0-alpha11"))
        assertTrue("build.gradle must contain Health Connect 1.1.0-alpha11", buildGradleContent.contains("androidx.health.connect:connect-client:1.1.0-alpha11"))

        assertTrue("Must ground Google Play Services Location", content.contains("play-services-location"))
        assertTrue("Must ground Google Maps SDK", content.contains("play-services-maps"))

        // 5. Decommissioned Legacy Interfaces
        assertTrue("Must demarcate Runkeeper as PERMANENTLY DECOMMISSIONED", content.contains("Runkeeper") && content.contains("PERMANENTLY DECOMMISSIONED"))
        assertTrue("Must demarcate TrainingPeaks as PERMANENTLY DECOMMISSIONED", content.contains("TrainingPeaks") && content.contains("PERMANENTLY DECOMMISSIONED"))
    }

    /**
     * Asserts that operational assumptions and environmental constraints are documented (REQ-PRO-053.1).
     */
    @Test
    fun testSystemContext_documentsOperationalAssumptions() {
        val content = findRepoRootFile("docs/system/system_context.md").readText()

        assertTrue("Must contain Section 4 (Operational Assumptions)", content.contains("## 4. Operational Assumptions & Environmental Constraints"))
        assertTrue("Must document Offline-First Resilience", content.contains("Offline-First Resilience"))
        assertTrue("Must document Sensor Disconnection & Auto-Failover", content.contains("Sensor Disconnection & Auto-Failover"))
        assertTrue("Must document Asynchronous Cloud Synchronization", content.contains("Asynchronous Cloud Synchronization"))
    }
}
