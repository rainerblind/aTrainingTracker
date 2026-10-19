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
import com.atrainingtracker.banalservice.devices.DeviceType
import com.atrainingtracker.banalservice.sensor.MySensorManager
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle
import io.mockk.*
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Unit test suite verifying ANT+ handle lifecycle management, duplicate request prevention,
 * and DEAD state recovery in MyANTDevice (REQ-CON-020.4, TST-CON-012.4).
 */
class ANTDeviceLifecycleTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager

    private class TestHarnessANTDevice(
        context: Context,
        sensorManager: MySensorManager,
        deviceId: Long,
        antDeviceNumber: Int
    ) : MyANTDevice(context, sensorManager, DeviceType.HRM, deviceId, antDeviceNumber) {

        var nextHandleToReturn: PccReleaseHandle<*>? = null

        override fun addSensors() {}

        override fun setSpecificPcc(p0: AntPluginPcc?) {}

        override fun subscribeSpecificEvents() {}

        override fun subscribeCommonEvents() {}

        override fun requestAccess(): PccReleaseHandle<*>? {
            return nextHandleToReturn
        }

        fun triggerDeviceStateChange(state: DeviceState) {
            onDeviceStateChangeHandler(state)
        }
    }

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null
        every { ContextCompat.registerReceiver(any(), any(), any(), any(), any(), any()) } returns null

        mockkStatic(DevicesDatabaseManager::class)
        val mockDevicesDbManager = mockk<DevicesDatabaseManager>(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager

        mockkConstructor(android.content.Intent::class)
        every { anyConstructed<android.content.Intent>().setPackage(any()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<String>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Int>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Long>()) } answers { self as android.content.Intent }
        every { anyConstructed<android.content.Intent>().putExtra(any<String>(), any<Boolean>()) } answers { self as android.content.Intent }

        mockContext = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testStartSearchingReleasesExistingHandleBeforeRequestingNew() {
        val device = TestHarnessANTDevice(mockContext, mockSensorManager, 301L, 12345)
        val handle1 = mockk<PccReleaseHandle<*>>(relaxed = true)
        val handle2 = mockk<PccReleaseHandle<*>>(relaxed = true)

        device.setPccReleaseHandleForTesting(handle1)
        device.nextHandleToReturn = handle2

        device.startSearching()

        // Verify handle1 was closed to prevent DEVICE_ALREADY_IN_USE
        verify(exactly = 1) { handle1.close() }
        assertSame(handle2, device.pccReleaseHandle)
    }

    @Test
    fun testDeadDeviceStateTriggersReleaseAccessAndNullifiesHandle() {
        val device = TestHarnessANTDevice(mockContext, mockSensorManager, 302L, 23456)
        val handle = mockk<PccReleaseHandle<*>>(relaxed = true)
        device.setPccReleaseHandleForTesting(handle)

        device.triggerDeviceStateChange(DeviceState.DEAD)

        // Verify handle was closed and nulled
        verify(exactly = 1) { handle.close() }
        assertNull(device.pccReleaseHandle)
    }

    @Test
    fun testShutDownReleasesHandleAndNullifies() {
        val device = TestHarnessANTDevice(mockContext, mockSensorManager, 303L, 34567)
        val handle = mockk<PccReleaseHandle<*>>(relaxed = true)
        device.setPccReleaseHandleForTesting(handle)

        device.shutDown()

        verify(exactly = 1) { handle.close() }
        assertNull(device.pccReleaseHandle)
    }
}
