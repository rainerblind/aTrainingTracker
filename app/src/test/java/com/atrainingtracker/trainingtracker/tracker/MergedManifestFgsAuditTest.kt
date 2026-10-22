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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.atrainingtracker.trainingtracker.tracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Architectural audit test verifying that the application manifest and merged manifest
 * strictly omit FOREGROUND_SERVICE_CONNECTED_DEVICE and define streamlined location|health
 * foreground service types for TrackerService and BANALService (REQ-TRK-022, TST-TRK-014, ATT-3567).
 */
class MergedManifestFgsAuditTest {

    private fun resolveManifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not locate AndroidManifest.xml in any candidate path: $candidates")
    }

    @Test
    fun testManifest_doesNotDeclareForegroundServiceConnectedDevice() {
        val manifest = resolveManifestFile()
        val content = manifest.readText()

        assertFalse(
            "AndroidManifest.xml must NOT declare FOREGROUND_SERVICE_CONNECTED_DEVICE permission (REQ-TRK-022)",
            content.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE")
        )
    }

    @Test
    fun testManifest_retainsForegroundServiceLocationAndHealthPermissions() {
        val manifest = resolveManifestFile()
        val content = manifest.readText()

        assertTrue(
            "AndroidManifest.xml must retain FOREGROUND_SERVICE_LOCATION permission",
            content.contains("android.permission.FOREGROUND_SERVICE_LOCATION")
        )
        assertTrue(
            "AndroidManifest.xml must retain FOREGROUND_SERVICE_HEALTH permission",
            content.contains("android.permission.FOREGROUND_SERVICE_HEALTH")
        )
    }

    @Test
    fun testManifest_trackerService_declaresLocationAndHealthForegroundServiceType() {
        val manifest = resolveManifestFile()
        val content = manifest.readText()

        // Match TrackerService definition block
        val trackerServiceRegex = Regex(
            """<service\s+[^>]*android:name="\.trainingtracker\.tracker\.TrackerService"[^>]*android:foregroundServiceType="([^"]+)"\s*/>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = trackerServiceRegex.find(content)
            ?: throw AssertionError("TrackerService declaration with android:foregroundServiceType not found in manifest")

        val fgsTypeAttr = match.groupValues[1]
        assertTrue(
            "TrackerService foregroundServiceType must be 'location|health', was '$fgsTypeAttr'",
            fgsTypeAttr == "location|health"
        )
        assertFalse(
            "TrackerService foregroundServiceType must NOT contain 'connectedDevice'",
            fgsTypeAttr.contains("connectedDevice")
        )
    }

    @Test
    fun testManifest_banalService_declaresLocationAndHealthForegroundServiceType() {
        val manifest = resolveManifestFile()
        val content = manifest.readText()

        // Match BANALService definition block
        val banalServiceRegex = Regex(
            """<service\s+[^>]*android:name="\.banalservice\.BANALService"[^>]*android:foregroundServiceType="([^"]+)"\s*/>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = banalServiceRegex.find(content)
            ?: throw AssertionError("BANALService declaration with android:foregroundServiceType not found in manifest")

        val fgsTypeAttr = match.groupValues[1]
        assertTrue(
            "BANALService foregroundServiceType must be 'location|health', was '$fgsTypeAttr'",
            fgsTypeAttr == "location|health"
        )
        assertFalse(
            "BANALService foregroundServiceType must NOT contain 'connectedDevice'",
            fgsTypeAttr.contains("connectedDevice")
        )
    }

    @Test
    fun testMergedManifest_ifGenerated_omitsConnectedDevicePermissionAndAttribute() {
        val mergedCandidates = listOf(
            File("build/intermediates/merged_manifests/debug/processDebugMainManifest/AndroidManifest.xml"),
            File("app/build/intermediates/merged_manifests/debug/processDebugMainManifest/AndroidManifest.xml")
        )
        val mergedFile = mergedCandidates.firstOrNull { it.exists() } ?: return

        val content = mergedFile.readText()
        assertFalse(
            "Merged manifest must NOT contain android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            content.contains("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE")
        )
        assertFalse(
            "Merged manifest must NOT contain 'connectedDevice' in foregroundServiceType",
            content.contains("connectedDevice")
        )
    }
}
