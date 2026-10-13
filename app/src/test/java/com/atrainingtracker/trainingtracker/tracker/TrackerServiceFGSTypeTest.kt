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

import android.app.Notification
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Unit tests verifying permission-gated Foreground Service types derivation in TrackerService
 * (REQ-TRK-015, TST-TRK-007, ATT-2972).
 */
class TrackerServiceFGSTypeTest {

    private class TestableTrackerService : TrackerService() {
        var bluetoothGranted = false
        var healthGranted = false
        var fgLocationGranted = true
        var testSdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE

        override fun getBuildVersionSdkInt(): Int = testSdkInt
        override fun hasBluetoothPermission(): Boolean = bluetoothGranted
        override fun hasHealthPermission(): Boolean = healthGranted
        override fun hasLocationPermission(): Boolean = fgLocationGranted

        // Expose protected methods for testing
        public override fun determineForegroundServiceType(): Int = super.determineForegroundServiceType()
    }

    private lateinit var service: TestableTrackerService

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        service = TestableTrackerService()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testDetermineForegroundServiceType_WithoutBluetoothAndHealth_ReturnsLocationOnly() {
        service.bluetoothGranted = false
        service.healthGranted = false

        val fgsType = service.determineForegroundServiceType()

        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION, fgsType)
        assertTrue(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0)
        assertFalse(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE != 0)
        assertFalse(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH != 0)
    }

    @Test
    fun testDetermineForegroundServiceType_WithBluetoothOnly_ReturnsLocationAndConnectedDevice() {
        service.bluetoothGranted = true
        service.healthGranted = false

        val fgsType = service.determineForegroundServiceType()

        val expected = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        assertEquals(expected, fgsType)
        assertTrue(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0)
        assertTrue(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE != 0)
        assertFalse(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH != 0)
    }

    @Test
    fun testDetermineForegroundServiceType_WithBluetoothAndHealth_ReturnsAllThreeTypes() {
        service.bluetoothGranted = true
        service.healthGranted = true

        val fgsType = service.determineForegroundServiceType()

        val expected = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        assertEquals(expected, fgsType)
        assertTrue(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION != 0)
        assertTrue(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE != 0)
        assertTrue(fgsType and ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH != 0)
    }
}
