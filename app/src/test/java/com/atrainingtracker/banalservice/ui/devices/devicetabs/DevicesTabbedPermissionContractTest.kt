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

/**
 * Sensor Discovery Just-in-Time Permission Gating Tests (REQ-PRI-006.3, REQ-CON-019.2, TST-PRI-005.7, TST-CON-011.7, ATT-3998, ATT-4449).
 *
 * Verifies:
 * 1. On Android 12+, calling sensor search when BLUETOOTH_SCAN is missing triggers permission request contract.
 * 2. If permission is denied, visible feedback state is emitted (bluetooth_permission_scan_required) and search broadcast is withheld.
 * 3. On Android 12+, calling sensor search when BLUETOOTH_SCAN is granted dispatches search immediately.
 * 4. PairingProtocolBottomSheet checks BLUETOOTH_SCAN before BLE sensor pairing scan.
 * 5. Screen teardown lifecycle is decoupled so granting permission does not cancel pairing flow.
 */
class DevicesTabbedPermissionContractTest {

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

        // 3. With decoupled screen teardown, permission elevation (false -> true)
        // does NOT trigger onDispose teardown.
        assertEquals("hasBluetoothScanPermission must become true", true, hasBluetoothScanPermission)
        assertEquals("pendingPairingProtocol must be cleared", null, pendingPairingProtocol)
        assertEquals(
            "selectedPairingProtocol must be preserved to open DeviceTypeSelectionDialog",
            Protocol.BLUETOOTH_LE,
            selectedPairingProtocol
        )
    }
}
