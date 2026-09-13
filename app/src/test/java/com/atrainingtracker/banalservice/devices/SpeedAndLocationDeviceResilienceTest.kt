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

package com.atrainingtracker.banalservice.devices

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.location.LocationProvider
import android.util.Log
import androidx.core.content.ContextCompat
import com.atrainingtracker.banalservice.database.DevicesDatabaseManager
import com.atrainingtracker.banalservice.sensor.MySensorManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying location provider resilience and exception shielding for
 * SpeedAndLocationDevice_GPS and SpeedAndLocationDevice_Network (REQ-STB-004, TST-STB-004, ATT-623).
 */
class SpeedAndLocationDeviceResilienceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSensorManager: MySensorManager
    private lateinit var mockLocationManager: LocationManager
    private lateinit var mockDevicesDbManager: DevicesDatabaseManager

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any()) } returns 0

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any()) } returns null

        mockkConstructor(Intent::class)
        every { anyConstructed<Intent>().setPackage(any()) } answers { self as Intent }

        mockkStatic(DevicesDatabaseManager::class)
        mockDevicesDbManager = mockk(relaxed = true)
        every { DevicesDatabaseManager.getInstance(any()) } returns mockDevicesDbManager
        every { mockDevicesDbManager.getSpeedAndLocationGPSDeviceId() } returns 101L
        every { mockDevicesDbManager.getSpeedAndLocationNetworkDeviceId() } returns 102L

        mockLocationManager = mockk(relaxed = true)
        mockSensorManager = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.getSystemService(Context.LOCATION_SERVICE) } returns mockLocationManager
        every { mockContext.packageName } returns "com.atrainingtracker"
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testGpsDevice_missingGpsProvider_initializesCleanlyWithoutCrashing() {
        // Arrange: "gps" provider does not exist on this device/environment
        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns null

        // Act
        val device = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)

        // Assert: device created successfully without throwing IllegalArgumentException
        assertNotNull(device)
        verify(exactly = 0) {
            mockLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, any<Long>(), any<Float>(), any<SpeedAndLocationDevice_GPS>())
        }
    }

    @Test
    fun testGpsDevice_requestLocationUpdatesThrowsIllegalArgumentException_handledCleanly() {
        // Arrange: provider appears in getProvider, but requestLocationUpdates throws IllegalArgumentException
        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockProvider
        every {
            mockLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, any<Long>(), any<Float>(), any<SpeedAndLocationDevice_GPS>())
        } throws IllegalArgumentException("provider \"gps\" does not exist")

        // Act
        val device = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)

        // Assert: Exception caught and handled cleanly without bubbling up
        assertNotNull(device)
    }

    @Test
    fun testGpsDevice_requestLocationUpdatesThrowsSecurityException_handledCleanly() {
        // Arrange: permission revoked or missing while requesting updates
        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockProvider
        every {
            mockLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, any<Long>(), any<Float>(), any<SpeedAndLocationDevice_GPS>())
        } throws SecurityException("Missing ACCESS_FINE_LOCATION permission")

        // Act
        val device = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)

        // Assert: SecurityException caught cleanly
        assertNotNull(device)
    }

    @Test
    fun testGpsDevice_validProvider_registersLocationUpdatesAndShutsDownCleanly() {
        // Arrange: standard GPS provider available
        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockProvider

        // Act
        val device = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)

        // Assert
        verify(exactly = 1) {
            mockLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, device)
        }

        // Act: shutdown
        device.shutDown()

        // Assert: updates removed
        verify(exactly = 1) {
            mockLocationManager.removeUpdates(device)
        }
    }

    @Test
    fun testNetworkDevice_missingNetworkProvider_initializesCleanlyWithoutCrashing() {
        // Arrange: "network" provider does not exist
        every { mockLocationManager.getProvider(LocationManager.NETWORK_PROVIDER) } returns null

        // Act
        val device = SpeedAndLocationDevice_Network(mockContext, mockSensorManager)

        // Assert: initialized safely without requesting updates
        assertNotNull(device)
        verify(exactly = 0) {
            mockLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, any<Long>(), any<Float>(), any<SpeedAndLocationDevice_Network>())
        }
    }

    @Test
    fun testNetworkDevice_requestLocationUpdatesThrowsIllegalArgumentException_handledCleanly() {
        // Arrange: provider exists but throws IllegalArgumentException
        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.NETWORK_PROVIDER) } returns mockProvider
        every {
            mockLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, any<Long>(), any<Float>(), any<SpeedAndLocationDevice_Network>())
        } throws IllegalArgumentException("provider \"network\" does not exist")

        // Act
        val device = SpeedAndLocationDevice_Network(mockContext, mockSensorManager)

        // Assert: caught safely
        assertNotNull(device)
    }

    @Test
    fun testGpsDevice_onProviderEnabled_handlesExceptionGracefully() {
        // Arrange: initial creation with provider present
        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockProvider

        val device = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)

        // When provider re-enables, requestLocationUpdates throws unexpected SecurityException
        every {
            mockLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, any<Long>(), any<Float>(), any<SpeedAndLocationDevice_GPS>())
        } throws SecurityException("Permission temporarily unavailable")

        // Act: onProviderEnabled
        device.onProviderEnabled(LocationManager.GPS_PROVIDER)

        // Assert: no crash
        assertNotNull(device)
    }

    @Test
    fun testNetworkDevice_onProviderEnabled_handlesExceptionGracefully() {
        // Arrange: initial creation with provider present
        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.NETWORK_PROVIDER) } returns mockProvider

        val device = SpeedAndLocationDevice_Network(mockContext, mockSensorManager)

        // When provider re-enables, requestLocationUpdates throws IllegalArgumentException
        every {
            mockLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, any<Long>(), any<Float>(), any<SpeedAndLocationDevice_Network>())
        } throws IllegalArgumentException("provider \"network\" does not exist")

        // Act: onProviderEnabled
        device.onProviderEnabled(LocationManager.NETWORK_PROVIDER)

        // Assert: no crash
        assertNotNull(device)
    }

    @Test
    fun testShutDown_handlesRemoveUpdatesExceptionGracefully() {
        // Arrange
        val mockProvider = mockk<LocationProvider>()
        every { mockLocationManager.getProvider(LocationManager.GPS_PROVIDER) } returns mockProvider
        every { mockLocationManager.removeUpdates(any<SpeedAndLocationDevice_GPS>()) } throws RuntimeException("Remote dead service")

        val device = SpeedAndLocationDevice_GPS(mockContext, mockSensorManager)

        // Act: shutdown should not propagate exception
        device.shutDown()

        // Assert
        assertNotNull(device)
    }
}
