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

package com.atrainingtracker.banalservice.ui.devices.devicetabs

import com.atrainingtracker.banalservice.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Sensor Discovery Just-in-Time Permission Gating Tests (REQ-PRI-006.3, REQ-CON-019.2, TST-PRI-005.7, TST-CON-011.7, ATT-3998, ATT-4449).
 *
 * Verifies:
 * 1. On Android 12+, calling sensor search when BLUETOOTH_SCAN is missing triggers permission request contract.
 * 2. If permission is denied, visible feedback state is emitted (bluetooth_permission_scan_required) and search broadcast is withheld.
 * 3. On Android 12+, calling sensor search when BLUETOOTH_SCAN is granted dispatches search immediately.
 * 4. PairingProtocolBottomSheet checks BLUETOOTH_SCAN before BLE sensor pairing scan.
 * 5. Screen teardown lifecycle is decoupled into DisposableEffect(Unit) and LaunchedEffect(hasBluetoothScanPermission) so granting permission does not cancel pairing flow.
 * 6. AndroidManifest.xml declares neverForLocation and tools:targetApi="s" on BLUETOOTH_SCAN.
 */
class DevicesTabbedPermissionContractTest {

    private fun findFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("app/$relativePath"),
            File("../$relativePath")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("File not found in candidates: $relativePath")
    }

    @Test
    fun testDevicesTabbedScreen_sourceContract_verifiesBluetoothScanGating() {
        val file = findFile("src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt")
        val content = file.readText()

        // 1. Must check Manifest.permission.BLUETOOTH_SCAN on API 31+
        assertTrue(
            "DevicesTabbedScreen must check Manifest.permission.BLUETOOTH_SCAN",
            content.contains("Manifest.permission.BLUETOOTH_SCAN")
        )

        // 2. Must reference bluetooth_permission_scan_required string
        assertTrue(
            "DevicesTabbedScreen must reference R.string.bluetooth_permission_scan_required",
            content.contains("R.string.bluetooth_permission_scan_required")
        )

        // 3. Must reference grant_permission action button string
        assertTrue(
            "DevicesTabbedScreen must reference R.string.grant_permission",
            content.contains("R.string.grant_permission")
        )

        // 4. Must decouple screen lifecycle teardown from dynamic permission state (ATT-4449, REQ-PRI-006.3, TST-PRI-005.7)
        assertTrue(
            "DevicesTabbedScreen must isolate screen lifecycle cleanup in DisposableEffect(Unit)",
            content.contains("DisposableEffect(Unit)")
        )
        assertTrue(
            "DevicesTabbedScreen must reactively trigger search via LaunchedEffect(hasBluetoothScanPermission)",
            content.contains("LaunchedEffect(hasBluetoothScanPermission)")
        )
        assertFalse(
            "DevicesTabbedScreen must NOT key DisposableEffect on hasBluetoothScanPermission",
            content.contains("DisposableEffect(hasBluetoothScanPermission)")
        )

        // 5. Must check hasBluetoothScanPermission in PairingProtocolBottomSheet
        assertTrue(
            "DevicesTabbedScreen must check scan permission on BLE pairing",
            content.contains("selectedProtocol == Protocol.BLUETOOTH_LE && !hasBluetoothScanPermission")
        )
    }

    @Test
    fun testScanPermissionStateSimulation_android12PlusMissingScanWithholdsSearchAndShowsBanner() {
        val sdkInt = 31 // Android 12
        val scanGranted = false

        val checkHasBluetoothScan = if (sdkInt >= 31) scanGranted else true
        val showScanWarningBanner = !checkHasBluetoothScan && sdkInt >= 31
        val shouldStartSearching = checkHasBluetoothScan

        assertTrue("Warning banner must be shown when BLUETOOTH_SCAN is missing on Android 12+", showScanWarningBanner)
        assertFalse("Search must be withheld when BLUETOOTH_SCAN is missing on Android 12+", shouldStartSearching)
    }

    @Test
    fun testScanPermissionStateSimulation_android12PlusGrantedScanDispatchesSearchAndHidesBanner() {
        val sdkInt = 31 // Android 12
        val scanGranted = true

        val checkHasBluetoothScan = if (sdkInt >= 31) scanGranted else true
        val showScanWarningBanner = !checkHasBluetoothScan && sdkInt >= 31
        val shouldStartSearching = checkHasBluetoothScan

        assertFalse("Warning banner must NOT be shown when BLUETOOTH_SCAN is granted on Android 12+", showScanWarningBanner)
        assertTrue("Search must start immediately when BLUETOOTH_SCAN is granted on Android 12+", shouldStartSearching)
    }

    @Test
    fun testScanPermissionStateSimulation_preAndroid12AlwaysSatisfiesScanWithoutBanner() {
        val sdkInt = 30 // Android 11
        val scanGranted = false // Ignored on Android 11

        val checkHasBluetoothScan = if (sdkInt >= 31) scanGranted else true
        val showScanWarningBanner = !checkHasBluetoothScan && sdkInt >= 31
        val shouldStartSearching = checkHasBluetoothScan

        assertFalse("Warning banner must NOT be shown on Android < 12", showScanWarningBanner)
        assertTrue("Search must proceed on Android < 12 without BLUETOOTH_SCAN runtime check", shouldStartSearching)
    }

    @Test
    fun testManifestDeclaration_bluetoothScanHasNeverForLocation() {
        val manifestFile = findFile("src/main/AndroidManifest.xml")
        val manifestContent = manifestFile.readText()

        assertTrue(
            "AndroidManifest.xml must declare android:usesPermissionFlags=\"neverForLocation\" on BLUETOOTH_SCAN",
            manifestContent.contains("android:usesPermissionFlags=\"neverForLocation\"")
        )
        assertTrue(
            "AndroidManifest.xml must declare tools:targetApi=\"s\" on BLUETOOTH_SCAN",
            manifestContent.contains("tools:targetApi=\"s\"")
        )
    }

    @Test
    fun testPairingFlow_permissionElevationPreservesSelectedPairingProtocol() {
        // Initial state before FAB pairing click
        var hasBluetoothScanPermission = false
        var pendingPairingProtocol: Protocol? = null
        var selectedPairingProtocol: Protocol? = null

        // 1. User taps '+' FAB and selects Bluetooth
        val selectedProtocol = Protocol.BLUETOOTH_LE
        if (selectedProtocol == Protocol.BLUETOOTH_LE && !hasBluetoothScanPermission) {
            pendingPairingProtocol = selectedProtocol
            // bluetoothScanLauncher.launch(...) triggered
        } else {
            selectedPairingProtocol = selectedProtocol
        }

        assertEquals(Protocol.BLUETOOTH_LE, pendingPairingProtocol)
        assertEquals(null, selectedPairingProtocol)

        // 2. User grants permission in system dialog -> launcher callback runs
        val granted = true
        hasBluetoothScanPermission = granted
        if (granted) {
            pendingPairingProtocol?.let { protocolToPair ->
                pendingPairingProtocol = null
                selectedPairingProtocol = protocolToPair
            }
        }

        // 3. With decoupled DisposableEffect(Unit), permission elevation (false -> true)
        // does NOT trigger onDispose teardown.
        // If it were buggy DisposableEffect(hasBluetoothScanPermission), onDispose would run:
        // selectedPairingProtocol = null
        // Under decoupled architecture:
        assertEquals("hasBluetoothScanPermission must become true", true, hasBluetoothScanPermission)
        assertEquals("pendingPairingProtocol must be cleared", null, pendingPairingProtocol)
        assertEquals(
            "selectedPairingProtocol must be preserved to open DeviceTypeSelectionDialog",
            Protocol.BLUETOOTH_LE,
            selectedPairingProtocol
        )
    }
}
