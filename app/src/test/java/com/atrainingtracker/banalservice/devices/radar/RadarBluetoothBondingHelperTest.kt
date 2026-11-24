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

package com.atrainingtracker.banalservice.devices.radar

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit test suite for [RadarBluetoothBondingHelper] (`REQ-CON-061.2`, `TST-CON-061.2`, `ATT-8731`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadarBluetoothBondingHelperTest {

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        RadarStateRepository.reset()
    }

    @After
    fun tearDown() {
        RadarStateRepository.reset()
    }

    @Test
    fun testRequestBond_api33PlusRandomStaticAndPublicAddress_initiatesCreateBond_tstCon061_2() {
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockRandomDevice = mockk<BluetoothDevice>(relaxed = true)
        val mockPublicDevice = mockk<BluetoothDevice>(relaxed = true)

        every { mockAdapter.isEnabled } returns true
        every {
            mockAdapter.getRemoteLeDevice("C6:CC:E3:F5:F0:37", BluetoothDevice.ADDRESS_TYPE_RANDOM)
        } returns mockRandomDevice
        every { mockRandomDevice.bondState } returns BluetoothDevice.BOND_NONE
        every { mockRandomDevice.createBond() } returns true

        val randomResult = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
            context = appContext,
            macAddress = "  C6:CC:E3:F5:F0:37  ",
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            hasBluetoothConnectPermission = { true },
            bluetoothAdapterProvider = { mockAdapter },
            settingsLauncher = { _, _ -> throw AssertionError("Should not open settings") }
        )
        assertEquals(RadarBondActionResult.BONDING_INITIATED, randomResult)
        verify(exactly = 1) {
            mockAdapter.getRemoteLeDevice("C6:CC:E3:F5:F0:37", BluetoothDevice.ADDRESS_TYPE_RANDOM)
            mockRandomDevice.createBond()
        }

        every {
            mockAdapter.getRemoteLeDevice("00:11:22:33:44:55", BluetoothDevice.ADDRESS_TYPE_PUBLIC)
        } returns mockPublicDevice
        every { mockPublicDevice.bondState } returns BluetoothDevice.BOND_NONE
        every { mockPublicDevice.createBond() } returns true

        val publicResult = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
            context = appContext,
            macAddress = "00:11:22:33:44:55",
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasBluetoothConnectPermission = { true },
            bluetoothAdapterProvider = { mockAdapter },
            settingsLauncher = { _, _ -> throw AssertionError("Should not open settings") }
        )
        assertEquals(RadarBondActionResult.BONDING_INITIATED, publicResult)
        verify(exactly = 1) {
            mockAdapter.getRemoteLeDevice("00:11:22:33:44:55", BluetoothDevice.ADDRESS_TYPE_PUBLIC)
            mockPublicDevice.createBond()
        }
    }

    @Test
    fun testRequestBond_belowApi33UsesGetRemoteDevice_andAlreadyBondedClearsRepository_tstCon061_2() {
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockDevice = mockk<BluetoothDevice>(relaxed = true)
        every { mockAdapter.isEnabled } returns true
        every { mockAdapter.getRemoteDevice("AA:BB:CC:DD:EE:FF") } returns mockDevice
        every { mockDevice.bondState } returns BluetoothDevice.BOND_NONE
        every { mockDevice.createBond() } returns true

        val legacyResult = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
            context = appContext,
            macAddress = "AA:BB:CC:DD:EE:FF",
            sdkInt = Build.VERSION_CODES.S,
            hasBluetoothConnectPermission = { true },
            bluetoothAdapterProvider = { mockAdapter },
            settingsLauncher = { _, _ -> throw AssertionError("Should not open settings") }
        )
        assertEquals(RadarBondActionResult.BONDING_INITIATED, legacyResult)
        verify(exactly = 1) { mockAdapter.getRemoteDevice("AA:BB:CC:DD:EE:FF") }

        // Already bonded must clear RadarStateRepository and return ALREADY_BONDED
        RadarStateRepository.updateThreatState(
            RadarThreatState(connectionState = RadarConnectionState.CONNECTED, source = RadarSource.BLE)
        )
        RadarStateRepository.setV2BondingRequired(true, "AA:BB:CC:DD:EE:FF")
        every { mockDevice.bondState } returns BluetoothDevice.BOND_BONDED

        val alreadyBonded = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
            context = appContext,
            macAddress = "AA:BB:CC:DD:EE:FF",
            sdkInt = Build.VERSION_CODES.S,
            hasBluetoothConnectPermission = { true },
            bluetoothAdapterProvider = { mockAdapter },
            settingsLauncher = { _, _ -> throw AssertionError("Should not open settings") }
        )
        assertEquals(RadarBondActionResult.ALREADY_BONDED, alreadyBonded)
        assertFalse("ALREADY_BONDED must clear repository bonding flag", RadarStateRepository.getState().isV2BondingRequired)
    }

    @Test
    fun testFallbackToBluetoothSettings_missingPermissionSecurityExceptionAndFalseCreateBond_setsNewTaskFlagStrictlyForNonActivity_tstCon061_2() {
        // 1. Missing BLUETOOTH_CONNECT on API 31+ with Application context -> FLAG_ACTIVITY_NEW_TASK set
        var capturedAppIntent: Intent? = null
        val noPermResult = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
            context = appContext,
            macAddress = "AA:BB:CC:DD:EE:FF",
            sdkInt = Build.VERSION_CODES.S,
            hasBluetoothConnectPermission = { false },
            bluetoothAdapterProvider = { throw AssertionError("Adapter must not be touched without permission") },
            settingsLauncher = { _, intent ->
                capturedAppIntent = intent
                true
            }
        )
        assertEquals(RadarBondActionResult.OPENED_BLUETOOTH_SETTINGS, noPermResult)
        assertNotNull(capturedAppIntent)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, capturedAppIntent?.action)
        assertTrue(
            "Non-Activity context must include FLAG_ACTIVITY_NEW_TASK",
            (capturedAppIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        )

        // 2. Activity context on SecurityException -> FLAG_ACTIVITY_NEW_TASK NOT set
        val activity = Robolectric.buildActivity(Activity::class.java).get()
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockDevice = mockk<BluetoothDevice>(relaxed = true)
        every { mockAdapter.isEnabled } returns true
        every { mockAdapter.getRemoteLeDevice(any(), any()) } returns mockDevice
        every { mockDevice.bondState } returns BluetoothDevice.BOND_NONE
        every { mockDevice.createBond() } throws SecurityException("missing BLUETOOTH_CONNECT")

        var capturedActivityIntent: Intent? = null
        val secExResult = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
            context = activity,
            macAddress = "C6:CC:E3:F5:F0:37",
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            hasBluetoothConnectPermission = { true },
            bluetoothAdapterProvider = { mockAdapter },
            settingsLauncher = { _, intent ->
                capturedActivityIntent = intent
                true
            }
        )
        assertEquals(RadarBondActionResult.OPENED_BLUETOOTH_SETTINGS, secExResult)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, capturedActivityIntent?.action)
        assertEquals(
            "Activity context must NOT force FLAG_ACTIVITY_NEW_TASK",
            0,
            capturedActivityIntent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK
        )

        // 3. createBond() == false with failing launcher -> FAILED (no crash)
        every { mockDevice.createBond() } returns false
        val failedResult = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
            context = appContext,
            macAddress = "C6:CC:E3:F5:F0:37",
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            hasBluetoothConnectPermission = { true },
            bluetoothAdapterProvider = { mockAdapter },
            settingsLauncher = { _, _ -> false }
        )
        assertEquals(RadarBondActionResult.FAILED, failedResult)
    }

    /**
     * TST-CON-061.2 / REQ-CON-061.2 (ATT-8731 Bounce 1 Heal):
     * When BluetoothDevice.getBondState() is already BOND_BONDING, short-circuit and return
     * BONDING_INITIATED without calling createBond() or kicking the user out to Settings.
     */
    @Test
    fun testRequestBond_bondBondingInProgress_shortCircuitsWithoutCreateBondOrSettings_tstCon061_2() {
        val mockAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val mockDevice = mockk<BluetoothDevice>(relaxed = true)
        every { mockAdapter.isEnabled } returns true
        every {
            mockAdapter.getRemoteLeDevice("C6:CC:E3:F5:F0:37", BluetoothDevice.ADDRESS_TYPE_RANDOM)
        } returns mockDevice
        every { mockDevice.bondState } returns BluetoothDevice.BOND_BONDING

        val result = RadarBluetoothBondingHelper.requestBondOrOpenSettings(
            context = appContext,
            macAddress = "C6:CC:E3:F5:F0:37",
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            hasBluetoothConnectPermission = { true },
            bluetoothAdapterProvider = { mockAdapter },
            settingsLauncher = { _, _ -> throw AssertionError("BOND_BONDING must not open Bluetooth settings") }
        )
        assertEquals(RadarBondActionResult.BONDING_INITIATED, result)
        verify(exactly = 0) { mockDevice.createBond() }
    }
}
