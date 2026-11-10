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

package com.atrainingtracker.banalservice.devices.ant_plus

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit test verifying unconditional PCC handle release and leak prevention in ANTBikeSpeedAndCadenceDevice
 * pursuant to REQ-CON-025.1 and TST-CON-025.1-2.
 */
class ANTBikeSpeedAndCadenceDeviceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager

    private class TestANTBikeSpeedAndCadenceDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        antDeviceNumber: Int
    ) : ANTBikeSpeedAndCadenceDevice(context, sensorManager, deviceId, antDeviceNumber) {

        public override fun releaseAccess() {
            super.releaseAccess()
        }

        public override fun setPccReleaseHandleForTesting(handle: PccReleaseHandle<*>?) {
            super.setPccReleaseHandleForTesting(handle)
        }

        public override fun getPccReleaseHandle(): PccReleaseHandle<*>? {
            return super.getPccReleaseHandle()
        }

        public override fun setPccReleaseHandle2ForTesting(handle: PccReleaseHandle<*>?) {
            super.setPccReleaseHandle2ForTesting(handle)
        }

        public override fun getPccReleaseHandle2ForTesting(): PccReleaseHandle<*>? {
            return super.getPccReleaseHandle2ForTesting()
        }

        override fun notifySensorsChanged() {
            // No-op for unit testing
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDb = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDb

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    /**
     * TST-CON-025.1: Verify super.releaseAccess() closes and nulls primary speed handle
     * even when cadence handle (pccReleaseHandle2) was never initialized or failed.
     */
    @Test
    fun testReleaseAccess_whenSecondaryHandleNull_unconditionallyReleasesPrimaryHandle() {
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 101L, 4567)
        val mockPrimaryHandle = mockk<PccReleaseHandle<*>>(relaxed = true)

        device.setPccReleaseHandleForTesting(mockPrimaryHandle)
        device.setPccReleaseHandle2ForTesting(null)

        device.releaseAccess()

        verify(exactly = 1) { mockPrimaryHandle.close() }
        assertNull("Primary release handle must be nulled", device.getPccReleaseHandle())
        assertNull("Secondary release handle must be null", device.getPccReleaseHandle2ForTesting())
    }

    /**
     * TST-CON-025.2: Verify both secondary cadence handle and primary speed handle
     * are closed and nulled when both were active.
     */
    @Test
    fun testReleaseAccess_whenBothHandlesActive_releasesBothSafely() {
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 102L, 4568)
        val mockPrimaryHandle = mockk<PccReleaseHandle<*>>(relaxed = true)
        val mockSecondaryHandle = mockk<PccReleaseHandle<*>>(relaxed = true)

        device.setPccReleaseHandleForTesting(mockPrimaryHandle)
        device.setPccReleaseHandle2ForTesting(mockSecondaryHandle)

        device.releaseAccess()

        verify(exactly = 1) { mockSecondaryHandle.close() }
        verify(exactly = 1) { mockPrimaryHandle.close() }
        assertNull("Secondary handle must be nulled", device.getPccReleaseHandle2ForTesting())
        assertNull("Primary handle must be nulled", device.getPccReleaseHandle())
    }

    /**
     * TST-CON-025.2: Verify that even if secondary handle close throws an exception,
     * primary handle is still guaranteed to be closed and nulled via finally block.
     */
    @Test
    fun testReleaseAccess_whenSecondaryHandleThrows_primaryHandleStillCleanedUp() {
        val device = TestANTBikeSpeedAndCadenceDevice(mockContext, mockSensorManager, 103L, 4569)
        val mockPrimaryHandle = mockk<PccReleaseHandle<*>>(relaxed = true)
        val mockSecondaryHandle = mockk<PccReleaseHandle<*>>()
        every { mockSecondaryHandle.close() } throws RuntimeException("ANT plugin IPC error")

        device.setPccReleaseHandleForTesting(mockPrimaryHandle)
        device.setPccReleaseHandle2ForTesting(mockSecondaryHandle)

        device.releaseAccess()

        verify(exactly = 1) { mockSecondaryHandle.close() }
        verify(exactly = 1) { mockPrimaryHandle.close() }
        assertNull("Secondary handle must be nulled in finally", device.getPccReleaseHandle2ForTesting())
        assertNull("Primary handle must be nulled", device.getPccReleaseHandle())
    }
}
