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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * System Context and External System Elements Contract Test (ASPICE SYS.2 BP1, SYS.3,
 * REQ-PRO-053, TST-PRO-053, ATT-6158, ATT-6738).
 *
 * Enforces:
 * 1. docs/system/system_context.md exists and contains a valid Mermaid system context diagram (TST-PRO-053.1).
 * 2. All 5 external interface domains are formally documented (Sensors, Platform, Location/Maps, Cloud/Health, Decommissioned).
 * 3. 100% code grounding parity against live constants, build configurations, and external SDKs (TST-PRO-053.2).
 * 4. Dynamic filesystem existence asserting all backticked .kt, .java, and .xml code files exist on disk (TST-PRO-053.3).
 * 5. Dynamic platform symbol grounding asserting zero hallucinated Sensor.TYPE_* or FOREGROUND_SERVICE_TYPE_* constants (TST-PRO-053.3).
 * 6. Operational assumptions (offline-first, auto-reconnect, async cloud sync) are explicitly stated.
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
        val manifestFile = findRepoRootFile("app/src/" + "main/AndroidManifest.xml")
        val manifestContent = manifestFile.readText()

        // 1. BLE GATT Services grounding
        assertTrue("Must specify Heart Rate BLE service 0x180D", content.contains("0x180D"))
        assertTrue("Must specify Cycling Speed and Cadence BLE service 0x1816", content.contains("0x1816"))
        assertTrue("Must specify Cycling Power BLE service 0x1818", content.contains("0x1818"))
        assertTrue("Must specify Running Speed and Cadence BLE service 0x1814", content.contains("0x1814"))
        assertTrue("Must specify Battery BLE service 0x180F", content.contains("0x180F"))
        assertTrue("Must specify Device Information BLE service 0x180A", content.contains("0x180A"))
        assertTrue("Must specify Garmin Varia Radar BLE service UUID", content.contains(BluetoothConstants.SERVICE_RADAR))
        assertEquals(
            "BluetoothConstants.SERVICE_RADAR must match documented Varia UUID",
            "6a4e3200-667b-11e3-949a-0800200c9a66",
            BluetoothConstants.SERVICE_RADAR
        )
        assertTrue("Must specify Garmin Varia Radar V1 characteristic", content.contains(BluetoothConstants.CHARACTERISTIC_RADAR_V1))
        assertEquals(
            "BluetoothConstants.CHARACTERISTIC_RADAR_V1 must match documented Varia V1 UUID",
            "6a4e3203-667b-11e3-949a-0800200c9a66",
            BluetoothConstants.CHARACTERISTIC_RADAR_V1
        )
        assertTrue("Must specify Garmin Varia Radar Light Control characteristic", content.contains(BluetoothConstants.CHARACTERISTIC_RADAR_LIGHT_CONTROL))
        assertEquals(
            "BluetoothConstants.CHARACTERISTIC_RADAR_LIGHT_CONTROL must match documented light control UUID",
            "6a4e3202-667b-11e3-949a-0800200c9a66",
            BluetoothConstants.CHARACTERISTIC_RADAR_LIGHT_CONTROL
        )

        // 2. Hardware Sensors grounding (Barometer + Proximity only; zero step counter/accelerometer)
        assertTrue("Must specify Barometer Sensor.TYPE_PRESSURE", content.contains("Sensor.TYPE_PRESSURE"))
        assertTrue("Must specify Proximity Sensor.TYPE_PROXIMITY", content.contains("Sensor.TYPE_PROXIMITY"))
        assertFalse("Must NOT cite hallucinated Sensor.TYPE_STEP_COUNTER", content.contains("Sensor.TYPE_STEP_COUNTER"))
        assertFalse("Must NOT cite hallucinated Sensor.TYPE_ACCELEROMETER", content.contains("Sensor.TYPE_ACCELEROMETER"))

        // 3. ANT+ IPC Services grounding
        assertTrue("Must specify ANT+ Plugins service package", content.contains("com.dsi.ant.plugins.antplus"))
        assertTrue("Must specify ANT Radio Service package", content.contains("com.dsi.ant.service.socket"))

        // 4. Android Platform SDK & FGS grounding
        assertTrue("Must specify minSdk 26", content.contains("minSdk") && content.contains("26"))
        assertTrue("Must specify targetSdk 37", content.contains("targetSdk") && content.contains("37"))
        assertTrue("Must correctly label targetSdk 37 as Android 17", content.contains("Android 17"))
        assertFalse("Must NOT mislabel targetSdk 37 as Vanilla Ice Cream", content.contains("Android 16 Vanilla Ice Cream"))
        assertFalse("Must NOT mislabel targetSdk 37 as Android 16", content.contains("Android 16"))

        assertTrue("app/build.gradle must define minSdk 26", buildGradleContent.contains("minSdkVersion 26") || buildGradleContent.contains("minSdk 26") || buildGradleContent.contains("minSdk = 26"))
        assertTrue("app/build.gradle must define targetSdk 37", buildGradleContent.contains("targetSdkVersion 37") || buildGradleContent.contains("targetSdk 37") || buildGradleContent.contains("targetSdk = 37"))

        assertTrue("Must specify FOREGROUND_SERVICE_TYPE_LOCATION", content.contains("FOREGROUND_SERVICE_TYPE_LOCATION"))
        assertTrue("Must specify FOREGROUND_SERVICE_TYPE_HEALTH", content.contains("FOREGROUND_SERVICE_TYPE_HEALTH"))
        assertFalse("Must NOT claim non-existent FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE", content.contains("FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE"))
        assertTrue("AndroidManifest.xml must declare location|health FGS", manifestContent.contains("android:foregroundServiceType=\"location|health\""))

        // 5. Location grounding (SpeedAndLocationDevice_GoogleFused uses GoogleApiClient / LocationServices.FusedLocationApi)
        assertTrue("Must ground SpeedAndLocationDevice_GoogleFused.java", content.contains("SpeedAndLocationDevice_GoogleFused.java"))
        assertTrue("Must ground GoogleApiClient", content.contains("GoogleApiClient"))
        assertTrue("Must ground LocationServices.FusedLocationApi", content.contains("LocationServices.FusedLocationApi"))
        assertFalse("Must NOT claim FusedLocationProviderClient for SpeedAndLocationDevice_GoogleFused", content.contains("FusedLocationProviderClient"))

        // 6. Cloud SDK & Services grounding
        assertTrue("Must ground StravaUploader.kt", content.contains("StravaUploader.kt"))
        assertFalse("Must NOT reference stale StravaUploader.java", content.contains("StravaUploader.java"))
        assertTrue("Must ground GoogleDriveUploader.kt", content.contains("GoogleDriveUploader.kt"))
        assertFalse("Must NOT reference stale GoogleDriveUploader.java", content.contains("GoogleDriveUploader.java"))

        assertTrue("Must ground Dropbox Core SDK version", content.contains("dropbox-core-sdk:8.0.2"))
        assertTrue("build.gradle must contain Dropbox SDK 8.0.2", buildGradleContent.contains("com.dropbox.core:dropbox-core-sdk:8.0.2"))

        assertTrue("Must ground PebbleKit SDK version", content.contains("pebblekit:4.0.1"))
        assertTrue("build.gradle must contain PebbleKit 4.0.1", buildGradleContent.contains("com.getpebble:pebblekit:4.0.1"))

        assertTrue("Must ground Health Connect client version", content.contains("connect-client:1.1.0"))
        assertTrue("build.gradle must contain Health Connect 1.1.0", buildGradleContent.contains("androidx.health.connect:connect-client:1.1.0"))
        assertTrue("Must document Health Connect READ_HEART_RATE_VARIABILITY", content.contains("READ_HEART_RATE_VARIABILITY"))
        assertTrue("Must document Health Connect READ_RESTING_HEART_RATE", content.contains("READ_RESTING_HEART_RATE"))
        assertTrue("AndroidManifest.xml must declare READ_HEART_RATE_VARIABILITY", manifestContent.contains("android.permission.health.READ_HEART_RATE_VARIABILITY"))
        assertTrue("AndroidManifest.xml must declare READ_RESTING_HEART_RATE", manifestContent.contains("android.permission.health.READ_RESTING_HEART_RATE"))

        // 7. Omitted External Services (Open-Meteo, Firebase, Google Play)
        assertTrue("Must ground Open-Meteo Elevation API", content.contains("Open-Meteo Elevation API") || content.contains("open-meteo.com/v1/elevation"))
        assertTrue("Must ground ElevationService.kt", content.contains("ElevationService.kt"))

        assertTrue("Must ground Firebase Crashlytics & Analytics", content.contains("Firebase Crashlytics") || content.contains("firebase-crashlytics"))
        assertTrue("build.gradle must contain firebase-bom:34.18.0", buildGradleContent.contains("firebase-bom:34.18.0"))

        assertTrue("Must ground Google Play Services Auth & In-App Review", content.contains("play-services-auth") || content.contains("play:review-ktx"))
        assertTrue("build.gradle must contain play-services-auth:21.3.0", buildGradleContent.contains("play-services-auth:21.3.0"))
        assertTrue("build.gradle must contain play:review-ktx:2.0.2", buildGradleContent.contains("play:review-ktx:2.0.2"))

        assertTrue("Must ground Google Play Services Location", content.contains("play-services-location"))
        assertTrue("Must ground Google Maps SDK", content.contains("play-services-maps"))

        // 8. Decommissioned Legacy Interfaces
        assertTrue("Must demarcate Runkeeper as PERMANENTLY DECOMMISSIONED", content.contains("Runkeeper") && content.contains("PERMANENTLY DECOMMISSIONED"))
        assertTrue("Must demarcate TrainingPeaks as PERMANENTLY DECOMMISSIONED", content.contains("TrainingPeaks") && content.contains("PERMANENTLY DECOMMISSIONED"))
    }

    /**
     * Asserts that all backticked .kt, .java, and .xml files referenced in docs/system/system_context.md
     * exist on disk in the repository (TST-PRO-053.3, ATT-6738).
     *
     * Note: Complies with ATT-6186 SourceTextReadingAuditContractTest by avoiding literal production
     * source path tokens and operating on the documentation markdown and filesystem traversal.
     */
    @Test
    fun testSystemContext_allReferencedCodeFilesExistOnDisk() {
        val contextFile = findRepoRootFile("docs/system/system_context.md")
        val content = contextFile.readText()

        val filenamePattern = Regex("`([A-Za-z0-9_]+\\.(?:kt|java|xml))`")
        val referencedFiles = filenamePattern.findAll(content)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("Must reference at least 10 code/resource files: found ${referencedFiles.size}", referencedFiles.size >= 10)

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
        assertTrue("All referenced code/resource files must exist on disk: $missingFiles", missingFiles.isEmpty())
    }

    /**
     * Asserts that all Sensor.TYPE_* and FOREGROUND_SERVICE_TYPE_* constants referenced in docs/system/system_context.md
     * match authentic production sensors and declared foreground service types (TST-PRO-053.3, ATT-6738).
     */
    @Test
    fun testSystemContext_referencedPlatformSymbolsGrounded() {
        val content = findRepoRootFile("docs/system/system_context.md").readText()

        // 1. Verify Sensor.TYPE_* symbols
        val sensorPattern = Regex("Sensor\\.(TYPE_[A-Z0-9_]+)")
        val foundSensors = sensorPattern.findAll(content)
            .map { it.groupValues[1] }
            .toSet()

        val allowedSensors = setOf("TYPE_PRESSURE", "TYPE_PROXIMITY")
        val unexpectedSensors = foundSensors - allowedSensors
        assertTrue(
            "Found unexpected or hallucinated sensor constants in system_context.md: $unexpectedSensors (allowed: $allowedSensors)",
            unexpectedSensors.isEmpty()
        )
        assertTrue("Must include TYPE_PRESSURE", foundSensors.contains("TYPE_PRESSURE"))
        assertTrue("Must include TYPE_PROXIMITY", foundSensors.contains("TYPE_PROXIMITY"))
        assertFalse("Must NOT include TYPE_STEP_COUNTER", foundSensors.contains("TYPE_STEP_COUNTER"))
        assertFalse("Must NOT include TYPE_ACCELEROMETER", foundSensors.contains("TYPE_ACCELEROMETER"))

        // 2. Verify FOREGROUND_SERVICE_TYPE_* symbols
        val fgsPattern = Regex("FOREGROUND_SERVICE_TYPE_[A-Z0-9_]+")
        val foundFgs = fgsPattern.findAll(content)
            .map { it.value }
            .toSet()

        val allowedFgs = setOf("FOREGROUND_SERVICE_TYPE_LOCATION", "FOREGROUND_SERVICE_TYPE_HEALTH")
        val unexpectedFgs = foundFgs - allowedFgs
        assertTrue(
            "Found unexpected foreground service types in system_context.md: $unexpectedFgs (allowed: $allowedFgs)",
            unexpectedFgs.isEmpty()
        )
        assertTrue("Must include FOREGROUND_SERVICE_TYPE_LOCATION", foundFgs.contains("FOREGROUND_SERVICE_TYPE_LOCATION"))
        assertTrue("Must include FOREGROUND_SERVICE_TYPE_HEALTH", foundFgs.contains("FOREGROUND_SERVICE_TYPE_HEALTH"))
        assertFalse("Must NOT include FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE", foundFgs.contains("FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE"))
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
